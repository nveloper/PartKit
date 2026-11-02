package com.partkit.widgets.glance.weather

import android.content.Context
import android.text.format.DateFormat
import com.partkit.widgets.R
import com.partkit.widgets.data.CurrentWeather
import com.partkit.widgets.data.DailyPoint
import com.partkit.widgets.data.HourlyPoint
import com.partkit.widgets.data.SavedLocation
import com.partkit.widgets.data.TempUnit
import com.partkit.widgets.data.WeatherSnapshot
import com.partkit.widgets.data.WidgetState
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt

internal object WeatherFormat {

    private fun locale(context: Context): Locale = context.resources.configuration.locales[0]

    /** 시간별 예보 레이블. 첫 칸은 "지금", 나머지는 로캘에 맞춘 시각(ko "오후 3시", en "3 PM"). */
    fun hourLabel(context: Context, index: Int, time: LocalDateTime): String {
        if (index == 0) return context.getString(R.string.label_now)
        val locale = locale(context)
        val pattern = DateFormat.getBestDateTimePattern(locale, if (DateFormat.is24HourFormat(context)) "H" else "j")
        return time.format(DateTimeFormatter.ofPattern(pattern, locale))
    }

    /** 일별 예보 레이블. 첫 칸은 "오늘", 나머지는 짧은 요일. */
    fun dayLabel(context: Context, index: Int, date: LocalDate): String =
        if (index == 0) context.getString(R.string.label_today)
        else date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale(context))

    fun highLow(context: Context, unit: TempUnit, day: DailyPoint?): String? =
        day?.let { context.getString(R.string.high_low, unit.format(it.maxC), unit.format(it.minC)) }

    fun feelsLike(context: Context, unit: TempUnit, current: CurrentWeather): String =
        context.getString(R.string.feels_like, unit.format(current.feelsLikeC))
}

/** 위젯 선택기 미리보기(Android 15+ 생성형 미리보기)에 쓰는 고정 샘플 데이터. */
internal object SampleWeather {

    fun state(context: Context): WidgetState {
        val now = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS)
        val codes = intArrayOf(1, 2, 2, 3, 61, 61, 3, 2, 1, 0, 0, 0)
        val hours = (0 until 24).map { i ->
            val time = now.plusHours(i.toLong())
            HourlyPoint(
                time = time,
                tempC = 19.0 + 4 * cos((time.hour - 15) / 24.0 * 2 * Math.PI),
                code = codes[i % codes.size],
                isDay = time.hour in 6..18,
            )
        }
        val dayCodes = intArrayOf(2, 61, 3, 0, 1, 71, 0)
        val days = (0 until 7).map { i ->
            DailyPoint(
                date = now.toLocalDate().plusDays(i.toLong()),
                code = dayCodes[i],
                maxC = (23 - i * 1.5 + (i % 2) * 2).roundToInt().toDouble(),
                minC = (13 - i + (i % 3)).toDouble(),
            )
        }
        val place = context.getString(R.string.default_place)
        val snapshot = WeatherSnapshot(
            place = place,
            fetchedAt = System.currentTimeMillis(),
            utcOffsetSeconds = java.time.ZoneId.systemDefault().rules.getOffset(java.time.Instant.now()).totalSeconds,
            current = CurrentWeather(tempC = 21.0, feelsLikeC = 20.0, code = 2, isDay = true),
            hourly = hours,
            daily = days,
        )
        return WidgetState(snapshot, TempUnit.CELSIUS, SavedLocation(37.5665, 126.978, place, true), version = 0)
    }
}
