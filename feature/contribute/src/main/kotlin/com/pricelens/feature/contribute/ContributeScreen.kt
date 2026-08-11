package com.pricelens.feature.contribute

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pricelens.domain.model.CanonicalUnit
import com.pricelens.feature.contribute.component.ItemPicker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContributeScreen(
    onNavigateBack: () -> Unit,
    viewModel: ContributeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedItem by viewModel.selectedItem.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                ContributeUiEvent.NavigateBack -> onNavigateBack()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Contribute Price") },
                navigationIcon = {
                    if (uiState !is ContributeUiState.SelectItem) {
                        IconButton(onClick = { viewModel.onBackTapped() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (uiState) {
                is ContributeUiState.SelectItem -> {
                    ItemPicker(
                        onItemSelected = { viewModel.onItemSelected(it) },
                        searchTaxonomy = { viewModel.searchTaxonomy(it) }
                    )
                }
                is ContributeUiState.EnterDetails -> {
                    DetailsForm(
                        itemName = selectedItem?.displayName ?: "",
                        onSubmit = { p, q, u -> viewModel.submitContribution(p, q, u) }
                    )
                }
                is ContributeUiState.Success -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "Thank you!", style = MaterialTheme.typography.headlineMedium)
                        Text(text = "Your contribution helps everyone.", modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailsForm(
    itemName: String,
    onSubmit: (Long, Double, String) -> Unit
) {
    var price by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("1") }
    var unit by remember { mutableStateOf("kg") }

    Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
        Text(text = itemName, style = MaterialTheme.typography.headlineSmall)
        
        OutlinedTextField(
            value = price,
            onValueChange = { price = it },
            label = { Text("Price (e.g. 150)") },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            prefix = { Text("KES ") }
        )

        Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
            OutlinedTextField(
                value = quantity,
                onValueChange = { quantity = it },
                label = { Text("Quantity") },
                modifier = Modifier.weight(1f)
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
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
                                unit = u.name.lowercase()
                                expanded = false
                            }
                        )
                    }
                }
            }
        }

        Button(
            onClick = {
                val p = ((price.toDoubleOrNull() ?: 0.0) * 100).toLong()
                val q = quantity.toDoubleOrNull() ?: 1.0
                onSubmit(p, q, unit)
            },
            modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
            enabled = price.isNotEmpty() && quantity.isNotEmpty()
        ) {
            Text("Submit")
        }
    }
}
