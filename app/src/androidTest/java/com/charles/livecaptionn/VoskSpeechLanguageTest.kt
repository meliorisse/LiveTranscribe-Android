package com.charles.livecaptionn

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.charles.livecaptionn.speech.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Opt-in native integration tests. Place 16kHz mono PCM fixtures named en.pcm and
 * de.pcm in targetContext.filesDir/language-id-test (see scripts/test-language-id.py).
 * Downloads the production detector and German Vosk model when absent.
 */
@RunWith(AndroidJUnit4::class)
class VoskSpeechLanguageTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun fixture(language: String): ByteArray {
        val file = File(context.filesDir, "language-id-test/$language.pcm")
        assumeTrue("Optional speech fixture is not installed", file.isFile)
        return file.readBytes()
    }

    @Test fun detectorDownloadsVerifiedAssetsAndRecognizesRealSpeech() = runBlocking {
        val english = fixture("en")
        val german = fixture("de")
        val store = SpokenLanguageModelStore(context)
        withTimeout(240_000) { store.download() }
        assertTrue(store.state.value.error, store.isInstalled())
        OfflineSpeechLanguageDetector(store.directory).use { detector ->
            assertEquals("en", detector.detect(english))
            assertEquals("de", detector.detect(german))
        }
    }

    @Test fun bufferedAudioSwitchesRealVoskModelsAndKeepsNativeSessionsAlive() = runBlocking {
        val english = fixture("en")
        val german = fixture("de")
        val store = SpokenLanguageModelStore(context)
        withTimeout(240_000) { store.download() }
        assertTrue(store.state.value.error, store.isInstalled())
        val registry = VoskModelRegistry(context)
        registry.refresh()
        val deModel = registry.models.value.first { it.languageCode == "de" && it.quality == ModelQuality.SMALL }
        if (!deModel.installed) assertTrue(registry.downloadAndInstall(deModel))
        val enModel = registry.models.value.first { it.languageCode == "en" && it.quality == ModelQuality.SMALL }
        if (!enModel.installed) assertTrue(registry.downloadAndInstall(enModel))
        val client = LocalVoskSttClient(registry)
        val englishSession = checkNotNull(client.openSession("en", 16000))
        // Opening another model must not invalidate an existing native recognizer.
        checkNotNull(client.openSession("de", 16000)).use { other ->
            assertNotNull(englishSession.feed(english))
            assertNotNull(other.feed(german))
        }
        englishSession.finish()
        val results = mutableListOf<SpeechResult>()
        BufferedLanguageSession("en", englishSession, OfflineSpeechLanguageDetector(store.directory),
            setOf("en", "de"), { client.openSession(it, 16000) }, results::add, {}).use { processor ->
            fun fourSeconds(pcm: ByteArray) = ByteArray(BufferedLanguageSession.WINDOW_BYTES) { pcm[it % pcm.size] }
            val deWindow = fourSeconds(german)
            processor.feed(deWindow, true)
            processor.feed(deWindow, true)
            processor.feed(ByteArray(BufferedLanguageSession.WINDOW_BYTES), false)
            assertTrue("Expected German captions after switching", results.any { it.sourceLanguage == "de" && it.text.isNotBlank() })
            val enWindow = fourSeconds(english)
            processor.feed(enWindow, true)
            processor.feed(enWindow, true)
            processor.finish()
            assertTrue("Expected English captions after switching back", results.any { it.sourceLanguage == "en" && it.text.isNotBlank() })
        }
    }
}
