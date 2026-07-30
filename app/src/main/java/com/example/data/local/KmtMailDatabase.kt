package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [MessageEntity::class, EmailHistoryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class KmtMailDatabase : RoomDatabase() {

    abstract fun kmtMailDao(): KmtMailDao

    companion object {
        @Volatile
        private var INSTANCE: KmtMailDatabase? = null

        fun getDatabase(context: Context): KmtMailDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    KmtMailDatabase::class.java,
                    "kmtmail_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
