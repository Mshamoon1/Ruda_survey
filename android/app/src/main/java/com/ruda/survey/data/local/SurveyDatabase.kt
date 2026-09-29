package com.ruda.survey.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        CachedParcel::class,
        CachedSurvey::class,
        DraftSurvey::class,
        PendingSubmission::class,
        CachedImage::class,
        SyncQueueEntry::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class SurveyDatabase : RoomDatabase() {
    abstract fun surveyDao(): SurveyDao
    abstract fun syncDao(): SyncDao

    companion object {
        @Volatile
        private var INSTANCE: SurveyDatabase? = null

        fun getInstance(context: Context): SurveyDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SurveyDatabase::class.java,
                    "ruda_survey.db"
                )
                .addMigrations(MIGRATION_1_2)
                .build()
                INSTANCE = instance
                instance
            }
        }

        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS sync_queue (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    operation_type TEXT NOT NULL, parcel_code TEXT NOT NULL,
                    client_uuid TEXT NOT NULL, revision_no INTEGER,
                    data_json TEXT NOT NULL, file_path TEXT, image_type TEXT,
                    status TEXT NOT NULL, retry_count INTEGER NOT NULL,
                    last_error TEXT, error_code TEXT, next_retry_at INTEGER NOT NULL,
                    created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL
                )""")
            }
        }
    }
}
