package com.charles.livecaptionn

import com.charles.livecaptionn.speech.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class BufferedLanguageSessionTest {
    private class Session : PcmSpeechSession {
        val audio = java.io.ByteArrayOutputStream()
        var closed = false
        override fun feed(pcm: ByteArray, length: Int): VoskStreamingSession.FeedResult {
            check(!closed)
            audio.write(pcm, 0, length)
            return VoskStreamingSession.FeedResult("caption", false)
        }
        override fun finish() = ""
        override fun close() { closed = true }
    }

    private class Detector(vararg labels: String?) : SpeechLanguageDetector {
        val labels = labels.toMutableList()
        var calls = 0
        var closed = false
        override fun detect(pcm: ByteArray): String? { calls++; return labels.removeAt(0) }
        override fun close() { closed = true }
    }
    private fun window(value: Int) = ByteArray(BufferedLanguageSession.WINDOW_BYTES) { value.toByte() }

    @Test fun switchesOnlyAfterAgreementAndReplaysEveryByteOnce() = runTest {
        val old = Session()
        val next = Session()
        val detector = Detector("es", "es", "es")
        val results = mutableListOf<SpeechResult>()
        val processor = BufferedLanguageSession("en", old, detector, setOf("en", "es"),
            { assertEquals("es", it); next }, results::add, {})
        processor.feed(window(1), true)
        assertEquals(0, old.audio.size())
        assertEquals(0, next.audio.size())
        processor.feed(window(2), true)
        assertTrue(old.closed)
        assertArrayEquals(window(1) + window(2), next.audio.toByteArray())
        processor.feed(window(3), true)
        assertArrayEquals(window(1) + window(2) + window(3), next.audio.toByteArray())
        assertTrue(results.all { it.sourceLanguage == "es" })
        processor.close()
        assertTrue(next.closed)
        assertTrue(detector.closed)
    }

    @Test fun conflictingGuessesAndUnavailableLanguagesStayOnFallback() = runTest {
        val old = Session()
        val processor = BufferedLanguageSession("en", old, Detector("es", "fr", "en", "ja"),
            setOf("en", "es", "fr"), { fail("No agreement; must not load a model"); null }, {}, {})
        (1..4).forEach { processor.feed(window(it), true) }
        assertArrayEquals((1..4).flatMap { window(it).asIterable() }.toByteArray(), old.audio.toByteArray())
        processor.close()
    }

    @Test fun silenceSkipsDetectionAndReleasesHeldAudio() = runTest {
        val old = Session()
        val detector = Detector("es")
        val processor = BufferedLanguageSession("en", old, detector, setOf("en", "es"), { null }, {}, {})
        processor.feed(window(1), true)
        processor.feed(window(0), false)
        assertEquals(1, detector.calls)
        assertArrayEquals(window(1) + window(0), old.audio.toByteArray())
        processor.close()
    }

    @Test fun loadFailureKeepsOldModelAndBufferedSpeech() = runTest {
        val old = Session()
        val processor = BufferedLanguageSession("en", old, Detector("es", "es"), setOf("en", "es"),
            { null }, {}, {})
        processor.feed(window(1), true)
        processor.feed(window(2), true)
        assertFalse(old.closed)
        assertArrayEquals(window(1) + window(2), old.audio.toByteArray())
        processor.close()
    }

    @Test fun detectorFailureDisablesDetectionAndContinuesCaptioning() = runTest {
        val old = Session()
        val detector = object : SpeechLanguageDetector {
            var calls = 0
            override fun detect(pcm: ByteArray): String? { calls++; error("inference failed") }
            override fun close() {}
        }
        val processor = BufferedLanguageSession("en", old, detector, setOf("en", "es"), { null }, {}, {})
        processor.feed(window(1), true)
        processor.feed(window(2), true)
        assertEquals(1, detector.calls)
        assertEquals(2 * BufferedLanguageSession.WINDOW_BYTES, old.audio.size())
        processor.close()
    }

    @Test fun manualModeDoesNotBufferOrLoadDetector() = runTest {
        val old = Session()
        val processor = BufferedLanguageSession("en", old, null, emptySet(), { null }, {}, {})
        processor.feed(ByteArray(3200) { 1 }, true)
        assertEquals(3200, old.audio.size())
        processor.close()
    }

    @Test fun pauseDiscardsHeldSpeechAndResetsAgreement() = runTest {
        val old = Session()
        val processor = BufferedLanguageSession("en", old, Detector("es", "es"), setOf("en", "es"),
            { fail("Agreement must reset"); null }, {}, {})
        processor.feed(window(1), true)
        processor.discardPending()
        processor.feed(window(2), true)
        assertEquals(0, old.audio.size())
        processor.finish()
        assertArrayEquals(window(2), old.audio.toByteArray())
        processor.close()
    }

    @Test fun partialWindowIsFlushedOnFinish() = runTest {
        val old = Session()
        val processor = BufferedLanguageSession("en", old, Detector(), setOf("en", "es"), { null }, {}, {})
        processor.feed(ByteArray(3200) { 7 }, true)
        assertEquals(0, old.audio.size())
        processor.finish()
        assertArrayEquals(ByteArray(3200) { 7 }, old.audio.toByteArray())
        processor.close()
    }

    @Test fun cancellationIsNotTreatedAsDetectorFailure() = runTest {
        val old = Session()
        val detector = object : SpeechLanguageDetector {
            override fun detect(pcm: ByteArray): String? = throw CancellationException()
            override fun close() {}
        }
        val processor = BufferedLanguageSession("en", old, detector, setOf("en", "es"), { null }, {}, {})
        try { processor.feed(window(1), true); fail("Expected cancellation") }
        catch (_: CancellationException) { assertEquals(0, old.audio.size()) }
        finally { processor.close() }
    }
    @Test fun cancellationAfterModelLoadClosesBothOwnedSessions() = runTest {
        val old = Session()
        val next = Session()
        val processor = BufferedLanguageSession("en", old, Detector("es", "es"), setOf("en", "es"),
            { currentCoroutineContext().cancel(); next }, {}, {})
        val worker = launch {
            try {
                processor.feed(window(1), true)
                processor.feed(window(2), true)
            } finally {
                processor.close()
            }
        }
        worker.join()
        assertTrue(old.closed)
        assertTrue(next.closed)
        assertEquals(0, next.audio.size())
    }

}
