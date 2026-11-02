package com.partkit.widgets.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale

/**
 * Open-Meteo 예보 API 클라이언트 (API 키 불필요, CC BY 4.0 — 앱 화면에 출처 표기).
 * https://open-meteo.com/en/docs
 */
object OpenMeteo {

    private const val ENDPOINT = "https://api.open-meteo.com/v1/forecast"

    /** 원본 JSON 문자열을 반환한다. 캐시에는 원본을 저장하고 읽을 때 [parse]한다. */
    suspend fun fetchForecastJson(latitude: Double, longitude: Double): String = withContext(Dispatchers.IO) {
        val url = URL(
            String.format(
                Locale.US,
                "%s?latitude=%.4f&longitude=%.4f" +
                    "&current=temperature_2m,apparent_temperature,weather_code,is_day" +
                    "&hourly=temperature_2m,weather_code,is_day" +
                    "&daily=weather_code,temperature_2m_max,temperature_2m_min" +
                    "&timezone=auto&forecast_days=7",
                ENDPOINT, latitude, longitude,
            )
        )
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("Open-Meteo HTTP ${connection.responseCode}")
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    fun parse(json: String, place: String, fetchedAt: Long): WeatherSnapshot {
        val root = JSONObject(json)
        val current = root.getJSONObject("current")
        val hourly = root.getJSONObject("hourly")
        val daily = root.getJSONObject("daily")

        val hourTimes = hourly.getJSONArray("time")
        val hourTemps = hourly.getJSONArray("temperature_2m")
        val hourCodes = hourly.getJSONArray("weather_code")
        val hourIsDay = hourly.getJSONArray("is_day")
        val hours = (0 until hourTimes.length()).map { i ->
            HourlyPoint(
                time = LocalDateTime.parse(hourTimes.getString(i)),
                tempC = hourTemps.getDouble(i),
                code = hourCodes.getInt(i),
                isDay = hourIsDay.getInt(i) == 1,
            )
        }

        val dayDates = daily.getJSONArray("time")
        val dayCodes = daily.getJSONArray("weather_code")
        val dayMax = daily.getJSONArray("temperature_2m_max")
        val dayMin = daily.getJSONArray("temperature_2m_min")
        val days = (0 until dayDates.length()).map { i ->
            DailyPoint(
                date = LocalDate.parse(dayDates.getString(i)),
                code = dayCodes.getInt(i),
                maxC = dayMax.getDouble(i),
                minC = dayMin.getDouble(i),
            )
        }

        return WeatherSnapshot(
            place = place,
            fetchedAt = fetchedAt,
            utcOffsetSeconds = root.optInt("utc_offset_seconds", 0),
            current = CurrentWeather(
                tempC = current.getDouble("temperature_2m"),
                feelsLikeC = current.getDouble("apparent_temperature"),
                code = current.getInt("weather_code"),
                isDay = current.getInt("is_day") == 1,
            ),
            hourly = hours,
            daily = days,
        )
    }
}
