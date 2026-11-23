package com.lechixy.kick.components.settings

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.lechixy.kick.R
import com.lechixy.kick.data.settings.model.SettingsKeys

@Composable
internal fun VideoNoiseReductionSetting() {
    val supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    SettingSwitch(
        key = SettingsKeys.VIDEO_NOISE_REDUCTION,
        title = stringResource(R.string.player_video_noise_reduction),
        description = stringResource(
            if (supported) R.string.player_video_noise_reduction_description
            else R.string.player_video_noise_reduction_requires_android_13
        ),
        icon = R.drawable.deblur_24dp_e3e3e3_fill0_wght400_grad0_opsz24,
        enabled = supported,
        badge = stringResource(R.string.player_experimental),
    )
}
