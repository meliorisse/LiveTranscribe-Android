package com.charles.livecaptionn

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.charles.livecaptionn.data.SettingsDataStore
import com.charles.livecaptionn.settings.SttBackend
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VoskDetectionSettingsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun detectorControlsAppearAndLanguageSelectionPersists() {
        val store = SettingsDataStore(compose.activity)
        runBlocking {
            store.update { it.copy(
                onboardingComplete = true,
                borderlineWarningDismissed = true,
                sttBackend = SttBackend.LOCAL_VOSK,
                autoDetectSource = true,
                voskDetectionLanguages = setOf("en", "de")
            ) }
            assertEquals(setOf("en", "de"), store.settingsFlow.first().voskDetectionLanguages)
        }
        compose.waitUntil(15_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasText("Offline speech-language detector"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Offline speech-language detector").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Enabled speech languages (none selected means all installed):")
            .performScrollTo().assertIsDisplayed()
    }
}
