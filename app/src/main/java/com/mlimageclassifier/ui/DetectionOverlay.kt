package com.mlimageclassifier.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.mlimageclassifier.data.DetectedObject
import androidx.compose.ui.graphics.nativeCanvas
import android.graphics.Paint
import androidx.compose.ui.Alignment

@Composable
fun DetectionOverlay(
    detections: List<DetectedObject>,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val textPaint = Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = 32f * density.density
        isFakeBoldText = true
        style = Paint.Style.FILL
    }

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            
            detections.forEach { detection ->
                val boundingBox = detection.boundingBox
                val imageWidth = if (detection.imageWidth > 0f) detection.imageWidth else canvasWidth
                val imageHeight = if (detection.imageHeight > 0f) detection.imageHeight else canvasHeight
                
                // Calculate scaling factors
                val imageAspectRatio = imageWidth / imageHeight
                val canvasAspectRatio = canvasWidth / canvasHeight
                
                // PreviewView uses FILL_CENTER, which scales to fill while maintaining aspect ratio
                // Calculate how the image is actually displayed
                val scale: Float
                val offsetX: Float
                val offsetY: Float
                
                if (imageAspectRatio > canvasAspectRatio) {
                    // Image is wider - scale based on width, add vertical padding
                    scale = canvasWidth / imageWidth
                    offsetX = 0f
                    offsetY = (canvasHeight - imageHeight * scale) / 2f
                } else {
                    // Image is taller - scale based on height, add horizontal padding
                    scale = canvasHeight / imageHeight
                    offsetX = (canvasWidth - imageWidth * scale) / 2f
                    offsetY = 0f
                }
                
                // Scale and translate bounding box coordinates
                val scaledLeft = boundingBox.left * scale + offsetX
                val scaledTop = boundingBox.top * scale + offsetY
                val scaledRight = boundingBox.right * scale + offsetX
                val scaledBottom = boundingBox.bottom * scale + offsetY
                
                // Draw bounding box
                val rect = Rect(
                    offset = Offset(scaledLeft, scaledTop),
                    size = Size(scaledRight - scaledLeft, scaledBottom - scaledTop)
                )
                
                // Only draw if box is visible on screen
                if (rect.left < canvasWidth && rect.top < canvasHeight && 
                    rect.right > 0f && rect.bottom > 0f) {
                    
                    // Draw filled rectangle with transparency
                    drawRect(
                        color = Color.Green.copy(alpha = 0.2f),
                        topLeft = rect.topLeft,
                        size = rect.size
                    )
                    
                    // Draw border
                    drawRect(
                        color = Color.Green,
                        topLeft = rect.topLeft,
                        size = rect.size,
                        style = Stroke(width = 4.dp.toPx())
                    )
                    
                    // Draw label and confidence
                    val labelText = "${detection.label}: ${(detection.confidence * 100).toInt()}%"
                    val textY = maxOf(scaledTop - 10f, textPaint.textSize)
                    
                    // Draw text background
                    val textWidth = textPaint.measureText(labelText)
                    val textBackgroundHeight = textPaint.textSize + 20f
                    drawRect(
                        color = Color.Green.copy(alpha = 0.9f),
                        topLeft = Offset(scaledLeft, scaledTop - textBackgroundHeight),
                        size = Size(textWidth + 20f, textBackgroundHeight)
                    )
                    
                    // Draw text
                    drawContext.canvas.nativeCanvas.apply {
                        drawText(
                            labelText,
                            scaledLeft + 10f,
                            textY,
                            textPaint
                        )
                    }
                }
            }
        }
        
        // Status text (for debugging) - moved to bottom to avoid red line
        Text(
            text = if (detections.isEmpty()) {
                "No detections"
            } else {
                "Detections: ${detections.size}"
            },
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.6f), androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

