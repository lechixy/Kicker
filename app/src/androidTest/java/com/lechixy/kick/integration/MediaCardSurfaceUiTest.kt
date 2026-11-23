package com.lechixy.kick.integration

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.lechixy.kick.components.VideoCard
import com.lechixy.kick.data.channel.model.ChannelVideo
import com.lechixy.kick.theme.KickTheme
import com.lechixy.kick.theme.Theme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MediaCardSurfaceUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun videoCardPaddingKeepsTheCardColorAcrossThemes() {
        var theme by mutableStateOf(Theme.LIGHT)
        var dynamicColor by mutableStateOf(false)
        val video = ChannelVideo(id = 1, sessionTitle = "Test recording", duration = 60_000)
        compose.setContent {
            KickTheme(theme, dynamicColor) {
                val colors = MaterialTheme.colorScheme
                val expectedFill = colors.surfaceVariant.copy(alpha = .3f).compositeOver(colors.surface)
                Box(Modifier.fillMaxSize().background(colors.surface).padding(16.dp)) {
                    VideoCard(video, Modifier.width(160.dp).testTag("video-card"))
                    Box(Modifier.align(Alignment.TopEnd).size(24.dp).background(expectedFill).testTag("card-fill"))
                }
            }
        }

        for (useDynamicColor in listOf(false, true)) {
            for (appearance in listOf(Theme.LIGHT, Theme.DARK, Theme.AMOLED)) {
                compose.runOnIdle {
                    theme = appearance
                    dynamicColor = useDynamicColor
                }
                val pixels = compose.onNodeWithTag("video-card").captureToImage().toPixelMap()
                val inset = pixels.width / 20
                val y = pixels.height - inset
                val expected = compose.onNodeWithTag("card-fill").captureToImage().toPixelMap()[0, 0]
                for (x in listOf(inset, pixels.width / 2, pixels.width - inset - 1)) {
                    val color = pixels[x, y]
                    val where = "$appearance, dynamicColor=$useDynamicColor, x=$x"
                    assertEquals(where, expected.red, color.red, 2f / 255)
                    assertEquals(where, expected.green, color.green, 2f / 255)
                    assertEquals(where, expected.blue, color.blue, 2f / 255)
                }
            }
        }
    }
}
