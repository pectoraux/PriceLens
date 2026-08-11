package com.pricelens.core.data.model

import kotlinx.serialization.Serializable

@Serializable
data class CatalogBundle(
    val taxonomy: List<TaxonomyItemDto>,
    val prototypes: List<PrototypeDto>
)

@Serializable
data class TaxonomyItemDto(
    val slug: String,
    val displayName: String,
    val vernacularNames: String? = null,
    val category: String,
    val defaultUnit: String,
    val densityKgPerL: Double? = null,
    val shapeModel: String? = null,
    val textEmbedding: String // Base64
)

@Serializable
data class PrototypeDto(
    val id: Long,
    val itemSlug: String,
    val centroid: String, // Base64
    val nMembers: Int
)
