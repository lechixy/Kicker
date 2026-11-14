package com.lechixy.kick.screens.you

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import com.lechixy.kick.components.account.AccountAvatar
import com.lechixy.kick.data.common.remote.KickNetwork
import com.lechixy.kick.data.common.repository.KickRepository
import kotlinx.coroutines.CancellationException

@Composable
internal fun UsageChannelAvatar(slug: String?, name: String, avatar: String?, modifier: Modifier = Modifier) {
    if (!avatar.isNullOrBlank() || slug.isNullOrBlank()) {
        AccountAvatar(avatar, name, modifier)
    } else {
        val resolved by produceState<String?>(null, slug) {
            value = null
            value = try {
                KickRepository(KickNetwork.api).getChannel(slug).user?.profilePic
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        }
        AccountAvatar(resolved, name, modifier)
    }
}
