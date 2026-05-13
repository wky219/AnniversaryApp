package com.anniversary.app.notification

import android.content.Context
import android.content.SharedPreferences
import java.util.Calendar

/**
 * Tracks which reminders have already been triggered today,
 * to prevent duplicate notifications after reboot or reschedule.
 */
object ReminderHistory {

    private const val PREFS_NAME = "reminder_history"
    private const val KEY_LAST_CLEANUP = "last_cleanup"

    /** Returns true if this reminder was already triggered today. */
    fun wasRemindedToday(context: Context, name: String, dateTimestamp: Long): Boolean {
        val key = buildKey(name, dateTimestamp)
        val lastRemindedDay = getPrefs(context).getLong(key, -1L)
        val today = getStartOfDay(System.currentTimeMillis())
        return lastRemindedDay == today
    }

    /** Marks that a reminder was triggered today. */
    fun markReminded(context: Context, name: String, dateTimestamp: Long) {
        val today = getStartOfDay(System.currentTimeMillis())
        getPrefs(context).edit()
            .putLong(buildKey(name, dateTimestamp), today)
            .apply()
        // Periodically clean up old entries
        cleanupIfNeeded(context)
    }

    private fun buildKey(name: String, dateTimestamp: Long): String {
        return "reminded_${name.hashCode()}_${dateTimestamp}"
    }

    /**
     * Remove entries older than 7 days to prevent SharedPreferences from growing indefinitely.
     */
    private fun cleanupIfNeeded(context: Context) {
        val prefs = getPrefs(context)
        val lastCleanup = prefs.getLong(KEY_LAST_CLEANUP, 0L)
        val now = System.currentTimeMillis()

        // Cleanup once per day at most
        if (now - lastCleanup < 86_400_000L) return

        val threshold = getStartOfDay(now) - 7 * 86_400_000L // 7 days ago
        val editor = prefs.edit()
        for (entry in prefs.all) {
            if (entry.key == KEY_LAST_CLEANUP) continue
            if (entry.value is Long && (entry.value as Long) < threshold) {
                editor.remove(entry.key)
            }
        }
        editor.putLong(KEY_LAST_CLEANUP, now).apply()
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun getStartOfDay(timestamp: Long): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }
}
