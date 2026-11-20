package com.tva.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.ParentDataModifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

// ------------------------------------------------------------ masonry (2 columns) ----
class SpanData(val full: Boolean) : ParentDataModifier {
    override fun Density.modifyParentData(parentData: Any?): Any = this@SpanData
}
fun Modifier.span(full: Boolean) = this.then(SpanData(full))

/** Equivalent of the 6-column "dense" grid with 1px rows: items fill the shortest column. */
@Composable
fun Masonry(gap: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier) { ms, c ->
        val g = gap.roundToPx()
        val w = c.maxWidth
        val colW = (w - g) / 2
        val h = intArrayOf(0, 0)
        val placed = ArrayList<Triple<androidx.compose.ui.layout.Placeable, Int, Int>>(ms.size)
        for (m in ms) {
            val full = (m.parentData as? SpanData)?.full == true
            if (full) {
                val p = m.measure(c.copy(minWidth = w, maxWidth = w, minHeight = 0))
                val y = max(h[0], h[1])
                placed.add(Triple(p, 0, y)); h[0] = y + p.height + g; h[1] = h[0]
            } else {
                val p = m.measure(c.copy(minWidth = colW, maxWidth = colW, minHeight = 0))
                val col = if (h[0] <= h[1]) 0 else 1
                placed.add(Triple(p, col * (colW + g), h[col])); h[col] += p.height + g
            }
        }
        val total = max(0, max(h[0], h[1]) - g)
        layout(w, total) { placed.forEach { (p, x, y) -> p.place(x, y) } }
    }
}

// ------------------------------------------------------------------ small helpers ----
@Composable
fun Modifier.press(onClick: () -> Unit): Modifier =
    this.combinedClickableNoRipple(onClick)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.combinedClickableNoRipple(onClick: () -> Unit, onLong: (() -> Unit)? = null): Modifier =
    this.combinedClickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick, onLongClick = onLong)

/** strike-through that draws left→right over each text line (the `bdi` background-size trick). */
@Composable
fun StrikeText(
    text: String, size: Number, done: Boolean, modifier: Modifier = Modifier, lineHeight: Float = 1.22f,
    doneColor: Color = Done, color: Color = Txt, strikeU: Float = 3.2f,
) {
    val prog by animateFloatAsState(if (done) 1f else 0f, tween(200, easing = CubicBezierEasing(.3f, .7f, .3f, 1f)), label = "strike")
    val col by animateColorAsState(if (done) doneColor else color, tween(350), label = "tcol")
    var lay by remember { mutableStateOf<TextLayoutResult?>(null) }
    val th = strikeU.upx()
    T(
        text, size, col, lineHeight = lineHeight,
        modifier = modifier.drawWithContent {
            drawContent()
            val l = lay
            if (l != null && prog > 0f) {
                for (i in 0 until l.lineCount) {
                    val x0 = l.getLineLeft(i); val x1 = l.getLineRight(i)
                    val y = l.getLineTop(i) + (l.getLineBottom(i) - l.getLineTop(i) - th) * .55f
                    drawRect(col, Offset(x0, y), Size((x1 - x0) * prog, th))
                }
            }
        },
        onLayout = { lay = it },
    )
}

/** Round icon button of the tile: task/note/checklist glyph that morphs into a tick. */
@Composable
fun TickCircle(icon: String, done: Boolean, modifier: Modifier = Modifier, tyU: Float = 42.56f, ckU: Float = 44.8f) {
    val bg by animateColorAsState(if (done) Accent else Circle2, tween(280), label = "ci")
    val fg by animateColorAsState(if (done) Color.White else NavTxt, tween(280), label = "cf")
    val ty by animateFloatAsState(if (done) 0f else 1f, tween(if (done) 160 else 280), label = "ty")
    val ck by animateFloatAsState(if (done) 1f else 0f, tween(300, delayMillis = if (done) 80 else 0), label = "ck")
    val pop = remember { Animatable(1f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(done) {
        if (first) { first = false; return@LaunchedEffect }
        pop.snapTo(.82f); pop.animateTo(1.22f, tween(220, easing = LinearEasing)); pop.animateTo(1f, tween(180))
    }
    Box(
        modifier.size(68.u).graphicsLayer { scaleX = pop.value; scaleY = pop.value }.background(bg, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.graphicsLayer { alpha = ty; val s = .5f + .5f * ty; scaleX = s; scaleY = s }) { Ico(icon, tyU.u, fg) }
        CheckMark(ck, ckU.u, fg)
    }
}

// ------------------------------------------------------------------------- tile ------
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Tile(s: AppState, it: Item, d: String, mt: String?, modifier: Modifier = Modifier) {
    val key = it.id + "|" + d
    val armed = s.armed == key
    val done = isDone(it, d)
    val scope = rememberCoroutineScope()
    var gone by remember { mutableStateOf(false) }
    val goneA by animateFloatAsState(if (gone) 0f else 1f, tween(220), label = "gone")
    val armA by animateFloatAsState(if (armed) 1f else 0f, tween(300), label = "arm")
    val dim by animateFloatAsState(if (armed) .3f else 1f, tween(300), label = "dim")
    val doneA by animateFloatAsState(if (done) .8f else 1f, tween(300), label = "doneA")
    val isCl = it.type == "checklist" && !it.sub.isNullOrEmpty()
    val tx = mt ?: (if (it.time.isNotEmpty()) fmtTime(it.time) else "")
    val bring = remember { BringIntoViewRequester() }
    val hl = remember { Animatable(0f) }
    val focusMe = s.focusId == it.id
    LaunchedEffect(s.hlToken) {
        if (focusMe && s.hlToken > 0) {
            delay(320); bring.bringIntoView()
            delay(300); hl.snapTo(0f); hl.animateTo(1f, tween(2400, easing = LinearEasing)); hl.snapTo(0f)
        }
    }
    val shape = RoundedCornerShape(44.u)
    val hlScale = run {
        val p = hl.value
        if (p <= 0f) 1f else if (p < .14f) 1f + .035f * (p / .14f) else if (p < .30f) 1.035f - .035f * ((p - .14f) / .16f)
        else if (p < .45f) 1f + .03f * ((p - .30f) / .15f) else if (p < .62f) 1.03f - .03f * ((p - .45f) / .17f) else 1f
    }
    val hlA = run { val p = hl.value; if (p <= 0f) 0f else if (p < .14f) p / .14f else if (p < .62f) 1f - .35f * ((p - .14f) / .48f) else (1f - (p - .62f) / .38f).coerceIn(0f, 1f) * .65f }
    val hlR = 44.upx(); val hlSw = 5.upx()
    val tileMod = modifier
        .bringIntoViewRequester(bring)
        .graphicsLayer { alpha = goneA * doneA; val sc = (.9f + .1f * goneA) * hlScale; scaleX = sc; scaleY = sc }
        .drawBehind {
            if (hlA > 0f) {
                val r = hlR
                drawRoundRect(Accent.copy(alpha = .8f * hlA), cornerRadius = androidx.compose.ui.geometry.CornerRadius(r), style = androidx.compose.ui.graphics.drawscope.Stroke(hlSw))
            }
        }
        .clip(shape).background(Surface)
        .let { m ->
            if (isCl) m else m.combinedClickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null,
                onClick = { if (s.armed != null) s.armed = null else s.tapItem(it.id, d) },
                onLongClick = { s.lpFired = true; s.armed = key },
            )
        }
    Box(tileMod) {
        if (isCl) {
            val sub = it.sub!!
            Column(
                Modifier.fillMaxWidth().padding(start = 28.u, end = 28.u, top = 18.u, bottom = 16.u).graphicsLayer { alpha = dim }
                    .combinedClickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
                        onClick = { if (s.armed != null) s.armed = null },
                        onLongClick = { s.lpFired = true; s.armed = key }),
                verticalArrangement = Arrangement.spacedBy(4.u),
            ) {
                sub.forEachIndexed { i, x ->
                    val sd = subDone(x, d)
                    Row(Modifier.fillMaxWidth().heightIn(min = 88.u).padding(vertical = 10.u), horizontalArrangement = Arrangement.spacedBy(18.u), verticalAlignment = Alignment.Top) {
                        Box(Modifier.combinedClickableNoRipple({ if (s.armed != null) s.armed = null else s.toggleSub(it.id, d, i) })) { TickCircle("task", sd) }
                        Box(Modifier.weight(1f).padding(top = 6.u).combinedClickableNoRipple({ if (s.armed != null) s.armed = null else s.toggleSub(it.id, d, i) })) {
                            StrikeText(x.t, 44.8, sd, lineHeight = 1.25f)
                        }
                    }
                }
                if (tx.isNotEmpty()) T(tx, 34.72, Ph, Modifier.align(Alignment.End), lineHeight = 1f)
            }
        } else {
            Column(Modifier.fillMaxWidth().padding(start = 28.u, end = 28.u, top = 24.u, bottom = 22.u).graphicsLayer { alpha = dim }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.u), verticalAlignment = Alignment.Top) {
                    TickCircle(TypeInfo.icon(it.type), done)
                    Box(Modifier.weight(1f).heightIn(min = 68.u).padding(top = 6.6.u)) { StrikeText(it.title, 44.8, done) }
                }
                if (tx.isNotEmpty()) {
                    Row(Modifier.padding(top = 2.u)) {
                        Spacer(Modifier.width(86.u))
                        T(tx, 34.72, Ph, lineHeight = 1f)
                    }
                }
            }
        }
        // armed (long-press) overlay + delete glyph
        if (armA > 0.001f) {
            Box(
                Modifier.matchParentSize().graphicsLayer { alpha = armA; val sc = .94f + .06f * armA; scaleX = sc; scaleY = sc }
                    .background(Brush.linearGradient(listOf(Color(0x61E5484D), Color(0x29E5484D))), shape)
            )
            val wob = rememberInfiniteTransition(label = "wob")
            val rot by wob.animateFloat(-10f, 10f, infiniteRepeatable(tween(350, easing = androidx.compose.animation.core.FastOutSlowInEasing), RepeatMode.Reverse), label = "r")
            Box(
                Modifier.align(Alignment.Center).size(150.u).graphicsLayer { alpha = armA; val sc = .4f + .6f * armA; scaleX = sc; scaleY = sc }
                    .combinedClickableNoRipple({
                        if (armed) {
                            gone = true; s.armed = null; s.mood("fail")
                            scope.launch { delay(240); s.deleteItem(it.id) }
                        }
                    }),
                contentAlignment = Alignment.Center,
            ) { Ico("delete", 100.u, Color(0xFFFF7A7E), stroke = 1.75f, rotate = rot) }
        }
    }
}

