package com.pricelens.feature.explore

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pricelens.core.data.local.db.entity.PriceCellCacheEntity
import com.pricelens.core.designsystem.component.PriceBandBar
import com.pricelens.domain.model.Money

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    onNavigateBack: () -> Unit,
    viewModel: ExploreViewModel = hiltViewModel()
) {
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val localPrices by viewModel.localPrices.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(text = "Local Prices", style = MaterialTheme.typography.headlineSmall) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.onSearchQueryChanged(it) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Search local items...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                )
            }
        }
    ) { padding ->
        if (localPrices.isEmpty()) {
            Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Learning this area", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Not enough data yet. Help by contributing!",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
                items(localPrices) { cell ->
                    PriceCellRow(cell = cell)
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}

@Composable
private fun PriceCellRow(cell: PriceCellCacheEntity) {
    val parts = cell.key.split("|")
    val itemLabel = parts.getOrNull(1) ?: "Unknown"
    val unit = parts.getOrNull(2) ?: ""

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = itemLabel, style = MaterialTheme.typography.bodyLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Text(text = "per $unit", style = MaterialTheme.typography.labelSmall)
        }
        
        PriceBandBar(
            p10 = Money(cell.p10Minor, cell.currencyCode),
            p50 = Money(cell.p50Minor, cell.currencyCode),
            p90 = Money(cell.p90Minor, cell.currencyCode),
            minRange = Money((cell.p10Minor * 0.8).toLong(), cell.currencyCode),
            maxRange = Money((cell.p90Minor * 1.2).toLong(), cell.currencyCode),
            modifier = Modifier.fillMaxWidth()
        )
        
        Text(
            text = "Updated ${cell.freshnessDays}d ago • ${cell.nObservations} observations",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
