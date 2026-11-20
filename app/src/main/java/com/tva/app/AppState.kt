package com.tva.app

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.SoundPool
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableSharedFlow
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

enum class Ov { Drawer, Sheet, List, Ai, Pick, Days, Settings, Chess, Search, Instr, Ctx }

/** Sound effects: chess mp3s through SoundPool + the synthesized mic tones. */
class Sfx(ctx: Context) {
    private val pool = SoundPool.Builder().setMaxStreams(4)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
    private val ids = HashMap<String, Int>()

    init {
        mapOf(
            "capture" to R.raw.snd_capture, "castle" to R.raw.snd_castle, "end" to R.raw.snd_end, "start" to R.raw.snd_start,
            "illegal" to R.raw.snd_illegal, "check" to R.raw.snd_check, "opp" to R.raw.snd_opp, "self" to R.raw.snd_self,
            "promote" to R.raw.snd_promote,
        ).forEach { (k, v) -> ids[k] = pool.load(ctx, v, 1) }
    }

    fun play(k: String) { ids[k]?.let { pool.play(it, 1f, 1f, 1, 0, 1f) } }

    private fun beep(fs: List<Int>, d: Double, gap: Double, vol: Double) {
        Thread {
            try {
                val sr = 22050
                val total = ((fs.size * (d + gap)) * sr).toInt() + sr / 10
                val buf = ShortArray(total)
                fs.forEachIndexed { i, f ->
                    val start = (i * (d + gap) * sr).toInt()
                    val n = ((d + .03) * sr).toInt()
                    val pk = vol * min(1.5, (660.0 / f).pow(.55))
                    for (k in 0 until n) {
                        val t = k.toDouble() / sr
                        val env = if (t < .03) t / .03 else exp(-(t - .03) / d * 9.2)
                        val idx = start + k
                        if (idx < total) buf[idx] = (buf[idx] + sin(2 * PI * f * t) * env * pk * 32767 * .8).toInt().coerceIn(-32768, 32767).toShort()
                    }
                }
                val at = AudioTrack(
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
                    AudioFormat.Builder().setSampleRate(sr).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build(),
                    buf.size * 2, AudioTrack.MODE_STATIC, AudioManager.AUDIO_SESSION_ID_GENERATE,
                )
                at.write(buf, 0, buf.size)
                at.play()
                Thread.sleep((total * 1000L / sr) + 80)
                at.release()
            } catch (_: Throwable) {}
        }.start()
    }

    fun toneOn() = beep(listOf(523, 659), .14, .03, .05)
    fun toneOff() = beep(listOf(659, 523), .14, .03, .05)
    fun toneDone() = beep(listOf(523, 659, 784), .12, .02, .05)
    fun toneBad() = beep(listOf(330, 262), .16, .03, .05)
}

class AppState(val ctx: Context) {
    private val sp = ctx.getSharedPreferences("tva", Context.MODE_PRIVATE)
    val sfx = Sfx(ctx)

    // ---- persisted items ------------------------------------------------------
    var items by mutableStateOf(loadItems())
    private fun loadItems(): List<Item> = try {
        val a = JSONArray(sp.getString("items", "[]"))
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            val days = o.optJSONArray("days")?.let { d -> (0 until d.length()).map { d.getInt(it) } } ?: emptyList()
            val done = o.optJSONArray("doneOn")?.let { d -> (0 until d.length()).map { d.getString(it) } } ?: emptyList()
            val sub = o.optJSONArray("sub")?.let { s ->
                (0 until s.length()).map { k ->
                    val so = s.getJSONObject(k)
                    SubItem(so.optString("t"), so.optJSONArray("doneOn")?.let { d -> (0 until d.length()).map { d.getString(it) } } ?: emptyList())
                }
            }
            Item(o.getString("id"), o.optString("type", "task"), o.optString("title"), o.optString("date", today()), o.optString("time", ""),
                o.optBoolean("daily"), days, done, sub, o.optBoolean("lateDone"))
        }
    } catch (e: Throwable) { emptyList() }

    private fun saveItems() {
        val a = JSONArray()
        items.forEach { it ->
            val o = JSONObject().put("id", it.id).put("type", it.type).put("title", it.title).put("date", it.date).put("time", it.time)
                .put("daily", it.daily).put("days", JSONArray(it.days)).put("doneOn", JSONArray(it.doneOn)).put("lateDone", it.lateDone)
            it.sub?.let { s -> o.put("sub", JSONArray().also { arr -> s.forEach { x -> arr.put(JSONObject().put("t", x.t).put("doneOn", JSONArray(x.doneOn))) } }) }
            a.put(o)
        }
        sp.edit().putString("items", a.toString()).apply()
    }

    private fun commit(n: List<Item>) { items = n; saveItems() }

    // ---- simple persisted settings ---------------------------------------------
    var aiKey by mutableStateOf(sp.getString("aiKey", "") ?: "")
    var aiModel by mutableStateOf(sp.getString("aiModel", "") ?: "")
    var aiInstr by mutableStateOf(sp.getString("aiInstr", "") ?: "")
    var aiCtx by mutableStateOf(sp.getString("aiCtx", "") ?: "")
    var chMode by mutableStateOf(sp.getString("chMode", "ai") ?: "ai")
    fun setAiKey(v: String) { aiKey = v.trim(); sp.edit().putString("aiKey", aiKey).apply() }
    fun setAiModel(v: String) { aiModel = v.trim(); sp.edit().putString("aiModel", aiModel).apply() }
    fun setAiInstr(v: String) { aiInstr = v; sp.edit().putString("aiInstr", v).apply() }
    fun setAiCtx(v: String) { aiCtx = v; sp.edit().putString("aiCtx", v).apply() }
    fun setChModePref(v: String) { chMode = v; sp.edit().putString("chMode", v).apply() }

    // ---- navigation (overlay stack) ---------------------------------------------
    val stack = mutableStateListOf<Ov>()
    fun openO(o: Ov) { if (!stack.contains(o)) stack.add(o) }
    fun closeTop() { if (stack.isNotEmpty()) stack.removeAt(stack.size - 1) }
    fun closeAll() { stack.clear() }
    fun openList(v: String, fid: String? = null) { view = v; armed = null; focusId = fid; hlToken = 0; openO(Ov.List); if (fid != null) hlToken = 1 }
    val navTick = mutableIntStateOf(0)

    // ---- UI state -----------------------------------------------------------------
    var draft by mutableStateOf(Draft())
    var pending by mutableStateOf<String?>(null)
    var armed by mutableStateOf<String?>(null)
    var view by mutableStateOf("task")
    var focusId by mutableStateOf<String?>(null)
    var hlToken by mutableIntStateOf(0)
    var pickMode by mutableStateOf("date")
    var lpFired = false
    var focusedInput = false
    var searchCy: Float = Float.NaN

    val moods = MutableSharedFlow<String>(extraBufferCapacity = 32)
    fun mood(m: String) { moods.tryEmit(m) }
    /** set by the mascot: say(text, action, quiet) */
    var say: ((String, String, Boolean) -> Unit)? = null
    /** set by the mascot: handles a message typed in the composer */
    fun later(ms: Long, f: () -> Unit) { android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ f() }, ms) }
    var focusInput: (() -> Unit)? = null
    fun requestFocusSoon() { android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ focusInput?.invoke() }, 320) }
    var onSend: ((String) -> Unit)? = null

    // ---- item actions ---------------------------------------------------------------
    fun addItem(type: String, title: String) {
        val it = mkItem(type, title, draft.date, draft.time, draft.daily, draft.days.toList())
        draft = Draft()
        commit(items + it)
        mood("jump")
    }

    fun tapItem(id: String, d: String) {
        val it = items.firstOrNull { x -> x.id == id } ?: return
        var n = it
        val sub = it.sub
        if (it.type == "checklist" && !sub.isNullOrEmpty()) {
            val all = isDone(it, d)
            n = it.copy(sub = sub.map { x ->
                val has = x.doneOn.contains(d)
                if (all) (if (has) x.copy(doneOn = x.doneOn - d) else x) else (if (!has) x.copy(doneOn = x.doneOn + d) else x)
            })
            if (!all) mood("jump")
        } else {
            n = if (it.doneOn.contains(d)) it.copy(doneOn = it.doneOn - d) else { mood("jump"); it.copy(doneOn = it.doneOn + d) }
        }
        if (isDone(n, d) && d < today()) n = n.copy(lateDone = true)
        commit(items.map { x -> if (x.id == id) n else x })
    }

    fun toggleSub(id: String, d: String, i: Int) {
        val it = items.firstOrNull { x -> x.id == id } ?: return
        val sub = it.sub ?: return
        if (i !in sub.indices) return
        val x = sub[i]
        val nx = if (x.doneOn.contains(d)) x.copy(doneOn = x.doneOn - d) else x.copy(doneOn = x.doneOn + d)
        var n = it.copy(sub = sub.mapIndexed { k, s -> if (k == i) nx else s })
        if (!x.doneOn.contains(d) && isDone(n, d)) { mood("jump"); if (d < today()) n = n.copy(lateDone = true) }
        commit(items.map { y -> if (y.id == id) n else y })
    }

    fun deleteItem(id: String) { commit(items.filter { it.id != id }); armed = null }

    // ---- API used by the assistant ---------------------------------------------------
    fun apiItems(): JSONArray {
        val d = today(); val a = JSONArray()
        items.forEach { i ->
            a.put(JSONObject().put("id", i.id).put("type", i.type).put("title", i.title).put("date", i.date).put("time", i.time)
                .put("daily", i.daily).put("days", JSONArray(i.days)).put("doneToday", isDone(i, d)))
        }
        return a
    }

    fun apiAdd(type: String, title: String, date: String?, time: String?, daily: Boolean, days: List<Int>): Item {
        val it = mkItem(type, title, if (date.isNullOrEmpty()) today() else date, time ?: "", daily, days)
        commit(items + it); return it
    }

    fun apiComplete(id: String, done: Boolean) {
        val it = items.firstOrNull { x -> x.id == id } ?: return
        val d = today()
        val sub = it.sub
        val n = if (it.type == "checklist") it.copy(sub = (sub ?: emptyList()).map { x ->
            val has = x.doneOn.contains(d)
            if (done) (if (!has) x.copy(doneOn = x.doneOn + d) else x) else (if (has) x.copy(doneOn = x.doneOn - d) else x)
        }) else {
            val has = it.doneOn.contains(d)
            if (done) (if (!has) it.copy(doneOn = it.doneOn + d) else it) else (if (has) it.copy(doneOn = it.doneOn - d) else it)
        }
        commit(items.map { x -> if (x.id == id) n else x })
    }

    fun apiRemove(id: String) { commit(items.filter { it.id != id }) }
}
