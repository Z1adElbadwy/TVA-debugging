package com.tva.app

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.text.TextLayoutResult

// ---- palette (same tokens as the HTML :root) -------------------------------
val Bg = Color(0xFF151515)
val Surface = Color(0xFF20201F)
val Circle = Color(0xFF313131)
val Circle2 = Color(0xFF363635)
val Pill = Color(0xFF272727)
val Txt = Color(0xFFF9F9F7)
val Muted = Color(0xFFB0AEA5)
val Ph = Color(0xFF8B8A82)
val Accent = Color(0xFFD97757)
val Terra = Color(0xFFC6613F)
val DrawerBg = Color(0xFF111111)
val Blue = Color(0xFF2A78D6)
val Edge = Color(0xFF343432)
val IconSoft = Color(0xFFD6D4C8)
val NavTxt = Color(0xFFC9C7B8)
val Dim = Color(0xFF8F8D84)
val Done = Color(0xFF6F6E66)

// ---- scale: 1 CSS unit "u" = screen width / 878 ; "q" = screen width / 583 ----
val LocalU = compositionLocalOf { 1f }
val LocalQ = compositionLocalOf { 1f }
val LocalSat = compositionLocalOf { 0.dp }
val LocalSab = compositionLocalOf { 0.dp }

val Number.u: Dp @Composable get() = (toFloat() * LocalU.current).dp
val Number.q: Dp @Composable get() = (toFloat() * LocalQ.current).dp
val Number.us: TextUnit
    @Composable get() = (toFloat() * LocalU.current / LocalDensity.current.fontScale).sp
val Number.qs: TextUnit
    @Composable get() = (toFloat() * LocalQ.current / LocalDensity.current.fontScale).sp

@Composable fun Number.upx(): Float = toFloat() * LocalU.current * LocalDensity.current.density

val EaseOut = CubicBezierEasing(.3f, .8f, .3f, 1f)
val Overshoot = CubicBezierEasing(.3f, 1.25f, .45f, 1f)
val Pop = CubicBezierEasing(.2f, 1.4f, .4f, 1f)

val Ui = FontFamily(Font(R.font.styrene, FontWeight.Bold), Font(R.font.styrene, FontWeight.Normal))
val Serif = FontFamily(Font(R.font.tiempos, FontWeight.Normal), Font(R.font.tiempos, FontWeight.Bold))
val Head = FontFamily(Font(R.font.tiempos_headline, FontWeight.Normal), Font(R.font.tiempos_headline, FontWeight.Bold))

/** Text sized in "u" units like the CSS (font-size: calc(N*var(--u))). */
@Composable
fun T(
    text: String,
    size: Number,
    color: Color = Txt,
    modifier: Modifier = Modifier,
    font: FontFamily = Ui,
    weight: FontWeight = FontWeight.Bold,
    lineHeight: Float = 0f,           // multiplier of font size, 0 = natural
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    align: TextAlign = TextAlign.Unspecified,
    spacing: TextUnit = TextUnit.Unspecified,
    onLayout: ((TextLayoutResult) -> Unit)? = null,
) {
    val fs = size.us
    val lh = if (lineHeight > 0f) (size.toFloat() * lineHeight).us else TextUnit.Unspecified
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = color, fontSize = fs, fontFamily = font, fontWeight = weight, lineHeight = lh,
            letterSpacing = spacing, textAlign = align, textDirection = TextDirection.Content,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
        ),
        maxLines = maxLines, overflow = overflow, onTextLayout = onLayout,
    )
}

/** Serif heading with a thin outline, mimicking -webkit-text-stroke on the hero line. */
@Composable
fun StrokedText(text: String, size: Number, strokeU: Float, color: Color = Txt, modifier: Modifier = Modifier, lineHeight: Float = 0f) {
    val sw = strokeU.upx()
    Box(modifier) {
        val fs = size.us
        val lh = if (lineHeight > 0f) (size.toFloat() * lineHeight).us else TextUnit.Unspecified
        val base = TextStyle(
            fontSize = fs, fontFamily = Serif, fontWeight = FontWeight.Normal, lineHeight = lh, letterSpacing = (-0.01f * size.toFloat() * LocalU.current).sp,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
        )
        BasicText(text, style = base.copy(color = color), maxLines = 1)
        BasicText(text, style = base.copy(color = color, drawStyle = Stroke(width = sw)), maxLines = 1)
    }
}

/** Fades the top / bottom of scrolling content like the CSS mask-image gradient. */
fun Modifier.fadeEdges(topPx: Float, bottomPx: Float): Modifier =
    this.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val h = size.height
            if (h > 0f) {
                val a = (topPx / h).coerceIn(0f, 1f)
                val b = (1f - bottomPx / h).coerceIn(a, 1f)
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to (if (topPx > 0f) Color.Transparent else Color.Black), a to Color.Black, b to Color.Black, 1f to Color.Transparent,
                        startY = 0f, endY = h,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            }
        }

/** Staggered fade/slide-in used by the drawer, list, chess and settings children. */
fun Modifier.stagger(t: Float, delay: Float, dxPx: Float, dyPx: Float): Modifier = this.graphicsLayer {
    val p = if (delay >= 1f) 0f else ((t - delay) / (1f - delay)).coerceIn(0f, 1f)
    alpha = p
    translationX = (1f - p) * dxPx
    translationY = (1f - p) * dyPx
}

val ZeroOffset = Offset.Zero
