package com.example.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.input.KeyboardType
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
import com.example.util.VaultImage

private val VaultBg = Color(0xFF0A0E1A)
private val VaultCardBg = Color(0xFF131A2B)
private val VaultBorder = Color(0x2A6C5CE7)
private val VaultAccent = Color(0xFF6C5CE7)
private val VaultAccent2 = Color(0xFFA29BFE)
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

    // Folder picker for Create
    val createFolderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) viewModel.onCreateFolderSelected(uri)
    }

    // Folder picker for Access
    val accessFolderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) viewModel.onAccessFolderSelected(uri)
    }

    // Direct .dat file picker
    val datFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.onDirectDatFilePicked(uri)
    }

    // Photo picker for adding images
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.addImages(uris)
    }

    // Auto-detect and sync changes when returning to the app from the phone's File Manager
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.syncFromExVault()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VaultBg)
    ) {
        // Vault Header Card
        VaultHeaderSection(
            currentTab = uiState.currentTab,
            onTabSelected = { viewModel.setTab(it) }
        )

        // Main content area
        Box(modifier = Modifier.weight(1f)) {
            when (uiState.currentTab) {
                VaultTab.CREATE -> {
                    CreatePanel(
                        folderName = uiState.createFolderName,
                        selectedSizeMB = uiState.createSizeMB,
                        passwordInput = uiState.createPasswordInput,
                        message = uiState.createMessage,
                        isError = uiState.isCreateError,
                        isProcessing = uiState.isProcessing,
                        progressPercent = uiState.progressPercent,
                        progressLabel = uiState.progressLabel,
                        onPickFolder = { createFolderPicker.launch(null) },
                        onSizeSelected = { viewModel.setCreateSize(it) },
                        onPasswordChanged = { viewModel.setCreatePassword(it) },
                        onGenerate = { viewModel.generateDatFile() }
                    )
                }
                VaultTab.ACCESS -> {
                    AccessPanel(
                        state = uiState,
                        onPickFolder = { accessFolderPicker.launch(null) },
                        onPickDatDirect = { datFilePicker.launch(arrayOf("application/octet-stream", "*/*")) },
                        onDatSelected = { viewModel.onDatFileDropdownSelected(it) },
                        onPasswordChanged = { viewModel.setAccessPassword(it) },
                        onUnlock = { viewModel.unlockVault() },
                        onLock = { viewModel.lockAndClean() },
                        onAddImages = {
                            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        onSaveVault = { viewModel.saveVault() },
                        onExportZip = { viewModel.exportToZip() },
                        onExportGallery = { viewModel.exportToGallery() },
                        onToggleSlideshow = { viewModel.toggleSlideshow() },
                        onNewPasswordChanged = { viewModel.setNewPassword(it) },
                        onResetPassword = { viewModel.resetPassword() },
                        onResizeSizeChanged = { viewModel.setResizeSizeMB(it) },
                        onResizeVault = { viewModel.resizeVault() },
                        onSyncExVault = { viewModel.syncFromExVault() },
                        onImageClick = { index -> viewModel.openModal(index) },
                        onDeleteImage = { index -> viewModel.deleteImage(index) },
                        onMoveLeft = { index -> if (index > 0) viewModel.moveImage(index, index - 1) },
                        onMoveRight = { index -> if (index < uiState.images.size - 1) viewModel.moveImage(index, index + 1) }
                    )
                }
            }
        }
    }

    // Modal Viewer
    uiState.activeModalIndex?.let { index ->
        VaultModalViewer(
            images = uiState.images,
            currentIndex = index,
            isSlideshowPlaying = uiState.isSlideshowPlaying,
            isModalPaused = uiState.isModalPaused,
            slideshowProgress = uiState.slideshowProgress,
            onClose = { viewModel.closeModal() },
            onNext = { viewModel.nextSlide() },
            onPrevious = { viewModel.prevSlide() },
            onToggleSlideshow = { viewModel.toggleSlideshow() },
            onTogglePause = { viewModel.toggleModalPause() }
        )
    }
}

@Composable
private fun VaultHeaderSection(
    currentTab: VaultTab,
    onTabSelected: (VaultTab) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "⚡ DAT Vault Pro",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Text(
                    text = "Encrypted · Slideshow · Swipe · Auto-sync · Drag to reorder · Resize",
                    fontSize = 11.sp,
                    color = VaultMuted,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Mode Switcher Tabs
        Surface(
            color = Color(0x33000000),
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, VaultBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(3.dp)
            ) {
                // Create Tab
                val isCreate = currentTab == VaultTab.CREATE
                val createBackground = if (isCreate) {
                    Modifier.background(Brush.horizontalGradient(listOf(VaultAccent, VaultCyan)))
                } else {
                    Modifier.background(Color.Transparent)
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .then(createBackground)
                        .clickable { onTabSelected(VaultTab.CREATE) }
                        .testTag("vault_tab_create")
                ) {
                    Text(
                        text = "✨ Create",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isCreate) Color.White else VaultMuted
                    )
                }

                // Access Tab
                val isAccess = currentTab == VaultTab.ACCESS
                val accessBackground = if (isAccess) {
                    Modifier.background(Brush.horizontalGradient(listOf(VaultAccent, VaultCyan)))
                } else {
                    Modifier.background(Color.Transparent)
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .then(accessBackground)
                        .clickable { onTabSelected(VaultTab.ACCESS) }
                        .testTag("vault_tab_access")
                ) {
                    Text(
                        text = "📂 Access",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isAccess) Color.White else VaultMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun CreatePanel(
    folderName: String?,
    selectedSizeMB: Long,
    passwordInput: String,
    message: String?,
    isError: Boolean,
    isProcessing: Boolean,
    progressPercent: Float,
    progressLabel: String,
    onPickFolder: () -> Unit,
    onSizeSelected: (Long) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onGenerate: () -> Unit
) {
    var sizeExpanded by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }

    val sizes = listOf(
        1L to "1 MB",
        5L to "5 MB",
        10L to "10 MB",
        25L to "25 MB",
        50L to "50 MB",
        100L to "100 MB",
        200L to "200 MB",
        500L to "500 MB",
        1024L to "1 GB"
    )

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Folder Selector
        item {
            Surface(
                color = VaultCardBg,
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, VaultBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = folderName?.let { "📁 $it" } ?: "📁 No folder chosen",
                        color = if (folderName != null) VaultAccent2 else VaultMuted,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onPickFolder,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0x336C5CE7),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.testTag("create_pick_folder_button")
                    ) {
                        Text("📁 Choose", fontSize = 12.sp)
                    }
                }
            }
        }

        // File Size Selector
        item {
            Column {
                Text(
                    text = "FILE SIZE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    color = VaultMuted
                )
                Spacer(modifier = Modifier.height(6.dp))

                Box(modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, VaultBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { sizeExpanded = true }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val label = sizes.find { it.first == selectedSizeMB }?.second ?: "$selectedSizeMB MB"
                            Text(
                                text = "$label .dat container",
                                color = Color.White,
                                fontSize = 14.sp
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = VaultMuted
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = sizeExpanded,
                        onDismissRequest = { sizeExpanded = false },
                        modifier = Modifier.background(VaultCardBg)
                    ) {
                        sizes.forEach { (mb, lbl) ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = lbl,
                                        color = if (mb == selectedSizeMB) VaultCyan else Color.White,
                                        fontWeight = if (mb == selectedSizeMB) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                onClick = {
                                    sizeExpanded = false
                                    onSizeSelected(mb)
                                }
                            )
                        }
                    }
                }
            }
        }

        // Password PIN Input
        item {
            Column {
                Text(
                    text = "PASSWORD (4-8 DIGITS)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    color = VaultMuted
                )
                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = onPasswordChanged,
                    placeholder = { Text("Enter 4-8 digits", color = VaultMuted, fontSize = 14.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null,
                                tint = VaultMuted
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.Black.copy(alpha = 0.5f),
                        unfocusedContainerColor = Color.Black.copy(alpha = 0.5f),
                        focusedBorderColor = VaultAccent,
                        unfocusedBorderColor = VaultBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_pin_input")
                )
            }
        }

        // Progress Bar
        if (isProcessing) {
            item {
                Column {
                    LinearProgressIndicator(
                        progress = { progressPercent },
                        color = VaultAccent,
                        trackColor = Color.White.copy(alpha = 0.1f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(CircleShape)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = progressLabel,
                        fontSize = 11.sp,
                        color = VaultMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Generate Button
        item {
            Button(
                onClick = onGenerate,
                enabled = !isProcessing,
                colors = ButtonDefaults.buttonColors(
                    containerColor = VaultAccent,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("generate_dat_button")
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = "🔒 Generate DAT File",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Feedback Message Card
        if (!message.isNullOrEmpty()) {
            item {
                Surface(
                    color = Color.Black.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isError) VaultDanger.copy(alpha = 0.5f) else VaultSuccess.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = message,
                        color = if (isError) VaultDanger else VaultSuccess,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccessPanel(
    state: VaultUiState,
    onPickFolder: () -> Unit,
    onPickDatDirect: () -> Unit,
    onDatSelected: (Uri) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onUnlock: () -> Unit,
    onLock: () -> Unit,
    onAddImages: () -> Unit,
    onSaveVault: () -> Unit,
    onExportZip: () -> Unit,
    onExportGallery: () -> Unit,
    onToggleSlideshow: () -> Unit,
    onNewPasswordChanged: (String) -> Unit,
    onResetPassword: () -> Unit,
    onResizeSizeChanged: (Long) -> Unit,
    onResizeVault: () -> Unit,
    onSyncExVault: () -> Unit,
    onImageClick: (Int) -> Unit,
    onDeleteImage: (Int) -> Unit,
    onMoveLeft: (Int) -> Unit,
    onMoveRight: (Int) -> Unit
) {
    var showPassword by remember { mutableStateOf(false) }
    var datDropdownExpanded by remember { mutableStateOf(false) }
    var resizeDropdownExpanded by remember { mutableStateOf(false) }

    val resizeSizes = listOf(
        5L to "5 MB",
        10L to "10 MB",
        25L to "25 MB",
        50L to "50 MB",
        100L to "100 MB",
        200L to "200 MB",
        500L to "500 MB",
        1024L to "1 GB"
    )

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Folder for EX Vault or Direct File Picker Row
        item {
            Surface(
                color = VaultCardBg,
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, VaultBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = state.accessFolderName?.let { "📁 $it (EX Vault)" } ?: "📁 Folder for EX Vault",
                        color = if (state.accessFolderName != null) VaultCyan else VaultMuted,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Row {
                        Button(
                            onClick = onPickFolder,
                            enabled = !state.isUnlocked,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0x336C5CE7),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.testTag("access_pick_folder_button")
                        ) {
                            Text("📁 Choose", fontSize = 11.sp)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        OutlinedButton(
                            onClick = onPickDatDirect,
                            enabled = !state.isUnlocked,
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, VaultBorder),
                            modifier = Modifier.testTag("access_pick_dat_button")
                        ) {
                            Text("Pick .dat", fontSize = 11.sp, color = VaultCyan)
                        }
                    }
                }
            }
        }

        // Select .dat file dropdown
        item {
            Column {
                Text(
                    text = "SELECT .DAT FILE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    color = VaultMuted
                )
                Spacer(modifier = Modifier.height(6.dp))

                Box(modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, VaultBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = !state.isUnlocked && state.availableDatFiles.isNotEmpty()) {
                                datDropdownExpanded = true
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val text = state.selectedDatName ?: if (state.availableDatFiles.isEmpty()) "— First pick a folder or file —" else "— Select .dat file —"
                            Text(
                                text = text,
                                color = if (state.selectedDatName != null) Color.White else VaultMuted,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = VaultMuted
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = datDropdownExpanded,
                        onDismissRequest = { datDropdownExpanded = false },
                        modifier = Modifier.background(VaultCardBg)
                    ) {
                        state.availableDatFiles.forEach { item ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "${item.fileName} (${item.sizeBytes / (1024 * 1024)} MB)",
                                        color = if (item.uri == state.selectedDatUri) VaultCyan else Color.White
                                    )
                                },
                                onClick = {
                                    datDropdownExpanded = false
                                    onDatSelected(item.uri)
                                }
                            )
                        }
                    }
                }
            }
        }

        // PIN Input (Enabled when locked)
        if (!state.isUnlocked) {
            item {
                Column {
                    Text(
                        text = "PASSWORD (4-8 DIGITS)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        color = VaultMuted
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = state.accessPasswordInput,
                        onValueChange = onPasswordChanged,
                        placeholder = { Text("Enter PIN", color = VaultMuted, fontSize = 14.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null,
                                    tint = VaultMuted
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.Black.copy(alpha = 0.5f),
                            unfocusedContainerColor = Color.Black.copy(alpha = 0.5f),
                            focusedBorderColor = VaultAccent,
                            unfocusedBorderColor = VaultBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("access_pin_input")
                    )
                }
            }

            // Unlock & Extract Button
            item {
                Button(
                    onClick = onUnlock,
                    enabled = !state.isProcessing && state.selectedDatUri != null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VaultAccent,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("unlock_vault_button")
                ) {
                    if (state.isProcessing) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("🔓 Unlock & Extract", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Status Row (Locked / Unlocked / Slideshow & Lock & Clean button)
        item {
            Surface(
                color = Color.Black.copy(alpha = 0.35f),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, VaultBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "Status: ", color = VaultMuted, fontSize = 13.sp)
                        val (statusBadgeBg, statusBadgeColor, statusText) = when (state.vaultStatus) {
                            VaultStatus.LOCKED -> Triple(Color(0xFF2D3436), VaultMuted, "🔒 Locked")
                            VaultStatus.UNLOCKED -> Triple(VaultSuccess, Color.Black, "🔓 Unlocked")
                            VaultStatus.SLIDESHOW -> Triple(VaultWarning, Color.Black, "🎬 Slideshow")
                        }
                        Surface(
                            color = statusBadgeBg,
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(
                                text = statusText,
                                color = statusBadgeColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Button(
                        onClick = onLock,
                        enabled = state.isUnlocked,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = VaultDanger,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.testTag("lock_vault_button")
                    ) {
                        Text("🔒 Lock & Clean", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // Progress Bar
        if (state.isProcessing) {
            item {
                Column {
                    LinearProgressIndicator(
                        progress = { state.progressPercent },
                        color = VaultCyan,
                        trackColor = Color.White.copy(alpha = 0.1f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(CircleShape)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = state.progressLabel,
                        fontSize = 11.sp,
                        color = VaultMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Password Reset Row (Visible when unlocked)
        if (state.isUnlocked && state.isPasswordResetVisible) {
            item {
                Surface(
                    color = Color.Black.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, VaultBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = state.newPasswordInput,
                            onValueChange = onNewPasswordChanged,
                            placeholder = { Text("New PIN (4-8 digits)", color = VaultMuted, fontSize = 12.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            visualTransformation = PasswordVisualTransformation(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.Black.copy(alpha = 0.4f),
                                unfocusedContainerColor = Color.Black.copy(alpha = 0.4f),
                                focusedBorderColor = VaultAccent,
                                unfocusedBorderColor = VaultBorder,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("reset_pin_input")
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = onResetPassword,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = VaultAccent,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(46.dp)
                        ) {
                            Text("🔄 Reset", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Resize Container Row (Visible when unlocked)
        if (state.isUnlocked && state.isResizeVisible) {
            item {
                Surface(
                    color = Color.Black.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, VaultBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Resize:",
                            color = VaultMuted,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )

                        Box(modifier = Modifier.weight(1f)) {
                            Surface(
                                color = Color.Black.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, VaultBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { resizeDropdownExpanded = true }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    val lbl = resizeSizes.find { it.first == state.resizeSizeMB }?.second ?: "${state.resizeSizeMB} MB"
                                    Text(text = lbl, color = Color.White, fontSize = 12.sp)
                                    Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null, tint = VaultMuted)
                                }
                            }

                            DropdownMenu(
                                expanded = resizeDropdownExpanded,
                                onDismissRequest = { resizeDropdownExpanded = false },
                                modifier = Modifier.background(VaultCardBg)
                            ) {
                                resizeSizes.forEach { (mb, lbl) ->
                                    DropdownMenuItem(
                                        text = { Text(lbl, color = Color.White) },
                                        onClick = {
                                            resizeDropdownExpanded = false
                                            onResizeSizeChanged(mb)
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = onResizeVault,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = VaultSuccess,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(44.dp)
                        ) {
                            Text("📏 Resize", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Action Rows (Add / Save / Sync EX / Gallery / ZIP / Slideshow)
        if (state.isUnlocked) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Row 1: Core Vault Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Add Button
                        Button(
                            onClick = onAddImages,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0x33FFFFFF),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
                            modifier = Modifier.weight(1f).height(40.dp)
                        ) {
                            Text("🖼️ Add", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }

                        // Save Button
                        Button(
                            onClick = onSaveVault,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = VaultSuccess,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
                            modifier = Modifier.weight(1f).height(40.dp)
                        ) {
                            Text("💾 Save", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }

                        // Sync EX Button
                        Button(
                            onClick = onSyncExVault,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF6C5CE7),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
                            modifier = Modifier.weight(1.1f).height(40.dp)
                        ) {
                            Text("🔄 Sync EX", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }

                        // Slideshow Button
                        Button(
                            onClick = onToggleSlideshow,
                            enabled = state.images.size >= 2,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (state.isSlideshowPlaying) VaultWarning else Color(0xFFFD79A8),
                                contentColor = if (state.isSlideshowPlaying) Color.Black else Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
                            modifier = Modifier.weight(1.1f).height(40.dp)
                        ) {
                            Text(
                                text = if (state.isSlideshowPlaying) "⏸ Pause" else "▶ Play",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Row 2: Export Options
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Export to Gallery Button
                        Button(
                            onClick = onExportGallery,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF0984E3),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp),
                            modifier = Modifier.weight(1f).height(38.dp)
                        ) {
                            Text("📤 Export to Gallery", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }

                        // ZIP Button
                        Button(
                            onClick = onExportZip,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0x336C5CE7),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp),
                            modifier = Modifier.weight(1f).height(38.dp)
                        ) {
                            Text("📥 Export as ZIP", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // Feedback / Access Message Box
        if (!state.accessMessage.isNullOrEmpty()) {
            item {
                Surface(
                    color = Color.Black.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (state.isAccessError) VaultDanger.copy(alpha = 0.5f) else VaultAccent.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = state.accessMessage,
                        color = if (state.isAccessError) VaultDanger else Color.White,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        // EX Vault Live Location Card
        if (state.isUnlocked) {
            item {
                Surface(
                    color = VaultCardBg,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, VaultCyan.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("📁 Live Extracted Folder: ", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                            Text(
                                text = state.exVaultFolderName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = VaultCyan,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Your images are extracted to '${state.exVaultFolderName}'! You can open your phone's File Manager to view, add, or delete images directly.\n\n⚡ Auto-Detect & Auto-Update: Whenever you tap '🔒 Lock & Clean' (or switch back to the app), changes in 'EX Vault' are automatically detected, encrypted into your container, and plaintext files are wiped cleanly.",
                            fontSize = 11.sp,
                            color = VaultMuted,
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            item {
                Text(
                    text = "↕ Tap ◀ ▶ to reorder images · Click to view · × to delete",
                    fontSize = 11.sp,
                    color = VaultMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                )
            }

            // Image Gallery Thumbnails
            if (state.images.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "📭 No images in vault.\nTap '🖼️ Add' to add photos.",
                            color = VaultMuted,
                            textAlign = TextAlign.Center,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                // Render gallery grid inside LazyColumn via nested chunked rows
                val chunkedImages = state.images.chunked(3)
                items(chunkedImages.size) { rowIndex ->
                    val rowItems = chunkedImages[rowIndex]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (colIndex in 0 until 3) {
                            if (colIndex < rowItems.size) {
                                val index = rowIndex * 3 + colIndex
                                val item = rowItems[colIndex]
                                Box(modifier = Modifier.weight(1f)) {
                                    VaultThumbnailCard(
                                        image = item,
                                        index = index,
                                        total = state.images.size,
                                        onClick = { onImageClick(index) },
                                        onDelete = { onDeleteImage(index) },
                                        onMoveLeft = { onMoveLeft(index) },
                                        onMoveRight = { onMoveRight(index) }
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        } else {
            // Locked Placeholder
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = VaultMuted,
                            modifier = Modifier.size(42.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "🔒 Unlock vault to view and manage images",
                            color = VaultMuted,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VaultThumbnailCard(
    image: VaultImage,
    index: Int,
    total: Int,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit
) {
    val context = LocalContext.current

    Surface(
        color = Color(0xFF1A1A2E),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, VaultBorder),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Low-RAM thumbnail: Coil with downscaled bounds
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(image.file)
                    .size(240, 240)
                    .crossfade(true)
                    .build(),
                contentDescription = image.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = VaultAccent, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    }
                },
                error = {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(imageVector = Icons.Default.PhotoLibrary, contentDescription = null, tint = VaultMuted)
                    }
                }
            )

            // Top Delete Button (×)
            Surface(
                color = VaultDanger,
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(22.dp)
                    .clickable(onClick = onDelete)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("×", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Bottom Name and Reorder Bar
            Surface(
                color = Color.Black.copy(alpha = 0.75f),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (index > 0) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Move Left",
                            tint = Color.White,
                            modifier = Modifier
                                .size(14.dp)
                                .clickable(onClick = onMoveLeft)
                        )
                    } else {
                        Spacer(modifier = Modifier.size(14.dp))
                    }

                    Text(
                        text = image.name,
                        fontSize = 9.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(horizontal = 2.dp),
                        textAlign = TextAlign.Center
                    )

                    if (index < total - 1) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Move Right",
                            tint = Color.White,
                            modifier = Modifier
                                .size(14.dp)
                                .clickable(onClick = onMoveRight)
                        )
                    } else {
                        Spacer(modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}
