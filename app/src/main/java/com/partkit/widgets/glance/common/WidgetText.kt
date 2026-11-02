package com.partkit.widgets.glance.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.TypedValue
import androidx.annotation.ColorInt
import androidx.annotation.FontRes
import com.partkit.widgets.R
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil
import kotlin.math.max

/**
 * 번들 폰트(PartKit Sans)로 텍스트를 비트맵에 그린다.
 *
 * 런처는 위젯 레이아웃을 CONTEXT_RESTRICTED 컨텍스트로 펼치고, 이때 TextView는 android:fontFamily의
 * 앱 폰트 리소스를 읽지 않고 시스템 폰트로 대체한다(TextView#readTextAppearance). RemoteViews로는
 * Typeface 객체도 넘길 수 없다. 그래서 텍스트를 앱 프로세스에서 비트맵으로 그려 ImageView로 보낸다.
 */
object WidgetText {

    enum class Weight(@param:FontRes val font: Int) {
        Regular(R.font.partkit_sans_regular),
        Medium(R.font.partkit_sans_medium),
    }

    /** 텍스트 그림자(At a Glance처럼 배경화면 위에 바로 그릴 때). 값은 dp 단위. */
    data class Shadow(@param:ColorInt val color: Int, val radiusDp: Float, val dyDp: Float)

    private val typefaces = ConcurrentHashMap<Int, Typeface>()

    private fun typeface(context: Context, weight: Weight): Typeface =
        typefaces.getOrPut(weight.font) { context.applicationContext.resources.getFont(weight.font) }

    /**
     * 틴트용 알파 마스크(ALPHA_8). 색은 ImageView 틴트로 입혀서 런처가 테마(다크 모드·동적 색상)에 맞춰
     * 그릴 때 정해지게 한다.
     */
    fun mask(
        context: Context,
        text: CharSequence,
        sizeSp: Float,
        weight: Weight,
        maxWidthPx: Int,
        maxLines: Int = 1,
        align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL,
        minSizeSp: Float = sizeSp,
    ): Bitmap = draw(context, text, sizeSp, minSizeSp, weight, maxWidthPx, maxLines, align, color = null, shadow = null)

    /** 색과 그림자를 구운 ARGB 비트맵. 배경화면 위 고정 색 텍스트(At a Glance)에 쓴다. */
    fun colored(
        context: Context,
        text: CharSequence,
        sizeSp: Float,
        weight: Weight,
        maxWidthPx: Int,
        @ColorInt color: Int,
        shadow: Shadow? = null,
    ): Bitmap = draw(context, text, sizeSp, sizeSp, weight, maxWidthPx, 1, Layout.Alignment.ALIGN_NORMAL, color, shadow)

    private fun draw(
        context: Context,
        text: CharSequence,
        sizeSp: Float,
        minSizeSp: Float,
        weight: Weight,
        maxWidthPx: Int,
        maxLines: Int,
        align: Layout.Alignment,
        @ColorInt color: Int?,
        shadow: Shadow?,
    ): Bitmap {
        val metrics = context.resources.displayMetrics
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = typeface(context, weight)
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sizeSp, metrics)
            this.color = color ?: 0xFF000000.toInt()
            if (shadow != null) {
                setShadowLayer(shadow.radiusDp * metrics.density, 0f, shadow.dyDp * metrics.density, shadow.color)
            }
        }
        // 글리프가 진행폭 밖으로 살짝 나가는 경우(°, f 등)와 그림자를 위한 여백.
        val pad = ceil(metrics.density + (shadow?.let { (it.radiusDp + it.dyDp) * metrics.density } ?: 0f)).toInt()
        val available = max(1, maxWidthPx - pad * 2)
        var desired = ceil(Layout.getDesiredWidth(text, paint)).toInt()
        // 한 줄 텍스트가 넘치면 먼저 minSizeSp까지 글자를 줄여 맞추고, 그래도 넘치면 말줄임표로 자른다.
        if (maxLines == 1 && desired > available && minSizeSp < sizeSp) {
            val minPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, minSizeSp, metrics)
            // 비율 계산 후 올림 오차로 1px 넘쳐 말줄임표가 붙지 않도록 약간 여유를 둔다.
            paint.textSize = max(minPx, paint.textSize * available / desired * 0.97f)
            desired = ceil(Layout.getDesiredWidth(text, paint)).toInt()
        }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, desired.coerceIn(1, available))
            .setAlignment(align)
            .setIncludePad(false)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()

        val config = if (color == null) Bitmap.Config.ALPHA_8 else Bitmap.Config.ARGB_8888
        val bitmap = Bitmap.createBitmap(layout.width + pad * 2, max(1, layout.height) + pad * 2, config)
        Canvas(bitmap).apply {
            translate(pad.toFloat(), pad.toFloat())
            layout.draw(this)
        }
        return bitmap
    }
}
