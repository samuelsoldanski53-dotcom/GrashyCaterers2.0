package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents WHERE isDeleted = 0 ORDER BY updatedAt DESC")
    fun observeActiveDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents")
    suspend fun getAll(): List<DocumentEntity>

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun getById(id: String): DocumentEntity?

    @Query("SELECT COUNT(*) FROM documents")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DocumentEntity)
}

@Dao
interface ClientDao {
    @Query("SELECT * FROM clients WHERE isDeleted = 0 ORDER BY name ASC")
    fun observeActiveClients(): Flow<List<ClientEntity>>

    @Query("SELECT * FROM clients")
    suspend fun getAll(): List<ClientEntity>

    @Query("SELECT * FROM clients WHERE id = :id")
    suspend fun getById(id: String): ClientEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ClientEntity)
}

@Dao
interface TemplateDao {
    @Query("SELECT * FROM templates WHERE isDeleted = 0 ORDER BY isDefault DESC, name ASC")
    fun observeActiveTemplates(): Flow<List<TemplateEntity>>

    @Query("SELECT * FROM templates")
    suspend fun getAll(): List<TemplateEntity>

    @Query("SELECT * FROM templates WHERE id = :id")
    suspend fun getById(id: String): TemplateEntity?

    @Query("SELECT COUNT(*) FROM templates")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<TemplateEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TemplateEntity)
}

@Dao
interface VersionDao {
    @Query("SELECT * FROM document_versions WHERE documentId = :documentId ORDER BY version DESC, createdAt DESC")
    fun observeVersionsForDocument(documentId: String): Flow<List<DocumentVersionEntity>>

    @Query("SELECT * FROM document_versions WHERE documentId = :documentId ORDER BY version DESC, createdAt DESC")
    suspend fun getVersionsForDocument(documentId: String): List<DocumentVersionEntity>

    @Query("SELECT * FROM document_versions WHERE id = :id")
    suspend fun getById(id: String): DocumentVersionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DocumentVersionEntity)
}

@Dao
interface SyncQueueDao {
    @Query("SELECT COUNT(*) FROM sync_queue")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM sync_queue ORDER BY createdAt ASC")
    suspend fun getAll(): List<SyncQueueEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SyncQueueEntity)

    @Query("DELETE FROM sync_queue WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface MetaDao {
    @Query("SELECT value FROM meta WHERE key = :key")
    fun observeValue(key: String): Flow<String?>

    @Query("SELECT value FROM meta WHERE key = :key")
    suspend fun getValue(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: MetaEntity)
}
