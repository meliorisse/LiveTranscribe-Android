package com.charles.livecaptionn

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.charles.livecaptionn.translation.MlKitLanguageDetector
import com.charles.livecaptionn.translation.MlKitTranslationRepository
import com.charles.livecaptionn.translation.SourceResolutionReason
import com.charles.livecaptionn.translation.TranslationSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MlKitAutoDetectTest {
    @Test
    fun bundledDetectorRecognizesDifferentLanguages() = runBlocking {
        val detector = MlKitLanguageDetector()
        try {
            assertEquals("en", detector.detect("The weather is beautiful today and I would like to go for a walk."))
            assertEquals("es", detector.detect("Me gustaría reservar una mesa para cenar esta noche con mi familia."))
            assertEquals("vi", detector.detect("Tôi muốn học tiếng Việt để có thể nói chuyện với bạn bè của tôi."))
        } finally {
            detector.close()
        }
    }

    @Test
    fun detectionRunsBeforeSameLanguageShortcut() = runBlocking {
        var source: TranslationSource? = null
        val repository = MlKitTranslationRepository { source = it }
        val text = "Me gustaría reservar una mesa para cenar esta noche con mi familia."
        try {
            // Resolving Spanish first avoids downloading an incorrect English-to-Spanish model.
            assertEquals(text, repository.translate(text, "en", "es", true))
            assertEquals("es", source?.languageCode)
            assertEquals(SourceResolutionReason.DETECTED, source?.reason)
        } finally {
            repository.close()
        }
    }
}
