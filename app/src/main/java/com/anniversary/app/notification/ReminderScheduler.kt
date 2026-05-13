package com.anniversary.app.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.util.*
import java.util.concurrent.TimeUnit

object ReminderScheduler {

    private const val TAG = "ReminderScheduler"

    fun scheduleReminder(
        context: Context,
        name: String,
        dateTimestamp: Long,
        reminderDaysBefore: Int
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Calculate reminder time using user's preferred time
        val reminderHour = ReminderSettings.getReminderHour(context)
        val reminderMinute = ReminderSettings.getReminderMinute(context)
        val reminderTime = calculateReminderTime(dateTimestamp, reminderDaysBefore, reminderHour, reminderMinute)

        // If reminder time has already passed today, still schedule for today
        // (AlarmManager will fire it immediately or very soon)
        val now = System.currentTimeMillis()
        if (reminderTime <= now) {
            // Check if the reminder day is today (grace period)
            val reminderDayStart = getStartOfDay(reminderTime)
            val todayStart = getStartOfDay(now)
            if (reminderDayStart == todayStart) {
                // Reminder day is today but the set time has passed -
                // schedule for 1 minute from now as a fallback
                val fallbackTime = now + TimeUnit.MINUTES.toMillis(1)
                scheduleAlarm(alarmManager, fallbackTime, context, name, dateTimestamp, reminderDaysBefore)
                Log.d(TAG, "Reminder time passed, scheduling fallback for '$name' at ${Date(fallbackTime)}")
            } else {
                // Reminder day is already past - skip
                Log.d(TAG, "Reminder day passed for '$name', skipping")
                return
            }
        } else {
            scheduleAlarm(alarmManager, reminderTime, context, name, dateTimestamp, reminderDaysBefore)
            Log.d(TAG, "Scheduled reminder for '$name' at ${Date(reminderTime)}")
        }
    }

    private fun calculateReminderTime(
        dateTimestamp: Long,
        reminderDaysBefore: Int,
        hourOfDay: Int,
        minute: Int
    ): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = dateTimestamp
            add(Calendar.DAY_OF_YEAR, -reminderDaysBefore)
            set(Calendar.HOUR_OF_DAY, hourOfDay)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }

    private fun scheduleAlarm(
        alarmManager: AlarmManager,
        triggerTime: Long,
        context: Context,
        name: String,
        dateTimestamp: Long,
        reminderDaysBefore: Int
    ) {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_NAME, name)
            putExtra(ReminderReceiver.EXTRA_DAYS_BEFORE, reminderDaysBefore)
        }

        val requestCode = (name.hashCode() + dateTimestamp.toInt())
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
        }
    }

    fun cancelReminder(context: Context, name: String, dateTimestamp: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java)
        val requestCode = (name.hashCode() + dateTimestamp.toInt())
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
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
