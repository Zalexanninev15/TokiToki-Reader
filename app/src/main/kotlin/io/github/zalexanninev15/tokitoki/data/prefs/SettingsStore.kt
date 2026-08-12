package io.github.zalexanninev15.tokitoki.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.zalexanninev15.tokitoki.ui.theme.FontSize
import io.github.zalexanninev15.tokitoki.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "tokitoki_settings")

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val fontSize: FontSize = FontSize.NORMAL,
    val dynamicColor: Boolean = true,
    /** Null means follow the system locale. */
    val languageTag: String? = null,
)

class SettingsStore(private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val FONT = stringPreferencesKey("font_size")
        val DYNAMIC = booleanPreferencesKey("dynamic_color")
        val LANGUAGE = stringPreferencesKey("language_tag")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map(::read)

    private fun read(prefs: Preferences) = AppSettings(
        themeMode = prefs[Keys.THEME]?.let { name ->
            ThemeMode.entries.firstOrNull { it.name == name }
        } ?: ThemeMode.SYSTEM,
        fontSize = prefs[Keys.FONT]?.let { name ->
            FontSize.entries.firstOrNull { it.name == name }
        } ?: FontSize.NORMAL,
        dynamicColor = prefs[Keys.DYNAMIC] ?: true,
        languageTag = prefs[Keys.LANGUAGE]?.takeIf { it.isNotBlank() },
    )

    suspend fun setTheme(mode: ThemeMode) = context.dataStore.edit { it[Keys.THEME] = mode.name }

    suspend fun setFontSize(size: FontSize) = context.dataStore.edit { it[Keys.FONT] = size.name }

    suspend fun setDynamicColor(enabled: Boolean) =
        context.dataStore.edit { it[Keys.DYNAMIC] = enabled }

    suspend fun setLanguage(tag: String?) = context.dataStore.edit {
        if (tag == null) it.remove(Keys.LANGUAGE) else it[Keys.LANGUAGE] = tag
    }
}
