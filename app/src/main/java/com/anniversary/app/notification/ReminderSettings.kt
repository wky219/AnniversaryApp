package com.anniversary.app.notification

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages reminder time settings using SharedPreferences.
 * Default reminder time is 9:00 AM.
 */
object ReminderSettings {

    private const val PREFS_NAME = "reminder_settings"
    private const val KEY_REMINDER_HOUR = "reminder_hour"
    private const val KEY_REMINDER_MINUTE = "reminder_minute"

    const val DEFAULT_HOUR = 9
    const val DEFAULT_MINUTE = 0

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getReminderHour(context: Context): Int {
        return getPrefs(context).getInt(KEY_REMINDER_HOUR, DEFAULT_HOUR)
    }

    fun getReminderMinute(context: Context): Int {
        return getPrefs(context).getInt(KEY_REMINDER_MINUTE, DEFAULT_MINUTE)
    }

    fun setReminderTime(context: Context, hour: Int, minute: Int) {
        getPrefs(context).edit()
            .putInt(KEY_REMINDER_HOUR, hour)
            .putInt(KEY_REMINDER_MINUTE, minute)
            .apply()
    }

    /**
     * Returns a display string like "09:00"
     */
    fun getReminderTimeDisplay(context: Context): String {
        val hour = getReminderHour(context)
        val minute = getReminderMinute(context)
        return String.format("%02d:%02d", hour, minute)
    }
}
