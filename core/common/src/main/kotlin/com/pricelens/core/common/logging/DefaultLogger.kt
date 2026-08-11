package com.pricelens.core.common.logging

/**
 * Interface for the actual platform-specific logging mechanism.
 */
interface LogDelegate {
    fun d(tag: String, message: String)
    fun i(tag: String, message: String)
    fun w(tag: String, message: String)
    fun e(tag: String, message: String, throwable: Throwable?)
}

/**
 * Standard implementation of [Logger] that redacts PII and delegates to a [LogDelegate].
 */
class DefaultLogger(
    private val delegate: LogDelegate
) : Logger {
    override fun d(tag: String, message: String) {
        delegate.d(tag, message)
    }

    override fun i(tag: String, message: String) {
        delegate.i(tag, message)
    }

    override fun w(tag: String, message: String) {
        delegate.w(tag, message)
    }

    override fun e(tag: String, message: String, throwable: Throwable?) {
        delegate.e(tag, message, throwable)
    }

    override fun pii(tag: String, message: String) {
        delegate.i(tag, PiiRedactor.redact(message))
    }
}

/**
 * Simple JVM delegate for testing or non-android environments.
 */
class JvmLogDelegate : LogDelegate {
    override fun d(tag: String, message: String) = println("DEBUG: [$tag] $message")
    override fun i(tag: String, message: String) = println("INFO: [$tag] $message")
    override fun w(tag: String, message: String) = println("WARN: [$tag] $message")
    override fun e(tag: String, message: String, throwable: Throwable?) {
        println("ERROR: [$tag] $message")
        throwable?.printStackTrace()
    }
}
