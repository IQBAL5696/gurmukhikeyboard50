package com.iqbal.gurmukhikeyboard50

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarNoteDao {
    @Query("SELECT * FROM calendar_notes WHERE dateKey = :dateKey")
    fun getNotesForDate(dateKey: String): Flow<List<CalendarNote>>

    @Query("SELECT * FROM calendar_notes WHERE isBookmark = 1")
    fun getBookmarks(): Flow<List<CalendarNote>>

    @Query("SELECT * FROM calendar_notes ORDER BY createdAt DESC")
    fun getAllNotes(): Flow<List<CalendarNote>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: CalendarNote)

    @Delete
    suspend fun deleteNote(note: CalendarNote)

    @Query("DELETE FROM calendar_notes WHERE dateKey = :dateKey")
    suspend fun deleteNotesForDate(dateKey: String)
}
