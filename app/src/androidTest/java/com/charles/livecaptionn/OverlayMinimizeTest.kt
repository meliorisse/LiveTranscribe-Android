package com.charles.livecaptionn

import android.view.View
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Root
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.platform.app.InstrumentationRegistry
import com.charles.livecaptionn.data.SettingsRepository
import com.charles.livecaptionn.overlay.OverlayController
import com.charles.livecaptionn.overlay.OverlayUiState
import com.charles.livecaptionn.settings.CaptionSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.hamcrest.Description
import org.hamcrest.TypeSafeMatcher
import org.junit.Assert.*
import org.junit.Test

class OverlayMinimizeTest {
    @get:org.junit.Rule
    val activity = androidx.test.ext.junit.rules.ActivityScenarioRule(MainActivity::class.java)

    @Test fun collapseRestoresSizeAndLatestCaptionsAcrossRepeatedUpdates() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val settings = object : SettingsRepository {
            override val settingsFlow = MutableStateFlow(CaptionSettings())
            override suspend fun update(transform: (CaptionSettings) -> CaptionSettings) {
                settingsFlow.value = transform(settingsFlow.value)
            }
        }
        var state = OverlayUiState(originalText = "Original", transcriptText = "Translation", statusDetail = "A long status detail")
        lateinit var controller: OverlayController
        val overlayRoot = object : TypeSafeMatcher<Root>() {
            override fun describeTo(description: Description) { description.appendText("overlay window") }
            override fun matchesSafely(root: Root) = root.windowLayoutParams.get().type == WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        }
        fun button(label: String) = onView(withContentDescription(label)).inRoot(overlayRoot)
        try {
            instrumentation.runOnMainSync {
                controller = OverlayController(ApplicationProvider.getApplicationContext(), scope, settings, {}, {}, {
                    state = state.copy(minimized = !state.minimized)
                    controller.update(state)
                })
                controller.show(20, 100, 320, 260)
                controller.update(state)
            }
            var expandedHeight = 0
            button("Minimize overlay").check { view, error ->
                if (error != null) throw error
                expandedHeight = view.rootView.height
            }
            repeat(3) {
                button("Minimize overlay").perform(click())
                button("Expand overlay").check { view, error ->
                    if (error != null) throw error
                    assertTrue(view.rootView.height < expandedHeight / 2)
                }
                instrumentation.runOnMainSync {
                    state = state.copy(transcriptText = "Latest captions $it")
                    controller.update(state)
                    controller.update(state)
                }
                if (it == 0) {
                    val file = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "overlay-collapsed.png")
                    file.outputStream().use { output -> instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output) }
                }
                button("Expand overlay").perform(click())
                button("Minimize overlay").check { view, error ->
                    if (error != null) throw error
                    assertEquals(expandedHeight, view.rootView.height)
                }
                onView(androidx.test.espresso.matcher.ViewMatchers.withText("Latest captions $it"))
                    .inRoot(overlayRoot).check { view, error ->
                        if (error != null) throw error
                        assertEquals(View.VISIBLE, view.visibility)
                        assertTrue(view.isShown)
                    }
            }
        } finally {
            instrumentation.runOnMainSync { controller.hide() }
            scope.cancel()
        }
    }
}
