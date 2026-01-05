package com.mlimageclassifier.data

import android.content.Context
import androidx.camera.core.ImageAnalysis
import com.mlimageclassifier.ml.YOLODetector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ObjectDetectionRepository(private val context: Context) {
    
    private val _detections = MutableStateFlow<List<DetectedObject>>(emptyList())
    val detections: StateFlow<List<DetectedObject>> = _detections.asStateFlow()

    private var yoloDetector: YOLODetector? = null

    fun createImageAnalyzer(): ImageAnalysis.Analyzer {
        yoloDetector = YOLODetector(context) { results ->
            _detections.value = results
        }
        return yoloDetector!!
    }

    fun cleanup() {
        yoloDetector?.close()
        yoloDetector = null
    }
}

