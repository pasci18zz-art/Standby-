package de.standby.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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

private val dashDateFmt = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)

/**
 * Tablet-Startbild aus Bausteinen: links Uhr (+ Datum, Wetter) und Lernzeit, rechts Musik mit Warteschlange.
 * Die Farbe des Covers färbt den ganzen Hintergrund. Es gibt bewusst keinen laufenden Fortschrittsbalken –
 * so muss sich nur einmal pro Minute etwas neu zeichnen (spart Akku).
 */
@Composable
fun DashboardPage(now: LocalDateTime, media: MediaInfo?, musicActive: Boolean) {
    val showMusic = Prefs.dashMusic && musicActive && media != null

    Box(Modifier.fillMaxSize()) {
        val musicColumn: @Composable (Modifier) -> Unit = { m ->
            when {
                showMusic && media != null -> MusicColumn(media, m)
                Prefs.dashMusic -> TasksTile(m.padding(vertical = 4.dp), max = 6)
                else -> Box(m)
            }
        }
        Row(
            Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(36.dp),
        ) {
            if (!Prefs.dashMusicRight && Prefs.dashMusic) musicColumn(Modifier.weight(0.85f).fillMaxHeight())
            LeftColumn(now, Modifier.weight(1.15f).fillMaxHeight())
            if (Prefs.dashMusicRight && Prefs.dashMusic) musicColumn(Modifier.weight(0.85f).fillMaxHeight())
        }
    }
}

@Composable
private fun LeftColumn(now: LocalDateTime, modifier: Modifier) {
    val weather by Weather.data.collectAsState()
    Column(modifier) {
        if (Prefs.dashClock) {
            ClockFace(Prefs.clockStyle, now, Modifier.fillMaxWidth().weight(1f))
        } else {
            Spacer(Modifier.weight(1f))
        }
        if (Prefs.dashDate || Prefs.dashWeather) {
            Column(Modifier.padding(start = 10.dp, top = 4.dp, bottom = 16.dp)) {
                if (Prefs.dashDate) {
                    Text(
                        now.format(dashDateFmt).uppercase(),
                        color = Color.White.copy(alpha = 0.6f), fontSize = 20.sp, letterSpacing = 5.sp,
                    )
                }
                val w = weather
                if (Prefs.dashWeather && w != null) {
                    Text(
                        "${w.emoji} ${w.temp}° · ${w.text} · ${w.city}",
                        color = Color.White.copy(alpha = 0.75f), fontSize = 19.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
        if (Prefs.dashStudy) StudyFlip(now, Modifier.fillMaxWidth())
    }
}

/** Lernzeit heute aus der Schulplaner-App als Klappuhr: nur Stunden und Minuten, Aktualisierung einmal pro Minute. */
@Composable
private fun StudyFlip(now: LocalDateTime, modifier: Modifier = Modifier) {
    val sessions by Planner.study.collectAsState()
    val data by Planner.data.collectAsState()
    val account by Planner.account.collectAsState()
    LaunchedEffect(account) {
        while (account != null) {
            Planner.refreshStudy()
            delay(60_000)
        }
    }
    val nowMs = System.currentTimeMillis()
    // Neu berechnen, sobald sich die Minute ändert.
    val (ms, running) = remember(sessions, now.minute, now.hour) { Planner.studyToday(sessions, nowMs) }
    val totalMin = (ms / 60_000).toInt()
    val hours = (totalMin / 60).toString().padStart(2, '0')
    val minutes = (totalMin % 60).toString().padStart(2, '0')

    Column(modifier.padding(bottom = 4.dp)) {
        Text("LERNZEIT HEUTE", color = Color.White.copy(alpha = 0.6f), fontSize = 15.sp, letterSpacing = 5.sp, modifier = Modifier.padding(start = 6.dp, bottom = 10.dp))
        if (account == null) {
            Text("Schulplaner verbinden: Einstellungen → Schulplaner", color = Color.White.copy(alpha = 0.5f), fontSize = 16.sp, modifier = Modifier.padding(start = 6.dp))
            return@Column
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cardW: Dp = minOf(200.dp, (maxWidth - 20.dp) / 2)
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Column {
                        FlipCard(hours, Modifier.width(cardW).height(cardW * 0.7f))
                        Text("STUNDEN", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp, letterSpacing = 4.sp, modifier = Modifier.padding(start = 6.dp, top = 6.dp))
                    }
                    Column {
                        FlipCard(minutes, Modifier.width(cardW).height(cardW * 0.7f))
                        Text("MINUTEN", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp, letterSpacing = 4.sp, modifier = Modifier.padding(start = 6.dp, top = 6.dp))
                    }
                }
                if (running != null) {
                    val subject = running.subjectId?.let { data.subjects[it]?.name }
                    Row(Modifier.padding(start = 6.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(10.dp).height(10.dp).background(Color(0xFF5BE37D), androidx.compose.foundation.shape.CircleShape))
                        Text("  läuft" + (subject?.let { " · $it" } ?: ""), color = Color.White.copy(alpha = 0.7f), fontSize = 15.sp)
                    }
                }
            }
        }
    }
}

/** Rechte Seite: großes Cover, Titel, Steuerung und Warteschlange darunter. */
@Composable
private fun MusicColumn(info: MediaInfo, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Cover(info.cover, Modifier.weight(1f, fill = false).aspectRatio(1f), radius = 36.dp, elevation = 26.dp)
        Spacer(Modifier.height(16.dp))
        Text(
            info.title, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Text(
            info.artist, color = Color.White.copy(alpha = 0.65f), fontSize = 20.sp,
            textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Ic.PREV, Color.White, 34.dp, { MediaRepo.previous() }, pad = 12.dp)
            IconBtn(if (info.playing) Ic.PAUSE else Ic.PLAY, Color.White, 36.dp, { MediaRepo.playPause() }, pad = 12.dp)
            IconBtn(Ic.NEXT, Color.White, 34.dp, { MediaRepo.next() }, pad = 12.dp)
        }
        if (Prefs.dashQueue) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.12f)))
            QueueColumn(info, scale = 1.1f, modifier = Modifier.fillMaxWidth().heightIn(max = 190.dp).padding(top = 12.dp))
        }
    }
}
