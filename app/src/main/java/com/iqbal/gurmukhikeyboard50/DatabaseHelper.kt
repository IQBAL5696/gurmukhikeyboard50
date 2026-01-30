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
        private const val DATABASE_VERSION = 30 // Incremented version for pinning feature
        private const val DATABASE_NAME = "GurmukhiSuggestions.db"

        private const val TABLE_WORDS = "words_table"
        private const val COLUMN_ID = "_id"
        private const val COLUMN_WORD = "word_text"
        private const val COLUMN_FREQUENCY = "frequency"

        private const val TABLE_WORD_PAIRS = "word_pairs_table"
        private const val COLUMN_PREVIOUS_WORD = "previous_word"
        private const val COLUMN_NEXT_WORD = "next_word"

        private const val TABLE_CLIPBOARD = "clipboard_table"
        private const val COLUMN_CLIP_ID = "_id"
        private const val COLUMN_CLIP_TEXT = "clip_text"
        private const val COLUMN_CLIP_TIMESTAMP = "timestamp"
        private const val COLUMN_CLIP_PINNED = "is_pinned" // New column for pinning

        private const val TABLE_ABBREVIATIONS = "abbreviations_table"
        private const val COLUMN_ABB_ID = "_id"
        private const val COLUMN_SHORTCUT = "shortcut"
        private const val COLUMN_FULL_TEXT = "full_text"

        private const val TAG = "DatabaseHelper"
        private const val PARAGRAPH_ASSET_FILENAME = "paragraph.txt"

        private const val SQL_CREATE_TABLE = """
            CREATE TABLE $TABLE_WORDS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_WORD TEXT UNIQUE NOT NULL,
                $COLUMN_FREQUENCY INTEGER DEFAULT 1
            )
        """

        private const val SQL_CREATE_WORD_PAIRS_TABLE = """
            CREATE TABLE $TABLE_WORD_PAIRS (
                _id INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_PREVIOUS_WORD TEXT NOT NULL,
                $COLUMN_NEXT_WORD TEXT NOT NULL,
                $COLUMN_FREQUENCY INTEGER DEFAULT 1,
                UNIQUE($COLUMN_PREVIOUS_WORD, $COLUMN_NEXT_WORD)
            )
        """

        private const val SQL_CREATE_CLIPBOARD_TABLE = """
            CREATE TABLE $TABLE_CLIPBOARD (
                $COLUMN_CLIP_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_CLIP_TEXT TEXT NOT NULL,
                $COLUMN_CLIP_TIMESTAMP INTEGER DEFAULT (strftime('%s', 'now')),
                $COLUMN_CLIP_PINNED INTEGER DEFAULT 0
            )
        """

        private const val SQL_CREATE_ABBREVIATIONS_TABLE = """
            CREATE TABLE $TABLE_ABBREVIATIONS (
                $COLUMN_ABB_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_SHORTCUT TEXT UNIQUE NOT NULL,
                $COLUMN_FULL_TEXT TEXT NOT NULL
            )
        """

        private const val SQL_CREATE_INDEX =
            "CREATE INDEX idx_word_prefix ON $TABLE_WORDS($COLUMN_WORD)"

        private val isPopulating = AtomicBoolean(false)
    }

    override fun onCreate(db: SQLiteDatabase?) {
        Log.i(TAG, "Creating database tables...")
        db?.execSQL(SQL_CREATE_TABLE)
        db?.execSQL(SQL_CREATE_INDEX)
        db?.execSQL(SQL_CREATE_CLIPBOARD_TABLE)
        db?.execSQL(SQL_CREATE_WORD_PAIRS_TABLE)
        db?.execSQL(SQL_CREATE_ABBREVIATIONS_TABLE)
        Log.i(TAG, "Database tables created.")
        CoroutineScope(Dispatchers.IO).launch { populateDatabaseFromAsset(db) }
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 30) {
            db?.execSQL("ALTER TABLE $TABLE_CLIPBOARD ADD COLUMN $COLUMN_CLIP_PINNED INTEGER DEFAULT 0")
        } else {
            db?.execSQL("DROP TABLE IF EXISTS $TABLE_WORDS")
            db?.execSQL("DROP TABLE IF EXISTS $TABLE_CLIPBOARD")
            db?.execSQL("DROP TABLE IF EXISTS $TABLE_WORD_PAIRS")
            db?.execSQL("DROP TABLE IF EXISTS $TABLE_ABBREVIATIONS")
            onCreate(db)
        }
    }

    // ---------------- Initial Word Population ----------------

    private fun populateDatabaseFromAsset(db: SQLiteDatabase?) {
        if (db == null) return
        if (isPopulating.getAndSet(true)) return

        try {
            context.assets.open(PARAGRAPH_ASSET_FILENAME).use { input ->
                BufferedReader(InputStreamReader(input, "UTF-8")).use { reader ->
                    val words = reader.readText()
                        .split(Regex("\\s+"))
                        .map { it.trim('|', '॥', ',', '.', '!', '?', ';', ':', '"', '\'', '(', ')', '[', ']', '{', '}', '—', '-') }
                        .filter { it.isNotEmpty() && isCleanGurmukhi(it) }
                        .distinct()

                    db.beginTransaction()
                    for (word in words) {
                        val values = ContentValues().apply { put(COLUMN_WORD, word) }
                        db.insertWithOnConflict(TABLE_WORDS, null, values, SQLiteDatabase.CONFLICT_IGNORE)
                    }
                    db.setTransactionSuccessful()
                    db.endTransaction()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error populating database", e)
        } finally {
            isPopulating.set(false)
        }
    }

    private fun isCleanGurmukhi(word: String): Boolean {
        return word.matches(Regex("^[\\u0A00-\\u0A7F]+$")) && !word.contains(Regex("[0-9੦-੯]"))
    }

    suspend fun deleteAllLearnedWords() = withContext(Dispatchers.IO) {
        val db = writableDatabase
        try {
            db.beginTransaction()
            db.delete(TABLE_WORDS, null, null)
            db.delete(TABLE_WORD_PAIRS, null, null)
            db.setTransactionSuccessful()
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting all words", e)
        } finally {
            db.endTransaction()
            populateDatabaseFromAsset(db)
        }
    }

    // ---------------- Word Table ----------------

    suspend fun addWord(word: String) = withContext(Dispatchers.IO) {
        if (isPopulating.get()) return@withContext
        val w = word.trim()
        if (w.isEmpty() || !isCleanGurmukhi(w)) return@withContext

        val db = writableDatabase
        val values = ContentValues().apply { put(COLUMN_WORD, w) }

        val result = db.insertWithOnConflict(TABLE_WORDS, null, values, SQLiteDatabase.CONFLICT_IGNORE)
        if (result == -1L) {
            db.execSQL("UPDATE $TABLE_WORDS SET $COLUMN_FREQUENCY = $COLUMN_FREQUENCY + 1 WHERE $COLUMN_WORD = ?", arrayOf(w))
        }
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

    suspend fun deleteWord(word: String) {
        val w = word.trim()
        if (w.isEmpty()) return
        writableDatabase.delete(TABLE_WORDS, "$COLUMN_WORD = ?", arrayOf(w))
    }

    suspend fun getAllWords(): List<String> = withContext(Dispatchers.IO) {
        val words = mutableListOf<String>()
        readableDatabase.rawQuery("SELECT $COLUMN_WORD FROM $TABLE_WORDS ORDER BY $COLUMN_WORD ASC", null)?.use { c ->
            val idx = c.getColumnIndex(COLUMN_WORD)
            while (c.moveToNext()) words.add(c.getString(idx))
        }
        words
    }

    // ---------------- Word Pairs, Abbreviations ----------------
    suspend fun addWordPair(previous: String, next: String) = withContext(Dispatchers.IO) {
        if (!isCleanGurmukhi(previous) || !isCleanGurmukhi(next)) return@withContext
        val values = ContentValues().apply { put(COLUMN_PREVIOUS_WORD, previous.trim()); put(COLUMN_NEXT_WORD, next.trim()) }
        writableDatabase.insertWithOnConflict(TABLE_WORD_PAIRS, null, values, SQLiteDatabase.CONFLICT_IGNORE)
    }
    
    suspend fun getNextWordSuggestions(previousWord: String, limit: Int): List<String> = withContext(Dispatchers.IO) {
        val list = mutableListOf<String>()
        val query = "SELECT $COLUMN_NEXT_WORD FROM $TABLE_WORD_PAIRS WHERE $COLUMN_PREVIOUS_WORD = ? ORDER BY $COLUMN_FREQUENCY DESC LIMIT ?"
        readableDatabase.rawQuery(query, arrayOf(previousWord.trim(), limit.toString()))?.use { c ->
            val idx = c.getColumnIndex(COLUMN_NEXT_WORD)
            while (c.moveToNext()) list.add(c.getString(idx))
        }
        list
    }

    suspend fun addAbbreviation(shortcut: String, fullText: String) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply { put(COLUMN_SHORTCUT, shortcut.trim()); put(COLUMN_FULL_TEXT, fullText.trim()) }
        writableDatabase.insertWithOnConflict(TABLE_ABBREVIATIONS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    suspend fun getAbbreviation(shortcut: String): String? = withContext(Dispatchers.IO) {
        var res: String? = null
        readableDatabase.rawQuery("SELECT full_text FROM $TABLE_ABBREVIATIONS WHERE shortcut = ?", arrayOf(shortcut.trim()))?.use {
            if (it.moveToFirst()) res = it.getString(0)
        }
        res
    }

    suspend fun getAllAbbreviations(): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Pair<String, String>>()
        readableDatabase.rawQuery("SELECT shortcut, full_text FROM $TABLE_ABBREVIATIONS", null)?.use {
            while (it.moveToNext()) list.add(Pair(it.getString(0), it.getString(1)))
        }
        list
    }

    suspend fun deleteAbbreviation(shortcut: String) = withContext(Dispatchers.IO) {
        writableDatabase.delete(TABLE_ABBREVIATIONS, "shortcut = ?", arrayOf(shortcut.trim()))
    }

    // ---------------- Clipboard Table ----------------

    suspend fun addClipboardItem(text: String) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply { put(COLUMN_CLIP_TEXT, text) }
        writableDatabase.insert(TABLE_CLIPBOARD, null, values)
    }

    suspend fun getClipboardHistory(searchQuery: String?, limit: Int): List<ClipboardItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<ClipboardItem>()
        val sql = StringBuilder("SELECT _id, clip_text, timestamp, is_pinned FROM $TABLE_CLIPBOARD")
        val args = mutableListOf<String>()

        if (!searchQuery.isNullOrEmpty()) {
            sql.append(" WHERE $COLUMN_CLIP_TEXT LIKE ?")
            args.add("%$searchQuery%")
        }

        // Fetch Pinned items first, then by timestamp
        sql.append(" ORDER BY $COLUMN_CLIP_PINNED DESC, timestamp DESC LIMIT ?")
        args.add(limit.toString())

        readableDatabase.rawQuery(sql.toString(), args.toTypedArray())?.use { c ->
            while (c.moveToNext()) {
                list.add(ClipboardItem(
                    id = c.getLong(0),
                    text = c.getString(1),
                    timestamp = c.getLong(2),
                    isPinned = c.getInt(3) == 1
                ))
            }
        }
        list
    }

    suspend fun updateClipboardPinned(id: Long, isPinned: Boolean) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply { put(COLUMN_CLIP_PINNED, if (isPinned) 1 else 0) }
        writableDatabase.update(TABLE_CLIPBOARD, values, "_id = ?", arrayOf(id.toString()))
    }

    suspend fun deleteClipboardItems(ids: List<Long>) = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        // Optional: Keep pinned items from being batch-deleted if desired
        writableDatabase.execSQL("DELETE FROM $TABLE_CLIPBOARD WHERE _id IN (${ids.joinToString(",")}) AND $COLUMN_CLIP_PINNED = 0")
    }
}
