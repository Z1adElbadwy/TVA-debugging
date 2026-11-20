package com.tva.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Voice typing — the mic button flow of the HTML (record → interim transcript every 1.6 s → final). */
class Voice(val s: AppState, val scope: CoroutineScope, val ctx: Context) {
    var text by mutableStateOf("")
    var rec by mutableStateOf(false)
    var ph by mutableStateOf(PH0)
    var reqPerm: ((Boolean) -> Unit) -> Unit = { it(false) }
    private var recorder: VoiceRecorder? = null
    private var base = ""
    private var busy = false
    private var fin = false
    private var ivJob: Job? = null
    private var limJob: Job? = null

    companion object { const val PH0 = "Chat with Miss Minutes…" }

    private fun msg(t: String) { s.say?.invoke(t, "fail", false) }

    fun toggle() {
        if (rec) { stop(); return }
        val key = s.aiKey
        if (key.isEmpty()) { msg("Voice typing needs my API key. Pop it into Settings, then Miss Minutes AI."); s.sfx.toneBad(); return }
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) begin(key)
        else reqPerm { ok -> if (ok) begin(key) else { msg("Microphone access is blocked. Allow it in your settings."); s.sfx.toneBad() } }
    }

    private fun begin(key: String) {
        val r = VoiceRecorder()
        if (!r.start()) { msg("Voice typing isn't available on this device. Tap the mic on your keyboard instead."); s.sfx.toneBad(); s.focusInput?.invoke(); return }
        recorder = r; fin = false; busy = false
        base = if (text.isNotEmpty()) text.trimEnd() + " " else ""
        rec = true; ph = "Listening…"; s.mood("wait"); s.sfx.toneOn()
        ivJob = scope.launch {
            while (true) {
                delay(1600)
                if (busy || fin || !r.hasAudio()) continue
                busy = true
                try { val t = Ai.transcribe(r.wav(), key); if (!fin && t.isNotEmpty()) text = base + t } catch (_: Throwable) {}
                busy = false
            }
        }
        limJob = scope.launch { delay(60000); if (rec) stop() }
    }

    fun stop() {
        val r = recorder ?: return
        recorder = null; fin = true
        ivJob?.cancel(); limJob?.cancel()
        rec = false; s.sfx.toneOff()
        r.stop()
        if (!r.hasAudio()) { ph = PH0; s.mood("idle"); return }
        ph = "Transcribing…"; s.mood("work")
        val key = s.aiKey
        scope.launch {
            try {
                val t = Ai.transcribe(r.wav(), key)
                if (t.isNotEmpty()) { text = base + t; s.sfx.toneDone() } else { msg("I didn't catch anything. Try again."); s.sfx.toneBad() }
            } catch (e: Throwable) {
                val m = e.message ?: e.toString()
                msg(if (m == "lang") "I only understand Arabic and English, sugar." else if (m == "badkey") "That key got turned away, darlin'. Check it in Settings." else "Voice typing couldn't reach the service. (" + m.take(60) + ")")
                s.sfx.toneBad()
            }
            ph = PH0; s.mood("idle")
        }
    }

    fun release() { try { recorder?.stop() } catch (_: Throwable) {}; ivJob?.cancel(); limJob?.cancel() }
}

@Composable
fun HomeScreen(s: AppState, v: Voice, chessT: Float, onH1Top: (Float) -> Unit) {
    val d = LocalDensity.current
    val u = LocalU.current
    val uPx = u * d.density
    val ime = WindowInsets.ime
    val kb = with(d) { ime.getBottom(d).toDp() }
    val fr = remember { FocusRequester() }
    val fm = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        s.focusInput = { try { fr.requestFocus() } catch (_: Throwable) {} }
        onDispose { s.focusInput = null }
    }
    val text = v.text
    val open = text.isNotEmpty() || focused || s.pending != null || v.rec
    val padB by animateDpAsState(
        if (focused || kb > 0.dp) 40.u else maxOf(99.u, LocalSab.current + 36.u), tween(280, easing = EaseOut), label = "cw",
    )

    fun send() {
        val t = v.text.trim(); if (t.isEmpty()) return
        val pt = s.pending
        if (pt != null) { s.pending = null; s.addItem(pt, t) } else s.onSend?.invoke(t)
        v.text = ""; fm.clearFocus()
    }

    Column(Modifier.fillMaxSize().padding(bottom = kb)) {
        Spacer(Modifier.height(LocalSat.current + 140.u))
        // hero
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(top = 78.u)
                .graphicsLayer { alpha = 1f - chessT; translationY = -60f * uPx * chessT },
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            Spacer(Modifier.height(100.u))
            Box(Modifier.padding(top = 30.u).height(80.u).onGloballyPositioned { onH1Top(it.boundsInRoot().top) }, contentAlignment = Alignment.Center) {
                StrokedText("Up late, Ziad?", 64, 1.6f, Txt)
            }
            Spacer(Modifier.height(94.u + 87.u))
        }
        // composer
        Box(
            Modifier.fillMaxWidth().padding(start = 40.u, end = 40.u, bottom = padB)
                .graphicsLayer { alpha = 1f - chessT; translationY = 90f * uPx * chessT },
        ) {
            Composer(s, v, open, fr, { focused = it }, ::send)
        }
    }
}

@Composable
private fun Composer(s: AppState, v: Voice, open: Boolean, fr: FocusRequester, onFocus: (Boolean) -> Unit, send: () -> Unit) {
    val has = v.text.isNotEmpty()
    val showSend = has && !v.rec
    val style = TextStyle(
        color = Txt, fontSize = 45.us, fontFamily = Ui, fontWeight = FontWeight.Bold, lineHeight = 58.us,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )
    val shape = RoundedCornerShape(62.u)
    val pad = if (open) 19.u else 18.u

    @Composable
    fun Plus() = Box(
        Modifier.size(87.u).clip(CircleShape).background(Circle).combinedClickableNoRipple(onClick = { s.openO(Ov.Sheet) }),
        contentAlignment = Alignment.Center,
    ) { Ico("plus", 52.u, Txt, 1.5f) }

    @Composable
    fun Action() {
        val bg = if (v.rec || showSend) Terra else Txt
        val fg = if (v.rec || showSend) Color.White else Color(0xFF161615)
        if (showSend) {
            Box(Modifier.size(87.u).clip(CircleShape).background(bg).combinedClickableNoRipple(onClick = { send() }), contentAlignment = Alignment.Center) { Ico("arrowup", 56.u, fg, 2f) }
        } else {
            val pulse by androidx.compose.animation.core.rememberInfiniteTransition(label = "mp").animateFloat(
                1f, if (v.rec) 1.08f else 1f,
                androidx.compose.animation.core.infiniteRepeatable(tween(600), androidx.compose.animation.core.RepeatMode.Reverse), label = "mpv",
            )
            Box(
                Modifier.size(87.u).graphicsLayer { scaleX = pulse; scaleY = pulse }.clip(CircleShape).background(bg).combinedClickableNoRipple(onClick = { v.toggle() }),
                contentAlignment = Alignment.Center,
            ) { Ico("mic", 52.u, fg, 1.9f) }
        }
    }

    @Composable
    fun Field(m: Modifier) {
        Box(m, contentAlignment = Alignment.TopStart) {
            if (!has) T(v.ph, 45, Ph, Modifier.padding(top = if (open) 22.u else 14.u), lineHeight = 58f / 45f, maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicTextField(
                v.text, { v.text = it },
                Modifier.fillMaxWidth().focusRequester(fr).onFocusChanged { onFocus(it.isFocused); s.focusedInput = it.isFocused; s.mood(if (it.isFocused) "wait" else "idle") }
                    .padding(top = if (open) 22.u else 14.u),
                textStyle = style, cursorBrush = SolidColor(Txt),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                maxLines = 8,
            )
        }
    }

    Box(
        Modifier.fillMaxWidth().clip(shape).background(Surface).border(1.5.dp, Edge, shape)
            .animateContentSize(tween(280, easing = EaseOut))
            .heightIn(min = if (open) 248.u else 124.u).padding(pad),
        contentAlignment = if (open) Alignment.BottomStart else Alignment.CenterStart,
    ) {
        if (open) {
            Column(Modifier.fillMaxWidth()) {
                Field(Modifier.fillMaxWidth().padding(horizontal = 12.u).heightIn(min = 102.u, max = 340.u))
                Spacer(Modifier.height(20.u))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Plus(); Spacer(Modifier.weight(1f)); Action() }
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Plus()
                Field(Modifier.weight(1f).padding(start = 33.u, end = 22.u).height(87.u))
                Action()
            }
        }
    }
}

/** Menu button (top-left); fades while chess is open. */
@Composable
fun MenuBtn(s: AppState, chessT: Float) {
    Box(
        Modifier.padding(top = LocalSat.current).offset(x = (-12).u).size(140.u).graphicsLayer { alpha = 1f - chessT }
            .combinedClickableNoRipple(onClick = { if (chessT < .5f) { s.armed = null; s.openO(Ov.Drawer) } }),
        contentAlignment = Alignment.Center,
    ) { Ico("menu", 62.u, IconSoft, 1.5f) }
}

/** Pawn button (top-right). Drawn above the chess screen; slides to the chess header and fills when chess is open. */
@Composable
fun PawnBtn(chessT: Float, onClick: () -> Unit) {
    val uPx = LocalU.current * LocalDensity.current.density
    Box(
        Modifier.padding(top = LocalSat.current).offset(x = 10.u).size(140.u)
            .graphicsLayer { translationX = -16f * uPx * chessT; translationY = 40f * uPx * chessT }
            .combinedClickableNoRipple(onClick),
        contentAlignment = Alignment.Center,
    ) { Ico("pawn", 70.u, IconSoft, 1.6f, fill = chessT) }
}
