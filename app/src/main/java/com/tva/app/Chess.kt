package com.tva.app

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random

/** Pure chess engine + coaching — direct port of the HTML app (board = 64 strings, "" empty, e.g. "wP"). */

class Cs(val wK: Boolean, val wQ: Boolean, val bK: Boolean, val bQ: Boolean) {
    operator fun get(k: String) = when (k) { "wK" -> wK; "wQ" -> wQ; "bK" -> bK; else -> bQ }
}

class Mv(val from: Int, val to: Int, val promo: String? = null)

class Pos(val b: Array<String>, val ep: Int, val cs: Cs, val cap: String = "")

class Scored(val m: Mv, val v: Int)

class Rating(val c: String, val loss: Int, val best: Mv)

object Chess {
    val PV = mapOf('P' to 100, 'N' to 320, 'B' to 330, 'R' to 500, 'Q' to 900, 'K' to 20000)
    val PN = mapOf('P' to "pawn", 'N' to "knight", 'B' to "bishop", 'R' to "rook", 'Q' to "queen", 'K' to "king")

    private val NK = arrayOf(intArrayOf(-2, -1), intArrayOf(-2, 1), intArrayOf(-1, -2), intArrayOf(-1, 2), intArrayOf(1, -2), intArrayOf(1, 2), intArrayOf(2, -1), intArrayOf(2, 1))
    private val DK = arrayOf(intArrayOf(-1, -1), intArrayOf(-1, 0), intArrayOf(-1, 1), intArrayOf(0, -1), intArrayOf(0, 1), intArrayOf(1, -1), intArrayOf(1, 0), intArrayOf(1, 1))

    fun startBoard(): Array<String> {
        val b = Array(64) { "" }
        val bk = "RNBQKBNR"
        for (i in 0 until 8) { b[i] = "b" + bk[i]; b[8 + i] = "bP"; b[48 + i] = "wP"; b[56 + i] = "w" + bk[i] }
        return b
    }
    val startCs get() = Cs(true, true, true, true)

    private fun g(b: Array<String>, y: Int, x: Int): String = if (y < 0 || y > 7 || x < 0 || x > 7) "#" else b[y * 8 + x]

    fun att(b: Array<String>, sq: Int, by: Char): Boolean {
        val r = sq shr 3; val c = sq and 7; val pd = if (by == 'w') 1 else -1
        val pawn = "${by}P"; val knight = "${by}N"; val king = "${by}K"
        if (g(b, r + pd, c - 1) == pawn || g(b, r + pd, c + 1) == pawn) return true
        for (i in 0 until 8) {
            if (g(b, r + NK[i][0], c + NK[i][1]) == knight || g(b, r + DK[i][0], c + DK[i][1]) == king) return true
            var y = r + DK[i][0]; var x = c + DK[i][1]
            while (g(b, y, x) == "") { y += DK[i][0]; x += DK[i][1] }
            val p = g(b, y, x)
            if (p.isNotEmpty() && p != "#" && p[0] == by &&
                (p[1] == 'Q' || (p[1] == 'R' && (DK[i][0] == 0 || DK[i][1] == 0)) || (p[1] == 'B' && DK[i][0] != 0 && DK[i][1] != 0))
            ) return true
        }
        return false
    }

    fun pseudo(S: Pos, col: Char): MutableList<Mv> {
        val b = S.b; val o = if (col == 'w') 'b' else 'w'; val out = ArrayList<Mv>(40)
        for (i in 0 until 64) {
            val p = b[i]; if (p.isEmpty() || p[0] != col) continue
            val r = i shr 3; val c = i and 7; val t = p[1]
            if (t == 'P') {
                val d = if (col == 'w') -1 else 1; val y = r + d
                if (y in 0..7 && b[y * 8 + c].isEmpty()) {
                    out.add(Mv(i, y * 8 + c))
                    if (r == (if (col == 'w') 6 else 1) && b[(r + 2 * d) * 8 + c].isEmpty()) out.add(Mv(i, (r + 2 * d) * 8 + c))
                }
                for (dx in intArrayOf(-1, 1)) {
                    val x = c + dx; if (x < 0 || x > 7 || y < 0 || y > 7) continue
                    val j = y * 8 + x
                    if ((b[j].isNotEmpty() && b[j][0] == o) || j == S.ep) out.add(Mv(i, j))
                }
            } else if (t == 'N' || t == 'K') {
                for (v in (if (t == 'N') NK else DK)) {
                    val y = r + v[0]; val x = c + v[1]; if (y < 0 || y > 7 || x < 0 || x > 7) continue
                    val j = y * 8 + x
                    if (b[j].isEmpty() || b[j][0] == o) out.add(Mv(i, j))
                }
                if (t == 'K' && i == (if (col == 'w') 60 else 4) && !att(b, i, o)) {
                    if (S.cs["${col}K"] && b[i + 1].isEmpty() && b[i + 2].isEmpty() && b[i + 3] == "${col}R" && !att(b, i + 1, o)) out.add(Mv(i, i + 2))
                    if (S.cs["${col}Q"] && b[i - 1].isEmpty() && b[i - 2].isEmpty() && b[i - 3].isEmpty() && b[i - 4] == "${col}R" && !att(b, i - 1, o)) out.add(Mv(i, i - 2))
                }
            } else {
                for (v in DK) {
                    if (t == 'R' && v[0] != 0 && v[1] != 0) continue
                    if (t == 'B' && (v[0] == 0 || v[1] == 0)) continue
                    var y = r + v[0]; var x = c + v[1]
                    while (y in 0..7 && x in 0..7) {
                        val j = y * 8 + x
                        if (b[j].isEmpty()) out.add(Mv(i, j)) else { if (b[j][0] == o) out.add(Mv(i, j)); break }
                        y += v[0]; x += v[1]
                    }
                }
            }
        }
        return out
    }

    fun doMove(S: Pos, m: Mv): Pos {
        val b = S.b.copyOf(); val p = b[m.from]; val col = p[0]; val t = p[1]
        var wK = S.cs.wK; var wQ = S.cs.wQ; var bK = S.cs.bK; var bQ = S.cs.bQ
        var ep = -1; var cap = b[m.to]
        if (t == 'P' && m.to == S.ep && cap.isEmpty()) {
            val k = m.to + (if (col == 'w') 8 else -8); cap = b[k]; b[k] = ""
        }
        b[m.to] = p; b[m.from] = ""
        if (t == 'P' && ((m.to shr 3) == 0 || (m.to shr 3) == 7)) b[m.to] = "" + col + (m.promo ?: "Q")
        if (t == 'P' && abs(m.to - m.from) == 16) ep = (m.from + m.to) / 2
        if (t == 'K') {
            if (col == 'w') { wK = false; wQ = false } else { bK = false; bQ = false }
            if (m.to - m.from == 2) { b[m.from + 1] = b[m.from + 3]; b[m.from + 3] = "" }
            if (m.to - m.from == -2) { b[m.from - 1] = b[m.from - 4]; b[m.from - 4] = "" }
        }
        if (m.from == 63 || m.to == 63) wK = false
        if (m.from == 56 || m.to == 56) wQ = false
        if (m.from == 7 || m.to == 7) bK = false
        if (m.from == 0 || m.to == 0) bQ = false
        return Pos(b, ep, Cs(wK, wQ, bK, bQ), cap)
    }

    fun king(b: Array<String>, col: Char) = b.indexOf("${col}K")

    fun legal(S: Pos, col: Char): List<Mv> {
        val o = if (col == 'w') 'b' else 'w'
        return pseudo(S, col).filter { m -> val n = doMove(S, m); !att(n.b, king(n.b, col), o) }
    }

    fun eval(b: Array<String>): Int {
        var v = 0.0
        for (i in 0 until 64) {
            val p = b[i]; if (p.isEmpty()) continue
            val r = i shr 3; val c = i and 7; var s = PV[p[1]]!!.toDouble()
            val cen = 3.5 - abs(c - 3.5) + 3.5 - abs(r - 3.5)
            if (p[1] == 'P') s += (if (p[0] == 'b') r else 7 - r) * 8 + cen * 2
            else if (p[1] == 'N' || p[1] == 'B') s += cen * 6
            else if (p[1] == 'Q') s += cen
            v += if (p[0] == 'b') s else -s
        }
        return v.roundToInt()
    }

    /** returns score from Black's point of view */
    fun search(S: Pos, col: Char, depth: Int, al0: Int, be0: Int): Int {
        var al = al0; var be = be0
        val ms = legal(S, col)
        var best = if (col == 'b') -1_000_000_000 else 1_000_000_000
        if (ms.isEmpty()) return if (att(S.b, king(S.b, col), if (col == 'w') 'b' else 'w')) (if (col == 'b') -100_000_000 - depth else 100_000_000 + depth) else 0
        if (depth == 0) return eval(S.b)
        val sorted = ms.sortedByDescending { if (S.b[it.to].isNotEmpty()) 1 else 0 }
        for (m in sorted) {
            val n = doMove(S, m)
            val v = search(n, if (col == 'w') 'b' else 'w', depth - 1, al, be)
            if (col == 'b') { if (v > best) best = v; if (best > al) al = best } else { if (v < best) best = v; if (best < be) be = best }
            if (al >= be) break
        }
        return best
    }

    fun scores(S: Pos, col: Char, d: Int = 1): List<Scored> {
        val o = if (col == 'w') 'b' else 'w'; val sg = if (col == 'b') 1 else -1
        return legal(S, col).map { m -> Scored(m, search(doMove(S, m), o, d, -1_000_000_000, 1_000_000_000) * sg) }
    }

    fun san(S: Pos, m: Mv, legal: List<Mv>): String {
        val p = S.b[m.from]; val t = p[1]; val to = m.to
        val cap = S.b[to].isNotEmpty() || (t == 'P' && to == S.ep)
        val f = "abcdefgh"
        fun sq(i: Int) = "${f[i and 7]}${8 - (i shr 3)}"
        var s: String
        if (t == 'K' && abs(to - m.from) == 2) s = if (to > m.from) "O-O" else "O-O-O"
        else {
            s = if (t == 'P') "" else "$t"
            if (t != 'P' && t != 'K') {
                val amb = legal.filter { it.to == to && it.from != m.from && S.b[it.from] == p }
                if (amb.isNotEmpty()) s += if (amb.all { (it.from and 7) != (m.from and 7) }) "${f[m.from and 7]}"
                else if (amb.all { (it.from shr 3) != (m.from shr 3) }) "${8 - (m.from shr 3)}" else sq(m.from)
            }
            if (cap) s += (if (t == 'P') "${f[m.from and 7]}" else "") + "x"
            s += sq(to)
            if (t == 'P' && ((to shr 3) == 0 || (to shr 3) == 7)) s += "=" + (m.promo ?: "Q")
        }
        val n = doMove(S, m); val o = if (p[0] == 'w') 'b' else 'w'
        if (att(n.b, king(n.b, o), p[0])) s += if (legal(n, o).isNotEmpty()) "+" else "#"
        return s
    }

    fun rate(S: Pos, sc: List<Scored>, m: Mv, book: Boolean): Rating {
        val best = sc.sortedByDescending { it.v }
        val pl = sc.first { it.m.from == m.from && it.m.to == m.to }
        val loss = max(0, best[0].v - pl.v)
        if (sc.size == 1) return Rating("forced", 0, best[0].m)
        val gap = best[0].v - best[1].v; val t = S.b[m.from][1]; val col = S.b[m.from][0]; val o = if (col == 'w') 'b' else 'w'
        var c: String
        if (loss <= 2) {
            val n = doMove(S, m)
            val sac = t != 'P' && t != 'K' && att(n.b, m.to, o) && PV[t]!! > (if (S.b[m.to].isNotEmpty()) PV[S.b[m.to][1]]!! else 0) + 50
            c = if (gap >= 150) (if (sac) "brilliant" else "great") else "best"
        } else if (loss <= 15) c = "excellent" else if (loss <= 40) c = "good"
        else if (loss <= 100) c = "inacc" else if (loss <= 250) c = "mistake" else c = "blunder"
        if (book && loss <= 60) c = "book"
        return Rating(c, loss, best[0].m)
    }

    fun pick(sc0: List<Scored>, skill: Double): Scored {
        val sc = sc0.sortedByDescending { it.v }; val n = sc.size
        if (n == 1) return sc[0]
        val t = (1 - skill).pow(1.4)
        val i = Math.round(t * (n - 1) * .85 + (Random.nextDouble() - .5) * n * .14 * (1.2 - skill)).toInt()
        return sc[i.coerceIn(0, n - 1)]
    }

    fun insufficient(b: Array<String>): Boolean {
        val pc = ArrayList<String>(); val bs = ArrayList<Int>()
        for (i in 0 until 64) {
            val p = b[i]; if (p.isEmpty() || p[1] == 'K') continue
            if (p[1] == 'P' || p[1] == 'R' || p[1] == 'Q') return false
            pc.add(p); if (p[1] == 'B') bs.add(((i shr 3) + (i and 7)) and 1)
        }
        if (pc.size <= 1) return true
        return pc.all { it[1] == 'B' } && bs.all { it == bs[0] }
    }

    val SC = mapOf("brilliant" to 1.0, "great" to 1.0, "best" to 1.0, "book" to .85, "excellent" to .9, "good" to .75, "forced" to .6, "inacc" to .45, "mistake" to .2, "blunder" to 0.0)
    val LB = mapOf("brilliant" to "a brilliant move", "great" to "a great move", "best" to "the best move", "excellent" to "an excellent move", "good" to "a good move", "book" to "a book move", "forced" to "forced", "inacc" to "an inaccuracy", "mistake" to "a mistake", "blunder" to "a blunder")
    val FL = mapOf(
        "brilliant" to listOf("Well, I never saw that coming!", "Brilliant, sugar!"),
        "great" to listOf("Sharp thinking!", "That is the only move that works."),
        "best" to listOf("Right on the mark.", "Exactly what the position asked for."),
        "excellent" to listOf("Mighty fine.", "Very nicely done."),
        "good" to listOf("Solid.", "Steady as she goes."),
        "book" to listOf("Straight out of the playbook.", "Classic opening play."),
        "forced" to listOf("You had no choice there.", "Only one way to go."),
        "inacc" to listOf("Not quite the sharpest.", "There was something a little better."),
        "mistake" to listOf("Oh dear, that gives me a chance.", "That one will cost you."),
        "blunder" to listOf("Oh my, that is a big slip!", "Careful, darlin', that hangs material."),
    )
    val FM = mapOf(
        "brilliant" to listOf("Oh, how daring of me!"), "great" to listOf("I do like that one."), "best" to listOf("That feels right."),
        "excellent" to listOf("Quite pleased with that."), "good" to listOf("Fair enough."), "book" to listOf("Just developing nicely."),
        "forced" to listOf("I had no other way."), "inacc" to listOf("Hmm, not my sharpest."), "mistake" to listOf("Oops, that may cost me."),
        "blunder" to listOf("Goodness, I slipped!"),
    )

    /** returns Pair(richHtml-less text for coach row, plain text for speech). Bold marker is handled by the UI via ** **. */
    fun msgYou(san: String, r: Rating, bestSan: String): Pair<String, String> {
        var h = "**$san** is ${LB[r.c]}. "; var pl = "$san is ${LB[r.c]}. "; val f = FL[r.c]!!.random()
        if (r.c == "inacc" || r.c == "mistake" || r.c == "blunder") { h += "Better was **$bestSan**."; pl += "Better was $bestSan." } else { h += f; pl += f }
        if (san.endsWith("#")) { h += " Checkmate!"; pl += " Checkmate!" } else if (san.endsWith("+")) { h += " Check!"; pl += " Check!" }
        return h to pl
    }

    fun msgMe(san: String, r: Rating, took: String): Pair<String, String> {
        val x = if (took.isNotEmpty()) " taking your $took" else if (san.startsWith("O")) ", castling" else ""
        val f = FM[r.c]!!.random()
        var h = "I play **$san**$x. $f"; var pl = "I play $san$x. $f"
        if (san.endsWith("#")) { h += " Checkmate!"; pl += " Checkmate!" } else if (san.endsWith("+")) { h += " Check!"; pl += " Check!" }
        return h to pl
    }

    val BOOK: List<List<String>> = listOf(
        "e4 e5 Nf3 Nc6 Bb5 a6 Ba4 Nf6 O-O Be7 Re1 b5 Bb3 d6 c3 O-O",
        "e4 e5 Nf3 Nc6 Bc4 Bc5 c3 Nf6 d4 exd4 cxd4 Bb4+",
        "e4 e5 Nf3 Nc6 Bc4 Nf6 d3 Bc5 c3 d6",
        "e4 e5 Nf3 Nc6 Bc4 Nf6 Ng5 d5 exd5 Na5 Bb5+ c6 dxc6 bxc6 Be2 h6",
        "e4 e5 Nf3 Nc6 d4 exd4 Nxd4 Nf6 Nxc6 bxc6 e5 Qe7 Qe2 Nd5 c4 Ba6",
        "e4 e5 Nf3 Nf6 Nxe5 d6 Nf3 Nxe4 d4 d5 Bd3 Nc6 O-O Be7",
        "e4 e5 Nc3 Nf6 f4 d5 fxe5 Nxe4",
        "e4 e5 f4 exf4 Nf3 g5 h4 g4 Ne5 Nf6",
        "e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 a6 Be3 e5 Nb3 Be6",
        "e4 c5 Nf3 Nc6 d4 cxd4 Nxd4 Nf6 Nc3 d6 Bg5 e6 Qd2 Be7",
        "e4 c5 Nf3 e6 d4 cxd4 Nxd4 Nc6 Nc3 Qc7 Be3 a6",
        "e4 c5 Nc3 Nc6 g3 g6 Bg2 Bg7 d3 d6 Be3 Nf6",
        "e4 c5 c3 d5 exd5 Qxd5 d4 Nf6 Nf3 e6 Be2 cxd4",
        "e4 e6 d4 d5 Nc3 Nf6 Bg5 Be7 e5 Nfd7 Bxe7 Qxe7",
        "e4 e6 d4 d5 e5 c5 c3 Nc6 Nf3 Qb6 a3 Nh6",
        "e4 e6 d4 d5 Nd2 Nf6 e5 Nfd7 Bd3 c5 c3 Nc6",
        "e4 c6 d4 d5 Nc3 dxe4 Nxe4 Bf5 Ng3 Bg6 h4 h6 Nf3 Nd7",
        "e4 c6 d4 d5 e5 Bf5 Nf3 e6 Be2 c5 Be3 Nc6",
        "e4 d5 exd5 Qxd5 Nc3 Qa5 d4 Nf6 Nf3 c6 Bc4 Bf5",
        "e4 d6 d4 Nf6 Nc3 g6 f4 Bg7 Nf3 O-O Bd3 Na6",
        "e4 g6 d4 Bg7 Nc3 d6 Nf3 Nf6 Be2 O-O O-O Bg4",
        "d4 d5 c4 e6 Nc3 Nf6 Bg5 Be7 e3 O-O Nf3 h6 Bh4 b6",
        "d4 d5 c4 dxc4 Nf3 Nf6 e3 e6 Bxc4 c5 O-O a6",
        "d4 d5 c4 c6 Nf3 Nf6 Nc3 e6 e3 Nbd7 Bd3 dxc4 Bxc4 b5",
        "d4 Nf6 c4 g6 Nc3 Bg7 e4 d6 Nf3 O-O Be2 e5 O-O Nc6",
        "d4 Nf6 c4 e6 Nc3 Bb4 e3 O-O Bd3 d5 Nf3 c5 O-O Nc6",
        "d4 Nf6 c4 e6 Nf3 b6 g3 Bb7 Bg2 Be7 O-O O-O",
        "d4 Nf6 Nf3 g6 Bf4 Bg7 e3 O-O h3 d6 Be2 Nbd7",
        "d4 d5 Bf4 Nf6 e3 e6 Nf3 Bd6 Bg3 O-O Nbd2 c5",
        "d4 d5 Nf3 Nf6 Bf4 c5 e3 Nc6 c3 Qb6 Qb3 c4",
        "c4 e5 Nc3 Nf6 Nf3 Nc6 g3 d5 cxd5 Nxd5 Bg2 Nb6 O-O Be7",
        "c4 c5 Nf3 Nf6 Nc3 Nc6 g3 g6 Bg2 Bg7 O-O O-O",
        "c4 Nf6 Nc3 e6 e4 d5 e5 d4 exf6 dxc3 bxc3 Qxf6",
        "Nf3 d5 g3 Nf6 Bg2 e6 O-O Be7 d3 O-O Nbd2 c5",
        "Nf3 Nf6 c4 g6 Nc3 Bg7 e4 d6 d4 O-O Be2 e5",
        "e4 e5 Nf3 d6 d4 exd4 Nxd4 Nf6 Nc3 Be7 Be2 O-O O-O Re8",
        "e4 e5 Nf3 Nc6 Bb5 Nf6 O-O Nxe4 d4 Nd6 Bxc6 dxc6 dxe5 Nf5 Qxd8+ Kxd8",
        "e4 e5 Nf3 Nc6 Bb5 Nf6 d3 Bc5 c3 O-O O-O d6 Bg5 h6 Bh4 Be7",
        "e4 e5 Nf3 Nc6 Bb5 f5 Nc3 fxe4 Nxe4 d5 Nxe5 dxe4 Nxc6 Qg5",
    ).map { it.split(" ") }

    fun isBook(h: List<String>) = BOOK.any { l -> l.size >= h.size && h.indices.all { l[it] == h[it] } }
}
