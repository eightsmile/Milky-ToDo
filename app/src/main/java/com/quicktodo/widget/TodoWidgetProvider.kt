package com.quicktodo.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.quicktodo.MainActivity
import com.quicktodo.R
import com.quicktodo.data.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class TodoWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) updateWidget(context, manager, id)
    }

    companion object {
        fun updateWidget(context: Context, manager: AppWidgetManager, id: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_todo)

            // Open app on tap (title area)
            val intent = PendingIntent.getActivity(context, 0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_root, intent)
            views.setOnClickPendingIntent(R.id.widget_title, intent)

            // Voice button → open voice
            val voiceIntent = PendingIntent.getActivity(context, 1,
                Intent(context, MainActivity::class.java).putExtra("open_voice", true),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_voice_btn, voiceIntent)

            try {
                val db = AppDatabase.getInstance(context)
                val todos = runBlocking { db.todoDao().getAllActive().first() }
                val pending = todos.filter { !it.isDone }

                val sb = StringBuilder()
                if (pending.isEmpty()) {
                    sb.append("No todos")
                } else {
                    for (t in pending) {
                        val dateTag = t.dueDate?.let {
                            val fmt = java.text.SimpleDateFormat("MM/dd", java.util.Locale.getDefault())
                            " 📅${fmt.format(java.util.Date(it))}"
                        } ?: ""
                        sb.append("• ").append(t.title).append(dateTag).append('\n')
                    }
                }

                val result = sb.toString().trimEnd()
                views.setTextViewText(R.id.widget_todo_text, result)
                views.setTextViewText(R.id.widget_title, "Milky ToDo (${pending.size})")
            } catch (e: Exception) {
                views.setTextViewText(R.id.widget_title, "Milky ToDo")
                views.setTextViewText(R.id.widget_todo_text, "Error: ${e.localizedMessage?.take(60) ?: "Unknown"}")
            }

            manager.updateAppWidget(id, views)
        }

        /** Call from app to refresh all widget instances */
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                android.content.ComponentName(context, TodoWidgetProvider::class.java))
            for (id in ids) updateWidget(context, manager, id)
        }
    }
}
