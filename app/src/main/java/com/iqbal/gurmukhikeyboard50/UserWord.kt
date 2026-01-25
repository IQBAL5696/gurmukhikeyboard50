package com.iqbal.gurmukhikeyboard50

import androidx.room.*

@Entity(tableName = "user_words")
data class UserWord(
    @PrimaryKey val word: String,
    @ColumnInfo(name = "frequency") var frequency: Int = 1
)

@Dao
interface UserWordDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(userWord: UserWord): Long

    @Query("UPDATE user_words SET frequency = frequency + 1 WHERE word = :word")
    fun incrementFrequency(word: String)

    @Query("SELECT word FROM user_words WHERE word LIKE :query || '%' ORDER BY frequency DESC LIMIT 10")
    fun getSuggestions(query: String): List<String>

    @Query("SELECT * FROM user_words WHERE word = :word")
    fun getWord(word: String): UserWord?

    @Query("SELECT * FROM user_words ORDER BY word ASC")
    fun getAllWords(): List<UserWord>

    @Delete
    fun delete(userWord: UserWord)

    @Query("DELETE FROM user_words WHERE word = :word")
    fun deleteWord(word: String)
}

@Database(entities = [UserWord::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userWordDao(): UserWordDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "gurmukhi_keyboard_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
