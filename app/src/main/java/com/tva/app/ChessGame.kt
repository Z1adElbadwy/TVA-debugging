package com.tva.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

class CRow(val icon: String, val text: String)   // icon: "b:<badge>" | "p:<piece>" | ""
class Promo(val from: Int, val to: Int, val col: Char)

private class Snap(
    val b: Array<String>, val ep: Int, val cs: Cs, val half: Int, val turn: Char, val keys: Map<String, Int>,
    val hist: List<String>, val last: Pair<Int, Int>, val badge: String, val skill: Double, val ply: Int, val check: String,
)

/** Game controller — direct port of the chess UI logic in the HTML (CH state, chPlay, chExchange …). */
class ChessGame(val s: AppState, private val scope: CoroutineScope) {
    private var gid = 0
    var started by mutableStateOf(false)
    var b by mutableStateOf(Chess.startBoard())
    private var ep = -1
    private var cs = Chess.startCs
    var turn by mutableStateOf('w')
    var sel by mutableIntStateOf(-1)
    var tg by mutableStateOf<List<Int>>(emptyList())
    var last by mutableStateOf(-1 to -1)
    var badge by mutableStateOf("")
    var over by mutableStateOf(false)
    var busy = false
    private var ply = 0
    private var skill = .5
    var rows by mutableStateOf(listOf("" to "", "" to ""))
    var hist by mutableStateOf<List<String>>(emptyList())
    private var keys = HashMap<String, Int>()
    private var half = 0
    var result by mutableStateOf("")
    var say by mutableStateOf("")
    var promo by mutableStateOf<Promo?>(null)
    var check by mutableStateOf("")
    var mode by mutableStateOf("ai")
    var tw by mutableFloatStateOf(600000f)
    var tb by mutableFloatStateOf(600000f)
    private var undo = ArrayList<Snap>()
    private var noAnim = false

    // animation of the last move
    var animFrom by mutableIntStateOf(-1)
    var animTo by mutableIntStateOf(-1)
    var animTick by mutableIntStateOf(0)

    // confirm-style buttons
    var newLbl by mutableStateOf("New game")
    var resignLbl by mutableStateOf("Resign")
    var drawLbl by mutableStateOf("Draw")
    var undoLbl by mutableStateOf("Takeback")
    var armMode by mutableStateOf("")
    private var nw = false; private var rs = false; private var un = false; private var dr = false

    // drag state
    var dragSq by mutableIntStateOf(-1)
    var dragPos by mutableStateOf(Offset.Zero)
    var hv by mutableIntStateOf(-1)
    private var dr_i = -1; private var dr_own = false; private var dr_was = false

    private fun live() = s.stack.contains(Ov.Chess)
    private fun alive(g: Int) = g == gid
    private fun liveG(g: Int) = g == gid && live()
    private fun pos() = Pos(b, ep, cs)

    fun snd(k: String) { s.sfx.play(k) }
    private fun speak(t: String, act: String) { if (live()) s.say?.invoke(t, act, true) }

    private fun key(t: Char): String =
        b.joinToString(",") + t + (if (cs.wK) "K" else "") + (if (cs.wQ) "Q" else "") + (if (cs.bK) "k" else "") + (if (cs.bQ) "q" else "") + (if (ep > -1) "$ep" else "")

    fun newGame() {
        gid++
        mode = s.chMode
        b = Chess.startBoard(); ep = -1; cs = Chess.startCs; turn = 'w'; sel = -1; tg = emptyList(); last = -1 to -1; badge = ""
        over = false; busy = false; ply = 0; skill = .5; rows = listOf("" to "", "" to ""); hist = emptyList(); keys = HashMap(); half = 0
        result = ""; say = ""; promo = null; check = ""; rs = false; tw = 600000f; tb = 600000f; undo = ArrayList(); animFrom = -1
        nw = false; rs = false; un = false; dr = false; armMode = ""
        newLbl = "New game"; resignLbl = "Resign"; undoLbl = "Takeback"; drawLbl = "Draw"
        keys[key('w')] = 1
        started = true
        snd("start")
    }

    /** called every 200 ms by the screen while open */
    fun tick(dt: Float) {
        if (!started || over || !live()) return
        if (turn == 'w') tw -= dt else tb -= dt
        val t = if (turn == 'w') tw else tb
        if (t <= 0f) {
            if (turn == 'w') tw = 0f else tb = 0f
            end("Time out · " + win(if (turn == 'w') 'b' else 'w'), if (turn == 'w') "Time is up, sugar!" else "My clock ran out. Well played!", if (turn == 'w') "jump" else "fail", 0)
        }
    }

    fun win(c: Char) = if (mode == "pvp") (if (c == 'w') "White" else "Black") + " wins" else (if (c == 'w') "You win" else "Miss Minutes wins")

    private fun end(res: String, sp: String, act: String, delayMs: Long) {
        val g = gid
        over = true; busy = false; result = res; say = if (mode == "pvp") "" else sp; sel = -1; tg = emptyList()
        scope.launch { delay(delayMs); if (!liveG(g)) return@launch; snd("end"); if (sp.isNotEmpty() && mode != "pvp") speak(sp, act) }
    }

    private fun status() {
        val col = turn; val o = if (col == 'w') 'b' else 'w'
        val ms = Chess.legal(pos(), col)
        val chk = Chess.att(b, Chess.king(b, col), o)
        check = if (chk) "$col" else ""
        if (ms.isEmpty()) end(
            if (chk) "Checkmate · " + win(o) else "Stalemate · Draw",
            if (chk) (if (col == 'w') "Better luck next time, sugar!" else "Well, I will be. You got me, darlin!") else "A draw. Well, that was a tussle.",
            if (chk && col == 'w') "jump" else "fail", 2600,
        )
        else if ((keys[key(col)] ?: 0) >= 3) end("Draw · threefold repetition", "Same position three times. A draw, sugar.", "idle", 2600)
        else if (half >= 100) end("Draw · fifty-move rule", "Fifty moves with no progress. We call it a draw.", "idle", 2600)
        else if (Chess.insufficient(b)) end("Draw · insufficient material", "Not enough pieces left to mate. A draw.", "idle", 2600)
    }

    private fun sound(pre: Pos, mv: Mv, o: Pos, mine: Boolean) {
        if (!live()) return
        val p = pre.b[mv.from]; val opp = if (p[0] == 'w') 'b' else 'w'
        var k = if (mine) "self" else "opp"
        if (Chess.att(o.b, Chess.king(o.b, opp), p[0])) k = "check"
        else if (p[1] == 'P' && ((mv.to shr 3) == 0 || (mv.to shr 3) == 7)) k = "promote"
        else if (p[1] == 'K' && abs(mv.to - mv.from) == 2) k = "castle"
        else if (o.cap.isNotEmpty()) k = "capture"
        snd(k)
    }

    private fun commit(p: String, mv: Mv, o: Pos, t: Char) {
        animFrom = mv.from; animTo = mv.to; animTick++
        half = if (p[1] == 'P' || o.cap.isNotEmpty()) 0 else half + 1
        b = o.b; ep = o.ep; cs = o.cs; last = mv.from to mv.to; turn = t
        val k = key(t); keys[k] = (keys[k] ?: 0) + 1
    }

    private fun parseRow(t: String) = t   // **bold** handled by the renderer

    fun play(mv: Mv) {
        val pre = pos(); val p = b[mv.from]; val o = Chess.doMove(pre, mv)
        sound(pre, mv, o, if (mode == "pvp") p[0] == 'w' else true)
        undo.add(Snap(b, ep, cs, half, p[0], HashMap(keys), hist.toList(), last, badge, skill, ply, check))
        sel = -1; tg = emptyList(); badge = ""; promo = null
        if (mode == "pvp") {
            commit(p, mv, o, if (p[0] == 'w') 'b' else 'w')
            if (noAnim) { animFrom = -1; noAnim = false }
            val lg0 = Chess.legal(pre, p[0]); val san0 = Chess.san(pre, mv, lg0)
            val rt0 = Chess.rate(pre, Chess.scores(pre, p[0], 1), mv, Chess.isBook(hist + san0))
            badge = rt0.c; hist = hist + san0; ply++
            status(); return
        }
        commit(p, mv, o, 'b')
        if (noAnim) { animFrom = -1; noAnim = false }
        exchange(pre, mv)
    }

    private fun bookMove(lg: List<Mv>): Mv? {
        val h = hist
        val c = Chess.BOOK.filter { l -> l.size > h.size && h.indices.all { l[it] == h[it] } }
        if (c.isEmpty()) return null
        val nx = c.random()[h.size]
        return lg.firstOrNull { Chess.san(pos(), it, lg) == nx }
    }

    private fun exchange(pre: Pos, mv: Mv) {
        val g = gid
        busy = true; status()
        val ended = over
        if (!ended) s.mood("work")
        scope.launch {
            delay(60)
            if (!alive(g) || (over && !ended)) return@launch
            class Y(val san: String, val r: Rating, val bs: String, val ms: Pair<String, String>)
            val y = withContext(Dispatchers.Default) {
                val legal = Chess.legal(pre, 'w'); val san = Chess.san(pre, mv, legal)
                val sc = Chess.scores(pre, 'w')
                val r = Chess.rate(pre, sc, mv, Chess.isBook(hist + san))
                val bs = Chess.san(pre, r.best, legal)
                Y(san, r, bs, Chess.msgYou(san, r, bs))
            }
            if (!alive(g)) return@launch
            hist = hist + y.san; skill = skill * .65 + Chess.SC[y.r.c]!! * .35; badge = y.r.c
            rows = listOf("b:${y.r.c}" to y.ms.first, "" to ""); ply++
            speak(y.ms.second, if (y.r.c == "blunder" || y.r.c == "mistake") "jump" else if (y.r.c == "best" || y.r.c == "great" || y.r.c == "brilliant") "wave" else "idle")
            if (ended) return@launch
            delay(max(1700, y.ms.second.length * 55).toLong())
            if (!alive(g) || over) return@launch
            val cur = pos(); val histNow = hist; val sk = skill
            class Z(val pm: Mv, val r2: Rating, val s2: String, val pc: String, val took: String, val o: Pos)
            val z = withContext(Dispatchers.Default) {
                val lg = Chess.legal(cur, 'b')
                val bm = if (sk > .35) {
                    val c = Chess.BOOK.filter { l -> l.size > histNow.size && histNow.indices.all { l[it] == histNow[it] } }
                    if (c.isEmpty()) null else { val nx = c.random()[histNow.size]; lg.firstOrNull { Chess.san(cur, it, lg) == nx } }
                } else null
                val pm: Mv; val r2: Rating
                if (bm != null) { pm = bm; r2 = Rating("book", 0, bm) }
                else {
                    val sc2 = Chess.scores(cur, 'b', if (sk > .62) 3 else 1)
                    pm = Chess.pick(sc2, sk).m
                    r2 = Chess.rate(cur, sc2, pm, Chess.isBook(histNow + Chess.san(cur, pm, lg)))
                }
                val s2 = Chess.san(cur, pm, lg); val pc = cur.b[pm.from]
                val took = if (cur.b[pm.to].isNotEmpty()) Chess.PN[cur.b[pm.to][1]]!! else if (pc[1] == 'P' && pm.to == cur.ep) "pawn" else ""
                Z(pm, r2, s2, pc, took, Chess.doMove(cur, pm))
            }
            if (!alive(g) || over) return@launch
            sound(cur, z.pm, z.o, false)
            hist = hist + z.s2
            commit(z.pc, z.pm, z.o, 'w'); ply++; badge = z.r2.c
            val m2 = Chess.msgMe(z.s2, z.r2, z.took)
            rows = listOf(rows[0], "b:${z.r2.c}" to m2.first)
            busy = false; status(); speak(m2.second, if (z.took.isNotEmpty()) "jump" else "idle")
        }
    }

    // ---- user input ------------------------------------------------------------------
    private fun can() = started && !over && (mode == "pvp" || (!busy && turn == 'w'))

    fun cancelPromo() { promo = null; sel = -1; tg = emptyList() }

    fun choosePromo(t: String) { val p = promo ?: return; promo = null; play(Mv(p.from, p.to, t)) }

    private fun tryMove(to: Int, dr: Boolean = false) {
        val from = sel
        if (from < 0 || !tg.contains(to)) return
        if (b[from][1] == 'P' && (to shr 3) == (if (b[from][0] == 'w') 0 else 7)) { promo = Promo(from, to, b[from][0]); return }
        if (dr) noAnim = true
        play(Mv(from, to))
    }

    /** returns true when the touch was handled (board interaction started) */
    fun onDown(sq: Int): Boolean {
        if (promo != null) { cancelPromo(); return false }
        if (sq < 0 || !can()) return false
        val own = b[sq].isNotEmpty() && b[sq][0] == turn
        dr_i = sq; dr_own = own; dr_was = sel == sq; hv = -1
        if (own) { sel = sq; tg = Chess.legal(pos(), turn).filter { it.from == sq }.map { it.to } }
        return true
    }

    fun dragStart() { if (dr_own && dr_i >= 0) dragSq = dr_i }
    fun dragMove(p: Offset, sq: Int) { dragPos = p; hv = if (sq > -1 && tg.contains(sq)) sq else -1 }

    fun onUp(dropSq: Int, dragged: Boolean) {
        val i = dr_i; dr_i = -1
        val wasDragging = dragSq >= 0
        dragSq = -1; hv = -1
        if (i < 0) return
        if (dragged && wasDragging) {
            if (dropSq > -1 && dropSq != i && tg.contains(dropSq)) tryMove(dropSq, true)
            else if (dropSq > -1 && dropSq != i) snd("illegal")
            return
        }
        if (!dr_own) {
            if (sel > -1) { if (tg.contains(i)) tryMove(i) else { snd("illegal"); sel = -1; tg = emptyList() } }
            return
        }
        if (dr_was) { sel = -1; tg = emptyList() }
    }

    fun onCancel() { dr_i = -1; dragSq = -1; hv = -1 }

    // ---- buttons ----------------------------------------------------------------------
    fun newAsk() {
        if (!started || over || hist.isEmpty()) { newLbl = "New game"; nw = false; newGame(); return }
        if (!nw) {
            nw = true; newLbl = "Sure?"; val g = gid
            scope.launch { delay(3000); if (g == gid && nw) { nw = false; newLbl = "New game" } }
            return
        }
        newLbl = "New game"; newGame()
    }

    fun resign() {
        if (!started || over) return
        if (!rs) { rs = true; resignLbl = "Sure?"; val g = gid; scope.launch { delay(3000); if (g == gid) { rs = false; resignLbl = "Resign" } }; return }
        if (mode == "pvp") { val rc = turn; end((if (rc == 'w') "White" else "Black") + " resigned · " + win(if (rc == 'w') 'b' else 'w'), "", "idle", 0); return }
        end("You resigned · Miss Minutes wins", "Good game, sugar. Better luck next time!", "jump", 0)
    }

    fun draw() {
        if (started && !over && mode == "pvp") {
            if (!dr) { dr = true; drawLbl = "Both agree?"; val g = gid; scope.launch { delay(3000); if (g == gid) { dr = false; drawLbl = "Draw" } }; return }
            end("Draw · agreed", "", "idle", 0); return
        }
        if (!started || over || busy || turn != 'w') return
        if (Chess.eval(b) <= 40) end("Draw · agreed", "I accept. A fair result, darlin.", "wave", 0)
        else { rows = listOf(rows[0], "b:mistake" to "I decline the draw. I like my position."); speak("I decline the draw. I like my position.", "idle") }
    }

    fun takeback() {
        if (!started || over || busy || (mode != "pvp" && turn != 'w') || undo.isEmpty()) return
        if (!un) { un = true; undoLbl = "Sure?"; val g = gid; scope.launch { delay(3000); if (g == gid && un) { un = false; undoLbl = "Takeback" } }; return }
        un = false; undoLbl = "Takeback"
        val u = undo.removeAt(undo.size - 1)
        b = u.b; ep = u.ep; cs = u.cs; half = u.half; keys = HashMap(u.keys); hist = u.hist; last = u.last; badge = u.badge; skill = u.skill; ply = u.ply
        rows = listOf("" to "", "" to ""); check = u.check; sel = -1; tg = emptyList(); promo = null; turn = u.turn; animFrom = -1
        if (mode == "pvp") return
        rows = listOf("" to "", "b:good" to "Take it back, darlin. Take your time.")
        speak("Take it back, darlin. Take your time.", "wave")
    }

    fun switchMode(m: String) {
        if (!started || mode == m) return
        if (ply > 0 && !over && armMode != m) {
            armMode = m; val g = gid
            scope.launch { delay(3000); if (g == gid) armMode = "" }
            return
        }
        s.setChModePref(m); newGame()
    }

    // ---- derived data for the trays ----------------------------------------------------
    fun tray(col: Char): List<String> {
        val init = mapOf('P' to 8, 'N' to 2, 'B' to 2, 'R' to 2, 'Q' to 1)
        val cnt = HashMap<Char, Int>()
        b.forEach { p -> if (p.isNotEmpty() && p[0] == col && init.containsKey(p[1])) cnt[p[1]] = (cnt[p[1]] ?: 0) + 1 }
        val out = ArrayList<String>()
        for (t in listOf('Q', 'R', 'B', 'N', 'P')) { var n = max(0, init[t]!! - (cnt[t] ?: 0)); while (n > 0) { out.add("$col$t"); n-- } }
        return out
    }

    fun mat(col: Char): Int { var v = 0; b.forEach { p -> if (p.isNotEmpty() && p[0] == col && p[1] != 'K') v += Chess.PV[p[1]]!! }; return v }
}
