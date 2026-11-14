package com.zero.assistant.ui

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

internal class OverlayBridgeShape : Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val radius = with(density) { 24.dp.toPx() }.coerceAtMost(size.height / 2f)
        val podRadius = size.height / 2f
        val path = Path().apply {
            moveTo(podRadius, 0f)
            lineTo(size.width - radius, 0f)
            arcTo(
                Rect(size.width - radius * 2f, 0f, size.width, radius * 2f),
                -90f,
                90f,
                false,
            )
            lineTo(size.width, size.height - radius)
            arcTo(
                Rect(size.width - radius * 2f, size.height - radius * 2f, size.width, size.height),
                0f,
                90f,
                false,
            )
            lineTo(podRadius, size.height)
            arcTo(
                Rect(0f, 0f, podRadius * 2f, podRadius * 2f),
                90f,
                180f,
                false,
            )
            close()
        }
        return Outline.Generic(path)
    }
}
