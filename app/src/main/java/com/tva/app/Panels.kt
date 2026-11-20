package com.tva.app

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.delay

@Composable
fun StyledField(
    value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier,
    password: Boolean = false, size: Number = 47.82, lineH: Float = 0f, singleLine: Boolean = true,
    requester: FocusRequester? = null, onFocus: ((Boolean) -> Unit)? = null,
) {
    val fs = size.us
    val st = TextStyle(
        color = Txt, fontSize = fs, fontFamily = Ui, fontWeight = FontWeight.Bold,
        lineHeight = if (lineH > 0f) (size.toFloat() * lineH).us else androidx.compose.ui.unit.TextUnit.Unspecified,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )
    Box(modifier, contentAlignment = Alignment.CenterStart) {
        if (value.isEmpty()) T(placeholder, size, Ph, lineHeight = lineH)
        BasicTextField(
            value, onChange, Modifier.fillMaxWidth().let { m -> if (requester != null) m.focusRequester(requester) else m }
                .let { m -> if (onFocus != null) m.onFocusChangedCompat(onFocus) else m },
            textStyle = st, cursorBrush = SolidColor(Txt), singleLine = singleLine,
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else KeyboardType.Text),
        )
    }
}

fun Modifier.onFocusChangedCompat(f: (Boolean) -> Unit): Modifier = this.onFocusChanged { f(it.isFocused) }

/** A full-height sheet that slides up (translateY .3s) — used by AI settings, instructions and context. */
@Composable
fun BottomPanel(on: Boolean, content: @Composable () -> Unit) {
    val p by animateFloatAsState(if (on) 1f else 0f, tween(300, easing = EaseOut), label = "bp")
    if (p <= 0.001f) return
    val ime = WindowInsets.ime
    val d = LocalDensity.current
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.padding(top = LocalSat.current + 23.u).fillMaxSize()
                .graphicsLayer { translationY = (1f - p) * size.height * 1.05f }
                .clip(RoundedCornerShape(topStart = 92.u, topEnd = 92.u)).background(Bg)
                .verticalScroll(rememberScrollState())
                .padding(bottom = with(d) { ime.getBottom(d).toDp() }),
        ) { content() }
    }
}

@Composable
fun SheetHeadX(title: String, onClose: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(187.u).padding(top = 7.u), contentAlignment = Alignment.Center) {
        CircleBtn(87, Circle, Modifier.align(Alignment.CenterStart).padding(start = 49.5.u), onClose) { Ico("plus", 66.u, Txt, 1.5f, rotate = 45f) }
        T(title, 49.28, Txt, weight = FontWeight.Normal)
    }
}

@Composable
private fun ChevRow(label: String, shape: androidx.compose.ui.graphics.Shape, top: Number, onClick: () -> Unit) {
    Row(
        Modifier.padding(top = top.u, start = 30.u, end = 30.u).fillMaxWidth().height(140.u).clip(shape).background(Surface).combinedClickableNoRipple(onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        T(label, 47.82, Txt, Modifier.padding(start = 50.u).weight(1f))
        Box(Modifier.padding(end = 34.u)) { Ico("chev", 40.u, Ph, 1.75f) }
    }
}

@Composable
fun AiSheet(s: AppState) {
    BottomPanel(s.stack.contains(Ov.Ai)) {
        SheetHeadX("Miss Minutes AI") { s.closeTop() }
        Row(Modifier.padding(top = 30.u, start = 30.u, end = 30.u).fillMaxWidth().height(140.u).clip(RoundedCornerShape(48.u, 48.u, 8.u, 8.u)).background(Surface), verticalAlignment = Alignment.CenterVertically) {
            StyledField(s.aiKey, { s.setAiKey(it) }, "API key", Modifier.padding(horizontal = 50.u).fillMaxWidth(), password = true)
        }
        Row(Modifier.padding(top = 4.u, start = 30.u, end = 30.u).fillMaxWidth().height(140.u).clip(RoundedCornerShape(8.u)).background(Surface), verticalAlignment = Alignment.CenterVertically) {
            StyledField(s.aiModel, { s.setAiModel(it) }, "Model (optional)", Modifier.padding(horizontal = 50.u).fillMaxWidth())
        }
        ChevRow("Instructions", RoundedCornerShape(8.u), 4) { s.openO(Ov.Instr) }
        ChevRow("Context", RoundedCornerShape(8.u, 8.u, 48.u, 48.u), 4) { s.openO(Ov.Ctx) }
    }
}

@Composable
fun TextSheet(s: AppState, ov: Ov, title: String, value: String, placeholder: String, onChange: (String) -> Unit) {
    BottomPanel(s.stack.contains(ov)) {
        SheetHeadX(title) { s.closeTop() }
        Box(Modifier.padding(top = 31.u, start = 30.u, end = 30.u).fillMaxWidth().clip(RoundedCornerShape(48.u)).background(Surface).padding(vertical = 24.u)) {
            StyledField(value, onChange, placeholder, Modifier.padding(horizontal = 40.u).fillMaxWidth(), lineH = 1.35f, singleLine = false)
        }
    }
}

// ------------------------------------------------------------------------- settings ----
@Composable
fun SettingsScreen(s: AppState, p: Float) {
    val dy = 1f
    Column(
        Modifier.fillMaxSize().graphicsLayer { translationY = (1f - p) * size.height }.background(Bg).verticalScroll(rememberScrollState()).padding(top = LocalSat.current),
    ) {
        Row(Modifier.fillMaxWidth().height(182.u).padding(start = 57.u, end = 40.u, top = 38.u), verticalAlignment = Alignment.CenterVertically) {
            T("Settings", 84, Txt, font = Head, weight = FontWeight.Normal, lineHeight = 1f)
            Spacer(Modifier.weight(1f))
            CloseBtn({ s.closeTop() })
        }
        Column(Modifier.padding(top = 20.q, start = 20.q, end = 20.q)) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 111.q).clip(RoundedCornerShape(28.q)).background(Surface)
                    .combinedClickableNoRipple({ s.openO(Ov.Ai) }).padding(start = 24.q, end = 25.q, top = 18.q, bottom = 18.q),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(48.q), contentAlignment = Alignment.Center) { Ico("stopwatch", 48.q, NavTxt, 1.75f) }
                Spacer(Modifier.width(12.q))
                Column {
                    T("Miss Minutes AI", 32.48f * 878f / 583f, Txt, lineHeight = 1.1f)
                    Spacer(Modifier.height(5.q))
                    T(if (s.aiKey.isNotEmpty()) "Key saved" else "Not set", 30.24f * 878f / 583f, Ph, lineHeight = 1f)
                }
            }
        }
    }
}

// --------------------------------------------------------------------------- search ----
@Composable
fun SearchSheet(s: AppState, a: Float) {
    val d = LocalDensity.current
    var q by remember { mutableStateOf("") }
    var ids by remember { mutableStateOf(listOf<String>()) }
    var loading by remember { mutableStateOf(false) }
    var sMood by remember { mutableStateOf("") }
    val fr = remember { FocusRequester() }
    val on = s.stack.contains(Ov.Search)
    LaunchedEffect(on) { if (on) { q = ""; ids = emptyList(); loading = false; delay(620); try { fr.requestFocus() } catch (_: Throwable) {} } }
    LaunchedEffect(q, s.items) {
        val qq = q.trim()
        if (!on) return@LaunchedEffect
        if (qq.isEmpty()) { ids = emptyList(); loading = false; return@LaunchedEffect }
        val loc = localSearch(qq, s.items)
        val hasAi = s.aiKey.isNotEmpty()
        ids = loc; loading = hasAi
        if (!hasAi) return@LaunchedEffect
        delay(450)
        try {
            val r = Ai.search(s, qq, s.items)
            ids = r + loc.filter { it !in r }
        } catch (_: Throwable) { ids = loc }
        loading = false
    }
    LaunchedEffect(q, ids, loading) {
        val qq = q.trim()
        val nm = if (qq.isEmpty()) "" else if (loading) "work" else if (ids.isNotEmpty()) "jump" else "fail"
        if (nm != sMood) { if (nm.isNotEmpty()) s.mood(nm) else if (sMood == "work") s.mood("wave"); sMood = nm }
    }
    val sab = LocalSab.current
    val uPx = LocalU.current * d.density
    val satPx = with(d) { LocalSat.current.toPx() }
    val ox = 76f * uPx
    val imeDp = with(d) { WindowInsets.ime.getBottom(d).toDp() }
    Column(
        Modifier.fillMaxSize()
            .graphicsLayer {
                alpha = a; val sc = .88f + .12f * a; scaleX = sc; scaleY = sc
                transformOrigin = TransformOrigin(1f - ox / size.width, (satPx + 91f * uPx) / size.height)
                translationX = (1f - a) * 30f * uPx; translationY = -(1f - a) * 30f * uPx
            }
            .background(Bg).padding(top = LocalSat.current).padding(bottom = max(imeDp, max(sab, 30.u))),
    ) {
        Box(Modifier.fillMaxWidth().height(182.u)) {
            T("Search", 84, Txt, Modifier.align(Alignment.CenterStart).padding(start = 57.u).offset(y = 18.u), font = Head, weight = FontWeight.Normal, lineHeight = 1f)
            CloseBtn({ s.closeTop() }, Modifier.align(Alignment.TopEnd).padding(end = 32.5.u, top = 66.5.u - 43.5.u + 0.u))
        }
        Row(
            Modifier.padding(top = 34.u, start = 236.u, end = 30.u).fillMaxWidth().height(124.u)
                .border(1.5.dp, Edge, RoundedCornerShape(62.u)).clip(RoundedCornerShape(62.u)).background(Surface)
                .onGloballyPositioned { c -> val p = c.positionInRoot(); s.searchCy = p.y + c.size.height / 2f },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StyledField(q, { q = it }, "Search for anything", Modifier.padding(start = 48.u, end = 20.u).fillMaxWidth(), requester = fr)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            val status = if (q.trim().isEmpty()) "" else if (loading) "Miss Minutes is looking…" else if (ids.isEmpty()) "Nothing found" else ""
            if (status.isNotEmpty()) T(status, 40.32, Dim, Modifier.padding(start = 59.u, end = 59.u, top = 36.u))
            val found = ids.mapNotNull { id -> s.items.firstOrNull { it.id == id } }
            listOf("task", "note", "checklist").forEach { ty ->
                val g = found.filter { it.type == ty }
                if (g.isNotEmpty()) {
                    T(TypeInfo.title(ty), 42.56, Dim, Modifier.padding(start = 59.u, end = 59.u, top = 48.u))
                    g.forEach { it ->
                        Row(
                            Modifier.padding(top = 20.u, start = 30.u, end = 30.u).fillMaxWidth().heightIn(min = 140.u).clip(RoundedCornerShape(48.u)).background(Surface)
                                .combinedClickableNoRipple({
                                    s.closeTop()
                                    val id = it.id
                                    s.later(180) { s.openList("schedule", id) }
                                }).padding(vertical = 20.u),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Spacer(Modifier.width(29.u))
                            Box(Modifier.size(100.u).background(Circle2, CircleShape), contentAlignment = Alignment.Center) { Ico(TypeInfo.icon(it.type), 67.2.u, NavTxt, 1.75f) }
                            Column(Modifier.padding(start = 26.u).weight(1f)) {
                                T(it.title, 47.82, Txt, lineHeight = 1.15f)
                                Spacer(Modifier.height(17.u))
                                T(meta(it, true).ifEmpty { TypeInfo.label(it.type) }, 43.9, Ph, lineHeight = 1f)
                            }
                            Box(Modifier.padding(end = 34.u)) { Ico("chev", 40.u, Ph, 1.75f) }
                        }
                    }
                }
            }
        }
    }
}

