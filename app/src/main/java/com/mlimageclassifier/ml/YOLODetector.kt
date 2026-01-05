package com.mlimageclassifier.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import org.tensorflow.lite.Interpreter
import com.mlimageclassifier.data.DetectedObject
import com.mlimageclassifier.data.BoundingBox
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class YOLODetector(
    private val context: Context,
    private val onDetectionResult: (List<DetectedObject>) -> Unit
) : ImageAnalysis.Analyzer {

    private var interpreter: Interpreter? = null
    private val modelInputSize = 640 // YOLO models typically use 640x640
    private val confidenceThreshold = 0.25f  // Standard YOLO threshold (0.25 = 25%)
    private val nmsThreshold = 0.45f  // NMS threshold for removing overlapping boxes
    private val minBoxSizeRatio = 0.01f  // Minimum box size: 1% of image
    private val labels: List<String> by lazy { loadLabels() }

    init {
        loadModel()
    }

    private fun loadModel() {
        // Try multiple possible filenames
        val possibleModelNames = listOf(
            "yolov8n.tflite",           // Colab default name
            "yolov8.tflite",            // Alternative name
            "tflite_model.tflite",      // Original name
            "yolov8n_float32.tflite"    // Full name variant
        )
        
        var modelLoaded = false
        for (modelName in possibleModelNames) {
            try {
                val modelBuffer = loadModelFile(modelName)
                val options = Interpreter.Options().apply {
                    setNumThreads(4)
                }
                interpreter = Interpreter(modelBuffer, options)
                
                // Log model input/output shapes for debugging
                val inputTensor = interpreter!!.getInputTensor(0)
                val outputTensor = interpreter!!.getOutputTensor(0)
                Log.d("YOLODetector", "Model loaded successfully: $modelName")
                Log.d("YOLODetector", "Input shape: ${inputTensor.shape().contentToString()}, Output shape: ${outputTensor.shape().contentToString()}")
                modelLoaded = true
                break
            } catch (e: Exception) {
                Log.d("YOLODetector", "Failed to load $modelName: ${e.message}")
                // Continue to next filename
            }
        }
        
        if (!modelLoaded) {
            Log.e("YOLODetector", "Failed to load model. Tried: ${possibleModelNames.joinToString()}")
            Log.e("YOLODetector", "Please ensure one of these files exists in app/src/main/assets/")
            onDetectionResult(emptyList())
        }
    }

    @Throws(Exception::class)
    private fun loadModelFile(modelPath: String): MappedByteBuffer {
        val assetFileDescriptor = context.assets.openFd(modelPath)
        val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun loadLabels(): List<String> {
        return try {
            val inputStream = context.assets.open("coco_labels.txt")
            val reader = BufferedReader(InputStreamReader(inputStream))
            val loadedLabels = reader.useLines { lines ->
                lines.filter { it.isNotBlank() && !it.trimStart().startsWith("#") }
                    .map { it.trim() }
                    .toList()
            }
            Log.d("YOLODetector", "Loaded ${loadedLabels.size} labels from coco_labels.txt")
            if (loadedLabels.isNotEmpty()) {
                Log.d("YOLODetector", "First 5 labels: ${loadedLabels.take(5).joinToString(", ")}")
            }
            loadedLabels
        } catch (e: Exception) {
            Log.e("YOLODetector", "Error loading labels: ${e.message}", e)
            e.printStackTrace()
            emptyList()
        }
    }

    override fun analyze(imageProxy: ImageProxy) {
        if (interpreter == null) {
            imageProxy.close()
            return
        }
        val bitmap = imageProxy.convertToBitmap()
        if (bitmap != null) {
            processImage(bitmap, imageProxy.width.toFloat(), imageProxy.height.toFloat())
        }
        imageProxy.close()
    }

    private fun processImage(bitmap: Bitmap, imageWidth: Float, imageHeight: Float) {
        try {
            if (interpreter == null) {
                Log.w("YOLODetector", "Interpreter is null, skipping")
                return
            }
            
            // Get actual output shape from model
            val outputTensor = interpreter!!.getOutputTensor(0)
            val outputShape = outputTensor.shape()
            
            // Handle transposed output: model outputs [1, 84, 8400] but we need [1, 8400, 84]
            val isTransposed = outputShape[1] < outputShape[2]
            val numBoxes = if (isTransposed) outputShape[2] else outputShape[1]
            val boxSize = if (isTransposed) outputShape[1] else outputShape[2]
            
            Log.d("YOLODetector", "Processing image - Output shape: [${outputShape[0]}, ${outputShape[1]}, ${outputShape[2]}], Transposed: $isTransposed")
            Log.d("YOLODetector", "Interpreted as: $numBoxes boxes, $boxSize values per box")
            
            // Resize bitmap to model input size
            val resizedBitmap = Bitmap.createScaledBitmap(
                bitmap,
                modelInputSize,
                modelInputSize,
                true
            )

            // Prepare input buffer: [1, 640, 640, 3] for YOLOv8
            val inputBuffer = Array(1) { Array(modelInputSize) { Array(modelInputSize) { FloatArray(3) } } }
            
            // Create output buffer matching the actual tensor shape (TensorFlow Lite requires exact match)
            // Model outputs [1, 84, 8400], so we must create buffer with that exact shape
            val outputBuffer = Array(1) { Array(outputShape[1]) { FloatArray(outputShape[2]) } }

            // Normalize pixel values to [0, 1] range (YOLO preprocessing)
            // Use better quality scaling algorithm
            val pixels = IntArray(modelInputSize * modelInputSize)
            resizedBitmap.getPixels(pixels, 0, modelInputSize, 0, 0, modelInputSize, modelInputSize)

            for (i in 0 until modelInputSize) {
                for (j in 0 until modelInputSize) {
                    val pixel = pixels[i * modelInputSize + j]
                    // YOLO expects RGB normalized to [0, 1]
                    // Extract RGB values and normalize
                    val r = (pixel shr 16 and 0xFF) / 255.0f
                    val g = (pixel shr 8 and 0xFF) / 255.0f
                    val b = (pixel and 0xFF) / 255.0f
                    
                    // Ensure values are in valid range
                    inputBuffer[0][i][j][0] = r.coerceIn(0f, 1f)
                    inputBuffer[0][i][j][1] = g.coerceIn(0f, 1f)
                    inputBuffer[0][i][j][2] = b.coerceIn(0f, 1f)
                }
            }

            interpreter!!.run(inputBuffer, outputBuffer)

            // Transpose output: [1, 84, 8400] -> [1, 8400, 84]
            // TensorFlow Lite outputs in shape [1, boxSize, numBoxes], we need [1, numBoxes, boxSize]
            val transposedOutput = Array(numBoxes) { FloatArray(boxSize) }
            for (i in 0 until numBoxes) {
                for (j in 0 until boxSize) {
                    transposedOutput[i][j] = outputBuffer[0][j][i]
                }
            }

            // Log first few output values for debugging
            if (transposedOutput.isNotEmpty() && transposedOutput[0].isNotEmpty()) {
                val firstBox = transposedOutput[0]
                Log.d("YOLODetector", "First box values (first 10): ${firstBox.take(10).joinToString(", ")}")
                // Log max class score from first box
                if (firstBox.size > 4) {
                    val classScores = firstBox.slice(4 until minOf(firstBox.size, 84))
                    val maxScore = classScores.maxOrNull() ?: 0f
                    val maxIndex = classScores.indexOf(maxScore)
                    Log.d("YOLODetector", "First box - max class score: ${(maxScore * 100).toInt()}% (class $maxIndex)")
                }
            }

            // Parse YOLO output based on actual box size
            val detections = parseYOLOOutput(
                transposedOutput,
                imageWidth,
                imageHeight,
                boxSize
            )
            
            Log.d("YOLODetector", "Found ${detections.size} detections after parsing")
            onDetectionResult(detections)
        } catch (e: Exception) {
            Log.e("YOLODetector", "Error processing image: ${e.message}", e)
            e.printStackTrace()
            onDetectionResult(emptyList())
        }
    }

    private fun parseYOLOOutput(
        output: Array<FloatArray>,
        imageWidth: Float,
        imageHeight: Float,
        boxSize: Int
    ): List<DetectedObject> {
        val detections = mutableListOf<DetectedObject>()
        
        var totalBoxes = 0
        var boxesWithHighScore = 0
        
        for (box in output) {
            totalBoxes++
            
            // YOLOv8 TFLite format can vary:
            // Format 1 (84 values): [x_center, y_center, width, height, class_score_0, ..., class_score_79]
            // Format 2 (85 values): [x_center, y_center, width, height, conf, class_score_0, ..., class_score_79]
            // Some models might use different formats
            
            if (box.size < 4) continue
            
            // YOLOv8 outputs normalized coordinates (0-1 range)
            // Some models output [x, y, w, h] where x,y are center coordinates
            // Others might output [x1, y1, x2, y2] corner coordinates
            // We assume center format: [x_center, y_center, width, height]
            val xCenter = box[0]
            val yCenter = box[1]
            val width = box[2]
            val height = box[3]
            
            // Validate coordinates are in reasonable range (0-1 for normalized, or could be pixel values)
            // Most YOLOv8 TFLite models output normalized (0-1), but some might output pixel values
            val isNormalized = (xCenter >= 0f && xCenter <= 1f && yCenter >= 0f && yCenter <= 1f &&
                               width >= 0f && width <= 1f && height >= 0f && height <= 1f)
            
            if (!isNormalized && (xCenter > imageWidth || yCenter > imageHeight)) {
                // Coordinates appear to be in pixel format, skip this box or convert
                if (totalBoxes <= 3) {
                    Log.w("YOLODetector", "Box $totalBoxes has non-normalized coordinates: x=$xCenter, y=$yCenter (image: ${imageWidth.toInt()}x${imageHeight.toInt()})")
                }
                // Try to normalize if they're pixel values
                if (xCenter > 0 && yCenter > 0) {
                    // Assume pixel coordinates, will handle in conversion
                }
            }
            
            // Determine format based on box size
            val classStartIndex = if (boxSize == 85) 5 else 4
            val hasObjectConfidence = boxSize == 85
            
            // Find class with highest score
            var maxClassScore = 0f
            var maxClassIndex = 0
            
            // Ensure we don't go beyond valid class range (COCO has 80 classes: 0-79)
            val validClassEnd = minOf(classStartIndex + 80, box.size)
            
            // Log first detection for debugging
            if (totalBoxes == 1) {
                Log.d("YOLODetector", "First box - xCenter: $xCenter, yCenter: $yCenter, width: $width, height: $height")
                Log.d("YOLODetector", "Box size: $boxSize, classStartIndex: $classStartIndex, validClassEnd: $validClassEnd")
                if (validClassEnd > classStartIndex) {
                    Log.d("YOLODetector", "First 5 class scores: ${box.slice(classStartIndex until minOf(classStartIndex + 5, validClassEnd)).joinToString(", ")}")
                }
            }
            
            // Only search within valid class range
            for (i in classStartIndex until validClassEnd) {
                if (box[i] > maxClassScore) {
                    maxClassScore = box[i]
                    maxClassIndex = i - classStartIndex
                }
            }
            
            // Final validation - class index must be 0-79
            if (maxClassIndex >= 80 || maxClassIndex < 0) {
                Log.w("YOLODetector", "Invalid class index: $maxClassIndex (boxSize: $boxSize, classStartIndex: $classStartIndex, validClassEnd: $validClassEnd)")
                continue
            }
            
            // Calculate final confidence
            // YOLOv8 format: [x, y, w, h, conf, class_scores...] or [x, y, w, h, class_scores...]
            val finalConfidence = if (hasObjectConfidence && box.size > 4) {
                val objectConf = box[4].coerceIn(0f, 1f)
                // Standard YOLO confidence: object confidence * class confidence
                objectConf * maxClassScore
            } else {
                maxClassScore  // Direct class confidence
            }
            
            // Log confidence values for debugging (first few boxes)
            if (totalBoxes <= 5) {
                Log.d("YOLODetector", "Box $totalBoxes - maxClassScore: ${(maxClassScore * 100).toInt()}%, finalConfidence: ${(finalConfidence * 100).toInt()}%, threshold: ${(confidenceThreshold * 100).toInt()}%")
            }
            
            // Filter by confidence threshold
            if (finalConfidence < confidenceThreshold) continue
            boxesWithHighScore++
            
            // YOLO outputs normalized coordinates (0-1) for center and size
            // Handle both normalized (0-1) and pixel coordinate formats
            val normalizedXCenter: Float
            val normalizedYCenter: Float
            val normalizedWidth: Float
            val normalizedHeight: Float
            
            if (xCenter <= 1f && yCenter <= 1f && width <= 1f && height <= 1f) {
                // Already normalized (0-1)
                normalizedXCenter = xCenter.coerceIn(0f, 1f)
                normalizedYCenter = yCenter.coerceIn(0f, 1f)
                normalizedWidth = width.coerceIn(0.01f, 1f)
                normalizedHeight = height.coerceIn(0.01f, 1f)
            } else {
                // Pixel coordinates - normalize them
                normalizedXCenter = (xCenter / imageWidth).coerceIn(0f, 1f)
                normalizedYCenter = (yCenter / imageHeight).coerceIn(0f, 1f)
                normalizedWidth = (width / imageWidth).coerceIn(0.01f, 1f)
                normalizedHeight = (height / imageHeight).coerceIn(0.01f, 1f)
            }
            
            // Convert normalized center coordinates to corner coordinates
            val left = (normalizedXCenter - normalizedWidth / 2) * imageWidth
            val top = (normalizedYCenter - normalizedHeight / 2) * imageHeight
            val right = (normalizedXCenter + normalizedWidth / 2) * imageWidth
            val bottom = (normalizedYCenter + normalizedHeight / 2) * imageHeight
            
            // Clamp to image bounds
            val clampedLeft = left.coerceIn(0f, imageWidth)
            val clampedTop = top.coerceIn(0f, imageHeight)
            val clampedRight = right.coerceIn(0f, imageWidth)
            val clampedBottom = bottom.coerceIn(0f, imageHeight)
            
            // Skip invalid boxes
            if (clampedLeft >= clampedRight || clampedTop >= clampedBottom) continue
            
            // Filter out very small boxes (likely false positives)
            val boxWidth = clampedRight - clampedLeft
            val boxHeight = clampedBottom - clampedTop
            val minBoxSize = minOf(imageWidth, imageHeight) * minBoxSizeRatio
            if (boxWidth < minBoxSize || boxHeight < minBoxSize) {
                if (detections.size < 3) {
                    Log.d("YOLODetector", "Filtered small box: ${boxWidth.toInt()}x${boxHeight.toInt()} (min: ${minBoxSize.toInt()})")
                }
                continue
            }
            
            // Get label
            val label = if (maxClassIndex < labels.size && labels.isNotEmpty()) {
                labels[maxClassIndex]
            } else {
                Log.w("YOLODetector", "Class index $maxClassIndex out of range (labels size: ${labels.size})")
                "Class $maxClassIndex"
            }
            
            // Log detections for debugging
            Log.d("YOLODetector", "✓ Detection: $label (${(finalConfidence * 100).toInt()}%) at [${clampedLeft.toInt()}, ${clampedTop.toInt()}, ${clampedRight.toInt()}, ${clampedBottom.toInt()}]")
            
            detections.add(
                DetectedObject(
                    label = label,
                    confidence = finalConfidence,
                    boundingBox = BoundingBox(
                        left = clampedLeft,
                        top = clampedTop,
                        right = clampedRight,
                        bottom = clampedBottom
                    ),
                    imageWidth = imageWidth,
                    imageHeight = imageHeight
                )
            )
        }
        
        Log.d("YOLODetector", "Parsed $totalBoxes boxes, $boxesWithHighScore above threshold (${(confidenceThreshold * 100).toInt()}%), ${detections.size} valid detections after filtering")
        
        if (detections.isEmpty() && boxesWithHighScore > 0) {
            Log.w("YOLODetector", "Had $boxesWithHighScore boxes above threshold but all were filtered out. Check box size/coordinate filters.")
        }
        
        // Apply Non-Maximum Suppression (NMS)
        return applyNMS(detections)
    }

    private fun applyNMS(detections: List<DetectedObject>): List<DetectedObject> {
        if (detections.isEmpty()) return emptyList()
        
        // Sort by confidence (highest first)
        val sorted = detections.sortedByDescending { it.confidence }
        val selected = mutableListOf<DetectedObject>()
        val suppressed = BooleanArray(sorted.size)
        
        for (i in sorted.indices) {
            if (suppressed[i]) continue
            
            // Add the highest confidence detection
            selected.add(sorted[i])
            
            // Suppress overlapping detections
            for (j in i + 1 until sorted.size) {
                if (suppressed[j]) continue
                
                // Only suppress if same class or high overlap
                val sameClass = sorted[i].label == sorted[j].label
                val iou = calculateIOU(sorted[i].boundingBox, sorted[j].boundingBox)
                
                // Suppress if high overlap (same class) or very high overlap (different classes)
                if (sameClass && iou > nmsThreshold) {
                    suppressed[j] = true
                } else if (!sameClass && iou > 0.7f) {
                    // Suppress different class detections with very high overlap
                    suppressed[j] = true
                }
            }
        }
        
        return selected
    }

    private fun calculateIOU(box1: BoundingBox, box2: BoundingBox): Float {
        val intersectionLeft = maxOf(box1.left, box2.left)
        val intersectionTop = maxOf(box1.top, box2.top)
        val intersectionRight = minOf(box1.right, box2.right)
        val intersectionBottom = minOf(box1.bottom, box2.bottom)
        
        if (intersectionRight <= intersectionLeft || intersectionBottom <= intersectionTop) {
            return 0f
        }
        
        val intersectionArea = (intersectionRight - intersectionLeft) * (intersectionBottom - intersectionTop)
        val box1Area = (box1.right - box1.left) * (box1.bottom - box1.top)
        val box2Area = (box2.right - box2.left) * (box2.bottom - box2.top)
        val unionArea = box1Area + box2Area - intersectionArea
        
        return if (unionArea > 0) intersectionArea / unionArea else 0f
    }

    private fun ImageProxy.convertToBitmap(): Bitmap? {
        return try {
            val yBuffer = planes[0].buffer
            val uBuffer = planes[1].buffer
            val vBuffer = planes[2].buffer

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            val nv21 = ByteArray(ySize + uSize + vSize)
            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)

            val yuvImage = YuvImage(
                nv21,
                ImageFormat.NV21,
                width,
                height,
                null
            )
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(
                Rect(0, 0, width, height),
                100,
                out
            )
            val imageBytes = out.toByteArray()
            android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}

