package com.pricelens.core.designsystem.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pricelens.core.designsystem.theme.PriceLensTheme

/**
 * A component presented to the user when prediction confidence is low.
 * Allows the user to select between the top candidates or indicate none of them match.
 */
@Composable
fun AbstainPrompt(
    options: List<String>,
    onOptionSelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Is it one of these?",
                style = MaterialTheme.typography.titleMedium
            )
            
            options.take(2).forEach { option ->
                Button(
                    onClick = { onOptionSelected(option) },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Text(text = option)
                }
            }
            
            OutlinedButton(
                onClick = { onOptionSelected(null) },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                Text(text = "None of these")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AbstainPromptPreview() {
    PriceLensTheme {
        Box(Modifier.padding(16.dp)) {
            AbstainPrompt(
                options = listOf("Roma Tomato", "Cherry Tomato"),
                onOptionSelected = {}
            )
        }
    }
}
