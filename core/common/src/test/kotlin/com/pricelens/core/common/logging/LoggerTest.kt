package com.pricelens.core.common.logging

import org.junit.Assert.assertEquals
import org.junit.Test

class LoggerTest {

    private class TestLogDelegate : LogDelegate {
        var lastMessage: String? = null
        override fun d(tag: String, message: String) { lastMessage = message }
        override fun i(tag: String, message: String) { lastMessage = message }
        override fun w(tag: String, message: String) { lastMessage = message }
        override fun e(tag: String, message: String, throwable: Throwable?) { lastMessage = message }
    }

    @Test
    fun `pii should redact message before logging`() {
        val delegate = TestLogDelegate()
        val logger = DefaultLogger(delegate)
        
        logger.pii("Test", "My email is test@example.com")
        
        assertEquals("My email is [EMAIL]", delegate.lastMessage)
    }
}
