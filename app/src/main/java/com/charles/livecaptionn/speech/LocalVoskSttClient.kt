package com.charles.livecaptionn.speech

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer

/**
 * Each streaming session owns its model. Switching or stopping one session must never
 * free a native model still in use by another session.
 */
class LocalVoskSttClient(private val registry: VoskModelRegistry) {
    private val modelMutex = Mutex()

    suspend fun openSession(languageCode: String, sampleRate: Int): VoskStreamingSession? {
        var opened: VoskStreamingSession? = null
        try {
            return withContext(Dispatchers.IO) {
                modelMutex.withLock {
                    val directory = registry.resolveModelDir(languageCode) ?: return@withLock null
                    var model: Model? = null
                    try {
                        model = Model(directory.absolutePath)
                        val recognizer = Recognizer(model, sampleRate.toFloat()).apply {
                            setWords(false)
                            setMaxAlternatives(0)
                        }
                        VoskStreamingSession(recognizer, model).also { opened = it }
                    } catch (e: Exception) {
                        model?.close()
                        Log.w("LocalVoskSttClient", "Cannot open $languageCode", e)
                        null
                    }
                }
            }
        } catch (e: CancellationException) {
            opened?.close()
            throw e
        }
    }

    suspend fun transcribe(pcmData: ByteArray, sampleRate: Int, languageCode: String): TranscribeOutcome {
        val session = openSession(languageCode, sampleRate)
            ?: return TranscribeOutcome("", "No usable Vosk model for '$languageCode'. Download it from the language picker.")
        return try {
            withContext(Dispatchers.IO) {
                val result = session.feed(pcmData)
                TranscribeOutcome(result.text.ifBlank { session.finish() })
            }
        } finally {
            session.close()
        }
    }

    companion object {
        internal fun extractText(resultJson: String): String {
            return try {
                val json = JSONObject(resultJson)
                json.optString("text")
                    .ifBlank { json.optString("partial") }
                    .trim()
            } catch (_: Throwable) {
                ""
            }
        }
    }
}

/**
 * Live streaming Vosk session. Feed PCM chunks as they arrive; pull
 * partial/final text after each feed. Not thread-safe — call from a single
 * worker coroutine.
 */
class VoskStreamingSession internal constructor(
    private val recognizer: Recognizer,
    private val model: Model
) : PcmSpeechSession {
    @Volatile private var closed = false

    /**
     * Feed a chunk of 16-bit LE PCM. Returns [FeedResult] indicating whether a
     * final result is available (`accepted == true` means Vosk detected a
     * silence/segment boundary) plus the current text — if `accepted`, [text]
     * is the finalized segment; otherwise it's the latest partial.
     */
    override fun feed(pcm: ByteArray, length: Int): FeedResult {
        if (closed) return FeedResult("", accepted = false)
        val accepted = try {
            recognizer.acceptWaveForm(pcm, length)
        } catch (t: Throwable) {
            Log.w("VoskStreamingSession", "acceptWaveForm failed", t)
            return FeedResult("", accepted = false)
        }
        val text = if (accepted) {
            LocalVoskSttClient.extractText(recognizer.result)
        } else {
            LocalVoskSttClient.extractText(recognizer.partialResult)
        }
        return FeedResult(text, accepted)
    }

    /** Flushes any buffered audio and returns the final remaining text. */
    override fun finish(): String {
        if (closed) return ""
        return try {
            LocalVoskSttClient.extractText(recognizer.finalResult)
        } catch (t: Throwable) {
            Log.w("VoskStreamingSession", "finalResult failed", t)
            ""
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        try { recognizer.close() } finally { model.close() }
    }

    data class FeedResult(
        /** Current text — segment if [accepted], partial otherwise. */
        val text: String,
        /** True when Vosk hit a silence boundary and emitted a finalized segment. */
        val accepted: Boolean
    )
}
