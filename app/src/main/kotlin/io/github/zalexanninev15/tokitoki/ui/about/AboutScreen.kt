package io.github.zalexanninev15.tokitoki.ui.about

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.zalexanninev15.tokitoki.BuildConfig
import io.github.zalexanninev15.tokitoki.R
import io.github.zalexanninev15.tokitoki.util.UpdateChecker
import kotlinx.coroutines.launch

private const val CONTACTS_URL = "https://z15.neocities.org/contacts"
private const val DONATE_URL = "https://z15.neocities.org/donate/"
private const val MASTODON_URL = "https://mastodon.ml/@voltmor"
private const val SHARKEY_URL = "https://shitpost.poridge.club/@qkon4"
private const val REPO_URL = "https://github.com/Zalexanninev15/TokiToki-Reader"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit, onOpenLink: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var updateStatus by remember { mutableStateOf<String?>(null) }
    var updateUrl by remember { mutableStateOf<String?>(null) }

    val upToDate = stringResource(R.string.update_up_to_date)
    val failed = stringResource(R.string.update_failed)
    val availableTemplate = stringResource(R.string.update_available)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Column {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = BuildConfig.VERSION_NAME,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text(
                text = stringResource(R.string.about_body),
                style = MaterialTheme.typography.bodyMedium,
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                    LinkRow(
                        icon = Icons.Default.Refresh,
                        label = stringResource(R.string.update_check),
                        trailing = { if (checking) CircularProgressIndicator(Modifier.size(18.dp)) },
                        onClick = {
                            if (!checking) {
                                checking = true
                                scope.launch {
                                    when (val result = UpdateChecker.check(BuildConfig.VERSION_NAME)) {
                                        is UpdateChecker.Result.UpToDate -> {
                                            updateStatus = upToDate
                                            updateUrl = null
                                        }
                                        is UpdateChecker.Result.Available -> {
                                            updateStatus = availableTemplate.format(result.version)
                                            updateUrl = result.url
                                        }
                                        is UpdateChecker.Result.Failed -> {
                                            updateStatus = failed
                                            updateUrl = null
                                        }
                                    }
                                    checking = false
                                }
                            }
                        },
                    )
                    updateStatus?.let { status ->
                        Text(
                            text = status,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 56.dp, end = 16.dp, bottom = 6.dp),
                        )
                        updateUrl?.let { url ->
                            TextButton(
                                onClick = { onOpenLink(url) },
                                modifier = Modifier.padding(start = 44.dp),
                            ) { Text(stringResource(R.string.update_open)) }
                        }
                    }
                }
            }

            SectionCard(stringResource(R.string.about_author)) {
                LinkRow(Icons.Default.Person, stringResource(R.string.about_mastodon)) {
                    onOpenLink(MASTODON_URL)
                }
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                LinkRow(Icons.Default.Person, stringResource(R.string.about_sharkey)) {
                    onOpenLink(SHARKEY_URL)
                }
            }

            SectionCard(stringResource(R.string.about_support)) {
                LinkRow(Icons.Default.MailOutline, stringResource(R.string.about_contacts)) {
                    onOpenLink(CONTACTS_URL)
                }
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                LinkRow(Icons.Default.Favorite, stringResource(R.string.about_donate)) {
                    onOpenLink(DONATE_URL)
                }
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                LinkRow(Icons.Default.Code, stringResource(R.string.about_source)) {
                    onOpenLink(REPO_URL)
                }
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp),
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(vertical = 6.dp)) { content() }
        }
    }
}

@Composable
private fun LinkRow(
    icon: ImageVector,
    label: String,
    trailing: @Composable () -> Unit = {
        Icon(
            Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
    },
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Text(label, modifier = Modifier.weight(1f))
            trailing()
        }
    }
}
