package com.partkit.widgets.debug

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.Process
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.compose
import com.partkit.widgets.glance.ataglance.AtAGlanceWidget
import com.partkit.widgets.glance.weather.CurrentWeatherWidget
import com.partkit.widgets.glance.weather.DailyWeatherWidget
import com.partkit.widgets.glance.weather.ForecastWeatherWidget
import com.partkit.widgets.glance.weather.HourlyWeatherWidget
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.io.File

/**
 * 디버그 전용: 화면을 띄우지 않고 모든 위젯을 여러 크기·라이트/다크로 그려 PNG로 저장한다.
 *
 *   adb shell am broadcast -n com.partkit.widgets/.debug.WidgetSnapshotReceiver
 *   adb pull /sdcard/Android/data/com.partkit.widgets/files/snapshots/
 *
 * 런처와 같은 조건으로 그리기 위해 RemoteViews를 CONTEXT_RESTRICTED 컨텍스트로 적용한다.
 * (런처는 위젯 레이아웃을 제한된 컨텍스트로 펼치며, 이때 TextView는 앱 폰트 리소스를 읽지 않는다.)
 */
class WidgetSnapshotReceiver : BroadcastReceiver() {

    private data class Spec(val widget: GlanceAppWidget, val sizes: List<DpSize>)

    /** 실제 휴대폰(4열 그리드) 기준 크기: 4×1 ≈ 360×90, 2×2 ≈ 170×170, 4×2 ≈ 360×190, 4×4 ≈ 360×390. */
    private val specs = listOf(
        Spec(AtAGlanceWidget(), listOf(DpSize(360.dp, 90.dp))),
        Spec(CurrentWeatherWidget(), listOf(DpSize(170.dp, 170.dp), DpSize(80.dp, 80.dp))),
        Spec(ForecastWeatherWidget(), listOf(DpSize(360.dp, 190.dp), DpSize(360.dp, 90.dp))),
        Spec(HourlyWeatherWidget(), listOf(DpSize(360.dp, 190.dp), DpSize(360.dp, 120.dp))),
        Spec(DailyWeatherWidget(), listOf(DpSize(360.dp, 190.dp), DpSize(360.dp, 390.dp))),
    )

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        MainScope().launch {
            try {
                if (intent.getStringExtra("mode") == "previews") {
                    dumpStoredPreviews(context)
                    return@launch
                }
                val dir = File(context.getExternalFilesDir(null), "snapshots").apply { mkdirs() }
                for (night in listOf(false, true)) {
                    val file = File(dir, if (night) "widgets_dark.png" else "widgets_light.png")
                    val sheet = renderSheet(themed(context, night), night)
                    file.outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    Log.i(TAG, "Wrote ${file.absolutePath}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Snapshot failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    /**
     * 시스템이 저장해 둔 위젯 선택기 미리보기(Android 15+ 생성형 미리보기)를 그대로 그려 저장한다.
     *   adb shell am broadcast -n com.partkit.widgets/.debug.WidgetSnapshotReceiver --es mode previews
     */
    private fun dumpStoredPreviews(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val host = hostContext(context)
        val dir = File(context.getExternalFilesDir(null), "snapshots").apply { mkdirs() }
        val tiles = manager.getInstalledProvidersForPackage(context.packageName, null).mapNotNull { info ->
            val preview = manager.getWidgetPreview(info.provider, Process.myUserHandle(), AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN)
            Log.i(TAG, "Stored preview for ${info.provider.shortClassName}: ${if (preview == null) "none" else "present"}")
            preview?.let { withBackdrop(context, draw(context, it.apply(host, FrameLayout(host)), DpSize(340.dp, 170.dp)), true) }
        }
        if (tiles.isEmpty()) return
        val file = File(dir, "stored_previews.png")
        file.outputStream().use { stack(context, tiles).compress(Bitmap.CompressFormat.PNG, 100, it) }
        Log.i(TAG, "Wrote ${file.absolutePath}")
    }

    private fun themed(context: Context, night: Boolean): Context {
        val config = Configuration(context.resources.configuration)
        config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
            (if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
        return context.createConfigurationContext(config)
    }

    /** 런처가 쓰는 것과 같은 제한된 컨텍스트. */
    private fun hostContext(context: Context): Context =
        context.createPackageContext(context.packageName, Context.CONTEXT_RESTRICTED)
            .createConfigurationContext(context.resources.configuration)

    private suspend fun renderSheet(context: Context, night: Boolean): Bitmap {
        val host = hostContext(context)
        val tiles = mutableListOf<Bitmap>()
        for (spec in specs) for (size in spec.sizes) {
            Log.i(TAG, "Rendering ${spec.widget::class.simpleName} @ $size")
            val views: RemoteViews = spec.widget.compose(context, size = size)
            val bitmap = draw(context, views.apply(host, FrameLayout(host)), size)
            // At a Glance는 배경이 없으므로 어두운 배경화면 위에 그린다.
            val lightWallpaper = spec.widget !is AtAGlanceWidget && !night
            tiles += withBackdrop(context, bitmap, lightWallpaper)
        }
        return stack(context, tiles)
    }

    private fun px(context: Context, dp: Float) = (dp * context.resources.displayMetrics.density).toInt()

    private fun draw(context: Context, view: View, size: DpSize): Bitmap {
        val w = px(context, size.width.value)
        val h = px(context, size.height.value)
        view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, w, h)
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
    }

    /** 배경화면 흉내: 위젯 뒤에 그라데이션을 깔아 투명 배경 위젯도 보이게 한다. */
    private fun withBackdrop(context: Context, tile: Bitmap, lightWallpaper: Boolean): Bitmap {
        val pad = px(context, 16f)
        val out = Bitmap.createBitmap(tile.width + pad * 2, tile.height + pad * 2, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val colors = if (lightWallpaper) intArrayOf(0xFFE8EEF8.toInt(), 0xFFC9D6EA.toInt())
        else intArrayOf(0xFF2A3350.toInt(), 0xFF10131C.toInt())
        canvas.drawPaint(Paint().apply {
            shader = LinearGradient(0f, 0f, out.width.toFloat(), out.height.toFloat(), colors[0], colors[1], Shader.TileMode.CLAMP)
        })
        canvas.drawBitmap(tile, pad.toFloat(), pad.toFloat(), null)
        return out
    }

    private fun stack(context: Context, tiles: List<Bitmap>): Bitmap {
        val gap = px(context, 6f)
        val width = tiles.maxOf { it.width }
        val out = Bitmap.createBitmap(width, tiles.sumOf { it.height } + gap * (tiles.size - 1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)
        var y = 0
        for (tile in tiles) {
            canvas.drawBitmap(tile, 0f, y.toFloat(), null)
            y += tile.height + gap
        }
        return out
    }

    private companion object {
        const val TAG = "WidgetSnapshot"
    }
}
