package io.github.zalexanninev15.tokitoki.ui.media

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import io.github.zalexanninev15.tokitoki.R
import io.github.zalexanninev15.tokitoki.util.Downloads
import kotlinx.coroutines.launch

@Composable
fun ImageViewerScreen(
    url: String,
    onClose: () -> Unit,
    onOpenExternally: (String) -> Unit = {},
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var dismissDrag by remember { mutableFloatStateOf(0f) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val savedMessage = stringResource(R.string.image_saved)
    val failedMessage = stringResource(R.string.image_save_failed)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 6f)
                    // Panning is pointless at 1x and would just drift the image away
                    // from centre, so it is gated on being zoomed in.
                    offset = if (scale > 1f) offset + pan else Offset.Zero
                }
            }
            // Drag down to dismiss, but only at 1x: while zoomed in a vertical drag
            // means "pan", and stealing it would make the image impossible to explore.
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (dismissDrag > 220f) onClose() else dismissDrag = 0f
                    },
                    onDragCancel = { dismissDrag = 0f },
                ) { _, delta ->
                    if (scale <= 1f) dismissDrag = (dismissDrag + delta).coerceAtLeast(0f)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // A silent black screen was the old failure mode: Mastodon serves `gifv`
        // attachments as MP4, which no image decoder can open, and the viewer just
        // showed nothing. Now the failure is stated and the file can be opened outside.
        SubcomposeAsyncImage(
            model = url,
            contentDescription = stringResource(R.string.cd_image),
            contentScale = ContentScale.Fit,
            loading = {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
            },
            error = {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                ) {
                    Text(
                        text = stringResource(R.string.image_unsupported),
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    )
                    TextButton(onClick = { onOpenExternally(url) }) {
                        Text(stringResource(R.string.open_link))
                    }
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y + dismissDrag,
                    // Fades out as it is dragged away, so the gesture feels connected
                    // to the result instead of snapping at a threshold.
                    alpha = (1f - dismissDrag / 600f).coerceIn(0.3f, 1f),
                ),
        )

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                // Without this the buttons sit under the status bar and the notch,
                // which on most phones puts them out of comfortable thumb reach.
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(12.dp),
        ) {
            IconButton(
                onClick = {
                    scope.launch {
                        val result = Downloads.saveImage(context, url)
                        snackbar.showSnackbar(
                            if (result.isSuccess) savedMessage else failedMessage,
                        )
                    }
                },
            ) {
                Icon(Icons.Default.Download, stringResource(R.string.action_save), tint = Color.White)
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, stringResource(R.string.cd_close), tint = Color.White)
            }
        }

        SnackbarHost(
            snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars),
        )
    }
}
