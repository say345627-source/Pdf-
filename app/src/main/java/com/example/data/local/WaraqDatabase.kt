package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.BookEntity
import com.example.data.model.BookmarkEntity

@Database(
    entities = [BookEntity::class, BookmarkEntity::class],
    version = 1,
    exportSchema = false
)
abstract class WaraqDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun bookmarkDao(): BookmarkDao

    companion object {
        @Volatile
        private var INSTANCE: WaraqDatabase? = null

        fun getInstance(context: Context): WaraqDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    WaraqDatabase::class.java,
                    "waraq_reader.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
