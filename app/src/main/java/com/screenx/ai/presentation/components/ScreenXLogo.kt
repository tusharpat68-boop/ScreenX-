package com.screenx.ai.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.screenx.ai.presentation.theme.ScreenXAccent
import com.screenx.ai.presentation.theme.ScreenXGradients

/** ScreenX AI logo: gradient rounded square with a record ring and an accent dot. */
@Composable
fun ScreenXLogo(modifier: Modifier = Modifier, logoSize: Dp = 48.dp) {
    Canvas(modifier = modifier.size(logoSize)) {
        val s = size.minDimension
        drawRoundRect(
            brush = ScreenXGradients.brand,
            cornerRadius = CornerRadius(s * 0.28f, s * 0.28f),
            size = Size(s, s)
        )
        drawCircle(
            color = Color.White,
            radius = s * 0.27f,
            center = center,
            style = Stroke(width = s * 0.07f)
        )
        drawCircle(color = ScreenXAccent, radius = s * 0.11f, center = center)
    }
}
