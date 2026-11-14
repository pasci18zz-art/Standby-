package de.standby.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.standby.data.PSubject
import de.standby.data.Planner
import de.standby.data.Weather
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun Tile(modifier: Modifier = Modifier, title: String, content: @Composable () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(32.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .padding(22.dp),
    ) {
        Text(title.uppercase(), color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        content()
    }
}

fun dueLabel(epochDay: Long?): Pair<String, Color> {
    if (epochDay == null) return "ohne Datum" to Color.White.copy(alpha = 0.45f)
    val today = LocalDate.now().toEpochDay()
    val d = LocalDate.ofEpochDay(epochDay)
    return when (val diff = epochDay - today) {
        in Long.MIN_VALUE..-1L -> "überfällig" to Color(0xFFFF6B6B)
        0L -> "heute" to Color(0xFFFFB347)
        1L -> "morgen" to Color(0xFFFFE08A)
        in 2L..6L -> d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.GERMAN) to Color.White.copy(alpha = 0.7f)
        else -> d.format(DateTimeFormatter.ofPattern("d.M.")) to Color.White.copy(alpha = 0.55f)
    }
}

@Composable
private fun SubjectDot(s: PSubject?) {
    Box(Modifier.size(10.dp).clip(CircleShape).background(if (s != null) Color(s.color.toInt()) else Color.White.copy(alpha = 0.3f)))
}

@Composable
fun WeatherTile(modifier: Modifier = Modifier) {
    val w by Weather.data.collectAsState()
    val err by Weather.error.collectAsState()
    Tile(modifier, "Wetter") {
        val d = w
        if (d == null) {
            Text(err ?: "Lädt …", color = Color.White.copy(alpha = 0.6f), fontSize = 18.sp)
        } else {
            Text(d.emoji, fontSize = 56.sp)
            Text("${d.temp}°", color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Light)
            Text(d.text, color = Color.White.copy(alpha = 0.8f), fontSize = 20.sp)
            Spacer(Modifier.height(6.dp))
            Text("↑ ${d.hi}°   ↓ ${d.lo}°   ☔ ${d.rain} %", color = Color.White.copy(alpha = 0.6f), fontSize = 16.sp)
            Text(d.city, color = Color.White.copy(alpha = 0.45f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun TasksTile(modifier: Modifier = Modifier, max: Int = 6) {
    val data by Planner.data.collectAsState()
    val acc by Planner.account.collectAsState()
    Tile(modifier, "Aufgaben") {
        if (acc == null) {
            Text("Schulplaner verbinden:\nEinstellungen → Schulplaner", color = Color.White.copy(alpha = 0.6f), fontSize = 17.sp)
            return@Tile
        }
        val tasks = Planner.openTasks(data)
        if (tasks.isEmpty()) {
            Text("Alles erledigt 🎉", color = Color.White.copy(alpha = 0.8f), fontSize = 20.sp)
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            tasks.take(max).forEach { t ->
                val subj = t.subjectId?.let { data.subjects[it] }
                val (label, color) = dueLabel(t.dueDate)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SubjectDot(subj)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.title.ifBlank { "Aufgabe" }, color = Color.White, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (subj != null) Text(subj.name, color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp, maxLines = 1)
                    }
                    Text(label, color = color, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            if (tasks.size > max) Text("+ ${tasks.size - max} weitere", color = Color.White.copy(alpha = 0.45f), fontSize = 14.sp)
        }
        data.error?.let { Text(it, color = Color(0xFFFF8A80), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
    }
}

@Composable
fun ExamsTile(modifier: Modifier = Modifier, max: Int = 4) {
    val data by Planner.data.collectAsState()
    val acc by Planner.account.collectAsState()
    Tile(modifier, "Klausuren") {
        if (acc == null) {
            Text("–", color = Color.White.copy(alpha = 0.5f), fontSize = 18.sp)
            return@Tile
        }
        val exams = Planner.upcomingExams(data)
        if (exams.isEmpty()) Text("Keine anstehend", color = Color.White.copy(alpha = 0.7f), fontSize = 18.sp)
        val today = LocalDate.now().toEpochDay()
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            exams.take(max).forEach { e ->
                val subj = data.subjects[e.subjectId]
                val days = e.date - today
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SubjectDot(subj)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(subj?.name ?: e.title.ifBlank { e.type }, color = Color.White, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            e.type + " · " + LocalDate.ofEpochDay(e.date).format(DateTimeFormatter.ofPattern("EE d.M.", Locale.GERMAN)),
                            color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp,
                        )
                    }
                    Text(
                        when (days) { 0L -> "heute"; 1L -> "morgen"; else -> "in $days T." },
                        color = if (days <= 3) Color(0xFFFFB347) else Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

/** Info-Seite: Wetter, Aufgaben, Klausuren nebeneinander. */
@Composable
fun InfoPage(modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        WeatherTile(Modifier.weight(0.8f).fillMaxHeight())
        TasksTile(Modifier.weight(1.3f).fillMaxHeight())
        ExamsTile(Modifier.weight(1f).fillMaxHeight())
    }
}

@Composable
fun FillerBox(modifier: Modifier) = Box(modifier.fillMaxWidth())
