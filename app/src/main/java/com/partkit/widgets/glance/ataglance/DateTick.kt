package com.partkit.widgets.glance.ataglance

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.updateAll
import com.partkit.widgets.data.WeatherStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/**
 * At a Glance의 날짜 갱신. 날짜를 비트맵으로 그리므로 TextClock처럼 스스로 바뀌지 않는다.
 * 자정마다(정확 알람 권한 없이 1분 창 안에서) 그리고 시각·시간대·언어가 바뀔 때 다시 그린다.
 */
object DateTick {

    private const val ACTION_MIDNIGHT = "com.partkit.widgets.action.MIDNIGHT"
    private const val WINDOW_MS = 60_000L

    fun scheduleNextMidnight(context: Context) {
        val zone = ZoneId.systemDefault()
        val next = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        context.getSystemService(AlarmManager::class.java)
            .setWindow(AlarmManager.RTC, next, WINDOW_MS, pendingIntent(context))
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, DateTickReceiver::class.java).setAction(ACTION_MIDNIGHT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}

/** 자정 알람과 시스템의 TIME_SET / TIMEZONE_CHANGED / LOCALE_CHANGED를 받는다. */
class DateTickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                WeatherStore.invalidate(context)
                AtAGlanceWidget().updateAll(context)
                DateTick.scheduleNextMidnight(context)
            } finally {
                pending.finish()
            }
        }
    }
}
