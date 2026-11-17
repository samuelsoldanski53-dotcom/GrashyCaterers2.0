package com.example.data

import com.example.domain.GrashyInitialProject
import com.example.domain.SyncEngine
import com.example.model.ConflictRemoteSnapshot
import com.example.model.ConflictStrategy
import com.example.model.DocumentVersionRecord
import com.example.model.LetterheadElement
import com.example.model.LetterheadProject
import com.example.model.LocalClientRecord
import com.example.model.LocalDocumentRecord
import com.example.model.LocalTemplateRecord
import com.example.model.PageSettings
import com.example.model.RecordSyncStatus
import com.example.model.SyncEntityType
import com.example.model.SyncQueueItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.max

class WorkspaceRepository(private val db: GrashyDatabase) {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    companion object {
        const val META_ACTIVE_DOC_ID = "activeDocumentId"
        const val META_LAST_SYNCED_AT = "lastSyncedAt"
        const val META_CLOUD_SESSION_EMAIL = "cloudSessionEmail"
        const val META_CLOUD_SESSION_USER_ID = "cloudSessionUserId"
        const val META_CLOUD_ACCESS_TOKEN = "cloudAccessToken"
        const val META_PERMANENT_PHYSICAL_ADDRESS = "permanentPhysicalAddress"
        const val META_PERMANENT_POSTAL_ADDRESS = "permanentPostalAddress"
    }

    val documentsFlow: Flow<List<LocalDocumentRecord>> = db.documentDao().observeActiveDocuments()
        .map { list -> list.mapNotNull { it.toDomainOrNull() } }

    val clientsFlow: Flow<List<LocalClientRecord>> = db.clientDao().observeActiveClients()
        .map { list -> list.map { it.toDomain() } }

    val templatesFlow: Flow<List<LocalTemplateRecord>> = db.templateDao().observeActiveTemplates()
        .map { list -> list.mapNotNull { it.toDomainOrNull() } }

    val pendingCountFlow: Flow<Int> = db.syncQueueDao().observeCount()

    val lastSyncedAtFlow: Flow<String?> = db.metaDao().observeValue(META_LAST_SYNCED_AT)

    val activeDocIdFlow: Flow<String?> = db.metaDao().observeValue(META_ACTIVE_DOC_ID)

    fun observeVersionsForDocument(docId: String): Flow<List<DocumentVersionRecord>> {
        return db.versionDao().observeVersionsForDocument(docId)
            .map { list -> list.mapNotNull { it.toDomainOrNull() } }
    }

    suspend fun enqueueOperation(
        entityType: SyncEntityType,
        entityId: String,
        operation: String = "upsert"
    ) {
        val queueId = "${entityType.name.lowercase()}:$entityId"
        db.syncQueueDao().upsert(
            SyncQueueEntity(
                id = queueId,
                entityType = entityType.name,
                entityId = entityId,
                operation = operation,
                createdAt = GrashyInitialProject.nowIso(),
                attempts = 0
            )
        )
    }

    suspend fun ensureLocalDatabaseSeeded(initialProject: LetterheadProject): LocalDocumentRecord {
        val now = GrashyInitialProject.nowIso()

        if (db.templateDao().count() == 0) {
            val refined = GrashyInitialProject.createRedesignedGrashyProject()
            val original = GrashyInitialProject.createOriginalGrashyProject()
            val defaults = listOf(
                LocalTemplateRecord(
                    id = "template-grashy-refined",
                    name = "Grashy Editorial Quotation (Refined)",
                    layoutMode = "refined",
                    pageSettings = refined.pageSettings,
                    elements = refined.elements,
                    isDefault = true,
                    updatedAt = now,
                    syncStatus = RecordSyncStatus.PENDING
                ),
                LocalTemplateRecord(
                    id = "template-grashy-original",
                    name = "Grashy Uploaded PDF Brand Layout",
                    layoutMode = "original",
                    pageSettings = original.pageSettings,
                    elements = original.elements,
                    isDefault = true,
                    updatedAt = now,
                    syncStatus = RecordSyncStatus.PENDING
                )
            )
            db.templateDao().upsertAll(defaults.map { it.toEntity() })
            defaults.forEach { enqueueOperation(SyncEntityType.TEMPLATE, it.id, "upsert") }
        }

        if (db.documentDao().count() == 0) {
            val docId = initialProject.id.ifEmpty { "grashy-doc-${System.currentTimeMillis()}" }
            val normalizedProject = initialProject.copy(
                id = docId,
                version = max(1, initialProject.version),
                updatedAt = initialProject.updatedAt.ifEmpty { now }
            )
            val clientRec = SyncEngine.extractClientFromFormData(normalizedProject.formData, null, now)
            if (clientRec != null) {
                db.clientDao().upsert(clientRec.toEntity())
                enqueueOperation(SyncEntityType.CLIENT, clientRec.id, "upsert")
            }

            val docRecord = LocalDocumentRecord(
                id = docId,
                name = normalizedProject.name,
                clientId = clientRec?.id,
                templateId = if (normalizedProject.layoutMode == "original") "template-grashy-original" else "template-grashy-refined",
                version = normalizedProject.version,
                baseCloudVersion = 0,
                updatedAt = normalizedProject.updatedAt,
                syncStatus = RecordSyncStatus.PENDING,
                isBootSeed = true,
                project = normalizedProject
            )
            db.documentDao().upsert(docRecord.toEntity())
            db.metaDao().put(MetaEntity(META_ACTIVE_DOC_ID, docId))
            enqueueOperation(SyncEntityType.DOCUMENT, docId, "upsert")

            val initialVer = DocumentVersionRecord(
                id = "$docId-v${docRecord.version}",
                documentId = docId,
                version = docRecord.version,
                summary = "Initial local document",
                createdAt = now,
                snapshot = normalizedProject,
                syncStatus = RecordSyncStatus.PENDING
            )
            db.versionDao().upsert(initialVer.toEntity())
            enqueueOperation(SyncEntityType.VERSION, initialVer.id, "upsert")
            return docRecord
        }

        val activeId = db.metaDao().getValue(META_ACTIVE_DOC_ID)
        val allDocs = db.documentDao().getAll().filter { !it.isDeleted }
        val activeEntity = allDocs.find { it.id == activeId } ?: allDocs.firstOrNull()
        val parsed = activeEntity?.toDomainOrNull()
        if (parsed != null) return parsed

        val fresh = GrashyInitialProject.createRedesignedGrashyProject()
        val fallbackDoc = LocalDocumentRecord(
            id = fresh.id,
            name = fresh.name,
            templateId = "template-grashy-refined",
            version = 1,
            baseCloudVersion = 0,
            updatedAt = now,
            syncStatus = RecordSyncStatus.PENDING,
            project = fresh
        )
        db.documentDao().upsert(fallbackDoc.toEntity())
        db.metaDao().put(MetaEntity(META_ACTIVE_DOC_ID, fallbackDoc.id))
        return fallbackDoc
    }

    suspend fun getDocumentById(docId: String): LocalDocumentRecord? {
        return db.documentDao().getById(docId)?.toDomainOrNull()
    }

    suspend fun setActiveDocumentId(docId: String) {
        db.metaDao().put(MetaEntity(META_ACTIVE_DOC_ID, docId))
    }

    suspend fun saveLocalDocumentWorkingCopy(
        project: LetterheadProject,
        recordVersion: Boolean = false,
        versionSummary: String? = null,
        templateId: String? = null
    ): LocalDocumentRecord {
        val now = GrashyInitialProject.nowIso()
        val docId = project.id.ifEmpty { "grashy-doc-${System.currentTimeMillis()}" }
        val existing = db.documentDao().getById(docId)?.toDomainOrNull()

        val clientCandidate = SyncEngine.extractClientFromFormData(project.formData, null, now)
        var resolvedClientId: String? = null
        if (clientCandidate != null) {
            val existingClient = db.clientDao().getById(clientCandidate.id)?.toDomain()
            val mergedClient = SyncEngine.extractClientFromFormData(project.formData, existingClient, now)
            if (mergedClient != null) {
                resolvedClientId = mergedClient.id
                db.clientDao().upsert(mergedClient.toEntity())
                enqueueOperation(SyncEntityType.CLIENT, mergedClient.id, "upsert")
            }
        }

        val prevVersion = existing?.version ?: max(1, project.version)
        val shouldBumpVersion = recordVersion || existing?.syncStatus == RecordSyncStatus.SYNCED
        val nextVersion = if (shouldBumpVersion) prevVersion + 1 else prevVersion

        val updatedProject = project.copy(
            id = docId,
            version = nextVersion,
            updatedAt = now
        )

        val nextDoc = LocalDocumentRecord(
            id = docId,
            name = updatedProject.name.ifEmpty { "Untitled Letterhead" },
            clientId = resolvedClientId,
            templateId = templateId ?: existing?.templateId ?: "template-grashy-refined",
            version = nextVersion,
            baseCloudVersion = existing?.baseCloudVersion ?: 0,
            updatedAt = now,
            syncedAt = existing?.syncedAt,
            syncStatus = if (existing?.syncStatus == RecordSyncStatus.CONFLICT) RecordSyncStatus.CONFLICT else RecordSyncStatus.PENDING,
            isBootSeed = false,
            conflictRemote = existing?.conflictRemote,
            storageBackupPath = existing?.storageBackupPath,
            project = updatedProject
        )

        db.documentDao().upsert(nextDoc.toEntity())
        db.metaDao().put(MetaEntity(META_ACTIVE_DOC_ID, docId))
        enqueueOperation(SyncEntityType.DOCUMENT, docId, "upsert")

        if (recordVersion) {
            val versionId = "$docId-v$nextVersion-${System.currentTimeMillis()}"
            val versionRec = DocumentVersionRecord(
                id = versionId,
                documentId = docId,
                version = nextVersion,
                summary = versionSummary ?: if (updatedProject.formData.clientName.isNotBlank()) {
                    "Saved version for ${updatedProject.formData.clientName}"
                } else {
                    "Saved document v$nextVersion"
                },
                createdAt = now,
                snapshot = updatedProject,
                syncStatus = RecordSyncStatus.PENDING
            )
            db.versionDao().upsert(versionRec.toEntity())
            enqueueOperation(SyncEntityType.VERSION, versionId, "upsert")
        }

        return nextDoc
    }

    suspend fun createNewLocalDocument(
        baseProject: LetterheadProject? = null,
        name: String? = null
    ): LocalDocumentRecord {
        val now = GrashyInitialProject.nowIso()
        val template = GrashyInitialProject.createRedesignedGrashyProject()
        val newId = "grashy-doc-${System.currentTimeMillis()}"
        val base = baseProject ?: template
        val newProject = base.copy(
            id = newId,
            name = name ?: "Quotation ${java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.UK).format(java.util.Date())}",
            version = 1,
            updatedAt = now
        )
        return saveLocalDocumentWorkingCopy(
            project = newProject,
            recordVersion = true,
            versionSummary = "Created new document"
        )
    }

    suspend fun deleteLocalDocumentById(documentId: String) {
        val existing = db.documentDao().getById(documentId) ?: return
        val now = GrashyInitialProject.nowIso()
        db.documentDao().upsert(
            existing.copy(
                isDeleted = true,
                updatedAt = now,
                syncStatus = RecordSyncStatus.PENDING.name
            )
        )
        enqueueOperation(SyncEntityType.DOCUMENT, documentId, "delete")

        val activeId = db.metaDao().getValue(META_ACTIVE_DOC_ID)
        if (activeId == documentId) {
            val remaining = db.documentDao().getAll().filter { !it.isDeleted }
            if (remaining.isNotEmpty()) {
                db.metaDao().put(MetaEntity(META_ACTIVE_DOC_ID, remaining.first().id))
            }
        }
    }

    suspend fun saveTemplateFromProject(
        project: LetterheadProject,
        templateName: String
    ): LocalTemplateRecord {
        val now = GrashyInitialProject.nowIso()
        val id = "tpl-${System.currentTimeMillis()}"
        val rec = LocalTemplateRecord(
            id = id,
            name = templateName.trim().ifEmpty { "${project.name} Template" },
            layoutMode = project.layoutMode,
            pageSettings = project.pageSettings,
            elements = project.elements,
            isDefault = false,
            updatedAt = now,
            syncStatus = RecordSyncStatus.PENDING
        )
        db.templateDao().upsert(rec.toEntity())
        enqueueOperation(SyncEntityType.TEMPLATE, id, "upsert")
        return rec
    }

    suspend fun resolveConflict(documentId: String, strategy: ConflictStrategy): LocalDocumentRecord? {
        val existing = db.documentDao().getById(documentId)?.toDomainOrNull() ?: return null
        val resolved = SyncEngine.resolveDocumentConflict(existing, strategy, GrashyInitialProject.nowIso())
        db.documentDao().upsert(resolved.updatedPrimary.toEntity())

        resolved.archivedVersion?.let {
            db.versionDao().upsert(it.toEntity())
            if (it.syncStatus == RecordSyncStatus.PENDING) {
                enqueueOperation(SyncEntityType.VERSION, it.id, "upsert")
            }
        }
        resolved.duplicateCopy?.let {
            db.documentDao().upsert(it.toEntity())
            enqueueOperation(SyncEntityType.DOCUMENT, it.id, "upsert")
        }
        if (resolved.updatedPrimary.syncStatus == RecordSyncStatus.PENDING) {
            enqueueOperation(SyncEntityType.DOCUMENT, resolved.updatedPrimary.id, "upsert")
        } else {
            db.syncQueueDao().deleteById("document:${resolved.updatedPrimary.id}")
        }
        return resolved.updatedPrimary
    }

    suspend fun setLastSyncedAt(iso: String) {
        db.metaDao().put(MetaEntity(META_LAST_SYNCED_AT, iso))
    }

    suspend fun saveCloudSession(userId: String?, email: String?, token: String?) {
        db.metaDao().put(MetaEntity(META_CLOUD_SESSION_USER_ID, userId ?: ""))
        db.metaDao().put(MetaEntity(META_CLOUD_SESSION_EMAIL, email ?: ""))
        db.metaDao().put(MetaEntity(META_CLOUD_ACCESS_TOKEN, token ?: ""))
    }

    suspend fun getSavedCloudSession(): Triple<String, String, String>? {
        val uid = db.metaDao().getValue(META_CLOUD_SESSION_USER_ID).orEmpty()
        val email = db.metaDao().getValue(META_CLOUD_SESSION_EMAIL).orEmpty()
        val token = db.metaDao().getValue(META_CLOUD_ACCESS_TOKEN).orEmpty()
        return if (uid.isNotEmpty() && token.isNotEmpty()) Triple(uid, email, token) else null
    }

    suspend fun savePermanentAddress(physicalAddress: String, postalAddress: String) {
        db.metaDao().put(MetaEntity(META_PERMANENT_PHYSICAL_ADDRESS, physicalAddress.trim()))
        db.metaDao().put(MetaEntity(META_PERMANENT_POSTAL_ADDRESS, postalAddress.trim()))
    }

    suspend fun getPermanentAddress(): Pair<String?, String?> {
        val physical = db.metaDao().getValue(META_PERMANENT_PHYSICAL_ADDRESS)?.takeIf { it.isNotBlank() }
        val postal = db.metaDao().getValue(META_PERMANENT_POSTAL_ADDRESS)?.takeIf { it.isNotBlank() }
        return physical to postal
    }

    suspend fun getAllQueueItems(): List<SyncQueueItem> {
        return db.syncQueueDao().getAll().map {
            SyncQueueItem(
                id = it.id,
                entityType = SyncEntityType.valueOf(it.entityType),
                entityId = it.entityId,
                operation = it.operation,
                createdAt = it.createdAt,
                attempts = it.attempts,
                lastError = it.lastError
            )
        }
    }

    suspend fun markQueueItemSynced(queueId: String) = db.syncQueueDao().deleteById(queueId)

    suspend fun getAllDocuments(): List<LocalDocumentRecord> =
        db.documentDao().getAll().mapNotNull { it.toDomainOrNull() }

    suspend fun getAllClients(): List<LocalClientRecord> =
        db.clientDao().getAll().map { it.toDomain() }

    suspend fun getAllTemplates(): List<LocalTemplateRecord> =
        db.templateDao().getAll().mapNotNull { it.toDomainOrNull() }

    suspend fun getClientById(id: String): LocalClientRecord? =
        db.clientDao().getById(id)?.toDomain()

    suspend fun getTemplateById(id: String): LocalTemplateRecord? =
        db.templateDao().getById(id)?.toDomainOrNull()

    suspend fun getVersionById(id: String): DocumentVersionRecord? =
        db.versionDao().getById(id)?.toDomainOrNull()

    suspend fun putDocument(doc: LocalDocumentRecord) = db.documentDao().upsert(doc.toEntity())
    suspend fun putClient(client: LocalClientRecord) = db.clientDao().upsert(client.toEntity())
    suspend fun putTemplate(tpl: LocalTemplateRecord) = db.templateDao().upsert(tpl.toEntity())
    suspend fun putVersion(ver: DocumentVersionRecord) = db.versionDao().upsert(ver.toEntity())

    // Mapping helpers
    private fun DocumentEntity.toDomainOrNull(): LocalDocumentRecord? = runCatching {
        LocalDocumentRecord(
            id = id,
            name = name,
            clientId = clientId,
            templateId = templateId,
            version = version,
            baseCloudVersion = baseCloudVersion,
            updatedAt = updatedAt,
            syncedAt = syncedAt,
            syncStatus = runCatching { RecordSyncStatus.valueOf(syncStatus) }.getOrDefault(RecordSyncStatus.PENDING),
            isDeleted = isDeleted,
            isBootSeed = isBootSeed,
            storageBackupPath = storageBackupPath,
            project = json.decodeFromString<LetterheadProject>(projectJson),
            conflictRemote = conflictRemoteJson?.let { json.decodeFromString<ConflictRemoteSnapshot>(it) },
            lastError = lastError
        )
    }.getOrNull()

    private fun LocalDocumentRecord.toEntity(): DocumentEntity = DocumentEntity(
        id = id,
        name = name,
        clientId = clientId,
        templateId = templateId,
        version = version,
        baseCloudVersion = baseCloudVersion,
        updatedAt = updatedAt,
        syncedAt = syncedAt,
        syncStatus = syncStatus.name,
        isDeleted = isDeleted,
        isBootSeed = isBootSeed,
        storageBackupPath = storageBackupPath,
        projectJson = json.encodeToString(project),
        conflictRemoteJson = conflictRemote?.let { json.encodeToString(it) },
        lastError = lastError
    )

    private fun ClientEntity.toDomain(): LocalClientRecord = LocalClientRecord(
        id = id,
        name = name,
        venue = venue,
        defaultPax = defaultPax,
        contactPerson = contactPerson,
        phoneNumber = phoneNumber,
        email = email,
        updatedAt = updatedAt,
        syncStatus = runCatching { RecordSyncStatus.valueOf(syncStatus) }.getOrDefault(RecordSyncStatus.PENDING),
        isDeleted = isDeleted
    )

    private fun LocalClientRecord.toEntity(): ClientEntity = ClientEntity(
        id = id,
        name = name,
        venue = venue,
        defaultPax = defaultPax,
        contactPerson = contactPerson,
        phoneNumber = phoneNumber,
        email = email,
        updatedAt = updatedAt,
        syncStatus = syncStatus.name,
        isDeleted = isDeleted
    )

    private fun TemplateEntity.toDomainOrNull(): LocalTemplateRecord? = runCatching {
        LocalTemplateRecord(
            id = id,
            name = name,
            layoutMode = layoutMode,
            pageSettings = json.decodeFromString<PageSettings>(pageSettingsJson),
            elements = json.decodeFromString<List<LetterheadElement>>(elementsJson),
            isDefault = isDefault,
            updatedAt = updatedAt,
            syncStatus = runCatching { RecordSyncStatus.valueOf(syncStatus) }.getOrDefault(RecordSyncStatus.PENDING),
            isDeleted = isDeleted
        )
    }.getOrNull()

    private fun LocalTemplateRecord.toEntity(): TemplateEntity = TemplateEntity(
        id = id,
        name = name,
        layoutMode = layoutMode,
        pageSettingsJson = json.encodeToString(pageSettings),
        elementsJson = json.encodeToString(elements),
        isDefault = isDefault,
        updatedAt = updatedAt,
        syncStatus = syncStatus.name,
        isDeleted = isDeleted
    )

    private fun DocumentVersionEntity.toDomainOrNull(): DocumentVersionRecord? = runCatching {
        DocumentVersionRecord(
            id = id,
            documentId = documentId,
            version = version,
            summary = summary,
            createdAt = createdAt,
            snapshot = json.decodeFromString<LetterheadProject>(snapshotJson),
            syncStatus = runCatching { RecordSyncStatus.valueOf(syncStatus) }.getOrDefault(RecordSyncStatus.PENDING)
        )
    }.getOrNull()

    private fun DocumentVersionRecord.toEntity(): DocumentVersionEntity = DocumentVersionEntity(
        id = id,
        documentId = documentId,
        version = version,
        summary = summary,
        createdAt = createdAt,
        snapshotJson = json.encodeToString(snapshot),
        syncStatus = syncStatus.name
    )
}
