package com.charles.livecaptionn

import com.charles.livecaptionn.update.UpdateChecker
import com.charles.livecaptionn.update.UpdateCheckStatus
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class ForkUpdateCheckerTest {
    private fun checker(code: Int = 200, body: String = "{}") = UpdateChecker(
        OkHttpClient.Builder().addInterceptor { chain ->
            assertEquals(
                "https://api.github.com/repos/meliorisse/LiveTranscribe-Android/releases/latest",
                chain.request().url.toString()
            )
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(code).message("Test response").body(body.toResponseBody()).build()
        }.build()
    )

    @Test fun newerReleaseOffersSignedApk() = runBlocking {
        val checker = checker(body = """{"tag_name":"v99.0.0","assets":[
            {"name":"app-release-unsigned.apk","browser_download_url":"https://example.com/unsigned"},
            {"name":"app-debug.apk","browser_download_url":"https://example.com/debug"},
            {"name":"fork-release.apk","browser_download_url":"https://example.com/release"}
        ]}""")
        assertEquals("https://example.com/release", checker.check()?.apkDownloadUrl)
        assertEquals(UpdateCheckStatus.AVAILABLE, checker.status.value)
        checker.dismiss()
        assertNull(checker.available.value)
        assertEquals(UpdateCheckStatus.IDLE, checker.status.value)
    }

    @Test fun installedReleaseIsUpToDate() = runBlocking {
        val checker = checker(body = """{"tag_name":"v${BuildConfig.VERSION_NAME}"}""")
        assertNull(checker.check())
        assertEquals(UpdateCheckStatus.UP_TO_DATE, checker.status.value)
    }

    @Test fun noPublishedReleaseIsDistinctFromNetworkFailure() = runBlocking {
        val missing = checker(404)
        missing.check()
        assertEquals(UpdateCheckStatus.NO_RELEASE, missing.status.value)
        val limited = checker(403)
        limited.check()
        assertEquals(UpdateCheckStatus.FAILED, limited.status.value)
    }

    @Test fun malformedResponseIsNotReportedAsUpToDate() = runBlocking {
        val checker = checker(body = "invalid JSON")
        checker.check()
        assertEquals(UpdateCheckStatus.FAILED, checker.status.value)
    }

    @Test fun offlineCheckReportsFailure() = runBlocking {
        val checker = UpdateChecker(OkHttpClient.Builder().addInterceptor {
            throw IOException("offline")
        }.build())
        checker.check()
        assertEquals(UpdateCheckStatus.FAILED, checker.status.value)
    }
}
