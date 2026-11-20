package com.tva.app

import android.Manifest
import android.graphics.Color as AColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent { Root() }
    }
}

@Composable
private fun Root() {
    val ctx = LocalContext.current
    val d = LocalDensity.current
    val scope = rememberCoroutineScope()
    val s = remember { AppState(ctx) }
    val game = remember { ChessGame(s, scope) }
    val voice = remember { Voice(s, scope, ctx) }
    val sheet = ImageBitmap.imageResource(R.drawable.mascot)
    val ctl = remember { MascotCtl(s, scope, sheet) }

    var permCb by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> permCb?.invoke(ok); permCb = null }
    voice.reqPerm = { cb -> permCb = cb; permLauncher.launch(Manifest.permission.RECORD_AUDIO) }

    val sat = with(d) { WindowInsets.statusBars.getTop(d).toDp() }
    val sab = with(d) { WindowInsets.navigationBars.getBottom(d).toDp() }
    val kbPx = WindowInsets.ime.getBottom(d).toFloat()

    BoxWithConstraints(Modifier.fillMaxSize().background(Bg)) {
        val wDp = maxWidth.value
        val hPx = with(d) { maxHeight.toPx() }
        val wPx = with(d) { maxWidth.toPx() }
        var h1Top by remember { mutableFloatStateOf(Float.NaN) }

        // geometry for the mascot
        ctl.W = wPx; ctl.vis = hPx - kbPx; ctl.kbh = 0f; ctl.h1Top = h1Top
        ctl.u = wPx / 878f; ctl.dens = d.density

        val stackKey = s.stack.joinToString(",")
        LaunchedEffect(wPx > 1f) { if (wPx > 1f) ctl.start() }
        LaunchedEffect(Unit) { s.moods.collect { ctl.onMood(it) } }
        LaunchedEffect(stackKey) {
            ctl.searchCy = s.searchCy
            ctl.onNav()
            delay(100)
            ctl.searchCy = s.searchCy
        }
        LaunchedEffect(game.started) { }

        BackHandler(enabled = s.stack.isNotEmpty()) { s.closeTop() }

        CompositionLocalProvider(LocalU provides wDp / 878f, LocalQ provides wDp / 583f, LocalSat provides sat, LocalSab provides sab) {
            val on = s.stack
            val drawerT by animateFloatAsState(if (on.contains(Ov.Drawer)) 1f else 0f, tween(280, easing = EaseOut), label = "dr")
            val listT by animateFloatAsState(if (on.contains(Ov.List)) 1f else 0f, tween(280, easing = EaseOut), label = "li")
            val chessT by animateFloatAsState(if (on.contains(Ov.Chess)) 1f else 0f, tween(360, easing = EaseOut), label = "ch")
            val setT by animateFloatAsState(if (on.contains(Ov.Settings)) 1f else 0f, tween(300, easing = EaseOut), label = "se")
            val srchT by animateFloatAsState(if (on.contains(Ov.Search)) 1f else 0f, tween(250, easing = EaseOut), label = "sr")
            val sheetOn = on.contains(Ov.Sheet)

            Box(
                Modifier.fillMaxSize().pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val e = awaitPointerEvent(PointerEventPass.Initial)
                            ctl.onPointer(e)
                            if (e.type == PointerEventType.Release) {
                                if (s.lpFired) s.lpFired = false
                                else if (s.armed != null) s.later(16) { s.armed = null }
                            }
                        }
                    }
                },
            ) {
                HomeScreen(s, voice, chessT) { h1Top = it }
                MenuBtn(s, chessT)
                if (drawerT > 0.001f) Drawer(s, drawerT, sab)
                if (listT > 0.001f) ListScreen(s, listT)
                if (chessT > 0.001f) ChessScreen(s, game, chessT)
                PawnBtn(chessT) {
                    if (s.stack.contains(Ov.Chess)) s.stack.remove(Ov.Chess)
                    else { if (!game.started) game.newGame(); s.openO(Ov.Chess) }
                }
                if (sheetOn && !on.contains(Ov.List)) MascotLayer(ctl)
                SheetHost(s, hPx)
                if (srchT > 0.001f) SearchSheet(s, srchT)
                if (setT > 0.001f) SettingsScreen(s, setT)
                AiSheet(s)
                TextSheet(s, Ov.Instr, "Instructions", s.aiInstr, "How should Miss Minutes behave? e.g. Always answer in Arabic. Keep replies to one sentence.") { s.updateAiInstr(it) }
                TextSheet(s, Ov.Ctx, "Context", s.aiCtx, "What should Miss Minutes know about you? e.g. I'm a student, my lectures are on Sundays and Tuesdays.") { s.updateAiCtx(it) }
                if (!sheetOn && !on.contains(Ov.List)) MascotLayer(ctl)
            }
        }
    }
}
