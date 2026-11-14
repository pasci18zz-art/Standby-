package de.standby

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import kotlin.reflect.KProperty

/** Einstellungen: bleiben gespeichert und lösen im UI sofort eine Aktualisierung aus. */
object Prefs {
    private var sp: SharedPreferences? = null

    fun init(ctx: Context) {
        if (sp == null) sp = ctx.applicationContext.getSharedPreferences("standby", Context.MODE_PRIVATE)
    }

    @Suppress("UNCHECKED_CAST")
    internal fun <T> read(key: String, def: T): T {
        val s = sp ?: return def
        return when (def) {
            is String -> s.getString(key, def) as T
            is Boolean -> s.getBoolean(key, def) as T
            is Int -> s.getInt(key, def) as T
            is Float -> s.getFloat(key, def) as T
            else -> def
        }
    }

    internal fun <T> write(key: String, v: T) {
        val e = sp?.edit() ?: return
        when (v) {
            is String -> e.putString(key, v)
            is Boolean -> e.putBoolean(key, v)
            is Int -> e.putInt(key, v)
            is Float -> e.putFloat(key, v)
        }
        e.apply()
    }

    class P<T>(private val key: String, private val def: T) {
        private val state = mutableStateOf(def)
        private var loaded = false
        private fun ensure() {
            if (!loaded && sp != null) {
                state.value = read(key, def)
                loaded = true
            }
        }
        operator fun getValue(r: Any?, p: KProperty<*>): T { ensure(); return state.value }
        operator fun setValue(r: Any?, p: KProperty<*>, v: T) { ensure(); state.value = v; write(key, v) }
    }

    // Uhr
    var clockStyle by P("clock_style", "lines")
    var palette by P("palette", "violet")
    var background by P("background", "navy")
    var use24h by P("use24h", true)
    var showSeconds by P("show_seconds", false)
    var showDate by P("show_date", true)

    // Seiten
    var miniPlayer by P("mini_player", true)
    var pageSplit by P("page_split", true)
    var pageInfo by P("page_info", true)

    // Display
    var brightnessMode by P("brightness_mode", "auto") // auto | bright | dim
    var nightFrom by P("night_from", 22)
    var nightTo by P("night_to", 6)
    var pixelShift by P("pixel_shift", true)

    // Wetter
    var city by P("city", "Frankfurt am Main")
    var lat by P("lat", 50.11f)
    var lon by P("lon", 8.68f)

    // Schulplaner (Supabase)
    var sbUrl by P("sb_url", "")
    var sbKey by P("sb_key", "")
}

class Palette(val id: String, val name: String, val c1: Color, val c2: Color, val c3: Color, val colon: Color)
class Backdrop(val id: String, val name: String, val color: Color)

val palettes = listOf(
    Palette("violet", "Violett", Color(0xFF8B5CF6), Color(0xFFE879F9), Color(0xFFFBA8C8), Color(0xFFF25C54)),
    Palette("ocean", "Ozean", Color(0xFF3B82F6), Color(0xFF22D3EE), Color(0xFFA7F3D0), Color(0xFFFB923C)),
    Palette("sunset", "Sonnenuntergang", Color(0xFFF97316), Color(0xFFF43F5E), Color(0xFFFBBF24), Color(0xFF8B5CF6)),
    Palette("mint", "Mint", Color(0xFF10B981), Color(0xFF34D399), Color(0xFFD9F99D), Color(0xFFF472B6)),
    Palette("mono", "Weiß", Color(0xFFFFFFFF), Color(0xFFCBD5E1), Color(0xFF94A3B8), Color(0xFFFFFFFF)),
)

val backdrops = listOf(
    Backdrop("navy", "Nachtblau", Color(0xFF2F2A4A)),
    Backdrop("black", "Schwarz", Color(0xFF000000)),
    Backdrop("warm", "Warmes Dunkel", Color(0xFF2B2325)),
    Backdrop("forest", "Wald", Color(0xFF12261F)),
    Backdrop("slate", "Schiefer", Color(0xFF1E293B)),
)

fun currentPalette() = palettes.firstOrNull { it.id == Prefs.palette } ?: palettes[0]
fun currentBackdrop() = backdrops.firstOrNull { it.id == Prefs.background } ?: backdrops[0]
