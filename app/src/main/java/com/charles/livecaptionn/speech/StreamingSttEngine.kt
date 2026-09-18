package com.charles.livecaptionn.speech

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.charles.livecaptionn.settings.AudioSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Captures 16kHz mono PCM for both microphone and MediaProjection audio.
 * Manual mode feeds Vosk immediately; automatic mode keeps capturing while the
 * worker buffers speech, identifies its language, and replays it into Vosk.
 * Native resources are closed only by their worker, never by the main thread.
 */
class StreamingSttEngine(
    private val context: Context,
    private val audioSource: AudioSource,
    private val mediaProjection: MediaProjection?,
    private val languageCode: String,
    private val localSttClient: LocalVoskSttClient,
    private val scope: CoroutineScope,
    private val onResult: (SpeechResult) -> Unit,
    private val onError: (String?) -> Unit = {},
    private val detectorFactory: (() -> SpeechLanguageDetector)? = null,
    private val detectionLanguages: Set<String> = emptySet(),
    private val onLanguageNotice: (String) -> Unit = {}
) : SpeechEngine {

    private val statusMutable = MutableStateFlow(RecognitionStatus.IDLE)
    val status: StateFlow<RecognitionStatus> = statusMutable

    private var audioRecord: AudioRecord? = null
    private var captureJob: Job? = null
    @Volatile private var pauseGeneration = 0
    @Volatile private var processingGeneration = 0

    @Volatile private var running = false
    @Volatile private var paused = false

    override fun start() {
        if (running) return
        running = true
        paused = false
        captureJob = scope.launch(Dispatchers.IO) { startInternal() }
    }

    override fun stop() {
        running = false
        paused = false
        captureJob?.cancel()
        captureJob = null
        try { audioRecord?.stop() } catch (_: Throwable) {}
        if (audioSource == AudioSource.SYSTEM) {
            try { mediaProjection?.stop() } catch (_: Throwable) {}
        }
        statusMutable.value = RecognitionStatus.IDLE
    }

    override fun pause() {
        paused = true
        pauseGeneration += 1
        statusMutable.value = RecognitionStatus.PAUSED
    }

    override fun resume() {
        if (!running) return
        paused = false
        statusMutable.value = RecognitionStatus.LISTENING
    }

    private suspend fun startInternal() {
        var processor: BufferedLanguageSession? = null
        var localSession: VoskStreamingSession? = null
        var detector: SpeechLanguageDetector? = null
        var record: AudioRecord? = null
        try {
            localSession = localSttClient.openSession(languageCode, SAMPLE_RATE)
                ?: error("No usable on-device model for '$languageCode'. Download one from the language picker.")
            currentCoroutineContext().ensureActive()
            detector = try {
                detectorFactory?.invoke()
            } catch (e: Exception) {
                onLanguageNotice("Speech-language detector could not load; using $languageCode. Remove and download the detector to retry.")
                null
            } catch (e: LinkageError) {
                onLanguageNotice("Speech-language detector is unavailable on this device; using $languageCode.")
                null
            }
            currentCoroutineContext().ensureActive()
            processor = BufferedLanguageSession(
                languageCode, localSession, detector, detectionLanguages,
                openSession = { localSttClient.openSession(it, SAMPLE_RATE) },
                onResult = { if (running && !paused && processingGeneration == pauseGeneration) onResult(it) },
                onNotice = { if (running && !paused && processingGeneration == pauseGeneration) onLanguageNotice(it) }
            )
            localSession = null // processor owns native resources from here
            detector = null
            record = buildAudioRecord() ?: error("Audio capture unavailable")
            check(record.state == AudioRecord.STATE_INITIALIZED) { "Audio capture failed to initialize" }
            audioRecord = record
            currentCoroutineContext().ensureActive()
            record.startRecording()
            check(record.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "Audio capture did not start" }
            statusMutable.value = RecognitionStatus.LISTENING
            onError(null)
            captureLoop(record, processor)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (running) {
                Log.e(TAG, "Capture failed", e)
                onError(e.message ?: "Audio capture failed")
                statusMutable.value = RecognitionStatus.ERROR
            }
        } finally {
            // Only this worker closes native recognizers, including after in-flight inference.
            processor?.close()
            localSession?.close()
            detector?.close()
            try { record?.stop() } catch (_: Exception) {}
            record?.release()
            audioRecord = null
            running = false
        }
    }

    private data class AudioChunk(val bytes: ByteArray, val generation: Int)

    private suspend fun captureLoop(record: AudioRecord, processor: BufferedLanguageSession) = coroutineScope {
        // Keep capture independent from inference/loading. Bound memory to 20 seconds;
        // an overloaded device reports an error instead of silently dropping speech.
        val chunks = Channel<AudioChunk>(200)
        val reader = launch(Dispatchers.IO) {
            try {
                val buffer = ByteArray(CHUNK_BYTES)
                while (isActive && running) {
                    val generation = pauseGeneration
                    val read = record.read(buffer, 0, buffer.size)
                    currentCoroutineContext().ensureActive()
                    if (read < 0) error("Audio capture failed ($read). Restart captioning.")
                    if (read == 0) { delay(10); continue }
                    if (paused || generation != pauseGeneration) continue
                    check(chunks.trySend(AudioChunk(buffer.copyOf(read), generation)).isSuccess) {
                        "Speech processing cannot keep up. Disable auto-detect or use smaller Vosk models, then restart."
                    }
                }
            } finally {
                chunks.close()
            }
        }
        var silentChunks = 0
        var sawAudio = false
        var generation = pauseGeneration
        try {
            for (chunk in chunks) {
                currentCoroutineContext().ensureActive()
                if (generation != pauseGeneration) {
                    processor.discardPending()
                    generation = pauseGeneration
                }
                if (paused || chunk.generation != generation) continue
                val silent = averageAbsAmplitude(chunk.bytes, chunk.bytes.size) < SILENCE_AVERAGE_ABS_THRESHOLD
                if (silent) {
                    silentChunks += 1
                    if (audioSource == AudioSource.SYSTEM && !sawAudio &&
                        silentChunks >= SILENT_CHUNKS_BEFORE_SYSTEM_HINT) onError(NO_CAPTURABLE_AUDIO_MESSAGE)
                } else {
                    silentChunks = 0
                    sawAudio = true
                    onError(null)
                }
                processingGeneration = generation
                processor.feed(chunk.bytes, !silent)
                if (running && !paused) statusMutable.value = RecognitionStatus.LISTENING
            }
        } finally {
            reader.cancel()
            chunks.cancel()
        }
    }

    private fun buildAudioRecord(): AudioRecord? {
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .build()

        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(CHUNK_BYTES * 8)

        return when (audioSource) {
            AudioSource.SYSTEM -> {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    onError("System audio capture requires Android 10 (API 29) or newer. Please switch to Microphone mode.")
                    return null
                }
                val projection = mediaProjection
                    ?: run {
                        onError("MediaProjection not available for system audio")
                        return null
                    }
                val config = AudioPlaybackCaptureConfiguration.Builder(projection)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(AudioAttributes.USAGE_GAME)
                    .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                    .build()
                AudioRecord.Builder()
                    .setAudioPlaybackCaptureConfig(config)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(minBuf)
                    .build()
            }
            AudioSource.MIC -> {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    onError("Microphone permission not granted. Allow microphone access and try again.")
                    return null
                }
                AudioRecord.Builder()
                    .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(minBuf)
                    .build()
            }
        }
    }

    companion object {
        private const val TAG = "StreamingSttEngine"
        private const val SAMPLE_RATE = 16_000
        /** ~100ms at 16kHz mono 16-bit = 3200 bytes. Small chunks = live partials. */
        private const val CHUNK_BYTES = 3200
        private const val SILENCE_AVERAGE_ABS_THRESHOLD = 120
        private const val SILENT_CHUNKS_BEFORE_SYSTEM_HINT = 30 // ~3s of silence
        private const val NO_CAPTURABLE_AUDIO_MESSAGE =
            "No capturable system audio yet. Play unmuted media in an app that allows audio capture."

        private fun averageAbsAmplitude(pcmData: ByteArray, length: Int): Int {
            var sum = 0L
            var samples = 0
            var i = 0
            val end = minOf(length, pcmData.size) - 1
            while (i < end) {
                val low = pcmData[i].toInt() and 0xFF
                val high = pcmData[i + 1].toInt()
                val sample = (high shl 8) or low
                sum += abs(sample)
                samples += 1
                i += 2
            }
            return if (samples == 0) 0 else (sum / samples).toInt()
        }
    }
}
