package com.mlimageclassifier.ui

import android.content.Context
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.Executor

@Composable
fun CameraPreview(
    imageAnalyzer: ImageAnalysis.Analyzer,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var previewUseCase by remember { mutableStateOf<Preview?>(null) }

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        },
        update = { previewView ->
            previewUseCase?.setSurfaceProvider(previewView.surfaceProvider)
        },
        modifier = modifier.fillMaxSize()
    )

    LaunchedEffect(Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val cameraProvider = cameraProviderFuture.get()

        val preview = Preview.Builder()
            .build()
            .also {
                previewUseCase = it
            }

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(ContextCompat.getMainExecutor(context), imageAnalyzer)
            }

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.get().unbindAll()
        }
    }
}

