package com.ruda.survey.domain.model

import org.junit.Assert.*
import org.junit.Test

class SyncStateTest {

    @Test
    fun `default sync state has no pending items`() {
        val state = SyncState()
        assertFalse(state.hasPendingItems)
        assertEquals(0, state.pendingCount)
    }

    @Test
    fun `sync state with pending items`() {
        val state = SyncState(pendingCount = 5)
        assertTrue(state.hasPendingItems)
        assertEquals(5, state.pendingCount)
    }

    @Test
    fun `sync state with conflicts`() {
        val state = SyncState(conflictCount = 3)
        assertTrue(state.hasConflicts)
        assertEquals(3, state.conflictCount)
    }

    @Test
    fun `can sync when online and not syncing`() {
        val state = SyncState(isOnline = true, isSyncing = false)
        assertTrue(state.canSync)
    }

    @Test
    fun `cannot sync when offline`() {
        val state = SyncState(isOnline = false, isSyncing = false)
        assertFalse(state.canSync)
    }

    @Test
    fun `cannot sync when already syncing`() {
        val state = SyncState(isOnline = true, isSyncing = true)
        assertFalse(state.canSync)
    }

    @Test
    fun `total actionable includes pending and conflicts`() {
        val state = SyncState(pendingCount = 3, conflictCount = 2)
        assertEquals(5, state.totalActionable)
    }

    @Test
    fun `sync state stores all fields`() {
        val state = SyncState(
            pendingCount = 3,
            conflictCount = 2,
            inProgressCount = 1,
            lastSyncTime = 1000L,
            isSyncing = true,
            lastError = "Error",
            isOnline = true
        )

        assertEquals(3, state.pendingCount)
        assertEquals(2, state.conflictCount)
        assertEquals(1, state.inProgressCount)
        assertEquals(1000L, state.lastSyncTime)
        assertTrue(state.isSyncing)
        assertEquals("Error", state.lastError)
        assertTrue(state.isOnline)
    }

    @Test
    fun `sync outcome success`() {
        val outcome = SyncOutcome.Success(synced = 5)
        assertTrue(outcome is SyncOutcome.Success)
        assertEquals(5, (outcome as SyncOutcome.Success).synced)
    }

    @Test
    fun `sync outcome partial`() {
        val outcome = SyncOutcome.Partial(synced = 3, failed = 2, conflicts = 1)
        assertTrue(outcome is SyncOutcome.Partial)
        val partial = outcome as SyncOutcome.Partial
        assertEquals(3, partial.synced)
        assertEquals(2, partial.failed)
        assertEquals(1, partial.conflicts)
    }

    @Test
    fun `sync outcome error`() {
        val outcome = SyncOutcome.Error(message = "Failed", code = "SYNC_ERROR")
        assertTrue(outcome is SyncOutcome.Error)
        val error = outcome as SyncOutcome.Error
        assertEquals("Failed", error.message)
        assertEquals("SYNC_ERROR", error.code)
    }

    @Test
    fun `sync outcome no items`() {
        val outcome = SyncOutcome.NoItems
        assertTrue(outcome is SyncOutcome.NoItems)
    }
}
