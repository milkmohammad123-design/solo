package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.ColorTheoryEngine
import com.example.data.GarmentCategory
import com.example.data.GarmentItem
import com.example.ui.theme.Champagne
import com.example.ui.theme.Obsidian
import com.example.ui.theme.parseHexColor
import java.io.File

/**
 * Renders either the user's high-res local garment photo (via Coil) or a bespoke
 * vector-crisp Canvas editorial garment illustration shaded in the garment's exact hex color.
 */
@Composable
fun GarmentVisual(
    category: String,
    colorHex: String,
    name: String,
    localImagePath: String?,
    modifier: Modifier = Modifier
) {
    val fileExists = !localImagePath.isNullOrBlank() && File(localImagePath).exists()
    Box(
        modifier = modifier.clip(RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (fileExists) {
            AsyncImage(
                model = File(localImagePath!!),
                contentDescription = name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            GarmentCanvasIllustration(
                category = category,
                colorHex = colorHex,
                silhouetteHint = name,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp)
            )
        }
    }
}

@Composable
fun GarmentVisual(
    item: GarmentItem,
    modifier: Modifier = Modifier
) {
    GarmentVisual(
        category = item.category,
        colorHex = item.colorHex,
        name = item.name,
        localImagePath = item.localImagePath,
        modifier = modifier
    )
}

@Composable
fun GarmentCanvasIllustration(
    category: String,
    colorHex: String,
    silhouetteHint: String = "",
    modifier: Modifier = Modifier
) {
    val fillColor = parseHexColor(colorHex, Obsidian)
    val isLight = ColorTheoryEngine.isLightColor(colorHex)
    val outlineColor = if (isLight) Color(0xFF8C8478) else Color.White.copy(alpha = 0.28f)
    val detailColor = if (isLight) Color(0xFF2B2825).copy(alpha = 0.42f) else Color(0xFFFAF8F5).copy(alpha = 0.42f)
    val hint = "$silhouetteHint $category".lowercase()
    val cat = GarmentCategory.fromLabel(category)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val unit = minOf(w, h) / 120f
        val ox = (w - 120f * unit) / 2f
        val oy = (h - 120f * unit) / 2f
        fun x(v: Float) = ox + v * unit
        fun y(v: Float) = oy + v * unit
        val stroke = Stroke(width = 1.8f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val detailStroke = Stroke(width = 1.3f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round)

        when {
            hint.contains("trench") || hint.contains("overcoat") || hint.contains("coat") -> {
                val p = Path().apply {
                    moveTo(x(40f), y(14f))
                    lineTo(x(60f), y(28f))
                    lineTo(x(80f), y(14f))
                    lineTo(x(97f), y(25f))
                    lineTo(x(106f), y(72f))
                    lineTo(x(89f), y(74f))
                    lineTo(x(85f), y(45f))
                    lineTo(x(88f), y(108f))
                    lineTo(x(32f), y(108f))
                    lineTo(x(35f), y(45f))
                    lineTo(x(31f), y(74f))
                    lineTo(x(14f), y(72f))
                    lineTo(x(23f), y(25f))
                    close()
                }
                drawPath(p, fillColor)
                drawPath(p, outlineColor, style = stroke)
                // Lapels + belt + double-breasted buttons
                val lapel = Path().apply {
                    moveTo(x(40f), y(14f))
                    lineTo(x(53f), y(46f))
                    lineTo(x(60f), y(28f))
                    lineTo(x(67f), y(46f))
                    lineTo(x(80f), y(14f))
                }
                drawPath(lapel, detailColor, style = detailStroke)
                drawRoundRect(
                    color = fillColor,
                    topLeft = Offset(x(34f), y(58f)),
                    size = Size(52f * unit, 6f * unit),
                    cornerRadius = CornerRadius(2f * unit)
                )
                drawRoundRect(
                    color = detailColor,
                    topLeft = Offset(x(34f), y(58f)),
                    size = Size(52f * unit, 6f * unit),
                    cornerRadius = CornerRadius(2f * unit),
                    style = detailStroke
                )
                drawCircle(detailColor, radius = 2.1f * unit, center = Offset(x(52f), y(74f)))
                drawCircle(detailColor, radius = 2.1f * unit, center = Offset(x(68f), y(74f)))
            }

            cat == GarmentCategory.OUTERWEAR || hint.contains("blazer") || hint.contains("jacket") -> {
                val p = Path().apply {
                    moveTo(x(42f), y(18f))
                    lineTo(x(60f), y(33f))
                    lineTo(x(78f), y(18f))
                    lineTo(x(97f), y(28f))
                    lineTo(x(105f), y(78f))
                    lineTo(x(88f), y(80f))
                    lineTo(x(84f), y(47f))
                    lineTo(x(84f), y(98f))
                    lineTo(x(36f), y(98f))
                    lineTo(x(36f), y(47f))
                    lineTo(x(32f), y(80f))
                    lineTo(x(15f), y(78f))
                    lineTo(x(23f), y(28f))
                    close()
                }
                drawPath(p, fillColor)
                drawPath(p, outlineColor, style = stroke)
                val lapel = Path().apply {
                    moveTo(x(42f), y(18f))
                    lineTo(x(54f), y(52f))
                    lineTo(x(60f), y(33f))
                    lineTo(x(66f), y(52f))
                    lineTo(x(78f), y(18f))
                }
                drawPath(lapel, detailColor, style = detailStroke)
                drawCircle(Champagne, radius = 2.3f * unit, center = Offset(x(60f), y(64f)))
            }

            hint.contains("knit") || hint.contains("cashmere") || hint.contains("turtleneck") || hint.contains("sweater") || hint.contains("polo") -> {
                val p = Path().apply {
                    moveTo(x(46f), y(15f))
                    lineTo(x(74f), y(15f))
                    lineTo(x(74f), y(26f))
                    lineTo(x(95f), y(34f))
                    lineTo(x(105f), y(84f))
                    lineTo(x(88f), y(86f))
                    lineTo(x(82f), y(48f))
                    lineTo(x(82f), y(102f))
                    lineTo(x(38f), y(102f))
                    lineTo(x(38f), y(48f))
                    lineTo(x(32f), y(86f))
                    lineTo(x(15f), y(84f))
                    lineTo(x(25f), y(34f))
                    lineTo(x(46f), y(26f))
                    close()
                }
                drawPath(p, fillColor)
                drawPath(p, outlineColor, style = stroke)
                drawLine(detailColor, Offset(x(46f), y(22f)), Offset(x(74f), y(22f)), strokeWidth = 1.4f * unit)
                drawLine(detailColor, Offset(x(38f), y(94f)), Offset(x(82f), y(94f)), strokeWidth = 1.4f * unit)
            }

            cat == GarmentCategory.TOPS && (hint.contains("shirt") || hint.contains("poplin") || hint.contains("silk") || hint.contains("overshirt")) -> {
                val p = Path().apply {
                    moveTo(x(45f), y(18f))
                    lineTo(x(60f), y(28f))
                    lineTo(x(75f), y(18f))
                    lineTo(x(96f), y(32f))
                    lineTo(x(104f), y(80f))
                    lineTo(x(88f), y(82f))
                    lineTo(x(82f), y(48f))
                    lineTo(x(82f), y(102f))
                    lineTo(x(38f), y(102f))
                    lineTo(x(38f), y(48f))
                    lineTo(x(32f), y(82f))
                    lineTo(x(16f), y(80f))
                    lineTo(x(24f), y(32f))
                    close()
                }
                drawPath(p, fillColor)
                drawPath(p, outlineColor, style = stroke)
                drawLine(detailColor, Offset(x(60f), y(28f)), Offset(x(60f), y(100f)), strokeWidth = 1.2f * unit)
                listOf(42f, 56f, 70f, 84f).forEach { by ->
                    drawCircle(detailColor, radius = 1.6f * unit, center = Offset(x(60f), y(by)))
                }
            }

            cat == GarmentCategory.TOPS -> {
                val p = Path().apply {
                    moveTo(x(44f), y(20f))
                    quadraticTo(x(60f), y(33f), x(76f), y(20f))
                    lineTo(x(100f), y(35f))
                    lineTo(x(91f), y(56f))
                    lineTo(x(80f), y(49f))
                    lineTo(x(80f), y(102f))
                    lineTo(x(40f), y(102f))
                    lineTo(x(40f), y(49f))
                    lineTo(x(29f), y(56f))
                    lineTo(x(20f), y(35f))
                    close()
                }
                drawPath(p, fillColor)
                drawPath(p, outlineColor, style = stroke)
            }

            cat == GarmentCategory.BOTTOMS -> {
                val p = Path().apply {
                    moveTo(x(35f), y(16f))
                    lineTo(x(85f), y(16f))
                    lineTo(x(90f), y(106f))
                    lineTo(x(66f), y(106f))
                    lineTo(x(60f), y(49f))
                    lineTo(x(54f), y(106f))
                    lineTo(x(30f), y(106f))
                    close()
                }
                drawPath(p, fillColor)
                drawPath(p, outlineColor, style = stroke)
                drawLine(detailColor, Offset(x(35f), y(25f)), Offset(x(85f), y(25f)), strokeWidth = 1.3f * unit)
                drawLine(detailColor, Offset(x(60f), y(25f)), Offset(x(60f), y(44f)), strokeWidth = 1.3f * unit)
                // Pleat lines
                drawLine(detailColor, Offset(x(46f), y(28f)), Offset(x(44f), y(60f)), strokeWidth = 1f * unit)
                drawLine(detailColor, Offset(x(74f), y(28f)), Offset(x(76f), y(60f)), strokeWidth = 1f * unit)
            }

            cat == GarmentCategory.FOOTWEAR && (hint.contains("boot") || hint.contains("chelsea")) -> {
                val p = Path().apply {
                    moveTo(x(36f), y(24f))
                    lineTo(x(68f), y(24f))
                    lineTo(x(68f), y(66f))
                    lineTo(x(96f), y(74f))
                    quadraticTo(x(104f), y(77f), x(101f), y(88f))
                    lineTo(x(32f), y(88f))
                    close()
                }
                drawPath(p, fillColor)
                drawPath(p, outlineColor, style = stroke)
                // Elastic gusset + lug sole
                drawRoundRect(detailColor, Offset(x(44f), y(34f)), Size(16f * unit, 28f * unit), CornerRadius(6f * unit), style = detailStroke)
                drawRoundRect(Obsidian, Offset(x(32f), y(88f)), Size(70f * unit, 8f * unit), CornerRadius(3f * unit))
            }

            cat == GarmentCategory.FOOTWEAR -> {
                val p = Path().apply {
                    moveTo(x(20f), y(62f))
                    lineTo(x(44f), y(48f))
                    lineTo(x(64f), y(62f))
                    lineTo(x(95f), y(68f))
                    quadraticTo(x(104f), y(71f), x(101f), y(83f))
                    lineTo(x(18f), y(83f))
                    close()
                }
                drawPath(p, fillColor)
                drawPath(p, outlineColor, style = stroke)
                drawRoundRect(
                    color = if (isLight) Color(0xFFD6D0C4) else Color(0xFF28282C),
                    topLeft = Offset(x(18f), y(83f)),
                    size = Size(84f * unit, 8f * unit),
                    cornerRadius = CornerRadius(3.5f * unit)
                )
            }

            cat == GarmentCategory.BAGS -> {
                val handle = Path().apply {
                    moveTo(x(44f), y(42f))
                    lineTo(x(44f), y(30f))
                    cubicTo(x(44f), y(18f), x(76f), y(18f), x(76f), y(30f))
                    lineTo(x(76f), y(42f))
                }
                drawPath(handle, fillColor, style = Stroke(width = 4.2f * unit, cap = StrokeCap.Round))
                val body = Path().apply {
                    moveTo(x(24f), y(42f))
                    lineTo(x(96f), y(42f))
                    lineTo(x(89f), y(98f))
                    lineTo(x(31f), y(98f))
                    close()
                }
                drawPath(body, fillColor)
                drawPath(body, outlineColor, style = stroke)
                drawRoundRect(Champagne, Offset(x(53f), y(53f)), Size(14f * unit, 8f * unit), CornerRadius(2f * unit))
            }

            else -> {
                // Sculpted belt / accessory
                drawRoundRect(
                    color = fillColor,
                    topLeft = Offset(x(14f), y(48f)),
                    size = Size(92f * unit, 22f * unit),
                    cornerRadius = CornerRadius(10f * unit)
                )
                drawRoundRect(
                    color = outlineColor,
                    topLeft = Offset(x(14f), y(48f)),
                    size = Size(92f * unit, 22f * unit),
                    cornerRadius = CornerRadius(10f * unit),
                    style = stroke
                )
                // Gold editorial buckle
                drawRoundRect(
                    color = Champagne,
                    topLeft = Offset(x(48f), y(44f)),
                    size = Size(24f * unit, 30f * unit),
                    cornerRadius = CornerRadius(6f * unit),
                    style = Stroke(width = 4f * unit)
                )
            }
        }
    }
}
