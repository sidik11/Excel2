package com.example.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.ui.components.ZoomableBox
import com.example.util.DualVaultFileInfo
import com.example.util.FirebaseBridgeManager
import com.example.util.SettingsManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class DualVaultMode {
    CHOOSE,
    SHOW_CODE,
    ENTER_CODE,
    COMBINED_VAULT
}

@Composable
fun DualVaultConnectDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val session by FirebaseBridgeManager.currentSession.collectAsState()
    val settings by SettingsManager.settings.collectAsState()

    var currentMode by remember(session.isConnected) {
        mutableStateOf(if (session.isConnected) DualVaultMode.COMBINED_VAULT else DualVaultMode.CHOOSE)
    }

    var generatedCode by remember { mutableStateOf(session.code) }
    var enteredCodeInput by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var uploadStatusMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedPreviewFile by remember { mutableStateOf<DualVaultFileInfo?>(null) }

    // Slideshow state
    var isSlideshowOpen by remember { mutableStateOf(false) }
    var slideshowStartIndex by remember { mutableIntStateOf(0) }

    // Delete confirmation state
    var fileToDelete by remember { mutableStateOf<DualVaultFileInfo?>(null) }

    // A connected session always owns the dialog state. Pairing controls are
    // unreachable while connected, even if the previous UI mode was CHOOSE.
    val effectiveMode = if (session.isConnected) DualVaultMode.COMBINED_VAULT else currentMode

    LaunchedEffect(Unit) {
        FirebaseBridgeManager.refreshDualVaultFiles(context)
        if (session.isConnected && session.sessionId.isNotBlank()) {
            FirebaseBridgeManager.startLiveSync(context, session.sessionId)
        }
    }

    LaunchedEffect(session.isConnected, session.sessionId) {
        if (session.isConnected && session.sessionId.isNotBlank()) {
            FirebaseBridgeManager.startLiveSync(context, session.sessionId)
        }
    }

    // Supports MULTIPLE photo selection at once (batches up to 50 photos)
    val multiPhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(50)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            coroutineScope.launch {
                isLoading = true
                var successCount = 0
                uris.forEachIndexed { index, uri ->
                    uploadStatusMessage = "Syncing ${index + 1} of ${uris.size} to Dual Vault & Peer..."
                    val result = FirebaseBridgeManager.addImageToDualVault(context, uri)
                    if (result.isSuccess) successCount++
                }
                isLoading = false
                uploadStatusMessage = null
                Toast.makeText(context, "Added and synced $successCount of ${uris.size} photos!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Fallback file picker for multiple files/images
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            coroutineScope.launch {
                isLoading = true
                var successCount = 0
                uris.forEachIndexed { index, uri ->
                    uploadStatusMessage = "Syncing ${index + 1} of ${uris.size} to Dual Vault & Peer..."
                    val result = FirebaseBridgeManager.addImageToDualVault(context, uri)
                    if (result.isSuccess) successCount++
                }
                isLoading = false
                uploadStatusMessage = null
                Toast.makeText(context, "Added and synced $successCount of ${uris.size} photos!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun copyCode(code: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Dual Vault Code", code))
        Toast.makeText(context, "Pairing code copied!", Toast.LENGTH_SHORT).show()
    }

    androidx.activity.compose.BackHandler {
        if (selectedPreviewFile != null) {
            selectedPreviewFile = null
        } else if (isSlideshowOpen) {
            isSlideshowOpen = false
        } else {
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(20.dp)),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Surface(
                            shape = CircleShape,
                            color = if (session.isConnected) Color(0xFF10B981).copy(alpha = 0.15f) else MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (session.isConnected) Icons.Default.FolderSpecial else Icons.Default.SyncAlt,
                                    contentDescription = null,
                                    tint = if (session.isConnected) Color(0xFF10B981) else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            val partner = if (session.isHost) session.peerName.ifBlank { "Partner" } else session.hostName.ifBlank { "Host" }
                            Text(
                                text = if (session.isConnected) "Combined Vault" else "Dual Vault Device Pairing",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (session.isConnected) "Paired with $partner • Auto-Sync Active" else "Cross-Device Media Vault",
                                fontSize = 11.sp,
                                color = if (session.isConnected) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (session.isConnected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }

                    FilledTonalIconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(40.dp).testTag("btn_close_dual_vault_dialog")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(20.dp))
                    }
                }

                // Peer Disconnect Alert Banner
                if (session.status == "DISCONNECTED") {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Session disconnected. Both devices are unlinked.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            TextButton(
                                onClick = {
                                    coroutineScope.launch {
                                        FirebaseBridgeManager.disconnect(context)
                                        currentMode = DualVaultMode.CHOOSE
                                    }
                                }
                            ) {
                                Text("Reset", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // General Error Banner
                errorMessage?.let { msg ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = msg,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                when (effectiveMode) {
                    DualVaultMode.CHOOSE -> {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Devices,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(60.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "Dual Device Pairing",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Connect 2 devices to create an auto-synced Combined Vault in Android/media/Dual_Vault.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
                            )

                            // 10-digit profile device code info
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier
                                    .fillMaxWidth(0.9f)
                                    .padding(vertical = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("Your 10-Digit Device Code", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(
                                            FirebaseBridgeManager.generate10DigitCode(),
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    IconButton(
                                        onClick = { copyCode(FirebaseBridgeManager.generate10DigitCode()) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Choice 1: Show Code
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        isLoading = true
                                        errorMessage = null
                                        val res = FirebaseBridgeManager.createPairingRoom(context)
                                        isLoading = false
                                        if (res.isSuccess) {
                                            generatedCode = res.getOrThrow()
                                            currentMode = DualVaultMode.SHOW_CODE
                                        } else {
                                            errorMessage = res.exceptionOrNull()?.message
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                enabled = !isLoading,
                                modifier = Modifier
                                    .fillMaxWidth(0.85f)
                                    .testTag("btn_show_pairing_code")
                            ) {
                                Icon(Icons.Default.VpnKey, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Show My 10-Digit Code", fontWeight = FontWeight.Bold)
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Choice 2: Enter Code
                            OutlinedButton(
                                onClick = {
                                    errorMessage = null
                                    currentMode = DualVaultMode.ENTER_CODE
                                },
                                shape = RoundedCornerShape(12.dp),
                                enabled = !isLoading,
                                modifier = Modifier
                                    .fillMaxWidth(0.85f)
                                    .testTag("btn_enter_pairing_code")
                            ) {
                                Icon(Icons.Default.Pin, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Enter Other User's Code", fontWeight = FontWeight.Bold)
                            }

                            if (session.dualVaultFiles.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(14.dp))
                                TextButton(onClick = { currentMode = DualVaultMode.COMBINED_VAULT }) {
                                    Text("Browse Local Dual Vault Files (${session.dualVaultFiles.size})")
                                }
                            }
                        }
                    }

                    DualVaultMode.SHOW_CODE -> {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Your 10-Digit Pairing Code",
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                                modifier = Modifier
                                    .clickable { copyCode(generatedCode) }
                                    .testTag("box_generated_code")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = generatedCode.chunked(5).joinToString(" "),
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 26.sp,
                                        letterSpacing = 2.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        contentDescription = "Copy Code",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Waiting for other user to enter this code...",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            OutlinedButton(
                                onClick = {
                                    coroutineScope.launch {
                                        FirebaseBridgeManager.disconnect(context)
                                        currentMode = DualVaultMode.CHOOSE
                                    }
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Cancel")
                            }
                        }
                    }

                    DualVaultMode.ENTER_CODE -> {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Enter Host's 10-Digit Code",
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Ask the other user for their 10-digit code displayed on their screen.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            OutlinedTextField(
                                value = enteredCodeInput,
                                onValueChange = { input ->
                                    if (input.length <= 12) {
                                        enteredCodeInput = input.trim()
                                    }
                                },
                                placeholder = { Text("e.g. sdmkd42123") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                textStyle = LocalTextStyle.current.copy(
                                    fontSize = 20.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                ),
                                modifier = Modifier
                                    .fillMaxWidth(0.85f)
                                    .testTag("input_pairing_code")
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            Button(
                                onClick = {
                                    val clean = enteredCodeInput.replace(" ", "")
                                    if (clean.length == 10) {
                                        coroutineScope.launch {
                                            isLoading = true
                                            errorMessage = null
                                            val res = FirebaseBridgeManager.joinPairingRoom(context, clean)
                                            isLoading = false
                                            if (res.isSuccess) {
                                                Toast.makeText(context, "Connected to Dual Vault!", Toast.LENGTH_SHORT).show()
                                                currentMode = DualVaultMode.COMBINED_VAULT
                                            } else {
                                                errorMessage = res.exceptionOrNull()?.message
                                            }
                                        }
                                    } else {
                                        errorMessage = "Please enter a valid 10-digit code."
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                enabled = enteredCodeInput.trim().length >= 8 && !isLoading,
                                modifier = Modifier
                                    .fillMaxWidth(0.85f)
                                    .testTag("btn_confirm_join")
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Connecting...")
                                } else {
                                    Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Connect & Create Dual Vault", fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedButton(
                                onClick = { currentMode = DualVaultMode.CHOOSE },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Back")
                            }
                        }
                    }

                    DualVaultMode.COMBINED_VAULT -> {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            // Connected status card & auto-sync info
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        // Paired Profile Images beside connected status
                                        DualVaultPairAvatars(
                                            hostImage = session.hostProfileImage,
                                            hostName = session.hostName.ifBlank { "Host" },
                                            peerImage = session.peerProfileImage,
                                            peerName = session.peerName.ifBlank { "Peer" }
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            val partner = if (session.isHost) session.peerName.ifBlank { "Connected Peer" } else session.hostName.ifBlank { "Host" }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Surface(
                                                    shape = CircleShape,
                                                    color = Color(0xFF10B981),
                                                    modifier = Modifier.size(8.dp)
                                                ) {}
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Paired: $partner",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                            Text(
                                                text = "Auto-sync active • ${session.dualVaultFiles.size} photos in Dual_Vault",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = {
                                                coroutineScope.launch {
                                                    val ok = FirebaseBridgeManager.syncNow(context)
                                                    if (ok) Toast.makeText(context, "Dual Vault synced with peer!", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.Sync, contentDescription = "Sync Now", tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                        IconButton(
                                            onClick = { FirebaseBridgeManager.refreshDualVaultFiles(context) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }

                            if (uploadStatusMessage != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = uploadStatusMessage ?: "Syncing...",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Action buttons: Upload Photos, Slideshow (Files option removed)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        multiPhotoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1.3f)
                                        .testTag("btn_add_dual_vault_photos")
                                ) {
                                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Upload Photos", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }

                                // Slideshow Button
                                FilledTonalButton(
                                    onClick = {
                                        slideshowStartIndex = 0
                                        isSlideshowOpen = true
                                    },
                                    enabled = session.dualVaultFiles.isNotEmpty(),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1.1f).testTag("btn_dual_slideshow")
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Slideshow", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Grid of Dual Vault Photos
                            if (session.dualVaultFiles.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(48.dp))
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("No photos in Combined Vault yet.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("Tap 'Upload Photos' above to store images in Dual Vault.", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                    }
                                }
                            } else {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(3),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    itemsIndexed(session.dualVaultFiles) { index, item ->
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                            modifier = Modifier
                                                .aspectRatio(1f)
                                                .clip(RoundedCornerShape(10.dp))
                                                .clickable { selectedPreviewFile = item }
                                        ) {
                                            Box(modifier = Modifier.fillMaxSize()) {
                                                AsyncImage(
                                                    model = item.localFile,
                                                    contentDescription = item.fileName,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )

                                                // Top-Right Quick Delete Button
                                                Surface(
                                                    shape = CircleShape,
                                                    color = Color.Black.copy(alpha = 0.65f),
                                                    modifier = Modifier
                                                        .size(26.dp)
                                                        .align(Alignment.TopEnd)
                                                        .padding(2.dp)
                                                        .clickable { fileToDelete = item }
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(
                                                            Icons.Default.Delete,
                                                            contentDescription = "Delete",
                                                            tint = Color.White,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                    }
                                                }

                                                // Bottom filename pill
                                                Surface(
                                                    color = Color.Black.copy(alpha = 0.65f),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .align(Alignment.BottomCenter)
                                                ) {
                                                    Text(
                                                        text = item.fileName,
                                                        fontSize = 9.sp,
                                                        color = Color.White,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.padding(3.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            FirebaseBridgeManager.disconnect(context)
                                            currentMode = DualVaultMode.CHOOSE
                                            Toast.makeText(context, "Disconnected session for both devices", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f).testTag("btn_disconnect_dual_vault")
                                ) {
                                    Icon(Icons.Default.LinkOff, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Disconnect")
                                }

                                Button(
                                    onClick = onDismiss,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f).testTag("btn_close_dual_vault_done")
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Close Vault", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Full-Screen Photo Modal Dialog
    selectedPreviewFile?.let { previewItem ->
        val fileList = session.dualVaultFiles
        var currentModalIndex by remember(previewItem) {
            mutableIntStateOf(fileList.indexOf(previewItem).coerceAtLeast(0))
        }
        val currentModalFile = fileList.getOrNull(currentModalIndex) ?: previewItem

        Dialog(
            onDismissRequest = { selectedPreviewFile = null },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            androidx.activity.compose.BackHandler {
                selectedPreviewFile = null
            }
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Black
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Fullscreen interactive photo with pinch-to-zoom and swipe
                    ZoomableBox(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(fileList.size) {
                                detectHorizontalDragGestures { _, dragAmount ->
                                    if (dragAmount > 60) {
                                        if (currentModalIndex > 0) currentModalIndex--
                                        else if (fileList.isNotEmpty()) currentModalIndex = fileList.size - 1
                                    } else if (dragAmount < -60) {
                                        if (fileList.isNotEmpty()) currentModalIndex = (currentModalIndex + 1) % fileList.size
                                    }
                                }
                            }
                    ) {
                        AsyncImage(
                            model = currentModalFile.localFile,
                            contentDescription = currentModalFile.fileName,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Top Fullscreen Header Bar
                    Surface(
                        color = Color.Black.copy(alpha = 0.75f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(
                                onClick = { selectedPreviewFile = null },
                                modifier = Modifier.size(40.dp).testTag("btn_close_photo_modal")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close", tint = Color.White)
                            }
                            Column(
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = currentModalFile.fileName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${currentModalIndex + 1} of ${fileList.size} • ${currentModalFile.fileSizeBytes / 1024} KB • Added by: ${currentModalFile.addedBy}",
                                    fontSize = 11.sp,
                                    color = Color.LightGray,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = {
                                    slideshowStartIndex = currentModalIndex
                                    selectedPreviewFile = null
                                    isSlideshowOpen = true
                                },
                                modifier = Modifier.size(40.dp).testTag("btn_modal_start_slideshow")
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Slideshow", tint = Color.White)
                            }
                        }
                    }

                    // Bottom Fullscreen Action Bar
                    Surface(
                        color = Color.Black.copy(alpha = 0.75f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(
                                onClick = {
                                    if (currentModalIndex > 0) currentModalIndex--
                                    else if (fileList.isNotEmpty()) currentModalIndex = fileList.size - 1
                                },
                                enabled = fileList.size > 1
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous", tint = Color.White, modifier = Modifier.size(26.dp))
                            }

                            Button(
                                onClick = {
                                    fileToDelete = currentModalFile
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("btn_modal_delete_photo")
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Delete Photo", fontSize = 12.sp)
                            }

                            IconButton(
                                onClick = {
                                    if (fileList.isNotEmpty()) currentModalIndex = (currentModalIndex + 1) % fileList.size
                                },
                                enabled = fileList.size > 1
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(26.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog (Auto-syncs deletion to both devices)
    fileToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete from Combined Vault?") },
            text = {
                Text(
                    "Are you sure you want to delete '${item.fileName}'?\n\nThis photo will be removed locally and automatically deleted from the paired device within 10 seconds via auto-sync.",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val fileName = item.fileName
                        fileToDelete = null
                        if (selectedPreviewFile?.fileName == fileName) {
                            selectedPreviewFile = null
                        }
                        coroutineScope.launch {
                            val ok = FirebaseBridgeManager.deleteImageFromDualVault(context, fileName)
                            if (ok) {
                                Toast.makeText(context, "Photo deleted & deletion synced to peer", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Could not delete photo", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete for Both", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { fileToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Fullscreen Dual Vault Slideshow Dialog
    if (isSlideshowOpen && session.dualVaultFiles.isNotEmpty()) {
        DualVaultSlideshowDialog(
            files = session.dualVaultFiles,
            initialIndex = slideshowStartIndex,
            slideshowIntervalSeconds = settings.dualVaultSlideshowIntervalSeconds,
            onDismiss = { isSlideshowOpen = false },
            onDelete = { item ->
                fileToDelete = item
            }
        )
    }
}

/**
 * Dedicated Slideshow Player for Dual Vault Images.
 * Configurable 1s - 5s speed setting with play/pause, next/prev, and auto-delete.
 */
@Composable
internal fun DualVaultSlideshowDialog(
    files: List<DualVaultFileInfo>,
    initialIndex: Int,
    slideshowIntervalSeconds: Int = 3,
    onDismiss: () -> Unit,
    onDelete: (DualVaultFileInfo) -> Unit
) {
    var currentIndex by remember { mutableIntStateOf(initialIndex.coerceIn(0, (files.size - 1).coerceAtLeast(0))) }
    var isPlaying by remember { mutableStateOf(true) }
    var currentIntervalSec by remember(slideshowIntervalSeconds) {
        mutableIntStateOf(slideshowIntervalSeconds.coerceIn(1, 5))
    }

    // Auto-advance loop
    LaunchedEffect(isPlaying, currentIndex, files.size, currentIntervalSec) {
        if (isPlaying && files.isNotEmpty()) {
            delay(currentIntervalSec * 1000L)
            currentIndex = (currentIndex + 1) % files.size
        }
    }

    // Ensure valid index when list updates
    LaunchedEffect(files.size) {
        if (files.isEmpty()) {
            onDismiss()
        } else if (currentIndex >= files.size) {
            currentIndex = files.size - 1
        }
    }

    val currentFile = files.getOrNull(currentIndex)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            color = Color.Black
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures { _, dragAmount ->
                            if (dragAmount > 50) {
                                // Swipe right -> Previous
                                if (currentIndex > 0) currentIndex-- else currentIndex = files.size - 1
                            } else if (dragAmount < -50) {
                                // Swipe left -> Next
                                currentIndex = (currentIndex + 1) % files.size
                            }
                        }
                    }
            ) {
                // Main Photo
                currentFile?.let { item ->
                    AsyncImage(
                        model = item.localFile,
                        contentDescription = item.fileName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 70.dp)
                    )
                }

                // Top Bar
                Surface(
                    color = Color.Black.copy(alpha = 0.7f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Dual Vault Slideshow",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 15.sp
                            )
                            currentFile?.let {
                                Text(
                                    text = "${currentIndex + 1} of ${files.size} • ${it.fileName}",
                                    color = Color.LightGray,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }
                }

                // Bottom Control Bar
                Surface(
                    color = Color.Black.copy(alpha = 0.85f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        // Slideshow Speed Selector (1s, 2s, 3s, 4s, 5s)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Speed: ",
                                color = Color.LightGray,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            listOf(1, 2, 3, 4, 5).forEach { sec ->
                                val isSelected = currentIntervalSec == sec
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.DarkGray.copy(alpha = 0.6f),
                                    modifier = Modifier
                                        .padding(horizontal = 3.dp)
                                        .clickable {
                                            currentIntervalSec = sec
                                            SettingsManager.setDualVaultSlideshowIntervalSeconds(sec)
                                        }
                                ) {
                                    Text(
                                        text = "${sec}s",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Previous
                            IconButton(
                                onClick = {
                                    if (currentIndex > 0) currentIndex-- else currentIndex = files.size - 1
                                }
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous", tint = Color.White, modifier = Modifier.size(28.dp))
                            }

                            // Play / Pause
                            IconButton(
                                onClick = { isPlaying = !isPlaying },
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            // Next
                            IconButton(
                                onClick = {
                                    currentIndex = (currentIndex + 1) % files.size
                                }
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(28.dp))
                            }

                            // Delete current photo
                            IconButton(
                                onClick = {
                                    currentFile?.let { onDelete(it) }
                                }
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(26.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Avatar display showing both paired users' profile images side of connected status.
 */
@Composable
fun DualVaultPairAvatars(
    hostImage: String,
    hostName: String,
    peerImage: String,
    peerName: String,
    size: Dp = 34.dp
) {
    Box(contentAlignment = Alignment.Center) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy((-8).dp)
        ) {
            // Host Avatar
            UserAvatarBubble(imageData = hostImage, name = hostName, size = size, borderColor = Color(0xFF107C41))
            // Peer Avatar
            UserAvatarBubble(imageData = peerImage, name = peerName, size = size, borderColor = Color(0xFF0288D1))
        }
        // Connection Link Badge
        Surface(
            shape = CircleShape,
            color = Color(0xFF10B981),
            border = BorderStroke(1.dp, Color.White),
            modifier = Modifier
                .size(14.dp)
                .align(Alignment.BottomCenter)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Link,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(9.dp)
                )
            }
        }
    }
}

@Composable
fun UserAvatarBubble(
    imageData: String,
    name: String,
    size: Dp,
    borderColor: Color
) {
    val bitmap = remember(imageData) {
        if (imageData.isBlank()) null else {
            try {
                val clean = if (imageData.contains(",")) imageData.substringAfter(",") else imageData
                val bytes = android.util.Base64.decode(clean, android.util.Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (_: Throwable) {
                try {
                    val file = File(imageData)
                    if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
                } catch (_: Throwable) {
                    null
                }
            }
        }
    }

    Surface(
        shape = CircleShape,
        color = borderColor.copy(alpha = 0.18f),
        border = BorderStroke(1.5.dp, borderColor),
        modifier = Modifier.size(size)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (bitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                )
            } else {
                Text(
                    text = name.take(1).uppercase().ifBlank { "?" },
                    fontWeight = FontWeight.Bold,
                    fontSize = (size.value * 0.42f).sp,
                    color = borderColor
                )
            }
        }
    }
}
