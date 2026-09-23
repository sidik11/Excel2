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
import com.example.util.FaceRecognitionEngine
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

@Composable
fun FaceLockSetupDialog(
    onDismiss: () -> Unit,
    onEnrolled: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var status by remember { mutableStateOf("Register your face using the front camera.") }
    var progress by remember { mutableFloatStateOf(0f) }
    var vectors by remember { mutableStateOf<List<List<Float>>>(emptyList()) }
    var finished by remember { mutableStateOf(false) }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (!granted) Toast.makeText(context, "Camera permission is required for Face Lock.", Toast.LENGTH_LONG).show()
    }

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

    fun stopCamera() {
        try { provider?.unbindAll() } catch (_: Throwable) {}
    }

    DisposableEffect(Unit) {
        onDispose {
            stopCamera()
            try { detector.close() } catch (_: Throwable) {}
            analysisExecutor.shutdown()
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

                analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                    val mediaImage = imageProxy.image
                    if (mediaImage == null || finished) {
                        imageProxy.close()
                        return@setAnalyzer
                    }
                    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                    detector.process(image)
                        .addOnSuccessListener { faces ->
                            val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                            if (face != null && !finished) {
                                val vector = FaceRecognitionEngine.vector(face)
                                ContextCompat.getMainExecutor(context).execute {
                                    if (vectors.size < 8) {
                                        vectors = vectors + listOf(vector)
                                        progress = (vectors.size / 8f).coerceAtMost(0.92f)
                                        status = "Face detected. Keep your head steady… ${vectors.size}/8"
                                    }
                                }
                            } else if (!finished) {
                                ContextCompat.getMainExecutor(context).execute {
                                    status = "No clear face detected. Center your face in the frame."
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

    LaunchedEffect(vectors.size, finished) {
        if (!finished && vectors.size >= 8) {
            status = "Building your encrypted face template…"
            progress = 0.96f
            val size = vectors.first().size
            val average = List(size) { i -> vectors.map { it[i] }.average().toFloat() }
            val (ok, message) = AppSecurityManager.enrollFace(context, average)
            if (ok) {
                progress = 1f
                finished = true
                stopCamera()
                status = "Face Lock registered successfully. Fingerprint is not required."
                Toast.makeText(context, "Face Lock registered.", Toast.LENGTH_SHORT).show()
                onEnrolled()
            } else {
                vectors = emptyList()
                progress = 0f
                status = message
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.92f).wrapContentHeight().clip(RoundedCornerShape(24.dp))
                .testTag("face_lock_setup_dialog"),
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
                            Text("Face Lock Setup", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Text("Camera-only face matching", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close") }
                }

                Spacer(Modifier.height(18.dp))

                if (!hasPermission) {
                    Text(
                        "Face Lock uses the front camera. It does not use fingerprint authentication.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) { Text("Allow Camera") }
                } else if (!finished) {
                    Box(
                        modifier = Modifier.size(260.dp).clip(CircleShape)
                            .background(Color.Black)
                            .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    ) {
                        AndroidView(
                            factory = { PreviewView(context).also { bindCamera(it) } },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    Text(status, textAlign = TextAlign.Center, fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Keep your face centered and steady. The app stores an encrypted facial-geometry template, not a photo.",
                        textAlign = TextAlign.Center,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text("✓ Face Lock Registered", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                    Spacer(Modifier.height(8.dp))
                    Text(status, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(18.dp))
                    Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Done") }
                }
            }
        }
    }
}
