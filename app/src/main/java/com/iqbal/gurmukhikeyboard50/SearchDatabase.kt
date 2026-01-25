package com.iqbal.gurmukhikeyboard50

import androidx.room.*

@Entity(tableName = "pages")
data class PageEntity(
    @field:PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val url: String,
    val content: String
)

@Dao
interface PageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(page: PageEntity)

    @Query("SELECT * FROM pages WHERE content LIKE '%' || :query || '%' OR title LIKE '%' || :query || '%' LIMIT 10")
    suspend fun search(query: String): List<PageEntity>
}

@Database(entities = [PageEntity::class], version = 1, exportSchema = false)
abstract class SearchDatabase : RoomDatabase() {
    abstract fun pageDao(): PageDao
}
