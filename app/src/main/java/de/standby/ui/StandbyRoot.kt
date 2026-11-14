package de.standby.ui

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.view.Window
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.standby.Prefs
import de.standby.currentBackdrop
import de.standby.data.Planner
import de.standby.data.Weather
import de.standby.media.MediaInfo
import de.standby.media.MediaRepo
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class Page { CLOCK, MUSIC, SPLIT, INFO }

@Composable
fun rememberNow(): State<LocalDateTime> = produceState(LocalDateTime.now()) {
    while (true) {
        value = LocalDateTime.now()
        delay(1000 - System.currentTimeMillis() % 1000)
    }
}

private val dateFmt = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)

private fun isNight(hour: Int): Boolean {
    val from = Prefs.nightFrom
    val to = Prefs.nightTo
    return if (from == to) false else if (from < to) hour in from until to else hour >= from || hour < to
}

@Composable
fun StandbyRoot(window: Window) {
    val now by rememberNow()
    val media by MediaRepo.info.collectAsState()
    val musicActive = media?.visible() == true
    val bg = currentBackdrop().color

    // Hintergrund-Aktualisierungen
    LaunchedEffect(Unit) {
        while (true) {
            MediaRepo.refresh()
            delay(4000)
        }
    }
    LaunchedEffect(Prefs.lat, Prefs.lon) {
        while (true) {
            Weather.refresh()
            delay(20 * 60_000L)
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            Planner.refresh()
            delay(10 * 60_000L)
        }
    }

    // Akku
    val ctx = LocalContext.current
    var battery by remember { mutableIntStateOf(-1) }
    var charging by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context?, i: Intent?) {
                if (i == null) return
                val lvl = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                battery = if (lvl >= 0) lvl * 100 / scale else -1
                charging = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
            }
        }
        ctx.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        onDispose { ctx.unregisterReceiver(receiver) }
    }

    // Helligkeit / Nacht
    val dim = when (Prefs.brightnessMode) {
        "dim" -> true
        "bright" -> false
        else -> isNight(now.hour)
    }
    LaunchedEffect(dim) {
        val lp = window.attributes
        lp.screenBrightness = if (dim) 0.02f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window.attributes = lp
    }

    val pages = buildList {
        add(Page.CLOCK)
        if (musicActive) add(Page.MUSIC)
        if (Prefs.pageSplit) add(Page.SPLIT)
        if (Prefs.pageInfo) add(Page.INFO)
    }
    val pager = rememberPagerState { pages.size }

    var overlay by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    LaunchedEffect(overlay) {
        if (overlay) {
            delay(5000)
            overlay = false
        }
    }

    // Pixel-Verschiebung alle 3 Minuten um wenige dp
    val step = now.hour * 20 + now.minute / 3
    val dx = if (Prefs.pixelShift) ((step * 37) % 13 - 6) else 0
    val dy = if (Prefs.pixelShift) ((step * 53) % 9 - 4) else 0

    Box(
        Modifier
            .fillMaxSize()
            .background(bg)
            .pointerInput(Unit) { detectTapGestures(onTap = { overlay = !overlay }) },
    ) {
        HorizontalPager(
            state = pager,
            key = { pages.getOrNull(it)?.name ?: "p$it" },
            modifier = Modifier.fillMaxSize().offset(dx.dp, dy.dp),
        ) { i ->
            when (pages.getOrNull(i)) {
                Page.CLOCK -> ClockPage(now, media, musicActive)
                Page.MUSIC -> media?.let { MusicFull(it) }
                Page.SPLIT -> SplitPage(now, media, musicActive)
                Page.INFO -> InfoPage()
                null -> {}
            }
        }

        // Seitenpunkte
        if (pages.size > 1) {
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                pages.indices.forEach { i ->
                    Box(
                        Modifier.size(6.dp).clip(RoundedCornerShape(3.dp))
                            .background(Color.White.copy(alpha = if (i == pager.currentPage) 0.6f else 0.15f)),
                    )
                }
            }
        }

        // Nachtabdunklung
        if (dim) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))

        AnimatedVisibility(overlay && !settings, enter = fadeIn(), exit = fadeOut()) {
            TopBar(
                battery = battery,
                charging = charging,
                time = now,
                onBrightness = {
                    Prefs.brightnessMode = when (Prefs.brightnessMode) { "auto" -> "bright"; "bright" -> "dim"; else -> "auto" }
                    overlay = true
                },
                onSettings = { settings = true; overlay = false },
            )
        }

        if (settings) SettingsScreen(onClose = { settings = false })
    }
}

@Composable
private fun TopBar(battery: Int, charging: Boolean, time: LocalDateTime, onBrightness: () -> Unit, onSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (battery >= 0) {
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(Color.White).padding(horizontal = 10.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (charging) GIcon(Ic.BOLT, Color(0xFF1E1B2E), Modifier.size(14.dp))
                Text("$battery", color = Color(0xFF1E1B2E), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(14.dp))
        }
        Text(
            time.format(DateTimeFormatter.ofPattern("HH:mm")),
            color = Color.White.copy(alpha = 0.6f), fontSize = 16.sp,
        )
        Spacer(Modifier.weight(1f))
        val modeLabel = when (Prefs.brightnessMode) { "bright" -> "Hell"; "dim" -> "Gedimmt"; else -> "Auto" }
        Text(modeLabel, color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
        IconBtn(if (Prefs.brightnessMode == "dim") Ic.MOON else Ic.SUN, Color.White.copy(alpha = 0.75f), 24.dp, onBrightness)
        IconBtn(Ic.GEAR, Color.White.copy(alpha = 0.75f), 24.dp, onSettings)
    }
}

@Composable
private fun ClockPage(now: LocalDateTime, media: MediaInfo?, musicActive: Boolean) {
    val showMini = musicActive && Prefs.miniPlayer && media != null
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(top = 28.dp, bottom = if (showMini) 112.dp else 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ClockFace(Prefs.clockStyle, now, Modifier.weight(1f).fillMaxWidth().padding(horizontal = 28.dp))
            if (Prefs.showDate) {
                Text(
                    now.format(dateFmt),
                    color = Color.White.copy(alpha = 0.6f), fontSize = 22.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        if (showMini && media != null) {
            MiniPlayer(media, Modifier.align(Alignment.BottomCenter).padding(horizontal = 40.dp, vertical = 20.dp))
        }
    }
}

/** Zwei Kacheln wie bei iOS-StandBy: links Uhr, rechts Musik (oder Aufgaben, wenn nichts läuft). */
@Composable
private fun SplitPage(now: LocalDateTime, media: MediaInfo?, musicActive: Boolean) {
    Row(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Box(
            Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(36.dp))
                .background(Color.White.copy(alpha = 0.05f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ClockFace(Prefs.clockStyle, now, Modifier.fillMaxWidth().height(200.dp).padding(horizontal = 12.dp))
                if (Prefs.showDate) Text(now.format(dateFmt), color = Color.White.copy(alpha = 0.55f), fontSize = 18.sp)
            }
        }
        if (musicActive && media != null) {
            MusicCard(media, Modifier.weight(1f).fillMaxHeight())
        } else {
            TasksTile(Modifier.weight(1f).fillMaxHeight(), max = 5)
        }
    }
}
