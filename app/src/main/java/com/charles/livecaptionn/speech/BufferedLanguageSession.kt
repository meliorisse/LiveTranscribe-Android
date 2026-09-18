package com.charles.livecaptionn.speech

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.ByteArrayOutputStream
import java.io.Closeable

/** Native recognition is owned and used only by the processing coroutine. */
interface PcmSpeechSession : Closeable {
    fun feed(pcm: ByteArray, length: Int = pcm.size): VoskStreamingSession.FeedResult
    fun finish(): String
}

/**
 * Automatic mode buffers independent four-second windows. A suspected change holds
 * one window until the next decision, then feeds each byte exactly once to the chosen
 * recognizer. The capture coroutine keeps recording while detection/model loading runs.
 */
class BufferedLanguageSession(
    initialLanguage: String,
    private var session: PcmSpeechSession,
    private var detector: SpeechLanguageDetector?,
    allowedLanguages: Set<String>,
    private val openSession: suspend (String) -> PcmSpeechSession?,
    private val onResult: (SpeechResult) -> Unit,
    private val onNotice: (String) -> Unit
) : Closeable {
    private val policy = VoskLanguageSwitchPolicy(initialLanguage, allowedLanguages)
    private val window = ByteArrayOutputStream(WINDOW_BYTES)
    private var held: ByteArray? = null
    private var lastPartial = ""
    private var voicedBytes = 0
    private var closed = false

    suspend fun feed(pcm: ByteArray, voiced: Boolean) {
        check(!closed)
        if (detector == null) { emit(pcm); return }
        var offset = 0
        while (offset < pcm.size) {
            val length = minOf(WINDOW_BYTES - window.size(), pcm.size - offset)
            window.write(pcm, offset, length)
            if (voiced) voicedBytes += length
            offset += length
            if (window.size() == WINDOW_BYTES) {
                val bytes = window.toByteArray()
                window.reset()
                val enoughSpeech = voicedBytes >= MIN_VOICED_BYTES
                voicedBytes = 0
                processWindow(bytes, enoughSpeech)
            }
        }
    }

    private suspend fun processWindow(bytes: ByteArray, enoughSpeech: Boolean) {
        val language = try {
            if (enoughSpeech) detector?.detect(bytes) else null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            disableDetector()
            onNotice("Speech-language detection failed; continuing with ${policy.activeLanguage}. Restart captioning to retry.")
            null
        } catch (e: LinkageError) {
            disableDetector()
            onNotice("Speech-language detector unavailable on this device; using ${policy.activeLanguage}.")
            null
        }
        currentCoroutineContext().ensureActive()
        val decision = policy.observe(language)
        when (decision.action) {
            VoskLanguageSwitchPolicy.Action.HOLD -> {
                flushHeld()
                held = bytes
                onNotice("Checking spoken language: ${decision.language}…")
            }
            VoskLanguageSwitchPolicy.Action.SWITCH -> {
                val nextLanguage = checkNotNull(decision.language)
                val next = openSession(nextLanguage)
                try {
                    currentCoroutineContext().ensureActive()
                } catch (e: CancellationException) {
                    next?.close()
                    throw e
                }
                if (next == null) {
                    onNotice("Could not load Vosk model for $nextLanguage; keeping ${policy.activeLanguage}.")
                } else {
                    session.finish().takeIf { it.isNotBlank() }?.let { onResult(SpeechResult(it, true, policy.activeLanguage)) }
                    session.close()
                    session = next
                    policy.switched(nextLanguage)
                    lastPartial = ""
                    onNotice("Vosk spoken language: $nextLanguage")
                }
                flushHeld()
                emit(bytes)
            }
            else -> {
                if (decision.action == VoskLanguageSwitchPolicy.Action.UNAVAILABLE) {
                    onNotice("Detected ${decision.language}, but it is not enabled with an installed Vosk model; keeping ${policy.activeLanguage}.")
                } else if (language != null) {
                    onNotice("Vosk spoken language: ${policy.activeLanguage}")
                } else if (detector != null) {
                    onNotice("Waiting for clear speech; keeping ${policy.activeLanguage}.")
                }
                flushHeld()
                emit(bytes)
            }
        }
    }

    private fun emit(pcm: ByteArray) {
        // Preserve Vosk's usual 100ms chunking when replaying buffered speech.
        var offset = 0
        while (offset < pcm.size) {
            val end = minOf(offset + 3200, pcm.size)
            val result = session.feed(pcm.copyOfRange(offset, end))
            if (result.accepted) {
                if (result.text.isNotBlank()) onResult(SpeechResult(result.text, true, policy.activeLanguage))
                lastPartial = ""
            } else if (result.text.isNotBlank() && result.text != lastPartial) {
                onResult(SpeechResult(result.text, false, policy.activeLanguage))
                lastPartial = result.text
            }
            offset = end
        }
    }

    private fun flushHeld() { held?.let(::emit); held = null }
    private fun disableDetector() { detector?.close(); detector = null }

    /** Pausing discards uncommitted speech instead of carrying it into another session. */
    fun discardPending() {
        held = null
        window.reset()
        voicedBytes = 0
        policy.reset()
        // finalResult resets the recognizer; discard any partial from before the pause.
        session.finish()
        lastPartial = ""
    }

    fun finish() {
        if (closed) return
        flushHeld()
        if (window.size() > 0) emit(window.toByteArray())
        window.reset()
        session.finish().takeIf { it.isNotBlank() }?.let { onResult(SpeechResult(it, true, policy.activeLanguage)) }
    }

    override fun close() {
        if (closed) return
        closed = true
        try { session.close() } finally { detector?.close() }
    }

    companion object {
        const val WINDOW_BYTES = 16000 * 2 * 4
        private const val MIN_VOICED_BYTES = 16000 * 2 // one second above the capture silence threshold
    }
}
