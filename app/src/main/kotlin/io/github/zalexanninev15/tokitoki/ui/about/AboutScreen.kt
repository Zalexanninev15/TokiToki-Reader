package io.github.zalexanninev15.tokitoki.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.zalexanninev15.tokitoki.BuildConfig
import io.github.zalexanninev15.tokitoki.R

private const val CONTACTS_URL = "https://z15.neocities.org/contacts"
private const val DONATE_URL = "https://z15.neocities.org/donate/"
private const val MASTODON_URL = "https://mastodon.ml/@voltmor"
private const val SHARKEY_URL = "https://shitpost.poridge.club/@qkon4"
private const val REPO_URL = "https://github.com/Zalexanninev15/TokiToki-Reader"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit, onOpenLink: (String) -> Unit) {
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.about_body),
                style = MaterialTheme.typography.bodyMedium,
            )

            LinkCard(R.string.about_author, listOf(
                R.string.about_mastodon to MASTODON_URL,
                R.string.about_sharkey to SHARKEY_URL,
            ), onOpenLink)

            LinkCard(R.string.about_support, listOf(
                R.string.about_contacts to CONTACTS_URL,
                R.string.about_donate to DONATE_URL,
                R.string.about_source to REPO_URL,
            ), onOpenLink)
        }
    }
}

@Composable
private fun LinkCard(
    titleRes: Int,
    links: List<Pair<Int, String>>,
    onOpenLink: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            links.forEach { (labelRes, url) ->
                TextButton(onClick = { onOpenLink(url) }) {
                    Text(stringResource(labelRes))
                }
            }
        }
    }
}
