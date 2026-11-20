package com.tva.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

private val SheetShape @Composable get() = RoundedCornerShape(topStart = 92.u, topEnd = 92.u)
private val EaseIn = CubicBezierEasing(.5f, 0f, .75f, .4f)

@Composable
fun Scrim(on: Boolean, onClick: () -> Unit) {
    val a by animateFloatAsState(if (on) 1f else 0f, tween(320), label = "scrim")
    if (a > 0.001f) Box(
        Modifier.fillMaxSize().graphicsLayer { alpha = a }.background(Color.Black.copy(alpha = .33f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { if (on) onClick() }
    )
}

@Composable
private fun SheetHead(title: String, back: Boolean, onLeft: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(187.u).padding(top = 7.u), contentAlignment = Alignment.Center) {
        CircleBtn(87, Circle, Modifier.align(Alignment.CenterStart).padding(start = 49.5.u, bottom = 0.u), onLeft) {
            if (back) Ico("back", 64.u, Txt, 1.6f, Modifier.offset(x = 3.u)) else Ico("plus", 66.u, Txt, 1.5f, rotate = 45f)
        }
        T(title, 49.28, Txt, weight = androidx.compose.ui.text.font.FontWeight.Normal)
    }
}

@Composable
private fun Grab() {
    Box(Modifier.fillMaxWidth().padding(top = 12.u), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.size(88.u, 12.u).background(Color(0xFF343332), RoundedCornerShape(6.u)))
    }
}

@Composable
private fun DoneBtn(text: String, alt: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(330.u, 100.u).clip(RoundedCornerShape(54.u)).background(if (alt) Circle2 else Txt).combinedClickableNoRipple(onClick),
        contentAlignment = Alignment.Center,
    ) { T(text, 45.92, if (alt) Txt else Color(0xFF1A1A19)) }
}

@Composable
private fun Toggle(on: Boolean) {
    val bg by animateColorAsState(if (on) Blue else Color(0xFF55544F), tween(200), label = "tg")
    val x by animateDpAsState(if (on) 36.q else 0.q, tween(200), label = "tgx")
    Box(Modifier.size(74.q, 38.q).background(bg, RoundedCornerShape(19.q)).padding(4.q)) {
        Box(Modifier.offset(x = x).size(30.q).background(Color.White, CircleShape))
    }
}

@Composable
private fun SRow(
    icon: String, label: String, sub: String?, shape: androidx.compose.ui.graphics.Shape, h: Number, top: Number,
    onClick: () -> Unit, trail: @Composable () -> Unit,
) {
    Row(
        Modifier.padding(top = top.u, start = 30.u, end = 30.u).fillMaxWidth().height(h.u).clip(shape).background(Surface).combinedClickableNoRipple(onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(29.u))
        Box(Modifier.size(100.u).background(Circle2, CircleShape), contentAlignment = Alignment.Center) { Ico(icon, 78.4.u, NavTxt, 1.75f) }
        Column(Modifier.padding(start = 26.u).weight(1f)) {
            T(label, 47.82, Txt, lineHeight = 1.15f)
            if (sub != null) { Spacer(Modifier.height(17.u)); T(sub, 43.9, Ph, lineHeight = 1f) }
        }
        trail()
    }
}

@Composable
private fun Chev() { Box(Modifier.padding(end = 34.u)) { Ico("chev", 40.u, Ph, 1.75f) } }

// ----------------------------------------------------------------------------- host ----
@Composable
fun SheetHost(s: AppState, vhPx: Float) {
    val d = LocalDensity.current
    val sat = LocalSat.current
    val sab = LocalSab.current
    val st = s.stack
    val addOn = st.contains(Ov.Sheet)
    val pickOn = st.contains(Ov.Pick)
    val daysOn = st.contains(Ov.Days)
    val under = pickOn || daysOn
    val vhDp = with(d) { vhPx.toDp() }
    val fullH = vhDp - sat - 23.u
    val halfH = vhDp * .47f
    var full by remember { mutableStateOf(false) }
    var dragT by remember { mutableStateOf<Float?>(null) }   // dp offset from "base" while dragging
    LaunchedEffect(addOn) { if (addOn) { full = false; dragT = null } }

    // main "Add" sheet
    val enter by animateFloatAsState(
        if (addOn) 1f else 0f,
        if (addOn) tween(540, easing = Overshoot) else tween(260, easing = EaseIn), label = "addEnter",
    )
    val dim by animateFloatAsState(if (under) 1f else 0f, tween(300), label = "under")
    val targetH = if (full) fullH else halfH
    val offDpVal = vhDp.value * .53f - (sat.value + 23f * LocalU.current)
    val animH by animateDpAsState(targetH, tween(300, easing = EaseOut), label = "addH")
    val curH = dragT?.let { (targetH - it.dp).coerceIn(0.dp, fullH) } ?: animH

    Scrim(addOn) { s.closeTop() }

    if (enter > 0.001f) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Box(
                Modifier.fillMaxWidth().height(curH)
                    .graphicsLayer {
                        translationY = (1f - enter.coerceAtMost(1.2f)) * size.height
                        val sc = 1f - .035f * dim; scaleX = sc; scaleY = sc; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(.5f, 1f)
                    }
                    .clip(SheetShape).background(Bg)
                    .drawWithContent { drawContent(); if (dim > 0f) drawRect(Color.Black.copy(alpha = .7f * dim)) }
                    .pointerInput(Unit) {
                        var acc = 0f
                        detectVerticalDragGestures(
                            onDragStart = { acc = 0f; dragT = 0f },
                            onDragEnd = {
                                val dd = acc / density
                                dragT = null
                                if (full) { if (dd > offDpVal + 90f) s.closeTop() else if (dd > 60f) full = false }
                                else { if (dd < -50f) full = true else if (dd > 90f) s.closeTop() }
                            },
                            onDragCancel = { dragT = null },
                            onVerticalDrag = { c, dy -> acc += dy; dragT = (acc / density).coerceAtLeast(if (full) 0f else -(fullH.value - halfH.value)); c.consume() },
                        )
                    },
            ) {
                Box(Modifier.fillMaxWidth().wrapContentHeight(Alignment.Top, unbounded = true)) { AddContent(s) }
                Grab()
            }
        }
    }

    // sub sheets slide up over the dimmed main sheet
    SubSheet(daysOn) { DaysContent(s) }
    SubSheet(pickOn) { PickContent(s) }
}

@Composable
private fun SubSheet(on: Boolean, content: @Composable () -> Unit) {
    val p by animateFloatAsState(if (on) 1f else 0f, tween(300, easing = EaseOut), label = "sub")
    if (p <= 0.001f) return
    val sab = LocalSab.current
    val dy = 50f * LocalU.current * LocalDensity.current.density
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Box(
            Modifier.fillMaxWidth().heightIn(max = 4000.dp).graphicsLayer { alpha = p; translationY = (1f - p) * dy }
                .clip(SheetShape).background(Bg).padding(bottom = maxOf(sab, 44.u))
                .verticalScroll(rememberScrollState())
        ) { Column { Grab(); content() } }
    }
}

// ------------------------------------------------------------------------ add content ----
@Composable
private fun AddContent(s: AppState) {
    val d = s.draft
    Column(Modifier.fillMaxWidth()) {
        SheetHead(if (s.pending != null) "Edit session" else "Add session", false) { s.closeTop() }
        Row(Modifier.padding(horizontal = 30.u), horizontalArrangement = Arrangement.spacedBy(20.u)) {
            listOf(Triple("task", "Task", "task"), Triple("compose", "Note", "note"), Triple("checklist", "Checklist", "checklist")).forEach { (ic, lb, ty) ->
                Column(
                    Modifier.weight(1f).height(253.u).clip(RoundedCornerShape(48.u)).background(Surface)
                        .combinedClickableNoRipple({
                            s.pending = if (s.pending == ty) null else ty
                            s.closeTop()
                            if (s.pending != null) s.requestFocusSoon()
                        }).padding(top = 33.u),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.size(110.u).background(Circle2, CircleShape), contentAlignment = Alignment.Center) { Ico(ic, 84.u, NavTxt, 1.75f) }
                    Spacer(Modifier.height(29.u))
                    T(lb, 43.68, Txt)
                }
            }
        }
        SRow("calendar", "Date", fmtDate(d.date), RoundedCornerShape(48.u), 167, 31, { s.pickMode = "date"; s.openO(Ov.Pick) }) { Chev() }
        SRow("stopwatch", "Time", if (d.time.isNotEmpty()) fmtTime(d.time) else "None", RoundedCornerShape(48.u), 167, 30, { s.pickMode = "time"; s.openO(Ov.Pick) }) { Chev() }
        SRow("refresh", "Repeat daily", null, RoundedCornerShape(48.u, 48.u, 8.u, 8.u), 140, 30,
            { s.draft = d.copy(daily = !d.daily, days = if (!d.daily) emptyList() else d.days) }) { Box(Modifier.padding(end = 34.u)) { Toggle(d.daily) } }
        SRow("caldays", "Repeat on specific days", null, RoundedCornerShape(8.u, 8.u, 48.u, 48.u), 140, 4, { s.openO(Ov.Days) }) { Chev() }
    }
}

// ------------------------------------------------------------------------ days sheet ----
@Composable
private fun DaysContent(s: AppState) {
    val d = s.draft
    val order = listOf(6, 0, 1, 2, 3, 4, 5)
    val pk = order.filter { d.days.contains(it) }
    val sum = if (pk.isEmpty()) "No days picked yet" else if (pk.size == 7) "Every day" else "Every " + pk.joinToString(", ") { DN[it].take(3) }
    Column(Modifier.fillMaxWidth()) {
        SheetHead("Repeat on specific days", true) { s.closeTop() }
        Column(Modifier.padding(horizontal = 30.u).fillMaxWidth().clip(RoundedCornerShape(48.u)).background(Surface).padding(start = 30.u, end = 30.u, top = 40.u, bottom = 34.u)) {
            Row(Modifier.fillMaxWidth().height(132.u).clip(RoundedCornerShape(58.u)).background(Circle2).padding(9.u)) {
                order.forEach { day ->
                    val on = d.days.contains(day)
                    val sc by animateFloatAsState(if (on) 1f else .55f, tween(360, easing = CubicBezierEasing(.3f, 1.5f, .5f, 1f)), label = "dc")
                    val tc by animateColorAsState(if (on) Bg else Txt, tween(200), label = "dt")
                    Box(
                        Modifier.weight(1f).fillMaxHeight().combinedClickableNoRipple({
                            val n = if (on) d.days - day else d.days + day
                            s.draft = d.copy(days = n, daily = if (n.isNotEmpty()) false else d.daily)
                        }),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.padding(horizontal = 3.u).fillMaxSize().graphicsLayer { alpha = if (on) 1f else 0f; scaleX = sc; scaleY = sc }.background(Txt, RoundedCornerShape(46.u)))
                        T(DN[day].take(3), 34, tc)
                    }
                }
            }
            Box(Modifier.padding(start = 10.u, end = 10.u, top = 26.u).heightIn(min = 48.u)) { T(sum, 38, Txt) }
            Box(Modifier.padding(start = 10.u, end = 10.u, top = 26.u)) { T("Repeats on these days until the end of the month.", 32, Ph) }
        }
        Box(Modifier.fillMaxWidth().padding(top = 40.u), contentAlignment = Alignment.Center) { DoneBtn("Done", false) { s.closeTop() } }
    }
}

// ------------------------------------------------------------------------ pick sheet ----
@Composable
private fun PickContent(s: AppState) {
    val date = s.pickMode == "date"
    Column(Modifier.fillMaxWidth().padding(bottom = 50.u)) {
        SheetHead(if (date) "Date" else "Time", true) { s.closeTop() }
        if (date) CalendarPick(s) else TimePick(s)
    }
}

@Composable
private fun CalendarPick(s: AppState) {
    val init = remember { parseD(s.draft.date) }
    var y by remember { mutableIntStateOf(init.year) }
    var m by remember { mutableIntStateOf(init.monthValue - 1) }
    val td = today()
    val first = LocalDate.of(y, m + 1, 1)
    val lead = (dow(first) + 1) % 7
    val days = first.lengthOfMonth()
    Column(Modifier.padding(horizontal = 30.u).fillMaxWidth().clip(RoundedCornerShape(44.u)).background(Surface).padding(start = 16.u, end = 16.u, top = 12.u, bottom = 14.u)) {
        Row(Modifier.fillMaxWidth().height(100.u).padding(start = 24.u, end = 4.u), verticalAlignment = Alignment.CenterVertically) {
            T("${MON[m]} $y", 42.56, Txt, Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(14.u)) {
                CircleBtn(76, Circle2, Modifier, { m--; if (m < 0) { m = 11; y-- } }) { Ico("chev", 44.8.u, Txt, 1.75f, rotate = 180f) }
                CircleBtn(76, Circle2, Modifier, { m++; if (m > 11) { m = 0; y++ } }) { Ico("chev", 44.8.u, Txt, 1.75f) }
            }
        }
        Row(Modifier.fillMaxWidth()) { listOf("S", "S", "M", "T", "W", "T", "F").forEach { Box(Modifier.weight(1f).height(56.u), contentAlignment = Alignment.Center) { T(it, 32.48, Ph) } } }
        val rows = (lead + days + 6) / 7
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val dn = r * 7 + c - lead + 1
                    Box(Modifier.weight(1f).height(78.u), contentAlignment = Alignment.Center) {
                        if (dn in 1..days) {
                            val v = "%04d-%02d-%02d".format(y, m + 1, dn)
                            val sel = v == s.draft.date
                            val sc by animateFloatAsState(if (sel) 1f else .55f, tween(380, easing = CubicBezierEasing(.34f, 1.7f, .5f, 1f)), label = "ds")
                            Box(Modifier.size(68.u).combinedClickableNoRipple({ s.draft = s.draft.copy(date = v) }), contentAlignment = Alignment.Center) {
                                Box(Modifier.fillMaxSize().graphicsLayer { alpha = if (sel) 1f else 0f; scaleX = sc; scaleY = sc }.background(Accent, CircleShape))
                                T("$dn", 35, if (sel) Color.White else if (v == td) Accent else Txt)
                            }
                        }
                    }
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 30.u), horizontalArrangement = Arrangement.Center) { DoneBtn("Done", false) { s.closeTop() } }
}

class WheelState(val n: Int, val loop: Boolean, start: Float) {
    var pos by mutableFloatStateOf(start)
    fun cl(p: Float) = if (loop) p else p.coerceIn(0f, (n - 1).toFloat())
    fun value(): Int { val r = pos.roundToInt(); return if (loop) ((r % n) + n) % n else r.coerceIn(0, n - 1) }
}

@Composable
private fun Wheel(ws: WheelState, items: List<String>, sp: Float, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    val dens = LocalDensity.current
    val R = 90f * LocalU.current * dens.density
    var job by remember { mutableStateOf<Job?>(null) }
    val base by remember { derivedStateOf { ws.pos.roundToInt() } }
    val fr = if (sp > 1f) .965f else .95f
    fun snap(to0: Float) {
        val to = ws.cl(to0); job?.cancel(); val from = ws.pos
        job = scope.launch { animate(0f, 1f, animationSpec = tween(380, easing = Easing { 1f - (1f - it).pow(3) })) { v, _ -> ws.pos = from + (to - from) * v } }
    }
    fun glide(v0: Float) {
        job?.cancel()
        job = scope.launch {
            var vel = v0; var last = withFrameMillis { it }
            while (true) {
                val t = withFrameMillis { it }; val dt = min(32L, t - last).toFloat(); last = t
                ws.pos = ws.cl(ws.pos + vel * dt); vel *= fr.pow(dt / 16f)
                if (abs(vel) < .0006f || (!ws.loop && (ws.pos <= 0f || ws.pos >= ws.n - 1))) { snap(ws.pos.roundToInt().toFloat()); break }
            }
        }
    }
    Box(
        modifier.fillMaxHeight()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(Brush.verticalGradient(0f to Color.Transparent, .3f to Color.Black, .7f to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    job?.cancel()
                    var vel = 0f; var moved = 0f; var lt = down.uptimeMillis; val h = size.height
                    drag(down.id) { ch ->
                        val dy = ch.positionChange().y
                        val dt = max(1L, ch.uptimeMillis - lt).toFloat(); lt = ch.uptimeMillis
                        moved += abs(dy)
                        ws.pos = ws.cl(ws.pos - dy / R * sp)
                        vel = vel * .6f + (-dy / R * sp / dt) * .4f
                        ch.consume()
                    }
                    val vm = .008f * sp; vel = max(-vm, min(vm, vel * .6f))
                    if (moved < 6f * dens.density) snap(ws.pos.roundToInt() + ((down.position.y - h / 2f) / R).roundToInt().toFloat())
                    else if (abs(vel) > .0008f) glide(vel) else snap(ws.pos.roundToInt().toFloat())
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        for (k in 0 until 7) {
            val idx = base + k - 3
            val ok = ws.loop || (idx in 0 until ws.n)
            Box(
                Modifier.fillMaxWidth().height(90.u).graphicsLayer { translationY = (idx - ws.pos) * R },
                contentAlignment = Alignment.Center,
            ) { if (ok) T(items[((idx % ws.n) + ws.n) % ws.n], 44, if (k == 3) Txt else Ph) }
        }
    }
}

@Composable
private fun TimePick(s: AppState) {
    val hs = remember { (1..12).map { "$it" } }
    val ms = remember { (0 until 60).map { "%02d".format(it) } }
    val ap = remember { listOf("AM", "PM") }
    val t = s.draft.time.ifEmpty { java.time.LocalTime.now().let { "%02d:%02d".format(it.hour, it.minute) } }
    val H = t.substring(0, 2).toInt(); val M = t.substring(3, 5).toInt()
    val wh = remember { WheelState(12, true, ((H + 11) % 12).toFloat()) }
    val wm = remember { WheelState(60, true, M.toFloat()) }
    val wa = remember { WheelState(2, false, if (H >= 12) 1f else 0f) }
    Box(Modifier.padding(horizontal = 30.u).fillMaxWidth().height(360.u).clip(RoundedCornerShape(44.u)).background(Surface).padding(horizontal = 30.u)) {
        Box(Modifier.fillMaxWidth().padding(horizontal = (-6).u.coerceAtLeast(0.dp)).offset(y = 135.u).height(90.u).background(Circle2, RoundedCornerShape(30.u)))
        Row(Modifier.fillMaxSize()) {
            Wheel(wh, hs, 1.5f, Modifier.weight(1f))
            Wheel(wm, ms, 1.5f, Modifier.weight(1f))
            Wheel(wa, ap, 1f, Modifier.weight(1f))
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 30.u), horizontalArrangement = Arrangement.spacedBy(20.u, Alignment.CenterHorizontally)) {
        DoneBtn("No time", true) { s.draft = s.draft.copy(time = ""); s.closeTop() }
        DoneBtn("Done", false) {
            val h = wh.value() + 1; val m = wm.value(); val pm = wa.value() == 1
            s.draft = s.draft.copy(time = "%02d:%02d".format((h % 12) + (if (pm) 12 else 0), m)); s.closeTop()
        }
    }
}
