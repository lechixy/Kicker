package com.lechixy.kick.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class SkeletonLoadingUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun separateBlocksShareOneDiagonalBeamInLightTheme() = verifyDiagonalBeam(darkTheme = false)

    @Test fun separateBlocksShareOneDiagonalBeamInDarkTheme() = verifyDiagonalBeam(darkTheme = true)

    private fun verifyDiagonalBeam(darkTheme: Boolean) {
        compose.activityRule.scenario.onActivity {
            it.setShowWhenLocked(true)
            it.setTurnScreenOn(true)
        }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme(colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()) {
                SkeletonLoadingScope(active = true, modifier = Modifier.size(200.dp).testTag("skeleton")) {
                    Column {
                        SkeletonBlock(Modifier.fillMaxWidth().height(80.dp), RectangleShape)
                        Spacer(Modifier.height(40.dp))
                        SkeletonBlock(Modifier.fillMaxWidth().height(80.dp), RectangleShape)
                    }
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(900)
        val pixels = compose.onNodeWithTag("skeleton").captureToImage().toPixelMap()
        fun brightness(x: Float, y: Float): Float {
            val color = pixels[(pixels.width * x).toInt(), (pixels.height * y).toInt()]
            return (color.red + color.green + color.blue) / 3f
        }
        // These points have equal screen x + y, but different positions in their own blocks.
        val upperBeam = brightness(0.75f, 0.25f)
        val lowerBeam = brightness(0.15f, 0.85f)
        val outsideBeam = brightness(0.05f, 0.05f)
        assertTrue("Separate blocks must share a diagonal highlight", abs(upperBeam - lowerBeam) < 0.015f)
        assertTrue("The beam must be brighter than the surrounding skeleton", upperBeam > outsideBeam + 0.02f)

        compose.mainClock.advanceTimeBy(350)
        val moved = compose.onNodeWithTag("skeleton").captureToImage().toPixelMap()
        val oldPoint = moved[(moved.width * 0.75f).toInt(), (moved.height * 0.25f).toInt()]
        val movedBrightness = (oldPoint.red + oldPoint.green + oldPoint.blue) / 3f
        assertTrue("The beam must travel across the screen", upperBeam > movedBrightness + 0.02f)
    }
}
