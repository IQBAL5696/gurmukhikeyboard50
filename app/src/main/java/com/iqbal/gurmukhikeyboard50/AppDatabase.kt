package com.iqbal.gurmukhikeyboard50

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ClipboardItem::class, UserWord::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun clipboardDao(): ClipboardDao
    abstract fun userWordDao(): UserWordDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "gurmukhikeyboard_db"
                )
                .fallbackToDestructiveMigration() // Use this carefully if data preservation is needed
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
