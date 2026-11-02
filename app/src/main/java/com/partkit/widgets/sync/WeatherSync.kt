package com.partkit.widgets.sync

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.partkit.widgets.data.OpenMeteo
import com.partkit.widgets.data.WeatherStore
import com.partkit.widgets.glance.ataglance.AtAGlanceWidget
import com.partkit.widgets.glance.weather.CurrentWeatherWidget
import com.partkit.widgets.glance.weather.DailyWeatherWidget
import com.partkit.widgets.glance.weather.ForecastWeatherWidget
import com.partkit.widgets.glance.weather.HourlyWeatherWidget
import java.util.concurrent.TimeUnit

object WeatherSync {

    private const val PERIODIC = "weather-periodic"
    private const val ONE_SHOT = "weather-now"

    /** 캐시가 이보다 오래되면 위젯이 그려질 때 즉시 새로고침을 요청한다. */
    const val STALE_MS = 30 * 60 * 1000L

    private val networkConstraint = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /** 30분 주기 새로고침. 이미 등록돼 있으면 그대로 둔다. */
    fun ensurePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<WeatherSyncWorker>(30, TimeUnit.MINUTES)
            .setConstraints(networkConstraint)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun refreshNow(context: Context, force: Boolean = false) {
        val request = OneTimeWorkRequestBuilder<WeatherSyncWorker>()
            .setConstraints(networkConstraint)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        val policy = if (force) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP
        WorkManager.getInstance(context).enqueueUniqueWork(ONE_SHOT, policy, request)
    }

    /** 위젯 렌더링 경로에서 호출: 주기 작업을 보장하고, 캐시가 낡았으면 새로고침을 건다. */
    fun onWidgetRendered(context: Context) {
        ensurePeriodic(context)
        val snapshot = WeatherStore.current(context).snapshot
        if (snapshot == null || System.currentTimeMillis() - snapshot.fetchedAt > STALE_MS) {
            refreshNow(context)
        }
    }

    suspend fun updateAllWidgets(context: Context) {
        AtAGlanceWidget().updateAll(context)
        CurrentWeatherWidget().updateAll(context)
        ForecastWeatherWidget().updateAll(context)
        HourlyWeatherWidget().updateAll(context)
        DailyWeatherWidget().updateAll(context)
    }
}

class WeatherSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val location = WeatherStore.current(applicationContext).location
        return try {
            val json = OpenMeteo.fetchForecastJson(location.latitude, location.longitude)
            WeatherStore.saveForecast(applicationContext, json, System.currentTimeMillis())
            WeatherSync.updateAllWidgets(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Log.w("WeatherSyncWorker", "Weather refresh failed", e)
            // 캘린더 일정 등 날씨 외 정보는 계속 갱신되도록 한다.
            WeatherStore.invalidate(applicationContext)
            WeatherSync.updateAllWidgets(applicationContext)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
