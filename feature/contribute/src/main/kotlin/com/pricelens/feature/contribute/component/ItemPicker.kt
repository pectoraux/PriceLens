package com.pricelens.feature.contribute.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pricelens.core.data.local.db.entity.TaxonomyItemCacheEntity
import kotlinx.coroutines.flow.Flow

@Composable
fun ItemPicker(
    onItemSelected: (TaxonomyItemCacheEntity) -> Unit,
    searchTaxonomy: (String) -> Flow<List<TaxonomyItemCacheEntity>>,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    val items by searchTaxonomy(searchQuery).collectAsState(initial = emptyList())

    Column(modifier = modifier) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Search food item") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            singleLine = true
        )

        LazyColumn {
            items(items) { item ->
                ListItem(
                    headlineContent = { Text(item.displayName) },
                    supportingContent = { Text(item.category) },
                    modifier = Modifier.clickable { onItemSelected(item) }
                )
                HorizontalDivider()
            }
        }
    }
}
