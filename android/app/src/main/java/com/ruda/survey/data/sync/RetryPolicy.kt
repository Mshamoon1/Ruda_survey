package com.ruda.survey.data.sync

object RetryPolicy {
    private val DELAYS = longArrayOf(
        30_000L,      // 30 seconds
        60_000L,      // 1 minute
        300_000L,     // 5 minutes
        900_000L,     // 15 minutes
        3600_000L     // 1 hour
    )
    private const val MAX_RETRIES = 5

    fun nextDelay(retryCount: Int): Long {
        if (retryCount >= MAX_RETRIES) return -1L
        return DELAYS[retryCount.coerceAtMost(DELAYS.size - 1)]
    }

    fun shouldRetry(retryCount: Int): Boolean = retryCount < MAX_RETRIES

    fun calculateNextRetryAt(retryCount: Int): Long {
        val delay = nextDelay(retryCount)
        if (delay < 0) return Long.MAX_VALUE
        return System.currentTimeMillis() + delay
    }

    fun classifyError(errorCode: String?): ErrorClass {
        return when (errorCode) {
            // CONFLICT - no automatic retry, user action required
            "REVISION_CONFLICT",
            "STALE_REVISION",
            "IDEMPOTENCY_CONFLICT",
            "IMAGE_ALREADY_EXISTS" -> ErrorClass.CONFLICT

            // PERMANENT - do not retry, user must fix input
            "VALIDATION_ERROR",
            "INVALID_IMAGE",
            "PARCEL_NOT_FOUND",
            "INVALID_REVISION",
            "NOT_FOUND" -> ErrorClass.PERMANENT

            // AUTH - do not retry, user must re-login
            "AUTHENTICATION_REQUIRED",
            "INVALID_CREDENTIALS",
            "USER_INACTIVE",
            "PERMISSION_DENIED",
            "TOKEN_EXPIRED" -> ErrorClass.AUTH

            // TRANSIENT - retry with backoff
            "NETWORK_ERROR",
            "SERVER_ERROR",
            "THROTTLED",
            "TIMEOUT",
            null -> ErrorClass.TRANSIENT

            else -> ErrorClass.TRANSIENT
        }
    }

    fun isRetryableError(errorCode: String?): Boolean {
        return classifyError(errorCode) == ErrorClass.TRANSIENT
    }

    fun isConflictError(errorCode: String?): Boolean {
        return classifyError(errorCode) == ErrorClass.CONFLICT
    }

    fun isPermanentError(errorCode: String?): Boolean {
        return classifyError(errorCode) == ErrorClass.PERMANENT
    }

    fun isAuthError(errorCode: String?): Boolean {
        return classifyError(errorCode) == ErrorClass.AUTH
    }
}

enum class ErrorClass {
    TRANSIENT,   // Retry with backoff
    CONFLICT,    // No auto-retry, user action required
    PERMANENT,   // Do not retry, fix input
    AUTH         // Do not retry, re-login required
}
