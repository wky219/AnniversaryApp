package com.anniversary.app.data.dao

import androidx.room.*
import com.anniversary.app.data.entity.Anniversary
import com.anniversary.app.data.entity.AnniversaryType
import kotlinx.coroutines.flow.Flow

@Dao
interface AnniversaryDao {

    @Query("SELECT * FROM anniversaries WHERE username = :username ORDER BY date ASC")
    fun getAllAnniversaries(username: String): Flow<List<Anniversary>>

    @Query("SELECT * FROM anniversaries WHERE username = :username ORDER BY date ASC")
    suspend fun getAllAnniversariesStatic(username: String): List<Anniversary>

    @Query("SELECT * FROM anniversaries WHERE username = :username AND type = :type ORDER BY date ASC")
    fun getAnniversariesByType(username: String, type: AnniversaryType): Flow<List<Anniversary>>

    @Query("SELECT * FROM anniversaries WHERE username = :username AND name LIKE '%' || :query || '%' ORDER BY date ASC")
    fun searchAnniversaries(username: String, query: String): Flow<List<Anniversary>>

    @Query("SELECT * FROM anniversaries WHERE id = :id")
    suspend fun getAnniversaryById(id: Long): Anniversary?

    @Query("SELECT * FROM anniversaries WHERE username = :username AND reminderDays >= 0")
    suspend fun getAnniversariesWithReminder(username: String): List<Anniversary>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(anniversary: Anniversary): Long

    @Update
    suspend fun update(anniversary: Anniversary)

    @Delete
    suspend fun delete(anniversary: Anniversary)

    @Query("DELETE FROM anniversaries WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM anniversaries WHERE username = :username")
    suspend fun getCount(username: String): Int
}
