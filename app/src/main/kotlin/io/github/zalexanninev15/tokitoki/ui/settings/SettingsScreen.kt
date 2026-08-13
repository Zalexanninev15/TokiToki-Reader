package io.github.zalexanninev15.tokitoki.ui.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import android.app.Activity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.zalexanninev15.tokitoki.AppContainer
import io.github.zalexanninev15.tokitoki.R
import io.github.zalexanninev15.tokitoki.data.prefs.AppSettings
import io.github.zalexanninev15.tokitoki.util.LocaleController
import io.github.zalexanninev15.tokitoki.ui.theme.FontSize
import io.github.zalexanninev15.tokitoki.ui.theme.ThemeMode
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(container: AppContainer, onBack: () -> Unit) {
    val context = LocalContext.current
    var currentLanguage by remember { mutableStateOf(LocaleController.current(context)) }
    val settings by container.settingsStore.settings
        .collectAsStateWithLifecycle(initialValue = AppSettings())
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            stringResource(R.string.cd_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionTitle(stringResource(R.string.appearance))

            ThemeMode.entries.forEach { mode ->
                RadioRow(
                    label = stringResource(
                        when (mode) {
                            ThemeMode.SYSTEM -> R.string.theme_system
                            ThemeMode.LIGHT -> R.string.theme_light
                            ThemeMode.DARK -> R.string.theme_dark
                            ThemeMode.AMOLED -> R.string.theme_amoled
                        },
                    ),
                    selected = settings.themeMode == mode,
                    onSelect = { scope.launch { container.settingsStore.setTheme(mode) } },
                )
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.dynamic_color),
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = settings.dynamicColor,
                        // Meaningless under AMOLED, which pins surfaces to black.
                        enabled = settings.themeMode != ThemeMode.AMOLED,
                        onCheckedChange = { enabled ->
                            scope.launch { container.settingsStore.setDynamicColor(enabled) }
                        },
                    )
                }
            }

            HorizontalDivider()
            SectionTitle(stringResource(R.string.font_size))

            FontSize.entries.forEach { size ->
                RadioRow(
                    label = stringResource(
                        when (size) {
                            FontSize.SMALL -> R.string.font_small
                            FontSize.NORMAL -> R.string.font_normal
                            FontSize.LARGE -> R.string.font_large
                            FontSize.EXTRA_LARGE -> R.string.font_extra_large
                        },
                    ),
                    selected = settings.fontSize == size,
                    onSelect = { scope.launch { container.settingsStore.setFontSize(size) } },
                )
            }

            HorizontalDivider()
            SectionTitle(stringResource(R.string.language))
            listOf(
                null to R.string.language_system,
                "en" to R.string.language_en,
                "ru" to R.string.language_ru,
                "ja" to R.string.language_ja,
            ).forEach { (tag, labelRes) ->
                RadioRow(
                    label = stringResource(labelRes),
                    selected = currentLanguage == tag,
                    onSelect = {
                        currentLanguage = tag
                        val needsRestart = LocaleController.apply(context, tag)
                        scope.launch { container.settingsStore.setLanguage(tag) }
                        // Below Android 13 the framework does not restart us, so the new
                        // resources only take effect on an explicit recreate.
                        if (needsRestart) (context as? Activity)?.recreate()
                    },
                )
            }

            HorizontalDivider()
            SectionTitle(stringResource(R.string.read_sync))
            Text(
                text = stringResource(R.string.read_sync_local),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // selectable() on the whole row gives a 48dp target and a single
            // screen-reader announcement instead of a stray unlabelled radio button.
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(text = label, modifier = Modifier.padding(start = 12.dp))
    }
}
