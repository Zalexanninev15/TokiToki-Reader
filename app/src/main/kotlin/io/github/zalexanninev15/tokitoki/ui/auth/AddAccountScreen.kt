package io.github.zalexanninev15.tokitoki.ui.auth

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.zalexanninev15.tokitoki.AppContainer
import io.github.zalexanninev15.tokitoki.R
import io.github.zalexanninev15.tokitoki.domain.model.SourceKind

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAccountScreen(
    container: AppContainer,
    callback: Uri?,
    onCallbackHandled: () -> Unit,
    onDone: () -> Unit,
    onBack: () -> Unit,
) {
    val viewModel: AuthViewModel = viewModel(
        factory = AuthViewModel.Factory(container.authService),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(state.authorizeUrl) {
        state.authorizeUrl?.let { url ->
            CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, url.toUri())
            viewModel.onAuthorizeUrlConsumed()
        }
    }

    LaunchedEffect(callback) {
        callback?.let {
            viewModel.completeSignIn(it)
            onCallbackHandled()
        }
    }

    LaunchedEffect(state.completed) {
        if (state.completed) onDone()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.add_account)) },
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
            modifier = Modifier.padding(padding).fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.choose_service),
                style = MaterialTheme.typography.titleMedium,
            )

            // Cards instead of chips: each service needs a line of explanation, and the
            // Sharkey question comes up every time it is not spelled out.
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ServiceOption(
                    selected = state.source == SourceKind.MASTODON,
                    title = "Mastodon",
                    subtitle = stringResource(R.string.mastodon_hint),
                    onClick = { viewModel.selectSource(SourceKind.MASTODON) },
                )
                ServiceOption(
                    selected = state.source == SourceKind.MISSKEY,
                    title = stringResource(R.string.misskey_title),
                    subtitle = stringResource(R.string.misskey_hint),
                    onClick = { viewModel.selectSource(SourceKind.MISSKEY) },
                )
            }

            OutlinedTextField(
                value = state.instanceUrl,
                onValueChange = viewModel::updateInstanceUrl,
                label = { Text(stringResource(R.string.instance_url)) },
                placeholder = {
                    Text(
                        if (state.source == SourceKind.MISSKEY) "misskey.io" else "mastodon.social",
                    )
                },
                supportingText = { Text(stringResource(R.string.instance_url_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go,
                ),
            )

            Button(
                onClick = viewModel::beginSignIn,
                enabled = state.instanceUrl.isNotBlank() && !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.sign_in))
            }

            if (state.busy) CircularProgressIndicator()

            state.error?.let { message ->
                Text(
                    text = "${stringResource(R.string.auth_failed)}: $message",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServiceOption(
    selected: Boolean,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
