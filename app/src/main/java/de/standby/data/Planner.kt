package de.standby.data

import android.content.Context
import android.content.SharedPreferences
import de.standby.BuildConfig
import de.standby.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate

@Serializable
data class PTask(
    val id: String = "",
    val title: String = "",
    val subjectId: String? = null,
    val dueDate: Long? = null,
    val done: Boolean = false,
    val deleted: Boolean = false,
)

@Serializable
data class PSubject(
    val id: String = "",
    val name: String = "",
    val shortName: String = "",
    val color: Long = 0xFF5B7FDB,
)

@Serializable
data class PExam(
    val id: String = "",
    val subjectId: String = "",
    val date: Long = 0,
    val title: String = "",
    val type: String = "Klausur",
    val deleted: Boolean = false,
)

data class PlannerData(
    val tasks: List<PTask> = emptyList(),
    val exams: List<PExam> = emptyList(),
    val subjects: Map<String, PSubject> = emptyMap(),
    val error: String? = null,
)

/**
 * Liest Aufgaben, Klausuren und Fächer aus demselben Supabase-Konto wie die Schulplaner-App.
 * Nur lesend – die Standby-App ändert nichts an deinen Daten.
 */
object Planner {
    val data = MutableStateFlow(PlannerData())
    val account = MutableStateFlow<String?>(null)

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private var sp: SharedPreferences? = null

    fun init(ctx: Context) {
        if (sp != null) return
        sp = ctx.applicationContext.getSharedPreferences("planner", Context.MODE_PRIVATE)
        account.value = sp!!.getString("email", null)
        sp!!.getString("cache", null)?.let { runCatching { data.value = parse(json.parseToJsonElement(it) as JsonArray) } }
    }

    private fun normalizeUrl(raw: String): String {
        val clean = raw.filter { it.code in 33..126 }
        Regex("([a-z0-9-]+)\\.supabase\\.(co|in)(?![a-z])", RegexOption.IGNORE_CASE).find(clean)?.let {
            return "https://" + it.value.lowercase()
        }
        Regex("project/([a-z0-9]{10,})", RegexOption.IGNORE_CASE).find(clean)?.let {
            return "https://" + it.groupValues[1].lowercase() + ".supabase.co"
        }
        return clean.trimEnd('/')
    }

    private val baseUrl get() = normalizeUrl(Prefs.sbUrl.ifBlank { BuildConfig.SUPABASE_URL })
    private val apiKey get() = Prefs.sbKey.filter { it.code in 33..126 }.ifBlank { BuildConfig.SUPABASE_KEY.filter { it.code in 33..126 } }
    val configured get() = baseUrl.startsWith("https://") && apiKey.isNotBlank()

    private fun http(method: String, path: String, body: String?, token: String?): Pair<Int, String> {
        val conn = URL(baseUrl + path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("apikey", apiKey)
            conn.setRequestProperty("Content-Type", "application/json")
            if (token != null) conn.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            return code to (stream?.bufferedReader()?.use { it.readText() } ?: "")
        } finally {
            conn.disconnect()
        }
    }

    /** Meldet sich an. Gibt null bei Erfolg zurück, sonst eine Fehlermeldung. */
    suspend fun signIn(email: String, password: String): String? = withContext(Dispatchers.IO) {
        if (!configured) return@withContext "Bitte Server-Adresse und Schlüssel eintragen"
        try {
            val body = buildJsonObject { put("email", email.trim()); put("password", password) }.toString()
            val (code, text) = http("POST", "/auth/v1/token?grant_type=password", body, null)
            if (code !in 200..299) {
                return@withContext if (text.contains("Invalid login", true)) "E-Mail oder Passwort falsch" else "Anmeldung fehlgeschlagen ($code)"
            }
            saveSession(json.parseToJsonElement(text).jsonObject, email.trim())
            account.value = email.trim()
            refresh()
            null
        } catch (e: Exception) {
            "Server nicht erreichbar – Adresse und Internet prüfen"
        }
    }

    fun signOut() {
        sp?.edit()?.clear()?.apply()
        account.value = null
        data.value = PlannerData()
    }

    private fun saveSession(o: JsonObject, email: String) {
        sp?.edit()
            ?.putString("access", o["access_token"]!!.jsonPrimitive.content)
            ?.putString("refresh", o["refresh_token"]?.jsonPrimitive?.content ?: "")
            ?.putLong("expires", System.currentTimeMillis() / 1000 + (o["expires_in"]?.jsonPrimitive?.long ?: 3600))
            ?.putString("email", email)
            ?.apply()
    }

    private fun validToken(): String? {
        val s = sp ?: return null
        val token = s.getString("access", null) ?: return null
        if (s.getLong("expires", 0) - 60 > System.currentTimeMillis() / 1000) return token
        val refresh = s.getString("refresh", "") ?: ""
        if (refresh.isBlank()) return null
        val body = buildJsonObject { put("refresh_token", refresh) }.toString()
        val (code, text) = http("POST", "/auth/v1/token?grant_type=refresh_token", body, null)
        if (code !in 200..299) {
            if (code == 400 || code == 401) signOut()
            return null
        }
        saveSession(json.parseToJsonElement(text).jsonObject, s.getString("email", "") ?: "")
        return s.getString("access", null)
    }

    suspend fun refresh() = withContext(Dispatchers.IO) {
        if (account.value == null || !configured) return@withContext
        try {
            val token = validToken() ?: run {
                data.value = data.value.copy(error = "Bitte neu anmelden")
                return@withContext
            }
            val rows = ArrayList<kotlinx.serialization.json.JsonElement>()
            var offset = 0
            while (true) {
                val q = "/rest/v1/records?select=type,data&type=in.(task,subject,exam)&deleted=eq.false" +
                    "&order=id.asc&limit=1000&offset=$offset"
                val (code, text) = http("GET", q, null, token)
                if (code !in 200..299) {
                    data.value = data.value.copy(error = "Abruf fehlgeschlagen ($code)")
                    return@withContext
                }
                val arr = json.parseToJsonElement(text) as JsonArray
                rows.addAll(arr)
                if (arr.size < 1000) break
                offset += 1000
            }
            val all = JsonArray(rows)
            sp?.edit()?.putString("cache", all.toString())?.apply()
            data.value = parse(all)
        } catch (e: Exception) {
            data.value = data.value.copy(error = "Keine Verbindung")
        }
    }

    private fun parse(rows: JsonArray): PlannerData {
        val tasks = ArrayList<PTask>()
        val exams = ArrayList<PExam>()
        val subjects = HashMap<String, PSubject>()
        for (r in rows) {
            val o = r.jsonObject
            val type = o["type"]?.jsonPrimitive?.content ?: continue
            val d = o["data"] as? JsonObject ?: continue
            runCatching {
                when (type) {
                    "task" -> json.decodeFromJsonElement(PTask.serializer(), d).takeIf { !it.deleted }?.let { tasks.add(it) }
                    "exam" -> json.decodeFromJsonElement(PExam.serializer(), d).takeIf { !it.deleted }?.let { exams.add(it) }
                    "subject" -> json.decodeFromJsonElement(PSubject.serializer(), d).let { subjects[it.id] = it }
                }
            }
        }
        return PlannerData(tasks = tasks, exams = exams, subjects = subjects)
    }

    /** Offene Aufgaben: überfällige und baldige zuerst, ohne Datum zuletzt. */
    fun openTasks(d: PlannerData): List<PTask> =
        d.tasks.filter { !it.done }.sortedWith(compareBy({ it.dueDate ?: Long.MAX_VALUE }, { it.title }))

    fun upcomingExams(d: PlannerData): List<PExam> {
        val today = LocalDate.now().toEpochDay()
        return d.exams.filter { it.date >= today }.sortedBy { it.date }
    }
}
