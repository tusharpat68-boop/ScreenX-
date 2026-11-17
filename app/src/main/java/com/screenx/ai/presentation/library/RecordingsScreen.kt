package com.screenx.ai.presentation.library

import android.content.ActivityNotFoundException
import android.content.Intent
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.screenx.ai.domain.SortOrder
import com.screenx.ai.presentation.components.GlassCard
import com.screenx.ai.presentation.theme.ScreenXAccent
import com.screenx.ai.utils.TimeFormat

@Composable
fun RecordingsScreen(
    modifier: Modifier = Modifier,
    onEdit: (MediaItem) -> Unit = {},
    viewModel: RecordingsViewModel = viewModel()
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    LibraryEffects(viewModel)

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
            text = stringResource(R.string.nav_recordings),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 12.dp)
        )
        Text(
            text = stringResource(
                R.string.rec_summary, ui.totalCount, Formatter.formatShortFileSize(context, ui.totalBytes)
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        OutlinedTextField(
            value = ui.query,
            onValueChange = viewModel::setQuery,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.rec_search_hint)) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (ui.query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.setQuery("") }) {
                        Icon(Icons.Rounded.Close, contentDescription = null)
                    }
                }
            }
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = ui.favoritesOnly,
                onClick = viewModel::toggleFavoritesOnly,
                label = { Text(stringResource(R.string.filter_favorites)) },
                leadingIcon = { Icon(Icons.Rounded.Star, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            SortOrder.values().forEach { order ->
                FilterChip(
                    selected = ui.sort == order,
                    onClick = { viewModel.setSort(order) },
                    label = {
                        Text(
                            stringResource(
                                when (order) {
                                    SortOrder.NEWEST -> R.string.sort_newest
                                    SortOrder.OLDEST -> R.string.sort_oldest
                                    SortOrder.NAME -> R.string.sort_name
                                    SortOrder.SIZE -> R.string.sort_size
                                    SortOrder.DURATION -> R.string.sort_duration
                                }
                            )
                        )
                    }
                )
            }
        }

        when {
            ui.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            ui.visible.isEmpty() -> EmptyState(
                stringResource(if (ui.totalCount == 0) R.string.rec_empty else R.string.rec_empty_search)
            )
            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(ui.visible, key = { it.id }) { item ->
                    VideoCard(
                        item = item,
                        favorite = item.fileName in ui.favorites,
                        onPlay = { launch(viewModel.viewIntent(item)) },
                        onFavorite = { viewModel.toggleFavorite(item) },
                        onShare = {
                            launch(Intent.createChooser(viewModel.shareIntent(item), null))
                        },
                        onEdit = { onEdit(item) },
                        onRename = { renameTarget = item },
                        onDetails = { detailsTarget = item },
                        onDelete = { deleteTarget = item }
                    )
                }
                item { Box(Modifier.height(8.dp)) }
            }
        }
    }

    renameTarget?.let { target ->
        RenameDialog(
            initial = target.title,
            onConfirm = { name ->
                viewModel.rename(target, name)
                renameTarget = null
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
            },
            onDismiss = { deleteTarget = null }
        )
    }
    detailsTarget?.let { target ->
        val fps by produceState<Int?>(null, target.id) { value = viewModel.estimateFps(target) }
        val unknown = stringResource(R.string.details_unknown)
        val bitrate = if (target.durationMs > 0) {
            val kbps = target.sizeBytes * 8L / target.durationMs // bits per ms = kbit/s
            stringResource(R.string.details_bitrate_value, kbps.toInt())
        } else {
            unknown
        }
        DetailsDialog(
            rows = listOf(
                stringResource(R.string.details_name) to target.fileName,
                stringResource(R.string.details_location) to viewModel.locationOf(target),
                stringResource(R.string.details_resolution) to resolutionLabel(target).ifEmpty { unknown },
                stringResource(R.string.details_fps) to
                    (fps?.let { stringResource(R.string.details_fps_value, it) } ?: unknown),
                stringResource(R.string.details_duration) to TimeFormat.elapsed(target.durationMs),
                stringResource(R.string.details_size) to Formatter.formatFileSize(context, target.sizeBytes),
                stringResource(R.string.details_bitrate) to bitrate,
                stringResource(R.string.details_date) to formatDate(target.addedMs)
            ),
            onClose = { detailsTarget = null }
        )
    }
}

@Composable
private fun VideoCard(
    item: MediaItem,
    favorite: Boolean,
    onPlay: () -> Unit,
    onFavorite: () -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onRename: () -> Unit,
    onDetails: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }

    GlassCard(modifier = Modifier.fillMaxWidth(), onClick = onPlay) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                Thumbnail(
                    item = item,
                    kind = MediaKind.VIDEO,
                    placeholder = Icons.Rounded.VideoLibrary,
                    modifier = Modifier.width(112.dp).height(64.dp).clip(RoundedCornerShape(12.dp))
                )
                Text(
                    text = TimeFormat.elapsed(item.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .background(Color(0xAA000000), RoundedCornerShape(6.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOf(resolutionLabel(item), Formatter.formatShortFileSize(context, item.sizeBytes))
                        .filter { it.isNotEmpty() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = ScreenXAccent,
                    maxLines = 1
                )
                Text(
                    text = formatDate(item.addedMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = onFavorite, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = if (favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = stringResource(
                            if (favorite) R.string.favorite_remove else R.string.favorite_add
                        ),
                        tint = if (favorite) Color(0xFFFACC15) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box {
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = null)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_play)) },
                            leadingIcon = { Icon(Icons.Rounded.PlayArrow, contentDescription = null) },
                            onClick = { menuOpen = false; onPlay() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_share)) },
                            leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
                            onClick = { menuOpen = false; onShare() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_edit)) },
                            leadingIcon = { Icon(Icons.Rounded.ContentCut, contentDescription = null) },
                            onClick = { menuOpen = false; onEdit() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_rename)) },
                            leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                            onClick = { menuOpen = false; onRename() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_details)) },
                            leadingIcon = { Icon(Icons.Rounded.Info, contentDescription = null) },
                            onClick = { menuOpen = false; onDetails() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_delete)) },
                            leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                            onClick = { menuOpen = false; onDelete() }
                        )
                    }
                }
            }
        }
    }
}
