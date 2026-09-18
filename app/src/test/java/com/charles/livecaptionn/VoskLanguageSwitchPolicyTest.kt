package com.charles.livecaptionn

import com.charles.livecaptionn.speech.VoskLanguageSwitchPolicy
import com.charles.livecaptionn.speech.VoskLanguageSwitchPolicy.Action.*
import org.junit.Assert.*
import org.junit.Test

class VoskLanguageSwitchPolicyTest {
    @Test fun normalizesRegionalTagsAndDoesNotCommitUntilModelLoads() {
        val policy = VoskLanguageSwitchPolicy("en", setOf("en", "es"))
        assertEquals(HOLD, policy.observe("es-MX").action)
        assertEquals(SWITCH, policy.observe("ES").action)
        assertEquals("en", policy.activeLanguage)
        policy.switched("es")
        assertEquals(KEEP, policy.observe("es").action)
    }

    @Test fun unknownAndDisabledLanguageResetAgreement() {
        val policy = VoskLanguageSwitchPolicy("en", setOf("en", "es"))
        assertEquals(HOLD, policy.observe("es").action)
        assertEquals(KEEP, policy.observe(null).action)
        assertEquals(HOLD, policy.observe("es").action)
        assertEquals(UNAVAILABLE, policy.observe("fr").action)
        assertEquals(HOLD, policy.observe("es").action)
    }
}
