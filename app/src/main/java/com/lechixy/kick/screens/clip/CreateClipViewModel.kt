package com.lechixy.kick.screens.clip

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lechixy.kick.R
import com.lechixy.kick.data.account.model.AccountProfile
import com.lechixy.kick.data.account.repository.AuthRepository
import com.lechixy.kick.data.channel.model.ClipDetail
import com.lechixy.kick.data.channel.model.ClipDraft
import com.lechixy.kick.data.channel.model.CreateClipRequest
import com.lechixy.kick.data.channel.model.FinalizeClipRequest
import com.lechixy.kick.data.channel.repository.ClipRepository
import com.lechixy.kick.data.common.remote.KickNetwork
import com.lechixy.kick.data.common.repository.KickRepository
import com.lechixy.kick.data.player.remote.ViewerSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

internal const val CLIP_TITLE_LIMIT = 50

@Immutable
internal data class ClipTrim(val start: Int, val end: Int) {
    val duration get() = end - start

    fun moveBy(delta: Int, sourceDuration: Int): ClipTrim {
        val newStart = (start + delta).coerceIn(0, sourceDuration - duration)
        return ClipTrim(newStart, newStart + duration)
    }

    fun resizeStart(delta: Int, sourceDuration: Int) =
        copy(start = (start + delta).coerceIn((end - 60).coerceAtLeast(0), end - minOf(10, sourceDuration)))

    fun resizeEnd(delta: Int, sourceDuration: Int) =
        copy(end = (end + delta).coerceIn(start + minOf(10, sourceDuration), (start + 60).coerceAtMost(sourceDuration)))

    companion object {
        fun initial(sourceDuration: Int) = ClipTrim((sourceDuration - 30).coerceAtLeast(0), sourceDuration)
    }
}

@Immutable
internal data class CreateClipUiState(
    val isInitializing: Boolean = true,
    val isPublishing: Boolean = false,
    val needsSignIn: Boolean = false,
    val draft: ClipDraft? = null,
    val publishedClip: ClipDetail? = null,
    val creator: AccountProfile? = null,
    val title: String = "",
    val trim: ClipTrim = ClipTrim.initial(180),
    val isPlaying: Boolean = false,
    val isMuted: Boolean = false,
    @StringRes val errorMessage: Int? = null,
) {
    val canPublish get() = title.isNotBlank() && title.length <= CLIP_TITLE_LIMIT &&
        draft != null && !isPublishing && !needsSignIn
}

internal class CreateClipViewModel(
    private val auth: AuthRepository,
    private val videoId: String?,
    private val positionSeconds: Int,
    private val liveSession: ViewerSession?,
) : ViewModel() {
    private val mutableState = MutableStateFlow(CreateClipUiState())
    val state = mutableState.asStateFlow()
    private var draftGeneration: Long? = null

    init {
        viewModelScope.launch {
            auth.session.map { it.signedIn to it.generation }.distinctUntilChanged().collect { (signedIn, _) ->
                if (state.value.publishedClip == null && !state.value.isPublishing) {
                    if (signedIn) initClip()
                    else requireSignIn()
                }
            }
        }
        viewModelScope.launch {
            auth.session.map { it.profile }.distinctUntilChanged().collect { profile ->
                mutableState.value = state.value.copy(creator = profile)
            }
        }
    }

    private fun initClip() {
        val session = auth.session.value
        if (!session.signedIn) {
            requireSignIn()
            return
        }
        if (state.value.isInitializing && draftGeneration == session.generation) return
        draftGeneration = session.generation
        mutableState.value = state.value.copy(
            isInitializing = true, needsSignIn = false, draft = null, isPlaying = false, errorMessage = null,
        )
        viewModelScope.launch {
            try {
                val id = videoId ?: KickRepository(KickNetwork.api)
                    .resolveViewerSession(checkNotNull(liveSession)).vodId
                check(!id.isNullOrBlank() && isCurrentDraft(session.generation))
                val draft = ClipRepository.create(auth, CreateClipRequest(id, positionSeconds))
                check(draft.id.isNotBlank() && draft.previewUrl.isNotBlank() && draft.sourceDurationSeconds > 0)
                if (isCurrentDraft(session.generation)) {
                    mutableState.value = state.value.copy(
                        isInitializing = false, draft = draft, trim = ClipTrim.initial(draft.sourceDurationSeconds),
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (isCurrentDraft(session.generation)) {
                    mutableState.value = state.value.copy(isInitializing = false, errorMessage = R.string.clip_create_failed)
                }
            }
        }
    }

    fun setTitle(title: String) {
        if (!state.value.isPublishing) mutableState.value = state.value.copy(title = title.take(CLIP_TITLE_LIMIT))
    }

    fun setTrim(trim: ClipTrim) {
        if (!state.value.isPublishing) mutableState.value = state.value.copy(trim = trim)
    }

    fun togglePlayback() {
        mutableState.value = state.value.copy(isPlaying = !state.value.isPlaying)
    }

    fun pausePreview() {
        mutableState.value = state.value.copy(isPlaying = false)
    }

    fun toggleMute() {
        mutableState.value = state.value.copy(isMuted = !state.value.isMuted)
    }

    fun retry() {
        if (state.value.isInitializing) return
        if (state.value.draft == null) initClip() else publish()
    }

    fun publish() {
        val current = state.value
        val draft = current.draft ?: return
        if (!current.canPublish || current.publishedClip != null) return
        val generation = draftGeneration ?: return
        if (!isCurrentDraft(generation)) {
            initClip()
            return
        }
        mutableState.value = current.copy(isPublishing = true, isPlaying = false, errorMessage = null)
        viewModelScope.launch {
            try {
                val request = FinalizeClipRequest(current.title.trim(), current.trim.duration, current.trim.start)
                val clip = ClipRepository.finalize(auth, draft.id, request)
                mutableState.value = state.value.copy(isPublishing = false, publishedClip = clip)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = state.value.copy(isPublishing = false)
                when {
                    isCurrentDraft(generation) ->
                        mutableState.value = state.value.copy(errorMessage = R.string.clip_publish_failed)
                    auth.session.value.signedIn -> initClip()
                    else -> requireSignIn()
                }
            }
        }
    }

    private fun isCurrentDraft(generation: Long): Boolean {
        val session = auth.session.value
        return session.signedIn && session.generation == generation && draftGeneration == generation
    }

    private fun requireSignIn() {
        draftGeneration = null
        mutableState.value = state.value.copy(
            isInitializing = false, needsSignIn = true, draft = null, isPlaying = false,
            errorMessage = R.string.clip_sign_in_required,
        )
    }
}
