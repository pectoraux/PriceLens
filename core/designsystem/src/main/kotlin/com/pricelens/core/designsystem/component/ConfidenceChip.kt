package com.pricelens.core.designsystem.component

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pricelens.core.designsystem.theme.ConfidenceHigh
import com.pricelens.core.designsystem.theme.ConfidenceLow
import com.pricelens.core.designsystem.theme.ConfidenceMedium
import com.pricelens.core.designsystem.theme.PriceLensTheme
import com.pricelens.core.designsystem.R

enum class ConfidenceLevel {
    HIGH, MEDIUM, LOW
}

@Composable
fun ConfidenceChip(
    level: ConfidenceLevel,
    modifier: Modifier = Modifier
) {
    val (labelRes, icon, color) = when (level) {
        ConfidenceLevel.HIGH -> Triple(R.string.high_confidence, Icons.Default.CheckCircle, ConfidenceHigh)
        ConfidenceLevel.MEDIUM -> Triple(R.string.medium_confidence, Icons.Default.Info, ConfidenceMedium)
        ConfidenceLevel.LOW -> Triple(R.string.low_confidence, Icons.Default.Warning, ConfidenceLow)
    }

    AssistChip(
        onClick = { },
        label = { Text(stringResource(id = labelRes)) },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(AssistChipDefaults.IconSize),
                tint = color
            )
        },
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun ConfidenceChipHighPreview() {
    PriceLensTheme {
        ConfidenceChip(level = ConfidenceLevel.HIGH, modifier = Modifier.padding(8.dp))
    }
}

@Preview(showBackground = true)
@Composable
fun ConfidenceChipMediumPreview() {
    PriceLensTheme {
        ConfidenceChip(level = ConfidenceLevel.MEDIUM, modifier = Modifier.padding(8.dp))
    }
}

@Preview(showBackground = true)
@Composable
fun ConfidenceChipLowPreview() {
    PriceLensTheme {
        ConfidenceChip(level = ConfidenceLevel.LOW, modifier = Modifier.padding(8.dp))
    }
}
