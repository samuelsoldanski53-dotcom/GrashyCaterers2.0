package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        DocumentEntity::class,
        ClientEntity::class,
        TemplateEntity::class,
        DocumentVersionEntity::class,
        SyncQueueEntity::class,
        MetaEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class GrashyDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
    abstract fun clientDao(): ClientDao
    abstract fun templateDao(): TemplateDao
    abstract fun versionDao(): VersionDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun metaDao(): MetaDao

    companion object {
        @Volatile
        private var INSTANCE: GrashyDatabase? = null

        fun getInstance(context: Context): GrashyDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    GrashyDatabase::class.java,
                    "grashy_studio_db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
