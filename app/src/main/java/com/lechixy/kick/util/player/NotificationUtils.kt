package com.lechixy.kick.util.player

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

import com.lechixy.kick.MainActivity
import com.lechixy.kick.R
import com.lechixy.kick.service.player.sleepTimerDisplayMinutes

fun sleepTimerRemainingText(context: Context, seconds: Int, compact: Boolean = false): String =
    sleepTimerRemainingText(context.resources, seconds, compact)

fun sleepTimerRemainingText(resources: Resources, seconds: Int, compact: Boolean = false): String {
    val totalMinutes = sleepTimerDisplayMinutes(seconds)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    val parts = buildList {
        if (hours > 0) add(if (compact) resources.getString(R.string.sleep_timer_short_hours, hours)
            else resources.getQuantityString(R.plurals.sleep_timer_hours, hours, hours))
        if (minutes > 0 || hours == 0) add(if (compact) resources.getString(R.string.sleep_timer_short_minutes, minutes)
            else resources.getQuantityString(R.plurals.sleep_timer_minutes, minutes, minutes))
    }
    return resources.getString(R.string.sleep_timer_time_left, parts.joinToString(" "))
}

object NotificationUtils {
    private const val CHANNEL_ID = "kick_sleep_timer_channel"
    // Media3 uses 1001 by default. The timer must not replace the media notification.
    internal const val NOTIFICATION_ID = 4102

    fun createNotificationChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.player_sleep_timer), NotificationManager.IMPORTANCE_LOW).apply {
            description = context.getString(R.string.sleep_timer_channel_description)
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun builder(context: Context): NotificationCompat.Builder {
        createNotificationChannel(context)
        val openPlayer = PendingIntent.getActivity(context, NOTIFICATION_ID, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.bedtime_24dp_e3e3e3_fill0_wght400_grad0_opsz24)
            .setContentTitle(context.getString(R.string.sleep_timer_notification_title))
            .setContentIntent(openPlayer)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
    }

    private inline fun safely(context: Context, action: () -> Unit) {
        // A permission denial, revoked permission, or disabled channel affects presentation only.
        runCatching {
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
            action()
        }.onFailure { Log.w("SleepTimer", "Unable to update timer notification", it) }
    }

    @SuppressLint("MissingPermission")
    fun updateTimerNotification(context: Context, remainingSeconds: Int) = safely(context) {
        val text = sleepTimerRemainingText(context, remainingSeconds)
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder(context)
            .setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text)).setOngoing(true).build())
    }

    @SuppressLint("MissingPermission")
    fun showTimerEndedNotification(context: Context) = safely(context) {
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder(context)
            .setContentText(context.getString(R.string.sleep_timer_ended)).setOngoing(false).setAutoCancel(true).build())
    }

    fun cancelTimerNotification(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID) }
    }
}
