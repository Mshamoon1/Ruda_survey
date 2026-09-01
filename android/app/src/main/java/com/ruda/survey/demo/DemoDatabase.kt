package com.ruda.survey.demo

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [DemoParcel::class, DemoRevision::class, DemoImage::class],
    version = 1,
    exportSchema = false
)
abstract class DemoDatabase : RoomDatabase() {

    abstract fun demoDao(): DemoDao

    companion object {
        @Volatile
        private var INSTANCE: DemoDatabase? = null

        fun getInstance(context: Context): DemoDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DemoDatabase::class.java,
                    "ruda_demo.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
