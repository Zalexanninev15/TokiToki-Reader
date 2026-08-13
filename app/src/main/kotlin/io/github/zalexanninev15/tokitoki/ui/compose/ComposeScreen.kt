package io.github.zalexanninev15.tokitoki.ui.compose

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.zalexanninev15.tokitoki.R
import io.github.zalexanninev15.tokitoki.domain.model.ComposeTarget
import io.github.zalexanninev15.tokitoki.domain.model.PostVisibility

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeScreen(
    viewModel: ComposeViewModel,
    target: ComposeTarget,
    onBack: () -> Unit,
    onSent: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.sent) {
        if (state.sent) onSent()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (target) {
                            is ComposeTarget.Reply -> stringResource(
                                R.string.compose_reply_to,
                                target.inReplyToHandle,
                            )
                            is ComposeTarget.Quote -> stringResource(R.string.compose_quote)
                            is ComposeTarget.NewPost -> stringResource(R.string.compose_new)
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::toggleWarning) {
                        Icon(
                            Icons.Default.WarningAmber,
                            contentDescription = stringResource(R.string.compose_cw),
                            tint = if (state.warningVisible) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    if (state.isSending) {
                        CircularProgressIndicator(Modifier.size(22.dp).padding(end = 4.dp))
                    } else {
                        IconButton(enabled = state.canSend, onClick = viewModel::send) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = stringResource(R.string.compose_send),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.warningVisible) {
                OutlinedTextField(
                    value = state.contentWarning,
                    onValueChange = viewModel::updateWarning,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.compose_cw)) },
                )
            }

            OutlinedTextField(
                value = state.text,
                onValueChange = viewModel::updateText,
                modifier = Modifier.fillMaxWidth().weight(1f),
                label = { Text(stringResource(R.string.compose_text)) },
            )

            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PostVisibility.entries.forEach { option ->
                    FilterChip(
                        selected = state.visibility == option,
                        onClick = { viewModel.setVisibility(option) },
                        label = { Text(stringResource(option.labelRes())) },
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = state.error.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = state.remaining.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (state.remaining < 0) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

private fun PostVisibility.labelRes(): Int = when (this) {
    PostVisibility.PUBLIC -> R.string.visibility_public
    PostVisibility.UNLISTED -> R.string.visibility_unlisted
    PostVisibility.FOLLOWERS -> R.string.visibility_followers
    PostVisibility.DIRECT -> R.string.visibility_direct
}
