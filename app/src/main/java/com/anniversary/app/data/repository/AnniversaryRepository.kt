package com.anniversary.app.data.repository

import com.anniversary.app.data.dao.AnniversaryDao
import com.anniversary.app.data.entity.Anniversary
import com.anniversary.app.data.entity.AnniversaryType
import kotlinx.coroutines.flow.Flow

class AnniversaryRepository(private val dao: AnniversaryDao) {

    fun getAllAnniversaries(): Flow<List<Anniversary>> = dao.getAllAnniversaries()

    fun getAnniversariesByType(type: AnniversaryType): Flow<List<Anniversary>> =
        dao.getAnniversariesByType(type)

    fun searchAnniversaries(query: String): Flow<List<Anniversary>> =
        dao.searchAnniversaries(query)

    suspend fun getAnniversaryById(id: Long): Anniversary? = dao.getAnniversaryById(id)

    suspend fun getAnniversariesWithReminder(): List<Anniversary> =
        dao.getAnniversariesWithReminder()

    suspend fun insert(anniversary: Anniversary): Long = dao.insert(anniversary)

    suspend fun update(anniversary: Anniversary) = dao.update(anniversary)

    suspend fun delete(anniversary: Anniversary) = dao.delete(anniversary)

    suspend fun deleteByIds(ids: List<Long>) = dao.deleteByIds(ids)
}
