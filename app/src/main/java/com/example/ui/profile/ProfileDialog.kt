package com.example.ui.profile

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.R
import com.example.data.local.UserProfile
import com.example.util.AppSecurityManager
import com.example.util.GoogleAuthHelper
import com.example.util.ProfileManager
import com.example.util.SettingsManager
import java.io.File

@Composable
fun ProfileDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentProfile by ProfileManager.userProfile.collectAsState()

    // Status flags
    val isNewUser = currentProfile == null || currentProfile!!.fullName.isBlank()
    val isGoogleMissing = currentProfile != null && !currentProfile!!.isGoogleAccountConnected()
    val isMandatorySetup = isNewUser || isGoogleMissing

    var isEditMode by remember { mutableStateOf(isMandatorySetup) }

    // Form states
    var fullName by remember { mutableStateOf(currentProfile?.fullName ?: "") }
    var dateOfBirth by remember { mutableStateOf(currentProfile?.dateOfBirth ?: "") }
    var phoneNumber by remember { mutableStateOf(currentProfile?.phoneNumber ?: "") }
    var emailId by remember { mutableStateOf(currentProfile?.emailId ?: "") }
    var device by remember {
        mutableStateOf(
            if (currentProfile?.device.isNullOrBlank()) ProfileManager.getAutoDeviceModel()
            else currentProfile!!.device
        )
    }
    var village by remember { mutableStateOf(currentProfile?.village ?: "") }
    var district by remember { mutableStateOf(currentProfile?.district ?: "") }
    var state by remember { mutableStateOf(currentProfile?.state ?: "") }
    var country by remember { mutableStateOf(currentProfile?.country ?: "") }
    var pincode by remember { mutableStateOf(currentProfile?.pincode ?: "") }
    var profileImageBase64 by remember { mutableStateOf(currentProfile?.profileImageBase64 ?: "") }
    var profileImagePath by remember { mutableStateOf(currentProfile?.profileImagePath ?: "") }

    // Google Account states
    var googleEmail by remember { mutableStateOf(currentProfile?.googleEmail ?: "") }
    var googleDisplayName by remember { mutableStateOf(currentProfile?.googleDisplayName ?: "") }
    var isGoogleConnected by remember { mutableStateOf(currentProfile?.isGoogleConnected == true || !currentProfile?.googleEmail.isNullOrBlank()) }

    var validationError by remember { mutableStateOf<String?>(null) }
    var showManualGoogleDialog by remember { mutableStateOf(false) }
    var showFacebookDialog by remember { mutableStateOf(false) }
    var manualGoogleEmailInput by remember { mutableStateOf("") }
    var manualGoogleNameInput by remember { mutableStateOf("") }
    var facebookUsernameInput by remember { mutableStateOf("") }

    // Sync fields if currentProfile changes externally (e.g. from fingerprint import or account connect)
    LaunchedEffect(currentProfile) {
        currentProfile?.let { p ->
            fullName = p.fullName
            dateOfBirth = p.dateOfBirth
            phoneNumber = p.phoneNumber
            emailId = p.emailId
            device = if (p.device.isNotBlank()) p.device else ProfileManager.getAutoDeviceModel()
            village = p.village
            district = p.district
            state = p.state
            country = p.country
            pincode = p.pincode
            profileImageBase64 = p.profileImageBase64
            profileImagePath = p.profileImagePath
            googleEmail = p.googleEmail
            googleDisplayName = p.googleDisplayName
            isGoogleConnected = p.isGoogleAccountConnected()

            if (p.isComplete()) {
                isEditMode = false
            }
        }
    }

    // Photo picker launcher
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val base64 = ProfileManager.uriToBase64(context, uri)
            if (base64 != null) {
                profileImageBase64 = base64
                validationError = null
            } else {
                Toast.makeText(context, "Could not process selected image.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Account picker launcher
    val accountPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val chosenEmail = GoogleAuthHelper.parseAccountPickerResult(result.resultCode, result.data)
        if (chosenEmail != null) {
            val (ok, msg) = GoogleAuthHelper.connectGoogleAccount(context, chosenEmail)
            if (ok) {
                googleEmail = chosenEmail
                googleDisplayName = GoogleAuthHelper.deriveDisplayNameFromEmail(chosenEmail)
                isGoogleConnected = true
                if (emailId.isBlank()) emailId = chosenEmail
                if (fullName.isBlank()) fullName = googleDisplayName
                validationError = null
                Toast.makeText(context, "Connected with Google: $chosenEmail", Toast.LENGTH_LONG).show()
            } else {
                validationError = msg
            }
        }
    }

    // Fingerprint DAT file picker launcher (Cross-device restore)
    val fingerprintFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val (ok, msg) = AppSecurityManager.importProfileAndFingerprintFromUri(context, uri)
            if (ok) {
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                validationError = null
                val restored = ProfileManager.userProfile.value
                if (restored != null && restored.isComplete()) {
                    isEditMode = false
                    onDismiss()
                }
            } else {
                validationError = msg
            }
        }
    }

    Dialog(
        onDismissRequest = {
            if (!isMandatorySetup) onDismiss()
        },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = !isMandatorySetup,
                dismissOnClickOutside = !isMandatorySetup
            )
        ) {
        Surface(
            modifier = modifier
                .fillMaxWidth(0.95f)
                .padding(vertical = 20.dp)
                .clip(RoundedCornerShape(22.dp))
                .testTag("dialog_user_profile"),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.AccountCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (isNewUser) "Create Profile" else if (isGoogleMissing) "Google Account Required" else if (isEditMode) "Edit Profile" else "User Profile",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isNewUser) "Fill profile form and connect Google account" else if (isGoogleMissing) "Mandatory: Connect Google account to proceed" else "Synced with fingerprint.dat",
                                fontSize = 11.sp,
                                color = if (isMandatorySetup) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (!isMandatorySetup) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("btn_close_profile")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // Import from fingerprint.dat shortcut button
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clickable {
                            fingerprintFilePicker.launch(arrayOf("*/*"))
                        }
                        .testTag("btn_import_fingerprint_dat")
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.UploadFile,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Put / Import fingerprint.dat",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "Restores all profile and Google account info automatically",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Profile Picture Avatar
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        contentAlignment = Alignment.BottomEnd,
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(2.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            .clickable(enabled = isEditMode) {
                                photoPickerLauncher.launch("image/*")
                            }
                            .testTag("profile_avatar_box")
                    ) {
                        val decodedBitmap = remember(profileImageBase64) {
                            try {
                                if (profileImageBase64.isNotBlank()) {
                                    val bytes = Base64.decode(profileImageBase64, Base64.DEFAULT)
                                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                } else null
                            } catch (_: Throwable) {
                                null
                            }
                        }

                        if (decodedBitmap != null) {
                            Image(
                                bitmap = decodedBitmap.asImageBitmap(),
                                contentDescription = "Profile Picture",
                                modifier = Modifier.size(96.dp).clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else if (profileImagePath.isNotBlank() && File(profileImagePath).exists()) {
                            AsyncImage(
                                model = File(profileImagePath),
                                contentDescription = "Profile Picture",
                                modifier = Modifier.size(96.dp).clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier.size(96.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(50.dp)
                                )
                            }
                        }

                        if (isEditMode) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(2.dp)
                                    .size(26.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.CameraAlt,
                                        contentDescription = "Pick Photo",
                                        tint = Color.White,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (isEditMode) {
                        Spacer(modifier = Modifier.height(4.dp))
                        TextButton(
                            onClick = { photoPickerLauncher.launch("image/*") },
                            modifier = Modifier.testTag("btn_select_profile_photo")
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                if (profileImageBase64.isBlank() && profileImagePath.isBlank()) "Add Profile Picture *" else "Change Picture",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // MANDATORY "CONNECT WITH GOOGLE" SECTION (Prominently featured)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isGoogleConnected) Color(0xFF00B894).copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    border = BorderStroke(
                        1.5.dp,
                        if (isGoogleConnected) Color(0xFF00B894) else MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .testTag("section_connect_google")
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_google_logo),
                                    contentDescription = "Google",
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Google Account",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (isGoogleConnected) "Connected & Synchronized" else "Mandatory Connection",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isGoogleConnected) Color(0xFF00B894) else MaterialTheme.colorScheme.error
                                    )
                                }
                            }

                            if (isGoogleConnected) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFF00B894).copy(alpha = 0.2f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = Color(0xFF00B894),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            "Linked",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF00B894)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (isGoogleConnected && googleEmail.isNotBlank()) {
                            // Connected info display
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = googleEmail,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (googleDisplayName.isNotBlank()) {
                                        Text(
                                            text = googleDisplayName,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = "Account data embedded in fingerprint.dat backup",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            if (isEditMode) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(
                                        onClick = {
                                            try {
                                                accountPickerLauncher.launch(GoogleAuthHelper.createGoogleAccountPickerIntent())
                                            } catch (_: ActivityNotFoundException) {
                                                showManualGoogleDialog = true
                                            }
                                        }
                                    ) {
                                        Text("Switch Google Account", fontSize = 12.sp)
                                    }
                                }
                            }
                        } else {
                            // Mandatory Notice
                            Text(
                                text = "Connecting with Google is mandatory to complete your profile and enable cross-device fingerprint.dat login.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Mandatory "Connect with google" Button
                            Button(
                                onClick = {
                                    try {
                                        accountPickerLauncher.launch(GoogleAuthHelper.createGoogleAccountPickerIntent())
                                    } catch (_: ActivityNotFoundException) {
                                        showManualGoogleDialog = true
                                    } catch (_: Throwable) {
                                        showManualGoogleDialog = true
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                ),
                                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .testTag("btn_connect_google")
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_google_logo),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Connect with google",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Optional Facebook Account Card
                val appSettings by SettingsManager.settings.collectAsState()
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, Color(0xFF1877F2).copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_facebook_logo),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Facebook Account (Optional)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (appSettings.facebookConnected) "Connected: ${appSettings.facebookUserName}" else "Optional social account link",
                                        fontSize = 11.sp,
                                        color = if (appSettings.facebookConnected) Color(0xFF1877F2) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (appSettings.facebookConnected) {
                                TextButton(
                                    onClick = { SettingsManager.setFacebookConnected(false, "") },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text("Disconnect", fontSize = 11.sp)
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { showFacebookDialog = true },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.testTag("btn_connect_facebook")
                                ) {
                                    Text("Connect", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Validation Error Banner
                if (validationError != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "⚠️ ${validationError ?: ""}",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // If VIEW MODE (not editMode and profile complete) -> Show crisp details + Update Button
                if (!isEditMode && currentProfile != null && currentProfile!!.isComplete()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ProfileDetailItem(label = "Full Name", value = fullName)
                        ProfileDetailItem(label = "Date of Birth", value = dateOfBirth)
                        ProfileDetailItem(label = "Phone Number", value = phoneNumber)
                        ProfileDetailItem(label = "Email ID", value = emailId)
                        ProfileDetailItem(label = "Device (Auto-fetched)", value = device)
                        ProfileDetailItem(label = "Village", value = village)
                        ProfileDetailItem(label = "District", value = district)
                        ProfileDetailItem(label = "State", value = state)
                        ProfileDetailItem(label = "Country", value = country)
                        ProfileDetailItem(label = "Pincode", value = pincode)
                        ProfileDetailItem(label = "Google Account", value = googleEmail)
                        ProfileDetailItem(
                            label = "Facebook (Optional)",
                            value = if (appSettings.facebookConnected) appSettings.facebookUserName else "Not linked"
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Fingerprint Info Badge
                        val securityConfig by AppSecurityManager.securityConfig.collectAsState()
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Fingerprint,
                                    contentDescription = null,
                                    tint = if (securityConfig.isFingerprintRegistered) Color(0xFF00B894) else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = if (securityConfig.isFingerprintRegistered) "Fingerprint & Google Account Linked" else "Profile Ready for Fingerprint Registration",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (securityConfig.isFingerprintRegistered) Color(0xFF00B894) else MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Saved in: Android/media/.../profile and fingerprint.dat",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Update Button
                        Button(
                            onClick = {
                                isEditMode = true
                                validationError = null
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_edit_profile"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Update Profile", fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    // EDIT MODE / FIRST TIME FORM
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = fullName,
                            onValueChange = { fullName = it; validationError = null },
                            label = { Text("Full Name *") },
                            placeholder = { Text("e.g. John Doe") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("input_profile_fullname")
                        )

                        OutlinedTextField(
                            value = dateOfBirth,
                            onValueChange = { dateOfBirth = it; validationError = null },
                            label = { Text("Date of Birth *") },
                            placeholder = { Text("e.g. 15-08-1995") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("input_profile_dob")
                        )

                        OutlinedTextField(
                            value = phoneNumber,
                            onValueChange = { phoneNumber = it; validationError = null },
                            label = { Text("Phone Number *") },
                            placeholder = { Text("e.g. +1 555-0199") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("input_profile_phone")
                        )

                        OutlinedTextField(
                            value = emailId,
                            onValueChange = { emailId = it; validationError = null },
                            label = { Text("Email ID *") },
                            placeholder = { Text("e.g. user@example.com") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("input_profile_email")
                        )

                        // Device (Auto-fetch with option to refresh)
                        OutlinedTextField(
                            value = device,
                            onValueChange = { device = it; validationError = null },
                            label = { Text("Device (Auto-fetched) *") },
                            trailingIcon = {
                                IconButton(onClick = { device = ProfileManager.getAutoDeviceModel() }) {
                                    Icon(Icons.Default.Smartphone, contentDescription = "Auto Fetch Device")
                                }
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("input_profile_device")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = village,
                                onValueChange = { village = it; validationError = null },
                                label = { Text("Village *") },
                                singleLine = true,
                                modifier = Modifier.weight(1f).testTag("input_profile_village")
                            )
                            OutlinedTextField(
                                value = district,
                                onValueChange = { district = it; validationError = null },
                                label = { Text("District *") },
                                singleLine = true,
                                modifier = Modifier.weight(1f).testTag("input_profile_district")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = state,
                                onValueChange = { state = it; validationError = null },
                                label = { Text("State *") },
                                singleLine = true,
                                modifier = Modifier.weight(1f).testTag("input_profile_state")
                            )
                            OutlinedTextField(
                                value = country,
                                onValueChange = { country = it; validationError = null },
                                label = { Text("Country *") },
                                singleLine = true,
                                modifier = Modifier.weight(1f).testTag("input_profile_country")
                            )
                        }

                        OutlinedTextField(
                            value = pincode,
                            onValueChange = { pincode = it; validationError = null },
                            label = { Text("Pincode *") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("input_profile_pincode")
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Save Button
                        Button(
                            onClick = {
                                val proposed = UserProfile(
                                    fullName = fullName.trim(),
                                    dateOfBirth = dateOfBirth.trim(),
                                    phoneNumber = phoneNumber.trim(),
                                    emailId = emailId.trim(),
                                    device = device.trim().ifEmpty { ProfileManager.getAutoDeviceModel() },
                                    village = village.trim(),
                                    district = district.trim(),
                                    state = state.trim(),
                                    country = country.trim(),
                                    pincode = pincode.trim(),
                                    profileImageBase64 = profileImageBase64.trim(),
                                    profileImagePath = profileImagePath.trim(),
                                    googleEmail = googleEmail.trim(),
                                    googleDisplayName = googleDisplayName.trim(),
                                    isGoogleConnected = isGoogleConnected && googleEmail.isNotBlank()
                                )

                                val missing = proposed.getFirstMissingField()
                                if (missing != null) {
                                    validationError = "Every field is required: $missing."
                                    return@Button
                                }

                                val (ok, msg) = ProfileManager.saveProfile(context, proposed)
                                if (ok) {
                                    Toast.makeText(context, "Profile saved and synced with fingerprint.dat!", Toast.LENGTH_SHORT).show()
                                    AppSecurityManager.updateFingerprintProfileDataIfRegistered(context)
                                    isEditMode = false
                                    validationError = null
                                    if (isMandatorySetup) {
                                        onDismiss()
                                    }
                                } else {
                                    validationError = msg
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_save_profile"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isNewUser) "Save & Connect Profile" else "Save Changes", fontWeight = FontWeight.Bold)
                        }

                        if (!isMandatorySetup) {
                            OutlinedButton(
                                onClick = {
                                    isEditMode = false
                                    validationError = null
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("btn_cancel_edit_profile"),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Cancel")
                            }
                        }
                    }
                }
            }
        }
    }

    // Fallback dialog for entering Google Account email (e.g. for emulators or testing)
    if (showManualGoogleDialog) {
        AlertDialog(
            onDismissRequest = { showManualGoogleDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_google_logo),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Connect Google Account", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Enter your Google Account email to link your profile with fingerprint.dat:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = manualGoogleEmailInput,
                        onValueChange = { manualGoogleEmailInput = it },
                        label = { Text("Google Account Email") },
                        placeholder = { Text("e.g. user@gmail.com") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("input_manual_google_email")
                    )
                    OutlinedTextField(
                        value = manualGoogleNameInput,
                        onValueChange = { manualGoogleNameInput = it },
                        label = { Text("Display Name (Optional)") },
                        placeholder = { Text("e.g. John Doe") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("input_manual_google_name")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val (ok, msg) = GoogleAuthHelper.connectGoogleAccount(
                            context = context,
                            email = manualGoogleEmailInput,
                            displayName = manualGoogleNameInput
                        )
                        if (ok) {
                            googleEmail = manualGoogleEmailInput.trim()
                            googleDisplayName = if (manualGoogleNameInput.isNotBlank()) manualGoogleNameInput.trim() else GoogleAuthHelper.deriveDisplayNameFromEmail(googleEmail)
                            isGoogleConnected = true
                            if (emailId.isBlank()) emailId = googleEmail
                            if (fullName.isBlank()) fullName = googleDisplayName
                            showManualGoogleDialog = false
                            validationError = null
                            Toast.makeText(context, "Google Account connected!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier.testTag("btn_confirm_manual_google")
                ) {
                    Text("Connect")
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualGoogleDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Optional Facebook Account Connect Dialog
    if (showFacebookDialog) {
        AlertDialog(
            onDismissRequest = { showFacebookDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_facebook_logo),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Connect Facebook", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text(
                        "Link your Facebook profile name or username (optional):",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = facebookUsernameInput,
                        onValueChange = { facebookUsernameInput = it },
                        label = { Text("Facebook Username or Name") },
                        placeholder = { Text("e.g. Alex Miller") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("input_facebook_username")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = facebookUsernameInput.trim().ifEmpty { "Connected User" }
                        SettingsManager.setFacebookConnected(true, name)
                        showFacebookDialog = false
                        Toast.makeText(context, "Facebook account linked!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.testTag("btn_confirm_facebook")
                ) {
                    Text("Link Account")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFacebookDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ProfileDetailItem(
    label: String,
    value: String
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = value.ifEmpty { "-" },
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
