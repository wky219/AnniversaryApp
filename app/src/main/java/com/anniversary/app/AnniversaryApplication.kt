package com.anniversary.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import com.anniversary.app.data.database.AnniversaryDatabase
import com.anniversary.app.data.repository.AnniversaryRepository

class AnniversaryApplication : Application() {

    val database by lazy { AnniversaryDatabase.getDatabase(this) }
    val repository by lazy { AnniversaryRepository(database.anniversaryDao()) }

    override fun onCreate() {
        super.onCreate()
        restoreNightMode()
        createNotificationChannel()
    }

    private fun restoreNightMode() {
        val savedMode = getSharedPreferences("app_settings", MODE_PRIVATE)
            .getInt("night_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(savedMode)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "纪念日提醒",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "纪念日到期提醒通知"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "anniversary_reminder_channel"
    }
}
