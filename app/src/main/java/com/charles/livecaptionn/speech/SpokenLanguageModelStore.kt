package com.charles.livecaptionn.speech

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class SpokenLanguageModelState(
    val installed: Boolean = false,
    val progress: Float? = null,
    val error: String? = null
)

/** Optional model assets are pinned, verified, and served by this fork. */
class SpokenLanguageModelStore(context: Context) {
    val directory = File(context.filesDir, "spoken-language/whisper-tiny-int8-v1")
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(SpokenLanguageModelState(installed = isInstalled()))
    val state = mutableState.asStateFlow()

    fun isInstalled(): Boolean = File(directory, ".ready").isFile &&
        FILES.all { File(directory, it.name).length() == it.bytes }

    suspend fun download() = withContext(Dispatchers.IO) {
        if (!mutex.tryLock()) return@withContext
        try {
            if (isInstalled()) return@withContext
            mutableState.value = SpokenLanguageModelState(progress = 0f)
            check(directory.mkdirs() || directory.isDirectory)
            val client = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS).build()
            var completed = 0L
            val total = FILES.sumOf { it.bytes }
            for (asset in FILES) {
                currentCoroutineContext().ensureActive()
                val partial = File(directory, asset.name + ".part")
                val digest = MessageDigest.getInstance("SHA-256")
                var count = 0L
                try {
                    client.newCall(Request.Builder().url("$BASE_URL/${asset.name}").build())
                        .execute().use { response ->
                            check(response.isSuccessful) { "Download failed: HTTP ${response.code}" }
                            val body = checkNotNull(response.body) { "Empty download" }
                            body.byteStream().use { input ->
                                partial.outputStream().use { output ->
                                    val buffer = ByteArray(64 * 1024)
                                    while (true) {
                                        currentCoroutineContext().ensureActive()
                                        val read = input.read(buffer)
                                        if (read < 0) break
                                        count += read
                                        check(count <= asset.bytes) { "Unexpected model size" }
                                        digest.update(buffer, 0, read)
                                        output.write(buffer, 0, read)
                                        mutableState.value = SpokenLanguageModelState(
                                            progress = (completed + count).toFloat() / total
                                        )
                                    }
                                }
                            }
                        }
                    check(count == asset.bytes && digest.digest().hex() == asset.sha256) {
                        "Model verification failed. Please retry."
                    }
                    val target = File(directory, asset.name)
                    check(!target.exists() || target.delete())
                    check(partial.renameTo(target)) { "Cannot install model" }
                    completed += count
                } finally {
                    partial.delete()
                }
            }
            File(directory, ".ready").writeText("whisper-tiny-int8-v1")
            mutableState.value = SpokenLanguageModelState(installed = true)
        } catch (e: CancellationException) {
            mutableState.value = SpokenLanguageModelState(installed = isInstalled())
            throw e
        } catch (e: Exception) {
            mutableState.value = SpokenLanguageModelState(installed = isInstalled(), error = e.message)
        } finally {
            mutex.unlock()
        }
    }

    suspend fun remove() = withContext(Dispatchers.IO) {
        mutex.withLock {
            val deleted = !directory.exists() || directory.deleteRecursively()
            mutableState.value = SpokenLanguageModelState(
                installed = isInstalled(), error = if (deleted) null else "Could not remove detector"
            )
        }
    }

    data class Asset(val name: String, val bytes: Long, val sha256: String)
    companion object {
        const val BASE_URL = "https://github.com/meliorisse/LiveTranscribe-Android/releases/download/language-id-model-v1"
        val FILES = listOf(
            Asset("tiny-encoder.int8.onnx", 12937772, "d24fb083ae3b1041fc24e97971d60e280c9342201fbb67b0ab428a8b4a51a434"),
            Asset("tiny-decoder.int8.onnx", 89855401, "d2fece8dd42771f1df975c6c0445770d0c292bf7547c2cae04a6c0cc57540925")
        )
        private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
    }
}
