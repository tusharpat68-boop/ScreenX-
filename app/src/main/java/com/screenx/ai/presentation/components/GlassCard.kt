package com.screenx.ai.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/** Rounded translucent card with a soft border. Clickable when [onClick] is given. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    val scheme = MaterialTheme.colorScheme
    var m = modifier
        .clip(shape)
        .background(scheme.surface.copy(alpha = 0.85f))
        .border(1.dp, scheme.onSurface.copy(alpha = 0.10f), shape)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Column(modifier = m.padding(16.dp), content = content)
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp)
    )
}
