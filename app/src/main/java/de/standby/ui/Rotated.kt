package de.standby.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints

/**
 * Zeigt den Inhalt im Querformat-Layout, auch wenn das Handy hochkant steht: Der Inhalt wird um 90° gedreht
 * (mode "cw" = im Uhrzeigersinn, "ccw" = dagegen). Berührungen werden mitgedreht, Wischen geht also
 * entlang der langen Handykante.
 */
@Composable
fun Rotated90(mode: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    if (mode == "off") {
        Box(modifier.fillMaxSize()) { content() }
        return
    }
    Layout(content = content, modifier = modifier.fillMaxSize()) { measurables, constraints ->
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val portrait = h > w
        val inner = if (portrait) Constraints.fixed(h, w) else Constraints.fixed(w, h)
        val angle = if (!portrait) 0f else if (mode == "cw") 90f else -90f
        val placeables = measurables.map { it.measure(inner) }
        layout(w, h) {
            placeables.forEach { p ->
                p.placeWithLayer((w - p.width) / 2, (h - p.height) / 2) { rotationZ = angle }
            }
        }
    }
}
