package com.lechixy.kick.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.lechixy.kick.R
import com.lechixy.kick.data.settings.repository.WinterWelcome
import com.lechixy.kick.theme.KickTheme
import com.lechixy.kick.theme.Theme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WinterAndBadgeUiTest {
    @get:Rule val compose = createComposeRule()
    private val resources get() = InstrumentationRegistry.getInstrumentation().targetContext.resources

    @Test
    fun verifiedAndFavoriteBadgesOpenTooltipsOnHoverAndTap() {
        compose.setContent {
            KickTheme(Theme.LIGHT, dynamicColor = false) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    VerifiedBadge()
                    DeveloperFavoriteBadge(listOf(Color.Green, Color.Blue))
                }
            }
        }
        val verified = resources.getString(R.string.badge_verified)
        val favorite = resources.getString(R.string.badge_developer_favorite, "@lechixy")
        compose.onNodeWithContentDescription(verified).performMouseInput { enter(); moveTo(center) }
        compose.onNodeWithText(verified).assertExists()
        compose.onNodeWithContentDescription(verified).performMouseInput { exit() }
        compose.onNodeWithContentDescription(favorite).performClick()
        compose.onNodeWithText(favorite).assertExists()
    }

    @Test
    fun winterDialogOffersTurnOffAndSnowCelebration() {
        var turnedOff = false
        var celebrated = false
        compose.setContent {
            KickTheme(Theme.LIGHT, dynamicColor = false) {
                WinterWelcomeDialog(WinterWelcome.OffSeason, {}, { turnedOff = true }, { celebrated = true })
            }
        }
        compose.onNodeWithText(resources.getString(R.string.winter_offseason_title)).assertExists()
        compose.onNodeWithText(resources.getString(R.string.winter_turn_off)).performClick()
        compose.onNodeWithText(resources.getString(R.string.winter_celebrate)).performClick()
        compose.runOnIdle { assertTrue(turnedOff && celebrated) }
    }
}
