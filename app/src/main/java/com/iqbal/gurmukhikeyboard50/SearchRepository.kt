package com.iqbal.gurmukhikeyboard50

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SearchRepository(context: Context) {

    private val db = Room.databaseBuilder(
        context.applicationContext,
        SearchDatabase::class.java,
        "search_db"
    ).build()

    suspend fun addPage(title: String, url: String, content: String) {
        withContext(Dispatchers.IO) {
            db.pageDao().insert(PageEntity(title = title, url = url, content = content))
        }
    }

    suspend fun search(query: String): List<PageEntity> {
        return withContext(Dispatchers.IO) {
            db.pageDao().search(query)
        }
    }
}
