package com.pricelens.core.common.logging

object PiiRedactor {
    private val COORDINATE_REGEX = Regex("""-?\d{1,3}\.\d{5,15}""")
    private val PHONE_REGEX = Regex("""\+?\d{1,4}?[-.\s]?\(?\d{1,3}?\)?[-.\s]?\d{1,4}[-.\s]?\d{1,4}[-.\s]?\d{1,9}""")
    private val EMAIL_REGEX = Regex("""[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}""")
    private val AUTH_TOKEN_REGEX = Regex("""(Bearer\s+|Token\s+)([a-zA-Z0-9._\-/+=]{10,})""")

    fun redact(message: String): String {
        return message
            .replace(COORDINATE_REGEX, "[COORD]")
            .replace(EMAIL_REGEX, "[EMAIL]")
            .replace(PHONE_REGEX, "[PHONE]")
            .replace(AUTH_TOKEN_REGEX, "$1[TOKEN]")
    }
}
