package com.charles.livecaptionn.update

import com.charles.livecaptionn.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class UpdateCheckStatus { IDLE, CHECKING, UP_TO_DATE, AVAILABLE, NO_RELEASE, FAILED }

/** Checks stable GitHub releases from this fork; downloaded APKs are installed by the user. */
class UpdateChecker(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
) {
    private val mutable = MutableStateFlow<UpdateInfo?>(null)
    val available: StateFlow<UpdateInfo?> = mutable.asStateFlow()
    private val mutableStatus = MutableStateFlow(UpdateCheckStatus.IDLE)
    val status: StateFlow<UpdateCheckStatus> = mutableStatus.asStateFlow()
    private val checkMutex = Mutex()

    /**
     * Fetches /releases/latest. Returns the [UpdateInfo] when a newer build is
     * found (and pushes it into [available]), or null otherwise.
     */
    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        if (!BuildConfig.GITHUB_SELF_UPDATE_ENABLED) return@withContext null
        if (!checkMutex.tryLock()) return@withContext null
        mutableStatus.value = UpdateCheckStatus.CHECKING
        try {
            val request = Request.Builder()
                .url("https://api.github.com/repos/${BuildConfig.UPDATE_REPO_OWNER}/${BuildConfig.UPDATE_REPO_NAME}/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "LiveCaptionN-Android/${BuildConfig.VERSION_NAME}")
                .build()
            client.newCall(request).execute().use { response ->
                if (response.code == 404) {
                    mutable.value = null
                    mutableStatus.value = UpdateCheckStatus.NO_RELEASE
                    return@withContext null
                }
                if (!response.isSuccessful) {
                    mutableStatus.value = UpdateCheckStatus.FAILED
                    return@withContext null
                }
                val parsed = parseRelease(response.body?.string().orEmpty())
                if (parsed == null) {
                    mutableStatus.value = UpdateCheckStatus.FAILED
                    return@withContext null
                }
                // Compare version names, so local and CI version-code schemes both work.
                val currentVersion = parseBuildNumber(BuildConfig.VERSION_NAME) ?: BuildConfig.VERSION_CODE
                if (parsed.buildNumber > currentVersion) {
                    mutable.value = parsed
                    mutableStatus.value = UpdateCheckStatus.AVAILABLE
                    parsed
                } else {
                    mutable.value = null
                    mutableStatus.value = UpdateCheckStatus.UP_TO_DATE
                    null
                }
            }
        } catch (e: CancellationException) {
            mutableStatus.value = UpdateCheckStatus.IDLE
            throw e
        } catch (e: Exception) {
            mutableStatus.value = UpdateCheckStatus.FAILED
            null
        } finally {
            checkMutex.unlock()
        }
    }

    /** Clears any currently-surfaced update (used when the user dismisses the banner). */
    fun dismiss() {
        mutable.value = null
        mutableStatus.value = UpdateCheckStatus.IDLE
    }

    private fun parseRelease(json: String): UpdateInfo? {
        return try {
            val obj = JSONObject(json)
            val tag = obj.optString("tag_name").takeIf { it.isNotBlank() } ?: return null
            val buildNumber = parseBuildNumber(tag) ?: return null
            val name = obj.optString("name").ifBlank { "LiveCaptionN $tag" }
            val notes = obj.optString("body").trim()
            val htmlUrl = obj.optString("html_url")
                .ifBlank { "https://github.com/${BuildConfig.UPDATE_REPO_OWNER}/${BuildConfig.UPDATE_REPO_NAME}/releases/latest" }

            val apkUrl = obj.optJSONArray("assets")?.let { assets ->
                var best: String? = null
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val assetName = asset.optString("name")
                    val download = asset.optString("browser_download_url")
                    if (download.isBlank() || !assetName.endsWith(".apk", ignoreCase = true)) continue
                    if (assetName.contains("unsigned", ignoreCase = true) ||
                        assetName.contains("debug", ignoreCase = true)) continue
                    // Prefer a release APK; never offer unsigned or debug builds.
                    if (assetName.contains("release", ignoreCase = true)) return@let download
                    if (best == null) best = download
                }
                best
            }

            UpdateInfo(
                tagName = tag,
                releaseName = name,
                buildNumber = buildNumber,
                apkDownloadUrl = apkUrl,
                releasePageUrl = htmlUrl,
                notes = notes
            )
        } catch (t: Throwable) {
            null
        }
    }

    companion object {
        /**
         * Extracts a comparable build number from a semver tag.
         *
         * Accepts `v1.0.42`, `1.2.3`, `v2.0.0-beta`, etc. — anything matching
         * `v?X.Y.Z(...)`. Returns (major * 1_000_000 + minor * 1_000 + patch)
         * for comparing release tags with [BuildConfig.VERSION_NAME].
         *
         * Returns null for non-semver tags (e.g. `v1`, `v1.0`, `latest`).
         */
        internal fun parseBuildNumber(tag: String): Int? {
            val match = TAG_REGEX.matchEntire(tag.trim()) ?: return null
            val major = match.groupValues[1].toIntOrNull() ?: return null
            val minor = match.groupValues[2].toIntOrNull() ?: return null
            val patch = match.groupValues[3].toIntOrNull() ?: return null
            return major * 1_000_000 + minor * 1_000 + patch
        }

        private val TAG_REGEX = Regex("""v?(\d+)\.(\d+)\.(\d+)(?:[.\-].*)?""")
    }
}
