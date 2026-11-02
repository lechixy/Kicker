package com.lechixy.kick.components

import android.content.Context
import android.media.AudioManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.lechixy.kick.util.player.MediaVolumeDevice
import com.lechixy.kick.util.player.PlayerVolumeController
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.time.Duration.Companion.milliseconds

@Composable
internal fun rememberPlayerVolume(syncWithDevice: Boolean): PlayerVolumeController {
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val controller = remember(context) {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        PlayerVolumeController(object : MediaVolumeDevice {
            override val fixed get() = audio.isVolumeFixed
            override val minimum get() = audio.getStreamMinVolume(AudioManager.STREAM_MUSIC)
            override val maximum get() = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            override var volume: Int
                get() = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                set(value) { audio.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0) }
        })
    }
    LaunchedEffect(controller, syncWithDevice, lifecycle) {
        // Enabling sync adopts device volume without writing to system settings.
        controller.setSyncEnabled(syncWithDevice)
        if (syncWithDevice) lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            // Public AudioManager has no per-stream volume callback. Poll only while visible,
            // covering hardware keys, Bluetooth routes and external volume controls.
            while (isActive) {
                controller.refresh()
                delay(250.milliseconds)
            }
        }
    }
    return controller
}
