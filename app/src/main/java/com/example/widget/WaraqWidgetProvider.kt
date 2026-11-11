package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.local.WaraqDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WaraqWidgetProvider : AppWidgetProvider() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        scope.launch {
            updateWidgetsInternal(context, appWidgetManager, appWidgetIds)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE ||
            intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == ACTION_UPDATE_ALL_WIDGETS
        ) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, WaraqWidgetProvider::class.java))
            scope.launch {
                updateWidgetsInternal(context, appWidgetManager, ids)
            }
        }
    }

    private suspend fun updateWidgetsInternal(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        if (appWidgetIds.isEmpty()) return

        val db = WaraqDatabase.getInstance(context)
        val recentBooks = try {
            db.bookDao().getRecentBooksSync(2)
        } catch (e: Exception) {
            emptyList()
        }

        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_waraq_layout)

            // Open app on header click
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openAppPendingIntent = PendingIntent.getActivity(
                context,
                0,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_action_open_app, openAppPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_title, openAppPendingIntent)

            if (recentBooks.isEmpty()) {
                views.setViewVisibility(R.id.widget_empty_view, View.VISIBLE)
                views.setViewVisibility(R.id.widget_content_container, View.GONE)
                views.setOnClickPendingIntent(R.id.widget_empty_view, openAppPendingIntent)
            } else {
                views.setViewVisibility(R.id.widget_empty_view, View.GONE)
                views.setViewVisibility(R.id.widget_content_container, View.VISIBLE)

                // First Book
                val book1 = recentBooks[0]
                views.setTextViewText(R.id.widget_book_1_title, book1.title)
                views.setTextViewText(R.id.widget_book_1_type, book1.fileType)
                val isPdf1 = book1.fileType.equals("PDF", ignoreCase = true)
                views.setInt(
                    R.id.widget_book_1_type,
                    "setBackgroundResource",
                    if (isPdf1) R.drawable.bg_badge_pdf else R.drawable.bg_badge_docx
                )

                val progress1 = book1.progressPercent.toInt().coerceIn(0, 100)
                views.setProgressBar(R.id.widget_book_1_progress, 100, progress1, false)

                val unitLabel1 = if (isPdf1) "صفحة" else "فقرة"
                views.setTextViewText(
                    R.id.widget_book_1_page,
                    "$unitLabel1 ${book1.lastReadPage} من ${book1.totalPages.coerceAtLeast(1)}"
                )

                val book1Intent = Intent(context, MainActivity::class.java).apply {
                    action = ACTION_OPEN_BOOK
                    putExtra(EXTRA_BOOK_ID, book1.id)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val book1PendingIntent = PendingIntent.getActivity(
                    context,
                    book1.id.toInt(),
                    book1Intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_book_1, book1PendingIntent)
                views.setOnClickPendingIntent(R.id.widget_book_1_action, book1PendingIntent)

                // Second Book (if available)
                if (recentBooks.size >= 2) {
                    val book2 = recentBooks[1]
                    views.setViewVisibility(R.id.widget_book_2, View.VISIBLE)
                    views.setTextViewText(R.id.widget_book_2_title, book2.title)
                    views.setTextViewText(R.id.widget_book_2_type, book2.fileType)
                    val isPdf2 = book2.fileType.equals("PDF", ignoreCase = true)
                    views.setInt(
                        R.id.widget_book_2_type,
                        "setBackgroundResource",
                        if (isPdf2) R.drawable.bg_badge_pdf else R.drawable.bg_badge_docx
                    )

                    val progress2 = book2.progressPercent.toInt().coerceIn(0, 100)
                    views.setProgressBar(R.id.widget_book_2_progress, 100, progress2, false)

                    val unitLabel2 = if (isPdf2) "صفحة" else "فقرة"
                    views.setTextViewText(
                        R.id.widget_book_2_page,
                        "$unitLabel2 ${book2.lastReadPage} من ${book2.totalPages.coerceAtLeast(1)}"
                    )

                    val book2Intent = Intent(context, MainActivity::class.java).apply {
                        action = ACTION_OPEN_BOOK
                        putExtra(EXTRA_BOOK_ID, book2.id)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    val book2PendingIntent = PendingIntent.getActivity(
                        context,
                        book2.id.toInt(),
                        book2Intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_book_2, book2PendingIntent)
                    views.setOnClickPendingIntent(R.id.widget_book_2_action, book2PendingIntent)
                } else {
                    views.setViewVisibility(R.id.widget_book_2, View.GONE)
                }
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    companion object {
        const val ACTION_OPEN_BOOK = "com.example.waraq.ACTION_OPEN_BOOK"
        const val EXTRA_BOOK_ID = "extra_book_id"
        const val ACTION_UPDATE_ALL_WIDGETS = "com.example.waraq.ACTION_UPDATE_ALL_WIDGETS"

        fun updateAllWidgets(context: Context) {
            val intent = Intent(context, WaraqWidgetProvider::class.java).apply {
                action = ACTION_UPDATE_ALL_WIDGETS
            }
            context.sendBroadcast(intent)
        }
    }
}
