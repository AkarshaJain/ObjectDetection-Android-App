package com.mlimageclassifier.ui

import android.Manifest
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionHandler(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val permissionsState = rememberMultiplePermissionsState(
        permissions = listOf(Manifest.permission.CAMERA)
    )

    when {
        permissionsState.allPermissionsGranted -> {
            content()
        }
        permissionsState.shouldShowRationale -> {
            PermissionRationaleScreen(
                onRequestPermission = { permissionsState.launchMultiplePermissionRequest() }
            )
        }
        else -> {
            PermissionRequestScreen(
                onRequestPermission = { permissionsState.launchMultiplePermissionRequest() }
            )
        }
    }
}

@Composable
fun PermissionRequestScreen(
    onRequestPermission: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Camera Permission Required",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            Text(
                text = "This app needs camera access to perform real-time image classification.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onRequestPermission,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text("Grant Permission")
            }
        }
    }
}

@Composable
fun PermissionRationaleScreen(
    onRequestPermission: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Camera Permission Denied",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Please grant camera permission in settings to use this app.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onRequestPermission,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text("Request Again")
            }
        }
    }
}

