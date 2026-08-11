package com.pricelens.feature.review

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pricelens.core.designsystem.component.ConfidenceChip
import com.pricelens.core.designsystem.component.ConfidenceLevel
import com.pricelens.core.designsystem.component.PriceBandBar
import com.pricelens.core.designsystem.theme.PriceLensTheme
import com.pricelens.feature.review.R

import com.pricelens.core.data.remote.model.PriceBand
import com.pricelens.domain.model.Money
import com.pricelens.domain.policy.PriceVerdict
import com.pricelens.domain.policy.PriceVerdictPolicy

import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.pricelens.domain.model.CanonicalUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    onNavigateBack: () -> Unit,
    onNavigateToHistory: () -> Unit,
    viewModel: ReviewViewModel = hiltViewModel()
) {
    val isPickingItem by viewModel.isPickingItem.collectAsStateWithLifecycle()
    val observation by viewModel.observation.collectAsStateWithLifecycle()
    val observationId = viewModel.observationId
    
    val quantity by viewModel.quantity.collectAsStateWithLifecycle()
    val unit by viewModel.unit.collectAsStateWithLifecycle()
    val isPlausible by viewModel.isPlausible.collectAsStateWithLifecycle()
    val priceBand by viewModel.priceBand.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                ReviewUiEvent.NavigateBack -> onNavigateBack()
                ReviewUiEvent.NavigateToHistory -> onNavigateToHistory()
            }
        }
    }

    if (isPickingItem) {
        ItemPicker(onItemSelected = { viewModel.onItemSelected(observationId, it) })
        return
    }

    if (observation == null) {
        // Loading state
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Loading observation...")
        }
        return
    }

    val obs = observation!!

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(id = R.string.review_screen_title),
                        style = MaterialTheme.typography.headlineSmall
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Label Section
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(id = R.string.item_recognized),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = obs.itemId ?: obs.predictedItemId ?: "Unknown",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    ConfidenceChip(
                        level = when {
                            (obs.predictedConfidence ?: 0f) > 0.8f -> ConfidenceLevel.HIGH
                            (obs.predictedConfidence ?: 0f) > 0.4f -> ConfidenceLevel.MEDIUM
                            else -> ConfidenceLevel.LOW
                        },
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Price Section
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (priceBand != null && priceBand!!.confidence != PriceBand.Confidence.INSUFFICIENT) {
                        val band = priceBand!!
                        val quoted = Money(obs.priceMinor, obs.currencyCode)
                        
                        PriceBandBar(
                            p10 = Money(band.p10Minor, band.currencyCode),
                            p50 = Money(band.p50Minor, band.currencyCode),
                            p90 = Money(band.p90Minor, band.currencyCode),
                            quotedPrice = quoted,
                            minRange = Money((band.p10Minor * 0.8).toLong(), band.currencyCode),
                            maxRange = Money((band.p90Minor * 1.5).toLong(), band.currencyCode),
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        val verdict = PriceVerdictPolicy.evaluate(
                            quotedPrice = quoted,
                            p10 = Money(band.p10Minor, band.currencyCode),
                            p50 = Money(band.p50Minor, band.currencyCode),
                            p90 = Money(band.p90Minor, band.currencyCode),
                            nObservations = band.nObservations,
                            freshnessDays = band.freshnessDays ?: 0
                        )

                        Text(
                            text = when (verdict) {
                                PriceVerdict.GOOD_DEAL -> "Below the usual range — good deal"
                                PriceVerdict.USUAL_RANGE -> "Within the usual range here"
                                PriceVerdict.ABOVE_USUAL -> "Above the usual range"
                                PriceVerdict.WELL_ABOVE -> "Well above the usual range"
                                PriceVerdict.INSUFFICIENT_DATA -> "Not enough local data yet"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (verdict == PriceVerdict.WELL_ABOVE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    } else {
                        // Insufficient Data State
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Not enough local data yet",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Your contribution will help seed this market.",
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Quantity and Unit Selection
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Quantity & Unit", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = quantity,
                            onValueChange = { viewModel.onQuantityChanged(it) },
                            modifier = Modifier.weight(1f),
                            label = { Text("Quantity") },
                            isError = !isPlausible
                        )
                        Spacer(modifier = Modifier.padding(horizontal = 8.dp))

                        var expanded by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedButton(
                                onClick = { expanded = true },
                                modifier = Modifier.fillMaxWidth().height(56.dp)
                            ) {
                                Text(unit.uppercase())
                            }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                CanonicalUnit.entries.forEach { u ->
                                    DropdownMenuItem(
                                        text = { Text(u.displayName) },
                                        onClick = {
                                            viewModel.onUnitChanged(u.name.lowercase())
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    if (!isPlausible) {
                        Text(
                            text = "This quantity seems high. Please double check.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Actions
            Text(
                text = "Is this accurate?",
                style = MaterialTheme.typography.titleMedium
            )
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.onIncorrectTapped() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Close, contentDescription = null)
                    Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                    Text(stringResource(id = R.string.incorrect))
                }
                
                Spacer(modifier = Modifier.padding(horizontal = 8.dp))

                Button(
                    onClick = { viewModel.onConfirmTapped(observationId) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                    Text(stringResource(id = R.string.confirm))
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ReviewScreenPreview() {
    PriceLensTheme {
        ReviewScreen(onNavigateBack = {}, onNavigateToHistory = {})
    }
}
