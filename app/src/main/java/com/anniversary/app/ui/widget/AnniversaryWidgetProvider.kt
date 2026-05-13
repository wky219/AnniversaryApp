package com.anniversary.app.ui.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.RemoteViews
import com.anniversary.app.R
import android.app.PendingIntent
import com.anniversary.app.ui.detail.DetailActivity

class AnniversaryWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_UPDATE_WIDGET) {
            notifyDataChanged(context)
        }
    }

    /**
     * Notify all widget instances to refresh their data.
     * Can be called from anywhere via the companion method.
     */
    private fun notifyDataChanged(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, AnniversaryWidgetProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
        for (appWidgetId in appWidgetIds) {
            appWidgetManager.notifyAppWidgetViewDataChanged(
                appWidgetId, R.id.widgetListView
            )
        }
    }

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        try {
            val intent = Intent(context, WidgetRemoteViewsService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }

            val views = RemoteViews(context.packageName, R.layout.widget_anniversary).apply {
                setRemoteAdapter(R.id.widgetListView, intent)
                setEmptyView(R.id.widgetListView, R.id.emptyView)
            }

            // Use setPendingIntentTemplate for ListView with remote adapter
            // (setOnClickPendingIntent is NOT supported on collection views)
            val templateIntent = Intent(context, DetailActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context, appWidgetId, templateIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setPendingIntentTemplate(R.id.widgetListView, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
            appWidgetManager.notifyAppWidgetViewDataChanged(
                appWidgetId, R.id.widgetListView
            )
        } catch (e: Exception) {
            Log.e("WidgetProvider", "Error updating widget $appWidgetId", e)
        }
    }

    override fun onEnabled(context: Context) {}

    override fun onDisabled(context: Context) {}

    companion object {
        const val ACTION_UPDATE_WIDGET = "com.anniversary.app.ACTION_UPDATE_WIDGET"

        /**
         * Call this from any Activity to notify widgets that data has changed.
         */
        fun notifyDataChanged(context: Context) {
            val intent = Intent(context, AnniversaryWidgetProvider::class.java).apply {
                action = ACTION_UPDATE_WIDGET
            }
            context.sendBroadcast(intent)
        }
    }
}
