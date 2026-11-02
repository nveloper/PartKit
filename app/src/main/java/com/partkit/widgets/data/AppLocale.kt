package com.partkit.widgets.data

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.core.content.edit
import java.util.Locale

/**
 * 앱 설정 "영어로 표시". 켜면 모든 위젯과 앱 화면을 시스템·앱별 언어와 관계없이 영어로 그린다.
 *
 * Android 13+의 앱별 언어(LocaleManager)는 Android 12(minSdk 31)에서 쓸 수 없고 시스템 설정과도 얽히므로,
 * 이 앱만의 설정으로 두고 문자열을 읽는 컨텍스트를 영어 구성으로 감싸서([wrap]) 적용한다.
 * - 위젯: Glance 구성 안의 LocalContext를 감싼 컨텍스트로 바꾼다.
 * - 앱 화면: Activity#attachBaseContext에서 감싼다.
 */
object AppLocale {

    private const val PREFS = "app_locale"
    private const val KEY_ENGLISH_ONLY = "english_only"

    val ENGLISH: Locale = Locale.US

    fun isEnglishOnly(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENGLISH_ONLY, false)

    fun setEnglishOnly(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(KEY_ENGLISH_ONLY, enabled) }
    }

    /** 문자열·날짜 형식에 쓸 로캘. 영어 전용이면 영어, 아니면 컨텍스트의 언어. */
    fun locale(context: Context): Locale =
        if (isEnglishOnly(context)) ENGLISH else context.resources.configuration.locales[0]

    /** 영어 전용이면 영어 구성의 컨텍스트로 감싼다. 아니면 그대로 돌려준다. */
    fun wrap(context: Context, englishOnly: Boolean = isEnglishOnly(context)): Context {
        if (!englishOnly) return context
        val config = Configuration(context.resources.configuration).apply { setLocales(LocaleList(ENGLISH)) }
        return context.createConfigurationContext(config)
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
