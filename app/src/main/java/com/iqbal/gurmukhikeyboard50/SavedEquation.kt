package com.iqbal.gurmukhikeyboard50

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_equations")
data class SavedEquation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val equation: String,
    val timestamp: Long = System.currentTimeMillis()
)
