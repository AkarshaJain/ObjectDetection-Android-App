package com.mlimageclassifier.data

data class BoundingBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

data class DetectedObject(
    val label: String,
    val confidence: Float,
    val boundingBox: BoundingBox,
    val imageWidth: Float = 0f,  // Original image width for scaling
    val imageHeight: Float = 0f // Original image height for scaling
)

