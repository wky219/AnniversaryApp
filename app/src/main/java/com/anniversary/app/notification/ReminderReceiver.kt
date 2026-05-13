package com.anniversary.app.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.anniversary.app.AnniversaryApplication
import com.anniversary.app.R
import com.anniversary.app.ui.main.MainActivity

class ReminderReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_NAME = "extra_name"
        const val EXTRA_DAYS_BEFORE = "extra_days_before"
        const val EXTRA_DATE_TIMESTAMP = "extra_date_timestamp"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val name = intent.getStringExtra(EXTRA_NAME) ?: return
        val daysBefore = intent.getIntExtra(EXTRA_DAYS_BEFORE, 0)
        val dateTimestamp = intent.getLongExtra(EXTRA_DATE_TIMESTAMP, 0L)

        // Prevent duplicate reminders (e.g., after phone reboot)
        if (dateTimestamp != 0L && ReminderHistory.wasRemindedToday(context, name, dateTimestamp)) {
            return
        }

        val contentText = if (daysBefore > 0) {
            "「$name」还有${daysBefore}天就到了"
        } else {
            "「$name」就是今天!"
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, AnniversaryApplication.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("纪念日提醒")
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(name.hashCode(), notification)

        // Mark this reminder as sent today
        if (dateTimestamp != 0L) {
            ReminderHistory.markReminded(context, name, dateTimestamp)
        }
    }
}
