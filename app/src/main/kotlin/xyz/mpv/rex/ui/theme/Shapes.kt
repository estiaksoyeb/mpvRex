package xyz.mpv.rex.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Shapes
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

val compactControlShape = RoundedCornerShape(12.dp)
val pillShape = RoundedCornerShape(percent = 50)

/**
 * Material 3 Expressive Flower / Clover shape for FABs and badges.
 */
class FlowerShape(
    val petals: Int = 4,
    val depth: Float = 0.12f,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val path = Path()
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = minOf(cx, cy)
        val steps = 120
        for (i in 0 until steps) {
            val angle = (i.toFloat() / steps) * (2f * Math.PI.toFloat())
            val r = radius * (1f - depth + depth * cos(petals * angle))
            val x = cx + r * cos(angle)
            val y = cy + r * sin(angle)
            if (i == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
        path.close()
        return Outline.Generic(path)
    }
}

val flowerFabShape = FlowerShape(petals = 4, depth = 0.12f)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val AppShapes =
    Shapes(
        extraSmall = RoundedCornerShape(6.dp),
        small = RoundedCornerShape(10.dp),
        medium = RoundedCornerShape(14.dp),
        large = RoundedCornerShape(18.dp),
        extraLarge = RoundedCornerShape(24.dp),
        largeIncreased = RoundedCornerShape(22.dp),
        extraLargeIncreased = RoundedCornerShape(28.dp),
        extraExtraLarge = RoundedCornerShape(36.dp),
    )
