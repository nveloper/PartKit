package com.partkit.widgets.glance.weather

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.TextAlign
import com.partkit.widgets.R
import com.partkit.widgets.data.AppLocale
import com.partkit.widgets.data.WeatherCondition
import com.partkit.widgets.data.WeatherSnapshot
import com.partkit.widgets.data.WeatherStore
import com.partkit.widgets.data.WidgetState
import com.partkit.widgets.glance.common.PixelText
import com.partkit.widgets.glance.common.PixelTokens
import com.partkit.widgets.glance.common.TextTone
import com.partkit.widgets.glance.common.WeatherIcon
import com.partkit.widgets.glance.common.openWeatherAppAction
import com.partkit.widgets.sync.WeatherSync

/**
 * 날씨 위젯 공통 골격: 캐시 흐름 구독, 새로고침 트리거, 위젯 선택기 미리보기(Android 15+).
 */
abstract class WeatherGlanceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        WeatherSync.onWidgetRendered(context)
        val flow = WeatherStore.flow(context)
        provideContent {
            val state by flow.collectAsState()
            LocalizedContent(state.englishOnly) {
                GlanceTheme {
                    val snapshot = state.snapshot
                    if (snapshot == null) LoadingContent() else Content(snapshot, state)
                }
            }
        }
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        val englishOnly = AppLocale.isEnglishOnly(context)
        val state = SampleWeather.state(AppLocale.wrap(context, englishOnly))
        provideContent {
            LocalizedContent(englishOnly) {
                GlanceTheme { Content(state.snapshot!!, state) }
            }
        }
    }

    @Composable
    abstract fun Content(snapshot: WeatherSnapshot, state: WidgetState)
}

/**
 * 설정 "영어로 표시"가 켜져 있으면 구성 안의 LocalContext를 영어 컨텍스트로 바꾼다.
 * 위젯의 모든 문자열·날짜 형식은 LocalContext에서 읽으므로 이것만으로 일괄 적용된다.
 */
@Composable
fun LocalizedContent(englishOnly: Boolean, content: @Composable () -> Unit) {
    val base = LocalContext.current
    val localized = remember(base, englishOnly) { AppLocale.wrap(base, englishOnly) }
    CompositionLocalProvider(LocalContext provides localized) { content() }
}

abstract class WeatherGlanceReceiver : GlanceAppWidgetReceiver() {
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WeatherSync.ensurePeriodic(context)
        WeatherSync.refreshNow(context)
    }
}

/**
 * 표준 사각형 컨테이너: 동적 색상 배경 + 시스템 위젯 모서리 반경.
 * 칸이 가로로 늘어서는 위젯은 [horizontalPadding]을 줄여 칸 자체의 여백으로 가장자리를 맞춘다.
 */
@Composable
fun WidgetSurface(
    horizontalPadding: Dp = PixelTokens.EdgePadding,
    verticalPadding: Dp = PixelTokens.EdgePadding,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(PixelTokens.OuterRadius)
            .padding(horizontal = horizontalPadding, vertical = verticalPadding)
            .clickable(openWeatherAppAction(context)),
    ) { content() }
}

@Composable
private fun LoadingContent() {
    val context = LocalContext.current
    WidgetSurface {
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WeatherIcon(WeatherCondition.PARTLY_CLOUDY, isDay = true, size = 36.dp)
            Spacer(GlanceModifier.height(6.dp))
            PixelText(
                text = context.getString(R.string.weather_loading),
                size = 13.sp,
                tone = TextTone.OnSurfaceVariant,
                align = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}
