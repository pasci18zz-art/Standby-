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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.translate
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

private val glyphPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
    typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, 700, false)
}

/**
 * Umriss einer Ziffer als Pfad. Systemschriften bestehen oft aus überlappenden Teilformen –
 * beim reinen Nachzeichnen entstehen dann innere Linien (z. B. in der 3). Die Vereinigung
 * der Formen ergibt einen sauberen Außenumriss.
 */
private fun glyphPath(ch: Char): android.graphics.Path {
    val raw = android.graphics.Path()
    glyphPaint.getTextPath(ch.toString(), 0, 1, 0f, 0f, raw)
    val clean = android.graphics.Path()
    return if (clean.op(raw, android.graphics.Path(), android.graphics.Path.Op.UNION)) clean else raw
}

@Composable
private fun GlyphClock(time: LocalDateTime, neon: Boolean, modifier: Modifier) {
    val pal = currentPalette()
    val bg = currentBackdrop().color
    val digits = hourString(time) + minuteString(time)
    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        // Referenzmaße bei Schriftgröße 100, daraus die passende Größe.
        glyphPaint.textSize = 100f
        val refW = glyphPaint.measureText("0")
        val rb = android.graphics.Rect()
        glyphPaint.getTextBounds("0", 0, 1, rb)
        val refCap = rb.height().toFloat()
        val s = min(h * 0.62f / refCap, w * 0.92f / (refW * 4.62f))
        val fp = 100f * s
        glyphPaint.textSize = fp
        val cell = refW * s
        val cap = refCap * s
        val colonW = cell * 0.62f
        val startX = (w - (cell * 4f + colonW)) / 2f
        val baseline = (h + cap) / 2f

        val paths = digits.map { ch ->
            val p = glyphPath(ch)
            val adv = glyphPaint.measureText(ch.toString())
            Triple(p.asComposePath(), adv, ch)
        }
        fun cellX(i: Int) = startX + i * cell + if (i >= 2) colonW else 0f

        val layers: List<StrokeLayer> = if (neon) {
            listOf(
                StrokeLayer(0.14f, Color.White, alpha = 0.04f),
                StrokeLayer(0.095f, Color.White, alpha = 0.07f),
                StrokeLayer(0.06f, Color.White, alpha = 0.13f),
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

        val playful = Prefs.playfulLines && !neon
        val spec = pal.spectrum

        fun drawDigits(l: StrokeLayer, fill: Boolean) {
            paths.forEachIndexed { i, (path, adv, _) ->
                val x = cellX(i) + (cell - adv) / 2f
                // Verspielt: farbige Linien leicht versetzt und mit Lücken an unterschiedlichen Stellen.
                val colored = !fill && l.color == null
                val wob = if (playful && colored) fp * 0.012f * (l.brushIdx - 1) else 0f
                translate(x + wob, baseline - wob * 0.6f) {
                    val effect = if (playful && colored) {
                        val on = fp * (1.05f + 0.42f * l.brushIdx + 0.13f * i)
                        val off = fp * (0.07f + 0.035f * l.brushIdx)
                        PathEffect.dashPathEffect(floatArrayOf(on, off), fp * (0.61f * i + 0.37f * l.brushIdx))
                    } else null
                    val st = if (fill) Fill else Stroke(width = fp * l.frac, join = StrokeJoin.Round, cap = StrokeCap.Round, pathEffect = effect)
                    when {
                        fill -> drawPath(path, bg, style = st)
                        l.color != null -> drawPath(path, l.color.copy(alpha = l.alpha), style = st)
                        else -> {
                            // Verlauf über das ganze Farbspektrum, je Linie und Ziffer verschoben.
                            val shift = (l.brushIdx + i) % spec.size
                            val cols = spec.drop(shift) + spec.take(shift)
                            val brush = Brush.linearGradient(cols, start = Offset(0f, -cap), end = Offset(cell, 0f))
                            drawPath(path, brush, style = st)
                        }
                    }
                }
            }
        }

        for (l in layers) drawDigits(l, fill = false)
        if (neon) {
            drawDigits(StrokeLayer(0f, null), fill = true)
            // Doppelte Leuchtlinie: heller Kern + feiner Innenschimmer
            drawDigits(StrokeLayer(0.034f, Color.White, alpha = 0.22f), fill = false)
            drawDigits(StrokeLayer(0.016f, Color.White, alpha = 0.97f), fill = false)
        }

        // Doppelpunkt
        val cx = startX + cell * 2f + colonW / 2f
        val cy = baseline - cap / 2f
        val gap = cap * 0.22f
        val r = fp * 0.075f
        for (dy in listOf(-gap, gap)) {
            val c = Offset(cx, cy + dy)
            if (neon) {
                drawCircle(Color.White.copy(alpha = 0.07f), r * 1.5f, c, style = Stroke(fp * 0.07f))
                drawCircle(bg, r, c)
                drawCircle(Color.White.copy(alpha = 0.22f), r, c, style = Stroke(fp * 0.034f))
                drawCircle(Color.White.copy(alpha = 0.97f), r, c, style = Stroke(fp * 0.016f))
            } else {
                val e1 = if (playful) PathEffect.dashPathEffect(floatArrayOf(r * 5.4f, r * 0.55f), r * (if (dy < 0) 0.8f else 3.1f)) else null
                val e2 = if (playful) PathEffect.dashPathEffect(floatArrayOf(r * 3.6f, r * 0.45f), r * (if (dy < 0) 2.2f else 0.4f)) else null
                drawCircle(pal.colon, r * 1.15f, c, style = Stroke(fp * 0.026f, cap = StrokeCap.Round, pathEffect = e1))
                drawCircle(lerp(pal.colon, pal.c3, 0.35f), r * 0.78f, c, style = Stroke(fp * 0.016f, cap = StrokeCap.Round, pathEffect = e2))
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
