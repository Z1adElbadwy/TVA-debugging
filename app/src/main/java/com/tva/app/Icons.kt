package com.tva.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.geometry.Offset

/** Line icons (24x24 viewBox) — identical geometry to the SVG symbols in the HTML. "F:" prefix = filled shape. */
private fun circ(cx: Double, cy: Double, r: Double) =
    "M${cx - r},${cy}a$r,$r 0 1,0 ${2 * r},0a$r,$r 0 1,0 ${-2 * r},0"

private fun rect(x: Double, y: Double, w: Double, h: Double, r: Double) =
    "M${x + r},${y}h${w - 2 * r}a$r,$r 0 0 1 $r,${r}v${h - 2 * r}a$r,$r 0 0 1 ${-r},${r}h${-(w - 2 * r)}a$r,$r 0 0 1 ${-r},${-r}v${-(h - 2 * r)}a$r,$r 0 0 1 $r,${-r}z"

private val PAWN = "M11.75 2.81C11.56 2.81 11.42 2.84 11.27 2.87C11.12 2.91 10.96 2.95 10.82 3.00C10.67 3.05 10.53 3.12 10.39 3.19C10.25 3.26 10.11 3.34 9.98 3.43C9.85 3.52 9.72 3.62 9.60 3.73C9.48 3.83 9.37 3.95 9.27 4.07C9.16 4.20 9.07 4.33 8.99 4.46C8.91 4.60 8.83 4.73 8.77 4.88C8.70 5.02 8.65 5.16 8.60 5.31C8.56 5.46 8.52 5.62 8.49 5.77C8.47 5.93 8.45 6.09 8.44 6.25C8.44 6.41 8.44 6.55 8.47 6.74C8.49 6.92 8.55 7.18 8.60 7.36C8.65 7.53 8.70 7.65 8.76 7.79C8.83 7.94 8.90 8.07 8.98 8.21C9.06 8.35 9.16 8.47 9.24 8.61C9.31 8.74 9.41 8.88 9.43 9.01C9.45 9.14 9.42 9.28 9.36 9.40C9.30 9.52 9.16 9.62 9.05 9.72C8.93 9.82 8.79 9.90 8.66 9.99C8.53 10.08 8.39 10.17 8.27 10.26C8.15 10.36 8.02 10.45 7.95 10.57C7.88 10.69 7.85 10.83 7.85 10.97C7.84 11.11 7.89 11.28 7.94 11.41C8.00 11.55 8.04 11.69 8.17 11.79C8.30 11.88 8.53 11.95 8.71 11.99C8.88 12.03 9.04 12.00 9.20 12.02C9.35 12.04 9.52 12.06 9.64 12.13C9.75 12.20 9.84 12.32 9.88 12.44C9.93 12.57 9.91 12.73 9.89 12.88C9.88 13.03 9.83 13.19 9.79 13.34C9.75 13.49 9.70 13.64 9.64 13.78C9.59 13.93 9.53 14.07 9.47 14.22C9.40 14.36 9.33 14.50 9.25 14.63C9.17 14.77 9.09 14.90 9.00 15.04C8.91 15.17 8.81 15.30 8.71 15.42C8.61 15.55 8.50 15.67 8.39 15.79C8.27 15.91 8.18 16.01 8.03 16.14C7.89 16.27 7.68 16.44 7.53 16.56C7.38 16.67 7.27 16.75 7.14 16.84C7.01 16.94 6.88 17.02 6.75 17.12C6.62 17.21 6.49 17.31 6.37 17.42C6.25 17.52 6.13 17.64 6.03 17.76C5.92 17.88 5.83 18.01 5.74 18.14C5.65 18.27 5.57 18.41 5.49 18.55C5.42 18.69 5.35 18.83 5.29 18.97C5.23 19.11 5.18 19.26 5.13 19.41C5.09 19.56 5.05 19.71 5.01 19.87C4.98 20.02 4.96 20.18 4.94 20.34C4.92 20.50 4.90 20.68 4.91 20.82C4.92 20.97 4.90 21.12 5.00 21.21C5.10 21.31 5.33 21.36 5.50 21.39C5.66 21.42 5.83 21.40 6.00 21.40L18.31 21.40C18.49 21.38 18.76 21.38 18.89 21.31C19.02 21.24 19.05 21.13 19.08 21.00C19.11 20.87 19.09 20.69 19.08 20.53C19.07 20.38 19.04 20.21 19.01 20.06C18.98 19.90 18.95 19.74 18.91 19.59C18.87 19.44 18.82 19.29 18.77 19.15C18.71 19.00 18.65 18.86 18.58 18.72C18.51 18.58 18.44 18.44 18.35 18.30C18.27 18.17 18.18 18.04 18.08 17.91C17.98 17.79 17.88 17.66 17.76 17.55C17.65 17.44 17.52 17.34 17.40 17.24C17.27 17.14 17.14 17.05 17.01 16.96C16.88 16.87 16.76 16.79 16.62 16.68C16.47 16.56 16.26 16.39 16.11 16.27C15.97 16.14 15.87 16.04 15.75 15.92C15.63 15.80 15.52 15.68 15.41 15.56C15.30 15.44 15.19 15.31 15.09 15.18C15.00 15.05 14.91 14.92 14.82 14.79C14.74 14.65 14.66 14.52 14.59 14.38C14.52 14.24 14.46 14.10 14.40 13.95C14.34 13.81 14.28 13.66 14.24 13.51C14.19 13.36 14.14 13.21 14.11 13.06C14.08 12.91 14.04 12.74 14.06 12.61C14.08 12.47 14.12 12.32 14.21 12.23C14.30 12.13 14.45 12.08 14.59 12.04C14.74 12.01 14.90 12.03 15.08 12.01C15.26 11.98 15.53 11.96 15.68 11.89C15.82 11.81 15.89 11.70 15.96 11.57C16.03 11.45 16.08 11.29 16.11 11.14C16.13 11.00 16.13 10.83 16.08 10.70C16.04 10.57 15.94 10.46 15.83 10.36C15.73 10.26 15.58 10.18 15.45 10.09C15.32 10.00 15.18 9.91 15.06 9.82C14.93 9.73 14.79 9.64 14.70 9.53C14.61 9.42 14.53 9.29 14.52 9.16C14.51 9.03 14.57 8.89 14.63 8.76C14.70 8.62 14.81 8.50 14.90 8.36C14.98 8.23 15.07 8.10 15.14 7.96C15.21 7.82 15.27 7.70 15.33 7.53C15.38 7.36 15.45 7.10 15.48 6.92C15.51 6.74 15.52 6.60 15.52 6.43C15.52 6.27 15.52 6.10 15.50 5.94C15.48 5.78 15.46 5.62 15.42 5.47C15.38 5.32 15.33 5.17 15.28 5.02C15.22 4.88 15.15 4.74 15.08 4.60C15.00 4.46 14.91 4.33 14.82 4.20C14.72 4.08 14.61 3.95 14.50 3.84C14.38 3.72 14.26 3.61 14.13 3.52C14.00 3.42 13.87 3.33 13.73 3.26C13.60 3.18 13.46 3.11 13.31 3.05C13.17 2.99 13.02 2.95 12.87 2.91C12.71 2.87 12.58 2.84 12.40 2.82C12.21 2.81 11.93 2.80 11.75 2.81Z"

private val ICONS: Map<String, List<String>> = mapOf(
    "menu" to listOf("M4 6h16M4 12h16M4 18h8"),
    "pawn" to listOf(PAWN),
    "plus" to listOf("M12 4.5v15M4.5 12h15"),
    "mic" to listOf(rect(9.0, 3.0, 6.0, 11.0, 3.0), "M5.5 11.5a6.5 6.5 0 0 0 13 0", "M12 18v3"),
    "arrowup" to listOf("M12 19.5V5", "M6 10.5L12 4.5l6 6"),
    "search" to listOf(circ(10.5, 10.5, 6.5), "M15.4 15.4L20.5 20.5"),
    "task" to listOf(circ(12.0, 12.0, 8.5), "M8.3 12.3l2.6 2.6 4.8-5.3"),
    "compose" to listOf(
        "M11 4.5H7A2.5 2.5 0 0 0 4.5 7v10A2.5 2.5 0 0 0 7 19.5h10a2.5 2.5 0 0 0 2.5-2.5v-4",
        "M10 14l.6-3L17.9 3.7a1.8 1.8 0 0 1 2.6 2.6L13.2 13.6z",
    ),
    "checklist" to listOf("M3.8 7l1.7 1.7 3-3.4", "M3.8 16.6l1.7 1.7 3-3.4", "M12.5 7h7.7M12.5 16.6h7.7"),
    "sched" to listOf(rect(3.5, 4.5, 17.0, 15.0, 3.0), "M8 2.8v3.4M16 2.8v3.4M3.5 9.5h17", circ(14.5, 15.0, 3.3), "M14.5 13.4v1.7l1.1.7"),
    "calendar" to listOf(rect(4.0, 5.5, 16.0, 14.5, 3.0), "M8.5 3.5v3M15.5 3.5v3M4 10.5h16"),
    "stopwatch" to listOf(circ(12.0, 13.5, 7.5), "M9.8 3.5h4.4", "M12 13.5L9.4 10.8"),
    "refresh" to listOf("M21 12a9 9 0 1 1-9-9c2.5 0 4.9 1 6.7 2.7L21 8", "M21 3v5h-5"),
    "caldays" to listOf(
        rect(3.5, 5.0, 17.0, 15.5, 3.0), "M8 3v4M16 3v4M3.5 10h17",
        "F:" + circ(8.2, 14.2, .95), "F:" + circ(12.0, 14.2, .95), "F:" + circ(15.8, 14.2, .95),
        "F:" + circ(8.2, 17.4, .95), "F:" + circ(12.0, 17.4, .95), "F:" + circ(15.8, 17.4, .95),
    ),
    "chev" to listOf("M9 5.5l6.5 6.5L9 18.5"),
    "delete" to listOf(
        "M4 7h16", "M9.5 7V5.2A1.2 1.2 0 0 1 10.7 4h2.6a1.2 1.2 0 0 1 1.2 1.2V7",
        "M6 7l.8 11.6A2 2 0 0 0 8.8 20.5h6.4a2 2 0 0 0 2-1.9L18 7", "M10 11v6M14 11v6",
    ),
    "back" to listOf("M19 12H5M11 5l-7 7 7 7"),
)

private val parsed = HashMap<String, List<Pair<Path, Boolean>>>()
private fun pathsOf(name: String): List<Pair<Path, Boolean>> = parsed.getOrPut(name) {
    (ICONS[name] ?: emptyList()).map { d ->
        val filled = d.startsWith("F:")
        PathParser().parsePathString(if (filled) d.substring(2) else d).toPath() to filled
    }
}

@Composable
fun Ico(
    name: String, size: Dp, color: Color = Txt, stroke: Float = 1.75f,
    modifier: Modifier = Modifier, rotate: Float = 0f, fill: Float = 0f,
) {
    val ps = remember(name) { pathsOf(name) }
    Canvas(modifier.size(size)) {
        val sc = this.size.width / 24f
        scale(sc, sc, Offset.Zero) {
            rotate(rotate, Offset(12f, 12f)) {
                for ((p, f) in ps) {
                    if (f) drawPath(p, color, style = Fill)
                    else {
                        if (fill > 0f) drawPath(p, color.copy(alpha = color.alpha * fill), style = Fill)
                        drawPath(p, color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
            }
        }
    }
}

/** The animated tick: stroke-dasharray 26 / dashoffset 26 -> 0 in the CSS. */
private val CHECK: Path = PathParser().parsePathString("M5 12.5l4.5 4.5L19 7.5").toPath()

@Composable
fun CheckMark(progress: Float, size: Dp, color: Color, stroke: Float = 2.6f, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        if (progress <= 0f) return@Canvas
        val sc = this.size.width / 24f
        scale(sc, sc, Offset.Zero) {
            val pm = PathMeasure(); pm.setPath(CHECK, false)
            val len = pm.length
            val shown = (26f * progress).coerceAtMost(len)
            val seg = Path()
            pm.getSegment(0f, shown, seg, true)
            drawPath(seg, color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
