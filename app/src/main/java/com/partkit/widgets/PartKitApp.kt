package com.partkit.widgets

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.util.Log
import androidx.core.content.edit
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.partkit.widgets.data.LocationResolver
import com.partkit.widgets.data.WeatherStore
import com.partkit.widgets.sync.WeatherSync

class PartKitApp : Application() {

    override fun onCreate() {
        super.onCreate()
        LocaleSync.check(this)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        LocaleSync.check(this)
    }
}

/**
 * 언어가 바뀌면 모든 위젯을 다시 그린다.
 *
 * 위젯 문구는 앱 프로세스에서 문자열 리소스로 정해져 비트맵에 구워지므로, 언어가 바뀌어도 다시 그리기 전까지
 * 이전 언어로 남는다. 시스템 언어 변경(LOCALE_CHANGED)과 앱별 언어 변경(Android 13+, 앱에는 브로드캐스트가
 * 오지 않고 실행 중일 때 구성 변경으로만 알 수 있음)을 모두 잡도록, 마지막으로 그린 언어를 저장해 두고
 * 프로세스 시작·구성 변경 때 비교한다.
 */
object LocaleSync {

    private const val PREFS = "locale_sync"
    private const val KEY_RENDERED = "rendered_locales"

    fun check(context: Context) {
        val current = context.resources.configuration.locales.toLanguageTags()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val previous = prefs.getString(KEY_RENDERED, null)
        if (previous == current) return
        prefs.edit { putString(KEY_RENDERED, current) }
        // previous == null(첫 실행·업데이트 직후)이어도 다시 그린다. 위젯이 없으면 아무 일도 하지 않는다.
        Log.i("LocaleSync", "Locale changed $previous -> $current, re-rendering widgets")
        rerenderWidgets(context, relocalizePlace = true)
    }

    /**
     * 모든 위젯을 백그라운드에서 다시 그린다. [relocalizePlace]면 저장된 위치의 지명도 현재 표시 언어로 다시 받는다.
     * (설정 화면에서 호출해도 액티비티가 다시 만들어지는 동안 끊기지 않도록 WorkManager로 실행한다.)
     */
    fun rerenderWidgets(context: Context, relocalizePlace: Boolean) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            "widgets-rerender",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<RerenderWorker>()
                .setInputData(workDataOf(RerenderWorker.KEY_RELOCALIZE to relocalizePlace))
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build(),
        )
    }
}

/** 캐시된 날씨 데이터로 모든 위젯을 다시 그린다(지명 재조회만 네트워크를 쓴다). */
class RerenderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (inputData.getBoolean(KEY_RELOCALIZE, false)) {
            LocationResolver.relocalizeSavedPlace(applicationContext)
        }
        // 살아 있는 Glance 세션도 다시 구성되도록 버전을 올린다(At a Glance는 버전을 키로 모델을 캐시한다).
        WeatherStore.invalidate(applicationContext)
        WeatherSync.updateAllWidgets(applicationContext)
        return Result.success()
    }

    companion object {
        const val KEY_RELOCALIZE = "relocalize"
    }
}
