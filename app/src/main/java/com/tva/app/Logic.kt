package com.tva.app

import java.text.DateFormat
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Pure data + date logic (no Android imports) — port of the HTML app's item model. */

data class SubItem(val t: String, val doneOn: List<String> = emptyList())

data class Item(
    val id: String,
    val type: String,           // task | note | checklist
    val title: String,
    val date: String,           // yyyy-MM-dd
    val time: String = "",      // HH:mm or ""
    val daily: Boolean = false,
    val days: List<Int> = emptyList(),   // 0=Sunday..6=Saturday
    val doneOn: List<String> = emptyList(),
    val sub: List<SubItem>? = null,
    val lateDone: Boolean = false,
)

data class Draft(
    val date: String = today(),
    val time: String = "",
    val daily: Boolean = false,
    val days: List<Int> = emptyList(),
)

val DN = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
val MON = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

object TypeInfo {
    fun icon(type: String) = when (type) { "note" -> "compose"; "checklist" -> "checklist"; else -> "task" }
    fun title(type: String) = when (type) { "note" -> "Notes"; "checklist" -> "Checklists"; else -> "Tasks" }
    fun label(type: String) = when (type) { "note" -> "Note"; "checklist" -> "Checklist"; else -> "Task" }
}

fun today(): String = LocalDate.now().toString()
fun parseD(d: String): LocalDate = LocalDate.parse(d)
fun dow(d: LocalDate): Int = d.dayOfWeek.value % 7

fun fmtDate(d: String): String {
    if (d == today()) return "Today"
    val ld = parseD(d)
    val loc = Locale.getDefault()
    val pat = "EEE, MMM d"
    return DateTimeFormatter.ofPattern(pat, loc).format(ld)
}

fun fmtTime(t: String): String {
    val p = t.split(":")
    val lt = LocalTime.of(p[0].toInt(), p[1].toInt())
    val df = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault())
    val c = java.util.Calendar.getInstance(); c.set(2000, 0, 1, lt.hour, lt.minute, 0)
    return df.format(c.time)
}

fun fmtGb(d: String): String = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.UK).format(parseD(d))

fun monthEnd(v: String): String { val p = parseD(v); return p.withDayOfMonth(p.lengthOfMonth()).toString() }

fun dueOn(it: Item, d: String): Boolean {
    if (d < it.date) return false
    if (it.date == d) return true
    if (d > monthEnd(it.date)) return false
    if (it.daily) return true
    return it.days.isNotEmpty() && it.days.contains(dow(parseD(d)))
}

fun subDone(x: SubItem, d: String) = x.doneOn.contains(d)

fun isDone(it: Item, d: String): Boolean {
    val s = it.sub
    if (it.type == "checklist" && !s.isNullOrEmpty()) return s.all { x -> x.doneOn.contains(d) }
    return it.doneOn.contains(d)
}

fun progress(it: Item, d: String): String {
    val q = it.sub ?: return ""
    return if (q.isEmpty()) "" else "${q.count { subDone(it, d) }}/${q.size}"
}

val byTime = Comparator<Item> { a, b ->
    val x = a.time.ifEmpty { "99:99" }; val y = b.time.ifEmpty { "99:99" }
    if (x < y) -1 else 1
}

fun meta(it: Item, withDate: Boolean, d: String? = null): String {
    var m = if (it.time.isNotEmpty()) fmtTime(it.time) else ""
    if (withDate) {
        val r = if (it.daily) "Daily"
        else if (it.days.isNotEmpty()) it.days.sorted().joinToString(" ") { DN[it].take(3) }
        else fmtDate(it.date)
        m = if (m.isNotEmpty()) "$m · $r" else r
    }
    if (it.type == "checklist" && d != null) {
        val pg = progress(it, d)
        if (pg.isNotEmpty()) m = if (m.isNotEmpty()) "$m · $pg" else pg
    }
    return m
}

/** Tile column span class as in the web app (collapsed later to half/full width). */
fun tileFull(it: Item): Boolean {
    val s = it.sub
    if (it.type == "checklist" && !s.isNullOrEmpty()) {
        val ml = s.maxOf { x -> x.t.codePointCount(0, x.t.length) }
        return ml > 24
    }
    val ln = it.title.codePointCount(0, it.title.length)
    return ln > 24
}

fun newId(): String =
    "i" + java.lang.Long.toString(System.currentTimeMillis(), 36) + (1..3).map { "abcdefghijklmnopqrstuvwxyz0123456789".random() }.joinToString("")

/** Port of mkSub: checklist titles with several lines become sub-items. */
fun mkItem(type: String, title: String, date: String, time: String, daily: Boolean, days: List<Int>): Item {
    var t = title
    var sub: List<SubItem>? = null
    if (type == "checklist") {
        val lines = title.split(Regex("\\r?\\n")).map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isNotEmpty()) { t = lines[0]; sub = lines.map { SubItem(it) } }
    }
    return Item(newId(), type, t, date, time, daily, days, emptyList(), sub)
}

// ---------------------------------------------------------------- search ----
private val DIAC = Regex("[ً-ٰۖ-ۭـ]")
fun nz(t: String): String = t.lowercase().replace(DIAC, "")
    .replace(Regex("[أإآٱ]"), "ا").replace('ى', 'ي').replace('ؤ', 'و').replace('ئ', 'ي').replace('ة', 'ه')
    .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")

private val SUFFIX = Regex("(ات|ون|ين|ان|ها|ه|ing|ed|es|s)$")
fun stm(w0: String): String {
    var w = w0
    if (w.length > 4 && w.startsWith("ال")) w = w.substring(2)
    val r = w.replace(SUFFIX, "")
    return if (r.length >= 3) r else w
}

fun lev(a: String, b: String): Int {
    val m = Array(a.length + 1) { IntArray(b.length + 1) }
    for (i in 0..a.length) m[i][0] = i
    for (j in 0..b.length) m[0][j] = j
    for (i in 1..a.length) for (j in 1..b.length)
        m[i][j] = minOf(m[i - 1][j] + 1, m[i][j - 1] + 1, m[i - 1][j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
    return m[a.length][b.length]
}

fun tokOk(q: String, t: String): Boolean {
    if (q == t) return true
    if (q.length >= 3 && t.length >= 3 && (t.startsWith(q) || q.startsWith(t))) return true
    return minOf(q.length, t.length) >= 4 && lev(q, t) <= 1
}

fun localSearch(q: String, items: List<Item>): List<String> {
    val qs = nz(q).split(Regex("\\s+")).filter { it.isNotEmpty() }.map(::stm)
    if (qs.isEmpty()) return emptyList()
    return items.filter { it ->
        val ts = nz(it.title).split(Regex("\\s+")).filter { s -> s.isNotEmpty() }.map(::stm)
        qs.all { x -> ts.any { t -> tokOk(x, t) } }
    }.map { it.id }
}
