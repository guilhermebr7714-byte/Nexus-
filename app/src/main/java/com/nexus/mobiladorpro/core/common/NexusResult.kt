package com.nexus.mobiladorpro.core.common

sealed interface NexusResult<out T> {
    data class Success<T>(val data: T, val verificationEvidence: String? = null) : NexusResult<T>
    data class Error(
        val code: ErrorCode,
        val message: String,
        val technicalDetail: String? = null,
        val recoverable: Boolean = true
    ) : NexusResult<Nothing>
}

enum class ErrorCode {
    SHIZUKU_NOT_INSTALLED,
    SHIZUKU_SERVICE_STOPPED,
    SHIZUKU_PERMISSION_DENIED,
    SHIZUKU_BINDER_LOST,
    COMMAND_NOT_ALLOWLISTED,
    PARAMETER_OUT_OF_BOUNDS,
    VERIFICATION_MISMATCH,
    OEM_RESTRICTION_BLOCKED,
    UNSUPPORTED_ANDROID_VERSION,
    STORAGE_CORRUPTED,
    ENTITLEMENT_REQUIRED,
    EXECUTION_TIMEOUT,
    UNKNOWN_FAILURE
}
