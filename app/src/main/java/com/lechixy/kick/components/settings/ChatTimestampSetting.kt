package com.lechixy.kick.components.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.lechixy.kick.R
import com.lechixy.kick.data.chat.model.ChatTimestampMode
import com.lechixy.kick.data.settings.model.SettingsKeys

@Composable
internal fun ChatTimestampSetting() {
    SettingDropdown(key = SettingsKeys.CHAT_TIMESTAMPS, title = stringResource(R.string.chat_show_timestamps),
        icon = R.drawable.timer_24dp_e3e3e3_fill0_wght400_grad0_opsz24,
        options = ChatTimestampMode.entries.map { mode -> mode.value to stringResource(when (mode) {
            ChatTimestampMode.LIVE_AND_VIDEO -> R.string.chat_timestamps_live_and_video
            ChatTimestampMode.ONLY_LIVE -> R.string.chat_timestamps_only_live
            ChatTimestampMode.ONLY_VIDEO -> R.string.chat_timestamps_only_video
            ChatTimestampMode.DONT_SHOW -> R.string.chat_timestamps_dont_show
        }) })
}
