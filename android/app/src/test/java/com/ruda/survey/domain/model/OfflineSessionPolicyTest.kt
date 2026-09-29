package com.ruda.survey.domain.model

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class OfflineSessionPolicyTest {
    private val zone = ZoneId.of("Asia/Karachi")
    private fun time(value: String) = Instant.parse(value).toEpochMilli()
    @Test fun sameDayDoesNotDependOnJwtExpiry() {
        val morning = time("2026-09-25T03:00:00Z")
        assertTrue(OfflineSessionPolicy.allows(morning, morning, time("2026-09-25T18:59:59Z"), zone))
        assertFalse(OfflineSessionPolicy.allows(morning, morning, time("2026-09-25T19:00:00Z"), zone))
    }
    @Test fun missingVerificationAndBackwardsClockAreRejected() {
        val now = time("2026-09-25T08:00:00Z")
        assertFalse(OfflineSessionPolicy.allows(0, now, now, zone))
        assertFalse(OfflineSessionPolicy.allows(now, now + 600_000, now, zone))
        assertFalse(OfflineSessionPolicy.allows(now + 600_000, now, now, zone))
    }
}
