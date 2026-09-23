package com.example.ui

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.ui.components.ZoomableBox
import com.example.ui.settings.DualVaultPairAvatars
import com.example.ui.settings.DualVaultSlideshowDialog
import com.example.util.DualVaultFileInfo
import com.example.util.FirebaseBridgeManager
import com.example.util.SettingsManager
import kotlinx.coroutines.launch

/**
 * Fullscreen Tab Screen for the Dual Vault.
 * Position in top bar: Excel → DAT Vault → SShow → Dual Vault → Settings
 * Only visible when a device is actually paired.
 * "Close Tab" closes the view and switches back to Excel tab without disconnecting/unpairing.
 * Only explicit "Disconnect" unpairs the devices.
 */
@Composable
fun DualVaultScreen(
    onCloseTab: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val session by FirebaseBridgeManager.currentSession.collectAsState()
    val settings by SettingsManager.settings.collectAsState()

    var isLoading by remember { mutableStateOf(false) }
    var uploadStatusMessage by remember { mutableStateOf<String?>(null) }
    var selectedPreviewFile by remember { mutableStateOf<DualVaultFileInfo?>(null) }
    var isSlideshowOpen by remember { mutableStateOf(false) }
    var slideshowStartIndex by remember { mutableIntStateOf(0) }
    var fileToDelete by remember { mutableStateOf<DualVaultFileInfo?>(null) }
    var showDisconnectConfirmDialog by remember { mutableStateOf(false) }

    // If connection drops or user disconnects, automatically exit the tab
    LaunchedEffect(session.isConnected) {
        if (!session.isConnected) {
            onCloseTab()
        }
    }

    LaunchedEffect(Unit) {
        FirebaseBridgeManager.refreshDualVaultFiles(context)
        if (session.isConnected && session.sessionId.isNotBlank()) {
            FirebaseBridgeManager.startLiveSync(context, session.sessionId)
        }
    }

    // Android device back button: Closes the tab, does NOT disconnect/unpair
    BackHandler {
        if (selectedPreviewFile != null) {
            selectedPreviewFile = null
        } else if (isSlideshowOpen) {
            isSlideshowOpen = false
        } else {
            onCloseTab()
        }
    }

    // Multiple photo picker (batches up to 50 photos)
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

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            // Top Status Header Card
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, Color(0xFF00B894).copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        DualVaultPairAvatars(
                            hostImage = session.hostProfileImage,
                            hostName = session.hostName.ifBlank { "You" },
                            peerImage = session.peerProfileImage,
                            peerName = session.peerName.ifBlank { "Partner" },
                            size = 38.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            val partner = if (session.isHost) session.peerName.ifBlank { "Connected Partner" } else session.hostName.ifBlank { "Connected Host" }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFF00B894),
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
                            modifier = Modifier.size(32.dp).testTag("btn_dual_sync_now")
                        ) {
                            Icon(
                                Icons.Default.Sync,
                                contentDescription = "Sync Now",
                                tint = Color(0xFF00B894),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = { FirebaseBridgeManager.refreshDualVaultFiles(context) },
                            modifier = Modifier.size(32.dp).testTag("btn_dual_refresh")
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Refresh Files",
                                tint = Color(0xFF00B894),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Close Tab Icon (Closes tab view, does NOT disconnect)
                        IconButton(
                            onClick = onCloseTab,
                            modifier = Modifier.size(32.dp).testTag("btn_close_dual_tab_header")
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close Tab",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // Syncing Progress Indicator
            if (uploadStatusMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
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

            // Action Buttons: Upload Photos, Slideshow
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
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00B894)
                    ),
                    modifier = Modifier
                        .weight(1.3f)
                        .testTag("btn_add_dual_vault_photos")
                ) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Upload Photos", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                FilledTonalButton(
                    onClick = {
                        slideshowStartIndex = 0
                        isSlideshowOpen = true
                    },
                    enabled = session.dualVaultFiles.isNotEmpty(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1.1f)
                        .testTag("btn_dual_slideshow")
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Slideshow", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Grid of Synced Dual Vault Photos
            if (session.dualVaultFiles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF00B894).copy(alpha = 0.12f),
                            modifier = Modifier.size(72.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.PhotoLibrary,
                                    contentDescription = null,
                                    tint = Color(0xFF00B894),
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Combined Vault is Empty",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tap 'Upload Photos' above to sync photos to both devices. Photos are saved in Android/media/Dual_Vault without storing images on the cloud.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(0.85f)
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("grid_dual_vault_photos"),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(session.dualVaultFiles, key = { it.fileName }) { item ->
                        val isLocalSender = item.addedBy.equals("You", ignoreCase = true)
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clickable { selectedPreviewFile = item },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                AsyncImage(
                                    model = item.localFile,
                                    contentDescription = item.fileName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )

                                // Sender Badge (Top Left)
                                Surface(
                                    shape = RoundedCornerShape(topStart = 10.dp, bottomEnd = 8.dp),
                                    color = if (isLocalSender) MaterialTheme.colorScheme.primary.copy(alpha = 0.85f) else Color(0xFF00B894).copy(alpha = 0.85f),
                                    modifier = Modifier.align(Alignment.TopStart)
                                ) {
                                    Text(
                                        text = if (isLocalSender) "You" else item.addedBy.take(8),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }

                                // Filename Overlay (Bottom)
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
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Bottom Navigation Actions: Disconnect (unpairs) vs Close Tab (returns to Excel, stays paired)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { showDisconnectConfirmDialog = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).testTag("btn_disconnect_dual_vault")
                ) {
                    Icon(Icons.Default.LinkOff, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Disconnect", fontSize = 12.sp)
                }

                Button(
                    onClick = onCloseTab,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.weight(1f).testTag("btn_close_dual_vault_tab")
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Close Tab", fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
            BackHandler {
                selectedPreviewFile = null
            }
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Black
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
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

                    // Top Bar
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

                    // Bottom Bar
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
                                onClick = { fileToDelete = currentModalFile },
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

    // Delete Confirmation Dialog
    fileToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete from Combined Vault?") },
            text = {
                Text(
                    "Are you sure you want to delete '${item.fileName}'?\n\nThis photo will be removed locally and automatically deleted from the paired device via auto-sync.",
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

    // Disconnect Confirmation Dialog
    if (showDisconnectConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectConfirmDialog = false },
            icon = { Icon(Icons.Default.LinkOff, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Disconnect Dual Vault?") },
            text = {
                Text(
                    "This will unpair both devices and clear photos stored in Dual_Vault.\n\nTo access Combined Vault again in the future, you will need to reconnect using a new 10-digit code.",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDisconnectConfirmDialog = false
                        coroutineScope.launch {
                            FirebaseBridgeManager.disconnect(context)
                            Toast.makeText(context, "Pairing disconnected", Toast.LENGTH_SHORT).show()
                            onCloseTab()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Disconnect & Unpair", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDisconnectConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Fullscreen Slideshow Dialog
    if (isSlideshowOpen && session.dualVaultFiles.isNotEmpty()) {
        DualVaultSlideshowDialog(
            files = session.dualVaultFiles,
            initialIndex = slideshowStartIndex,
            slideshowIntervalSeconds = settings.dualVaultSlideshowIntervalSeconds,
            onDismiss = { isSlideshowOpen = false },
            onDelete = { item -> fileToDelete = item }
        )
    }
}
