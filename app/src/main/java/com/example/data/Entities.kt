package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey val id: String,
    val name: String,
    val clientId: String? = null,
    val templateId: String? = null,
    val version: Int = 1,
    val baseCloudVersion: Int = 0,
    val updatedAt: String,
    val syncedAt: String? = null,
    val syncStatus: String,
    val isDeleted: Boolean = false,
    val isBootSeed: Boolean = false,
    val storageBackupPath: String? = null,
    val projectJson: String,
    val conflictRemoteJson: String? = null,
    val lastError: String? = null
)

@Entity(tableName = "clients")
data class ClientEntity(
    @PrimaryKey val id: String,
    val name: String,
    val venue: String = "",
    val defaultPax: String = "",
    val contactPerson: String = "",
    val phoneNumber: String = "",
    val email: String = "",
    val updatedAt: String,
    val syncStatus: String,
    val isDeleted: Boolean = false
)

@Entity(tableName = "templates")
data class TemplateEntity(
    @PrimaryKey val id: String,
    val name: String,
    val layoutMode: String,
    val pageSettingsJson: String,
    val elementsJson: String,
    val isDefault: Boolean = false,
    val updatedAt: String,
    val syncStatus: String,
    val isDeleted: Boolean = false
)

@Entity(tableName = "document_versions")
data class DocumentVersionEntity(
    @PrimaryKey val id: String,
    val documentId: String,
    val version: Int,
    val summary: String,
    val createdAt: String,
    val snapshotJson: String,
    val syncStatus: String
)

@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey val id: String,
    val entityType: String,
    val entityId: String,
    val operation: String,
    val createdAt: String,
    val attempts: Int = 0,
    val lastError: String? = null
)

@Entity(tableName = "meta")
data class MetaEntity(
    @PrimaryKey val key: String,
    val value: String
)
