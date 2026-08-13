package io.github.zalexanninev15.tokitoki.util

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * In-app language switching without AppCompat.
 *
 * AppCompatDelegate would be the usual answer, but it needs an AppCompatActivity and a
 * Theme.AppCompat-derived theme; this app is pure Compose on android:Theme.Material, so
 * adopting it would mean changing the theme just to change a language.
 *
 * On Android 13+ the system per-app language store is used, so the in-app setting and the
 * one in Android settings are the same setting. Below that the tag is kept in a small
 * SharedPreferences — it has to be readable synchronously from attachBaseContext, which
 * DataStore cannot do.
 */
object LocaleController {

    private const val PREFS = "locale"
    private const val KEY_TAG = "language_tag"

    /** null means "follow the system". */
    fun current(context: Context): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java)
                ?.applicationLocales
                ?.takeIf { !it.isEmpty }
                ?.get(0)
                ?.language
        } else {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_TAG, null)
        }

    /**
     * @return true when the caller must recreate the activity for the change to show.
     */
    fun apply(context: Context, tag: String?): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java)?.applicationLocales =
                if (tag == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
            // The framework restarts the activity itself.
            return false
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .apply { if (tag == null) remove(KEY_TAG) else putString(KEY_TAG, tag) }
            .apply()
        return true
    }

    /** Wraps the base context in attachBaseContext so resources resolve to the choice. */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val tag = base.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TAG, null) ?: return base

        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val configuration = Configuration(base.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        return base.createConfigurationContext(configuration)
    }
}
