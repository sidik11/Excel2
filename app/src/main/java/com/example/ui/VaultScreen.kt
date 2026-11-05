package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.DriveFolderUpload
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.ui.components.VaultModalViewer
import com.example.util.ImgVaultEngine
import com.example.util.VaultImage
import com.example.util.VaultSortMode
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val VaultNavyBg = Color(0xFF0A0E1A)
private val VaultCardBg = Color(0xFF131A2B)
private val VaultBorder = Color(0x336C5CE7)
private val VaultAccent = Color(0xFF6C5CE7)
private val VaultCyan = Color(0xFF00CEC9)
private val VaultDanger = Color(0xFFFF6B6B)
private val VaultSuccess = Color(0xFF00B894)
private val VaultWarning = Color(0xFFFDCB6E)
private val VaultMuted = Color(0xFF8899BB)

@Composable
fun VaultScreen(
    viewModel: VaultViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var showAddImagesChoiceDialog by remember { mutableStateOf(false) }

    // Batch media picker (Storage Access Framework: up to 500 images at once without 100 limit)
    val batchImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val toImport = if (uris.size > 500) {
                Toast.makeText(context, "Selecting first 500 images (batch limit is 500)", Toast.LENGTH_LONG).show()
                uris.take(500)
            } else {
                uris
            }
            viewModel.importImages(toImport)
        }
    }

    // Standard Photo Grid Picker (system capped at 100 photos)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val toImport = if (uris.size > 500) uris.take(500) else uris
            viewModel.importImages(toImport)
        }
    }

    // SAF Folder picker for location
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            // Convert or notify
            Toast.makeText(context, "Location updated", Toast.LENGTH_SHORT).show()
        }
    }

    // SAF Document picker for selecting any .vault file directly
    val vaultFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            // If direct file scheme or content, try resolving
            try {
                val path = uri.path
                if (path != null && File(path).exists()) {
                    viewModel.onSelectVaultFile(File(path))
                } else {
                    Toast.makeText(context, "Selected container: ${uri.lastPathSegment}", Toast.LENGTH_SHORT).show()
                }
            } catch (_: Throwable) {}
        }
    }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbarMessage()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(VaultNavyBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            // 1. Header
            VaultHeader()

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Vault Location Panel
            VaultLocationPanel(
                currentLocation = uiState.selectedLocationDir?.absolutePath ?: "Default Storage",
                onChooseLocation = { folderPickerLauncher.launch(null) },
                onRefresh = { viewModel.scanAvailableVaults() }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 3. Container Selection & Password / Status Row
            if (!uiState.isUnlocked) {
                VaultLockedControls(
                    uiState = uiState,
                    onSelectFile = { viewModel.onSelectVaultFile(it) },
                    onPasswordChange = { viewModel.setPasswordInput(it) },
                    onTogglePasswordVisibility = { viewModel.togglePasswordVisibility() },
                    onUnlock = { viewModel.unlockVault() },
                    onOpenCreate = { viewModel.openCreateDialog() },
                    onFilePicker = { vaultFilePickerLauncher.launch(arrayOf("*/*")) }
                )
            } else {
                VaultUnlockedHeaderActions(
                    uiState = uiState,
                    onLock = { viewModel.lockVault() },
                    onLockAndClear = { viewModel.lockAndClearSession() },
                    onInfo = { viewModel.openVaultInfoDialog() },
                    onCompact = { viewModel.compactVault() }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 4. Vault Status Panel
            VaultStatusPanel(
                uiState = uiState,
                onInfoClick = { viewModel.openVaultInfoDialog() }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 5. Processing progress indicator
            if (uiState.isProcessing) {
                Surface(
                    color = VaultCardBg,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, VaultBorder),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = uiState.progressLabel.ifEmpty { "Processing…" },
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                color = VaultCyan,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        if (uiState.progressPercent > 0f) {
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { uiState.progressPercent },
                                modifier = Modifier.fillMaxWidth().height(4.dp),
                                color = VaultCyan,
                                trackColor = Color.White.copy(alpha = 0.1f)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            // 6. Main Area: Locked State View vs Unlocked Gallery
            if (!uiState.isUnlocked) {
                LockedStatePlaceholder(
                    message = uiState.statusMessage,
                    isError = uiState.isError,
                    onOpenCreate = { viewModel.openCreateDialog() }
                )
            } else {
                UnlockedGalleryView(
                    uiState = uiState,
                    onAddImages = {
                        showAddImagesChoiceDialog = true
                    },
                    onImageClick = { index -> viewModel.openModal(index) },
                    onImageLongClick = { img ->
                        if (!uiState.isMultiSelectActive) {
                            viewModel.startMultiSelect(img.id)
                        } else {
                            viewModel.toggleSelectImage(img.id)
                        }
                    },
                    onToggleSelect = { id -> viewModel.toggleSelectImage(id) },
                    onSelectAll = { viewModel.selectAll() },
                    onClearSelect = { viewModel.clearSelection() },
                    onOpenDeleteConfirm = { viewModel.openDeleteConfirmDialog() },
                    onOpenFolderAction = { viewModel.openFolderActionDialog() },
                    onOpenMobileStorage = { viewModel.openMobileStorageDialog() },
                    onShareSelected = { viewModel.shareSelectedImages(context) },
                    onOpenCreateFolder = { viewModel.openCreateFolderDialog() },
                    onSelectFolder = { folderId -> viewModel.setFolder(folderId) },
                    onRenameImage = { img -> viewModel.openRenameDialog(img) },
                    onSearchQueryChange = { viewModel.setSearchQuery(it) },
                    onSortModeChange = { viewModel.setSortMode(it) },
                    onToggleViewMode = { viewModel.toggleGridView() },
                    onImportToSsi = { img -> viewModel.importImageToSsi(img) }
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
        )

        // Fullscreen Modal Viewer
        uiState.activeModalIndex?.let { index ->
            VaultModalViewer(
                images = uiState.displayedImages,
                currentIndex = index,
                isSlideshowPlaying = uiState.isSlideshowPlaying,
                isModalPaused = uiState.isModalPaused,
                slideshowProgress = uiState.slideshowProgress,
                onClose = { viewModel.closeModal() },
                onNext = { viewModel.nextImage() },
                onPrevious = { viewModel.previousImage() },
                onToggleSlideshow = { viewModel.toggleSlideshow() },
                onTogglePause = { viewModel.togglePause() },
                onImportToSsi = { image -> viewModel.importImageToSsi(image) }
            )
        }

        // Dialogs
        if (showAddImagesChoiceDialog) {
            AddImagesChoiceDialog(
                onDismiss = { showAddImagesChoiceDialog = false },
                onChooseBatch500 = {
                    showAddImagesChoiceDialog = false
                    batchImagePickerLauncher.launch(arrayOf("image/*"))
                },
                onChoosePhotoPicker100 = {
                    showAddImagesChoiceDialog = false
                    photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            )
        }

        if (uiState.isCreateDialogOpen) {
            CreateVaultDialog(
                onDismiss = { viewModel.closeCreateDialog() },
                onCreate = { name, pass, confirm, cap ->
                    viewModel.createVault(name, pass, confirm, cap)
                }
            )
        }

        if (uiState.showRecoveryKeyDialog && uiState.generatedRecoveryKey != null) {
            RecoveryKeyDialog(
                recoveryKey = uiState.generatedRecoveryKey!!,
                onDismiss = { viewModel.closeRecoveryKeyDialog() }
            )
        }

        if (uiState.isVaultInfoDialogOpen && uiState.metadata != null) {
            VaultInfoDialog(
                metadata = uiState.metadata!!,
                file = uiState.selectedVaultFile,
                onDismiss = { viewModel.closeVaultInfoDialog() }
            )
        }

        if (uiState.isCreateFolderDialogOpen) {
            CreateFolderDialog(
                onDismiss = { viewModel.closeCreateFolderDialog() },
                onCreate = { name -> viewModel.createFolder(name) }
            )
        }

        if (uiState.isRenameDialogOpen && uiState.renameTargetImage != null) {
            RenameImageDialog(
                currentName = uiState.renameTargetImage!!.name,
                onDismiss = { viewModel.closeRenameDialog() },
                onRename = { newName -> viewModel.renameImage(newName) }
            )
        }

        if (uiState.isDeleteConfirmDialogOpen) {
            DeleteConfirmDialog(
                count = uiState.selectedImageIds.size,
                onDismiss = { viewModel.closeDeleteConfirmDialog() },
                onConfirm = { viewModel.deleteSelectedImages() }
            )
        }

        if (uiState.isFolderActionDialogOpen) {
            VaultFolderActionDialog(
                count = uiState.selectedImageIds.size,
                folders = uiState.folders,
                onDismiss = { viewModel.closeFolderActionDialog() },
                onMove = { targetId -> viewModel.moveSelectedImages(targetId) },
                onCopy = { targetId -> viewModel.copySelectedImages(targetId) },
                onOpenCreateFolder = { viewModel.openCreateFolderDialog() }
            )
        }

        if (uiState.isMobileStorageDialogOpen) {
            MobileStorageActionDialog(
                count = uiState.selectedImageIds.size,
                onDismiss = { viewModel.closeMobileStorageDialog() },
                onCopyToMobile = { viewModel.exportSelectedImages(deleteFromVaultAfterExport = false) },
                onMoveToMobile = { viewModel.exportSelectedImages(deleteFromVaultAfterExport = true) }
            )
        }

        if (uiState.isMoveDialogOpen) {
            MoveImagesDialog(
                folders = uiState.folders,
                onDismiss = { viewModel.closeMoveDialog() },
                onMove = { targetId -> viewModel.moveSelectedImages(targetId) }
            )
        }
    }
}

// ==========================================
// 1. Header
// ==========================================
@Composable
private fun VaultHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "IMG Vault",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    color = VaultAccent.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "ENCRYPTED .VAULT",
                        color = VaultCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Text(
                text = "Encrypted Images • Offline • Low RAM • Portable",
                fontSize = 11.sp,
                color = VaultMuted,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

// ==========================================
// 2. Vault Location Panel
// ==========================================
@Composable
private fun VaultLocationPanel(
    currentLocation: String,
    onChooseLocation: () -> Unit,
    onRefresh: () -> Unit
) {
    Surface(
        color = VaultCardBg,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, VaultBorder),
        modifier = Modifier.fillMaxWidth().testTag("vault_location_panel")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, tint = VaultCyan, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("Vault Location", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = VaultMuted)
                    Text(
                        text = currentLocation.substringAfterLast("media/").ifEmpty { currentLocation },
                        fontSize = 12.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.size(32.dp).testTag("btn_refresh_location")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = VaultMuted, modifier = Modifier.size(16.dp))
                }
                Spacer(modifier = Modifier.width(4.dp))
                Button(
                    onClick = onChooseLocation,
                    colors = ButtonDefaults.buttonColors(containerColor = VaultAccent.copy(alpha = 0.3f), contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.height(28.dp).testTag("btn_choose_location")
                ) {
                    Text("Choose", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ==========================================
// 3. Locked Controls (Selection & Password)
// ==========================================
@Composable
private fun VaultLockedControls(
    uiState: VaultUiState,
    onSelectFile: (File) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onUnlock: () -> Unit,
    onOpenCreate: () -> Unit,
    onFilePicker: () -> Unit
) {
    var dropdownExpanded by remember { mutableStateOf(false) }

    Surface(
        color = VaultCardBg,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, VaultBorder),
        modifier = Modifier.fillMaxWidth().testTag("vault_locked_controls")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Container Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SELECT VAULT FILE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = VaultCyan
                )

                Row {
                    TextButton(
                        onClick = onFilePicker,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("Browse", fontSize = 11.sp, color = VaultAccent)
                    }
                    TextButton(
                        onClick = onOpenCreate,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("+ Create Vault", fontSize = 11.sp, color = VaultSuccess, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Dropdown Selector Button
            Box(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    color = Color.Black.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                    modifier = Modifier.fillMaxWidth().clickable { dropdownExpanded = true }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = VaultMuted, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = uiState.selectedVaultName ?: (if (uiState.availableVaultFiles.isEmpty()) "No .vault found (Create one)" else "Choose .vault file"),
                                color = if (uiState.selectedVaultFile != null) Color.White else VaultMuted,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Text("▼", color = VaultMuted, fontSize = 10.sp)
                    }
                }

                DropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false },
                    modifier = Modifier.background(VaultCardBg)
                ) {
                    if (uiState.availableVaultFiles.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("No .vault files in this location", color = VaultMuted, fontSize = 12.sp) },
                            onClick = { dropdownExpanded = false }
                        )
                    } else {
                        uiState.availableVaultFiles.forEach { item ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(item.fileName, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        Text("${item.sizeBytes / (1024*1024)} MB", color = VaultMuted, fontSize = 10.sp)
                                    }
                                },
                                onClick = {
                                    onSelectFile(item.file)
                                    dropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Password Field
            OutlinedTextField(
                value = uiState.passwordInput,
                onValueChange = onPasswordChange,
                label = { Text("VAULT PASSWORD", fontSize = 11.sp) },
                placeholder = { Text("Enter master password or recovery key", fontSize = 12.sp) },
                singleLine = true,
                visualTransformation = if (uiState.isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = onTogglePasswordVisibility) {
                        Icon(
                            imageVector = if (uiState.isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (uiState.isPasswordVisible) "Hide Password" else "Show Password",
                            tint = VaultMuted
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onUnlock() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = VaultCyan,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                    focusedLabelColor = VaultCyan,
                    unfocusedLabelColor = VaultMuted,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = VaultCyan
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().testTag("input_vault_password")
            )

            if (uiState.statusMessage != null && uiState.isError) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = uiState.statusMessage ?: "",
                    color = VaultDanger,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Unlock Button
            Button(
                onClick = onUnlock,
                enabled = uiState.selectedVaultFile != null && uiState.passwordInput.isNotBlank() && !uiState.isProcessing,
                colors = ButtonDefaults.buttonColors(
                    containerColor = VaultCyan,
                    contentColor = Color.Black,
                    disabledContainerColor = Color.White.copy(alpha = 0.08f),
                    disabledContentColor = VaultMuted
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().height(44.dp).testTag("btn_unlock_vault")
            ) {
                Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Unlock Vault", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ==========================================
// Unlocked Header Actions
// ==========================================
@Composable
private fun VaultUnlockedHeaderActions(
    uiState: VaultUiState,
    onLock: () -> Unit,
    onLockAndClear: () -> Unit,
    onInfo: () -> Unit,
    onCompact: () -> Unit
) {
    Surface(
        color = VaultCardBg,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, VaultBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = onLock,
                    colors = ButtonDefaults.buttonColors(containerColor = VaultDanger.copy(alpha = 0.25f), contentColor = VaultDanger),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp).testTag("btn_lock_vault")
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Lock Vault", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.width(6.dp))

                OutlinedButton(
                    onClick = onLockAndClear,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = VaultMuted),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp).testTag("btn_lock_clear_session")
                ) {
                    Text("Lock & Clear", fontSize = 11.sp)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCompact, modifier = Modifier.size(32.dp).testTag("btn_compact_vault")) {
                    Icon(Icons.Default.Tune, contentDescription = "Compact Vault", tint = VaultCyan, modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onInfo, modifier = Modifier.size(32.dp).testTag("btn_vault_info")) {
                    Icon(Icons.Default.Info, contentDescription = "Vault Information", tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

// ==========================================
// 4. Status Panel
// ==========================================
@Composable
private fun VaultStatusPanel(
    uiState: VaultUiState,
    onInfoClick: () -> Unit
) {
    val meta = uiState.metadata
    val isUnlocked = uiState.isUnlocked

    Surface(
        color = VaultCardBg,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, VaultBorder),
        modifier = Modifier.fillMaxWidth().testTag("vault_status_panel")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(if (isUnlocked) VaultSuccess else VaultDanger, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isUnlocked) "UNLOCKED" else "LOCKED",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isUnlocked) VaultSuccess else VaultDanger
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "• ${uiState.selectedVaultName ?: "None"}",
                        fontSize = 12.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (isUnlocked && meta != null) {
                    Text(
                        text = "${meta.activeImageCount} images",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = VaultCyan
                    )
                }
            }

            if (isUnlocked && meta != null) {
                Spacer(modifier = Modifier.height(6.dp))
                val usedMB = meta.totalUsedBytes / (1024 * 1024)
                val maxGB = meta.maxCapacityBytes / (1024L * 1024 * 1024)
                val availGB = "%.2f".format(meta.availableBytes.toDouble() / (1024.0 * 1024 * 1024))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Used: $usedMB MB of $maxGB GB", fontSize = 11.sp, color = VaultMuted)
                    Text("Available: $availGB GB", fontSize = 11.sp, color = VaultSuccess)
                }
                Spacer(modifier = Modifier.height(4.dp))
                val fraction = (meta.totalUsedBytes.toFloat() / meta.maxCapacityBytes.coerceAtLeast(1L)).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = VaultCyan,
                    trackColor = Color.White.copy(alpha = 0.1f)
                )
            }
        }
    }
}

// ==========================================
// Locked State Placeholder
// ==========================================
@Composable
private fun LockedStatePlaceholder(
    message: String?,
    isError: Boolean,
    onOpenCreate: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            color = Color.White.copy(alpha = 0.05f),
            shape = CircleShape,
            modifier = Modifier.size(72.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = VaultCyan,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Unlock your vault to view and manage private images.",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "All images are encrypted using AES-256-GCM directly inside the portable .vault file with zero RAM pre-allocation.",
            color = VaultMuted,
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        OutlinedButton(
            onClick = onOpenCreate,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = VaultCyan),
            border = BorderStroke(1.dp, VaultCyan.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Create New Vault", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ==========================================
// 5. Unlocked Gallery View
// ==========================================
@Composable
private fun UnlockedGalleryView(
    uiState: VaultUiState,
    onAddImages: () -> Unit,
    onImageClick: (Int) -> Unit,
    onImageLongClick: (VaultImage) -> Unit,
    onToggleSelect: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelect: () -> Unit,
    onOpenDeleteConfirm: () -> Unit,
    onOpenFolderAction: () -> Unit,
    onOpenMobileStorage: () -> Unit,
    onShareSelected: () -> Unit,
    onOpenCreateFolder: () -> Unit,
    onSelectFolder: (String?) -> Unit,
    onRenameImage: (VaultImage) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSortModeChange: (VaultSortMode) -> Unit,
    onToggleViewMode: () -> Unit,
    onImportToSsi: (VaultImage) -> Unit
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Folders Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // "All" folder chip
            FilterChip(
                selected = uiState.currentFolderId == null,
                onClick = { onSelectFolder(null) },
                label = { Text("All (${uiState.images.size})", fontSize = 11.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = VaultAccent,
                    selectedLabelColor = Color.White,
                    containerColor = VaultCardBg,
                    labelColor = VaultMuted
                ),
                shape = RoundedCornerShape(16.dp)
            )

            uiState.folders.forEach { folder ->
                val count = uiState.images.count { it.folderId == folder.id }
                FilterChip(
                    selected = uiState.currentFolderId == folder.id,
                    onClick = { onSelectFolder(folder.id) },
                    label = { Text("${folder.name} ($count)", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = VaultAccent,
                        selectedLabelColor = Color.White,
                        containerColor = VaultCardBg,
                        labelColor = VaultMuted
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
            }

            // New Folder Button
            IconButton(
                onClick = onOpenCreateFolder,
                modifier = Modifier.size(32.dp).testTag("btn_create_folder")
            ) {
                Icon(Icons.Default.CreateNewFolder, contentDescription = "New Folder", tint = VaultCyan, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Search & Controls Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Search Input
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Search photos…", fontSize = 11.sp, color = VaultMuted) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = VaultMuted, modifier = Modifier.size(16.dp)) },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = VaultMuted, modifier = Modifier.size(14.dp))
                        }
                    }
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = VaultCyan,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f).height(42.dp)
            )

            Spacer(modifier = Modifier.width(6.dp))

            // Sort Menu
            Box {
                IconButton(
                    onClick = { sortMenuExpanded = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.Sort, contentDescription = "Sort", tint = Color.White)
                }

                DropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = { sortMenuExpanded = false },
                    modifier = Modifier.background(VaultCardBg)
                ) {
                    DropdownMenuItem(
                        text = { Text("Date (Newest first)", color = Color.White, fontSize = 12.sp) },
                        onClick = { onSortModeChange(VaultSortMode.DATE_DESC); sortMenuExpanded = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Name (A–Z)", color = Color.White, fontSize = 12.sp) },
                        onClick = { onSortModeChange(VaultSortMode.NAME_ASC); sortMenuExpanded = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Size (Largest first)", color = Color.White, fontSize = 12.sp) },
                        onClick = { onSortModeChange(VaultSortMode.SIZE_DESC); sortMenuExpanded = false }
                    )
                }
            }

            // Grid / List toggle
            IconButton(
                onClick = onToggleViewMode,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (uiState.isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                    contentDescription = "Toggle View",
                    tint = Color.White
                )
            }

            // Add Images Button
            Button(
                onClick = onAddImages,
                colors = ButtonDefaults.buttonColors(containerColor = VaultCyan, contentColor = Color.Black),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier.height(36.dp).testTag("btn_add_images")
            ) {
                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("+ Add (500)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Multi-select Action Bar
        if (uiState.isMultiSelectActive) {
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                color = VaultAccent.copy(alpha = 0.25f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, VaultCyan.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth().testTag("multiselect_action_bar")
            ) {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${uiState.selectedImageIds.size} Selected",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(
                                onClick = onSelectAll,
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (uiState.selectedImageIds.size == uiState.displayedImages.size) "Deselect All" else "Select All",
                                    fontSize = 11.sp,
                                    color = VaultCyan
                                )
                            }
                        }

                        IconButton(onClick = onClearSelect, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel", tint = VaultMuted, modifier = Modifier.size(18.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Action Buttons Row: Vault Folder (Move/Copy) | Phone Export (Move/Copy) | Delete
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Move/Copy to Vault Folder
                        Button(
                            onClick = onOpenFolderAction,
                            colors = ButtonDefaults.buttonColors(containerColor = VaultCyan, contentColor = Color.Black),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.weight(1.1f).height(32.dp).testTag("btn_vault_folder_action")
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Vault Folder", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }

                        // 2. Move/Copy to Phone Storage
                        Button(
                            onClick = onOpenMobileStorage,
                            colors = ButtonDefaults.buttonColors(containerColor = VaultSuccess, contentColor = Color.Black),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.weight(1.1f).height(32.dp).testTag("btn_mobile_storage_action")
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Phone Export", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }

                        // 3. Delete Selected
                        Button(
                            onClick = onOpenDeleteConfirm,
                            colors = ButtonDefaults.buttonColors(containerColor = VaultDanger, contentColor = Color.White),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.weight(0.9f).height(32.dp).testTag("btn_delete_selected")
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Image List or Grid
        val images = uiState.displayedImages
        if (images.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = VaultMuted, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No images in this folder yet", color = VaultMuted, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onAddImages,
                        colors = ButtonDefaults.buttonColors(containerColor = VaultAccent),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Add Images (Up to 500)", fontSize = 12.sp)
                    }
                }
            }
        } else if (uiState.isGridView) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 100.dp),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 60.dp)
            ) {
                items(images, key = { it.id }) { image ->
                    val isSelected = uiState.selectedImageIds.contains(image.id)
                    val idx = images.indexOf(image)
                    VaultImageGridCard(
                        image = image,
                        isSelected = isSelected,
                        isMultiSelectActive = uiState.isMultiSelectActive,
                        onClick = {
                            if (uiState.isMultiSelectActive) {
                                onToggleSelect(image.id)
                            } else {
                                onImageClick(idx)
                            }
                        },
                        onLongClick = { onImageLongClick(image) },
                        onRename = { onRenameImage(image) },
                        onImportToSsi = { onImportToSsi(image) }
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 60.dp)
            ) {
                items(images, key = { it.id }) { image ->
                    val isSelected = uiState.selectedImageIds.contains(image.id)
                    val idx = images.indexOf(image)
                    VaultImageListCard(
                        image = image,
                        isSelected = isSelected,
                        isMultiSelectActive = uiState.isMultiSelectActive,
                        onClick = {
                            if (uiState.isMultiSelectActive) {
                                onToggleSelect(image.id)
                            } else {
                                onImageClick(idx)
                            }
                        },
                        onLongClick = { onImageLongClick(image) },
                        onRename = { onRenameImage(image) },
                        onImportToSsi = { onImportToSsi(image) }
                    )
                }
            }
        }
    }
}

// ==========================================
// Grid Card
// ==========================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VaultImageGridCard(
    image: VaultImage,
    isSelected: Boolean,
    isMultiSelectActive: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRename: () -> Unit,
    onImportToSsi: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = VaultCardBg),
        border = BorderStroke(
            if (isSelected) 2.dp else 1.dp,
            if (isSelected) VaultCyan else Color.White.copy(alpha = 0.08f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(image.thumbnailFile ?: image.file)
                    .crossfade(true)
                    .build(),
                contentDescription = image.name,
                contentScale = ContentScale.Crop,
                loading = {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(strokeWidth = 2.dp, color = VaultCyan, modifier = Modifier.size(20.dp))
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Bottom Name Label
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = image.name,
                    color = Color.White,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Selection Indicator Overlay
            if (isMultiSelectActive) {
                Surface(
                    color = if (isSelected) VaultCyan else Color.Black.copy(alpha = 0.6f),
                    shape = CircleShape,
                    border = BorderStroke(1.5.dp, if (isSelected) VaultCyan else Color.White.copy(alpha = 0.8f)),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(24.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = Color.Black,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// List Card
// ==========================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VaultImageListCard(
    image: VaultImage,
    isSelected: Boolean,
    isMultiSelectActive: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRename: () -> Unit,
    onImportToSsi: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = VaultCardBg),
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) VaultCyan else Color.White.copy(alpha = 0.08f)),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(image.thumbnailFile ?: image.file)
                    .crossfade(true)
                    .build(),
                contentDescription = image.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(6.dp))
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = image.name,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val sizeKB = image.sizeBytes / 1024
                val dateStr = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(image.createdAt))
                Text(
                    text = "$sizeKB KB • $dateStr",
                    color = VaultMuted,
                    fontSize = 11.sp
                )
            }

            // Action buttons
            IconButton(onClick = onImportToSsi, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.FileDownload, contentDescription = "Import to SSI", tint = VaultAccent, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onRename, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Edit, contentDescription = "Rename", tint = VaultMuted, modifier = Modifier.size(16.dp))
            }

            if (isMultiSelectActive) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() },
                    colors = CheckboxDefaults.colors(checkedColor = VaultCyan, checkmarkColor = Color.Black)
                )
            }
        }
    }
}

// ==========================================
// Dialogs: Create, Recovery, Info, Folders
// ==========================================
@Composable
private fun CreateVaultDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, pass: String, confirm: String, capacityBytes: Long) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var selectedCapacity by remember { mutableStateOf(ImgVaultEngine.CAPACITY_5GB) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New IMG Vault", fontWeight = FontWeight.Bold, color = Color.White) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Text(
                    text = "A secure container (.vault) will be created. It only consumes storage as images are imported.",
                    fontSize = 11.sp,
                    color = VaultMuted
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Vault Name") },
                    placeholder = { Text("e.g. Personal_Gallery") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))
                Text("Max Capacity Limit", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = selectedCapacity == ImgVaultEngine.CAPACITY_5GB,
                        onClick = { selectedCapacity = ImgVaultEngine.CAPACITY_5GB },
                        colors = RadioButtonDefaults.colors(selectedColor = VaultCyan)
                    )
                    Text("5 GB Limit", fontSize = 12.sp, color = Color.White)
                    Spacer(modifier = Modifier.width(16.dp))
                    RadioButton(
                        selected = selectedCapacity == ImgVaultEngine.CAPACITY_10GB,
                        onClick = { selectedCapacity = ImgVaultEngine.CAPACITY_10GB },
                        colors = RadioButtonDefaults.colors(selectedColor = VaultCyan)
                    )
                    Text("10 GB Limit", fontSize = 12.sp, color = Color.White)
                }

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Vault Password (min 6 chars)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("Confirm Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name, password, confirmPassword, selectedCapacity) },
                colors = ButtonDefaults.buttonColors(containerColor = VaultCyan, contentColor = Color.Black)
            ) {
                Text("Create Vault", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = VaultMuted) }
        },
        containerColor = VaultCardBg
    )
}

@Composable
private fun RecoveryKeyDialog(
    recoveryKey: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LockReset, contentDescription = null, tint = VaultWarning)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Emergency Recovery Key", fontWeight = FontWeight.Bold, color = Color.White)
            }
        },
        text = {
            Column {
                Text(
                    text = "Save this 32-character recovery key in a safe place. If you forget your vault password, this key can restore access to your encrypted images.",
                    fontSize = 12.sp,
                    color = VaultMuted
                )
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    color = Color.Black.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, VaultWarning.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth().padding(4.dp)
                ) {
                    Text(
                        text = recoveryKey,
                        color = VaultWarning,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clip.setPrimaryClip(ClipData.newPlainText("Recovery Key", recoveryKey))
                    Toast.makeText(context, "Recovery key copied to clipboard!", Toast.LENGTH_SHORT).show()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = VaultCyan, contentColor = Color.Black)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Copy & Close")
            }
        },
        containerColor = VaultCardBg
    )
}

@Composable
private fun VaultInfoDialog(
    metadata: com.example.util.ImgVaultMetadata,
    file: File?,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Vault Information", fontWeight = FontWeight.Bold, color = Color.White) },
        text = {
            Column {
                Text("Name: ${metadata.name}", color = Color.White, fontSize = 13.sp)
                Text("File: ${file?.name ?: "Unknown"}", color = VaultMuted, fontSize = 12.sp)
                Text("Format: Encrypted .vault (AES-256-GCM)", color = VaultCyan, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(8.dp))
                val usedMB = metadata.totalUsedBytes / (1024 * 1024)
                val maxGB = metadata.maxCapacityBytes / (1024L * 1024 * 1024)
                Text("Storage: $usedMB MB used of $maxGB GB limit", color = Color.White, fontSize = 12.sp)
                Text("Active Images: ${metadata.activeImageCount}", color = Color.White, fontSize = 12.sp)
                Text("Folders: ${metadata.folders.size}", color = Color.White, fontSize = 12.sp)
                val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(metadata.createdAt))
                Text("Created: $dateStr", color = VaultMuted, fontSize = 11.sp)
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = VaultAccent)) {
                Text("Close")
            }
        },
        containerColor = VaultCardBg
    )
}

@Composable
private fun CreateFolderDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String) -> Unit
) {
    var folderName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Folder", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = folderName,
                onValueChange = { folderName = it },
                label = { Text("Folder Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { onCreate(folderName) },
                colors = ButtonDefaults.buttonColors(containerColor = VaultCyan, contentColor = Color.Black)
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = VaultMuted) }
        },
        containerColor = VaultCardBg
    )
}

@Composable
private fun RenameImageDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onRename: (newName: String) -> Unit
) {
    var newName by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Image", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text("Image Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { onRename(newName) },
                colors = ButtonDefaults.buttonColors(containerColor = VaultCyan, contentColor = Color.Black)
            ) {
                Text("Rename")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = VaultMuted) }
        },
        containerColor = VaultCardBg
    )
}

@Composable
private fun MoveImagesDialog(
    folders: List<com.example.util.ImgVaultFolder>,
    onDismiss: () -> Unit,
    onMove: (targetFolderId: String?) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move to Folder", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Select destination folder:", fontSize = 12.sp, color = VaultMuted)
                Spacer(modifier = Modifier.height(8.dp))
                folders.forEach { f ->
                    Surface(
                        color = Color.White.copy(alpha = 0.05f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clickable { onMove(f.id) }
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = null, tint = VaultCyan, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(f.name, color = Color.White, fontSize = 13.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = VaultMuted) }
        },
        containerColor = VaultCardBg
    )
}

@Composable
private fun AddImagesChoiceDialog(
    onDismiss: () -> Unit,
    onChooseBatch500: () -> Unit,
    onChoosePhotoPicker100: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VaultCardBg,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AddPhotoAlternate,
                    contentDescription = null,
                    tint = VaultCyan,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Add Images to Vault",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Select how you would like to pick photos. The batch picker allows selecting up to 500 images at a time:",
                    color = VaultMuted,
                    fontSize = 12.sp
                )

                // Option 1: Up to 500
                Surface(
                    onClick = onChooseBatch500,
                    color = VaultAccent.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, VaultCyan.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth().testTag("opt_batch_picker_500")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = VaultCyan.copy(alpha = 0.2f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.DriveFolderUpload,
                                    contentDescription = null,
                                    tint = VaultCyan,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Batch Picker",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = VaultCyan,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "UP TO 500",
                                        color = Color.Black,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Select up to 500 images at once from Gallery, Camera, Pictures, or SD Card with no 100-photo limit.",
                                color = VaultMuted,
                                fontSize = 11.sp,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }

                // Option 2: Up to 100
                Surface(
                    onClick = onChoosePhotoPicker100,
                    color = Color.White.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                    modifier = Modifier.fillMaxWidth().testTag("opt_photo_picker_100")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.1f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.PhotoLibrary,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "System Photo Grid",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = Color.White.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "UP TO 100",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Standard Android visual picker (hard-capped to 100 items by Android OS).",
                                color = VaultMuted,
                                fontSize = 11.sp,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = VaultMuted, fontSize = 13.sp)
            }
        }
    )
}

// ==========================================
// Delete Confirmation Dialog
// ==========================================
@Composable
private fun DeleteConfirmDialog(
    count: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VaultCardBg,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = VaultDanger, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Delete $count Images?", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            Column {
                Text(
                    text = "Are you sure you want to delete $count selected image(s) from this vault container?",
                    color = Color.White,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "The images will be removed from the active gallery. You can run 'Compact' in the vault header at any time to physically reclaim disk space.",
                    color = VaultMuted,
                    fontSize = 11.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = VaultDanger, contentColor = Color.White),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Delete ($count)", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = VaultMuted)
            }
        }
    )
}

// ==========================================
// Vault Folders: Move / Copy Dialog
// ==========================================
@Composable
private fun VaultFolderActionDialog(
    count: Int,
    folders: List<com.example.util.ImgVaultFolder>,
    onDismiss: () -> Unit,
    onMove: (targetFolderId: String?) -> Unit,
    onCopy: (targetFolderId: String?) -> Unit,
    onOpenCreateFolder: () -> Unit
) {
    var selectedTargetFolderId by remember { mutableStateOf<String?>(null) } // null = Main Gallery

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VaultCardBg,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Folder, contentDescription = null, tint = VaultCyan, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Vault Folders ($count items)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Select destination folder inside vault:", fontSize = 12.sp, color = VaultMuted)
                Spacer(modifier = Modifier.height(10.dp))

                // Folder Choices list
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Option: Root / Main Gallery
                    Surface(
                        color = if (selectedTargetFolderId == null) VaultAccent.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.05f),
                        border = BorderStroke(1.dp, if (selectedTargetFolderId == null) VaultCyan else Color.Transparent),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().clickable { selectedTargetFolderId = null }
                    ) {
                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = selectedTargetFolderId == null,
                                onClick = { selectedTargetFolderId = null },
                                colors = RadioButtonDefaults.colors(selectedColor = VaultCyan)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(Icons.Default.FolderOpen, contentDescription = null, tint = VaultCyan, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Main Gallery (No folder)", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }

                    // Existing user folders
                    folders.forEach { f ->
                        Surface(
                            color = if (selectedTargetFolderId == f.id) VaultAccent.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.05f),
                            border = BorderStroke(1.dp, if (selectedTargetFolderId == f.id) VaultCyan else Color.Transparent),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().clickable { selectedTargetFolderId = f.id }
                        ) {
                            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = selectedTargetFolderId == f.id,
                                    onClick = { selectedTargetFolderId = f.id },
                                    colors = RadioButtonDefaults.colors(selectedColor = VaultCyan)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(Icons.Default.Folder, contentDescription = null, tint = VaultCyan, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(f.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // + New folder shortcut
                OutlinedButton(
                    onClick = onOpenCreateFolder,
                    border = BorderStroke(1.dp, VaultCyan.copy(alpha = 0.4f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = VaultCyan),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("+ Create New Folder", fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onCopy(selectedTargetFolderId) },
                    border = BorderStroke(1.dp, VaultCyan),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = VaultCyan),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy Here", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = { onMove(selectedTargetFolderId) },
                    colors = ButtonDefaults.buttonColors(containerColor = VaultCyan, contentColor = Color.Black),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.DriveFileMove, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Move Here", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = VaultMuted)
            }
        }
    )
}

// ==========================================
// Mobile Local Storage: Move / Copy Dialog
// ==========================================
@Composable
private fun MobileStorageActionDialog(
    count: Int,
    onDismiss: () -> Unit,
    onCopyToMobile: () -> Unit,
    onMoveToMobile: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VaultCardBg,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FileDownload, contentDescription = null, tint = VaultSuccess, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Phone Storage ($count items)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Export decrypted images to your device gallery (Pictures/IMGVault). Choose an option below:",
                    color = VaultMuted,
                    fontSize = 12.sp
                )

                // Option 1: Copy to Mobile (Keep in Vault)
                Surface(
                    onClick = onCopyToMobile,
                    color = Color.White.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, VaultSuccess.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth().testTag("opt_copy_to_mobile")
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = VaultSuccess.copy(alpha = 0.2f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, tint = VaultSuccess, modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Copy to Mobile (Keep in Vault)", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "Saves unencrypted copies to phone gallery. Originals remain securely inside the vault.",
                                color = VaultMuted,
                                fontSize = 11.sp,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }

                // Option 2: Move to Mobile (Delete from Vault)
                Surface(
                    onClick = onMoveToMobile,
                    color = Color.White.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, VaultAccent.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth().testTag("opt_move_to_mobile")
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = VaultAccent.copy(alpha = 0.2f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.DriveFileMove, contentDescription = null, tint = VaultAccent, modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Move to Mobile (Delete from Vault)", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "Saves unencrypted copies to phone gallery and deletes them from this vault container.",
                                color = VaultMuted,
                                fontSize = 11.sp,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = VaultMuted)
            }
        }
    )
}

