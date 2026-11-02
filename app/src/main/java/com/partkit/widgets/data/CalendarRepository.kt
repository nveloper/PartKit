package com.partkit.widgets.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract

data class UpcomingEvent(val eventId: Long, val title: String, val begin: Long, val end: Long)

/** At a Glance의 일정 카드용. 진행 중이거나 곧 시작하는 종일이 아닌 일정 하나를 찾는다. */
object CalendarRepository {

    private const val LOOKAHEAD_MS = 60 * 60 * 1000L

    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun nextEvent(context: Context, now: Long = System.currentTimeMillis()): UpcomingEvent? {
        if (!hasPermission(context)) return null
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, now - 12 * LOOKAHEAD_MS)
            ContentUris.appendId(it, now + LOOKAHEAD_MS)
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
        )
        val selection = "${CalendarContract.Instances.ALL_DAY} = 0 AND " +
            "${CalendarContract.Instances.VISIBLE} = 1 AND " +
            "${CalendarContract.Instances.END} > ? AND " +
            "${CalendarContract.Instances.BEGIN} < ?"
        val args = arrayOf(now.toString(), (now + LOOKAHEAD_MS).toString())

        return runCatching {
            context.contentResolver.query(uri, projection, selection, args, "${CalendarContract.Instances.BEGIN} ASC")
                ?.use { cursor ->
                    if (!cursor.moveToFirst()) return@use null
                    UpcomingEvent(
                        eventId = cursor.getLong(0),
                        title = cursor.getString(1).orEmpty(),
                        begin = cursor.getLong(2),
                        end = cursor.getLong(3),
                    )
                }
        }.getOrNull()
    }
}
