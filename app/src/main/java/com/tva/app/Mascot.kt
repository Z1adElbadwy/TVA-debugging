package com.tva.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/** Miss Minutes — sprite-sheet mascot (192x208 cells), same behaviours as the HTML overlay. */
class MascotCtl(val s: AppState, val scope: CoroutineScope, val sheet: ImageBitmap) {
    // sprite rows: name -> (row, frames, ms per frame)
    private val sp = mapOf(
        "idle" to Triple(0, 6, 340L), "runR" to Triple(1, 8, 170L), "runL" to Triple(2, 8, 170L), "wave" to Triple(3, 4, 320L),
        "jump" to Triple(4, 5, 230L), "fail" to Triple(5, 8, 260L), "wait" to Triple(6, 6, 300L), "work" to Triple(7, 6, 260L), "review" to Triple(8, 6, 260L),
    )
    private val oneShot = setOf("wave", "jump", "review", "fail")

    // geometry inputs (px) pushed from the composition
    var W = 1f
    var vis = 1f          // visible viewport height minus keyboard
    var kbh = 0f
    var h1Top = Float.NaN
    var searchCy = Float.NaN
    var u = 1f            // px per CSS unit
    var dens = 1f         // px per dp
    var hidden by mutableStateOf(false)

    val pos = Animatable(Offset(-9999f, -9999f), Offset.VectorConverter)
    var mood by mutableStateOf("idle")
    var frame by mutableIntStateOf(0)
    var look by mutableStateOf<Int?>(null)
    var bubble by mutableStateOf("")
    var glitchIn by mutableStateOf(false)
    var glitchP by mutableFloatStateOf(0f)
    var glitchOut by mutableStateOf(false)
    var spriteRect: Rect = Rect.Zero

    private var busy = false         // AI request running (h)
    private var hold = false         // G: hold idle behaviours (typing / speaking)
    private var anchored = true      // w
    private var ba = "home"
    private var moveJob: Job? = null
    private var moodJob: Job? = null
    private var bubbleJob: Job? = null
    private var lookJob: Job? = null
    private var navJob: Job? = null
    private var tpJob: Job? = null
    private val timers = ArrayList<Job>()

    val nl get() = W * 200f / 878f
    val mc get() = 208f * nl / 192f

    private fun later(ms: Long, f: () -> Unit): Job { val j = scope.launch { delay(ms); f() }; timers.add(j); return j }

    fun mode(): String = when {
        s.stack.contains(Ov.Settings) || s.stack.contains(Ov.Search) -> "fix"
        s.stack.contains(Ov.Ai) || s.stack.contains(Ov.Instr) || s.stack.contains(Ov.Ctx) -> "roam"
        s.stack.contains(Ov.Drawer) || s.stack.contains(Ov.List) || s.stack.contains(Ov.Chess) -> "dock"
        else -> "home"
    }
    private fun isAnchored() = mode() != "roam"

    private fun homePos(): Offset {
        val y = if (!h1Top.isNaN()) h1Top - kbh - mc - W * 6f / 878f else vis / 2f
        return Offset((W - nl) / 2f, y)
    }

    private fun target(m: String): Offset {
        if (m == "fix") {
            if (!searchCy.isNaN() && s.stack.contains(Ov.Search)) return Offset(W * 14f / 878f, searchCy - mc / 2f)
            return Offset(W - nl - W * 12f / 878f, vis - mc - W * 8f / 878f)
        }
        if (m != "dock") return homePos()
        val x = if (s.stack.contains(Ov.Chess)) max(0f, W * 12f / 878f) else max(0f, W - nl - W * 12f / 878f)
        return Offset(x, vis - max(W * 98f / 878f, 36f * dens) - mc + W * 14f / 878f)
    }

    private fun randomPos() = Offset(Random.nextFloat() * (W - nl), vis * .15f + Random.nextFloat() * (vis - mc - vis * .15f))

    // ---- sprite loop ----------------------------------------------------------------
    fun applyMood(m: String) {
        mood = m; frame = 0
        moodJob?.cancel()
        val (_, n, ms) = sp[m] ?: return
        moodJob = scope.launch {
            var b = 0
            while (true) {
                delay(ms); b++
                if (m in oneShot && b >= n) { mood = "idle"; frame = 0; moodJob = null; applyMood("idle"); return@launch }
                frame = b % n
            }
        }
    }

    // ---- look direction -------------------------------------------------------------
    private fun lookAt(deg: Double, dur: Long = 2400) {
        if (hold || mood != "idle") return
        look = (((deg % 360 + 360) % 360) / 22.5).roundToInt() % 16
        lookJob?.cancel(); lookJob = scope.launch { delay(dur); look = null }
    }

    fun onPointer(e: PointerEvent) {
        if (busy || mood == "runR" || mood == "runL") return
        val ch = e.changes.firstOrNull() ?: return
        val isMove = e.type == androidx.compose.ui.input.pointer.PointerEventType.Move && ch.pressed
        val isDown = e.type == androidx.compose.ui.input.pointer.PointerEventType.Press
        if (!isMove && !isDown) return
        val p = ch.position
        val r = spriteRect
        if (r != Rect.Zero && r.contains(p)) return
        if (mood != "idle") applyMood("idle")
        val cx = r.left + r.width / 2f; val cy = r.top + r.height / 2f
        val deg = atan2((p.x - cx).toDouble(), (-(p.y - cy)).toDouble()) * 180.0 / PI
        look = (((deg % 360 + 360) % 360) / 22.5).roundToInt() % 16
        lookJob?.cancel()
        lookJob = scope.launch { delay(1600); look = null; if (s.focusedInput) applyMood("wait") }
    }

    // ---- movement ---------------------------------------------------------------------
    private fun run(t: Offset) {
        anchored = false
        val c = pos.value; val d = hypot(t.x - c.x, t.y - c.y)
        val ms = min(7000f, max(1500f, d / (W * .15f) * 1000f)).toInt()
        look = null
        applyMood(if (t.x >= c.x) "runR" else "runL")
        moveJob?.cancel(); moveJob = scope.launch { pos.animateTo(t, tween(ms, easing = LinearEasing)) }
        later(ms.toLong()) { if (mood == "runR" || mood == "runL") applyMood("idle") }
    }

    /** glitch-out, jump to a position, glitch-in */
    private fun teleport(t: Offset, anch: Boolean, wave: Boolean) {
        glitchOut = true
        later(80) {
            anchored = anch; moveJob?.cancel()
            scope.launch { pos.snapTo(t) }
            glitchOut = false; hidden = false; glitchIn = true; glitchP = 0f
            scope.launch {
                val a = Animatable(0f); a.animateTo(1f, tween(300, easing = LinearEasing)) { glitchP = value }
                glitchIn = false
                if (wave && !hold && mood == "idle") applyMood("wave")
            }
        }
    }

    fun placeNow() { scope.launch { pos.snapTo(homePos()) } }

    fun onLayoutChanged() {
        // resize / keyboard: re-anchor (or clamp when roaming)
        moveJob?.cancel()
        scope.launch {
            if (anchored) pos.snapTo(target(ba))
            else {
                val c = pos.value
                pos.snapTo(Offset(c.x.coerceIn(0f, W - nl), c.y.coerceIn(0f, vis - mc)))
            }
        }
    }

    fun onNav() {
        val m = mode()
        if (m == "roam" || m != ba) { hidden = true; tpJob?.cancel(); tpJob = later(320) { if (!glitchIn && !glitchOut) hidden = false } }
        navJob?.cancel()
        navJob = scope.launch {
            delay(140)
            if (hold) { hidden = false; return@launch }
            val b = mode()
            if (b == "roam") { ba = b; teleport(randomPos(), false, true) }
            else if (ba != b || !anchored) { ba = b; teleport(target(b), true, true) }
            else hidden = false
        }
    }

    // ---- speech bubble ------------------------------------------------------------------
    fun say(text: String, act: String, quiet: Boolean) {
        bubble = if (quiet) "" else text
        look = null; applyMood(act)
        bubbleJob?.cancel()
        val a = max(6000, text.length * 110).toLong()
        bubbleJob = scope.launch { delay(a); bubble = "" }
        hold = true
        later(min(a, 6000L)) { if (!busy && mood != "wait") hold = false }
    }

    fun dismissBubble() { bubbleJob?.cancel(); bubble = "" }

    fun onMood(m: String) {
        when (m) {
            "wait" -> { if (!busy) { hold = true; look = null; applyMood("wait") } }
            "idle" -> { if (!busy) { hold = false; if (mood == "wait") applyMood("idle") } }
            else -> { if (!busy) { look = null; applyMood(m) } }
        }
    }

    fun send(msg: String) {
        val ar = Regex("[؀-ۿ]").containsMatchIn(msg)
        if (busy) return
        bubble = ""
        if (aiProvider(s).key.isEmpty()) {
            say(if (ar) "أحتاج مفتاح الـ API أولًا يا عزيزي. ضعه في الإعدادات ثم Miss Minutes AI." else "I need my API key first, sugar. Pop it into Settings, then Miss Minutes AI.", "fail", false)
            return
        }
        busy = true; bubbleJob?.cancel(); hold = true; look = null; applyMood("work"); bubble = "…"
        scope.launch {
            try {
                val r = Ai.chat(s, msg)
                busy = false; say(r.reply, if (r.acted) "jump" else "review", false)
            } catch (e: Throwable) {
                busy = false
                val a = e.message ?: e.toString()
                say(
                    if (a == "badkey") (if (ar) "المفتاح مرفوض يا عزيزي. راجعه في الإعدادات." else "That key got turned away, darlin'. Check it in Settings.")
                    else if (a == "nokey") (if (ar) "أحتاج مفتاح الـ API أولًا يا عزيزي." else "I need my API key first, sugar.")
                    else (if (ar) "الاتصال عندي متعب يا عزيزي. (" else "My connection's acting up, sugar. (") + a.take(80) + ")",
                    "fail", false,
                )
            } finally { busy = false }
        }
    }

    // ---- lifecycle ------------------------------------------------------------------------
    fun start() {
        s.say = { t, a, q -> say(t, a, q) }
        s.onSend = { m -> send(m) }
        applyMood("idle")
        scope.launch { pos.snapTo(homePos()) }
        later(1400) { if (!hold) applyMood("wave") }
        // random idle behaviour every 6–13 s
        scope.launch {
            delay(5000)
            while (true) {
                if (!hold && mood == "idle") {
                    val b = Random.nextFloat(); val a = isAnchored()
                    if (!a && b < .3f) run(randomPos())
                    else if (!a && b < .5f) teleport(randomPos(), false, true)
                    else if (b < .62f) applyMood("jump")
                    else if (b < .74f) applyMood("wave")
                    else lookAt(Random.nextDouble(0.0, 360.0), 2400)
                }
                delay(6000 + (Random.nextFloat() * 7000).toLong())
            }
        }
    }

    fun tapSprite() {
        val top = s.stack.lastOrNull()
        if (top == Ov.Drawer || top == Ov.Settings) s.closeTop()
    }

    fun pressSprite() {
        if (glitchIn || glitchOut) return
        if (isAnchored()) { if (!hold && mood == "idle") applyMood("jump") }
        else teleport(randomPos(), false, true)
    }
}

// glitch keyframes (mmg2): pct, scaleX, scaleY, alpha, dx (css units), dy
private class GK(val p: Float, val sx: Float, val sy: Float, val a: Float, val dx: Float, val dy: Float)
private val GKS = listOf(
    GK(0f, 0f, 0f, 0f, 0f, 0f), GK(.12f, 1.5f, .04f, .8f, 0f, 0f), GK(.26f, 1.12f, .35f, .45f, 0f, 0f),
    GK(.42f, 1f, 1f, .6f, 16f, 0f), GK(.58f, 1f, .92f, 1f, -5f, 3f), GK(.72f, 1f, 1f, .55f, 12f, 0f),
    GK(.86f, 1f, 1f, 1f, -9f, 0f), GK(1f, 1f, 1f, 1f, 0f, 0f),
)

@Composable
fun MascotLayer(ctl: MascotCtl) {
    val d = LocalDensity.current
    val uPx = ctl.u
    val nlPx = ctl.nl; val mcPx = ctl.mc
    val nlDp = with(d) { nlPx.toDp() }; val mcDp = with(d) { mcPx.toDp() }
    if (nlPx < 2f) return
    Box(Modifier.fillMaxSize()) {
        // sprite
        Canvas(
            Modifier
                .offset { IntOffset(ctl.pos.value.x.roundToInt(), ctl.pos.value.y.roundToInt()) }
                .size(nlDp, mcDp)
                .graphicsLayer {
                    alpha = if (ctl.hidden || ctl.glitchOut) 0f else 1f
                    if (ctl.glitchIn) {
                        var k = GKS[0]
                        for (g in GKS) if (g.p <= ctl.glitchP) k = g
                        scaleX = k.sx; scaleY = k.sy; alpha = k.a
                        translationX = k.dx * uPx; translationY = k.dy * uPx
                    }
                }
                .onGloballyPositioned { c ->
                    val p = c.positionInRoot()
                    ctl.spriteRect = Rect(p.x, p.y, p.x + c.size.width, p.y + c.size.height)
                }
                .pointerInput(Unit) {
                    detectTapGestures(onPress = { ctl.pressSprite() }, onTap = { ctl.tapSprite() })
                }
        ) {
            val lk = ctl.look
            val row: Int; val col: Int
            if (lk != null && ctl.mood == "idle") { row = 9 + (if (lk >= 8) 1 else 0); col = lk % 8 }
            else {
                row = when (ctl.mood) { "idle" -> 0; "runR" -> 1; "runL" -> 2; "wave" -> 3; "jump" -> 4; "fail" -> 5; "wait" -> 6; "work" -> 7; else -> 8 }
                val n = when (ctl.mood) { "idle" -> 6; "runR", "runL", "fail" -> 8; "wave" -> 4; "jump" -> 5; else -> 6 }
                col = ctl.frame % n
            }
            drawImage(
                ctl.sheet, srcOffset = IntOffset(col * 192, row * 208), srcSize = IntSize(192, 208),
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()), filterQuality = FilterQuality.None,
            )
        }
        // bubble
        val text = ctl.bubble
        if (text.isNotEmpty() && !ctl.hidden) {
            val maxW = min(560f * uPx, ctl.W - 56f * uPx).roundToInt()
            Box(
                Modifier
                    .layout { m, c ->
                        val p = m.measure(Constraints(maxWidth = maxW.coerceAtLeast(1)))
                        val pos = ctl.pos.value
                        val left = pos.x + nlPx / 2f < ctl.W / 2f
                        val above = pos.y >= ctl.W * 240f / 878f
                        var x = pos.x + (if (left) 0f else nlPx - p.width)
                        val y = pos.y + (if (above) (mcPx * .08f - p.height) else mcPx * .92f)
                        val mg = max(10f * d.density, ctl.W * 14f / 878f)
                        if (x < mg) x = mg else if (x + p.width > ctl.W - mg) x = ctl.W - mg - p.width
                        layout(c.maxWidth, c.maxHeight) { p.place(x.roundToInt(), y.roundToInt()) }
                    }
                    .background(Surface, RoundedCornerShape(48.u))
                    .border(1.5.dp, Edge, RoundedCornerShape(48.u))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { ctl.dismissBubble() }
                    .padding(horizontal = 34.u, vertical = 24.u)
            ) {
                T(text, 38, Txt, lineHeight = 1.3f)
            }
        }
    }
}
