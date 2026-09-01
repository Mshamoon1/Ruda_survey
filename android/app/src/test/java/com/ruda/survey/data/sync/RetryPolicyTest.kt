package com.ruda.survey.data.sync

import org.junit.Assert.*
import org.junit.Test

class RetryPolicyTest {

    @Test
    fun `first retry delay is 30 seconds`() {
        val delay = RetryPolicy.nextDelay(0)
        assertEquals(30_000L, delay)
    }

    @Test
    fun `second retry delay is 1 minute`() {
        val delay = RetryPolicy.nextDelay(1)
        assertEquals(60_000L, delay)
    }

    @Test
    fun `third retry delay is 5 minutes`() {
        val delay = RetryPolicy.nextDelay(2)
        assertEquals(300_000L, delay)
    }

    @Test
    fun `fourth retry delay is 15 minutes`() {
        val delay = RetryPolicy.nextDelay(3)
        assertEquals(900_000L, delay)
    }

    @Test
    fun `fifth retry delay is 1 hour`() {
        val delay = RetryPolicy.nextDelay(4)
        assertEquals(3600_000L, delay)
    }

    @Test
    fun `max retries stops retries`() {
        val delay = RetryPolicy.nextDelay(5)
        assertEquals(-1L, delay)
    }

    @Test
    fun `should retry returns true for retry count less than max`() {
        assertTrue(RetryPolicy.shouldRetry(0))
        assertTrue(RetryPolicy.shouldRetry(4))
    }

    @Test
    fun `should retry returns false for retry count at max`() {
        assertFalse(RetryPolicy.shouldRetry(5))
        assertFalse(RetryPolicy.shouldRetry(10))
    }

    @Test
    fun `calculate next retry at returns future time`() {
        val before = System.currentTimeMillis()
        val nextRetryAt = RetryPolicy.calculateNextRetryAt(0)
        val after = System.currentTimeMillis()

        assertTrue(nextRetryAt >= before + 30_000L)
        assertTrue(nextRetryAt <= after + 30_000L)
    }

    @Test
    fun `calculate next retry at returns max value when retries exhausted`() {
        val nextRetryAt = RetryPolicy.calculateNextRetryAt(5)
        assertEquals(Long.MAX_VALUE, nextRetryAt)
    }

    @Test
    fun `exponential backoff increases delay`() {
        val delays = (0..4).map { RetryPolicy.nextDelay(it) }
        for (i in 1 until delays.size) {
            assertTrue(delays[i] > delays[i - 1])
        }
    }

    @Test
    fun `classify REVISION_CONFLICT as CONFLICT`() {
        assertEquals(ErrorClass.CONFLICT, RetryPolicy.classifyError("REVISION_CONFLICT"))
    }

    @Test
    fun `classify STALE_REVISION as CONFLICT`() {
        assertEquals(ErrorClass.CONFLICT, RetryPolicy.classifyError("STALE_REVISION"))
    }

    @Test
    fun `classify IDEMPOTENCY_CONFLICT as CONFLICT`() {
        assertEquals(ErrorClass.CONFLICT, RetryPolicy.classifyError("IDEMPOTENCY_CONFLICT"))
    }

    @Test
    fun `classify IMAGE_ALREADY_EXISTS as CONFLICT`() {
        assertEquals(ErrorClass.CONFLICT, RetryPolicy.classifyError("IMAGE_ALREADY_EXISTS"))
    }

    @Test
    fun `classify VALIDATION_ERROR as PERMANENT`() {
        assertEquals(ErrorClass.PERMANENT, RetryPolicy.classifyError("VALIDATION_ERROR"))
    }

    @Test
    fun `classify INVALID_IMAGE as PERMANENT`() {
        assertEquals(ErrorClass.PERMANENT, RetryPolicy.classifyError("INVALID_IMAGE"))
    }

    @Test
    fun `classify PARCEL_NOT_FOUND as PERMANENT`() {
        assertEquals(ErrorClass.PERMANENT, RetryPolicy.classifyError("PARCEL_NOT_FOUND"))
    }

    @Test
    fun `classify AUTHENTICATION_REQUIRED as AUTH`() {
        assertEquals(ErrorClass.AUTH, RetryPolicy.classifyError("AUTHENTICATION_REQUIRED"))
    }

    @Test
    fun `classify INVALID_CREDENTIALS as AUTH`() {
        assertEquals(ErrorClass.AUTH, RetryPolicy.classifyError("INVALID_CREDENTIALS"))
    }

    @Test
    fun `classify TOKEN_EXPIRED as AUTH`() {
        assertEquals(ErrorClass.AUTH, RetryPolicy.classifyError("TOKEN_EXPIRED"))
    }

    @Test
    fun `classify NETWORK_ERROR as TRANSIENT`() {
        assertEquals(ErrorClass.TRANSIENT, RetryPolicy.classifyError("NETWORK_ERROR"))
    }

    @Test
    fun `classify SERVER_ERROR as TRANSIENT`() {
        assertEquals(ErrorClass.TRANSIENT, RetryPolicy.classifyError("SERVER_ERROR"))
    }

    @Test
    fun `classify THROTTLED as TRANSIENT`() {
        assertEquals(ErrorClass.TRANSIENT, RetryPolicy.classifyError("THROTTLED"))
    }

    @Test
    fun `classify null as TRANSIENT`() {
        assertEquals(ErrorClass.TRANSIENT, RetryPolicy.classifyError(null))
    }

    @Test
    fun `isConflictError returns true for conflict codes`() {
        assertTrue(RetryPolicy.isConflictError("REVISION_CONFLICT"))
        assertTrue(RetryPolicy.isConflictError("STALE_REVISION"))
        assertTrue(RetryPolicy.isConflictError("IDEMPOTENCY_CONFLICT"))
    }

    @Test
    fun `isConflictError returns false for non-conflict codes`() {
        assertFalse(RetryPolicy.isConflictError("NETWORK_ERROR"))
        assertFalse(RetryPolicy.isConflictError("VALIDATION_ERROR"))
        assertFalse(RetryPolicy.isConflictError("AUTHENTICATION_REQUIRED"))
    }

    @Test
    fun `isPermanentError returns true for permanent codes`() {
        assertTrue(RetryPolicy.isPermanentError("VALIDATION_ERROR"))
        assertTrue(RetryPolicy.isPermanentError("INVALID_IMAGE"))
        assertTrue(RetryPolicy.isPermanentError("PARCEL_NOT_FOUND"))
    }

    @Test
    fun `isPermanentError returns false for non-permanent codes`() {
        assertFalse(RetryPolicy.isPermanentError("NETWORK_ERROR"))
        assertFalse(RetryPolicy.isPermanentError("REVISION_CONFLICT"))
        assertFalse(RetryPolicy.isPermanentError("AUTHENTICATION_REQUIRED"))
    }

    @Test
    fun `isAuthError returns true for auth codes`() {
        assertTrue(RetryPolicy.isAuthError("AUTHENTICATION_REQUIRED"))
        assertTrue(RetryPolicy.isAuthError("INVALID_CREDENTIALS"))
        assertTrue(RetryPolicy.isAuthError("TOKEN_EXPIRED"))
    }

    @Test
    fun `isAuthError returns false for non-auth codes`() {
        assertFalse(RetryPolicy.isAuthError("NETWORK_ERROR"))
        assertFalse(RetryPolicy.isAuthError("REVISION_CONFLICT"))
        assertFalse(RetryPolicy.isAuthError("VALIDATION_ERROR"))
    }
}
