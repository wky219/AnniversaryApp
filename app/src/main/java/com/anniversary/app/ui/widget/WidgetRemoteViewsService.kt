package com.anniversary.app.ui.widget

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.anniversary.app.R
import com.anniversary.app.data.database.AnniversaryDatabase
import com.anniversary.app.data.entity.Anniversary
import com.anniversary.app.ui.detail.DetailActivity
import com.anniversary.app.util.DateUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class WidgetRemoteViewsService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return WidgetRemoteViewsFactory(applicationContext)
    }
}

class WidgetRemoteViewsFactory(
    private val context: Context
) : RemoteViewsService.RemoteViewsFactory {

    private var anniversaries: List<Anniversary> = emptyList()

    override fun onCreate() {}

    override fun onDataSetChanged() {
        try {
            val database = AnniversaryDatabase.getDatabase(context)
            anniversaries = runBlocking {
                database.anniversaryDao().getAllAnniversaries().first()
            }.sortedBy { anniversary ->
                DateUtils.getDaysUntilNext(anniversary)
            }.take(10) // Show top 10 upcoming
        } catch (e: Exception) {
            Log.e("WidgetFactory", "Error loading data", e)
            anniversaries = emptyList()
        }
    }

    override fun onDestroy() {
        anniversaries = emptyList()
    }

    override fun getCount(): Int = anniversaries.size

    override fun getViewAt(position: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_item)
        try {
            val anniversary = anniversaries[position]
            views.setTextViewText(R.id.widgetItemName, anniversary.name)
            views.setTextViewText(
                R.id.widgetItemDays,
                DateUtils.getCountdownText(anniversary)
            )
            // Set fill-in intent for click handling (must use with setPendingIntentTemplate)
            val fillInIntent = Intent().apply {
                putExtra(DetailActivity.EXTRA_ANNIVERSARY_ID, anniversary.id)
            }
            views.setOnClickFillInIntent(R.id.widgetItemRoot, fillInIntent)
        } catch (e: Exception) {
            Log.e("WidgetFactory", "Error binding view at position $position", e)
        }
        return views
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long =
        anniversaries.getOrNull(position)?.id ?: position.toLong()

    override fun hasStableIds(): Boolean = true
}
