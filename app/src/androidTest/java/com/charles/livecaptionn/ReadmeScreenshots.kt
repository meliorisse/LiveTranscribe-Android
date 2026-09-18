package com.charles.livecaptionn

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.charles.livecaptionn.data.TranscriptEntry
import com.charles.livecaptionn.overlay.OverlayController
import com.charles.livecaptionn.overlay.OverlayUiState
import com.charles.livecaptionn.settings.SttBackend
import com.charles.livecaptionn.speech.RecognitionStatus
import kotlinx.coroutines.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Opt-in screenshot fixture for a disposable emulator. It replaces demo history. */
class ReadmeScreenshots {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun capture() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("readmeScreenshots") == "true")
        val appearance = arguments.getString("appearance") ?: "light"
        val app = compose.activity.application as LiveCaptionApp
        val container = app.container
        runBlocking {
            container.voskRegistry.refresh()
            for (code in listOf("en", "vi")) {
                if (code !in container.voskRegistry.installedLanguageCodes()) {
                    val model = container.voskRegistry.models.value.first {
                        it.languageCode == code && it.quality == com.charles.livecaptionn.speech.ModelQuality.SMALL
                    }
                    check(container.voskRegistry.downloadAndInstall(model))
                }
            }
            container.settingsRepository.update { it.copy(
                onboardingComplete = true, borderlineWarningDismissed = true,
                textSizeSp = 20f, showOriginal = true, overlayOpacity = 0.65f,
                overlayThemeId = "default", overlayFontId = "default",
                sttBackend = SttBackend.LOCAL_VOSK, autoDetectSource = false,
                sourceLanguageCode = "en", targetLanguageCode = "vi", uiLanguageCode = "en"
            ) }
            container.transcriptHistory.clear()
            listOf(
                "Welcome. Today we will explore something new." to "Chào mừng. Hôm nay chúng ta sẽ khám phá điều mới.",
                "You can follow the conversation in your own language." to "Bạn có thể theo dõi cuộc trò chuyện bằng ngôn ngữ của mình.",
                "Take your time. Every small step makes a difference." to "Cứ từ từ. Mỗi bước nhỏ đều tạo nên sự khác biệt."
            ).forEachIndexed { i, pair ->
                container.transcriptHistory.add(TranscriptEntry(
                    timestamp = 1789731000000L + i * 60000,
                    originalText = pair.first, translatedText = pair.second,
                    sourceLanguage = "en", targetLanguage = "vi"
                ))
            }
        }
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("Audio Source").fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
        fun capture(name: String, waitForCompose: Boolean = true) {
            if (waitForCompose) compose.waitForIdle()
            // Allow the window compositor/status bars to settle after scrolling.
            Thread.sleep(400)
            val file = File(compose.activity.getExternalFilesDir(null), "readme/$name-$appearance.png")
            file.parentFile!!.mkdirs()
            checkNotNull(instrumentation.uiAutomation.takeScreenshot()).let { bitmap ->
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        }
        capture("main")
        compose.onNodeWithText("Auto-detect language").performScrollTo()
        compose.onNode(SemanticsMatcher.keyIsDefined(
            androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange
        )).performSemanticsAction(
            androidx.compose.ui.semantics.SemanticsActions.ScrollBy
        ) { scroll -> scroll(0f, 480 * compose.activity.resources.displayMetrics.density) }
        capture("languages")
        compose.onNodeWithText("Reset size").performScrollTo()
        compose.onNode(SemanticsMatcher.keyIsDefined(
            androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange
        )).performSemanticsAction(
            androidx.compose.ui.semantics.SemanticsActions.ScrollBy
        ) { scroll -> scroll(0f, 300 * compose.activity.resources.displayMetrics.density) }
        capture("caption-preview")
        compose.onNodeWithContentDescription("History").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Transcript History").fetchSemanticsNodes().isNotEmpty()
        }
        capture("history")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val overlay = OverlayController(compose.activity, scope, container.settingsRepository, {}, {}, {})
        val sampleOverlay = OverlayUiState(
            originalText = "You can follow the conversation in your own language.",
            transcriptText = "Bạn có thể theo dõi cuộc trò chuyện bằng ngôn ngữ của mình.",
            status = RecognitionStatus.LISTENING, opacity = 0.9f
        )
        try {
            compose.runOnIdle {
                val density = compose.activity.resources.displayMetrics.density
                overlay.show((24 * density).toInt(), (600 * density).toInt(), 360, 220)
                overlay.update(sampleOverlay)
            }
            capture("overlay")
            instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_HOME").close()
            Thread.sleep(800)
            instrumentation.runOnMainSync {
                val density = compose.activity.resources.displayMetrics.density
                overlay.hide()
                overlay.show((24 * density).toInt(), (230 * density).toInt(), 360, 220)
                overlay.update(sampleOverlay)
            }
            capture("overlay-home", waitForCompose = false)
        } finally {
            instrumentation.runOnMainSync { overlay.hide() }
            scope.cancel()
        }
    }
}
