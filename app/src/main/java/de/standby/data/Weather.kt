package de.standby.data

import de.standby.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.roundToInt

data class WeatherData(
    val temp: Int,
    val code: Int,
    val hi: Int,
    val lo: Int,
    val rain: Int,
    val city: String,
) {
    val emoji: String
        get() = when (code) {
            0 -> "☀️"
            1, 2 -> "🌤️"
            3 -> "☁️"
            45, 48 -> "🌫️"
            in 51..67 -> "🌧️"
            in 71..77, 85, 86 -> "❄️"
            in 80..82 -> "🌦️"
            in 95..99 -> "⛈️"
            else -> "🌡️"
        }
    val text: String
        get() = when (code) {
            0 -> "Klar"
            1, 2 -> "Heiter"
            3 -> "Bewölkt"
            45, 48 -> "Nebel"
            in 51..57 -> "Nieselregen"
            in 61..67 -> "Regen"
            in 71..77, 85, 86 -> "Schnee"
            in 80..82 -> "Schauer"
            in 95..99 -> "Gewitter"
            else -> ""
        }
}

object Weather {
    val data = MutableStateFlow<WeatherData?>(null)
    val error = MutableStateFlow<String?>(null)

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 20_000
        try {
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    suspend fun refresh() = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.open-meteo.com/v1/forecast?latitude=${Prefs.lat}&longitude=${Prefs.lon}" +
                "&current=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
                "&timezone=auto&forecast_days=1"
            val o = Json.parseToJsonElement(get(url)).jsonObject
            val cur = o["current"]!!.jsonObject
            val daily = o["daily"]!!.jsonObject
            fun first(a: String): Double = (daily[a] as JsonArray).first().jsonPrimitive.doubleOrNull ?: 0.0
            data.value = WeatherData(
                temp = cur["temperature_2m"]!!.jsonPrimitive.doubleOrNull?.roundToInt() ?: 0,
                code = cur["weather_code"]!!.jsonPrimitive.intOrNull ?: 0,
                hi = first("temperature_2m_max").roundToInt(),
                lo = first("temperature_2m_min").roundToInt(),
                rain = (daily["precipitation_probability_max"] as? JsonArray)?.firstOrNull()?.jsonPrimitive?.intOrNull ?: 0,
                city = Prefs.city,
            )
            error.value = null
        } catch (e: Exception) {
            error.value = "Wetter nicht erreichbar"
        }
    }

    /** Sucht einen Ort und speichert ihn. Gibt den gefundenen Namen zurück oder null. */
    suspend fun setCity(query: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = "https://geocoding-api.open-meteo.com/v1/search?count=1&language=de&name=" + URLEncoder.encode(query.trim(), "UTF-8")
            val o = Json.parseToJsonElement(get(url)).jsonObject
            val r = (o["results"] as? JsonArray)?.firstOrNull()?.jsonObject ?: return@withContext null
            val name = r["name"]!!.jsonPrimitive.content
            Prefs.lat = (r["latitude"]!!.jsonPrimitive.doubleOrNull ?: return@withContext null).toFloat()
            Prefs.lon = (r["longitude"]!!.jsonPrimitive.doubleOrNull ?: return@withContext null).toFloat()
            Prefs.city = name
            name
        } catch (e: Exception) {
            null
        }
    }
}
