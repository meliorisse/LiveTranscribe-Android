package com.charles.livecaptionn.translation

import kotlinx.coroutines.CancellationException

fun interface TextLanguageDetector {
    /** Returns a confident BCP-47 language tag, or null when undetermined. */
    suspend fun detect(text: String): String?
}

enum class SourceResolutionReason { MANUAL, DETECTED, UNCERTAIN, UNSUPPORTED, FAILED }

data class TranslationSource(
    val languageCode: String,
    val reason: SourceResolutionReason,
    val detectedLanguage: String? = null
)

/** Keeps an uncertain guess from overriding the user's explicit fallback language. */
class TranslationSourceResolver(
    private val detector: TextLanguageDetector,
    private val supportedLanguage: (String) -> String?
) {
    suspend fun resolve(text: String, selectedLanguage: String, autoDetect: Boolean): TranslationSource {
        if (!autoDetect) return TranslationSource(selectedLanguage, SourceResolutionReason.MANUAL)
        if (text.none { it.isLetter() }) {
            return TranslationSource(selectedLanguage, SourceResolutionReason.UNCERTAIN)
        }
        val detected = try {
            detector.detect(text)?.takeUnless { it.isBlank() || it == "und" }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return TranslationSource(selectedLanguage, SourceResolutionReason.FAILED)
        } ?: return TranslationSource(selectedLanguage, SourceResolutionReason.UNCERTAIN)
        val supported = supportedLanguage(detected)
            ?: return TranslationSource(selectedLanguage, SourceResolutionReason.UNSUPPORTED, detected)
        return TranslationSource(supported, SourceResolutionReason.DETECTED, detected)
    }
}
