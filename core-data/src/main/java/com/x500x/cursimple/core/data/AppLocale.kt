package com.x500x.cursimple.core.data

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import java.util.Locale

/**
 * Synchronously readable locale mirror serves context attachment, widgets and notifications
 * before DataStore is available.
 */
object AppLocale {

    private const val PREFS_NAME = "app_locale"
    private const val KEY_LANGUAGE = "app_language"

    val KEY_APP_LANGUAGE_PREFERENCE = stringPreferencesKey(KEY_LANGUAGE)

    /** Synchronous locale read for [wrap] on the main thread. */
    fun current(context: Context): AppLanguage {
        // applicationContext may be null during attachBaseContext; use the supplied Context.
        val stored = context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, null)
            ?: return AppLanguage.System
        return runCatching { AppLanguage.valueOf(stored) }.getOrDefault(AppLanguage.System)
    }

    fun cache(context: Context, language: AppLanguage) {
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, language.name)
            .apply()
    }

    /** Reconcile the locale mirror asynchronously after restore paths that bypass settings. */
    suspend fun syncCacheFrom(context: Context, repository: UserPreferencesRepository) {
        runCatching {
            val language = repository.preferencesFlow.first().appLanguage
            cache(context, language)
        }
    }

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

fun AppLanguage.toLocale(): Locale? = when (this) {
    AppLanguage.System -> null
    AppLanguage.Chinese -> Locale.SIMPLIFIED_CHINESE
    AppLanguage.TraditionalChinese -> Locale.TRADITIONAL_CHINESE
    AppLanguage.English -> Locale.ENGLISH
}
