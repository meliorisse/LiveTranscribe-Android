package com.charles.livecaptionn

import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.charles.livecaptionn.data.SettingsDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class OverlayPreviewControlsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun controlsPersistSizeAndUpdateNativePreview() {
        val store = SettingsDataStore(compose.activity)
        runBlocking {
            store.update { it.copy(onboardingComplete = true, borderlineWarningDismissed = true,
                textSizeSp = 20f, showOriginal = true, autoDetectSource = false) }
        }
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("Caption preview").fetchSemanticsNodes().isNotEmpty()
        }
        fun verifySize(size: Float) {
            compose.waitUntil(5_000) { runBlocking { store.settingsFlow.first().textSizeSp == size } }
            compose.waitForIdle()
            compose.waitUntil(5_000) {
                var matches = false
                compose.runOnUiThread {
                fun find(view: View): TextView? {
                    if (view is TextView && view.text.startsWith("Translated captions appear here.")) return view
                    if (view is ViewGroup) for (i in 0 until view.childCount) find(view.getChildAt(i))?.let { return it }
                    return null
                }
                val preview = checkNotNull(find(compose.activity.window.decorView))
                val expected = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, size, preview.resources.displayMetrics)
                matches = kotlin.math.abs(expected - preview.textSize) < 0.1f
                }
                matches
            }
        }
        compose.onNodeWithContentDescription("Larger caption text").performScrollTo().performClick()
        verifySize(21f)
        compose.onNodeWithContentDescription("Smaller caption text").performClick()
        verifySize(20f)
        compose.onNodeWithContentDescription("Caption text size").performSemanticsAction(SemanticsActions.SetProgress) { it(40f) }
        verifySize(40f)
        compose.onNodeWithContentDescription("Larger caption text").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Caption text size").performSemanticsAction(SemanticsActions.SetProgress) { it(14f) }
        verifySize(14f)
        compose.onNodeWithContentDescription("Smaller caption text").assertIsNotEnabled()
        compose.onNodeWithText("Reset size").performClick()
        verifySize(20f)
    }
}
