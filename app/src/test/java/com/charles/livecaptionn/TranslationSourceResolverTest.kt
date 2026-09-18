package com.charles.livecaptionn

import com.charles.livecaptionn.translation.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class TranslationSourceResolverTest {
    private fun resolver(detector: TextLanguageDetector) = TranslationSourceResolver(detector) {
        when (it) { "es", "es-MX" -> "es"; "en" -> "en"; "vi" -> "vi"; else -> null }
    }

    @Test fun manualModeNeverCallsDetector() = runTest {
        val resolver = resolver { error("Detection must not run") }
        assertEquals(TranslationSource("en", SourceResolutionReason.MANUAL), resolver.resolve("Hola mundo", "en", false))
    }

    @Test fun confidentLanguageOverridesWrongSelectedSource() = runTest {
        val source = resolver { "es-MX" }.resolve("Hola mundo", "en", true)
        assertEquals("es", source.languageCode)
        assertEquals(SourceResolutionReason.DETECTED, source.reason)
    }

    @Test fun undeterminedAndUnsupportedLanguagesUseSelectedFallback() = runTest {
        for (tag in listOf(null, "und", "", "xx")) {
            val source = resolver { tag }.resolve("hello", "vi", true)
            assertEquals("vi", source.languageCode)
            assertNotEquals(SourceResolutionReason.DETECTED, source.reason)
        }
    }

    @Test fun digitsAndPunctuationDoNotTriggerDownloadsFromRandomGuesses() = runTest {
        val source = resolver { error("No language to detect") }.resolve("123...", "en", true)
        assertEquals(SourceResolutionReason.UNCERTAIN, source.reason)
    }

    @Test fun detectorFailureFallsBackWithoutLosingCaptions() = runTest {
        val source = resolver { throw IllegalStateException("model unavailable") }.resolve("hello", "en", true)
        assertEquals(TranslationSource("en", SourceResolutionReason.FAILED), source)
    }

    @Test fun cancellationIsNotSwallowed() = runTest {
        try {
            resolver { throw CancellationException() }.resolve("hello", "en", true)
            fail("Cancellation should propagate")
        } catch (_: CancellationException) { }
    }

    @Test fun changingLanguagesIsNotPinnedToFirstDetection() = runTest {
        val resolver = resolver { if (it.startsWith("Hola")) "es" else "vi" }
        assertEquals("es", resolver.resolve("Hola mundo", "en", true).languageCode)
        assertEquals("vi", resolver.resolve("Xin chào mọi người", "en", true).languageCode)
    }
}
