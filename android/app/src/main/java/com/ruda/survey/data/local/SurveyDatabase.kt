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
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
