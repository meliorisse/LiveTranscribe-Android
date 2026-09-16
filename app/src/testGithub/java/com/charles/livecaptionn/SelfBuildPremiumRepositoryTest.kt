package com.charles.livecaptionn

import com.charles.livecaptionn.billing.SelfBuildPremiumRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SelfBuildPremiumRepositoryTest {
    @Test
    fun proIsIncludedImmediatelyAndDoesNotExpire() {
        val repo = SelfBuildPremiumRepository()
        assertTrue(repo.state.value.hasPro)
        assertTrue(repo.state.value.hasAdFree)
        assertTrue(repo.state.value.isWithinGracePeriod(Long.MAX_VALUE))
        assertFalse(repo.supportsEmailRestore)
        assertNull(repo.state.value.licenseToken)
    }

    @Test
    fun refreshRestoreAndRestartKeepAccessWithoutCredentials() = runBlocking {
        val repo = SelfBuildPremiumRepository()
        assertEquals(repo.state.value, repo.refresh().getOrThrow())
        assertEquals(repo.state.value, repo.refresh("unused-session").getOrThrow())
        assertEquals(repo.state.value, repo.restore().getOrThrow())
        assertEquals(repo.state.value, SelfBuildPremiumRepository().state.value)
    }
}
