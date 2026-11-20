package com.tva.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

private val PCS = mapOf(
    "wb" to R.drawable.pc_wb, "wk" to R.drawable.pc_wk, "wn" to R.drawable.pc_wn, "wp" to R.drawable.pc_wp, "wq" to R.drawable.pc_wq, "wr" to R.drawable.pc_wr,
    "bb" to R.drawable.pc_bb, "bk" to R.drawable.pc_bk, "bn" to R.drawable.pc_bn, "bp" to R.drawable.pc_bp, "bq" to R.drawable.pc_bq, "br" to R.drawable.pc_br,
)
private val BDS = mapOf(
    "brilliant" to R.drawable.bd_brilliant, "great" to R.drawable.bd_great, "best" to R.drawable.bd_best, "excellent" to R.drawable.bd_excellent,
    "good" to R.drawable.bd_good, "book" to R.drawable.bd_book, "inacc" to R.drawable.bd_inacc, "mistake" to R.drawable.bd_mistake,
    "blunder" to R.drawable.bd_blunder, "forced" to R.drawable.bd_forced,
)
private fun pcId(p: String) = PCS[p.lowercase()]!!   // "wP" -> "wp"

private val TrayTxt = Color(0xFF9A978E)
private val ClkBg = Color(0xFF262522)
private val CoachTxt = Color(0xFFC9C7B8)
private val SqDark = Color(0xFF292826)
private val SqLight = Color(0xFF43413D)
private val AccentA = Color(0xFFD97757)

private fun rich(t: String): AnnotatedString = buildAnnotatedString {
    val parts = t.split("**")
    parts.forEachIndexed { i, p -> if (i % 2 == 1) withStyle(SpanStyle(color = Txt, fontWeight = FontWeight.SemiBold)) { append(p) } else append(p) }
}

@Composable
private fun CoachText(text: AnnotatedString, modifier: Modifier = Modifier) {
    androidx.compose.foundation.text.BasicText(
        text, modifier,
        style = androidx.compose.ui.text.TextStyle(
            color = CoachTxt, fontSize = 34.72.us, fontFamily = Ui, fontWeight = FontWeight.Bold, lineHeight = (34.72 * 1.25).us,
            platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
        ),
        maxLines = 2, overflow = TextOverflow.Ellipsis,
    )
}

private fun fmtClock(ms: Float): String {
    val t = Math.max(0, Math.ceil(ms / 1000.0).toInt()); val m = t / 60; val x = t % 60
    return "$m:" + (if (x < 10) "0" else "") + x
}

@Composable
private fun Tray(g: ChessGame, col: Char, adv: Int, clkSide: Char) {
    val ms = if (clkSide == 'w') g.tw else g.tb
    val on = !g.over && g.turn == clkSide
    val lo = ms < 20000f
    Row(Modifier.fillMaxWidth().height(64.u).padding(horizontal = 40.u), verticalAlignment = Alignment.CenterVertically) {
        val pcs = g.tray(col)
        Row(horizontalArrangement = Arrangement.spacedBy((-14).u)) { pcs.forEach { p -> Image(painterResource(pcId(p)), null, Modifier.size(44.u)) } }
        if (adv > 0) T("+" + Math.round(adv / 100f), 33.6, TrayTxt, Modifier.padding(start = 30.u))
        Box(Modifier.weight(1f))
        Box(
            Modifier.clip(RoundedCornerShape(12.u)).background(if (on && lo) Color(0xFFFF6A5C) else if (on) Color(0xFFF1F0EA) else ClkBg)
                .padding(horizontal = 18.u, vertical = 5.u),
        ) { T(fmtClock(ms), 40.32, if (on && lo) Color.White else if (on) Color(0xFF1C1C1A) else if (lo) Color(0xFFFF6A5C) else TrayTxt, weight = FontWeight.SemiBold) }
    }
}

@Composable
private fun CoachRow(icon: String, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().height(96.u), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(22.u)) {
        if (icon.startsWith("b:")) Image(painterResource(BDS[icon.substring(2)] ?: R.drawable.bd_good), null, Modifier.size(60.u))
        else if (icon.startsWith("p:")) Image(painterResource(pcId(icon.substring(2))), null, Modifier.size(60.u))
        content()
    }
}

@Composable
private fun Coach(g: ChessGame) {
    val items = ArrayList<Pair<String, AnnotatedString>>()
    if (g.mode == "pvp") {
        if (!g.over) items.add("p:${g.turn}K" to rich("**" + (if (g.turn == 'w') "White" else "Black") + "** to move" + (if (g.check.isNotEmpty()) " · Check!" else "")))
        if (g.hist.isNotEmpty()) items.add("" to rich("Last move: **" + g.hist.last() + "**"))
    } else {
        g.rows.forEach { (ic, tx) -> if (tx.isNotEmpty()) items.add(ic to rich(tx)) }
    }
    if (g.result.isNotEmpty()) items.add("" to rich("**" + g.result + "**" + (if (g.say.isNotEmpty()) " " + g.say else "")))
    if (items.isEmpty()) items.add("" to rich("Make your first move. I will walk you through every move."))
    Column(Modifier.padding(start = 30.u, end = 30.u, top = 16.u).fillMaxWidth().clip(RoundedCornerShape(44.u)).background(Surface).padding(horizontal = 26.u, vertical = 10.u)) {
        items.forEachIndexed { i, (ic, tx) ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(androidx.compose.ui.unit.Dp(1.5f)).background(Color(0xFF2E2E2C)))
            CoachRow(ic) { CoachText(tx, Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun PillBtn(label: String, modifier: Modifier, bg: Color, fg: Color, h: Number, size: Number, onClick: () -> Unit) {
    Box(modifier.height(h.u).clip(RoundedCornerShape(54.u)).background(bg).press(onClick), contentAlignment = Alignment.Center) {
        T(label, size, fg, maxLines = 1, align = TextAlign.Center)
    }
}

@Composable
fun ChessScreen(s: AppState, g: ChessGame, t: Float) {
    val dens = LocalDensity.current.density
    val u = LocalU.current
    val uPx = u * dens
    val sc = rememberScrollState()
    LaunchedEffect(Unit) { while (true) { delay(200); g.tick(200f) } }
    val dy = 44f * uPx
    fun st(i: Int) = Modifier.stagger(t, (0.04f + 0.04f * i).coerceAtMost(.9f), 0f, dy)

    Column(
        Modifier.fillMaxSize().graphicsLayer { alpha = t; translationY = (1f - t) * 30f * uPx }.background(DrawerBg)
            .padding(top = LocalSat.current).verticalScroll(sc),
    ) {
        Row(st(0).fillMaxWidth().height(182.u).padding(start = 57.u, end = 40.u, top = 38.u), verticalAlignment = Alignment.CenterVertically) {
            T("Chess", 84, Txt, font = Head, weight = FontWeight.Normal, lineHeight = 1f)
        }
        val d = g.mat('b') - g.mat('w')
        Box(st(1)) { Tray(g, 'w', d, 'b') }
        Box(st(2).fillMaxWidth().padding(top = 10.u), contentAlignment = Alignment.TopCenter) { Board(g, uPx) }
        Box(st(3).padding(top = 10.u)) { Tray(g, 'b', -d, 'w') }
        Box(st(4)) { Coach(g) }
        Row(st(5).fillMaxWidth().padding(start = 30.u, end = 30.u, top = 22.u), horizontalArrangement = Arrangement.spacedBy(12.u)) {
            PillBtn(g.newLbl, Modifier.weight(1f), Circle2, Txt, 96, 31) { g.newAsk() }
            PillBtn(g.resignLbl, Modifier.weight(1f), Circle2, Txt, 96, 31) { g.resign() }
            PillBtn(g.drawLbl, Modifier.weight(1f), Circle2, Txt, 96, 31) { g.draw() }
            PillBtn(g.undoLbl, Modifier.weight(1f), Circle2, Txt, 96, 31) { g.takeback() }
        }
        Row(st(6).fillMaxWidth().padding(start = 30.u, end = 30.u, top = 16.u, bottom = 30.u), horizontalArrangement = Arrangement.spacedBy(10.u)) {
            val pv = g.mode == "pvp"
            PillBtn(if (g.armMode == "ai") "Tap to confirm" else "Miss Minutes", Modifier.weight(1f), if (!pv) Txt else Surface, if (!pv) Color(0xFF1A1A19) else Muted, 84, 34.72) { g.setMode("ai") }
            PillBtn(if (g.armMode == "pvp") "Tap to confirm" else "In person", Modifier.weight(1f), if (pv) Txt else Surface, if (pv) Color(0xFF1A1A19) else Muted, 84, 34.72) { g.setMode("pvp") }
        }
    }
}

@Composable
private fun Board(g: ChessGame, uPx: Float) {
    val side = 818f * uPx
    val cell = side / 8f
    val rot = g.mode == "pvp"
    val kp = if (g.check.isNotEmpty()) Chess.king(g.b, g.check[0]) else -1
    fun sqAt(p: Offset): Int {
        if (p.x < 0 || p.y < 0 || p.x >= side || p.y >= side) return -1
        return (p.y / cell).toInt() * 8 + (p.x / cell).toInt()
    }
    Box(
        Modifier.size(818.u).pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()
                val sq = sqAt(down.position)
                if (!g.onDown(sq)) return@awaitEachGesture
                down.consume()
                var dragged = false
                var lastAdj = down.position
                while (true) {
                    val ev = awaitPointerEvent()
                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                    val adj = Offset(ch.position.x, ch.position.y - cell * .95f)
                    lastAdj = adj
                    if (!ch.pressed) { ch.consume(); g.onUp(sqAt(adj), dragged); return@awaitEachGesture }
                    if (!dragged && hypot(ch.position.x - down.position.x, ch.position.y - down.position.y) >= 6f) { dragged = true; g.dragStart() }
                    if (dragged) { ch.consume(); g.dragMove(adj, sqAt(adj)) }
                }
                g.onCancel()
            }
        },
    ) {
        Box(Modifier.size(818.u).clip(RoundedCornerShape(36.u))) {
            Column(Modifier.fillMaxSize()) {
                for (r in 0 until 8) Row(Modifier.weight(1f).fillMaxWidth()) {
                    for (c in 0 until 8) {
                        val i = r * 8 + c
                        Box(Modifier.weight(1f).fillMaxSize()) { Square(g, i, r, c, uPx, cell, rot, i == kp) }
                    }
                }
            }
            // move-quality badge (drawn above neighbours)
            val lt = g.last.second
            if (lt >= 0 && g.badge.isNotEmpty() && BDS[g.badge] != null) {
                val lr = lt / 8; val lc = lt % 8
                Image(
                    painterResource(BDS[g.badge]!!), null,
                    Modifier.size(44.u).offset { IntOffset(((lc + 1) * cell - 32f * uPx).roundToInt(), (lr * cell - 12f * uPx).roundToInt()) },
                )
            }
        }
        // promotion popup
        val pr = g.promo
        if (pr != null) {
            Row(
                Modifier.align(Alignment.Center)
                    .drawBehind { drawRect(Color(0x8C000000), Offset(-4000f, -4000f), Size(size.width + 8000f, size.height + 8000f)) }
                    .clip(RoundedCornerShape(36.u)).background(Surface).padding(14.u),
                horizontalArrangement = Arrangement.spacedBy(6.u),
            ) {
                for (tp in listOf("Q", "R", "B", "N")) {
                    Box(Modifier.size(150.u).clip(RoundedCornerShape(28.u)).background(Circle2).press { g.choosePromo(tp) }, contentAlignment = Alignment.Center) {
                        Image(painterResource(pcId("${pr.col}$tp")), null, Modifier.size(124.u).graphicsLayer { rotationZ = if (rot && pr.col == 'b') 180f else 0f })
                    }
                }
            }
        }
        // dragged piece
        if (g.dragSq >= 0) {
            val p = g.b[g.dragSq]
            if (p.isNotEmpty()) {
                val w = cell * 1.45f
                Image(
                    painterResource(pcId(p)), null,
                    Modifier.size(with(LocalDensity.current) { w.toDp() })
                        .offset { IntOffset((g.dragPos.x - w / 2).roundToInt(), (g.dragPos.y - w / 2).roundToInt()) }
                        .graphicsLayer { rotationZ = if (rot && p[0] == 'b') 180f else 0f },
                )
            }
        }
    }
}

@Composable
private fun Square(g: ChessGame, i: Int, r: Int, c: Int, uPx: Float, cell: Float, rot: Boolean, isKing: Boolean) {
    val p = g.b[i]
    val lt = (r + c) % 2 == 0
    val sel = g.sel == i
    val mv = g.tg.contains(i)
    val cap = mv && p.isNotEmpty()
    val lm = g.last.first == i || g.last.second == i
    val hv = g.hv == i
    val a = AccentA
    val ringU = 5f * uPx; val hvU = 6f * uPx; val dotR = 13f * uPx
    Box(
        Modifier.fillMaxSize()
            .background(if (lt) SqLight else SqDark)
            .drawBehind {
                if (sel) drawRect(a.copy(alpha = .55f))
                if (lm) drawRect(a.copy(alpha = .25f))
                if (isKing) {
                    drawRect(Brush.radialGradient(listOf(Color(0x6BFF4030), Color(0x33FF4030), Color(0x0DFF4030)), Offset(size.width / 2f, size.height / 2f), size.minDimension * .75f))
                }
                if (mv && !cap) drawCircle(a.copy(alpha = .85f), dotR, Offset(size.width / 2f, size.height / 2f))
                if (cap) drawRect(a.copy(alpha = .9f), Offset(ringU / 2, ringU / 2), Size(size.width - ringU, size.height - ringU), style = Stroke(ringU))
                if (hv) drawRect(Color.White.copy(alpha = .75f), Offset(hvU / 2, hvU / 2), Size(size.width - hvU, size.height - hvU), style = Stroke(hvU))
            },
    ) {
        val coCol = if (lt) Color(0xFFAAA79D) else Color(0xFF7D7A72)
        if (c == 0) T("${8 - r}", 22.4, coCol, Modifier.align(Alignment.TopStart).padding(start = 6.u, top = 5.u), weight = FontWeight.SemiBold)
        if (r == 7) T("abcdefgh"[c].toString(), 22.4, coCol, Modifier.align(Alignment.BottomEnd).padding(end = 6.u, bottom = 5.u), weight = FontWeight.SemiBold)
        if (p.isNotEmpty() && g.dragSq != i) {
            val anim = remember(g.animTick, i) { Animatable(1f) }
            val isMover = g.animTo == i && g.animFrom >= 0
            LaunchedEffect(g.animTick, i) {
                if (isMover) { anim.snapTo(0f); anim.animateTo(1f, tween(170, easing = FastOutSlowInEasing)) }
            }
            val fx = ((g.animFrom % 8) - c) * cell
            val fy = ((g.animFrom / 8) - r) * cell
            Image(
                painterResource(pcId(p)), null,
                Modifier.align(Alignment.Center).fillMaxSize(.92f).graphicsLayer {
                    val k = if (isMover) 1f - anim.value else 0f
                    translationX = fx * k; translationY = fy * k
                    rotationZ = if (rot && p[0] == 'b') 180f else 0f
                },
            )
        }
    }
}
