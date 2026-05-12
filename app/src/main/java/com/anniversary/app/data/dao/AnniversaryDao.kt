package com.anniversary.app.data.dao

import androidx.room.*
import com.anniversary.app.data.entity.Anniversary
import com.anniversary.app.data.entity.AnniversaryType
import kotlinx.coroutines.flow.Flow

@Dao
interface AnniversaryDao {

    @Query("SELECT * FROM anniversaries ORDER BY date ASC")
    fun getAllAnniversaries(): Flow<List<Anniversary>>

    @Query("SELECT * FROM anniversaries WHERE type = :type ORDER BY date ASC")
    fun getAnniversariesByType(type: AnniversaryType): Flow<List<Anniversary>>

    @Query("SELECT * FROM anniversaries WHERE name LIKE '%' || :query || '%' ORDER BY date ASC")
    fun searchAnniversaries(query: String): Flow<List<Anniversary>>

    @Query("SELECT * FROM anniversaries WHERE id = :id")
    suspend fun getAnniversaryById(id: Long): Anniversary?

    @Query("SELECT * FROM anniversaries WHERE reminderDays >= 0")
    suspend fun getAnniversariesWithReminder(): List<Anniversary>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(anniversary: Anniversary): Long

    @Update
    suspend fun update(anniversary: Anniversary)

    @Delete
    suspend fun delete(anniversary: Anniversary)

    @Query("DELETE FROM anniversaries WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM anniversaries")
    suspend fun getCount(): Int
}
