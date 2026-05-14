package com.anniversary.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.anniversary.app.data.database.AnniversaryDatabase
import com.anniversary.app.data.repository.AnniversaryRepository
import com.anniversary.app.ui.login.AuthManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Reschedule all reminders after boot
            rescheduleAllReminders(context)
        }
    }

    private fun rescheduleAllReminders(context: Context) {
        val database = AnniversaryDatabase.getDatabase(context)
        val repository = AnniversaryRepository(database.anniversaryDao())
        val username = AuthManager.getLoggedInPhone(context)

        CoroutineScope(Dispatchers.IO).launch {
            val anniversaries = repository.getAnniversariesWithReminder(username)
            anniversaries.forEach { anniversary ->
                if (anniversary.reminderDays > 0) {
                    ReminderScheduler.scheduleReminder(
                        context,
                        anniversary.name,
                        anniversary.date,
                        anniversary.reminderDays
                    )
                }
            }
        }
    }
}
