package com.partkit.widgets.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * 날씨 위젯을 눌렀을 때 열 기기의 날씨 앱을 찾는다.
 *
 * 1. 제조사 기본 날씨 앱(알려진 패키지). 이런 앱은 표준 카테고리를 선언하지 않는 경우가 많다.
 * 2. Intent.CATEGORY_APP_WEATHER를 선언한 앱. 여러 개면 시스템 선택 화면이 뜬다.
 * 3. 없으면 null → 호출하는 쪽에서 이 앱(PartKit)을 연다.
 *
 * 패키지 가시성(Android 11+) 때문에 아래 패키지와 카테고리는 AndroidManifest의 <queries>에도 있어야 한다.
 */
object WeatherApps {

    /** 제조사 기본 날씨 앱. 앞에 있을수록 우선한다. */
    val KNOWN_PACKAGES = listOf(
        "com.sec.android.daemonapp",          // Samsung Weather
        "com.miui.weather2",                  // Xiaomi / Redmi / POCO
        "com.coloros.weather2",               // OPPO / realme (ColorOS)
        "com.oplus.weather",                  // OPPO / OnePlus (ColorOS 12+)
        "net.oneplus.weather",                // OnePlus (OxygenOS)
        "com.vivo.weather",                   // vivo
        "com.huawei.android.totemweather",    // Huawei
        "com.hihonor.android.totemweather",   // Honor
        "com.asus.weathertime",               // ASUS
        "com.lge.sizechangable.weather",      // LG
        "com.google.android.apps.weather",    // Pixel Weather
    )

    /** 열 날씨 앱의 인텐트. 찾지 못하면 null. */
    fun launchIntent(context: Context): Intent? {
        val pm = context.packageManager
        for (pkg in KNOWN_PACKAGES) {
            (pm.getLaunchIntentForPackage(pkg) ?: mainActivityIntent(pm, pkg))
                ?.let { return it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        }
        val selector = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_WEATHER)
        val matches = pm.queryIntentActivities(selector, PackageManager.MATCH_DEFAULT_ONLY)
            .filter { it.activityInfo.packageName != context.packageName }
        return when (matches.size) {
            0 -> null
            1 -> Intent(Intent.ACTION_MAIN)
                .setClassName(matches[0].activityInfo.packageName, matches[0].activityInfo.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            else -> selector.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * 런처 아이콘이 없는 경우(예: Samsung Weather는 설정에서 앱스 화면 아이콘을 숨길 수 있다)를 위해
     * 해당 패키지에서 ACTION_MAIN을 받는 공개 액티비티를 찾는다.
     */
    private fun mainActivityIntent(pm: PackageManager, pkg: String): Intent? {
        val info = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).setPackage(pkg), 0)
            .firstOrNull { it.activityInfo.exported } ?: return null
        return Intent(Intent.ACTION_MAIN).setClassName(pkg, info.activityInfo.name)
    }

    /** 설정 화면 표시용 날씨 앱 이름. 찾지 못하면 null. */
    fun label(context: Context): CharSequence? {
        val intent = launchIntent(context) ?: return null
        val pm = context.packageManager
        val component = intent.component ?: return null // 여러 앱 중 선택
        return runCatching { pm.getApplicationLabel(pm.getApplicationInfo(component.packageName, 0)) }.getOrNull()
    }
}
