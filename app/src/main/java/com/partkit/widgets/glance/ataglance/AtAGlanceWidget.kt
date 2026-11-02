package com.partkit.widgets.glance.ataglance

import android.app.PendingIntent
import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.provider.CalendarContract
import android.text.format.DateFormat
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.DrawableRes
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.fillMaxSize
import com.partkit.widgets.R
import com.partkit.widgets.data.AppLocale
import com.partkit.widgets.data.CalendarRepository
import com.partkit.widgets.data.UpcomingEvent
import com.partkit.widgets.data.WeatherCondition
import com.partkit.widgets.data.WeatherSnapshot
import com.partkit.widgets.data.WeatherStore
import com.partkit.widgets.data.WidgetState
import com.partkit.widgets.glance.common.WidgetText
import com.partkit.widgets.glance.common.weatherAppIntent
import com.partkit.widgets.glance.weather.SampleWeather
import com.partkit.widgets.sync.WeatherSync
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Pixel 홈 화면 상단의 At a Glance.
 *
 * - 배경 없이 배경화면 위에 바로 그려진다. 배경화면이 밝으면 어두운 글자, 어두우면 그림자 있는 흰 글자.
 * - 첫 줄: 날짜 또는 진행 중/1시간 내 일정 제목. 날짜는 [DateTick]이 자정·시간대 변경 때 갱신한다.
 * - 둘째 줄: 날씨 아이콘 + 기온, 그리고 상황별 칩(일정 시간, 곧 올 비/눈).
 * - 텍스트는 번들 폰트로 그린 비트맵이다([WidgetText]).
 */
class AtAGlanceWidget : GlanceAppWidget() {

    /** 실제 위젯 폭에 맞춰 텍스트 최대 폭(말줄임)을 정한다. */
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        WeatherSync.onWidgetRendered(context)
        DateTick.scheduleNextMidnight(context)
        val flow = WeatherStore.flow(context)
        provideContent {
            val state by flow.collectAsState()
            val ctx = AppLocale.wrap(LocalContext.current, state.englishOnly)
            val width = LocalSize.current.width
            val model = remember(state.version) { AtAGlanceModel.build(ctx, state, CalendarRepository.nextEvent(ctx)) }
            AndroidRemoteViews(AtAGlanceViews.build(ctx, model, width), GlanceModifier.fillMaxSize())
        }
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        // 위젯 선택기 배경은 시스템 테마를 따르므로 라이트 테마에서는 어두운 글자로 그린다.
        val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val localized = AppLocale.wrap(context)
        val model = AtAGlanceModel.build(localized, SampleWeather.state(localized), event = null, darkTextOverride = !night)
        provideContent {
            AndroidRemoteViews(AtAGlanceViews.build(localized, model, LocalSize.current.width), GlanceModifier.fillMaxSize())
        }
    }
}

class AtAGlanceReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AtAGlanceWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WeatherSync.ensurePeriodic(context)
        DateTick.scheduleNextMidnight(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        DateTick.cancel(context)
    }
}

/** 둘째 줄의 "아이콘 + 텍스트" 한 덩어리. */
internal data class GlanceChip(@DrawableRes val icon: Int?, val text: String, val tintIcon: Boolean)

internal data class AtAGlanceModel(
    val title: String,
    val event: UpcomingEvent?,
    val first: GlanceChip?,
    val second: GlanceChip?,
    val darkText: Boolean,
) {
    companion object {
        fun build(
            context: Context,
            state: WidgetState,
            event: UpcomingEvent?,
            darkTextOverride: Boolean? = null,
        ): AtAGlanceModel {
            val weather = state.snapshot?.let { snapshot ->
                val current = snapshot.currentForDisplay()
                GlanceChip(current.condition.icon(current.isDay), state.unit.formatWithUnit(current.tempC), tintIcon = false)
            }
            val darkText = darkTextOverride ?: wallpaperPrefersDarkText(context)
            return if (event != null) {
                // 일정이 있으면: 제목 = 일정, 둘째 줄 = [일정 시간] · [날씨]
                val time = GlanceChip(R.drawable.ic_event, eventTimeRange(context, event), tintIcon = true)
                val title = event.title.ifBlank { context.getString(R.string.glance_untitled_event) }
                AtAGlanceModel(title, event, time, weather, darkText)
            } else {
                // 평소: 제목 = 날짜, 둘째 줄 = [날씨] · [곧 올 비/눈]
                val precip = state.snapshot?.let { precipitationChip(context, it) }
                AtAGlanceModel(todayLabel(context), null, weather, precip, darkText)
            }
        }

        /** ko "10월 1일 (목)", en "Thu, Oct 1". */
        private fun todayLabel(context: Context): String {
            // DateFormat.format()은 요일·월 이름을 프로세스 기본 로캘에서 가져오므로, 컨텍스트 로캘을 쓰는 java.time으로 만든다.
            val locale = context.resources.configuration.locales[0]
            return LocalDate.now().format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "MMMdEEE"), locale))
        }

        private fun eventTimeRange(context: Context, event: UpcomingEvent): String {
            val locale = context.resources.configuration.locales[0]
            val skeleton = if (DateFormat.is24HourFormat(context)) "Hm" else "hm"
            val format = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
                .withZone(ZoneId.systemDefault())
            return "${format.format(Instant.ofEpochMilli(event.begin))} – ${format.format(Instant.ofEpochMilli(event.end))}"
        }

        /** 지금은 강수가 아니지만 3시간 안에 비/눈이 예보되어 있으면 칩을 만든다. */
        private fun precipitationChip(context: Context, snapshot: WeatherSnapshot): GlanceChip? {
            val hours = snapshot.upcomingHours(4)
            if (hours.isEmpty() || snapshot.currentForDisplay().condition.isPrecipitation) return null
            val next = hours.drop(1).firstOrNull { it.condition.isPrecipitation } ?: return null
            val locale: Locale = context.resources.configuration.locales[0]
            val skeleton = if (DateFormat.is24HourFormat(context)) "H" else "j"
            val time = next.time.format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale))
            val snow = next.condition == WeatherCondition.SNOW
            val text = context.getString(if (snow) R.string.glance_snow_from else R.string.glance_rain_from, time)
            return GlanceChip(R.drawable.ic_umbrella, text, tintIcon = true)
        }

        private fun wallpaperPrefersDarkText(context: Context): Boolean = runCatching {
            val colors = WallpaperManager.getInstance(context).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
            colors != null && (colors.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT) != 0
        }.getOrDefault(false)
    }
}

internal object AtAGlanceViews {

    private const val DARK_TEXT = 0xFF1F1F1F.toInt()
    private const val LIGHT_TEXT = 0xFFFFFFFF.toInt()

    /** 어두운 배경화면 위 흰 글자용 그림자. */
    private val LIGHT_TEXT_SHADOW = WidgetText.Shadow(color = 0x66000000, radiusDp = 2f, dyDp = 0.5f)

    private const val TITLE_SP = 26f
    private const val SUBTITLE_SP = 20f

    fun build(context: Context, model: AtAGlanceModel, width: Dp): RemoteViews {
        val textColor = if (model.darkText) DARK_TEXT else LIGHT_TEXT
        val shadow = if (model.darkText) null else LIGHT_TEXT_SHADOW
        val density = context.resources.displayMetrics.density
        // 레이아웃 좌우 여백 16dp×2를 뺀 폭. 둘째 줄의 두 칩은 절반씩 나눠 쓴다.
        val maxWidthPx = ((width.value - 32f) * density).toInt().coerceAtLeast(1)

        fun text(value: String, sizeSp: Float, maxPx: Int) =
            WidgetText.colored(context, value, sizeSp, WidgetText.Weight.Regular, maxPx, textColor, shadow)

        return RemoteViews(context.packageName, R.layout.widget_at_a_glance).apply {
            // 첫 줄: 날짜 또는 일정 제목
            setImageViewBitmap(R.id.glance_title, text(model.title, TITLE_SP, maxWidthPx))
            setContentDescription(R.id.glance_title, model.title)
            setOnClickPendingIntent(
                R.id.glance_title,
                if (model.event != null) eventIntent(context, model.event) else calendarIntent(context),
            )

            // 둘째 줄
            val chipPx = maxWidthPx / 2 - (32 * density).toInt() // 아이콘 26dp + 간격 6dp
            bindChip(model.first, R.id.glance_chip1, R.id.glance_chip1_icon, R.id.glance_chip1_text, textColor) {
                text(it, SUBTITLE_SP, chipPx)
            }
            bindChip(model.second, R.id.glance_chip2, R.id.glance_chip2_icon, R.id.glance_chip2_text, textColor) {
                text(it, SUBTITLE_SP, chipPx)
            }
            val bothChips = model.first != null && model.second != null
            setViewVisibility(R.id.glance_divider, if (bothChips) View.VISIBLE else View.GONE)
            if (bothChips) {
                setImageViewBitmap(R.id.glance_divider, text(context.getString(R.string.glance_divider), SUBTITLE_SP, maxWidthPx))
            }
            setViewVisibility(
                R.id.glance_subtitle,
                if (model.first != null || model.second != null) View.VISIBLE else View.GONE,
            )
            setOnClickPendingIntent(R.id.glance_subtitle, weatherIntent(context))
        }
    }

    private fun RemoteViews.bindChip(
        chip: GlanceChip?,
        container: Int,
        icon: Int,
        text: Int,
        textColor: Int,
        render: (String) -> Bitmap,
    ) {
        if (chip == null) {
            setViewVisibility(container, View.GONE)
            return
        }
        setViewVisibility(container, View.VISIBLE)
        setImageViewBitmap(text, render(chip.text))
        setContentDescription(text, chip.text)
        if (chip.icon == null) {
            setViewVisibility(icon, View.GONE)
        } else {
            setViewVisibility(icon, View.VISIBLE)
            setImageViewResource(icon, chip.icon)
            if (chip.tintIcon) setInt(icon, "setColorFilter", textColor)
        }
    }

    private fun calendarIntent(context: Context): PendingIntent {
        val uri = CalendarContract.CONTENT_URI.buildUpon()
            .appendPath("time")
            .appendPath(System.currentTimeMillis().toString())
            .build()
        return activity(context, 1, Intent(Intent.ACTION_VIEW, uri))
    }

    private fun eventIntent(context: Context, event: UpcomingEvent): PendingIntent {
        val intent = Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.eventId))
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.begin)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.end)
        return activity(context, 2, intent)
    }

    /** 날씨 줄을 누르면 기기의 날씨 앱(없으면 이 앱)으로. */
    private fun weatherIntent(context: Context): PendingIntent =
        activity(context, 3, weatherAppIntent(context))

    private fun activity(context: Context, requestCode: Int, intent: Intent): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
