package com.example.model

import kotlinx.serialization.Serializable

enum class RecordSyncStatus {
    SYNCED,
    PENDING,
    CONFLICT,
    ERROR
}

enum class SyncConnectionState {
    SYNCED,
    OFFLINE,
    SYNCING,
    PENDING,
    ERROR
}

enum class SyncEntityType {
    DOCUMENT,
    CLIENT,
    TEMPLATE,
    VERSION
}

@Serializable
data class LocalClientRecord(
    val id: String,
    val name: String,
    val venue: String = "",
    val defaultPax: String = "",
    val contactPerson: String = "",
    val phoneNumber: String = "",
    val email: String = "",
    val updatedAt: String = "",
    val syncStatus: RecordSyncStatus = RecordSyncStatus.PENDING,
    val isDeleted: Boolean = false
)

@Serializable
data class LocalTemplateRecord(
    val id: String,
    val name: String,
    val layoutMode: String = "refined",
    val pageSettings: PageSettings,
    val elements: List<LetterheadElement>,
    val isDefault: Boolean = false,
    val updatedAt: String = "",
    val syncStatus: RecordSyncStatus = RecordSyncStatus.PENDING,
    val isDeleted: Boolean = false
)

@Serializable
data class ConflictRemoteSnapshot(
    val version: Int,
    val updatedAt: String,
    val name: String,
    val project: LetterheadProject
)

@Serializable
data class LocalDocumentRecord(
    val id: String,
    val name: String,
    val clientId: String? = null,
    val templateId: String? = null,
    val version: Int = 1,
    val baseCloudVersion: Int = 0,
    val updatedAt: String = "",
    val syncedAt: String? = null,
    val syncStatus: RecordSyncStatus = RecordSyncStatus.PENDING,
    val isDeleted: Boolean = false,
    val isBootSeed: Boolean = false,
    val storageBackupPath: String? = null,
    val project: LetterheadProject,
    val conflictRemote: ConflictRemoteSnapshot? = null,
    val lastError: String? = null
)

@Serializable
data class DocumentVersionRecord(
    val id: String,
    val documentId: String,
    val version: Int,
    val summary: String,
    val createdAt: String,
    val snapshot: LetterheadProject,
    val syncStatus: RecordSyncStatus = RecordSyncStatus.PENDING
)

@Serializable
data class SyncQueueItem(
    val id: String,
    val entityType: SyncEntityType,
    val entityId: String,
    val operation: String, // "upsert" | "delete"
    val createdAt: String,
    val attempts: Int = 0,
    val lastError: String? = null
)

data class CloudUserSession(
    val userId: String,
    val email: String
)

data class SyncStatusSnapshot(
    val state: SyncConnectionState,
    val label: String,
    val shortLabel: String,
    val isOnline: Boolean,
    val isSyncing: Boolean,
    val supabaseConfigured: Boolean,
    val isAuthenticated: Boolean,
    val userEmail: String?,
    val pendingCount: Int,
    val conflictCount: Int,
    val lastSyncedAt: String?,
    val lastError: String?
)

enum class ConflictStrategy {
    KEEP_LOCAL,
    KEEP_REMOTE,
    KEEP_BOTH
}
