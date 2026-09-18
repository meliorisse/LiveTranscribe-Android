package com.charles.livecaptionn.speech

import com.k2fsa.sherpa.onnx.SpokenLanguageIdentification
import com.k2fsa.sherpa.onnx.SpokenLanguageIdentificationConfig
import com.k2fsa.sherpa.onnx.SpokenLanguageIdentificationWhisperConfig
import java.io.File
import java.io.Closeable

interface SpeechLanguageDetector : Closeable {
    fun detect(pcm: ByteArray): String?
}

/** Worker-thread only. Whisper is used for language ID, never for transcription. */
class OfflineSpeechLanguageDetector(directory: File) : SpeechLanguageDetector {
    private val identifier = SpokenLanguageIdentification(config = SpokenLanguageIdentificationConfig(
        whisper = SpokenLanguageIdentificationWhisperConfig(
            encoder = File(directory, "tiny-encoder.int8.onnx").absolutePath,
            decoder = File(directory, "tiny-decoder.int8.onnx").absolutePath
        ),
        numThreads = 2,
        provider = "cpu"
    ))

    override fun detect(pcm: ByteArray): String? {
        val samples = FloatArray(pcm.size / 2) { i ->
            (((pcm[2 * i + 1].toInt() shl 8) or (pcm[2 * i].toInt() and 255)) / 32768f)
        }
        val stream = identifier.createStream()
        return try {
            stream.acceptWaveform(samples, 16000)
            identifier.compute(stream).trim().takeIf { it.isNotBlank() }
        } finally {
            stream.release()
        }
    }

    override fun close() = identifier.release()
}
