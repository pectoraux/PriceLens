package com.pricelens.ml.pipeline.model

import android.graphics.RectF

data class Detection(
    val boundingBox: RectF,
    val score: Float,
    val label: String? = null,
    val isPerson: Boolean = false,
    val focusSharpness: Float = 0f
)
