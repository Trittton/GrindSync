package dev.gatsyuk.grindsync.core.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ChartPoint(val date: LocalDate, val value: Double)

/**
 * Minimal themed line chart (deliberate stand-in for Vico — swap here if
 * charts ever need zoom/scrubbing). Draws a line + soft fill, min/max grid
 * lines, and first/last date labels. Handles 1..n points.
 */
@Composable
fun LineChart(
    points: List<ChartPoint>,
    valueLabel: (Double) -> String,
    modifier: Modifier = Modifier,
) {
    if (points.isEmpty()) return
    val accent = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outline
    val labelStyle = MaterialTheme.typography.labelSmall
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val dateFormat = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)

    val minV = points.minOf { it.value }
    val maxV = points.maxOf { it.value }

    Column(modifier) {
        // Y-axis extremes: max above the chart, min below — both left-aligned.
        Text(valueLabel(maxV), style = labelStyle, color = labelColor)
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(140.dp)
                .padding(vertical = 6.dp),
        ) {
            val w = size.width
            val h = size.height
            val range = (maxV - minV).takeIf { it > 0.0 } ?: 1.0
            fun x(i: Int) = if (points.size == 1) w / 2 else w * i / (points.size - 1)
            fun y(v: Double) = (h * (1 - (v - minV) / range)).toFloat()
                .coerceIn(3f, h - 3f)

            // min/max guide lines
            val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
            drawLine(gridColor, Offset(0f, y(maxV)), Offset(w, y(maxV)), 1f, pathEffect = dash)
            drawLine(gridColor, Offset(0f, y(minV)), Offset(w, y(minV)), 1f, pathEffect = dash)

            val line = Path()
            points.forEachIndexed { i, p ->
                val px = x(i)
                val py = y(p.value)
                if (i == 0) line.moveTo(px, py) else line.lineTo(px, py)
            }
            if (points.size > 1) {
                val fill = Path().apply {
                    addPath(line)
                    lineTo(x(points.size - 1), h)
                    lineTo(x(0), h)
                    close()
                }
                drawPath(
                    fill,
                    Brush.verticalGradient(listOf(accent.copy(alpha = 0.25f), accent.copy(alpha = 0f))),
                )
                drawPath(line, accent, style = Stroke(width = 4f, cap = StrokeCap.Round))
            }
            points.forEachIndexed { i, p ->
                drawCircle(accent, radius = 7f, center = Offset(x(i), y(p.value)))
            }
        }
        if (maxV != minV) Text(valueLabel(minV), style = labelStyle, color = labelColor)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween) {
            Text(points.first().date.format(dateFormat), style = labelStyle, color = labelColor)
            if (points.size > 1) {
                Text(points.last().date.format(dateFormat), style = labelStyle, color = labelColor)
            }
        }
    }
}
