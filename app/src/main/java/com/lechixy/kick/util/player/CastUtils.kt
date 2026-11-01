package com.lechixy.kick.util.player

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.net.toUri

object CastUtils {

    const val WVC_PACKAGE_NAME = "com.instantbits.cast.webvideo"

    fun launchWebVideoCaster(
        context: Context,
        playbackUrl: String,
        streamTitle: String = "",
        channelName: String = "",
        posterUrl: String? = null
    ) {
        if (playbackUrl.isBlank()) return

        val title = streamTitle.ifBlank { channelName.ifBlank { "Kick Canlı Yayın" } }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(playbackUrl.toUri(), "application/x-mpegurl")
            setPackage(WVC_PACKAGE_NAME)
            putExtra("title", title)
            if (!posterUrl.isNullOrBlank()) {
                putExtra("poster", posterUrl)
            }
            val headers = Bundle().apply {
                putString("Origin", "https://kick.com")
                putString("Referer", "https://kick.com/")
            }
            putExtra("android.media.intent.extra.HTTP_HEADERS", headers)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        runCatching {
            context.startActivity(intent)
        }.onFailure {
            // Web Video Caster yüklü değilse Play Store sayfasına yönlendir
            val playStoreIntent = Intent(
                Intent.ACTION_VIEW,
                "market://details?id=$WVC_PACKAGE_NAME".toUri()
            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }

            runCatching {
                context.startActivity(playStoreIntent)
            }.onFailure {
                val browserIntent = Intent(
                    Intent.ACTION_VIEW,
                    "https://play.google.com/store/apps/details?id=${WVC_PACKAGE_NAME}".toUri()
                ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                runCatching { context.startActivity(browserIntent) }
            }
        }
    }
}
