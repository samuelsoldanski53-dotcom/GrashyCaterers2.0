package com.example.domain

import com.example.model.ConflictStrategy
import com.example.model.DocumentVersionRecord
import com.example.model.LetterheadFormData
import com.example.model.LocalClientRecord
import com.example.model.LocalDocumentRecord
import com.example.model.RecordSyncStatus
import com.example.model.SyncConnectionState
import com.example.model.SyncEntityType
import com.example.model.SyncQueueItem
import com.example.model.SyncStatusSnapshot
import kotlin.math.max

object SyncEngine {
    enum class DocumentReconciliationAction {
        NO_CHANGE, PUSH_LOCAL, PULL_REMOTE, CONFLICT
    }

    fun deriveSyncStatusSnapshot(
        isOnline: Boolean,
        isSyncing: Boolean,
        supabaseConfigured: Boolean,
        isAuthenticated: Boolean,
        userEmail: String?,
        pendingCount: Int,
        conflictCount: Int,
        lastSyncedAt: String?,
        lastError: String?
    ): SyncStatusSnapshot {
        val state: SyncConnectionState
        val label: String
        val shortLabel: String

        when {
            !isOnline -> {
                state = SyncConnectionState.OFFLINE
                label = "Offline – Working locally"
                shortLabel = if (pendingCount > 0) "Offline ($pendingCount)" else "Offline"
            }
            isSyncing -> {
                state = SyncConnectionState.SYNCING
                label = "Syncing – Uploading changes"
                shortLabel = "Syncing..."
            }
            lastError != null || conflictCount > 0 -> {
                state = SyncConnectionState.ERROR
                label = if (conflictCount > 0) {
                    "Error – Synchronization failed ($conflictCount conflict${if (conflictCount > 1) "s" else ""})"
                } else {
                    "Error – Synchronization failed"
                }
                shortLabel = if (conflictCount > 0) "Conflict ($conflictCount)" else "Sync Error"
            }
            pendingCount > 0 -> {
                state = SyncConnectionState.PENDING
                label = "Pending – Changes awaiting synchronization"
                shortLabel = "Pending ($pendingCount)"
            }
            else -> {
                state = SyncConnectionState.SYNCED
                label = "Online – Synced"
                shortLabel = if (supabaseConfigured && isAuthenticated) "Synced" else "Local Ready"
            }
        }

        return SyncStatusSnapshot(
            state = state,
            label = label,
            shortLabel = shortLabel,
            isOnline = isOnline,
            isSyncing = isSyncing,
            supabaseConfigured = supabaseConfigured,
            isAuthenticated = isAuthenticated,
            userEmail = userEmail,
            pendingCount = pendingCount,
            conflictCount = conflictCount,
            lastSyncedAt = lastSyncedAt,
            lastError = lastError
        )
    }

    fun detectDocumentConflict(
        localDoc: LocalDocumentRecord?,
        remoteVersion: Int,
        remoteUpdatedAt: String,
        remoteIsDeleted: Boolean
    ): DocumentReconciliationAction {
        if (localDoc == null) {
            return if (remoteIsDeleted) DocumentReconciliationAction.NO_CHANGE else DocumentReconciliationAction.PULL_REMOTE
        }
        if (localDoc.syncStatus == RecordSyncStatus.CONFLICT) {
            return DocumentReconciliationAction.CONFLICT
        }
        val hasLocalUnsyncedChanges =
            localDoc.syncStatus == RecordSyncStatus.PENDING || localDoc.syncStatus == RecordSyncStatus.ERROR
        val remoteAdvanced = remoteVersion > localDoc.baseCloudVersion

        if (hasLocalUnsyncedChanges && remoteAdvanced) return DocumentReconciliationAction.CONFLICT
        if (hasLocalUnsyncedChanges) return DocumentReconciliationAction.PUSH_LOCAL
        if (remoteAdvanced || (remoteIsDeleted && !localDoc.isDeleted)) return DocumentReconciliationAction.PULL_REMOTE
        if (remoteVersion == localDoc.version && remoteUpdatedAt == localDoc.updatedAt) return DocumentReconciliationAction.NO_CHANGE
        if (remoteUpdatedAt > localDoc.updatedAt) return DocumentReconciliationAction.PULL_REMOTE
        return DocumentReconciliationAction.NO_CHANGE
    }

    data class ConflictResolutionResult(
        val updatedPrimary: LocalDocumentRecord,
        val archivedVersion: DocumentVersionRecord? = null,
        val duplicateCopy: LocalDocumentRecord? = null
    )

    fun resolveDocumentConflict(
        localDoc: LocalDocumentRecord,
        strategy: ConflictStrategy,
        nowIso: String = GrashyInitialProject.nowIso()
    ): ConflictResolutionResult {
        val remote = localDoc.conflictRemote
            ?: return ConflictResolutionResult(
                updatedPrimary = localDoc.copy(
                    syncStatus = RecordSyncStatus.PENDING,
                    updatedAt = nowIso
                )
            )

        return when (strategy) {
            ConflictStrategy.KEEP_LOCAL -> {
                val nextVersion = max(localDoc.version, remote.version) + 1
                val updatedProject = localDoc.project.copy(version = nextVersion, updatedAt = nowIso)
                val archivedVersion = DocumentVersionRecord(
                    id = "${localDoc.id}-cloud-v${remote.version}-${System.currentTimeMillis()}",
                    documentId = localDoc.id,
                    version = remote.version,
                    summary = "Cloud v${remote.version} archived before keeping local changes",
                    createdAt = remote.updatedAt,
                    snapshot = remote.project,
                    syncStatus = RecordSyncStatus.SYNCED
                )
                ConflictResolutionResult(
                    updatedPrimary = localDoc.copy(
                        version = nextVersion,
                        baseCloudVersion = remote.version,
                        updatedAt = nowIso,
                        syncStatus = RecordSyncStatus.PENDING,
                        conflictRemote = null,
                        lastError = null,
                        project = updatedProject
                    ),
                    archivedVersion = archivedVersion
                )
            }
            ConflictStrategy.KEEP_REMOTE -> {
                val archivedVersion = DocumentVersionRecord(
                    id = "${localDoc.id}-local-backup-v${localDoc.version}-${System.currentTimeMillis()}",
                    documentId = localDoc.id,
                    version = localDoc.version,
                    summary = "Preserved local draft v${localDoc.version} before adopting cloud v${remote.version}",
                    createdAt = localDoc.updatedAt,
                    snapshot = localDoc.project,
                    syncStatus = RecordSyncStatus.PENDING
                )
                ConflictResolutionResult(
                    updatedPrimary = localDoc.copy(
                        name = remote.name,
                        version = remote.version,
                        baseCloudVersion = remote.version,
                        updatedAt = remote.updatedAt,
                        syncedAt = nowIso,
                        syncStatus = RecordSyncStatus.SYNCED,
                        conflictRemote = null,
                        lastError = null,
                        project = remote.project
                    ),
                    archivedVersion = archivedVersion
                )
            }
            ConflictStrategy.KEEP_BOTH -> {
                val copyId = "${localDoc.id}-copy-${System.currentTimeMillis()}"
                val duplicateProject = localDoc.project.copy(
                    id = copyId,
                    name = "${localDoc.name} (Local Copy)",
                    version = 1,
                    updatedAt = nowIso
                )
                val duplicateCopy = LocalDocumentRecord(
                    id = copyId,
                    name = duplicateProject.name,
                    clientId = localDoc.clientId,
                    templateId = localDoc.templateId,
                    version = 1,
                    baseCloudVersion = 0,
                    updatedAt = nowIso,
                    syncStatus = RecordSyncStatus.PENDING,
                    project = duplicateProject
                )
                ConflictResolutionResult(
                    updatedPrimary = localDoc.copy(
                        name = remote.name,
                        version = remote.version,
                        baseCloudVersion = remote.version,
                        updatedAt = remote.updatedAt,
                        syncedAt = nowIso,
                        syncStatus = RecordSyncStatus.SYNCED,
                        conflictRemote = null,
                        lastError = null,
                        project = remote.project
                    ),
                    duplicateCopy = duplicateCopy
                )
            }
        }
    }

    fun normalizeClientId(clientName: String): String {
        val slug = clientName
            .trim()
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .replace(Regex("^-+|-+$"), "")
        return if (slug.isNotEmpty()) "client-$slug" else ""
    }

    fun extractClientFromFormData(
        formData: LetterheadFormData,
        existing: LocalClientRecord? = null,
        nowIso: String = GrashyInitialProject.nowIso()
    ): LocalClientRecord? {
        val name = formData.clientName.trim()
        if (name.isEmpty()) return null
        val id = existing?.id ?: normalizeClientId(name)
        if (id.isEmpty()) return null
        return LocalClientRecord(
            id = id,
            name = name,
            venue = formData.venue.trim(),
            defaultPax = formData.pax.trim(),
            contactPerson = existing?.contactPerson ?: "",
            phoneNumber = existing?.phoneNumber ?: "",
            email = existing?.email ?: "",
            updatedAt = nowIso,
            syncStatus = RecordSyncStatus.PENDING,
            isDeleted = false
        )
    }

    fun toCloudId(userId: String, localId: String): String {
        val trimmed = localId.trim()
        if (trimmed.isEmpty()) return ""
        val prefix = "$userId:"
        return if (trimmed.startsWith(prefix)) trimmed else "$prefix$trimmed"
    }

    fun fromCloudId(userId: String, cloudId: String): String {
        val prefix = "$userId:"
        return if (cloudId.startsWith(prefix)) cloudId.removePrefix(prefix) else cloudId
    }

    fun sortSyncQueueByDependencies(queue: List<SyncQueueItem>): List<SyncQueueItem> {
        val orderWeight = mapOf(
            SyncEntityType.CLIENT to 1,
            SyncEntityType.TEMPLATE to 2,
            SyncEntityType.DOCUMENT to 3,
            SyncEntityType.VERSION to 4
        )
        return queue.sortedWith(
            compareBy<SyncQueueItem> { orderWeight[it.entityType] ?: 9 }
                .thenBy { it.createdAt }
        )
    }
}
