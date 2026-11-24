package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.model.SpeedSample

@Composable
fun SpeedSparklineChart(
    samples: List<SpeedSample>,
    downloadColor: Color = MaterialTheme.colorScheme.primary,
    uploadColor: Color = MaterialTheme.colorScheme.secondary,
    gridColor: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(92.dp)
    ) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        // Subtle horizontal reference grid lines
        for (i in 1..3) {
            val y = h * (i / 4f)
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        if (samples.size < 2) {
            val midY = h * 0.85f
            drawLine(
                color = downloadColor.copy(alpha = 0.4f),
                start = Offset(0f, midY),
                end = Offset(w, midY),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
            return@Canvas
        }

        val maxVal = samples.maxOf { maxOf(it.downloadBps, it.uploadBps) }
            .coerceAtLeast(1024L)
            .toFloat()

        val stepX = w / (samples.size - 1).coerceAtLeast(1)
        val topPad = 6.dp.toPx()
        val bottomPad = 6.dp.toPx()
        val usableH = (h - topPad - bottomPad).coerceAtLeast(1f)

        val dlPath = Path()
        val dlFill = Path()
        val ulPath = Path()
        val ulFill = Path()

        samples.forEachIndexed { idx, sample ->
            val x = idx * stepX
            val dlY = topPad + usableH * (1f - (sample.downloadBps / maxVal).coerceIn(0f, 1f))
            val ulY = topPad + usableH * (1f - (sample.uploadBps / maxVal).coerceIn(0f, 1f))

            if (idx == 0) {
                dlPath.moveTo(x, dlY)
                dlFill.moveTo(x, h)
                dlFill.lineTo(x, dlY)
                ulPath.moveTo(x, ulY)
                ulFill.moveTo(x, h)
                ulFill.lineTo(x, ulY)
            } else {
                dlPath.lineTo(x, dlY)
                dlFill.lineTo(x, dlY)
                ulPath.lineTo(x, ulY)
                ulFill.lineTo(x, ulY)
            }
            if (idx == samples.lastIndex) {
                dlFill.lineTo(x, h)
                dlFill.close()
                ulFill.lineTo(x, h)
                ulFill.close()
            }
        }

        drawPath(
            path = ulFill,
            brush = Brush.verticalGradient(
                colors = listOf(uploadColor.copy(alpha = 0.22f), Color.Transparent),
                startY = 0f,
                endY = h
            )
        )
        drawPath(
            path = dlFill,
            brush = Brush.verticalGradient(
                colors = listOf(downloadColor.copy(alpha = 0.26f), Color.Transparent),
                startY = 0f,
                endY = h
            )
        )
        drawPath(
            path = ulPath,
            color = uploadColor,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        drawPath(
            path = dlPath,
            color = downloadColor,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}
