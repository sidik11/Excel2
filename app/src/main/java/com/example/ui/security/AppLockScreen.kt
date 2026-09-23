package com.example.ui.security

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.util.AppSecurityManager
import com.example.util.BiometricAvailability
import com.example.util.ProfileManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun AppLockScreen(
    onUnlocked: () -> Unit
) {
    val context = LocalContext.current
    val securityConfig by AppSecurityManager.securityConfig.collectAsState()
    val scope = rememberCoroutineScope()
    val userProfile by ProfileManager.userProfile.collectAsState()

    var enteredPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isChecking by remember { mutableStateOf(false) }

    var showBackupSuccessDialog by remember { mutableStateOf(false) }
    var showFaceUnlockDialog by remember { mutableStateOf(false) }
    var showResetPinDialog by remember { mutableStateOf(false) }
    var newPinInput by remember { mutableStateOf("") }
    var resetPinError by remember { mutableStateOf<String?>(null) }

    val shakeOffset = remember { Animatable(0f) }

    fun triggerHaptic(strong: Boolean = false) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val vibrator = vibratorManager?.defaultVibrator
                val effect = if (strong) VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                else VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                vibrator?.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (strong) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    vibrator?.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
                }
            }
        } catch (_: Throwable) {}
    }

    val uploadFingerprintLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val (ok, msg) = AppSecurityManager.verifyAndUnlockWithBackupFingerprint(context, uri)
            if (ok) {
                triggerHaptic(false)
                showBackupSuccessDialog = true
            } else {
                triggerHaptic(true)
                errorMessage = msg
            }
        }
    }

    fun launchBiometricPrompt() {
        val activity = context as? FragmentActivity ?: return
        val bioStatus = AppSecurityManager.checkBiometricStatus(context)
        if (bioStatus != BiometricAvailability.AVAILABLE) return

        val executor = ContextCompat.getMainExecutor(context)
        val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                triggerHaptic(false)
                AppSecurityManager.unlockAppBiometric()
                onUnlocked()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                // Don't show error if user deliberately cancelled to use PIN
                if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    errorMessage = errString.toString()
                }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                triggerHaptic(true)
                errorMessage = "Fingerprint not recognized. Try again or enter PIN."
            }
        })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock App")
            .setSubtitle("Authenticate with fingerprint or biometric")
            .setNegativeButtonText("Use 6-Digit PIN")
            .build()

        try {
            prompt.authenticate(promptInfo)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    // Auto-launch biometric on initial load if enabled
    LaunchedEffect(securityConfig.isFingerprintEnabled) {
        if (securityConfig.isFingerprintEnabled) {
            delay(300)
            launchBiometricPrompt()
        }
    }

    // Check PIN when 6 digits are entered
    LaunchedEffect(enteredPin) {
        if (enteredPin.length == 6) {
            isChecking = true
            delay(100)
            val isValid = AppSecurityManager.verifyPin(enteredPin, context)
            if (isValid) {
                triggerHaptic(false)
                errorMessage = null
                onUnlocked()
            } else {
                triggerHaptic(true)
                errorMessage = "Incorrect PIN. Please try again."
                // Shake animation
                scope.launch {
                    shakeOffset.animateTo(20f, animationSpec = tween(50))
                    shakeOffset.animateTo(-20f, animationSpec = tween(50))
                    shakeOffset.animateTo(15f, animationSpec = tween(50))
                    shakeOffset.animateTo(-15f, animationSpec = tween(50))
                    shakeOffset.animateTo(0f, animationSpec = tween(50))
                }
                delay(400)
                enteredPin = ""
                isChecking = false
            }
        }
    }

    val themePrimary = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
            .padding(horizontal = 24.dp, vertical = 32.dp)
            .testTag("app_lock_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(shakeOffset.value.roundToInt(), 0) },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Section
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 40.dp)
            ) {
                val prof = userProfile
                val profileImageBase64 = prof?.profileImageBase64
                val profileBitmap: android.graphics.Bitmap? = remember(profileImageBase64) {
                    if (!profileImageBase64.isNullOrBlank()) {
                        ProfileManager.base64ToBitmap(profileImageBase64)
                    } else null
                }

                Surface(
                    shape = CircleShape,
                    color = themePrimary.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(2.dp, themePrimary),
                    modifier = Modifier.size(76.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (profileBitmap != null) {
                            androidx.compose.foundation.Image(
                                bitmap = profileBitmap.asImageBitmap(),
                                contentDescription = "Profile Photo",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = if (prof != null && prof.isComplete()) Icons.Default.Person else Icons.Default.Lock,
                                contentDescription = "Lock",
                                tint = themePrimary,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = if (prof != null && prof.fullName.isNotBlank()) "Welcome, ${prof.fullName}" else "Excel & Image Vault",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = if (prof != null && prof.device.isNotBlank()) "${prof.device} • Enter PIN" else "Enter 6-Digit PIN to unlock",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(28.dp))

                // 6-Digit Indicators
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 0 until 6) {
                        val isFilled = i < enteredPin.length
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isFilled) themePrimary else Color.Transparent
                                )
                                .border(
                                    width = 2.dp,
                                    color = if (isFilled) themePrimary else MaterialTheme.colorScheme.outline,
                                    shape = CircleShape
                                )
                                .testTag("pin_indicator_$i")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Text(
                        text = "Protected with AES-256 & Secure Media Storage",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Numeric Keypad
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                val numRows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9")
                )

                for (row in numRows) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        for (digit in row) {
                            KeypadButton(
                                label = digit,
                                onClick = {
                                    if (enteredPin.length < 6 && !isChecking) {
                                        triggerHaptic(false)
                                        enteredPin += digit
                                        errorMessage = null
                                    }
                                },
                                testTag = "keypad_btn_$digit"
                            )
                        }
                    }
                }

                // Bottom Row: [Biometric / Empty], [0], [Backspace]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Biometric Button
                    if (securityConfig.isFingerprintEnabled) {
                        Surface(
                            shape = CircleShape,
                            color = themePrimary.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, themePrimary.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .clickable {
                                    triggerHaptic(false)
                                    launchBiometricPrompt()
                                }
                                .testTag("btn_lock_biometric")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Fingerprint,
                                    contentDescription = "Fingerprint Unlock",
                                    tint = themePrimary,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.size(72.dp))
                    }

                    // Digit 0
                    KeypadButton(
                        label = "0",
                        onClick = {
                            if (enteredPin.length < 6 && !isChecking) {
                                triggerHaptic(false)
                                enteredPin += "0"
                                errorMessage = null
                            }
                        },
                        testTag = "keypad_btn_0"
                    )

                    // Backspace Button
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .clickable {
                                if (enteredPin.isNotEmpty() && !isChecking) {
                                    triggerHaptic(false)
                                    enteredPin = enteredPin.dropLast(1)
                                    errorMessage = null
                                }
                            }
                            .testTag("keypad_btn_backspace")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Backspace,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Face Unlock Button
                    if (securityConfig.isFaceLockEnabled) {
                        OutlinedButton(
                            onClick = { showFaceUnlockDialog = true },
                            shape = RoundedCornerShape(24.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF06B6D4)),
                            modifier = Modifier.testTag("btn_use_facelock")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Face,
                                contentDescription = "Face Unlock",
                                modifier = Modifier.size(18.dp),
                                tint = Color(0xFF06B6D4)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Face Unlock",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF06B6D4)
                            )
                        }
                    }

                    // Option to use Backup Fingerprint (for forgotten PIN or new device)
                    OutlinedButton(
                        onClick = {
                            try {
                                uploadFingerprintLauncher.launch(arrayOf("*/*"))
                            } catch (_: Throwable) {
                                Toast.makeText(context, "Could not open file chooser.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(24.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, themePrimary.copy(alpha = 0.5f)),
                        modifier = Modifier.testTag("btn_use_backup_fingerprint")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = themePrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Backup Credential",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = themePrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }

    // Dialog when unlocked via backup fingerprint file
    if (showBackupSuccessDialog) {
        AlertDialog(
            onDismissRequest = {
                showBackupSuccessDialog = false
                onUnlocked()
            },
            icon = {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF00B894),
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text("Fingerprint Verified!", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            },
            text = {
                val restored = userProfile
                val isPinAutoEnabled = securityConfig.isPinEnabled
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Backup fingerprint file was verified successfully. The app is now unlocked.",
                        fontSize = 14.sp
                    )
                    if (restored != null && restored.fullName.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    "Profile: ${restored.fullName}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (restored.googleEmail.isNotBlank()) {
                                    Text(
                                        "Google Account: ${restored.googleEmail}",
                                        fontSize = 11.sp,
                                        color = Color(0xFF00B894),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                if (isPinAutoEnabled) {
                                    Text(
                                        "6-Digit Password: Auto-enabled & synchronized",
                                        fontSize = 11.sp,
                                        color = themePrimary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        if (isPinAutoEnabled) "Your 6-digit password has been auto-enabled. Would you like to change your 6-digit PIN or continue?"
                        else "Would you like to set a new 6-digit PIN for this device?",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showBackupSuccessDialog = false
                        showResetPinDialog = true
                    }
                ) {
                    Text(if (securityConfig.isPinEnabled) "Change PIN" else "Set New PIN")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showBackupSuccessDialog = false
                        onUnlocked()
                    }
                ) {
                    Text("Continue to App")
                }
            }
        )
    }

    // Dialog to reset 6-digit PIN after unlocking with backup fingerprint
    if (showResetPinDialog) {
        AlertDialog(
            onDismissRequest = {
                showResetPinDialog = false
                onUnlocked()
            },
            icon = {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    tint = themePrimary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text("Set New 6-Digit PIN", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Enter a new 6-digit PIN to secure your app:", fontSize = 13.sp)
                    OutlinedTextField(
                        value = newPinInput,
                        onValueChange = { input ->
                            if (input.length <= 6 && input.all { it.isDigit() }) {
                                newPinInput = input
                                resetPinError = null
                            }
                        },
                        label = { Text("New 6-Digit PIN") },
                        singleLine = true,
                        isError = resetPinError != null,
                        modifier = Modifier.fillMaxWidth().testTag("input_new_pin_after_backup")
                    )
                    if (resetPinError != null) {
                        Text(
                            resetPinError ?: "",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPinInput.length != 6) {
                            resetPinError = "PIN must be exactly 6 digits."
                        } else {
                            val ok = AppSecurityManager.set6DigitPin(context, newPinInput)
                            if (ok) {
                                Toast.makeText(context, "New PIN saved successfully!", Toast.LENGTH_SHORT).show()
                                showResetPinDialog = false
                                onUnlocked()
                            } else {
                                resetPinError = "Failed to save new PIN."
                            }
                        }
                    },
                    enabled = newPinInput.length == 6
                ) {
                    Text("Save PIN")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showResetPinDialog = false
                        onUnlocked()
                    }
                ) {
                    Text("Skip")
                }
            }
        )
    }

    if (showFaceUnlockDialog) {
        FaceUnlockDialog(
            onDismiss = { showFaceUnlockDialog = false },
            onUnlocked = {
                showFaceUnlockDialog = false
                onUnlocked()
            }
        )
    }
}

@Composable
private fun KeypadButton(
    label: String,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
