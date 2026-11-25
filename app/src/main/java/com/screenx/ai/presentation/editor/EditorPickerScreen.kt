package com.screenx.ai.presentation.editor

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.screenx.ai.R
import com.screenx.ai.domain.MediaItem
import com.screenx.ai.domain.MediaKind
import com.screenx.ai.editor.ImportKind
import com.screenx.ai.editor.ImportedMedia
import com.screenx.ai.editor.MediaImporter
import com.screenx.ai.presentation.components.GlassCard
import com.screenx.ai.presentation.library.RecordingsViewModel
import com.screenx.ai.presentation.library.Thumbnail
import com.screenx.ai.presentation.library.resolutionLabel
import com.screenx.ai.presentation.theme.ScreenXAccent
import com.screenx.ai.utils.TimeFormat

/**
 * The Editor tab: start a new project by importing media (gallery, files, music),
 * or edit one of your own recordings.
 */
@Composable
fun EditorPickerScreen(
    onPick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecordingsViewModel = viewModel(),
    importViewModel: ImportViewModel = viewModel()
) {
    val context = LocalContext.current
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val importUi by importViewModel.state.collectAsStateWithLifecycle()
    val media by importViewModel.media.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    val rejectedText = if (importUi.rejected > 0) stringResource(R.string.import_rejected, importUi.rejected) else null
    LaunchedEffect(importUi.rejected) {
        if (rejectedText != null) {
            Toast.makeText(context, rejectedText, Toast.LENGTH_LONG).show()
            importViewModel.dismissRejected()
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) {
        importViewModel.import(it)
    }
    val filesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {
        importViewModel.import(it)
    }
    val musicLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {
        importViewModel.import(it)
    }

    val firstVideo = media.firstOrNull { it.kind == ImportKind.VIDEO }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.title_editor),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        // ---- new project: import ----
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.editor_new_project),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.editor_import_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    ImportButton(
                        icon = Icons.Rounded.PhotoLibrary,
                        label = stringResource(R.string.import_gallery),
                        enabled = !importUi.importing,
                        modifier = Modifier.weight(1f)
                    ) {
                        galleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                        )
                    }
                    ImportButton(
                        icon = Icons.Rounded.Folder,
                        label = stringResource(R.string.import_files),
                        enabled = !importUi.importing,
                        modifier = Modifier.weight(1f)
                    ) { filesLauncher.launch(MediaImporter.FILE_MIME_TYPES) }
                    ImportButton(
                        icon = Icons.Rounded.MusicNote,
                        label = stringResource(R.string.import_music),
                        enabled = !importUi.importing,
                        modifier = Modifier.weight(1f)
                    ) { musicLauncher.launch(MediaImporter.AUDIO_MIME_TYPES) }
                }
                if (importUi.importing) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(
                            text = stringResource(R.string.import_importing),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 10.dp)
                        )
                    }
                }
            }
        }

        // ---- the imported media of this project ----
        if (media.isNotEmpty()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.import_media_title, media.size),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { importViewModel.clear() }) {
                        Text(stringResource(R.string.import_clear))
                    }
                }
            }
            items(media, key = { it.id }) { m ->
                ImportedRow(m, onRemove = { importViewModel.remove(m.id) })
            }
            item {
                Button(
                    onClick = { firstVideo?.let { onPick(it.toLibraryItem()) } },
                    enabled = firstVideo != null,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.import_start)) }
                Text(
                    text = stringResource(
                        if (firstVideo == null) R.string.import_need_video else R.string.import_first_note
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }

        // ---- own recordings ----
        item {
            Text(
                text = stringResource(R.string.editor_recordings_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                text = stringResource(R.string.editor_pick_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        when {
            ui.loading -> item {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            ui.totalCount == 0 -> item {
                Text(
                    text = stringResource(R.string.editor_pick_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }
            else -> items(ui.visible, key = { "rec_" + it.id }) { item ->
                GlassCard(modifier = Modifier.fillMaxWidth(), onClick = { onPick(item) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Thumbnail(
                            item = item,
                            kind = MediaKind.VIDEO,
                            placeholder = Icons.Rounded.VideoLibrary,
                            modifier = Modifier.width(104.dp).height(60.dp).clip(RoundedCornerShape(12.dp))
                        )
                        Column(modifier = Modifier.padding(start = 12.dp)) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = listOf(TimeFormat.elapsed(item.durationMs), resolutionLabel(item))
                                    .filter { it.isNotEmpty() }.joinToString(" · "),
                                style = MaterialTheme.typography.bodyMedium,
                                color = ScreenXAccent
                            )
                        }
                    }
                }
            }
        }
        item { Box(Modifier.height(16.dp)) }
    }
}

@Composable
private fun ImportButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Text(text = label, maxLines = 1, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun ImportedRow(m: ImportedMedia, onRemove: () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(72.dp)
                    .height(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF0B1020)),
                contentAlignment = Alignment.Center
            ) {
                val bmp = m.thumb
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = when (m.kind) {
                            ImportKind.VIDEO -> Icons.Rounded.VideoLibrary
                            ImportKind.IMAGE -> Icons.Rounded.Image
                            ImportKind.AUDIO -> Icons.Rounded.MusicNote
                        },
                        contentDescription = null,
                        tint = Color(0x99FFFFFF),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    text = m.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val kindLabel = stringResource(
                    when (m.kind) {
                        ImportKind.VIDEO -> R.string.import_kind_video
                        ImportKind.IMAGE -> R.string.import_kind_image
                        ImportKind.AUDIO -> R.string.import_kind_audio
                    }
                )
                val details = buildList {
                    add(kindLabel)
                    if (m.durationMs > 0L) add(TimeFormat.elapsed(m.durationMs))
                    if (m.width > 0 && m.height > 0) add("${m.width}×${m.height}")
                }
                Text(
                    text = details.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = ScreenXAccent,
                    maxLines = 1
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.import_remove),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun ImportedMedia.toLibraryItem() = MediaItem(
    id = uri.toString(),
    uri = uri,
    fileName = name,
    title = name,
    sizeBytes = sizeBytes,
    addedMs = 0L,
    durationMs = durationMs,
    width = width,
    height = height
)
