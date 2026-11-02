package com.partkit.widgets.ui

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.partkit.widgets.LocaleSync
import com.partkit.widgets.R
import com.partkit.widgets.data.AppLocale
import com.partkit.widgets.data.CalendarRepository
import com.partkit.widgets.data.LocationResolver
import com.partkit.widgets.data.TempUnit
import com.partkit.widgets.data.WeatherApps
import com.partkit.widgets.data.WeatherStore
import com.partkit.widgets.glance.ataglance.AtAGlanceReceiver
import com.partkit.widgets.glance.weather.CurrentWeatherReceiver
import com.partkit.widgets.glance.weather.DailyWeatherReceiver
import com.partkit.widgets.glance.weather.ForecastWeatherReceiver
import com.partkit.widgets.glance.weather.HourlyWeatherReceiver
import com.partkit.widgets.sync.WeatherSync
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.reflect.KClass

private data class WidgetEntry(@StringRes val name: Int, @StringRes val description: Int, val receiver: KClass<out GlanceAppWidgetReceiver>)

private val WIDGETS = listOf(
    WidgetEntry(R.string.widget_at_a_glance, R.string.widget_at_a_glance_desc, AtAGlanceReceiver::class),
    WidgetEntry(R.string.widget_weather_current, R.string.widget_weather_current_desc, CurrentWeatherReceiver::class),
    WidgetEntry(R.string.widget_weather_forecast, R.string.widget_weather_forecast_desc, ForecastWeatherReceiver::class),
    WidgetEntry(R.string.widget_weather_hourly, R.string.widget_weather_hourly_desc, HourlyWeatherReceiver::class),
    WidgetEntry(R.string.widget_weather_daily, R.string.widget_weather_daily_desc, DailyWeatherReceiver::class),
)

private val PartKitSans = FontFamily(
    Font(R.font.partkit_sans_regular, FontWeight.Normal),
    Font(R.font.partkit_sans_medium, FontWeight.Medium),
)

/** 앱 화면도 위젯과 같은 번들 폰트(PartKit Sans)를 쓴다(M3 기본 타입 스케일의 글꼴만 교체). */
private val PartKitTypography = Typography().let { base ->
    fun TextStyle.gs() = copy(fontFamily = PartKitSans)
    base.copy(
        displayLarge = base.displayLarge.gs(), displayMedium = base.displayMedium.gs(), displaySmall = base.displaySmall.gs(),
        headlineLarge = base.headlineLarge.gs(), headlineMedium = base.headlineMedium.gs(), headlineSmall = base.headlineSmall.gs(),
        titleLarge = base.titleLarge.gs(), titleMedium = base.titleMedium.gs(), titleSmall = base.titleSmall.gs(),
        bodyLarge = base.bodyLarge.gs(), bodyMedium = base.bodyMedium.gs(), bodySmall = base.bodySmall.gs(),
        labelLarge = base.labelLarge.gs(), labelMedium = base.labelMedium.gs(), labelSmall = base.labelSmall.gs(),
    )
}

class MainActivity : ComponentActivity() {

    /** 설정 "영어로 표시"면 이 화면도 영어로. */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val dark = isSystemInDarkTheme()
            val context = LocalContext.current
            MaterialTheme(
                colorScheme = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context),
                typography = PartKitTypography,
            ) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainer) {
                    SettingsScreen()
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by remember { WeatherStore.flow(context) }.collectAsState()
    var locationError by remember { mutableStateOf(false) }
    var calendarGranted by remember { mutableStateOf(CalendarRepository.hasPermission(context)) }

    fun useDeviceLocation() = scope.launch {
        locationError = !LocationResolver.updateFromDevice(context)
        if (!locationError) WeatherSync.refreshNow(context, force = true)
    }

    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) useDeviceLocation() else locationError = true
    }
    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        calendarGranted = granted
        if (granted) scope.launch {
            WeatherStore.invalidate(context)
            WeatherSync.updateAllWidgets(context)
        }
    }

    LaunchedEffect(Unit) {
        WeatherSync.onWidgetRendered(context)
        // 앱이 화면에 있을 때만 위치를 갱신한다(백그라운드 위치 권한 불필요).
        if (LocationResolver.hasPermission(context) && LocationResolver.updateFromDevice(context)) {
            WeatherSync.refreshNow(context, force = true)
        }
        publishWidgetPreviews(context)
    }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(8.dp))

        SettingsCard(stringResource(R.string.settings_location)) {
            val name = state.location.name
            Text(
                if (state.location.isDefault) stringResource(R.string.settings_location_default, name) else name,
                style = MaterialTheme.typography.titleMedium,
            )
            if (locationError) {
                Text(stringResource(R.string.settings_location_failed), color = MaterialTheme.colorScheme.error)
            }
            FilledTonalButton(onClick = {
                if (LocationResolver.hasPermission(context)) useDeviceLocation()
                else locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            }) { Text(stringResource(R.string.settings_use_current_location)) }
        }

        SettingsCard(stringResource(R.string.settings_unit)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                TempUnit.entries.forEachIndexed { index, unit ->
                    SegmentedButton(
                        selected = state.unit == unit,
                        onClick = {
                            WeatherStore.setUnit(context, unit)
                            scope.launch { WeatherSync.updateAllWidgets(context) }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index, TempUnit.entries.size),
                    ) { Text("°${unit.symbol}") }
                }
            }
        }

        SettingsCard(stringResource(R.string.settings_language)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_english_only), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.settings_english_only_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.englishOnly,
                    onCheckedChange = { enabled ->
                        WeatherStore.setEnglishOnly(context, enabled)
                        LocaleSync.rerenderWidgets(context.applicationContext, relocalizePlace = true)
                        (context as? Activity)?.recreate()
                    },
                )
            }
        }

        SettingsCard(stringResource(R.string.settings_weather_app)) {
            // 날씨 위젯을 눌렀을 때 열리는 앱. 제조사 날씨 앱이 제대로 잡히는지 확인용.
            val intent = remember { WeatherApps.launchIntent(context) }
            val label = remember { WeatherApps.label(context) }
            Text(
                when {
                    intent == null -> stringResource(R.string.settings_weather_app_none)
                    label == null -> stringResource(R.string.settings_weather_app_chooser)
                    else -> label.toString()
                },
                style = MaterialTheme.typography.titleMedium,
            )
            if (intent != null) {
                FilledTonalButton(onClick = { context.startActivity(intent) }) {
                    Text(stringResource(R.string.settings_weather_app_open))
                }
            }
        }

        SettingsCard(stringResource(R.string.settings_calendar)) {
            if (calendarGranted) {
                Text(stringResource(R.string.settings_calendar_on))
            } else {
                FilledTonalButton(onClick = { calendarPermission.launch(Manifest.permission.READ_CALENDAR) }) {
                    Text(stringResource(R.string.settings_calendar_grant))
                }
            }
        }

        SettingsCard(stringResource(R.string.settings_refresh)) {
            val fetchedAt = state.snapshot?.fetchedAt
            Text(
                if (fetchedAt == null) stringResource(R.string.settings_never_updated)
                else stringResource(R.string.settings_updated, formatTime(context, fetchedAt)),
            )
            Button(onClick = { WeatherSync.refreshNow(context, force = true) }) {
                Text(stringResource(R.string.settings_refresh))
            }
        }

        SettingsCard(stringResource(R.string.settings_add_widgets)) {
            WIDGETS.forEach { entry ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(entry.name), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(entry.description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { requestPin(context, entry.receiver) }) { Text("+") }
                }
            }
        }

        Text(
            stringResource(R.string.attribution),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

/**
 * 화면 언어(설정 "영어로 표시" 포함)로 시각을 표시한다. DateUtils의 상대 시각은 프로세스 기본 로캘을 써서
 * 영어 전용 설정을 따르지 않으므로 java.time으로 만든다.
 */
private fun formatTime(context: Context, epochMillis: Long): String {
    val locale = context.resources.configuration.locales[0]
    val skeleton = if (DateFormat.is24HourFormat(context)) "MMMdHm" else "MMMdhm"
    return DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(epochMillis))
}

private fun requestPin(context: Context, receiver: KClass<out GlanceAppWidgetReceiver>) {
    val manager = AppWidgetManager.getInstance(context)
    if (manager.isRequestPinAppWidgetSupported) {
        manager.requestPinAppWidget(ComponentName(context, receiver.java), null, null)
    }
}

/** Android 15+: 위젯 선택기에 실제 모양의 미리보기를 등록한다(시스템이 호출 빈도를 제한). */
private suspend fun publishWidgetPreviews(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
    val manager = GlanceAppWidgetManager(context)
    WIDGETS.forEach { entry ->
        runCatching { manager.setWidgetPreviews(entry.receiver) }
            .onFailure { Log.w("MainActivity", "Preview for ${entry.receiver.simpleName} failed", it) }
    }
}
