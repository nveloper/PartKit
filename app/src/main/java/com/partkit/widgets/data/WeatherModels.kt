package com.partkit.widgets.data

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.partkit.widgets.R
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/** 캐시에 저장되는 날씨 스냅샷. 온도는 항상 섭씨로 보관하고 표시할 때 변환한다. */
data class WeatherSnapshot(
    val place: String,
    val fetchedAt: Long,
    val utcOffsetSeconds: Int,
    val current: CurrentWeather,
    val hourly: List<HourlyPoint>,
    val daily: List<DailyPoint>,
) {
    /** 관측 지점 기준의 현재 시각. */
    fun nowAtPlace(): LocalDateTime =
        OffsetDateTime.now(ZoneOffset.ofTotalSeconds(utcOffsetSeconds)).toLocalDateTime()

    /**
     * 표시용 현재 날씨. 캐시가 1시간 이상 지났다면 시간별 예보에서 현재 시각 값을 대신 쓴다.
     */
    fun currentForDisplay(nowMillis: Long = System.currentTimeMillis()): CurrentWeather {
        if (nowMillis - fetchedAt < STALE_CURRENT_MS) return current
        val hour = upcomingHours(1).firstOrNull() ?: return current
        return current.copy(tempC = hour.tempC, feelsLikeC = hour.tempC, code = hour.code, isDay = hour.isDay)
    }

    /** 현재 시각이 속한 시간부터 [count]개의 시간별 예보. */
    fun upcomingHours(count: Int): List<HourlyPoint> {
        val start = nowAtPlace().truncatedTo(ChronoUnit.HOURS)
        return hourly.asSequence().filter { !it.time.isBefore(start) }.take(count).toList()
    }

    /** 오늘부터 [count]일의 일별 예보. */
    fun upcomingDays(count: Int): List<DailyPoint> {
        val today = nowAtPlace().toLocalDate()
        return daily.asSequence().filter { !it.date.isBefore(today) }.take(count).toList()
    }

    fun today(): DailyPoint? = upcomingDays(1).firstOrNull()

    private companion object {
        const val STALE_CURRENT_MS = 60 * 60 * 1000L
    }
}

data class CurrentWeather(val tempC: Double, val feelsLikeC: Double, val code: Int, val isDay: Boolean) {
    val condition: WeatherCondition get() = WeatherCondition.from(code, isDay)
}

data class HourlyPoint(val time: LocalDateTime, val tempC: Double, val code: Int, val isDay: Boolean) {
    val condition: WeatherCondition get() = WeatherCondition.from(code, isDay)
}

data class DailyPoint(val date: LocalDate, val code: Int, val maxC: Double, val minC: Double) {
    val condition: WeatherCondition get() = WeatherCondition.from(code, isDay = true)
}

enum class TempUnit(val symbol: String) {
    CELSIUS("C"), FAHRENHEIT("F");

    fun convert(celsius: Double): Int = when (this) {
        CELSIUS -> celsius.roundToInt()
        FAHRENHEIT -> (celsius * 9 / 5 + 32).roundToInt()
    }

    /** "18°" 형태. Pixel 위젯처럼 단위 문자는 생략한다. */
    fun format(celsius: Double): String = "${convert(celsius)}°"

    /** "18°C" 형태. At a Glance처럼 한 줄 요약에 쓴다. */
    fun formatWithUnit(celsius: Double): String = "${convert(celsius)}°$symbol"
}

/** WMO weather interpretation code(Open-Meteo)를 Pixel 스타일 상태/아이콘으로 매핑. */
enum class WeatherCondition(@StringRes val label: Int, @DrawableRes val dayIcon: Int, @DrawableRes val nightIcon: Int) {
    CLEAR(R.string.cond_clear, R.drawable.wx_clear_day, R.drawable.wx_clear_night),
    MOSTLY_CLEAR(R.string.cond_mostly_clear, R.drawable.wx_partly_cloudy_day, R.drawable.wx_partly_cloudy_night),
    PARTLY_CLOUDY(R.string.cond_partly_cloudy, R.drawable.wx_partly_cloudy_day, R.drawable.wx_partly_cloudy_night),
    CLOUDY(R.string.cond_cloudy, R.drawable.wx_cloudy, R.drawable.wx_cloudy),
    FOG(R.string.cond_fog, R.drawable.wx_fog, R.drawable.wx_fog),
    DRIZZLE(R.string.cond_drizzle, R.drawable.wx_drizzle, R.drawable.wx_drizzle),
    RAIN(R.string.cond_rain, R.drawable.wx_rain, R.drawable.wx_rain),
    HEAVY_RAIN(R.string.cond_heavy_rain, R.drawable.wx_rain, R.drawable.wx_rain),
    SLEET(R.string.cond_sleet, R.drawable.wx_sleet, R.drawable.wx_sleet),
    SNOW(R.string.cond_snow, R.drawable.wx_snow, R.drawable.wx_snow),
    THUNDERSTORM(R.string.cond_thunderstorm, R.drawable.wx_thunderstorm, R.drawable.wx_thunderstorm);

    /** 이 상태가 강수(비/눈)인지. At a Glance의 강수 예보 카드에 쓴다. */
    val isPrecipitation: Boolean
        get() = this in setOf(DRIZZLE, RAIN, HEAVY_RAIN, SLEET, SNOW, THUNDERSTORM)

    @DrawableRes
    fun icon(isDay: Boolean): Int = if (isDay) dayIcon else nightIcon

    companion object {
        fun from(code: Int, isDay: Boolean): WeatherCondition = when (code) {
            0 -> CLEAR
            1 -> MOSTLY_CLEAR
            2 -> PARTLY_CLOUDY
            3 -> CLOUDY
            45, 48 -> FOG
            51, 53, 55 -> DRIZZLE
            61, 63, 80, 81 -> RAIN
            65, 82 -> HEAVY_RAIN
            56, 57, 66, 67 -> SLEET
            71, 73, 75, 77, 85, 86 -> SNOW
            95, 96, 99 -> THUNDERSTORM
            else -> CLOUDY
        }
    }
}
