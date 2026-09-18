package com.charles.livecaptionn

import com.charles.livecaptionn.speech.whisperTranscriptionUrl
import org.junit.Assert.*
import org.junit.Test

class WhisperLanguageTest {
    @Test fun autoDetectRemovesStaleLanguageAndRequestsTranscriptionJson() {
        val url = whisperTranscriptionUrl("https://example.com/asr?language=en&task=translate&output=txt", null)
        assertNull(url.queryParameter("language"))
        assertEquals("transcribe", url.queryParameter("task"))
        assertEquals("json", url.queryParameter("output"))
    }

    @Test fun manualLanguageReplacesExistingValueWithoutDuplicates() {
        val url = whisperTranscriptionUrl("https://example.com/asr?language=en&language=fr&custom=1", "vi")
        assertEquals(listOf("vi"), url.queryParameterValues("language"))
        assertEquals("1", url.queryParameter("custom"))
    }

    @Test fun blankOrAutoDoesNotPinLanguage() {
        for (language in listOf("", " ", "auto")) {
            assertNull(whisperTranscriptionUrl("https://example.com/asr", language).queryParameter("language"))
        }
    }
}
