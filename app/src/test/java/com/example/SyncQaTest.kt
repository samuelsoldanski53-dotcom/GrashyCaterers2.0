package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.GrashyDatabase
import com.example.data.SupabaseSyncClient
import com.example.data.WorkspaceRepository
import com.example.domain.GrashyInitialProject
import com.example.domain.SyncEngine
import com.example.model.ConflictRemoteSnapshot
import com.example.model.ConflictStrategy
import com.example.model.RecordSyncStatus
import com.example.model.SyncEntityType
import com.example.model.SyncQueueItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SyncQaTest {

    @Test
    fun syncQueueSortsByForeignKeyDependencies() {
        val unsorted = listOf(
            SyncQueueItem("version:v1", SyncEntityType.VERSION, "v1", "upsert", "2026-11-19T10:00:00Z"),
            SyncQueueItem("document:d1", SyncEntityType.DOCUMENT, "d1", "upsert", "2026-11-19T10:00:01Z"),
            SyncQueueItem("template:t1", SyncEntityType.TEMPLATE, "t1", "upsert", "2026-11-19T10:00:02Z"),
            SyncQueueItem("client:c1", SyncEntityType.CLIENT, "c1", "upsert", "2026-11-19T10:00:03Z")
        )
        val sorted = SyncEngine.sortSyncQueueByDependencies(unsorted)
        val types = sorted.map { it.entityType }
        assertTrue(types.indexOf(SyncEntityType.CLIENT) < types.indexOf(SyncEntityType.DOCUMENT))
        assertTrue(types.indexOf(SyncEntityType.TEMPLATE) < types.indexOf(SyncEntityType.DOCUMENT))
        assertTrue(types.indexOf(SyncEntityType.DOCUMENT) < types.indexOf(SyncEntityType.VERSION))
    }

    @Test
    fun cloudIdRoundTripsCleanly() {
        val userId = "user-12345"
        val localId = "grashy-doc-98765"
        val cloudId = SyncEngine.toCloudId(userId, localId)
        assertEquals(localId, SyncEngine.fromCloudId(userId, cloudId))
    }

    @Test
    fun syncStatusSnapshotDerivesOfflinePendingAndConflictStates() {
        val offline = SyncEngine.deriveSyncStatusSnapshot(
            isOnline = false,
            isSyncing = false,
            supabaseConfigured = true,
            isAuthenticated = true,
            userEmail = "test@grashy.co.ke",
            pendingCount = 2,
            conflictCount = 0,
            lastSyncedAt = null,
            lastError = null
        )
        assertEquals(false, offline.isOnline)
        assertEquals(2, offline.pendingCount)

        val conflict = SyncEngine.deriveSyncStatusSnapshot(
            isOnline = true,
            isSyncing = false,
            supabaseConfigured = true,
            isAuthenticated = true,
            userEmail = "test@grashy.co.ke",
            pendingCount = 1,
            conflictCount = 1,
            lastSyncedAt = "2026-11-19T10:00:00Z",
            lastError = null
        )
        assertEquals(1, conflict.conflictCount)
    }

    @Test
    fun roomSyncQueueEnqueuesAndClearsAndResolvesAllConflictStrategies() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, GrashyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = WorkspaceRepository(db)
            val seeded = repo.ensureLocalDatabaseSeeded(GrashyInitialProject.createRedesignedGrashyProject())
            val initialQueue = repo.getAllQueueItems()
            assertTrue("Expected seeded items in sync queue", initialQueue.isNotEmpty())

            // Simulate a remote conflict on the seeded document
            val remoteProject = seeded.project.copy(name = "Remote Edited Quotation", version = 3)
            val conflicted = seeded.copy(
                version = 2,
                baseCloudVersion = 1,
                syncStatus = RecordSyncStatus.CONFLICT,
                conflictRemote = ConflictRemoteSnapshot(
                    version = 3,
                    updatedAt = "2026-11-19T11:00:00Z",
                    name = remoteProject.name,
                    project = remoteProject
                )
            )
            repo.putDocument(conflicted)

            // 1. Test KEEP_LOCAL
            val keptLocal = repo.resolveConflict(conflicted.id, ConflictStrategy.KEEP_LOCAL)
            assertNotNull(keptLocal)
            assertEquals(RecordSyncStatus.PENDING, keptLocal!!.syncStatus)
            assertEquals(seeded.name, keptLocal.name)

            // 2. Test KEEP_REMOTE
            repo.putDocument(conflicted)
            val usedCloud = repo.resolveConflict(conflicted.id, ConflictStrategy.KEEP_REMOTE)
            assertNotNull(usedCloud)
            assertEquals(RecordSyncStatus.SYNCED, usedCloud!!.syncStatus)
            assertEquals("Remote Edited Quotation", usedCloud.name)

            // 3. Test KEEP_BOTH
            repo.putDocument(conflicted)
            val savedBoth = repo.resolveConflict(conflicted.id, ConflictStrategy.KEEP_BOTH)
            assertNotNull(savedBoth)
            val allDocs = repo.getAllDocuments().filter { !it.isDeleted }
            assertTrue("KEEP_BOTH should create a duplicate copy", allDocs.size >= 2)

            // Verify cloud session persistence
            repo.saveCloudSession("uid-1", "qa@grashy.co.ke", "token-abc")
            val session = repo.getSavedCloudSession()
            assertNotNull(session)
            assertEquals("qa@grashy.co.ke", session!!.second)
            repo.saveCloudSession(null, null, null)
            assertEquals(null, repo.getSavedCloudSession())
        } finally {
            db.close()
        }
    }

    @Test
    fun supabaseClientFailsGracefullyWhenNotConfigured() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, GrashyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = WorkspaceRepository(db)
            val client = SupabaseSyncClient(repo)
            if (!client.isConfigured()) {
                val result = runCatching { client.synchronize("uid-1", "fake-token") }
                assertTrue(result.isFailure)
                assertTrue(result.exceptionOrNull()?.message?.contains("Supabase") == true)
            }
        } finally {
            db.close()
        }
    }
}
