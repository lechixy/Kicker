package com.lechixy.kick

import android.app.Application
import com.lechixy.kick.data.settings.local.SettingsBackupManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class KickApplication : Application() {
    @Inject lateinit var settingsBackupManager: SettingsBackupManager

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch { settingsBackupManager.runAutomaticBackup() }
        com.lechixy.kick.data.favorites.local.FavoriteUsersStore.init(this)
        com.lechixy.kick.data.account.AccountGraph.init(this)
        com.lechixy.kick.data.player.local.PlaybackProgressStore.init(this)
        com.lechixy.kick.data.player.local.WatchHistoryStore.init(this)
        com.lechixy.kick.data.player.local.RecordingDownloadQueue.init(this)
        com.lechixy.kick.data.player.local.PlaybackUsageStore.init(this)
        com.lechixy.kick.data.common.remote.NetworkMonitor.init(this)
    }
}
