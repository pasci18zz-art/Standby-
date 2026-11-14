package de.standby.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.standby.Prefs
import de.standby.currentBackdrop
import de.standby.currentPalette
import java.time.LocalDateTime
import kotlin.math.min

val clockStyles = listOf(
    "lines" to "Linien (bunt)",
    "neon" to "Neon-Kontur",
    "flip" to "Klappzahlen",
    "minimal" to "Minimal",
)

private fun hourString(t: LocalDateTime): String {
    val h = if (Prefs.use24h) t.hour else (t.hour % 12).let { if (it == 0) 12 else it }
    return h.toString().padStart(2, '0')
}

private fun minuteString(t: LocalDateTime) = t.minute.toString().padStart(2, '0')

/** Die gewählte Uhr, füllt den verfügbaren Platz. */
@Composable
fun ClockFace(style: String, time: LocalDateTime, modifier: Modifier = Modifier) {
    when (style) {
        "flip" -> FlipClock(time, modifier)
        "neon" -> GlyphClock(time, neon = true, modifier = modifier)
        "minimal" -> MinimalClock(time, modifier)
        else -> GlyphClock(time, neon = false, modifier = modifier)
    }
}

// ---------------------------------------------------------------------------------------------
// Minimal
// ---------------------------------------------------------------------------------------------

@Composable
private fun MinimalClock(time: LocalDateTime, modifier: Modifier) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val fs = with(LocalDensity.current) { min(maxHeight.toPx() * 0.62f, maxWidth.toPx() / 3.1f).toSp() }
        val sec = if (Prefs.showSeconds) ":" + time.second.toString().padStart(2, '0') else ""
        Text(
            hourString(time) + ":" + minuteString(time) + sec,
            style = TextStyle(
                fontSize = if (Prefs.showSeconds) fs * 0.78f else fs,
                fontWeight = FontWeight.Light,
                color = Color.White,
                textAlign = TextAlign.Center,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
            ),
            maxLines = 1,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Linien- und Neon-Uhr (gezeichnete Konturen)
// ---------------------------------------------------------------------------------------------

private class StrokeLayer(val frac: Float, val color: Color?, val brushIdx: Int = 0, val alpha: Float = 1f)

@Composable
private fun GlyphClock(time: LocalDateTime, neon: Boolean, modifier: Modifier) {
    val tm = rememberTextMeasurer()
    val pal = currentPalette()
    val bg = currentBackdrop().color
    val digits = hourString(time) + minuteString(time)
    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val weight = FontWeight.Bold
        // Referenzmessung, daraus die passende Schriftgröße für Breite und Höhe.
        val ref = tm.measure("0", TextStyle(fontSize = 100.sp, fontWeight = weight))
        val refW = ref.size.width.toFloat()
        val refH = ref.size.height.toFloat()
        val totalRefW = refW * 4f + refW * 0.62f
        val s = min(h * 0.82f / refH, w * 0.94f / totalRefW)
        val fontSp = (100f * s).sp
        val fp = fontSp.toPx()
        val style = TextStyle(fontSize = fontSp, fontWeight = weight)
        val cell = tm.measure("0", style).size.width.toFloat()
        val colonW = cell * 0.62f
        val total = cell * 4f + colonW
        val startX = (w - total) / 2f
        val sample = tm.measure("0", style)
        val topY = (h - sample.size.height) / 2f

        fun cellX(i: Int) = startX + i * cell + if (i >= 2) colonW else 0f

        val layers: List<StrokeLayer> = if (neon) {
            listOf(
                StrokeLayer(0.16f, Color.White, alpha = 0.05f),
                StrokeLayer(0.11f, Color.White, alpha = 0.09f),
                StrokeLayer(0.07f, Color.White, alpha = 0.16f),
            )
        } else {
            listOf(
                StrokeLayer(0.150f, null, 0),
                StrokeLayer(0.120f, bg),
                StrokeLayer(0.100f, null, 1),
                StrokeLayer(0.072f, bg),
                StrokeLayer(0.055f, null, 2),
                StrokeLayer(0.028f, bg),
            )
        }

        fun drawDigits(l: StrokeLayer, fill: Boolean, fillColor: Color = bg) {
            for (i in 0 until 4) {
                val lay = tm.measure(digits[i].toString(), style)
                val x = cellX(i) + (cell - lay.size.width) / 2f
                val tl = Offset(x, topY)
                val drawStyle = if (fill) Fill else Stroke(width = fp * l.frac, join = StrokeJoin.Round)
                if (fill) {
                    drawText(lay, color = fillColor, topLeft = tl, drawStyle = drawStyle)
                } else if (l.color != null) {
                    drawText(lay, color = l.color.copy(alpha = l.alpha), topLeft = tl, drawStyle = drawStyle)
                } else {
                    val c = when (l.brushIdx) { 0 -> pal.c1; 1 -> pal.c2; else -> pal.c3 }
                    val c2 = when (l.brushIdx) { 0 -> pal.c2; 1 -> pal.c3; else -> pal.c1 }
                    val brush = Brush.linearGradient(listOf(c, c2), start = Offset(x, topY), end = Offset(x + cell, topY + sample.size.height))
                    drawText(lay, brush = brush, topLeft = tl, drawStyle = drawStyle)
                }
            }
        }

        for (l in layers) drawDigits(l, fill = false)
        if (neon) {
            drawDigits(StrokeLayer(0f, null), fill = true)
            drawDigits(StrokeLayer(0.022f, Color.White, alpha = 0.95f), fill = false)
        }

        // Doppelpunkt
        val cx = startX + cell * 2f + colonW / 2f
        val cy = h / 2f
        val gap = fp * 0.24f
        val r = fp * 0.085f
        for (dy in listOf(-gap, gap)) {
            val c = Offset(cx, cy + dy)
            if (neon) {
                drawCircle(Color.White.copy(alpha = 0.08f), r * 1.4f, c, style = Stroke(fp * 0.08f))
                drawCircle(bg, r, c)
                drawCircle(Color.White.copy(alpha = 0.95f), r, c, style = Stroke(fp * 0.022f))
            } else {
                drawCircle(pal.colon, r * 1.15f, c, style = Stroke(fp * 0.026f))
                drawCircle(pal.colon.copy(alpha = 0.75f), r * 0.78f, c, style = Stroke(fp * 0.016f))
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Klappzahlen-Uhr
// ---------------------------------------------------------------------------------------------

@Composable
private fun FlipClock(time: LocalDateTime, modifier: Modifier) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val gap = 20.dp
        val byWidth = (maxWidth - gap) / 2
        val cardW = if (byWidth / 1.7f > maxHeight * 0.85f) maxHeight * 0.85f * 1.7f else byWidth
        Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
            FlipCard(hourString(time), Modifier.width(cardW).height(cardW / 1.7f))
            FlipCard(minuteString(time), Modifier.width(cardW).height(cardW / 1.7f))
        }
    }
}

/** Eine Klappkarte: Bei neuem Wert fällt die obere Hälfte nach unten und gibt die neue Zahl frei. */
@Composable
fun FlipCard(value: String, modifier: Modifier = Modifier) {
    var shown by remember { mutableStateOf(value) }
    var old by remember { mutableStateOf(value) }
    val p = remember { Animatable(1f) }
    LaunchedEffect(value) {
        if (value != shown) {
            old = shown
            shown = value
            p.snapTo(0f)
            p.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
        }
    }
    val f = p.value
    val brush = Brush.verticalGradient(listOf(Color(0xFF3A3D47), Color(0xFF2A2C34)))
    val shape = RoundedCornerShape(26.dp)
    BoxWithConstraints(
        modifier
            .clip(shape)
            .border(1.dp, Color.White.copy(alpha = 0.08f), shape),
    ) {
        val fontSp = with(LocalDensity.current) { (maxHeight.toPx() * 0.72f).toSp() }
        val style = TextStyle(
            fontWeight = FontWeight.ExtraBold,
            fontSize = fontSp,
            lineHeight = fontSp,
            color = Color(0xFFF2F3F7),
            platformStyle = PlatformTextStyle(includeFontPadding = false),
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
        )
        Box(Modifier.fillMaxSize()) {
            FlipHalf(shown, top = true, brush = brush, style = style)
            FlipHalf(if (f < 1f) old else shown, top = false, brush = brush, style = style)
            if (f < 0.5f) {
                FlipHalf(old, top = true, angle = -180f * f, brush = brush, style = style)
            } else if (f < 1f) {
                FlipHalf(shown, top = false, angle = 180f * (1f - f), brush = brush, style = style)
            }
            Box(
                Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(Color.Black.copy(alpha = 0.55f)),
            )
        }
    }
}

@Composable
private fun BoxScope.FlipHalf(text: String, top: Boolean, brush: Brush, style: TextStyle, angle: Float = 0f) {
    val shade = (kotlin.math.abs(angle) / 90f).coerceIn(0f, 1f) * 0.3f
    Box(
        Modifier
            .matchParentSize()
            .graphicsLayer {
                rotationX = angle
                cameraDistance = 16f * density
                transformOrigin = TransformOrigin(0.5f, 0.5f)
            }
            .drawWithContent {
                val half = size.height / 2
                clipRect(top = if (top) 0f else half, bottom = if (top) half else size.height) {
                    this@drawWithContent.drawContent()
                    if (shade > 0f) drawRect(Color.Black.copy(alpha = shade))
                }
            }
            .background(brush),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = style, maxLines = 1)
    }
}
