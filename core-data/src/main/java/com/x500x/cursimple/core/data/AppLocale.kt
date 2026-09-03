package com.x500x.cursimple.core.data

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.Locale

/**
 * 界面语言的读取与应用。
 *
 * 语言要在 Activity 附着基础上下文时就确定，那一刻还不能挂起等待 DataStore，
 * 所以这里另存一份同步可读的副本；小组件与通知运行在没有 Activity 的上下文里，
 * 同样从这份副本取值，保证三处显示同一种语言。
 */
object AppLocale {

    private const val PREFS_NAME = "app_locale"
    private const val KEY_LANGUAGE = "app_language"

    val KEY_APP_LANGUAGE_PREFERENCE = stringPreferencesKey(KEY_LANGUAGE)

    /** 同步读取当前语言设置，供 [wrap] 在主线程调用。 */
    fun current(context: Context): AppLanguage {
        // Application.attachBaseContext 阶段 applicationContext 还是 null，只能用传进来的这个
        val stored = context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, null)
            ?: return AppLanguage.System
        return runCatching { AppLanguage.valueOf(stored) }.getOrDefault(AppLanguage.System)
    }

    /** 把语言写入同步副本，DataStore 的写入由调用方另行完成。 */
    fun cache(context: Context, language: AppLanguage) {
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, language.name)
            .apply()
    }

    /** 应用启动时把 DataStore 里的值同步到副本，覆盖备份恢复等绕过设置界面的写入。 */
    fun syncCacheFrom(context: Context, repository: UserPreferencesRepository) {
        runCatching {
            val language = runBlocking { repository.preferencesFlow.first().appLanguage }
            cache(context, language)
        }
    }

    /** 按当前语言设置包装上下文；跟随系统时原样返回。 */
    fun wrap(context: Context): Context =
        runCatching { wrap(context, current(context)) }.getOrDefault(context)

    fun wrap(context: Context, language: AppLanguage): Context {
        val locale = language.toLocale() ?: return context
        val configuration = Configuration(context.resources.configuration)
        Locale.setDefault(locale)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            configuration.setLocales(LocaleList(locale))
        } else {
            @Suppress("DEPRECATION")
            configuration.locale = locale
        }
        return context.createConfigurationContext(configuration)
    }
}

/** 跟随系统时没有指定的区域，返回 null。 */
fun AppLanguage.toLocale(): Locale? = when (this) {
    AppLanguage.System -> null
    AppLanguage.Chinese -> Locale.SIMPLIFIED_CHINESE
    AppLanguage.TraditionalChinese -> Locale.TRADITIONAL_CHINESE
    AppLanguage.English -> Locale.ENGLISH
}
