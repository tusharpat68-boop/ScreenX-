package com.screenx.ai.presentation.library

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.screenx.ai.R
import com.screenx.ai.domain.MediaItem
import com.screenx.ai.domain.MediaKind
import com.screenx.ai.presentation.components.GlassCard
import com.screenx.ai.recording.RecordingSession
import com.screenx.ai.screenshot.rememberScreenshotStarter

@Composable
fun ScreenshotsScreen(
    modifier: Modifier = Modifier,
    viewModel: ScreenshotsViewModel = viewModel()
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val delaySec by viewModel.delaySec.collectAsStateWithLifecycle()
    val recording by RecordingSession.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val startCapture = rememberScreenshotStarter(delaySec)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    LibraryEffects(viewModel)

    var previewTarget by remember { mutableStateOf<MediaItem?>(null) }
    var renameTarget by remember { mutableStateOf<MediaItem?>(null) }
    var deleteTarget by remember { mutableStateOf<MediaItem?>(null) }
    var detailsTarget by remember { mutableStateOf<MediaItem?>(null) }

    fun launch(intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, R.string.error_no_player, Toast.LENGTH_SHORT).show()
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text(
            text = stringResource(R.string.nav_screenshots),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 12.dp)
        )
        Text(
            text = stringResource(R.string.shots_summary, ui.totalCount),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = startCapture,
                enabled = !recording.isActive,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.PhotoCamera, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(
                    text = stringResource(R.string.shots_capture),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            Text(
                text = stringResource(R.string.shots_delay),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(0, 3, 5, 10).forEach { s ->
                    FilterChip(
                        selected = delaySec == s,
                        onClick = { viewModel.setDelay(s) },
                        label = {
                            Text(
                                if (s == 0) stringResource(R.string.delay_now)
                                else stringResource(R.string.delay_seconds, s)
                            )
                        }
                    )
                }
            }
            Text(
                text = stringResource(if (recording.isActive) R.string.shots_blocked else R.string.shots_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Box(Modifier.weight(1f).padding(top = 12.dp)) {
            when {
                ui.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                ui.visible.isEmpty() -> EmptyState(stringResource(R.string.shots_empty))
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 110.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(ui.visible, key = { it.id }) { item ->
                        Thumbnail(
                            item = item,
                            kind = MediaKind.IMAGE,
                            placeholder = Icons.Rounded.Image,
                            sizePx = 420,
                            modifier = Modifier
                                .aspectRatio(0.62f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { previewTarget = item }
                        )
                    }
                }
            }
        }
    }

    previewTarget?.let { target ->
        ScreenshotPreview(
            item = target,
            onClose = { previewTarget = null },
            onShare = { launch(Intent.createChooser(viewModel.shareIntent(target), null)) },
            onRename = { renameTarget = target },
            onDetails = { detailsTarget = target },
            onDelete = { deleteTarget = target }
        )
    }
    renameTarget?.let { target ->
        RenameDialog(
            initial = target.title,
            onConfirm = { name ->
                viewModel.rename(target, name)
                renameTarget = null
                previewTarget = null
            },
            onDismiss = { renameTarget = null }
        )
    }
    deleteTarget?.let { target ->
        DeleteDialog(
            name = target.fileName,
            onConfirm = {
                viewModel.delete(target)
                deleteTarget = null
                previewTarget = null
            },
            onDismiss = { deleteTarget = null }
        )
    }
    detailsTarget?.let { target ->
        val unknown = stringResource(R.string.details_unknown)
        DetailsDialog(
            rows = listOf(
                stringResource(R.string.details_name) to target.fileName,
                stringResource(R.string.details_location) to viewModel.locationOf(target),
                stringResource(R.string.details_resolution) to resolutionLabel(target).ifEmpty { unknown },
                stringResource(R.string.details_size) to Formatter.formatFileSize(context, target.sizeBytes),
                stringResource(R.string.details_date) to formatDate(target.addedMs)
            ),
            onClose = { detailsTarget = null }
        )
    }
}

@Composable
private fun ScreenshotPreview(
    item: MediaItem,
    onClose: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onDetails: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, item.id, item.fileName) {
        value = com.screenx.ai.storage.ThumbnailLoader.loadFull(context, item, 1600)
    }
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().background(Color(0xF2080B14)).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.fileName,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.dialog_close), tint = Color.White)
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val bmp = bitmap
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    CircularProgressIndicator()
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IconButton(onClick = onShare) {
                    Icon(Icons.Rounded.Share, contentDescription = stringResource(R.string.menu_share), tint = Color.White)
                }
                IconButton(onClick = onRename) {
                    Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.menu_rename), tint = Color.White)
                }
                IconButton(onClick = onDetails) {
                    Icon(Icons.Rounded.Info, contentDescription = stringResource(R.string.menu_details), tint = Color.White)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.menu_delete), tint = Color.White)
                }
            }
        }
    }
}
