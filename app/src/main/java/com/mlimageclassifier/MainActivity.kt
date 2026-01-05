package com.mlimageclassifier

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.mlimageclassifier.data.ObjectDetectionRepository
import com.mlimageclassifier.ui.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MLObjectDetectionApp()
                }
            }
        }
    }
}

@Composable
fun MLObjectDetectionApp() {
    val context = LocalContext.current
    val repository = remember { ObjectDetectionRepository(context) }
    val viewModel: ObjectDetectionViewModel = remember {
        ObjectDetectionViewModel(repository)
    }

    PermissionHandler {
        val detections by viewModel.detections.collectAsState()

        Box(modifier = Modifier.fillMaxSize()) {
            CameraPreview(
                imageAnalyzer = viewModel.getImageAnalyzer(),
                modifier = Modifier.fillMaxSize()
            )
            DetectionOverlay(
                detections = detections,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

