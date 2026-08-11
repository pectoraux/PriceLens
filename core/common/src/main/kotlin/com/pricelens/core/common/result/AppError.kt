package com.pricelens.core.common.result

sealed class AppError {
    abstract val message: String
    abstract val cause: Throwable?

    data class NetworkError(
        override val message: String,
        val code: Int? = null,
        override val cause: Throwable? = null
    ) : AppError()

    data class DatabaseError(
        override val message: String,
        override val cause: Throwable? = null
    ) : AppError()

    data class ValidationError(
        override val message: String,
        val field: String? = null,
        override val cause: Throwable? = null
    ) : AppError()

    data class CameraError(
        override val message: String,
        val type: CameraErrorType = CameraErrorType.UNKNOWN,
        override val cause: Throwable? = null
    ) : AppError()

    data class InferenceError(
        override val message: String,
        override val cause: Throwable? = null
    ) : AppError()

    data class IntegrityError(
        override val message: String,
        override val cause: Throwable? = null
    ) : AppError()

    data class UnknownError(
        override val message: String,
        override val cause: Throwable? = null
    ) : AppError()
}

enum class CameraErrorType {
    PERMISSION_DENIED,
    UNAVAILABLE,
    SESSION_CONFIG_FAILURE,
    CAPTURE_FAILURE,
    UNKNOWN
}
