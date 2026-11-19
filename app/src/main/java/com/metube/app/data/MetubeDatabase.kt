package com.metube.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [WatchProgress::class], version = 2, exportSchema = false)
abstract class MetubeDatabase : RoomDatabase() {
    abstract fun watchProgressDao(): WatchProgressDao

    companion object {
        @Volatile
        private var INSTANCE: MetubeDatabase? = null

        fun getInstance(context: Context): MetubeDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MetubeDatabase::class.java,
                    "metube_database.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
