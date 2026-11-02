package com.partkit.widgets.glance.common

import android.content.Context
import android.content.Intent
import android.text.Layout
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.TextAlign
import androidx.glance.unit.ColorProvider
import com.partkit.widgets.data.WeatherApps
import com.partkit.widgets.data.WeatherCondition
import com.partkit.widgets.ui.MainActivity

/**
 * Pixel 위젯 디자인 토큰.
 *
 * - 글꼴: 앱에 번들된 PartKit Sans(Google Sans + Noto Sans KR 한글, res/font)를 쓴다.
 *   런처는 위젯의 TextView에 앱 폰트를 적용하지 않으므로, [PixelText]는 텍스트를 이 폰트로
 *   비트맵에 그려([WidgetText]) 이미지로 보낸다. 시스템 글꼴과 무관하게 한글까지 같은 폰트로 나온다.
 * - 모서리: 바깥은 android.R.dimen.system_app_widget_background_radius,
 *   안쪽은 system_app_widget_inner_radius(= 바깥 - 8dp)를 쓴다.
 */
object PixelTokens {
    val OuterRadius = android.R.dimen.system_app_widget_background_radius
    val InnerRadius = android.R.dimen.system_app_widget_inner_radius

    /** 위젯 가장자리 안쪽 여백. 둥근 모서리에 내용이 잘리지 않을 만큼 둔다. */
    val EdgePadding = 16.dp
}

/** 텍스트 색 역할. GlanceTheme의 동적 색상으로 틴트해서 런처가 그릴 때 테마에 맞게 정해진다. */
enum class TextTone { OnSurface, OnSurfaceVariant, OnPrimaryContainer }

@Composable
private fun TextTone.colorProvider(): ColorProvider = when (this) {
    TextTone.OnSurface -> GlanceTheme.colors.onSurface
    TextTone.OnSurfaceVariant -> GlanceTheme.colors.onSurfaceVariant
    TextTone.OnPrimaryContainer -> GlanceTheme.colors.onPrimaryContainer
}

/**
 * 번들 폰트(PartKit Sans)로 그리는 위젯 텍스트. 기본은 한 줄, onSurface 색.
 *
 * 텍스트는 [maxWidth]까지 자연스러운 폭으로 그리고, 넘치면 [minSize]까지 글자를 줄인 뒤 그래도 넘치면
 * 말줄임표로 자른다. [maxWidth]를 주지 않으면
 * 위젯 폭에서 가장자리 여백을 뺀 값을 쓴다. 정렬은 [modifier]로 정한 영역 안에서 적용된다.
 */
@Composable
fun PixelText(
    text: String,
    size: TextUnit,
    modifier: GlanceModifier = GlanceModifier,
    tone: TextTone = TextTone.OnSurface,
    weight: FontWeight = FontWeight.Normal,
    align: TextAlign = TextAlign.Start,
    maxLines: Int = 1,
    maxWidth: Dp? = null,
    minSize: TextUnit = size,
) {
    val context = LocalContext.current
    val density = context.resources.displayMetrics.density
    val widthLimit = maxWidth ?: (LocalSize.current.width - PixelTokens.EdgePadding * 2)
    val (layoutAlign, alignment) = when (align) {
        TextAlign.Center -> Layout.Alignment.ALIGN_CENTER to Alignment.Center
        TextAlign.End, TextAlign.Right -> Layout.Alignment.ALIGN_OPPOSITE to Alignment.CenterEnd
        else -> Layout.Alignment.ALIGN_NORMAL to Alignment.CenterStart
    }
    val fontWeight = if (weight == FontWeight.Normal) WidgetText.Weight.Regular else WidgetText.Weight.Medium
    val bitmap = WidgetText.mask(
        context, text, size.value, fontWeight,
        maxWidthPx = (widthLimit.value * density).toInt(), maxLines = maxLines, align = layoutAlign,
        minSizeSp = minSize.value,
    )
    Box(modifier, contentAlignment = alignment) {
        Image(
            provider = ImageProvider(bitmap),
            contentDescription = text,
            contentScale = ContentScale.Fit,
            colorFilter = ColorFilter.tint(tone.colorProvider()),
            modifier = GlanceModifier.size((bitmap.width / density).dp, (bitmap.height / density).dp),
        )
    }
}

/** 큰 기온 숫자. */
@Composable
fun TemperatureText(
    text: String,
    size: TextUnit,
    modifier: GlanceModifier = GlanceModifier,
    tone: TextTone = TextTone.OnSurface,
    align: TextAlign = TextAlign.Start,
) = PixelText(text, size, modifier, tone, FontWeight.Normal, align)

/** 날씨 아이콘. 아이콘은 자체 색(그라데이션)을 가지므로 틴트하지 않는다. */
@Composable
fun WeatherIcon(condition: WeatherCondition, isDay: Boolean, size: Dp, modifier: GlanceModifier = GlanceModifier) {
    val context = LocalContext.current
    Image(
        provider = ImageProvider(condition.icon(isDay)),
        contentDescription = context.getString(condition.label),
        contentScale = ContentScale.Fit,
        modifier = modifier.size(size),
    )
}

@Composable
fun conditionLabel(condition: WeatherCondition): String = LocalContext.current.getString(condition.label)

/** 날씨를 눌렀을 때 열 화면: 기기의 날씨 앱([WeatherApps]), 없으면 이 앱. */
fun weatherAppIntent(context: Context): Intent =
    WeatherApps.launchIntent(context)
        ?: Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

fun openWeatherAppAction(context: Context): Action = actionStartActivity(weatherAppIntent(context))

