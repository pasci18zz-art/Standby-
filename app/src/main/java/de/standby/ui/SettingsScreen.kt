package de.standby.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.standby.Prefs
import de.standby.backdrops
import de.standby.data.Planner
import de.standby.data.Weather
import de.standby.media.MediaRepo
import de.standby.palettes
import de.standby.customPalette
import de.standby.hueColor
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.ui.graphics.Brush
import kotlinx.coroutines.launch

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Chips(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (id, label) ->
            FilterChip(selected = id == selected, onClick = { onSelect(id) }, label = { Text(label) })
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White.copy(alpha = 0.85f), fontSize = 16.sp, modifier = Modifier.weight(1f))
        Switch(checked, onChange)
    }
}

@Composable
private fun Stepper(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White.copy(alpha = 0.85f), fontSize = 16.sp, modifier = Modifier.width(80.dp))
        TextButton({ onChange((value + 23) % 24) }) { Text("–", fontSize = 22.sp) }
        Text("${value.toString().padStart(2, '0')}:00", color = Color.White, fontSize = 18.sp)
        TextButton({ onChange((value + 1) % 24) }) { Text("+", fontSize = 22.sp) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorDots(options: List<Pair<String, List<Color>>>, selected: String, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (id, cols) ->
            Row(
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .border(2.dp, if (id == selected) Color.White else Color.White.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                    .clickable { onSelect(id) }
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                cols.forEach { c -> Box(Modifier.size(22.dp).clip(CircleShape).background(c)) }
            }
        }
    }
}

@Composable
private fun HueSlider(label: String, hue: Float, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(26.dp).clip(CircleShape).background(hueColor(hue)))
        Text(label, color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp, modifier = Modifier.padding(start = 10.dp).width(96.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            // Regenbogen-Spur hinter dem Regler
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp).height(8.dp).clip(RoundedCornerShape(4.dp))
                    .background(Brush.horizontalGradient((0..6).map { hueColor(it * 60f) })),
            )
            Slider(
                value = hue, onValueChange = onChange, valueRange = 0f..360f,
                colors = SliderDefaults.colors(
                    thumbColor = hueColor(hue),
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent,
                ),
            )
        }
    }
}

@Composable
fun SettingsScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val musicAccess by MediaRepo.hasAccess.collectAsState()
    val account by Planner.account.collectAsState()

    Box(Modifier.fillMaxSize().background(Color(0xF00E0E12))) {
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = 760.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Einstellungen", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Button(onClose) { Text("Fertig") }
            }
            Spacer(Modifier.height(8.dp))

            Section("Uhr") {
                Chips(clockStyles, Prefs.clockStyle) { Prefs.clockStyle = it }
                Text("Farben (Linien-Uhr)", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
                ColorDots(
                    palettes.map { it.id to (it.spectrum + it.colon) } + ("custom" to (customPalette().spectrum + customPalette().colon)),
                    Prefs.palette,
                ) { Prefs.palette = it }
                Text(
                    (palettes.firstOrNull { it.id == Prefs.palette }?.name ?: "Eigene Farben"),
                    color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp,
                )
                if (Prefs.palette == "custom") {
                    HueSlider("Farbe 1", Prefs.customH1) { Prefs.customH1 = it }
                    HueSlider("Farbe 2", Prefs.customH2) { Prefs.customH2 = it }
                    HueSlider("Farbe 3", Prefs.customH3) { Prefs.customH3 = it }
                    HueSlider("Doppelpunkt", Prefs.customHColon) { Prefs.customHColon = it }
                }
                SwitchRow("Verspielte Linien (mit Lücken, wie Daily Board)", Prefs.playfulLines) { Prefs.playfulLines = it }
                Text("Hintergrund", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
                ColorDots(backdrops.map { it.id to listOf(it.color) }, Prefs.background) { Prefs.background = it }
                SwitchRow("Farben vom Cover übernehmen (Uhr & Hintergrund)", Prefs.coverTheme) { Prefs.coverTheme = it }
                SwitchRow("24-Stunden-Format", Prefs.use24h) { Prefs.use24h = it }
                SwitchRow("Sekunden anzeigen (Minimal-Uhr)", Prefs.showSeconds) { Prefs.showSeconds = it }
                SwitchRow("Datum unter der Uhr", Prefs.showDate) { Prefs.showDate = it }
            }

            Section("Musik") {
                Text(
                    if (musicAccess) "✓ Zugriff auf laufende Musik ist aktiv." else "Damit Titel, Cover und Steuerung angezeigt werden, braucht die App Zugriff auf Benachrichtigungen.",
                    color = if (musicAccess) Color(0xFF86EFAC) else Color.White.copy(alpha = 0.8f), fontSize = 15.sp,
                )
                if (!musicAccess) {
                    Button({ ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) {
                        Text("Zugriff erlauben")
                    }
                }
                SwitchRow("Mini-Player unter der Uhr", Prefs.miniPlayer) { Prefs.miniPlayer = it }
                Text("Nach einer Pause noch anzeigen für …", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
                Chips(listOf("2" to "2 Min.", "10" to "10 Min.", "30" to "30 Min.", "120" to "2 Std.", "0" to "Immer"), Prefs.musicHoldMin.toString()) { Prefs.musicHoldMin = it.toInt() }
                SwitchRow("Musik-Fenster bei neuem Lied automatisch öffnen", Prefs.popupAuto) { Prefs.popupAuto = it }
                if (Prefs.popupAuto) {
                    Text("Offen bleiben für …", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
                    Chips(listOf(5, 10, 15, 20, 30).map { it.toString() to "$it Sek." }, Prefs.popupSeconds.toString()) { Prefs.popupSeconds = it.toInt() }
                }
            }

            Section("Tablet-Startbild") {
                Text("Eigene Startseite aus Bausteinen: Uhr, Datum, Wetter, Lernzeit, Musik und Warteschlange – jeden einzeln ein- und ausschaltbar.", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
                SwitchRow("Startbild als erste Seite", Prefs.pageDash) { Prefs.pageDash = it }
                if (Prefs.pageDash) {
                    SwitchRow("Uhr", Prefs.dashClock) { Prefs.dashClock = it }
                    SwitchRow("Datum", Prefs.dashDate) { Prefs.dashDate = it }
                    SwitchRow("Wetter", Prefs.dashWeather) { Prefs.dashWeather = it }
                    SwitchRow("Lernzeit (Klappuhr aus dem Schulplaner)", Prefs.dashStudy) { Prefs.dashStudy = it }
                    SwitchRow("Musik mit Cover und Steuerung", Prefs.dashMusic) { Prefs.dashMusic = it }
                    SwitchRow("Warteschlange", Prefs.dashQueue) { Prefs.dashQueue = it }
                    Chips(listOf("right" to "Musik rechts", "left" to "Musik links"), if (Prefs.dashMusicRight) "right" else "left") { Prefs.dashMusicRight = it == "right" }
                }
            }

            Section("Seiten") {
                Text("Zur Seite wischen: Uhr → Musik (nur wenn etwas läuft) → Uhr + Musik → Infos", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
                SwitchRow("Seite „Uhr + Musik / Aufgaben“", Prefs.pageSplit) { Prefs.pageSplit = it }
                SwitchRow("Seite „Infos“ (Wetter, Aufgaben, Klausuren)", Prefs.pageInfo) { Prefs.pageInfo = it }
            }

            Section("Ausrichtung") {
                Text("Die App steht hochkant, das Bild bleibt aber im Querformat-Layout und wird gedreht.", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
                Chips(
                    listOf("cw" to "Oberkante rechts", "ccw" to "Oberkante links", "off" to "Nicht drehen"),
                    Prefs.rotateMode,
                ) { Prefs.rotateMode = it }
            }

            Section("Anzeige & Nacht") {
                Chips(listOf("auto" to "Automatisch (Nachtmodus)", "bright" to "Immer hell", "dim" to "Immer gedimmt"), Prefs.brightnessMode) { Prefs.brightnessMode = it }
                Stepper("Nacht ab", Prefs.nightFrom) { Prefs.nightFrom = it }
                Stepper("bis", Prefs.nightTo) { Prefs.nightTo = it }
                SwitchRow("Pixel-Verschiebung gegen Einbrennen", Prefs.pixelShift) { Prefs.pixelShift = it }
            }

            Section("Wetter") {
                var q by remember { mutableStateOf(Prefs.city) }
                var msg by remember { mutableStateOf<String?>(null) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(q, { q = it }, label = { Text("Ort") }, singleLine = true, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(12.dp))
                    Button({
                        scope.launch {
                            val r = Weather.setCity(q)
                            msg = if (r == null) "Ort nicht gefunden" else "Ort gesetzt: $r"
                            if (r != null) { q = r; Weather.refresh() }
                        }
                    }) { Text("Suchen") }
                }
                msg?.let { Text(it, color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp) }
            }

            Section("Schulplaner") {
                if (account != null) {
                    Text("Verbunden als $account", color = Color(0xFF86EFAC), fontSize = 16.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button({ scope.launch { Planner.refresh() } }) { Text("Jetzt aktualisieren") }
                        OutlinedButton({ Planner.signOut() }) { Text("Abmelden") }
                    }
                } else {
                    Text(
                        "Melde dich mit demselben Konto an wie in der Schulplaner-App. Server-Adresse und Schlüssel sind dieselben wie dort (Supabase-Projekt-URL und anon-Schlüssel).",
                        color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp,
                    )
                    if (!Planner.configured) {
                        OutlinedTextField(Prefs.sbUrl, { Prefs.sbUrl = it }, label = { Text("Server-Adresse (…supabase.co)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(Prefs.sbKey, { Prefs.sbKey = it }, label = { Text("Öffentlicher Schlüssel (anon key)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                    var email by remember { mutableStateOf("") }
                    var pw by remember { mutableStateOf("") }
                    var msg by remember { mutableStateOf<String?>(null) }
                    var busy by remember { mutableStateOf(false) }
                    OutlinedTextField(
                        email, { email = it }, label = { Text("E-Mail") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    )
                    OutlinedTextField(
                        pw, { pw = it }, label = { Text("Passwort") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                    Button({
                        busy = true
                        scope.launch {
                            msg = Planner.signIn(email, pw)
                            busy = false
                        }
                    }, enabled = !busy && email.isNotBlank() && pw.isNotBlank()) { Text(if (busy) "Melde an …" else "Anmelden") }
                    msg?.let { Text(it, color = Color(0xFFFF8A80), fontSize = 14.sp) }
                }
            }

            Section("Automatisch beim Laden starten") {
                Text(
                    "Samsung: Einstellungen → Modi und Routinen → Routinen → „+“\n" +
                        "Wenn: Ladestatus → Kabellos laden (oder Laden)\n" +
                        "Dann: Apps → App öffnen → Standby\n" +
                        "Tipp: Unter Einstellungen → Akku die App von der Optimierung ausnehmen.",
                    color = Color.White.copy(alpha = 0.75f), fontSize = 15.sp, lineHeight = 22.sp,
                )
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}
