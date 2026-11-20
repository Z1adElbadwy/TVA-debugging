package com.tva.app

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class AiProvider(val key: String, val base: String, val model: String)

fun aiProvider(s: AppState): AiProvider {
    val key = s.aiKey.trim()
    var base = "https://api.openai.com/v1"; var model = "gpt-4o-mini"
    if (key.startsWith("gsk_")) { base = "https://api.groq.com/openai/v1"; model = "openai/gpt-oss-120b" }
    else if (key.startsWith("AIza") || key.startsWith("AQ.")) { base = "https://generativelanguage.googleapis.com/v1beta/openai"; model = "gemini-2.5-flash" }
    val m = s.aiModel.trim()
    return AiProvider(key, base, if (m.isNotEmpty()) m else model)
}

class AiReply(val reply: String, val acted: Boolean)

object Ai {
    private val history = ArrayList<Pair<String, String>>()   // (role, content)

    private fun post(url: String, headers: Map<String, String>, body: ByteArray, contentType: String): Pair<Int, String> {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.connectTimeout = 20000; c.readTimeout = 60000; c.doOutput = true
        c.setRequestProperty("Content-Type", contentType)
        headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
        c.outputStream.use { it.write(body) }
        val code = c.responseCode
        val st = if (code in 200..299) c.inputStream else c.errorStream
        val txt = st?.bufferedReader()?.use { it.readText() } ?: ""
        return code to txt
    }

    private fun toolDefs(): JSONArray = JSONArray(
        """[{"type":"function","function":{"name":"add_item","description":"Create a task, note or checklist.","parameters":{"type":"object","properties":{"type":{"type":"string","enum":["task","note","checklist"]},"title":{"type":"string"},"date":{"type":"string","description":"YYYY-MM-DD, defaults to today"},"time":{"type":"string","description":"HH:MM 24h, optional"},"daily":{"type":"boolean"},"days":{"type":"array","items":{"type":"integer"},"description":"weekdays to repeat on, 0=Sunday..6=Saturday"}},"required":["type","title"]}}},{"type":"function","function":{"name":"complete_item","description":"Mark an item done (or not done) for today.","parameters":{"type":"object","properties":{"id":{"type":"string"},"done":{"type":"boolean"}},"required":["id"]}}},{"type":"function","function":{"name":"delete_item","description":"Delete an item permanently.","parameters":{"type":"object","properties":{"id":{"type":"string"}},"required":["id"]}}}]"""
    )

    private fun runTool(s: AppState, name: String, a: JSONObject): JSONObject {
        return try {
            if (name == "add_item") {
                val type = a.optString("type")
                if (type !in listOf("task", "note", "checklist")) return JSONObject().put("ok", false).put("error", "bad type")
                val title = a.optString("title")
                if (title.isEmpty()) return JSONObject().put("ok", false).put("error", "title required")
                val date = a.optString("date")
                if (date.isNotEmpty() && !Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(date)) return JSONObject().put("ok", false).put("error", "date must be YYYY-MM-DD")
                val time = a.optString("time")
                if (time.isNotEmpty() && !Regex("^\\d{2}:\\d{2}$").matches(time)) return JSONObject().put("ok", false).put("error", "time must be HH:MM")
                val days = a.optJSONArray("days")?.let { d -> (0 until d.length()).map { d.getInt(it) }.filter { it in 0..6 } } ?: emptyList()
                val it = s.apiAdd(type, title.trim(), date, time, a.optBoolean("daily"), days)
                JSONObject().put("ok", true).put("id", it.id).put("type", it.type).put("title", it.title).put("date", it.date).put("time", it.time)
            } else {
                val id = a.optString("id")
                if (s.items.none { it.id == id }) JSONObject().put("ok", false).put("error", "no item with that id")
                else when (name) {
                    "complete_item" -> { s.apiComplete(id, a.optBoolean("done", true)); JSONObject().put("ok", true) }
                    "delete_item" -> { s.apiRemove(id); JSONObject().put("ok", true) }
                    else -> JSONObject().put("ok", false).put("error", "unknown tool")
                }
            }
        } catch (e: Throwable) { JSONObject().put("ok", false).put("error", e.message ?: "error") }
    }

    suspend fun chat(s: AppState, text: String): AiReply = withContext(Dispatchers.IO) {
        val p = aiProvider(s)
        if (p.key.isEmpty()) throw Exception("nokey")
        val n = LocalDateTime.now(); val a = LocalDate.now().toString()
        val weekday = n.dayOfWeek.value % 7
        val instr = s.aiInstr.trim(); val ctx = s.aiCtx.trim()
        val itemsJson = withContext(Dispatchers.Main) { s.apiItems().toString() }
        val sys = "You are Miss Minutes, the cheerful clock assistant from the TVA, living inside the user's planner app. Warm, sassy, Southern charm (\"sugar\", \"darlin'\"). You only understand Arabic and English. Always reply in the same language as the user's latest message: Arabic if they wrote or spoke Arabic, English if English. Your replies are spoken aloud, so keep them to one or two short sentences, no lists, no markdown, no emoji.\n" +
            "You manage three kinds of items: tasks, notes and checklists. Use the tools to add, complete or delete them. Never claim you did something unless the tool result says ok:true; if a tool fails, say so honestly. If the user is just chatting, chat back briefly.\n" +
            "Now: $n. Today is $a (weekday $weekday, 0=Sunday). Resolve words like \"tomorrow\" or \"Friday\" to YYYY-MM-DD yourself.\n" +
            "Current items (use these ids): $itemsJson" +
            (if (ctx.isNotEmpty()) "\n\nContext about the user (always take it into account):\n$ctx" else "") +
            (if (instr.isNotEmpty()) "\n\nThe user's standing instructions for how you must behave. Follow them in every reply; they override your default style:\n$instr" else "")
        val msgs = JSONArray().put(JSONObject().put("role", "system").put("content", sys))
        history.takeLast(8).forEach { (r, c) -> msgs.put(JSONObject().put("role", r).put("content", c)) }
        msgs.put(JSONObject().put("role", "user").put("content", text))
        var acted = false; var out = ""
        for (round in 0 until 4) {
            val body = JSONObject().put("model", p.model).put("messages", msgs).put("tools", toolDefs()).put("tool_choice", "auto").put("temperature", .4)
            val (code, txt) = post(p.base + "/chat/completions", mapOf("Authorization" to "Bearer ${p.key}"), body.toString().toByteArray(), "application/json")
            if (code !in 200..299) {
                var m = ""
                try {
                    val j = JSONTokener(txt)
                    val err = if (j is JSONArray) j.optJSONObject(0)?.optJSONObject("error") else (j as? JSONObject)?.optJSONObject("error")
                    m = err?.optString("message") ?: ""
                } catch (_: Throwable) {}
                throw Exception(if (code == 401 || code == 403) "badkey" else "HTTP $code" + (if (m.isNotEmpty()) ": ${m.take(120)}" else ""))
            }
            val msg = JSONObject(txt).optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message") ?: throw Exception("empty reply")
            val calls = msg.optJSONArray("tool_calls")
            if (calls != null && calls.length() > 0) {
                msgs.put(msg)
                for (k in 0 until calls.length()) {
                    val c = calls.getJSONObject(k); val fn = c.getJSONObject("function")
                    val args = try { JSONObject(fn.optString("arguments", "{}").ifEmpty { "{}" }) } catch (_: Throwable) { JSONObject() }
                    val r = withContext(Dispatchers.Main) { runTool(s, fn.getString("name"), args) }
                    if (r.optBoolean("ok")) acted = true
                    msgs.put(JSONObject().put("role", "tool").put("tool_call_id", c.getString("id")).put("content", r.toString()))
                }
                continue
            }
            out = (if (msg.isNull("content")) "" else msg.optString("content")).trim()
            break
        }
        if (out.isEmpty()) {
            val ar = Regex("[؀-ۿ]").containsMatchIn(text)
            out = if (ar) (if (acted) "تم يا عزيزي!" else "حصلت لخبطة صغيرة يا عزيزي.") else (if (acted) "All done, sugar!" else "Hmm, I got a bit tangled up there, darlin'.")
        }
        history.add("user" to text); history.add("assistant" to out)
        AiReply(out, acted)
    }

    private fun JSONTokener(t: String): Any? = try { org.json.JSONTokener(t).nextValue() } catch (_: Throwable) { null }

    suspend fun search(s: AppState, q: String, items: List<Item>): List<String> = withContext(Dispatchers.IO) {
        val p = aiProvider(s)
        if (p.key.isEmpty() || items.isEmpty()) return@withContext emptyList<String>()
        val arr = JSONArray(); items.forEach { arr.put(JSONObject().put("id", it.id).put("type", it.type).put("title", it.title)) }
        val sys = "You are the search engine of a planner app. Given a query and a list of items, return ONLY a JSON array with the ids of every item relevant to the query. Match by meaning: plurals/singulars, spelling variations, word forms, synonyms, related words, and translations between languages (e.g. Arabic and English). Example: query \"محاضرة\" matches a title \"محاضرات\". No prose, no code fences."
        val body = JSONObject().put("model", p.model).put("temperature", 0).put("messages", JSONArray()
            .put(JSONObject().put("role", "system").put("content", sys))
            .put(JSONObject().put("role", "user").put("content", JSONObject().put("query", q).put("items", arr).toString())))
        val (code, txt) = post(p.base + "/chat/completions", mapOf("Authorization" to "Bearer ${p.key}"), body.toString().toByteArray(), "application/json")
        if (code !in 200..299) throw Exception("HTTP $code")
        val content = JSONObject(txt).optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content") ?: ""
        val m = Regex("\\[[\\s\\S]*\\]").find(content) ?: return@withContext emptyList<String>()
        try {
            val ja = JSONArray(m.value); val ok = items.map { it.id }.toSet()
            (0 until ja.length()).map { ja.optString(it) }.filter { it in ok }
        } catch (_: Throwable) { emptyList() }
    }

    /** Speech-to-text for the mic button: Gemini (AIza…), Groq (gsk_…) or OpenAI Whisper. */
    suspend fun transcribe(wav: ByteArray, key: String): String = withContext(Dispatchers.IO) {
        if (key.startsWith("AIza")) {
            val b64 = Base64.encodeToString(wav, Base64.NO_WRAP)
            val body = JSONObject().put("contents", JSONArray().put(JSONObject().put("parts", JSONArray()
                .put(JSONObject().put("text", "Transcribe this audio exactly as spoken. The speech is either Arabic or English, nothing else. Return only the transcript, nothing else."))
                .put(JSONObject().put("inline_data", JSONObject().put("mime_type", "audio/wav").put("data", b64))))))
            val (code, txt) = post("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=" + java.net.URLEncoder.encode(key, "UTF-8"),
                emptyMap(), body.toString().toByteArray(), "application/json")
            if (code !in 200..299) throw Exception("HTTP $code")
            return@withContext JSONObject(txt).optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text")?.trim() ?: ""
        }
        val groq = key.startsWith("gsk_")
        val url = if (groq) "https://api.groq.com/openai/v1/audio/transcriptions" else "https://api.openai.com/v1/audio/transcriptions"
        val bnd = "----tva" + UUID.randomUUID().toString().replace("-", "")
        val out = ByteArrayOutputStream()
        fun field(n: String, v: String) { out.write("--$bnd\r\nContent-Disposition: form-data; name=\"$n\"\r\n\r\n$v\r\n".toByteArray()) }
        out.write("--$bnd\r\nContent-Disposition: form-data; name=\"file\"; filename=\"voice.wav\"\r\nContent-Type: audio/wav\r\n\r\n".toByteArray())
        out.write(wav); out.write("\r\n".toByteArray())
        field("model", if (groq) "whisper-large-v3-turbo" else "whisper-1")
        field("response_format", "verbose_json")
        field("prompt", "مرحبا. Hello.")
        out.write("--$bnd--\r\n".toByteArray())
        val (code, txt) = post(url, mapOf("Authorization" to "Bearer $key"), out.toByteArray(), "multipart/form-data; boundary=$bnd")
        if (code == 401 || code == 403) throw Exception("badkey")
        if (code !in 200..299) throw Exception("HTTP $code")
        val j = JSONObject(txt)
        val lg = j.optString("language").lowercase()
        if (lg.isNotEmpty() && !Regex("^(ar|en)").containsMatchIn(lg)) throw Exception("lang")
        j.optString("text").trim()
    }
}

/** Microphone capture (16 kHz mono PCM) that can hand out a WAV of everything recorded so far. */
class VoiceRecorder {
    private var rec: AudioRecord? = null
    private val pcm = ByteArrayOutputStream()
    @Volatile private var running = false
    private var thread: Thread? = null
    private val sr = 16000

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        val min = AudioRecord.getMinBufferSize(sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (min <= 0) return false
        val r = try { AudioRecord(MediaRecorder.AudioSource.MIC, sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, min * 4) } catch (_: Throwable) { return false }
        if (r.state != AudioRecord.STATE_INITIALIZED) { r.release(); return false }
        rec = r; running = true; r.startRecording()
        thread = Thread {
            val buf = ByteArray(min)
            while (running) {
                val n = r.read(buf, 0, buf.size)
                if (n > 0) synchronized(pcm) { pcm.write(buf, 0, n) }
            }
        }.also { it.start() }
        return true
    }

    fun hasAudio(): Boolean = synchronized(pcm) { pcm.size() > sr / 4 }

    fun wav(): ByteArray {
        val data = synchronized(pcm) { pcm.toByteArray() }
        val h = ByteArray(44)
        fun i32(o: Int, v: Int) { h[o] = v.toByte(); h[o + 1] = (v shr 8).toByte(); h[o + 2] = (v shr 16).toByte(); h[o + 3] = (v shr 24).toByte() }
        fun i16(o: Int, v: Int) { h[o] = v.toByte(); h[o + 1] = (v shr 8).toByte() }
        "RIFF".toByteArray().copyInto(h, 0); i32(4, 36 + data.size); "WAVEfmt ".toByteArray().copyInto(h, 8)
        i32(16, 16); i16(20, 1); i16(22, 1); i32(24, sr); i32(28, sr * 2); i16(32, 2); i16(34, 16)
        "data".toByteArray().copyInto(h, 36); i32(40, data.size)
        return h + data
    }

    fun stop() {
        running = false
        try { thread?.join(300) } catch (_: Throwable) {}
        try { rec?.stop() } catch (_: Throwable) {}
        try { rec?.release() } catch (_: Throwable) {}
        rec = null
    }
}
