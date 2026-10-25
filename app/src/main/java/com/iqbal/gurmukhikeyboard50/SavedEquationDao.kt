package com.iqbal.gurmukhikeyboard50

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedEquationDao {
    @Query("SELECT * FROM saved_equations ORDER BY timestamp DESC")
    fun getAll(): Flow<List<SavedEquation>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(equation: SavedEquation)

    @Delete
    suspend fun delete(equation: SavedEquation)

    @Query("DELETE FROM saved_equations")
    suspend fun deleteAll()
}
