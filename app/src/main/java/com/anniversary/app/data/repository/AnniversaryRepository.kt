package com.anniversary.app.data.repository

import com.anniversary.app.data.dao.AnniversaryDao
import com.anniversary.app.data.entity.Anniversary
import com.anniversary.app.data.entity.AnniversaryType
import kotlinx.coroutines.flow.Flow

class AnniversaryRepository(private val dao: AnniversaryDao) {

    fun getAllAnniversaries(username: String): Flow<List<Anniversary>> =
        dao.getAllAnniversaries(username)

    fun getAnniversariesByType(username: String, type: AnniversaryType): Flow<List<Anniversary>> =
        dao.getAnniversariesByType(username, type)

    fun searchAnniversaries(username: String, query: String): Flow<List<Anniversary>> =
        dao.searchAnniversaries(username, query)

    suspend fun getAnniversaryById(id: Long): Anniversary? = dao.getAnniversaryById(id)

    suspend fun getAnniversariesWithReminder(username: String): List<Anniversary> =
        dao.getAnniversariesWithReminder(username)

    suspend fun insert(anniversary: Anniversary): Long = dao.insert(anniversary)

    suspend fun update(anniversary: Anniversary) = dao.update(anniversary)

    suspend fun delete(anniversary: Anniversary) = dao.delete(anniversary)

    suspend fun deleteByIds(ids: List<Long>) = dao.deleteByIds(ids)
}
