package com.iqbal.gurmukhikeyboard50

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicBoolean

class DatabaseHelper(private val context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_VERSION = 31
        private const val DATABASE_NAME = "GurmukhiSuggestions.db"

        private const val TABLE_WORDS = "words_table"
        private const val COLUMN_WORD = "word_text"
        private const val COLUMN_FREQUENCY = "frequency"

        private const val TABLE_CLIPBOARD = "clipboard_table"
        private const val COLUMN_CLIP_TEXT = "clip_text"
        private const val COLUMN_CLIP_TIMESTAMP = "timestamp"
        private const val COLUMN_CLIP_PINNED = "is_pinned"

        private const val TABLE_CALC_HISTORY = "calc_history_table"
        private const val COLUMN_CALC_EXPR = "expression"
        private const val COLUMN_CALC_RESULT = "result"
        private const val COLUMN_CALC_TIME = "calc_time"

        private const val TAG = "DatabaseHelper"
        private const val PARAGRAPH_ASSET_FILENAME = "paragraph.txt"

        private val isPopulating = AtomicBoolean(false)
    }

    override fun onCreate(db: SQLiteDatabase?) {
        db?.execSQL("CREATE TABLE $TABLE_WORDS (_id INTEGER PRIMARY KEY AUTOINCREMENT, $COLUMN_WORD TEXT UNIQUE NOT NULL, $COLUMN_FREQUENCY INTEGER DEFAULT 1)")
        db?.execSQL("CREATE TABLE $TABLE_CLIPBOARD (_id INTEGER PRIMARY KEY AUTOINCREMENT, $COLUMN_CLIP_TEXT TEXT NOT NULL, $COLUMN_CLIP_TIMESTAMP INTEGER DEFAULT (strftime('%s', 'now')), $COLUMN_CLIP_PINNED INTEGER DEFAULT 0)")
        db?.execSQL("CREATE TABLE $TABLE_CALC_HISTORY (_id INTEGER PRIMARY KEY AUTOINCREMENT, $COLUMN_CALC_EXPR TEXT NOT NULL, $COLUMN_CALC_RESULT TEXT NOT NULL, $COLUMN_CALC_TIME INTEGER DEFAULT (strftime('%s', 'now')))")
        db?.execSQL("CREATE TABLE word_pairs_table (_id INTEGER PRIMARY KEY AUTOINCREMENT, previous_word TEXT NOT NULL, next_word TEXT NOT NULL, frequency INTEGER DEFAULT 1, UNIQUE(previous_word, next_word))")
        db?.execSQL("CREATE TABLE abbreviations_table (_id INTEGER PRIMARY KEY AUTOINCREMENT, shortcut TEXT UNIQUE NOT NULL, full_text TEXT NOT NULL)")
        CoroutineScope(Dispatchers.IO).launch { populateDatabaseFromAsset(db) }
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 31) {
            db?.execSQL("CREATE TABLE IF NOT EXISTS $TABLE_CALC_HISTORY (_id INTEGER PRIMARY KEY AUTOINCREMENT, $COLUMN_CALC_EXPR TEXT NOT NULL, $COLUMN_CALC_RESULT TEXT NOT NULL, $COLUMN_CALC_TIME INTEGER DEFAULT (strftime('%s', 'now')))")
        }
    }

    // ---------------- Dictionary Management ----------------

    suspend fun deleteWord(word: String) = withContext(Dispatchers.IO) {
        try {
            val db = writableDatabase
            db.delete(TABLE_WORDS, "$COLUMN_WORD = ?", arrayOf(word))
            db.delete("word_pairs_table", "previous_word = ? OR next_word = ?", arrayOf(word, word))
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting word: $word", e)
        }
    }

    suspend fun deleteAllLearnedWords() = withContext(Dispatchers.IO) {
        val db = writableDatabase
        try {
            db.beginTransaction()
            db.delete(TABLE_WORDS, null, null)
            db.delete("word_pairs_table", null, null)
            db.setTransactionSuccessful()
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting all words", e)
        } finally {
            db.endTransaction()
            populateDatabaseFromAsset(db)
        }
    }

    // ---------------- Clipboard ----------------

    suspend fun addClipboardItem(text: String) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply { put(COLUMN_CLIP_TEXT, text) }
        writableDatabase.insert(TABLE_CLIPBOARD, null, values)
    }

    // ---------------- Calc History ----------------

    fun addCalcHistory(expr: String, res: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_CALC_EXPR, expr)
            put(COLUMN_CALC_RESULT, res)
        }
        db.insert(TABLE_CALC_HISTORY, null, values)
    }

    fun getCalcHistory(limit: Int): List<String> {
        val list = mutableListOf<String>()
        val query = "SELECT $COLUMN_CALC_EXPR, $COLUMN_CALC_RESULT FROM $TABLE_CALC_HISTORY ORDER BY _id DESC LIMIT ?"
        try {
            readableDatabase.rawQuery(query, arrayOf(limit.toString()))?.use { c ->
                while (c.moveToNext()) {
                    list.add("${c.getString(0)} = ${c.getString(1)}")
                }
            }
        } catch (e: Exception) { Log.e(TAG, "Error getting history", e) }
        return list
    }

    // ---------------- Auto Correct ----------------

    suspend fun getCorrectionCandidates(input: String): List<String> = withContext(Dispatchers.IO) {
        if (input.isEmpty()) return@withContext emptyList()
        val firstChar = input.substring(0, 1)
        val list = mutableListOf<String>()
        val query = "SELECT $COLUMN_WORD FROM $TABLE_WORDS WHERE $COLUMN_WORD LIKE ? LIMIT 100"
        readableDatabase.rawQuery(query, arrayOf("$firstChar%"))?.use { c ->
            val idx = c.getColumnIndex(COLUMN_WORD)
            while (c.moveToNext()) {
                list.add(c.getString(idx))
            }
        }
        list
    }

    // ---------------- Word Suggestions ----------------

    private fun populateDatabaseFromAsset(db: SQLiteDatabase?) {
        if (db == null) return
        if (isPopulating.getAndSet(true)) return
        try {
            context.assets.open(PARAGRAPH_ASSET_FILENAME).use { input ->
                BufferedReader(InputStreamReader(input, "UTF-8")).use { reader ->
                    val words = reader.readText().split(Regex("\\s+")).map { it.trim('|', '॥', ',', '.', '!', '?', ';', ':', '"', '\'', '(', ')', '[', ']', '{', '}', '—', '-') }.filter { it.isNotEmpty() && isCleanGurmukhi(it) }.distinct()
                    db.beginTransaction()
                    for (word in words) {
                        val values = ContentValues().apply { put(COLUMN_WORD, word) }
                        db.insertWithOnConflict(TABLE_WORDS, null, values, SQLiteDatabase.CONFLICT_IGNORE)
                    }
                    db.setTransactionSuccessful(); db.endTransaction()
                }
            }
        } catch (e: Exception) { Log.e(TAG, "Error populating database", e) } finally { isPopulating.set(false) }
    }

    private fun isCleanGurmukhi(word: String): Boolean = word.matches(Regex("^[\\u0A00-\\u0A7F]+$")) && !word.contains(Regex("[0-9੦-੯]"))

    suspend fun addWord(word: String) = withContext(Dispatchers.IO) {
        if (isPopulating.get()) return@withContext
        val w = word.trim()
        if (w.isEmpty() || !isCleanGurmukhi(w)) return@withContext
        val db = writableDatabase
        val values = ContentValues().apply { put(COLUMN_WORD, w) }
        val result = db.insertWithOnConflict(TABLE_WORDS, null, values, SQLiteDatabase.CONFLICT_IGNORE)
        if (result == -1L) db.execSQL("UPDATE $TABLE_WORDS SET $COLUMN_FREQUENCY = $COLUMN_FREQUENCY + 1 WHERE $COLUMN_WORD = ?", arrayOf(w))
    }

    suspend fun getWordSuggestions(prefix: String, limit: Int): List<String> = withContext(Dispatchers.IO) {
        if (isPopulating.get() || prefix.isEmpty()) return@withContext emptyList()
        val suggestions = mutableListOf<String>()
        val query = "SELECT $COLUMN_WORD FROM $TABLE_WORDS WHERE $COLUMN_WORD LIKE ? ORDER BY $COLUMN_FREQUENCY DESC LIMIT ?"
        readableDatabase.rawQuery(query, arrayOf("$prefix%", limit.toString()))?.use { c ->
            val idx = c.getColumnIndex(COLUMN_WORD)
            while (c.moveToNext()) suggestions.add(c.getString(idx))
        }
        suggestions
    }

    suspend fun getClipboardHistory(searchQuery: String?, limit: Int): List<ClipboardItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<ClipboardItem>()
        val sql = StringBuilder("SELECT _id, clip_text, timestamp, is_pinned FROM $TABLE_CLIPBOARD")
        val args = mutableListOf<String>()
        if (!searchQuery.isNullOrEmpty()) { sql.append(" WHERE $COLUMN_CLIP_TEXT LIKE ?"); args.add("%$searchQuery%") }
        sql.append(" ORDER BY is_pinned DESC, timestamp DESC LIMIT ?"); args.add(limit.toString())
        readableDatabase.rawQuery(sql.toString(), args.toTypedArray())?.use { c ->
            while (c.moveToNext()) list.add(ClipboardItem(id = c.getLong(0), text = c.getString(1), timestamp = c.getLong(2), isPinned = c.getInt(3) == 1))
        }
        list
    }

    suspend fun updateClipboardPinned(id: Long, isPinned: Boolean) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply { put(COLUMN_CLIP_PINNED, if (isPinned) 1 else 0) }
        writableDatabase.update(TABLE_CLIPBOARD, values, "_id = ?", arrayOf(id.toString()))
    }

    suspend fun deleteClipboardItems(ids: List<Long>) = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        writableDatabase.execSQL("DELETE FROM $TABLE_CLIPBOARD WHERE _id IN (${ids.joinToString(",")}) AND is_pinned = 0")
    }
}
