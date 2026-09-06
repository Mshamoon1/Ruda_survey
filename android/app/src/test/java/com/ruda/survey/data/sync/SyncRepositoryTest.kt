package com.ruda.survey.data.sync

import com.ruda.survey.data.local.SyncDao
import com.ruda.survey.data.local.SyncQueueEntry
import com.ruda.survey.data.remote.SurveyApi
import com.ruda.survey.utils.TokenManager
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

class SyncRepositoryTest {

    private lateinit var repository: SyncRepository
    private lateinit var api: SurveyApi
    private lateinit var dao: SyncDao
    private lateinit var tokenManager: TokenManager

    @Before
    fun setup() {
        api = mock()
        dao = mock()
        tokenManager = mock()
        repository = SyncRepository(api, dao, tokenManager)
    }

    @Test
    fun `processQueue returns zero counts (stub)`() = runTest {
        val result = repository.processQueue()

        assertEquals(0, result.synced)
        assertEquals(0, result.failed)
        assertEquals(0, result.conflicts)
        assertEquals(0, result.skipped)
    }

    @Test
    fun `hasPendingItems returns true when items exist`() = runTest {
        whenever(dao.getPendingItems()).thenReturn(listOf(mock()))
        assertTrue(repository.hasPendingItems())
    }

    @Test
    fun `hasPendingItems returns false when queue is empty`() = runTest {
        whenever(dao.getPendingItems()).thenReturn(emptyList())
        assertFalse(repository.hasPendingItems())
    }

    @Test
    fun `getPendingCount returns count`() = runTest {
        whenever(dao.getPendingItems()).thenReturn(listOf(mock(), mock()))
        assertEquals(2, repository.getPendingCount())
    }

    @Test
    fun `clearSynced deletes synced entries`() = runTest {
        repository.clearSynced()
        verify(dao).deleteSynced()
    }
}
