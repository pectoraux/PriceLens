package com.pricelens.core.common.logging

import org.junit.Assert.assertEquals
import org.junit.Test

class PiiRedactorTest {

    @Test
    fun `redact should mask coordinates`() {
        val message = "Location is 45.12345, -122.54321"
        val expected = "Location is [COORD], [COORD]"
        assertEquals(expected, PiiRedactor.redact(message))
    }

    @Test
    fun `redact should mask emails`() {
        val message = "Contact us at support@example.com"
        val expected = "Contact us at [EMAIL]"
        assertEquals(expected, PiiRedactor.redact(message))
    }

    @Test
    fun `redact should mask phone numbers`() {
        val message = "Call +1-555-0199"
        val expected = "Call [PHONE]"
        assertEquals(expected, PiiRedactor.redact(message))
    }

    @Test
    fun `redact should mask tokens`() {
        val message = "Authorization: Bearer my.secret.token"
        val expected = "Authorization: Bearer [TOKEN]"
        assertEquals(expected, PiiRedactor.redact(message))
    }
}
