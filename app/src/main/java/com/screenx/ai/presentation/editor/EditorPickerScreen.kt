package com.screenx.ai.presentation.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.screenx.ai.presentation.components.GlassCard
import com.screenx.ai.presentation.library.EmptyState
import com.screenx.ai.presentation.library.RecordingsViewModel
import com.screenx.ai.presentation.library.Thumbnail
import com.screenx.ai.presentation.library.resolutionLabel
import com.screenx.ai.presentation.theme.ScreenXAccent
import com.screenx.ai.utils.TimeFormat

/** The Editor tab: choose which recording to edit. */
@Composable
fun EditorPickerScreen(
    onPick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecordingsViewModel = viewModel()
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text(
            text = stringResource(R.string.title_editor),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 12.dp)
        )
        Text(
            text = stringResource(R.string.editor_pick_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        when {
            ui.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            ui.totalCount == 0 -> EmptyState(stringResource(R.string.editor_pick_empty))
            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(ui.visible, key = { it.id }) { item ->
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
        }
    }
}
