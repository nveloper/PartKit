package com.partkit.widgets.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.partkit.widgets.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SavedLocation(val latitude: Double, val longitude: Double, val name: String, val isDefault: Boolean)

/** 위젯이 그리는 데 필요한 전부. [version]은 캐시/설정이 바뀔 때마다 증가한다. */
data class WidgetState(
    val snapshot: WeatherSnapshot?,
    val unit: TempUnit,
    val location: SavedLocation,
    val version: Long,
    /** 설정 "영어로 표시"([AppLocale]). 위젯은 이 값에 따라 문자열 컨텍스트를 감싼다. */
    val englishOnly: Boolean = false,
)

/**
 * 날씨 캐시와 사용자 설정. SharedPreferences에 영속화하고, 프로세스 안에서는 [StateFlow]로
 * 노출해서 살아 있는 Glance 세션이 새 데이터로 곧바로 재구성되게 한다.
 */
object WeatherStore {

    private const val PREFS = "weather_store"
    private const val KEY_JSON = "forecast_json"
    private const val KEY_FETCHED_AT = "fetched_at"
    private const val KEY_UNIT = "unit"
    private const val KEY_LAT = "lat"
    private const val KEY_LON = "lon"
    private const val KEY_PLACE = "place"

    /** 위치를 한 번도 저장하지 않았을 때 쓰는 기본 위치(서울 시청). */
    private const val DEFAULT_LAT = 37.5665
    private const val DEFAULT_LON = 126.9780

    private val state = MutableStateFlow<WidgetState?>(null)

    fun flow(context: Context): StateFlow<WidgetState> {
        ensureLoaded(context)
        @Suppress("UNCHECKED_CAST")
        return state.asStateFlow() as StateFlow<WidgetState>
    }

    fun current(context: Context): WidgetState {
        ensureLoaded(context)
        return state.value!!
    }

    fun saveForecast(context: Context, json: String, fetchedAt: Long) {
        prefs(context).edit {
            putString(KEY_JSON, json)
            putLong(KEY_FETCHED_AT, fetchedAt)
        }
        reload(context)
    }

    fun setUnit(context: Context, unit: TempUnit) {
        prefs(context).edit { putString(KEY_UNIT, unit.name) }
        reload(context)
    }

    fun setLocation(context: Context, latitude: Double, longitude: Double, name: String) {
        prefs(context).edit {
            putLong(KEY_LAT, latitude.toRawBits())
            putLong(KEY_LON, longitude.toRawBits())
            putString(KEY_PLACE, name)
        }
        reload(context)
    }

    fun setEnglishOnly(context: Context, enabled: Boolean) {
        AppLocale.setEnglishOnly(context, enabled)
        reload(context)
    }

    /** 날씨 외 입력(캘린더, 배경화면 등)이 바뀌었을 때 위젯 재구성만 유도한다. */
    fun invalidate(context: Context) {
        ensureLoaded(context)
        state.update { it?.copy(version = it.version + 1) }
    }

    private fun ensureLoaded(context: Context) {
        if (state.value == null) synchronized(this) {
            if (state.value == null) reload(context)
        }
    }

    private fun reload(context: Context) {
        val prefs = prefs(context)
        val location = readLocation(context, prefs)
        val unit = prefs.getString(KEY_UNIT, null)?.let { runCatching { TempUnit.valueOf(it) }.getOrNull() }
            ?: TempUnit.CELSIUS
        val snapshot = prefs.getString(KEY_JSON, null)?.let { json ->
            runCatching { OpenMeteo.parse(json, location.name, prefs.getLong(KEY_FETCHED_AT, 0L)) }
                .onFailure { Log.w("WeatherStore", "Discarding unreadable cache", it) }
                .getOrNull()
        }
        state.update { previous ->
            WidgetState(
                snapshot, unit, location,
                version = (previous?.version ?: 0L) + 1,
                englishOnly = AppLocale.isEnglishOnly(context),
            )
        }
    }

    private fun readLocation(context: Context, prefs: SharedPreferences): SavedLocation {
        val defaultName = AppLocale.wrap(context).getString(R.string.default_place)
        if (!prefs.contains(KEY_LAT)) return SavedLocation(DEFAULT_LAT, DEFAULT_LON, defaultName, isDefault = true)
        return SavedLocation(
            latitude = Double.fromBits(prefs.getLong(KEY_LAT, 0L)),
            longitude = Double.fromBits(prefs.getLong(KEY_LON, 0L)),
            name = prefs.getString(KEY_PLACE, null).orEmpty().ifEmpty { defaultName },
            isDefault = false,
        )
    }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
