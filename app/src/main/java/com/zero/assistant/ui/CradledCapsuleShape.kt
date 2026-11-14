package com.zero.assistant.ui

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

class CradledCapsuleShape(
    private val cradleRadius: Float = 34f,
) : Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val radius = with(density) { cradleRadius.dp.toPx() }.coerceAtMost(size.width / 2f)
        val corner = (size.height / 2f).coerceAtMost(size.width / 2f)
        val centerX = size.width / 2f
        val path = Path().apply {
            moveTo(corner, 0f)
            lineTo(centerX - radius, 0f)
            arcTo(
                rect = Rect(centerX - radius, -radius, centerX + radius, radius),
                startAngleDegrees = 180f,
                sweepAngleDegrees = 180f,
                forceMoveTo = false,
            )
            lineTo(size.width - corner, 0f)
            arcTo(
                rect = Rect(size.width - 2f * corner, 0f, size.width, 2f * corner),
                startAngleDegrees = 270f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false,
            )
            lineTo(size.width, size.height - corner)
            arcTo(
                rect = Rect(size.width - 2f * corner, size.height - 2f * corner, size.width, size.height),
                startAngleDegrees = 0f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false,
            )
            lineTo(corner, size.height)
            arcTo(
                rect = Rect(0f, size.height - 2f * corner, 2f * corner, size.height),
                startAngleDegrees = 90f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false,
            )
            lineTo(0f, corner)
            arcTo(
                rect = Rect(0f, 0f, 2f * corner, 2f * corner),
                startAngleDegrees = 180f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false,
            )
            close()
        }
        return Outline.Generic(path)
    }
}
