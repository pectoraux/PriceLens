package com.pricelens.core.common.logging

interface Logger {
    fun d(tag: String, message: String)
    fun i(tag: String, message: String)
    fun w(tag: String, message: String)
    fun e(tag: String, message: String, throwable: Throwable? = null)
    
    /**
     * Logs a message that might contain PII.
     * Implementations should ensure PII is redacted or handled according to privacy policy.
     */
    fun pii(tag: String, message: String)
}
