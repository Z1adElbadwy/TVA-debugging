package com.tva.app

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import kotlinx.coroutines.delay

@Composable
fun CircleBtn(size: Number, bg: Color, modifier: Modifier = Modifier, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier.size(size.u).clip(CircleShape).background(bg).combinedClickableNoRipple(onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** The "×" close button used by list/search/settings/chess heads (plus rotated 45°). */
@Composable
fun CloseBtn(onClick: () -> Unit, modifier: Modifier = Modifier) {
    CircleBtn(87, Circle, modifier, onClick) { Ico("plus", 62.u, Txt, 1.5f, rotate = 45f) }
}

@Composable
fun EmptyState(icon: String, title: String, sub: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.alpha(.7f)) { Ico(icon, 150.u, Muted, 1.2f) }
        Spacer(Modifier.height(28.u))
        T(title, 52, Txt, font = Serif, weight = androidx.compose.ui.text.font.FontWeight.Normal)
        Spacer(Modifier.height(12.u))
        Box(Modifier.alpha(.8f)) { T(sub, 36, Muted) }
    }
}

@Composable
fun SectionLabel(text: String, topU: Number = 41, modifier: Modifier = Modifier) {
    T(text, 44.8, Dim, modifier.padding(start = 57.u, top = topU.u, bottom = 14.u))
}

// ----------------------------------------------------------------------------- Drawer ---
@Composable
fun Drawer(s: AppState, t: Float, sab: androidx.compose.ui.unit.Dp) {
    val d = today()
    val dx = -90f * LocalU.current * LocalDensity.current.density
    val items = remember(s.items, d) { s.items.filter { dueOn(it, d) }.sortedWith(byTime) }
    val sc = rememberScrollState()
    val topFade by animateFloatAsState(if (sc.value > 2) 60f else 0f, tween(200), label = "tf")
    val tfPx = topFade.upx()
    Box(
        Modifier.fillMaxSize()
            .graphicsLayer { alpha = t; translationX = (1f - t) * dx }
            .background(DrawerBg)
            .pointerInput(Unit) {
                var acc = 0f
                detectHorizontalDragGestures(onDragStart = { acc = 0f }, onDragEnd = { if (acc < -90.dp.toPx()) s.closeTop() }, onHorizontalDrag = { _, a -> acc += a })
            }
    ) {
        Column(Modifier.fillMaxSize().padding(top = LocalSat.current)) {
            // head
            Row(Modifier.fillMaxWidth().height(182.u).padding(start = 57.u, end = 40.u, top = 38.u), verticalAlignment = Alignment.CenterVertically) {
                val bmp = ImageBitmap.imageResource(R.drawable.tva_logo)
                Box(Modifier.stagger(t, .02f, dx * .78f, 0f).combinedClickableNoRipple({ s.openO(Ov.Settings) })) {
                    Image(bmp, null, Modifier.size(250.u, 111.u).alpha(.55f).blur(9.u, BlurredEdgeTreatment.Unbounded),
                        colorFilter = ColorFilter.tint(Color(0xFFFC8E03), BlendMode.SrcIn), filterQuality = FilterQuality.None)
                    Image(bmp, null, Modifier.size(250.u, 111.u), filterQuality = FilterQuality.None)
                }
                Spacer(Modifier.weight(1f))
                CircleBtn(87, Circle, Modifier.stagger(t, .06f, dx * .78f, 0f), { s.openO(Ov.Search) }) { Ico("search", 60.u, Txt, 1.75f) }
            }
            // nav
            val nav = listOf(Triple("task", "Tasks", "task"), Triple("compose", "Notes", "note"), Triple("checklist", "Checklists", "checklist"))
            nav.forEachIndexed { i, (ic, lb, ty) ->
                Row(
                    Modifier.fillMaxWidth().height(136.5.u).stagger(t, .08f + i * .05f, dx * .78f, 0f)
                        .combinedClickableNoRipple({ s.openList(ty) })
                        .padding(start = 63.u),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(Modifier.width(1.u)); Ico(ic, 78.4.u, NavTxt, 1.75f); Spacer(Modifier.width(22.u))
                    T(lb, 44.8, NavTxt)
                }
            }
            Box(Modifier.padding(top = 19.u, start = 40.u, end = 40.u).fillMaxWidth().height(1.5.dp).background(Color(0xFF262625)).stagger(t, .24f, dx * .78f, 0f))
            Box(Modifier.stagger(t, .28f, dx * .78f, 0f)) { T("Today", 44.8, Dim, Modifier.padding(start = 59.u, top = 41.u).height(119.u)) }
            // today's tiles
            Box(Modifier.weight(1f).fillMaxWidth().stagger(t, .32f, dx * .78f, 0f)) {
                if (items.isEmpty()) EmptyState("sched", "Nothing today", "Tap Schedule to add a session", Modifier.align(Alignment.Center))
                else Column(
                    Modifier.fillMaxSize().fadeEdges(tfPx, 60.upx()).verticalScroll(sc)
                        .padding(start = 40.u, end = 40.u, bottom = 220.u)
                ) {
                    Masonry(18.u, Modifier.fillMaxWidth()) {
                        items.forEach { it -> Tile(s, it, d, null, Modifier.span(tileFull(it))) }
                    }
                }
            }
        }
        // schedule button
        Row(
            Modifier.align(Alignment.BottomStart).padding(start = 39.u, bottom = max(98.u, sab + 36.dp)).stagger(t, .34f, dx * .78f, 0f)
                .size(400.u, 107.u).clip(RoundedCornerShape(54.u)).background(Color(0xFFFAF9F5))
                .combinedClickableNoRipple({ s.openList("schedule") }),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
        ) {
            Ico("sched", 53.76.u, Color(0xFF161615), 1.75f); Spacer(Modifier.width(16.u))
            T("Schedule", 45.92, Color(0xFF161615))
        }
    }
}

// ------------------------------------------------------------------------------- List ---
private val EM = mapOf(
    "task" to Pair("No tasks yet", "Tasks you add will show up here."),
    "note" to Pair("No notes yet", "Jot something down and it will live here."),
    "checklist" to Pair("No checklists yet", "Make a checklist and tick things off here."),
    "schedule" to Pair("Nothing scheduled", "The next two weeks are clear."),
)

private class Grp(val label: String, val rows: List<Triple<Item, String, String?>>)

@Composable
fun ListScreen(s: AppState, t: Float) {
    val type = s.view
    val ty = 30f * LocalU.current * LocalDensity.current.density
    val sc = rememberScrollState()
    val topFade by animateFloatAsState(if (sc.value > 2) 60f else 0f, tween(200), label = "tf")
    val tfPx = topFade.upx()
    val groups = remember(s.items, type, s.focusId) {
        val td = today(); val out = ArrayList<Grp>()
        if (type == "schedule") {
            val od = s.items.filter { !it.daily && it.days.isEmpty() && it.date < td && (!isDone(it, it.date) || it.lateDone) }
                .sortedWith { a, b -> if (a.date == b.date) byTime.compare(a, b) else if (a.date < b.date) -1 else 1 }
                .map { Triple(it, it.date, meta(it, true, it.date) as String?) }
            if (od.isNotEmpty()) out.add(Grp("Overdue", od))
            for (i in 0 until 14) {
                val d = parseD(td).plusDays(i.toLong()).toString()
                val arr = s.items.filter { dueOn(it, d) }.sortedWith(byTime)
                    .map { Triple(it, d, if (it.type == "checklist" && !it.sub.isNullOrEmpty()) meta(it, false, d) else null) }
                if (arr.isNotEmpty()) out.add(Grp(if (i == 0) "Today" else if (i == 1) "Tomorrow" else fmtDate(d), arr))
            }
            val fid = s.focusId
            if (fid != null && out.none { g -> g.rows.any { it.first.id == fid } }) {
                s.items.firstOrNull { it.id == fid }?.let { out.add(Grp(fmtDate(it.date), listOf(Triple(it, it.date, meta(it, true, it.date))))) }
            }
        } else {
            val all = s.items.filter { it.type == type }
                .sortedWith { a, b -> if (a.date == b.date) byTime.compare(a, b) else if (a.date < b.date) -1 else 1 }
                .map { val dd = if (it.daily || it.days.isNotEmpty()) td else it.date; Triple(it, dd, meta(it, true, dd) as String?) }
            if (all.isNotEmpty()) out.add(Grp("", all))
        }
        out
    }
    LaunchedEffect(s.focusId) { if (s.focusId != null) { s.hlToken = 0; delay(10); s.hlToken = 1 } }
    Column(Modifier.fillMaxSize().graphicsLayer { alpha = t; translationY = (1f - t) * ty }.background(DrawerBg).padding(top = LocalSat.current)) {
        Row(Modifier.fillMaxWidth().height(182.u).padding(start = 57.u, end = 40.u, top = 38.u), verticalAlignment = Alignment.CenterVertically) {
            T(if (type == "schedule") "Schedule" else TypeInfo.title(type), 84, Txt, font = Head, weight = androidx.compose.ui.text.font.FontWeight.Normal, lineHeight = 1f)
            Spacer(Modifier.weight(1f))
            CloseBtn({ s.closeTop() })
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (groups.isEmpty()) {
                val e = EM[type] ?: EM["task"]!!
                EmptyState(if (type == "schedule") "sched" else TypeInfo.icon(type), e.first, e.second, Modifier.align(Alignment.Center))
            } else {
                Column(Modifier.fillMaxSize().fadeEdges(tfPx, 60.upx()).verticalScroll(sc).padding(bottom = 220.u)) {
                    groups.forEach { g ->
                        if (g.label.isNotEmpty()) SectionLabel(g.label)
                        Masonry(18.u, Modifier.padding(horizontal = 40.u).fillMaxWidth()) {
                            g.rows.forEach { (it, d, mt) -> Tile(s, it, d, mt, Modifier.span(tileFull(it))) }
                        }
                    }
                }
            }
        }
    }
}
