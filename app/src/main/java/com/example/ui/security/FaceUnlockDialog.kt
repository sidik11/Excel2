package com.example.ui.security

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.util.AppSecurityManager
import com.example.util.FaceLockStore
import com.example.util.FaceRecognitionEngine
import com.example.util.FaceLockStore.MatchResult
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.delay
import java.util.concurrent.Executors

@Composable
fun FaceUnlockDialog(
    onDismiss: () -> Unit,
    onUnlocked: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var status by remember { mutableStateOf("Look at the front camera to unlock.") }
    var matched by remember { mutableStateOf(false) }
    var attempts by remember { mutableIntStateOf(0) }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val storedTemplate = remember { FaceLockStore.loadTemplate(context) }

    val detector = remember {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setMinFaceSize(0.15f)
                .build()
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { hasPermission = it }

    fun stopCamera() {
        try { provider?.unbindAll() } catch (_: Throwable) {}
    }

    DisposableEffect(Unit) {
        onDispose {
            stopCamera()
            try { detector.close() } catch (_: Throwable) {}
            executor.shutdown()
        }
    }

    fun bindCamera(previewView: PreviewView) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val cameraProvider = future.get()
                provider = cameraProvider
                cameraProvider.unbindAll()
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                analysis.setAnalyzer(executor) { imageProxy ->
                    val mediaImage = imageProxy.image
                    if (mediaImage == null || matched) {
                        imageProxy.close()
                        return@setAnalyzer
                    }
                    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                    detector.process(image)
                        .addOnSuccessListener { faces ->
                            val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                            if (face != null && !matched) {
                                val live = FaceRecognitionEngine.vector(face)
                                val result: MatchResult? = storedTemplate?.let { FaceLockStore.compare(it, live) }
                                ContextCompat.getMainExecutor(context).execute {
                                    attempts++
                                    if (result?.matched == true) {
                                        matched = true
                                        status = "Face match verified. Opening vault…"
                                        stopCamera()
                                        AppSecurityManager.unlockApp()
                                        onUnlocked()
                                    } else if (attempts < 40) {
                                        status = "Face detected. Matching…"
                                    } else {
                                        status = "Face did not match. Re-center and tap Retry."
                                    }
                                }
                            } else if (!matched) {
                                ContextCompat.getMainExecutor(context).execute {
                                    status = "No clear face detected. Center your face."
                                }
                            }
                        }
                        .addOnCompleteListener { imageProxy.close() }
                }

                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    analysis
                )
            } catch (e: Throwable) {
                status = "Could not start front camera: ${e.message}"
            }
        }, ContextCompat.getMainExecutor(context))
    }

    LaunchedEffect(hasPermission, storedTemplate) {
        if (!hasPermission) return@LaunchedEffect
        if (storedTemplate == null) {
            status = "No registered face template was found. Set up Face Lock first."
        } else {
            delay(350)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.92f).wrapContentHeight().clip(RoundedCornerShape(24.dp))
                .testTag("face_unlock_dialog"),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(22.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(40.dp)) {
                            Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Face, null, tint = MaterialTheme.colorScheme.primary) }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Face Unlock", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Text("Camera-only verification", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close") }
                }

                Spacer(Modifier.height(18.dp))

                when {
                    !hasPermission -> {
                        Text("Camera permission is required. Fingerprint is not used by Face Unlock.", textAlign = TextAlign.Center)
                        Spacer(Modifier.height(14.dp))
                        Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) { Text("Allow Camera") }
                    }
                    storedTemplate == null -> {
                        Text("No Face Lock template is available on this device.", textAlign = TextAlign.Center)
                        Spacer(Modifier.height(14.dp))
                        OutlinedButton(onClick = onDismiss) { Text("Close") }
                    }
                    else -> {
                        Box(
                            modifier = Modifier.size(260.dp).clip(CircleShape)
                                .background(Color.Black)
                                .border(3.dp, if (matched) Color(0xFF10B981) else MaterialTheme.colorScheme.primary, CircleShape)
                        ) {
                            AndroidView(
                                factory = { PreviewView(context).also { bindCamera(it) } },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(status, textAlign = TextAlign.Center, fontSize = 13.sp)
                        Spacer(Modifier.height(10.dp))
                        if (!matched && attempts >= 40) {
                            Button(
                                onClick = {
                                    attempts = 0
                                    status = "Retrying face match…"
                                },
                                modifier = Modifier.fillMaxWidth().testTag("btn_trigger_face_scan")
                            ) { Text("Retry Face Scan") }
                        } else if (matched) {
                            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Open") }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Face Lock uses the encrypted local facial template in Android/media/${context.packageName}/facelock. It does not call the fingerprint biometric prompt.",
                            textAlign = TextAlign.Center,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
