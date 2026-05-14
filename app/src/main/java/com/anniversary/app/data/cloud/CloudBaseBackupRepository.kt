package com.anniversary.app.data.cloud

import android.content.Context
import android.util.Log
import com.anniversary.app.data.database.AnniversaryDatabase
import com.anniversary.app.data.entity.Anniversary
import com.anniversary.app.data.entity.AnniversaryType
import com.anniversary.app.notification.ReminderScheduler
import com.anniversary.app.ui.login.AuthManager
import com.anniversary.app.ui.widget.AnniversaryWidgetProvider

/**
 * Repository for backing up and restoring anniversary data via CloudBase.
 * Uses CloudBase MySQL REST API (/v1/rdb/rest/) with username-based isolation.
 * Local Room remains the primary storage; CloudBase is used as cloud backup.
 */
object CloudBaseBackupRepository {

    private const val TAG = "CloudBaseBackup"
    private const val TABLE = "anniversaries"

    /**
     * Backup all local data to CloudBase (full overwrite for this user).
     * Returns the number of records backed up, or -1 on failure.
     */
    suspend fun backupToCloud(context: Context): Int {
        return try {
            val username = AuthManager.getLoggedInPhone(context)
            if (username.isNullOrEmpty()) {
                Log.e(TAG, "No logged-in user, cannot backup")
                return -1
            }

            val database = AnniversaryDatabase.getDatabase(context)
            val anniversaries = database.anniversaryDao().getAllAnniversariesStatic(username)
            Log.d(TAG, "Local records to backup for user [$username]: ${anniversaries.size}")

            if (anniversaries.isEmpty()) {
                return 0
            }

            // Clear this user's cloud data first
            val deleteResult = CloudBaseManager.deleteRecordsByUser(TABLE, username)
            Log.d(TAG, "Delete cloud records for user [$username]: $deleteResult")

            // Insert each record with username
            var count = 0
            for (ann in anniversaries) {
                val data = anniversaryToMap(ann, username)
                Log.d(TAG, "Inserting record: ${ann.name}")
                val result = CloudBaseManager.insertRecord(TABLE, data)
                if (result != null) {
                    count++
                } else {
                    Log.e(TAG, "Failed to insert record: ${ann.name}")
                }
            }
            Log.d(TAG, "Backup complete: $count/${anniversaries.size} records")
            count
        } catch (e: Exception) {
            Log.e(TAG, "Backup exception: ${e.message}", e)
            -1
        }
    }

    /**
     * Restore all data from CloudBase to local Room (full overwrite).
     * Only restores data belonging to the current logged-in user.
     * Returns the number of records restored, or -1 on failure.
     */
    suspend fun restoreFromCloud(context: Context): Int {
        return try {
            val username = AuthManager.getLoggedInPhone(context)
            if (username.isNullOrEmpty()) {
                Log.e(TAG, "No logged-in user, cannot restore")
                return -1
            }

            val records = CloudBaseManager.queryRecordsByUser(TABLE, username)
            Log.d(TAG, "Cloud records to restore for user [$username]: ${records.size}")

            if (records.isEmpty()) {
                Log.e(TAG, "No cloud records found for user [$username], aborting restore")
                return -1
            }

            val database = AnniversaryDatabase.getDatabase(context)

            // Convert cloud records to Anniversary entities
            val anniversaries = records.mapNotNull { mapToAnniversary(it) }

            if (anniversaries.isEmpty()) {
                Log.e(TAG, "Failed to parse any cloud records, aborting restore")
                return -1
            }

            // Clear local data for this user and insert cloud data
            val existing = database.anniversaryDao().getAllAnniversariesStatic(username)
            for (ann in existing) {
                database.anniversaryDao().delete(ann)
            }

            // Insert cloud records with username
            var count = 0
            for (ann in anniversaries) {
                val id = database.anniversaryDao().insert(ann.copy(id = 0, username = username))
                if (id > 0) {
                    count++
                    // Reschedule reminders
                    if (ann.reminderDays > 0) {
                        ReminderScheduler.scheduleReminder(
                            context,
                            ann.name,
                            ann.date,
                            ann.reminderDays
                        )
                    }
                }
            }

            // Refresh widget
            AnniversaryWidgetProvider.notifyDataChanged(context)

            Log.d(TAG, "Restore complete: $count/${anniversaries.size} records")
            count
        } catch (e: Exception) {
            Log.e(TAG, "Restore exception: ${e.message}", e)
            -1
        }
    }

    private fun anniversaryToMap(ann: Anniversary, username: String): Map<String, Any> {
        return mapOf(
            "username" to username,
            "name" to ann.name,
            "date" to ann.date,
            "type" to ann.type.name,
            "note" to ann.note,
            "isRepeatYearly" to ann.isRepeatYearly,
            "reminderDays" to ann.reminderDays,
            "isLunar" to ann.isLunar,
            "lunarMonth" to ann.lunarMonth,
            "lunarDay" to ann.lunarDay,
            "lunarIsLeapMonth" to ann.lunarIsLeapMonth,
            "createdAt" to ann.createdAt,
            "updatedAt" to ann.updatedAt
        )
    }

    private fun mapToAnniversary(map: Map<String, Any>): Anniversary? {
        return try {
            val typeStr = map["type"] as? String ?: "CUSTOM"
            val type = try {
                AnniversaryType.valueOf(typeStr)
            } catch (e: Exception) {
                AnniversaryType.CUSTOM
            }

            Anniversary(
                id = 0, // Let Room auto-generate
                username = map["username"] as? String ?: "",
                name = map["name"] as? String ?: return null,
                date = (map["date"] as? Number)?.toLong() ?: return null,
                type = type,
                note = map["note"] as? String ?: "",
                isRepeatYearly = map["isRepeatYearly"] as? Boolean ?: false,
                reminderDays = (map["reminderDays"] as? Number)?.toInt() ?: -1,
                isLunar = map["isLunar"] as? Boolean ?: false,
                lunarMonth = (map["lunarMonth"] as? Number)?.toInt() ?: 0,
                lunarDay = (map["lunarDay"] as? Number)?.toInt() ?: 0,
                lunarIsLeapMonth = map["lunarIsLeapMonth"] as? Boolean ?: false,
                createdAt = (map["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                updatedAt = (map["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
            )
        } catch (e: Exception) {
            null
        }
    }
}
