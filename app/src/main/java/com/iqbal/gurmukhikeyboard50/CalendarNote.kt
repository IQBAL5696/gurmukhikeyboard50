package com.iqbal.gurmukhikeyboard50

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "calendar_notes")
data class CalendarNote(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val dateKey: String, // Format: YYYY-MM-DD
    val note: String,
    val isBookmark: Boolean = false,
    val type: String = "NOTE", // NOTE, BIRTHDAY, ANNIVERSARY, RELIGIOUS
    val reminderTime: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)
