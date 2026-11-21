package com.lechixy.kick.integration

import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.lechixy.kick.R
import com.lechixy.kick.components.settings.SettingTextField
import com.lechixy.kick.data.settings.model.SettingsKeys
import com.lechixy.kick.data.settings.repository.LocalSettingsRepository
import com.lechixy.kick.data.settings.repository.SettingsRepository
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingTextFieldUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun fieldMovesBelowTheDescriptionWithoutLosingFocusOrDraft() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = File(context.cacheDir, "text-field-${System.nanoTime()}.preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val repository = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { preferences })
        var width by mutableStateOf(520.dp)
        val description = "Use a two-letter language code"
        try {
            compose.setContent {
                CompositionLocalProvider(LocalSettingsRepository provides repository) {
                    MaterialTheme {
                        SettingTextField(
                            key = SettingsKeys.RECOMMENDED_LIVESTREAM_LANGUAGE.copy(defaultProvider = { "tr" }),
                            title = "Language",
                            description = description,
                            modifier = Modifier.width(width),
                        )
                    }
                }
            }
            val field = compose.onNode(hasSetTextAction())
            val descriptionNode = compose.onNodeWithText(description)
            assertTrue(field.fetchSemanticsNode().boundsInRoot.left > descriptionNode.fetchSemanticsNode().boundsInRoot.right)
            field.performTextReplacement("en")
            compose.runOnIdle { width = 240.dp }
            assertTrue(field.fetchSemanticsNode().boundsInRoot.top > descriptionNode.fetchSemanticsNode().boundsInRoot.bottom)
            field.assertIsFocused().assertTextContains("en")
            val reset = compose.onNodeWithContentDescription(context.getString(R.string.settings_reset_value, "Language"))
            reset.performClick()
            field.assertTextContains("tr")
            reset.assertDoesNotExist()
        } finally {
            scope.cancel()
            preferences.delete()
        }
    }
}
