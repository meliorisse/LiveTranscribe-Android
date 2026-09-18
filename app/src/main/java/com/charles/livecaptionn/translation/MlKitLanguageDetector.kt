package com.charles.livecaptionn.translation

import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import kotlinx.coroutines.tasks.await

/** The small language-ID model is bundled in the APK, so detection also works offline. */
class MlKitLanguageDetector : TextLanguageDetector {
    private val identifier = lazy {
        LanguageIdentification.getClient(
            LanguageIdentificationOptions.Builder().setConfidenceThreshold(0.7f).build()
        )
    }

    override suspend fun detect(text: String): String? =
        identifier.value.identifyLanguage(text).await().takeUnless { it == "und" }

    fun close() {
        if (identifier.isInitialized()) identifier.value.close()
    }
}
