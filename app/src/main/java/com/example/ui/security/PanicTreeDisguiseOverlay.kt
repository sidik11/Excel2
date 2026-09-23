package com.example.ui.security

import android.app.Activity
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.util.ActivityLogManager
import com.example.util.AppSecurityManager
import com.example.util.AppStorageHelper
import com.example.util.SettingsManager
import kotlinx.coroutines.delay
import java.io.File

/**
 * Full-screen Panic Mode Disguise Overlay.
 * Displays the magical bioluminescent green tree (or user custom panic wallpaper)
 * for exactly 3 seconds, then securely locks and exits the app.
 */
@Composable
fun PanicTreeDisguiseOverlay(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val settings by SettingsManager.settings.collectAsState()
    val progress = remember { Animatable(0f) }

    val customBitmap = remember(settings.panicCustomImageUri) {
        val pathOrUri = settings.panicCustomImageUri
        var bitmap: android.graphics.Bitmap? = null
        if (pathOrUri.isNotBlank()) {
            try {
                val file = File(pathOrUri)
                if (file.exists() && file.length() > 0) {
                    bitmap = BitmapFactory.decodeFile(file.absolutePath)
                }
            } catch (_: Throwable) {}

            if (bitmap == null) {
                try {
                    val uri = Uri.parse(pathOrUri)
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        bitmap = BitmapFactory.decodeStream(stream)
                    }
                } catch (_: Throwable) {}
            }
        }

        // Also fallback to the saved disk file if set
        if (bitmap == null && pathOrUri.isNotBlank()) {
            try {
                val defaultPanicFile = File(AppStorageHelper.getSecurityDir(context), "panic_custom_disguise.jpg")
                if (defaultPanicFile.exists() && defaultPanicFile.length() > 0) {
                    bitmap = BitmapFactory.decodeFile(defaultPanicFile.absolutePath)
                }
            } catch (_: Throwable) {}
        }
        bitmap
    }

    LaunchedEffect(Unit) {
        // Log panic event
        ActivityLogManager.log(
            context = context,
            type = "LOCK_PANIC",
            title = "Panic Shake Lock Triggered",
            description = "Device shaken continuously for ${settings.shakeToLockDurationSeconds}s. Panic image displayed for 3 seconds before lock & exit.",
            severity = "DANGER"
        )

        // Animate 3-second countdown
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 3000, easing = LinearEasing)
        )

        // Lock app and exit
        AppSecurityManager.lockApp()
        onDismiss()
        (context as? Activity)?.finishAffinity()
    }

    Dialog(
        onDismissRequest = { /* Cannot dismiss during panic lock */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // Display Image: custom bitmap if set, otherwise default panic disguise tree
            if (customBitmap != null) {
                Image(
                    bitmap = customBitmap.asImageBitmap(),
                    contentDescription = "Panic Screen Wallpaper",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Image(
                    painter = painterResource(id = R.drawable.panic_disguise_tree),
                    contentDescription = "Panic Screen Tree Wallpaper",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Subtle 3-second progress indicator at the bottom edge
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                LinearProgressIndicator(
                    progress = { progress.value },
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(3.dp),
                    color = Color(0xFF00E676),
                    trackColor = Color.Black.copy(alpha = 0.5f)
                )
            }
        }
    }
}
