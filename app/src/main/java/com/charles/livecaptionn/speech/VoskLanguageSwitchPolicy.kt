package com.charles.livecaptionn.speech

/** Two independent windows must agree; top-1 Whisper labels are not confidence scores. */
class VoskLanguageSwitchPolicy(initialLanguage: String, private val allowed: Set<String>) {
    var activeLanguage: String = initialLanguage
        private set
    private var candidate: String? = null

    enum class Action { KEEP, HOLD, SWITCH, UNAVAILABLE }
    data class Decision(val action: Action, val language: String?)

    fun observe(language: String?): Decision {
        val normalized = language?.lowercase()?.substringBefore('-')?.let {
            when (it) { "jw" -> "jv"; else -> it }
        }
        if (normalized == null || normalized == activeLanguage) {
            candidate = null
            return Decision(Action.KEEP, activeLanguage)
        }
        if (normalized !in allowed) {
            candidate = null
            return Decision(Action.UNAVAILABLE, normalized)
        }
        if (candidate != normalized) {
            candidate = normalized
            return Decision(Action.HOLD, normalized)
        }
        candidate = null
        return Decision(Action.SWITCH, normalized)
    }

    fun switched(language: String) { activeLanguage = language; candidate = null }
    fun reset() { candidate = null }
}
