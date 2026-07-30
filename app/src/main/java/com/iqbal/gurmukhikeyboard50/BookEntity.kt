package com.iqbal.gurmukhikeyboard50

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val path: String, // Can be a URL for default books or local file path/URI for custom books
    val fileName: String,
    val isCustom: Boolean = false,
    val lastPage: Int = 0
)

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY id DESC")
    fun getAllBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE path = :path LIMIT 1")
    suspend fun getBookByPath(path: String): BookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: BookEntity)

    @Query("UPDATE books SET lastPage = :page WHERE path = :path")
    suspend fun updateLastPage(path: String, page: Int)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteBook(id: Int)
}
