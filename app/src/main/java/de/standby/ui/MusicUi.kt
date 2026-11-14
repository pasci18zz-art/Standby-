package de.standby.ui

import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.palette.graphics.Palette
import de.standby.media.MediaInfo
import de.standby.media.MediaRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val FallbackTint = Color(0xFF2B2325)

/** Dominante dunkle Farbe des Covers – dient als Hintergrund-Verlauf. */
@Composable
fun rememberCoverColor(bmp: Bitmap?): State<Color> = produceState(FallbackTint, bmp) {
    value = withContext(Dispatchers.Default) {
        if (bmp == null) FallbackTint
        else runCatching {
            val p = Palette.from(bmp).maximumColorCount(16).generate()
            val argb = p.getDarkVibrantColor(p.getDarkMutedColor(p.getMutedColor(p.getDominantColor(0xFF2B2325.toInt()))))
            Color(argb)
        }.getOrDefault(FallbackTint)
    }
}

/** Position, die jede halbe Sekunde fortgeschrieben wird. */
@Composable
fun rememberPosition(info: MediaInfo): State<Long> = produceState(info.positionNow(), info) {
    while (true) {
        value = info.positionNow()
        delay(500)
    }
}

fun fmtTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}

@Composable
fun Cover(bmp: Bitmap?, modifier: Modifier = Modifier, radius: Dp = 28.dp, elevation: Dp = 24.dp) {
    val shape = RoundedCornerShape(radius)
    Box(
        modifier
            .shadow(elevation, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(Color(0xFF1C1C22)),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(bmp, animationSpec = tween(600), label = "cover") { b ->
            if (b != null) {
                val img = remember(b) { b.asImageBitmap() }
                Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                GIcon(Ic.NOTE, Color.White.copy(alpha = 0.35f), Modifier.size(64.dp))
            }
        }
    }
}

/** Dünner Fortschrittsbalken, per Tippen oder Ziehen zu spulen. */
@Composable
fun SeekBar(progress: Float, onSeek: (Float) -> Unit, modifier: Modifier = Modifier, color: Color = Color.White) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val shown = drag ?: progress
    Box(
        modifier
            .height(30.dp)
            .pointerInput(Unit) {
                detectTapGestures { off -> onSeek((off.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { off -> drag = (off.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = { drag?.let(onSeek); drag = null },
                    onDragCancel = { drag = null },
                ) { change, _ -> drag = (change.position.x / size.width).coerceIn(0f, 1f) }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxWidth().height(10.dp)) {
            val r = CornerRadius(size.height / 2)
            drawRoundRect(color.copy(alpha = 0.22f), Offset.Zero, size, r)
            drawRoundRect(color, Offset.Zero, Size(size.width * shown.coerceIn(0f, 1f), size.height), r)
        }
    }
}

@Composable
private fun Controls(info: MediaInfo, iconSize: Dp, gap: Dp, tint: Color = Color.White) {
    Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
        IconBtn(Ic.PREV, tint, iconSize, { MediaRepo.previous() })
        IconBtn(if (info.playing) Ic.PAUSE else Ic.PLAY, tint, iconSize * 1.1f, { MediaRepo.playPause() })
        IconBtn(Ic.NEXT, tint, iconSize, { MediaRepo.next() })
    }
}

@Composable
private fun TimeRow(info: MediaInfo, pos: Long, modifier: Modifier = Modifier, size: Int = 18) {
    Row(modifier, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(fmtTime(pos), color = Color.White.copy(alpha = 0.75f), fontSize = size.sp)
        if (info.duration > 0) Text("-" + fmtTime(info.duration - pos), color = Color.White.copy(alpha = 0.75f), fontSize = size.sp)
    }
}

/** Vollbild-Player: großes Cover links, Titel und Steuerung rechts. */
@Composable
fun MusicFull(info: MediaInfo, modifier: Modifier = Modifier) {
    val tint by rememberCoverColor(info.cover)
    val animTint by animateColorAsState(tint, tween(900), label = "tint")
    val pos by rememberPosition(info)
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(lerp(animTint, Color.Black, 0.25f), Color(0xFF0B0B0E)))),
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 32.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(info.cover, Modifier.fillMaxHeight().aspectRatio(1f))
            Column(
                Modifier.weight(1f).padding(start = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    info.title, color = Color(0xFFE9E7F5), fontSize = 38.sp, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    info.artist, color = Color.White.copy(alpha = 0.6f), fontSize = 22.sp,
                    textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Spacer(Modifier.height(18.dp))
                Controls(info, iconSize = 52.dp, gap = 26.dp)
                Spacer(Modifier.height(10.dp))
                SeekBar(
                    progress = if (info.duration > 0) pos.toFloat() / info.duration else 0f,
                    onSeek = { if (info.duration > 0) MediaRepo.seekTo((it * info.duration).toLong()) },
                    modifier = Modifier.fillMaxWidth(),
                )
                TimeRow(info, pos, Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                Text(info.appName, color = Color.White.copy(alpha = 0.45f), fontSize = 14.sp, letterSpacing = 2.sp)
            }
        }
    }
}

/** Kompakte Karte (Split-Seite): unscharfes Cover als Hintergrund. */
@Composable
fun MusicCard(info: MediaInfo, modifier: Modifier = Modifier) {
    val pos by rememberPosition(info)
    val shape = RoundedCornerShape(36.dp)
    Box(modifier.clip(shape).background(Color(0xFF15151A))) {
        Crossfade(info.cover, animationSpec = tween(600), label = "bg") { b ->
            if (b != null) {
                val img = remember(b) { b.asImageBitmap() }
                Image(img, null, Modifier.fillMaxSize().blur(28.dp, BlurredEdgeTreatment.Rectangle), contentScale = ContentScale.Crop)
            }
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))
        Column(
            Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    info.title, color = Color(0xFFEDEBF7), fontSize = 28.sp, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Text(info.artist, color = Color.White.copy(alpha = 0.65f), fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Controls(info, iconSize = 44.dp, gap = 18.dp)
            Column {
                SeekBar(
                    progress = if (info.duration > 0) pos.toFloat() / info.duration else 0f,
                    onSeek = { if (info.duration > 0) MediaRepo.seekTo((it * info.duration).toLong()) },
                    modifier = Modifier.fillMaxWidth(),
                )
                TimeRow(info, pos, Modifier.fillMaxWidth(), size = 15)
            }
        }
    }
}

/**
 * Schlanke Leiste unter der Uhr: Titel · Interpret, darunter ein kurzer Fortschrittsbalken
 * (so breit wie der Text), rechts Zurück / Pause / Weiter. Antippen öffnet das Musik-Fenster.
 */
@Composable
fun MiniPlayer(info: MediaInfo, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val pos by rememberPosition(info)
    val shape = RoundedCornerShape(22.dp)
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(dampingRatio = 0.5f), label = "bar")
    Row(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(Color.White.copy(alpha = if (pressed) 0.13f else 0.07f))
            .clickable(interactionSource = src, indication = null, onClick = onOpen)
            .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.widthIn(max = 300.dp).width(IntrinsicSize.Max)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    info.title, color = Color.White.copy(alpha = 0.92f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
                if (info.artist.isNotBlank()) {
                    Text(
                        "  ·  " + info.artist, color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(5.dp))
            val prog = if (info.duration > 0) (pos.toFloat() / info.duration).coerceIn(0f, 1f) else 0f
            val animProg by animateFloatAsState(prog, tween(500), label = "prog")
            Canvas(Modifier.fillMaxWidth().height(3.dp)) {
                val r = CornerRadius(size.height / 2)
                drawRoundRect(Color.White.copy(alpha = 0.18f), Offset.Zero, size, r)
                drawRoundRect(Color.White.copy(alpha = 0.85f), Offset.Zero, Size(size.width * animProg, size.height), r)
            }
        }
        Spacer(Modifier.width(10.dp))
        IconBtn(Ic.PREV, Color.White.copy(alpha = 0.85f), 18.dp, { MediaRepo.previous() }, pad = 9.dp)
        IconBtn(if (info.playing) Ic.PAUSE else Ic.PLAY, Color.White, 19.dp, { MediaRepo.playPause() }, pad = 9.dp)
        IconBtn(Ic.NEXT, Color.White.copy(alpha = 0.85f), 18.dp, { MediaRepo.next() }, pad = 9.dp)
    }
}

/** Fenster über der Uhr: Cover, Titel, Steuerung links – Warteschlange rechts. */
@Composable
fun MusicPopup(info: MediaInfo, modifier: Modifier = Modifier) {
    val pos by rememberPosition(info)
    val tint by rememberCoverColor(info.cover)
    val animTint by animateColorAsState(tint, tween(900), label = "ptint")
    val shape = RoundedCornerShape(34.dp)
    Row(
        modifier
            .widthIn(max = 820.dp)
            .fillMaxWidth()
            .fillMaxHeight(0.9f)
            .shadow(40.dp, shape)
            .clip(shape)
            .background(Brush.linearGradient(listOf(lerp(animTint, Color.Black, 0.2f), Color(0xFF121216))))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .padding(20.dp),
    ) {
        // Links: Cover, Name, Steuerung
        Column(Modifier.fillMaxHeight().width(240.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Cover(info.cover, Modifier.size(136.dp), radius = 20.dp, elevation = 16.dp)
            Column(Modifier.padding(top = 10.dp)) {
                Text(info.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(info.artist, color = Color.White.copy(alpha = 0.6f), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                SeekBar(
                    progress = if (info.duration > 0) pos.toFloat() / info.duration else 0f,
                    onSeek = { if (info.duration > 0) MediaRepo.seekTo((it * info.duration).toLong()) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    IconBtn(Ic.PREV, Color.White, 28.dp, { MediaRepo.previous() }, pad = 10.dp)
                    IconBtn(if (info.playing) Ic.PAUSE else Ic.PLAY, Color.White, 30.dp, { MediaRepo.playPause() }, pad = 10.dp)
                    IconBtn(Ic.NEXT, Color.White, 28.dp, { MediaRepo.next() }, pad = 10.dp)
                }
            }
        }
        Spacer(Modifier.width(28.dp))
        // Rechts: Warteschlange
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Text("ALS NÄCHSTES", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            val next = info.upcoming()
            if (next.isEmpty()) {
                Text(
                    "${info.appName.lowercase().replaceFirstChar { it.uppercase() }} teilt die Warteschlange gerade nicht.",
                    color = Color.White.copy(alpha = 0.55f), fontSize = 15.sp,
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(next, key = { it.id }) { q ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { MediaRepo.playQueueItem(q.id) }
                                .padding(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Cover(q.icon, Modifier.size(46.dp), radius = 10.dp, elevation = 0.dp)
                            Column(Modifier.padding(start = 12.dp)) {
                                Text(q.title, color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (q.subtitle.isNotBlank()) Text(q.subtitle, color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}
