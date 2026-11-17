package com.example.data

import com.example.BuildConfig
import com.example.domain.GrashyInitialProject
import com.example.domain.SyncEngine
import com.example.model.CloudUserSession
import com.example.model.ConflictRemoteSnapshot
import com.example.model.DocumentVersionRecord
import com.example.model.LetterheadElement
import com.example.model.LetterheadFormData
import com.example.model.LetterheadProject
import com.example.model.LocalClientRecord
import com.example.model.LocalDocumentRecord
import com.example.model.LocalTemplateRecord
import com.example.model.PageSettings
import com.example.model.RecordSyncStatus
import com.example.model.SyncEntityType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class SupabaseSyncClient(private val repository: WorkspaceRepository) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val supabaseUrl: String
        get() = runCatching { BuildConfig.VITE_SUPABASE_URL }.getOrDefault("").trim().trimEnd('/')

    private val supabaseAnonKey: String
        get() = runCatching { BuildConfig.VITE_SUPABASE_ANON_KEY }.getOrDefault("").trim()

    fun isConfigured(): Boolean {
        return supabaseUrl.startsWith("http") && supabaseAnonKey.length > 10
    }

    data class AuthResponse(
        val session: CloudUserSession?,
        val accessToken: String?,
        val needsEmailConfirmation: Boolean = false
    )

    data class SyncRunResult(
        val uploadedCount: Int,
        val pulledCount: Int,
        val conflictCount: Int,
        val syncedAt: String
    )

    suspend fun signIn(email: String, password: String): AuthResponse = withContext(Dispatchers.IO) {
        if (!isConfigured()) error("Supabase environment variables are not configured.")
        val body = buildJsonObject {
            put("email", email)
            put("password", password)
        }.toString()
        val res = httpRequest("$supabaseUrl/auth/v1/token?grant_type=password", "POST", body, null)
        val obj = json.parseToJsonElement(res).jsonObject
        val token = obj["access_token"]?.jsonPrimitive?.content ?: error("Sign in failed")
        val userObj = obj["user"]?.jsonObject
        val userId = userObj?.get("id")?.jsonPrimitive?.content ?: error("Missing user ID")
        val userEmail = userObj["email"]?.jsonPrimitive?.content ?: email
        repository.saveCloudSession(userId, userEmail, token)
        AuthResponse(CloudUserSession(userId, userEmail), token)
    }

    suspend fun signUp(email: String, password: String): AuthResponse = withContext(Dispatchers.IO) {
        if (!isConfigured()) error("Supabase environment variables are not configured.")
        val body = buildJsonObject {
            put("email", email)
            put("password", password)
        }.toString()
        val res = httpRequest("$supabaseUrl/auth/v1/signup", "POST", body, null)
        val obj = json.parseToJsonElement(res).jsonObject
        val token = obj["access_token"]?.jsonPrimitive?.content
        val userObj = obj["user"]?.jsonObject ?: obj
        val userId = userObj["id"]?.jsonPrimitive?.content
        val userEmail = userObj["email"]?.jsonPrimitive?.content ?: email
        if (token != null && userId != null) {
            repository.saveCloudSession(userId, userEmail, token)
            AuthResponse(CloudUserSession(userId, userEmail), token, false)
        } else {
            AuthResponse(null, null, true)
        }
    }

    suspend fun signOut() {
        repository.saveCloudSession(null, null, null)
    }

    suspend fun synchronize(userId: String, accessToken: String): SyncRunResult = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            error("Supabase is not configured. Add VITE_SUPABASE_URL and VITE_SUPABASE_ANON_KEY in Secrets.")
        }
        var uploadedCount = 0
        var pulledCount = 0
        val nowIso = GrashyInitialProject.nowIso()

        val queue = SyncEngine.sortSyncQueueByDependencies(repository.getAllQueueItems())
        val skippedBootDocIds = mutableSetOf<String>()

        for (item in queue) {
            when (item.entityType) {
                SyncEntityType.CLIENT -> {
                    val rec = repository.getClientById(item.entityId)
                    if (rec == null) {
                        repository.markQueueItemSynced(item.id)
                        continue
                    }
                    upsertClient(userId, accessToken, rec, item.operation == "delete")
                    uploadedCount++
                }
                SyncEntityType.TEMPLATE -> {
                    val rec = repository.getTemplateById(item.entityId)
                    if (rec == null) {
                        repository.markQueueItemSynced(item.id)
                        continue
                    }
                    upsertTemplate(userId, accessToken, rec, item.operation == "delete")
                    uploadedCount++
                }
                SyncEntityType.DOCUMENT -> {
                    val rec = repository.getDocumentById(item.entityId)
                    if (rec == null) {
                        repository.markQueueItemSynced(item.id)
                        continue
                    }
                    if (rec.syncStatus == RecordSyncStatus.CONFLICT) continue

                    val cloudDocId = SyncEngine.toCloudId(userId, rec.id)
                    val remoteRows = httpRequest(
                        "$supabaseUrl/rest/v1/documents?id=eq.$cloudDocId&user_id=eq.$userId&select=*",
                        "GET",
                        null,
                        accessToken
                    )
                    val remoteArr = json.parseToJsonElement(remoteRows).jsonArray
                    if (remoteArr.isNotEmpty()) {
                        val rd = remoteArr.first().jsonObject
                        if (rec.isBootSeed) {
                            skippedBootDocIds.add(rec.id)
                            repository.markQueueItemSynced(item.id)
                            continue
                        }
                        val remoteVer = rd["version"]?.jsonPrimitive?.intOrNull ?: 1
                        val remoteUpdated = rd["updated_at"]?.jsonPrimitive?.content ?: ""
                        val remoteDel = rd["is_deleted"]?.jsonPrimitive?.booleanOrNull ?: false
                        val action = SyncEngine.detectDocumentConflict(rec, remoteVer, remoteUpdated, remoteDel)
                        if (action == SyncEngine.DocumentReconciliationAction.CONFLICT) {
                            val remoteProject = parseRemoteProject(rec.id, rd)
                            repository.putDocument(
                                rec.copy(
                                    syncStatus = RecordSyncStatus.CONFLICT,
                                    conflictRemote = ConflictRemoteSnapshot(
                                        version = remoteVer,
                                        updatedAt = remoteUpdated,
                                        name = remoteProject.name,
                                        project = remoteProject
                                    )
                                )
                            )
                            continue
                        }
                    }

                    upsertDocument(userId, accessToken, rec, item.operation == "delete", nowIso)
                    uploadedCount++
                }
                SyncEntityType.VERSION -> {
                    val ver = repository.getVersionById(item.entityId)
                    if (ver == null || skippedBootDocIds.contains(ver.documentId)) {
                        repository.markQueueItemSynced(item.id)
                        continue
                    }
                    upsertVersion(userId, accessToken, ver)
                    uploadedCount++
                }
            }
        }

        // Pull updates from Supabase
        val remoteDocsRaw = httpRequest("$supabaseUrl/rest/v1/documents?user_id=eq.$userId&select=*", "GET", null, accessToken)
        val remoteDocs = json.parseToJsonElement(remoteDocsRaw).jsonArray
        for (el in remoteDocs) {
            val rd = el.jsonObject
            val cloudId = rd["id"]?.jsonPrimitive?.content ?: continue
            val localId = SyncEngine.fromCloudId(userId, cloudId)
            val local = repository.getDocumentById(localId)
            val remoteVer = rd["version"]?.jsonPrimitive?.intOrNull ?: 1
            val remoteUpdated = rd["updated_at"]?.jsonPrimitive?.content ?: ""
            val remoteDel = rd["is_deleted"]?.jsonPrimitive?.booleanOrNull ?: false
            val action = if (local?.isBootSeed == true) {
                SyncEngine.DocumentReconciliationAction.PULL_REMOTE
            } else {
                SyncEngine.detectDocumentConflict(local, remoteVer, remoteUpdated, remoteDel)
            }
            val remoteProject = parseRemoteProject(localId, rd)
            if (action == SyncEngine.DocumentReconciliationAction.PULL_REMOTE) {
                repository.putDocument(
                    LocalDocumentRecord(
                        id = localId,
                        name = remoteProject.name,
                        clientId = rd["client_id"]?.jsonPrimitive?.content?.let { SyncEngine.fromCloudId(userId, it) },
                        templateId = rd["template_id"]?.jsonPrimitive?.content?.let { SyncEngine.fromCloudId(userId, it) },
                        version = remoteVer,
                        baseCloudVersion = remoteVer,
                        updatedAt = remoteUpdated,
                        syncedAt = nowIso,
                        syncStatus = RecordSyncStatus.SYNCED,
                        isBootSeed = false,
                        isDeleted = remoteDel,
                        project = remoteProject
                    )
                )
                pulledCount++
            } else if (action == SyncEngine.DocumentReconciliationAction.CONFLICT && local != null) {
                repository.putDocument(
                    local.copy(
                        syncStatus = RecordSyncStatus.CONFLICT,
                        conflictRemote = ConflictRemoteSnapshot(
                            version = remoteVer,
                            updatedAt = remoteUpdated,
                            name = remoteProject.name,
                            project = remoteProject
                        )
                    )
                )
            }
        }

        val conflictCount = repository.getAllDocuments().count { !it.isDeleted && it.syncStatus == RecordSyncStatus.CONFLICT }
        repository.setLastSyncedAt(nowIso)
        SyncRunResult(uploadedCount, pulledCount, conflictCount, nowIso)
    }

    private suspend fun upsertClient(userId: String, token: String, rec: LocalClientRecord, deleted: Boolean) {
        val cloudId = SyncEngine.toCloudId(userId, rec.id)
        val payload = buildJsonObject {
            put("id", cloudId)
            put("user_id", userId)
            put("name", rec.name)
            put("venue", rec.venue)
            put("default_pax", rec.defaultPax)
            put("contact_person", rec.contactPerson)
            put("phone_number", rec.phoneNumber)
            put("email", rec.email)
            put("updated_at", rec.updatedAt)
            put("is_deleted", deleted || rec.isDeleted)
        }.toString()
        httpRequest("$supabaseUrl/rest/v1/clients?on_conflict=id", "POST", payload, token, preferUpsert = true)
        repository.putClient(rec.copy(syncStatus = RecordSyncStatus.SYNCED))
        repository.markQueueItemSynced("client:${rec.id}")
    }

    private suspend fun upsertTemplate(userId: String, token: String, rec: LocalTemplateRecord, deleted: Boolean) {
        val cloudId = SyncEngine.toCloudId(userId, rec.id)
        val payload = buildJsonObject {
            put("id", cloudId)
            put("user_id", userId)
            put("name", rec.name)
            put("layout_mode", rec.layoutMode)
            put("page_settings", json.encodeToJsonElement(PageSettings.serializer(), rec.pageSettings))
            put("elements", json.parseToJsonElement(json.encodeToString(rec.elements)))
            put("is_default", rec.isDefault)
            put("updated_at", rec.updatedAt)
            put("is_deleted", deleted || rec.isDeleted)
        }.toString()
        httpRequest("$supabaseUrl/rest/v1/letterhead_templates?on_conflict=id", "POST", payload, token, preferUpsert = true)
        repository.putTemplate(rec.copy(syncStatus = RecordSyncStatus.SYNCED))
        repository.markQueueItemSynced("template:${rec.id}")
    }

    private suspend fun upsertDocument(userId: String, token: String, rec: LocalDocumentRecord, deleted: Boolean, nowIso: String) {
        val cloudId = SyncEngine.toCloudId(userId, rec.id)
        val payload = buildJsonObject {
            put("id", cloudId)
            put("user_id", userId)
            put("client_id", rec.clientId?.let { SyncEngine.toCloudId(userId, it) })
            put("template_id", rec.templateId?.let { SyncEngine.toCloudId(userId, it) })
            put("name", rec.name)
            put("version", rec.version)
            put("layout_mode", rec.project.layoutMode)
            put("page_count", rec.project.pageCount)
            put("form_data", json.encodeToJsonElement(LetterheadFormData.serializer(), rec.project.formData))
            put("document_settings", json.encodeToJsonElement(PageSettings.serializer(), rec.project.pageSettings))
            put("elements", json.parseToJsonElement(json.encodeToString(rec.project.elements)))
            put("updated_at", rec.updatedAt)
            put("is_deleted", deleted || rec.isDeleted)
        }.toString()
        httpRequest("$supabaseUrl/rest/v1/documents?on_conflict=id", "POST", payload, token, preferUpsert = true)
        repository.putDocument(
            rec.copy(
                syncStatus = RecordSyncStatus.SYNCED,
                isBootSeed = false,
                baseCloudVersion = rec.version,
                syncedAt = nowIso,
                lastError = null
            )
        )
        repository.markQueueItemSynced("document:${rec.id}")
    }

    private suspend fun upsertVersion(userId: String, token: String, ver: DocumentVersionRecord) {
        val payload = buildJsonObject {
            put("id", SyncEngine.toCloudId(userId, ver.id))
            put("user_id", userId)
            put("document_id", SyncEngine.toCloudId(userId, ver.documentId))
            put("version", ver.version)
            put("summary", ver.summary)
            put("snapshot", json.encodeToJsonElement(LetterheadProject.serializer(), ver.snapshot))
            put("created_at", ver.createdAt)
        }.toString()
        httpRequest("$supabaseUrl/rest/v1/document_versions?on_conflict=id", "POST", payload, token, preferUpsert = true)
        repository.putVersion(ver.copy(syncStatus = RecordSyncStatus.SYNCED))
        repository.markQueueItemSynced("version:${ver.id}")
    }

    private fun parseRemoteProject(localId: String, rd: JsonObject): LetterheadProject {
        val formData = rd["form_data"]?.let { json.decodeFromJsonElement<LetterheadFormData>(it) } ?: LetterheadFormData()
        val pageSettings = rd["document_settings"]?.let { json.decodeFromJsonElement<PageSettings>(it) } ?: PageSettings()
        val elements = rd["elements"]?.let { json.decodeFromJsonElement<List<LetterheadElement>>(it) } ?: emptyList()
        return LetterheadProject(
            id = localId,
            name = rd["name"]?.jsonPrimitive?.content ?: "Letterhead",
            version = rd["version"]?.jsonPrimitive?.intOrNull ?: 1,
            updatedAt = rd["updated_at"]?.jsonPrimitive?.content ?: "",
            pageCount = rd["page_count"]?.jsonPrimitive?.intOrNull ?: 1,
            layoutMode = rd["layout_mode"]?.jsonPrimitive?.content ?: "refined",
            formData = formData,
            pageSettings = pageSettings,
            elements = elements
        )
    }

    private fun httpRequest(
        urlStr: String,
        method: String,
        body: String?,
        accessToken: String?,
        preferUpsert: Boolean = false
    ): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10000
            readTimeout = 15000
            setRequestProperty("apikey", supabaseAnonKey)
            setRequestProperty("Content-Type", "application/json")
            if (accessToken != null) {
                setRequestProperty("Authorization", "Bearer $accessToken")
            }
            if (preferUpsert) {
                setRequestProperty("Prefer", "resolution=merge-duplicates,return=minimal")
            }
            if (body != null) {
                doOutput = true
            }
        }
        if (body != null) {
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }
        }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
        if (code !in 200..299) {
            error("Cloud error ($code): $text")
        }
        return text
    }
}
