package com.ruda.survey.domain.model

import java.time.Instant
import java.time.ZoneId

/** JWT lifetime is deliberately independent of permission to work locally. */
object OfflineSessionPolicy {
    const val CLOCK_TOLERANCE_MS = 120_000L

    fun allows(
        verifiedAt: Long,
        lastObservedAt: Long,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ): Boolean = verifiedAt > 0 &&
        now + CLOCK_TOLERANCE_MS >= maxOf(verifiedAt, lastObservedAt) &&
        Instant.ofEpochMilli(verifiedAt).atZone(zone).toLocalDate() ==
        Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
}
