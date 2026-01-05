package com.mlimageclassifier.ui

import androidx.lifecycle.ViewModel
import com.mlimageclassifier.data.DetectedObject
import com.mlimageclassifier.data.ObjectDetectionRepository
import kotlinx.coroutines.flow.StateFlow

class ObjectDetectionViewModel(
    private val repository: ObjectDetectionRepository
) : ViewModel() {

    val detections: StateFlow<List<DetectedObject>> = repository.detections

    fun getImageAnalyzer() = repository.createImageAnalyzer()

    override fun onCleared() {
        super.onCleared()
        repository.cleanup()
    }
}

