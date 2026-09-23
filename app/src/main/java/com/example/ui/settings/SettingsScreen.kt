package com.example.ui.settings

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NoPhotography
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.fragment.app.FragmentActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppTheme
import com.example.ui.security.ChangeMasterPasswordDialog
import com.example.ui.security.FaceLockSetupDialog
import com.example.ui.security.PinManagementDialog
import com.example.ui.security.SecurityFolderInspectorDialog
import com.example.ui.profile.ProfileDialog
import com.example.util.AppSecurityManager
import com.example.util.AppStorageHelper
import com.example.util.BiometricAvailability
import com.example.util.ProfileManager
import com.example.util.SettingsManager

@Composable
fun SettingsScreen(
    onRestoreComplete: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val settings by SettingsManager.settings.collectAsState()
    val securityConfig by AppSecurityManager.securityConfig.collectAsState()
    val context = LocalContext.current
    val dedicatedDir = AppStorageHelper.getDedicatedMediaDir(context)

    var showPinDialog by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showSecurityFolderDialog by remember { mutableStateOf(false) }
    var showManualDialog by remember { mutableStateOf(false) }
    var showProfileDialog by remember { mutableStateOf(false) }
    var showPathsDialog by remember { mutableStateOf(false) }
    var showLogoDialog by remember { mutableStateOf(false) }
    var showShareQrDialog by remember { mutableStateOf(false) }
    var showDualVaultDialog by remember { mutableStateOf(false) }
    var showFileManagerDialog by remember { mutableStateOf(false) }
    var showFaceSetupDialog by remember { mutableStateOf(false) }

    val bioStatus = remember { AppSecurityManager.checkBiometricStatus(context) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .padding(bottom = 80.dp)
    ) {
        // Title
        Text(
            text = "⚙️ App Settings & Customization",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Advanced controls for security, PIN, biometric, storage, themes & playback",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(18.dp))

        // SECTION 0: 🛡️ App Security & Privacy
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth().testTag("section_app_security")
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("App Security & Biometrics", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                    }

                    Surface(
                        color = if (securityConfig.isPinEnabled) Color(0xFF00B894).copy(alpha = 0.2f) else MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = if (securityConfig.isPinEnabled) "PIN ACTIVE" else "UNPROTECTED",
                            color = if (securityConfig.isPinEnabled) Color(0xFF00B894) else MaterialTheme.colorScheme.error,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // User Profile Management Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("User Profile & ID", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            val hasProfile = ProfileManager.hasCompleteProfile()
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (hasProfile) Color(0xFF00B894).copy(alpha = 0.15f) else MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = if (hasProfile) "COMPLETE" else "REQUIRED",
                                    color = if (hasProfile) Color(0xFF00B894) else MaterialTheme.colorScheme.error,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        val profile = ProfileManager.userProfile.collectAsState().value
                        val googleConnected = profile?.isGoogleAccountConnected() == true
                        Text(
                            if (profile != null && profile.fullName.isNotBlank()) {
                                if (googleConnected) "Linked to: ${profile.fullName} • Google: ${profile.googleEmail}"
                                else "⚠️ ${profile.fullName} • Mandatory: Connect Google Account"
                            } else "Setup profile with picture, contact, address & mandatory Google account.",
                            fontSize = 12.sp,
                            color = if (!googleConnected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = { showProfileDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("btn_settings_open_profile")
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (ProfileManager.hasCompleteProfile()) "View Profile" else "Setup Profile", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outline)

                // 6-Digit PIN App Lock Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("6-Digit App Lock PIN", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            if (securityConfig.isPinEnabled) "App requires 6-digit PIN on open." else "Require 6-digit PIN when opening app.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = { showPinDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (securityConfig.isPinEnabled) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary,
                            contentColor = if (securityConfig.isPinEnabled) MaterialTheme.colorScheme.onSurfaceVariant else Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("btn_setup_change_pin")
                    ) {
                        Icon(
                            imageVector = if (securityConfig.isPinEnabled) Icons.Default.Key else Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (securityConfig.isPinEnabled) "Change PIN" else "Set PIN", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outline)

                // Biometric / Fingerprint Unlock Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Fingerprint / Biometric Unlock", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            val (dotColor, statusLabel) = when (bioStatus) {
                                BiometricAvailability.AVAILABLE -> Color(0xFF00B894) to "Available"
                                BiometricAvailability.NONE_ENROLLED -> Color(0xFFFDCB6E) to "Not Enrolled"
                                BiometricAvailability.NO_HARDWARE -> Color.Gray to "No Sensor"
                                else -> Color.Gray to "Unavailable"
                            }
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(dotColor)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(statusLabel, fontSize = 10.sp, color = dotColor, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            "Use device fingerprint sensor to unlock app immediately without typing PIN.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = securityConfig.isFingerprintEnabled,
                        onCheckedChange = { isEnabled ->
                            val (ok, msg) = AppSecurityManager.setFingerprintEnabled(context, isEnabled)
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        },
                        enabled = securityConfig.isPinEnabled && bioStatus == BiometricAvailability.AVAILABLE,
                        colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.testTag("toggle_fingerprint_unlock")
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // FINGERPRINT REGISTRATION & BACKUP IN SECURITY FOLDER
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, if (securityConfig.isFingerprintRegistered) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Fingerprint,
                                    contentDescription = null,
                                    tint = if (securityConfig.isFingerprintRegistered) Color(0xFF00B894) else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Fingerprint Registration",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                            if (securityConfig.isFingerprintRegistered) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFF00B894).copy(alpha = 0.15f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF00B894), modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Registered", fontSize = 10.sp, color = Color(0xFF00B894), fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Register your fingerprint to create a secure backup containing your profile, connected Google account, and 6-digit password. When placed on another device, all profile info and your 6-digit password are auto-added and enabled.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        val hasProfile = ProfileManager.hasCompleteProfile()
                        if (!hasProfile) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "Profile required: You must complete all Profile fields before registering fingerprint.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        if (securityConfig.isFingerprintRegistered) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text("Backup Stored In:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    Text(
                                        "${AppStorageHelper.getFingerprintDir(context).path}/fingerprint.dat",
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (securityConfig.fingerprintRegisteredAt > 0) {
                                        val dateStr = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(securityConfig.fingerprintRegisteredAt))
                                        Text("Registered: $dateStr (with User Profile embedded)", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (!ProfileManager.hasCompleteProfile()) {
                                        Toast.makeText(context, "Please complete your Profile first before registering fingerprint.", Toast.LENGTH_LONG).show()
                                        showProfileDialog = true
                                        return@Button
                                    }
                                    val activity = context as? FragmentActivity
                                    if (activity != null) {
                                        AppSecurityManager.registerFingerprintWithBiometric(activity) { ok, msg ->
                                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                        }
                                    } else {
                                        val (ok, msg) = AppSecurityManager.registerFingerprintCredential(context)
                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f).testTag("btn_register_fingerprint")
                            ) {
                                Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    if (securityConfig.isFingerprintRegistered) "Re-Register Fingerprint" else "Register Fingerprint",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (securityConfig.isFingerprintRegistered) {
                                OutlinedButton(
                                    onClick = {
                                        val shareIntent = AppSecurityManager.createFingerprintShareIntent(context)
                                        if (shareIntent != null) {
                                            context.startActivity(Intent.createChooser(shareIntent, "Export Fingerprint Backup File"))
                                        } else {
                                            Toast.makeText(context, "No backup file found to export.", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.testTag("btn_share_fingerprint_backup")
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Export", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outline)

                // Anti-Screenshot & Screen Recording Prevention Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.NoPhotography, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Anti-Screenshot & Recording", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                        Text(
                            "Blocks screenshots, screen recording, and masks app preview in Recent Apps (FLAG_SECURE).",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = securityConfig.isAntiScreenshotEnabled,
                        onCheckedChange = { isEnabled ->
                            AppSecurityManager.setAntiScreenshotEnabled(context, isEnabled)
                            val status = if (isEnabled) "Screen protection enabled (screenshots & recording blocked)." else "Screen protection disabled."
                            Toast.makeText(context, status, Toast.LENGTH_SHORT).show()
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.testTag("toggle_anti_screenshot")
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outline)

                // Face Recognition Lock (Real Camera & Biometric Verification)
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Face, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Real Face Recognition Lock", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (securityConfig.isFaceEnrolled) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFFF59E0B).copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = if (securityConfig.isFaceEnrolled) "ENROLLED" else "SETUP REQUIRED",
                                        color = if (securityConfig.isFaceEnrolled) Color(0xFF10B981) else Color(0xFFF59E0B),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                if (securityConfig.isFaceEnrolled) {
                                    val dateStr = if (securityConfig.faceEnrolledAt > 0) {
                                        SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(securityConfig.faceEnrolledAt))
                                    } else "Active"
                                    "Face calibrated ($dateStr) • Front camera verification on startup."
                                } else {
                                    "Must complete face calibration setup first before enabling."
                                },
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = securityConfig.isFaceLockEnabled && securityConfig.isFaceEnrolled,
                            onCheckedChange = { isEnabled ->
                                if (isEnabled && !securityConfig.isFaceEnrolled) {
                                    Toast.makeText(context, "Please set up your face recognition first.", Toast.LENGTH_LONG).show()
                                    showFaceSetupDialog = true
                                } else {
                                    AppSecurityManager.setFaceLockEnabled(context, isEnabled)
                                    val status = if (isEnabled) "Face lock enabled." else "Face lock disabled."
                                    Toast.makeText(context, status, Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.testTag("toggle_face_lock")
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showFaceSetupDialog = true },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).testTag("btn_setup_face_lock")
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (securityConfig.isFaceEnrolled) "Re-Enroll Face" else "Set Up Face Lock",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (securityConfig.isFaceEnrolled) {
                            OutlinedButton(
                                onClick = {
                                    AppSecurityManager.removeEnrolledFace(context)
                                    Toast.makeText(context, "Face registration removed.", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.testTag("btn_remove_face_lock")
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Remove", fontSize = 12.sp)
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outline)

                // Master Password Change Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("DAT Vault Master Password", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            "Change container encryption password stored in secure storage.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = { showPasswordDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("btn_change_master_password")
                    ) {
                        Text("Change Password", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outline)

                // Security Folder Location Details
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Security Storage Location:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${AppStorageHelper.getSecurityDir(context).path}/",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { showSecurityFolderDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth().testTag("btn_inspect_security_folder")
                        ) {
                            Text("Inspect Security Files & Credentials", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // SECTION 1: 🔐 Persistent Vault Session
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LockOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Vault Persistence & Security", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Keep Vault Unlocked Across Reopen", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            "Reopening the app keeps the vault open until you manually tap 'Lock & Clean'.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.keepVaultUnlockedAcrossRestarts,
                        onCheckedChange = { SettingsManager.setKeepVaultUnlockedAcrossRestarts(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.testTag("toggle_keep_vault_unlocked")
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outline)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-Sync EX Vault on Resume", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            "Detects new or removed images in EX Vault when returning from file manager.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.autoSyncExVault,
                        onCheckedChange = { SettingsManager.setAutoSyncExVault(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.testTag("toggle_auto_sync_ex_vault")
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outline)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-Clean EX Vault on Lock", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            "Wipe decrypted images from EX Vault immediately when Lock & Clean is tapped.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.cleanExVaultOnLock,
                        onCheckedChange = { SettingsManager.setCleanExVaultOnLock(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.testTag("toggle_clean_ex_on_lock")
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outline)

                Text("Auto-Lock Security Timer:", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text(
                    "Automatically re-lock vault if app is in background for longer than:",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(0 to "Never", 5 to "5 min", 15 to "15 min", 30 to "30 min").forEach { (mins, label) ->
                        FilterChip(
                            selected = settings.autoLockTimeoutMinutes == mins,
                            onClick = { SettingsManager.setAutoLockTimeoutMinutes(mins) },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.testTag("chip_autolock_$mins")
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outline)

                // 🛡️ RECENT APP PRIVACY (Masks overview in multitasking switcher)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Recent App Privacy (Switcher Blur)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            "Mask and blank app screen in Android Recent Apps overview & switcher.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.recentAppPrivacy,
                        onCheckedChange = { SettingsManager.setRecentAppPrivacy(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.testTag("toggle_recent_app_privacy")
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outline)

                // 📁 SHOW CONNECTED PATHS (PIN / Fingerprint protected)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Show Connected Paths", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            "PIN / Fingerprint protected. View all media, vault, and database folder paths.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = { showPathsDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("btn_show_paths")
                    ) {
                        Icon(Icons.Default.FolderSpecial, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Show Paths", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outline)

                // 🤝 DUAL USER COMBINED VAULT (Firebase Bridge)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth().testTag("card_dual_vault_connect")
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Group,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Dual User Combined Vault",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Pair with another user via 10-digit code using Firebase bridge. All photos stored securely in Android/media/Dual_Vault without storing images on cloud.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { showDualVaultDialog = true },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().testTag("btn_connect_other_user")
                        ) {
                            Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Connect with Other User (Show / Enter Code)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // SECTION 2: 📁 Dedicated Storage & Zero-RAM Direct Storage
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Storage & Low RAM Architecture", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Use Storage Instead of RAM", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            "Streams encryption, decryption, and archives directly through disk storage. Works seamlessly with 1,000+ images without OOM.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.useInternalStorageReplaceRam,
                        onCheckedChange = { SettingsManager.setUseInternalStorageReplaceRam(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.testTag("toggle_storage_replace_ram")
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outline)

                Text("Dedicated Media Directory:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(
                    text = dedicatedDir.absolutePath,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        AppStorageHelper.clearTempWorkspaces(context)
                        Toast.makeText(context, "Temporary cache and workspaces cleaned!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().testTag("btn_clear_temp_cache")
                ) {
                    Icon(Icons.Default.CleaningServices, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Clean Temporary Cache & Workspace", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outline)

                // Password-Protected Media File Manager
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FolderSpecial, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("App Media File Manager", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                        Text(
                            "Password-protected browser to inspect app media folders, preview files, and zoom images.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = { showFileManagerDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("btn_open_file_manager")
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Open", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // SECTION 3: 🖼 Grid & Visual Display Customization
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.GridView, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Gallery & Grid Customization", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(modifier = Modifier.height(12.dp))

                Text("Grid Columns: ${settings.gridColumns}", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(2, 3, 4, 5).forEach { col ->
                        FilterChip(
                            selected = settings.gridColumns == col,
                            onClick = { SettingsManager.setGridColumns(col) },
                            label = { Text("$col Col") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.testTag("chip_grid_col_$col")
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outline)

                Text("Image Fit in Viewer:", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("fit" to "Fit Screen (Show All)", "crop" to "Fill (Zoom & Crop)").forEach { (mode, label) ->
                        FilterChip(
                            selected = settings.imageFitMode == mode,
                            onClick = { SettingsManager.setImageFitMode(mode) },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.testTag("chip_fit_$mode")
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outline)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Show File Info Overlay", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            "Display filename and image counter when viewing photos full-screen.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.showFileInfoOverlay,
                        onCheckedChange = { SettingsManager.setShowFileInfoOverlay(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.testTag("toggle_file_info_overlay")
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outline)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Haptic Touch Feedback", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            "Tactile vibration on swiping images, locking, and button clicks.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.hapticFeedback,
                        onCheckedChange = { SettingsManager.setHapticFeedback(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.testTag("toggle_haptic_feedback")
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outline)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("High Contrast Accent Borders", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            "Crisp colored outlines on cards, thumbnails, and preview borders.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.highContrastBorders,
                        onCheckedChange = { SettingsManager.setHighContrastBorders(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.testTag("toggle_high_contrast_borders")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // SECTION 4: 🎨 Themes
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ColorLens, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Theme Appearance", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text("Select visual palette for the entire application:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(14.dp))

                ThemeOptionCard(
                    title = "Matrix Green (Cyberpunk)",
                    subtitle = "Hacker terminal green and deep pitch black",
                    previewColor = Color(0xFF00FF41),
                    bgColor = Color(0xFF0D0208),
                    isSelected = settings.theme == AppTheme.CYBERPUNK_GREEN,
                    onClick = { SettingsManager.setTheme(AppTheme.CYBERPUNK_GREEN) }
                )

                Spacer(modifier = Modifier.height(8.dp))

                ThemeOptionCard(
                    title = "Cyber Purple",
                    subtitle = "Vibrant synthwave purple & cyan neon",
                    previewColor = Color(0xFF6C5CE7),
                    bgColor = Color(0xFF0F0E17),
                    isSelected = settings.theme == AppTheme.CYBER_PURPLE,
                    onClick = { SettingsManager.setTheme(AppTheme.CYBER_PURPLE) }
                )

                Spacer(modifier = Modifier.height(8.dp))

                ThemeOptionCard(
                    title = "Ocean Blue",
                    subtitle = "Deep cyan, navy oceanic and crisp high-contrast",
                    previewColor = Color(0xFF00D2D3),
                    bgColor = Color(0xFF0A192F),
                    isSelected = settings.theme == AppTheme.OCEAN_BLUE,
                    onClick = { SettingsManager.setTheme(AppTheme.OCEAN_BLUE) }
                )

                Spacer(modifier = Modifier.height(8.dp))

                ThemeOptionCard(
                    title = "Crimson Night",
                    subtitle = "Deep blood crimson and obsidian darkness",
                    previewColor = Color(0xFFFF4757),
                    bgColor = Color(0xFF130F13),
                    isSelected = settings.theme == AppTheme.CRIMSON_NIGHT,
                    onClick = { SettingsManager.setTheme(AppTheme.CRIMSON_NIGHT) }
                )

                Spacer(modifier = Modifier.height(8.dp))

                ThemeOptionCard(
                    title = "AMOLED Pitch Black",
                    subtitle = "Pure #000000 battery-saver black with blue accents",
                    previewColor = Color(0xFF3B82F6),
                    bgColor = Color(0xFF000000),
                    isSelected = settings.theme == AppTheme.AMOLED_DARK,
                    onClick = { SettingsManager.setTheme(AppTheme.AMOLED_DARK) }
                )

                Spacer(modifier = Modifier.height(8.dp))

                ThemeOptionCard(
                    title = "Modern Light",
                    subtitle = "Crisp light background for bright daylight reading",
                    previewColor = Color(0xFF2563EB),
                    bgColor = Color(0xFFF8FAFC),
                    isSelected = settings.theme == AppTheme.MODERN_LIGHT,
                    onClick = { SettingsManager.setTheme(AppTheme.MODERN_LIGHT) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // SECTION 5: ⏱ Slideshow & Playback Speeds
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("SShow Playback Speed", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(modifier = Modifier.height(12.dp))

                Text("Normal Slide Interval: ${settings.slideshowSpeedMs / 1000f}s", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Slider(
                    value = settings.slideshowSpeedMs.toFloat(),
                    onValueChange = { SettingsManager.setSlideshowSpeedMs(it.toLong()) },
                    valueRange = 500f..5000f,
                    steps = 8,
                    modifier = Modifier.fillMaxWidth().testTag("slideshow_speed_slider")
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text("Fast-Forward (>>) Interval: ${settings.fastForwardSpeedMs}ms", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Slider(
                    value = settings.fastForwardSpeedMs.toFloat(),
                    onValueChange = { SettingsManager.setFastForwardSpeedMs(it.toLong()) },
                    valueRange = 100f..1000f,
                    steps = 8,
                    modifier = Modifier.fillMaxWidth().testTag("fast_forward_speed_slider")
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outline)

                Text("Slide Transition Animation:", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("fade" to "Fade", "slide" to "Slide / Sweep", "instant" to "Instant Cut").forEach { (trans, label) ->
                        FilterChip(
                            selected = settings.slideshowTransition == trans,
                            onClick = { SettingsManager.setSlideshowTransition(trans) },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.testTag("chip_transition_$trans")
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outline)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Continuous Slideshow Loop", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            "Automatically restart from the first photo when reaching the end.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.slideshowLoop,
                        onCheckedChange = { SettingsManager.setSlideshowLoop(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.testTag("toggle_slideshow_loop")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // SECTION 6: 📖 User Manual & PDF Documentation
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth().testTag("section_user_manual")
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("User Manual & Documentation", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Official user manual covering security setup, emergency PIN recovery, DAT vault encryption, Excel catalog search, and Android/media storage paths.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { showManualDialog = true },
                        modifier = Modifier.weight(1f).testTag("btn_read_manual"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Read Manual", fontSize = 13.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            val file = com.example.util.UserManualPdfManager.generatePdf(context)
                            Toast.makeText(context, "PDF saved: ${file.name} (${file.length() / 1024} KB)", Toast.LENGTH_SHORT).show()
                            val shareIntent = com.example.util.UserManualPdfManager.createManualShareIntent(context)
                            if (shareIntent != null) {
                                try {
                                    context.startActivity(Intent.createChooser(shareIntent, "Share / Open User Manual PDF"))
                                } catch (e: Throwable) {
                                    Toast.makeText(context, "Saved to: ${file.absolutePath}", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        modifier = Modifier.weight(1f).testTag("btn_export_pdf_direct")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export PDF", fontSize = 13.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // SECTION 7: 🎨 App Branding & QR Sharing
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth().testTag("section_branding_share")
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Brush, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("App Branding & Quick Share", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Set / Change App Logo", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            "Select custom branding photo or restore standard vector icon.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = { showLogoDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("btn_settings_change_logo")
                    ) {
                        Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Change", fontSize = 12.sp)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outline)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Scan & Download App QR Code", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            "Scan from another phone to immediately download or open this application.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = { showShareQrDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("btn_settings_show_qr")
                    ) {
                        Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Show QR", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (showPinDialog) {
            PinManagementDialog(
                isExistingPinSet = securityConfig.isPinEnabled,
                onDismiss = { showPinDialog = false },
                onSuccess = { msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            )
        }

        if (showPasswordDialog) {
            ChangeMasterPasswordDialog(
                onDismiss = { showPasswordDialog = false },
                onSuccess = { msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            )
        }

        if (showSecurityFolderDialog) {
            SecurityFolderInspectorDialog(
                onDismiss = { showSecurityFolderDialog = false }
            )
        }

        if (showManualDialog) {
            UserManualDialog(
                onDismiss = { showManualDialog = false }
            )
        }

        if (showProfileDialog) {
            ProfileDialog(
                onDismiss = { showProfileDialog = false }
            )
        }

        if (showPathsDialog) {
            ConnectedPathsDialog(
                onDismiss = { showPathsDialog = false }
            )
        }

        if (showLogoDialog) {
            AppLogoDialog(
                onDismiss = { showLogoDialog = false }
            )
        }

        if (showShareQrDialog) {
            ShareAppQrDialog(
                onDismiss = { showShareQrDialog = false }
            )
        }

        if (showDualVaultDialog) {
            DualVaultConnectDialog(
                onDismiss = { showDualVaultDialog = false }
            )
        }

        if (showFileManagerDialog) {
            AppMediaFileManagerDialog(
                onDismiss = { showFileManagerDialog = false }
            )
        }

        if (showFaceSetupDialog) {
            FaceLockSetupDialog(
                onDismiss = { showFaceSetupDialog = false },
                onEnrolled = {
                    showFaceSetupDialog = false
                    Toast.makeText(context, "Face recognition calibrated and enabled!", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

@Composable
private fun ThemeOptionCard(
    title: String,
    subtitle: String,
    previewColor: Color,
    bgColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(bgColor)
                    .border(2.dp, previewColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(previewColor)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
