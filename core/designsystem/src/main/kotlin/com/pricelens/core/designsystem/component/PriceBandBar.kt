package com.pricelens.core.designsystem.component

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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pricelens.core.designsystem.theme.PriceLensTheme

import com.pricelens.domain.model.Money

@Composable
fun PriceBandBar(
    p10: Money,
    p50: Money,
    p90: Money,
    quotedPrice: Money? = null,
    minRange: Money,
    maxRange: Money,
    modifier: Modifier = Modifier
) {
    val barColor = MaterialTheme.colorScheme.surfaceVariant
    val rangeColor = MaterialTheme.colorScheme.primary
    val medianColor = MaterialTheme.colorScheme.onSurfaceVariant
    val markerColor = MaterialTheme.colorScheme.error

    Column(modifier = modifier.padding(8.dp)) {
        Text(
            text = "Price Estimate",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .padding(vertical = 8.dp)
        ) {
            val width = size.width
            val height = size.height
            val range = (maxRange.minorUnits - minRange.minorUnits).toDouble()
            
            val p10Pos = (((p10.minorUnits - minRange.minorUnits) / range).coerceIn(0.0, 1.0) * width).toFloat()
            val p50Pos = (((p50.minorUnits - minRange.minorUnits) / range).coerceIn(0.0, 1.0) * width).toFloat()
            val p90Pos = (((p90.minorUnits - minRange.minorUnits) / range).coerceIn(0.0, 1.0) * width).toFloat()

            // Background bar
            drawLine(
                color = barColor,
                start = Offset(0f, height / 2),
                end = Offset(width, height / 2),
                strokeWidth = 8.dp.toPx(),
                cap = StrokeCap.Round
            )

            // Confidence range (P10 to P90)
            drawLine(
                color = rangeColor,
                start = Offset(p10Pos, height / 2),
                end = Offset(p90Pos, height / 2),
                strokeWidth = 8.dp.toPx(),
                cap = StrokeCap.Round
            )

            // Median marker (P50)
            drawLine(
                color = medianColor,
                start = Offset(p50Pos, height / 2 - 6.dp.toPx()),
                end = Offset(p50Pos, height / 2 + 6.dp.toPx()),
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round
            )

            // Quoted Price Marker (if present)
            quotedPrice?.let { q ->
                val qPos = (((q.minorUnits - minRange.minorUnits) / range).coerceIn(0.0, 1.0) * width).toFloat()
                drawCircle(
                    color = markerColor,
                    center = Offset(qPos, height / 2),
                    radius = 5.dp.toPx()
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = p10.format(p10.currencyCode + " "),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = p50.format(p50.currencyCode + " "),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            Text(
                text = p90.format(p90.currencyCode + " "),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PriceBandBarPreview() {
    PriceLensTheme {
        PriceBandBar(
            p10 = Money(250, "KES"),
            p50 = Money(320, "KES"),
            p90 = Money(410, "KES"),
            quotedPrice = Money(380, "KES"),
            minRange = Money(200, "KES"),
            maxRange = Money(500, "KES"),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
