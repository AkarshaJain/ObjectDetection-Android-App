# YOLOv8 Object Detection - Android App

A real-time object detection Android application using YOLOv8 TensorFlow Lite model. Built with Kotlin, Jetpack Compose, and MVVM architecture.

## Features

- 🎯 Real-time object detection using YOLOv8
- 📦 Bounding boxes with class labels and confidence scores
- 🏗️ Clean MVVM architecture
- 🎨 Modern Jetpack Compose UI
- 📷 CameraX integration for camera operations
- 🤖 On-device TensorFlow Lite inference
- 🔐 Graceful permission handling

## 📸 Screenshots

*Add screenshots of your app here*

## 🏗️ Architecture

The app follows **MVVM (Model-View-ViewModel)** architecture:

- **Model**: `DetectedObject`, `ObjectDetectionRepository`
- **View**: Jetpack Compose UI (`CameraPreview`, `DetectionOverlay`, `PermissionHandler`)
- **ViewModel**: `ObjectDetectionViewModel` - manages UI state and business logic


## 🚀 Getting Started

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- Android SDK 24+ (minimum), 34 (target)
- JDK 17 or later
- Android device or emulator with camera support

### Installation

1. **Clone the repository**
   ```bash
   git clone https://github.com/yourusername/yolov8-android-detection.git
   cd yolov8-android-detection
   ```

2. **Download YOLOv8 Model**
   - Visit [Ultralytics YOLOv8n TFLite](https://huggingface.co/ultralytics/yolov8n-tflite)
   - Go to "Files and versions" tab
   - Download `yolov8n_float32.tflite`
   - Rename to `yolov8.tflite`
   - Place in `app/src/main/assets/yolov8.tflite`

3. **Open in Android Studio**
   - File → Open → Select project directory
   - Wait for Gradle sync to complete

4. **Run the app**
   - Connect device or start emulator
   - Click Run button (▶️) or press `Shift+F10`

