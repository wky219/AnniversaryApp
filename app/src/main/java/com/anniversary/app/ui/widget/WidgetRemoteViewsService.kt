package com.anniversary.app.ui.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.anniversary.app.R
import com.anniversary.app.data.database.AnniversaryDatabase
import com.anniversary.app.data.entity.Anniversary
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
        val database = AnniversaryDatabase.getDatabase(context)
        anniversaries = runBlocking {
            database.anniversaryDao().getAllAnniversaries().first()
        }.sortedBy { anniversary ->
            DateUtils.getDaysUntilNext(anniversary)
        }.take(5) // Show top 5 upcoming
    }

    override fun onDestroy() {
        anniversaries = emptyList()
    }

    override fun getCount(): Int = anniversaries.size

    override fun getViewAt(position: Int): RemoteViews {
        val anniversary = anniversaries[position]
        val views = RemoteViews(context.packageName, R.layout.widget_item)

        views.setTextViewText(R.id.widgetItemName, anniversary.name)
        views.setTextViewText(
            R.id.widgetItemDays,
            DateUtils.getCountdownText(anniversary)
        )

        return views
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = anniversaries[position].id

    override fun hasStableIds(): Boolean = true
}
