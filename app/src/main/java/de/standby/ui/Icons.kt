package de.standby.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class Ic { PLAY, PAUSE, PREV, NEXT, GEAR, SUN, MOON, BOLT, NOTE }

/** Selbst gezeichnete Symbole (kein zusätzliches Icon-Paket nötig). */
@Composable
fun GIcon(ic: Ic, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val c = center
        when (ic) {
            Ic.PLAY -> drawPath(Path().apply {
                moveTo(w * .22f, h * .1f); lineTo(w * .92f, h * .5f); lineTo(w * .22f, h * .9f); close()
            }, tint)
            Ic.PAUSE -> {
                drawRoundRect(tint, Offset(w * .16f, h * .1f), Size(w * .26f, h * .8f), CornerRadius(w * .06f))
                drawRoundRect(tint, Offset(w * .58f, h * .1f), Size(w * .26f, h * .8f), CornerRadius(w * .06f))
            }
            Ic.NEXT -> {
                drawPath(Path().apply { moveTo(w * .04f, h * .2f); lineTo(w * .5f, h * .5f); lineTo(w * .04f, h * .8f); close() }, tint)
                drawPath(Path().apply { moveTo(w * .5f, h * .2f); lineTo(w * .96f, h * .5f); lineTo(w * .5f, h * .8f); close() }, tint)
            }
            Ic.PREV -> {
                drawPath(Path().apply { moveTo(w * .96f, h * .2f); lineTo(w * .5f, h * .5f); lineTo(w * .96f, h * .8f); close() }, tint)
                drawPath(Path().apply { moveTo(w * .5f, h * .2f); lineTo(w * .04f, h * .5f); lineTo(w * .5f, h * .8f); close() }, tint)
            }
            Ic.GEAR -> {
                drawCircle(tint, radius = w * .2f, center = c, style = Stroke(w * .11f))
                for (i in 0 until 8) rotate(i * 45f) {
                    drawLine(tint, Offset(c.x, c.y - w * .3f), Offset(c.x, c.y - w * .44f), strokeWidth = w * .13f, cap = StrokeCap.Round)
                }
            }
            Ic.SUN -> {
                drawCircle(tint, radius = w * .2f, center = c)
                for (i in 0 until 8) rotate(i * 45f) {
                    drawLine(tint, Offset(c.x, c.y - w * .32f), Offset(c.x, c.y - w * .44f), strokeWidth = w * .08f, cap = StrokeCap.Round)
                }
            }
            Ic.MOON -> {
                val p = Path().apply {
                    moveTo(w * .65f, h * .1f)
                    cubicTo(w * .2f, h * .15f, w * .1f, h * .7f, w * .45f, h * .88f)
                    cubicTo(w * .7f, h * .98f, w * .95f, h * .8f, w * .92f, h * .62f)
                    cubicTo(w * .6f, h * .75f, w * .45f, h * .35f, w * .65f, h * .1f)
                    close()
                }
                drawPath(p, tint)
            }
            Ic.BOLT -> drawPath(Path().apply {
                moveTo(w * .58f, h * .02f); lineTo(w * .18f, h * .56f); lineTo(w * .46f, h * .56f)
                lineTo(w * .36f, h * .98f); lineTo(w * .82f, h * .38f); lineTo(w * .54f, h * .38f); close()
            }, tint)
            Ic.NOTE -> {
                drawCircle(tint, radius = w * .17f, center = Offset(w * .35f, h * .78f))
                drawLine(tint, Offset(w * .5f, h * .76f), Offset(w * .5f, h * .15f), strokeWidth = w * .09f)
                drawLine(tint, Offset(w * .5f, h * .15f), Offset(w * .82f, h * .3f), strokeWidth = w * .09f, cap = StrokeCap.Round)
            }
        }
    }
}

/**
 * Antippbares Symbol mit großzügiger Trefferfläche und spürbarer Rückmeldung:
 * beim Drücken federt es zusammen und ein heller Kreis leuchtet kurz auf.
 */
@Composable
fun IconBtn(ic: Ic, tint: Color, size: Dp, onClick: () -> Unit, modifier: Modifier = Modifier, pad: Dp = 14.dp) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.78f else 1f, spring(dampingRatio = 0.45f, stiffness = 600f), label = "scale")
    val glow = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    Box(
        modifier
            .clickable(interactionSource = src, indication = null) {
                scope.launch {
                    glow.snapTo(0.28f)
                    glow.animateTo(0f, tween(450))
                }
                onClick()
            }
            .padding(pad)
            .drawBehind {
                val g = glow.value
                if (g > 0f) drawCircle(tint.copy(alpha = g), radius = this.size.minDimension * (1.05f - g))
            },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = ic,
            transitionSpec = { (scaleIn(initialScale = 0.5f) + fadeIn()) togetherWith (scaleOut(targetScale = 0.5f) + fadeOut()) },
            label = "icon",
        ) { target ->
            GIcon(target, tint, Modifier.size(size).graphicsLayer { scaleX = scale; scaleY = scale })
        }
    }
}
