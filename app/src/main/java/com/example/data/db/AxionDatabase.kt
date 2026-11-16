package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [HistoryEntity::class, LocalModelEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AxionDatabase : RoomDatabase() {
    abstract fun axionDao(): AxionDao

    companion object {
        @Volatile
        private var INSTANCE: AxionDatabase? = null

        fun getInstance(context: Context): AxionDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AxionDatabase::class.java,
                    "axion_expander.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
