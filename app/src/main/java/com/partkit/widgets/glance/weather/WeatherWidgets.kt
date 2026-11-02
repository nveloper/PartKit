package com.partkit.widgets.glance.weather

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.TextAlign
import com.partkit.widgets.R
import com.partkit.widgets.data.DailyPoint
import com.partkit.widgets.data.WeatherSnapshot
import com.partkit.widgets.data.WidgetState
import com.partkit.widgets.glance.common.PixelText
import com.partkit.widgets.glance.common.PixelTokens
import com.partkit.widgets.glance.common.TemperatureText
import com.partkit.widgets.glance.common.TextTone
import com.partkit.widgets.glance.common.WeatherIcon
import com.partkit.widgets.glance.common.conditionLabel
import com.partkit.widgets.glance.common.openWeatherAppAction

/*
 * 크기 처리 원칙
 * - SizeMode.Exact: LocalSize가 실제 위젯 크기다. (Responsive에서는 LocalSize가 실제 크기가 아니라
 *   일치한 기준점 크기라서, 이를 기준으로 텍스트 폭을 정하면 실제보다 좁게 잘린다.)
 * - 글자·아이콘 크기는 레이아웃별 기준 크기 대비 실제 크기 배율([typeScale])로 키운다.
 * - 좁은 칸의 텍스트는 minSize까지 글자를 줄여 맞추고, 그래도 넘치면 말줄임표로 자른다.
 */

// ─────────────────────────────────────────────────────────────────────────────
// 1. 현재 날씨 — Material 3 Expressive "쿠키" 도형 안에 아이콘과 큰 기온.
// ─────────────────────────────────────────────────────────────────────────────

class CurrentWeatherWidget : WeatherGlanceWidget() {

    private companion object {
        val SMALL = DpSize(72.dp, 72.dp)
        val LARGE = DpSize(150.dp, 150.dp)
    }

    override val sizeMode = SizeMode.Exact
    override val previewSizeMode = SizeMode.Responsive(setOf(SMALL, LARGE))

    @Composable
    override fun Content(snapshot: WeatherSnapshot, state: WidgetState) {
        val context = LocalContext.current
        val size = LocalSize.current
        val large = size.width >= 110.dp && size.height >= 110.dp
        val k = typeScale(if (large) LARGE else SMALL)
        val current = snapshot.currentForDisplay()
        val onShape = TextTone.OnPrimaryContainer
        // 쿠키 도형 안쪽(지름의 약 70%)에 들어가도록 텍스트 폭을 제한한다.
        val inner = minOf(size.width, size.height) * 0.7f

        Box(
            modifier = GlanceModifier.fillMaxSize().clickable(openWeatherAppAction(context)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(if (large) R.drawable.shape_cookie9 else R.drawable.shape_circle),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(GlanceTheme.colors.primaryContainer),
                modifier = GlanceModifier.fillMaxSize(),
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (large) {
                    WeatherIcon(current.condition, current.isDay, 48.dp * k)
                    TemperatureText(state.unit.format(current.tempC), 48.sp * k, tone = onShape, align = TextAlign.Center)
                    WeatherFormat.highLow(context, state.unit, snapshot.today())?.let {
                        PixelText(it, 14.sp * k, tone = onShape, align = TextAlign.Center, maxWidth = inner, minSize = 11.sp)
                    }
                } else {
                    WeatherIcon(current.condition, current.isDay, 26.dp * k)
                    TemperatureText(state.unit.format(current.tempC), 20.sp * k, tone = onShape, align = TextAlign.Center)
                }
            }
        }
    }
}

class CurrentWeatherReceiver : WeatherGlanceReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CurrentWeatherWidget()
}

// ─────────────────────────────────────────────────────────────────────────────
// 2. 예보 — 도시/상태/체감/최고·최저를 왼쪽에, 아이콘과 큰 기온을 오른쪽에.
// ─────────────────────────────────────────────────────────────────────────────

class ForecastWeatherWidget : WeatherGlanceWidget() {

    private companion object {
        val COMPACT = DpSize(320.dp, 72.dp)
        val FULL = DpSize(320.dp, 150.dp)
    }

    override val sizeMode = SizeMode.Exact
    override val previewSizeMode = SizeMode.Responsive(setOf(COMPACT, FULL))

    @Composable
    override fun Content(snapshot: WeatherSnapshot, state: WidgetState) {
        val context = LocalContext.current
        val size = LocalSize.current
        val current = snapshot.currentForDisplay()
        val highLow = WeatherFormat.highLow(context, state.unit, snapshot.today())
        val variant = TextTone.OnSurfaceVariant

        if (size.height < 100.dp) {
            val k = typeScale(COMPACT)
            WidgetSurface(verticalPadding = 4.dp) {
                Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    WeatherIcon(current.condition, current.isDay, 36.dp * k)
                    Spacer(GlanceModifier.width(8.dp))
                    TemperatureText(state.unit.format(current.tempC), 32.sp * k)
                    Spacer(GlanceModifier.width(12.dp))
                    // 아이콘 + 기온(32sp 기준 약 64dp) + 여백을 뺀 나머지
                    val infoWidth = size.width - PixelTokens.EdgePadding * 2 - 36.dp * k - 20.dp - 64.dp * k
                    Column(GlanceModifier.defaultWeight()) {
                        PixelText(snapshot.place, 16.sp * k, weight = FontWeight.Medium, maxWidth = infoWidth, minSize = 12.sp)
                        PixelText(
                            listOfNotNull(conditionLabel(current.condition), highLow).joinToString(" · "),
                            14.sp * k, tone = variant, maxWidth = infoWidth, minSize = 11.sp,
                        )
                    }
                }
            }
            return
        }

        val k = typeScale(FULL)
        WidgetSurface {
            Row(GlanceModifier.fillMaxSize()) {
                // 오른쪽 큰 기온 열(64sp 기준 약 120dp)을 뺀 나머지
                val infoWidth = size.width - PixelTokens.EdgePadding * 2 - 120.dp * k
                Column(GlanceModifier.defaultWeight().fillMaxHeight()) {
                    PlaceLabel(snapshot.place, infoWidth, k)
                    Spacer(GlanceModifier.defaultWeight())
                    PixelText(conditionLabel(current.condition), 20.sp * k, weight = FontWeight.Medium, maxWidth = infoWidth, minSize = 14.sp)
                    PixelText(WeatherFormat.feelsLike(context, state.unit, current), 15.sp * k, tone = variant, maxWidth = infoWidth, minSize = 12.sp)
                    highLow?.let { PixelText(it, 15.sp * k, tone = variant, maxWidth = infoWidth, minSize = 12.sp) }
                }
                Column(GlanceModifier.fillMaxHeight(), horizontalAlignment = Alignment.End) {
                    WeatherIcon(current.condition, current.isDay, 56.dp * k)
                    Spacer(GlanceModifier.defaultWeight())
                    TemperatureText(state.unit.format(current.tempC), 64.sp * k, align = TextAlign.End)
                }
            }
        }
    }
}

class ForecastWeatherReceiver : WeatherGlanceReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ForecastWeatherWidget()
}

// ─────────────────────────────────────────────────────────────────────────────
// 3. 시간별 — 6시간 예보. "지금" 칸을 알약형 컨테이너로 강조.
// ─────────────────────────────────────────────────────────────────────────────

class HourlyWeatherWidget : WeatherGlanceWidget() {

    private companion object {
        // 짧은 형태는 칸(레이블+아이콘+기온)이 세로로 들어가야 해서 기준 높이를 넉넉히 잡는다.
        val SHORT = DpSize(320.dp, 140.dp)
        val TALL = DpSize(320.dp, 200.dp)
    }

    override val sizeMode = SizeMode.Exact
    override val previewSizeMode = SizeMode.Responsive(setOf(SHORT, TALL))

    @Composable
    override fun Content(snapshot: WeatherSnapshot, state: WidgetState) {
        val context = LocalContext.current
        val size = LocalSize.current
        val tall = size.height >= 150.dp
        val k = typeScale(if (tall) TALL else SHORT)
        val current = snapshot.currentForDisplay()
        val hours = snapshot.upcomingHours(6)

        WidgetSurface(horizontalPadding = 8.dp, verticalPadding = if (tall) 12.dp else 8.dp) {
            Column(GlanceModifier.fillMaxSize()) {
                if (tall) {
                    Row(GlanceModifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        // 아이콘 + 기온(48sp 기준 약 90dp)을 뺀 나머지
                        val infoWidth = size.width - 32.dp - 40.dp * k - 6.dp - 90.dp * k
                        Column(GlanceModifier.defaultWeight()) {
                            PlaceLabel(snapshot.place, infoWidth, k)
                            PixelText(
                                listOfNotNull(
                                    conditionLabel(current.condition),
                                    WeatherFormat.highLow(context, state.unit, snapshot.today()),
                                ).joinToString(" · "),
                                14.sp * k, tone = TextTone.OnSurfaceVariant, maxWidth = infoWidth, minSize = 11.sp,
                            )
                        }
                        WeatherIcon(current.condition, current.isDay, 40.dp * k)
                        Spacer(GlanceModifier.width(6.dp))
                        TemperatureText(state.unit.format(current.tempC), 48.sp * k)
                    }
                    Spacer(GlanceModifier.defaultWeight())
                }
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    hours.forEachIndexed { index, hour ->
                        ForecastCell(
                            label = WeatherFormat.hourLabel(context, index, hour.time),
                            iconContent = { WeatherIcon(hour.condition, hour.isDay, 32.dp * k) },
                            primary = state.unit.format(hour.tempC),
                            secondary = null,
                            highlighted = index == 0,
                            cellWidth = (size.width - 16.dp) / hours.size,
                            k = k,
                            cellPadding = if (tall) 10.dp else 6.dp,
                            modifier = GlanceModifier.defaultWeight(),
                        )
                    }
                }
            }
        }
    }
}

class HourlyWeatherReceiver : WeatherGlanceReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HourlyWeatherWidget()
}

// ─────────────────────────────────────────────────────────────────────────────
// 4. 5일 예보 — 넓으면 요일 열, 높으면 최저~최고 범위 막대가 있는 목록.
// ─────────────────────────────────────────────────────────────────────────────

class DailyWeatherWidget : WeatherGlanceWidget() {

    private companion object {
        val COLUMNS = DpSize(320.dp, 150.dp)
        val LIST = DpSize(320.dp, 330.dp)
        const val DAYS = 5
    }

    override val sizeMode = SizeMode.Exact
    override val previewSizeMode = SizeMode.Responsive(setOf(COLUMNS, LIST))

    @Composable
    override fun Content(snapshot: WeatherSnapshot, state: WidgetState) {
        val days = snapshot.upcomingDays(DAYS)
        if (LocalSize.current.height >= 220.dp) DailyList(snapshot, state, days) else DailyColumns(state, days)
    }

    @Composable
    private fun DailyColumns(state: WidgetState, days: List<DailyPoint>) {
        val context = LocalContext.current
        val size = LocalSize.current
        val k = typeScale(COLUMNS)
        WidgetSurface(horizontalPadding = 8.dp, verticalPadding = 12.dp) {
            Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                days.forEachIndexed { index, day ->
                    ForecastCell(
                        label = WeatherFormat.dayLabel(context, index, day.date),
                        iconContent = { WeatherIcon(day.condition, isDay = true, size = 32.dp * k) },
                        primary = state.unit.format(day.maxC),
                        secondary = state.unit.format(day.minC),
                        highlighted = index == 0,
                        cellWidth = (size.width - 16.dp) / days.size,
                        k = k,
                        modifier = GlanceModifier.defaultWeight(),
                    )
                }
            }
        }
    }

    @Composable
    private fun DailyList(snapshot: WeatherSnapshot, state: WidgetState, days: List<DailyPoint>) {
        val context = LocalContext.current
        val size = LocalSize.current
        val k = typeScale(LIST)
        val current = snapshot.currentForDisplay()
        val weekMin = days.minOfOrNull { it.minC } ?: 0.0
        val weekMax = days.maxOfOrNull { it.maxC } ?: 1.0
        val dayWidth = 56.dp * k
        val iconSize = 28.dp * k
        val tempWidth = 44.dp * k
        // 행 = 요일 + 아이콘 + 12 + 최저 + 6 + 막대 + 6 + 최고. 막대 폭은 남는 공간.
        val barWidth = (size.width - PixelTokens.EdgePadding * 2 - dayWidth - iconSize - tempWidth * 2 - 24.dp)
            .coerceAtLeast(24.dp)

        WidgetSurface {
            Column(GlanceModifier.fillMaxSize()) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    val infoWidth = size.width - PixelTokens.EdgePadding * 2 - 36.dp * k - 6.dp - 80.dp * k
                    Column(GlanceModifier.defaultWeight()) {
                        PlaceLabel(snapshot.place, infoWidth, k)
                        PixelText(conditionLabel(current.condition), 14.sp * k, tone = TextTone.OnSurfaceVariant, maxWidth = infoWidth, minSize = 11.sp)
                    }
                    WeatherIcon(current.condition, current.isDay, 36.dp * k)
                    Spacer(GlanceModifier.width(6.dp))
                    TemperatureText(state.unit.format(current.tempC), 40.sp * k)
                }
                Spacer(GlanceModifier.height(8.dp))
                days.forEachIndexed { index, day ->
                    Row(
                        GlanceModifier.fillMaxWidth().defaultWeight(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PixelText(
                            WeatherFormat.dayLabel(context, index, day.date), 16.sp * k,
                            modifier = GlanceModifier.width(dayWidth),
                            maxWidth = dayWidth, minSize = 12.sp,
                            weight = if (index == 0) FontWeight.Medium else FontWeight.Normal,
                        )
                        WeatherIcon(day.condition, isDay = true, size = iconSize)
                        Spacer(GlanceModifier.width(12.dp))
                        PixelText(
                            state.unit.format(day.minC), 16.sp * k,
                            modifier = GlanceModifier.width(tempWidth),
                            maxWidth = tempWidth, minSize = 12.sp,
                            tone = TextTone.OnSurfaceVariant,
                            align = TextAlign.End,
                        )
                        Spacer(GlanceModifier.width(6.dp))
                        TemperatureRangeBar(day, weekMin, weekMax, barWidth - 12.dp)
                        Spacer(GlanceModifier.width(6.dp))
                        PixelText(
                            state.unit.format(day.maxC), 16.sp * k,
                            modifier = GlanceModifier.width(tempWidth),
                            maxWidth = tempWidth, minSize = 12.sp,
                            weight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

class DailyWeatherReceiver : WeatherGlanceReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DailyWeatherWidget()
}

// ─────────────────────────────────────────────────────────────────────────────
// 공통 조각
// ─────────────────────────────────────────────────────────────────────────────

/**
 * 위젯 크기에 비례한 글자 배율. [reference]는 각 레이아웃의 기준 크기(배율 1)이고,
 * 실제 위젯이 그보다 크면 글자와 아이콘을 키운다(작으면 조금만 줄인다).
 */
@Composable
private fun typeScale(reference: DpSize): Float {
    val size = LocalSize.current
    return minOf(size.width / reference.width, size.height / reference.height).coerceIn(0.85f, 1.5f)
}

@Composable
private fun PlaceLabel(place: String, maxWidth: Dp, k: Float) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            provider = ImageProvider(R.drawable.ic_location),
            contentDescription = null,
            colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurface),
            modifier = GlanceModifier.size(16.dp * k),
        )
        Spacer(GlanceModifier.width(4.dp))
        PixelText(place, 16.sp * k, weight = FontWeight.Medium, maxWidth = maxWidth - 20.dp * k, minSize = 12.sp)
    }
}

/** 시간별/일별 칸. 강조 칸은 primaryContainer 알약형 배경(M3 Expressive). */
@Composable
private fun ForecastCell(
    label: String,
    iconContent: @Composable () -> Unit,
    primary: String,
    secondary: String?,
    highlighted: Boolean,
    cellWidth: Dp,
    k: Float,
    modifier: GlanceModifier,
    cellPadding: Dp = 10.dp,
) {
    val textColor = if (highlighted) TextTone.OnPrimaryContainer else TextTone.OnSurface
    val subColor = if (highlighted) TextTone.OnPrimaryContainer else TextTone.OnSurfaceVariant
    val base = modifier.padding(horizontal = 2.dp)
    Box(base, contentAlignment = Alignment.Center) {
        Column(
            modifier = GlanceModifier.fillMaxWidth()
                .then(
                    if (highlighted) GlanceModifier.background(GlanceTheme.colors.primaryContainer)
                        .cornerRadius(PixelTokens.InnerRadius)
                    else GlanceModifier
                )
                .padding(vertical = cellPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 칸 폭(좌우 여백 제외)에 맞춘다. "오후 10시"처럼 긴 레이블은 글자를 줄여서 맞춘다.
            val textWidth = cellWidth - 6.dp
            PixelText(label, 14.sp * k, tone = subColor, align = TextAlign.Center, maxWidth = textWidth, minSize = 10.sp)
            Spacer(GlanceModifier.height(cellPadding / 2))
            iconContent()
            Spacer(GlanceModifier.height(cellPadding / 2))
            PixelText(primary, 18.sp * k, tone = textColor, weight = FontWeight.Medium, align = TextAlign.Center, maxWidth = textWidth, minSize = 12.sp)
            secondary?.let { PixelText(it, 15.sp * k, tone = subColor, align = TextAlign.Center, maxWidth = textWidth, minSize = 11.sp) }
        }
    }
}

/** 주간 최저~최고 범위 안에서 그날의 범위를 채운 막대. */
@Composable
private fun TemperatureRangeBar(day: DailyPoint, weekMin: Double, weekMax: Double, width: Dp) {
    val span = (weekMax - weekMin).takeIf { it > 0 } ?: 1.0
    val start = width * ((day.minC - weekMin) / span).toFloat()
    val fill = (width * ((day.maxC - day.minC) / span).toFloat()).coerceAtLeast(6.dp)
    Box(
        GlanceModifier.width(width).height(6.dp)
            .background(GlanceTheme.colors.surfaceVariant)
            .cornerRadius(3.dp),
    ) {
        Row {
            Spacer(GlanceModifier.width(start))
            Box(
                GlanceModifier.width(fill).height(6.dp)
                    .background(GlanceTheme.colors.primary)
                    .cornerRadius(3.dp),
            ) {}
        }
    }
}
