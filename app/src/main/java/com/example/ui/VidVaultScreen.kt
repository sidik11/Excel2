package com.example.ui

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ui.components.ApsTransferModal
import com.example.ui.components.VidVaultPlayerModal
import com.example.ui.components.formatDurationMs
import com.example.util.ApsTransferEngine
import com.example.util.ApsTransferItem
import com.example.util.ApsVaultType
import com.example.util.SettingsManager
import com.example.util.VaultSortMode
import com.example.util.VaultVideo
import com.example.util.VidVaultEngine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun VidVaultScreen(
    viewModel: VidVaultViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by SettingsManager.settings.collectAsState()
    val context = LocalContext.current

    var passwordInput by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var vaultDropdownExpanded by remember { mutableStateOf(false) }
    var sortDropdownExpanded by remember { mutableStateOf(false) }
    var importMenuExpanded by remember { mutableStateOf(false) }
    var showApsModal by remember { mutableStateOf(false) }
    var apsItemsToSend by remember { mutableStateOf<List<ApsTransferItem>>(emptyList()) }
    val apsState by ApsTransferEngine.state.collectAsStateWithLifecycle()

    // All-format Video File Picker (SAF OpenMultipleDocuments: mp4, mkv, mov, avi, webm, 3gp, flv, ts, etc.)
    val allFormatVideoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importVideos(uris)
        }
    }

    // Android Zero-Permission Photo/Video Picker (VideoOnly)
    val galleryVideoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(50)
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importVideos(uris)
        }
    }

    // External .vidvault container picker
    val externalVaultPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importExternalVaultUri(uri)
        }
    }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top VID Vault Status & Actions Header
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        Brush.linearGradient(listOf(Color(0xFF00CEC9), Color(0xFF6C5CE7)))
                                    )
                            ) {
                                Icon(
                                    imageVector = if (uiState.isUnlocked) Icons.Default.LockOpen else Icons.Default.Movie,
                                    contentDescription = "VID Vault",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (uiState.isUnlocked) uiState.metadata?.name ?: "VID Vault" else "VID Vault",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFF00CEC9).copy(alpha = 0.18f)
                                    ) {
                                        Text(
                                            text = "30 GB MAX",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF00CEC9),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = if (uiState.isUnlocked) {
                                        "${uiState.metadata?.activeVideoCount ?: 0} videos • Zero-RAM Disk Streaming"
                                    } else {
                                        "Encrypted Video Vault • All Formats • Same Location as IMG Vault"
                                    },
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        if (uiState.isUnlocked) {
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                                val isVidApsReceiving = apsState.isReceiveEnabled && apsState.activeReceiveVaultType == ApsVaultType.VID_VAULT
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isVidApsReceiving) Color(0xFF00B894).copy(alpha = 0.22f) else Color(0xFF6C5CE7).copy(alpha = 0.2f),
                                    border = BorderStroke(1.dp, if (isVidApsReceiving) Color(0xFF00B894) else Color(0xFF00CEC9)),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            apsItemsToSend = emptyList()
                                            showApsModal = true
                                        }
                                        .testTag("vid_vault_aps_receive_btn")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Radar,
                                            contentDescription = "APS Receive",
                                            tint = if (isVidApsReceiving) Color(0xFF00B894) else Color(0xFF00CEC9),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (isVidApsReceiving) "APS ON" else "APS",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isVidApsReceiving) Color(0xFF00B894) else Color(0xFF00CEC9)
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { viewModel.setVaultInfoDialogOpen(true) },
                                    modifier = Modifier.testTag("vid_vault_info_btn")
                                ) {
                                    Icon(Icons.Default.Info, contentDescription = "VID Vault Info", tint = MaterialTheme.colorScheme.primary)
                                }
                                IconButton(
                                    onClick = { viewModel.compactVault() },
                                    modifier = Modifier.testTag("vid_vault_compact_btn")
                                ) {
                                    Icon(Icons.Default.CleaningServices, contentDescription = "Compact VID Vault", tint = Color(0xFF00CEC9))
                                }
                                IconButton(
                                    onClick = { viewModel.lockVault() },
                                    modifier = Modifier.testTag("vid_vault_lock_btn")
                                ) {
                                    Icon(Icons.Default.Lock, contentDescription = "Lock VID Vault", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }

                    // Progress Banner while streaming/importing/compacting
                    if (uiState.isLoading) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { uiState.progressFraction.coerceIn(0.05f, 1f) },
                            color = Color(0xFF00CEC9),
                            modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(4.dp))
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = uiState.loadingMessage.ifBlank { "Working on storage…" },
                            fontSize = 11.sp,
                            color = Color(0xFF00CEC9),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Error Banner
                    uiState.errorMessage?.let { err ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = err,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontSize = 11.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { viewModel.clearError() },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Dismiss error", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

            if (!uiState.isUnlocked) {
                // LOCKED STATE UI
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Shared Storage Location Panel
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Storage, contentDescription = null, tint = Color(0xFF00CEC9), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "VID Vault Location (Same as IMG Vault)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF00CEC9)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = uiState.vaultLocationPath,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Ultra-Low RAM Engine: 64 KB disk-to-disk streaming • Up to 30 GB per container • MP4, MKV, MOV, AVI, WEBM, 3GP, TS & more.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Vault Container Selector Card
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Select Video Vault (.vidvault)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                IconButton(
                                    onClick = { viewModel.scanAvailableVaults() },
                                    modifier = Modifier.testTag("vid_vault_refresh_btn")
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Refresh Video Vaults")
                                }
                            }

                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = { vaultDropdownExpanded = true },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth().testTag("vid_vault_dropdown_btn")
                                ) {
                                    Icon(Icons.Default.VideoFile, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = uiState.selectedVaultName.ifBlank { "No .vidvault found — Create one below" },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }

                                DropdownMenu(
                                    expanded = vaultDropdownExpanded,
                                    onDismissRequest = { vaultDropdownExpanded = false }
                                ) {
                                    if (uiState.availableVaults.isEmpty()) {
                                        DropdownMenuItem(
                                            text = { Text("No .vidvault containers yet") },
                                            onClick = { vaultDropdownExpanded = false }
                                        )
                                    } else {
                                        uiState.availableVaults.forEach { container ->
                                            val sizeLabel = formatBytesCompact(container.sizeBytes)
                                            DropdownMenuItem(
                                                text = { Text("${container.fileName} ($sizeLabel)") },
                                                onClick = {
                                                    viewModel.selectVaultContainer(container)
                                                    vaultDropdownExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.setCreateDialogOpen(true) },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00CEC9), contentColor = Color.Black),
                                    modifier = Modifier.weight(1f).testTag("vid_vault_create_btn")
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Create Vault", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                OutlinedButton(
                                    onClick = { externalVaultPicker.launch(arrayOf("*/*")) },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f).testTag("vid_vault_browse_btn")
                                ) {
                                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Load File", fontSize = 12.sp)
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                            OutlinedTextField(
                                value = passwordInput,
                                onValueChange = { passwordInput = it },
                                label = { Text("Vault Password or Recovery Key") },
                                singleLine = true,
                                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                        Icon(
                                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = "Toggle password visibility"
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().testTag("vid_vault_password_input")
                            )

                            Button(
                                onClick = {
                                    viewModel.unlockVault(passwordInput)
                                    passwordInput = ""
                                },
                                enabled = !uiState.isLoading && uiState.selectedVaultFile != null && passwordInput.isNotBlank(),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C5CE7)),
                                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("vid_vault_unlock_btn")
                            ) {
                                Icon(Icons.Default.LockOpen, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Unlock VID Vault", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                // UNLOCKED STATE UI
                val meta = uiState.metadata
                val usedBytes = meta?.totalUsedBytes ?: 0L
                val maxBytes = meta?.maxCapacityBytes ?: VidVaultEngine.CAPACITY_30GB
                val usedFraction = if (maxBytes > 0L) (usedBytes.toFloat() / maxBytes.toFloat()).coerceIn(0f, 1f) else 0f

                // Capacity & Quick Lock Strip
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Used: ${formatBytesCompact(usedBytes)} / ${formatBytesCompact(maxBytes)}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Lock & Clear",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { viewModel.lockAndClearSession() }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                    .testTag("vid_vault_lock_clear_btn")
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { usedFraction },
                            color = Color(0xFF00CEC9),
                            trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                        )
                    }
                }

                // Folders Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = uiState.selectedFolderId == null,
                        onClick = { viewModel.selectFolder(null) },
                        label = { Text("All (${meta?.activeVideoCount ?: 0})", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF00CEC9),
                            selectedLabelColor = Color.Black
                        ),
                        modifier = Modifier.testTag("vid_folder_all")
                    )
                    uiState.folders.forEach { folder ->
                        val count = meta?.activeRecords?.count { it.folderId == folder.id } ?: 0
                        FilterChip(
                            selected = uiState.selectedFolderId == folder.id,
                            onClick = { viewModel.selectFolder(folder.id) },
                            leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(14.dp)) },
                            label = { Text("${folder.name} ($count)", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF6C5CE7),
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                    OutlinedButton(
                        onClick = { viewModel.setCreateFolderDialogOpen(true) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier.height(32.dp).testTag("vid_vault_new_folder_btn")
                    ) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = "New Folder", modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Folder", fontSize = 11.sp)
                    }
                }

                // Search, Sort, Grid/List, Add Videos Toolbar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = { viewModel.onSearchQueryChanged(it) },
                        placeholder = { Text("Search videos…", fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).testTag("vid_vault_search_input")
                    )

                    Box {
                        IconButton(
                            onClick = { sortDropdownExpanded = true },
                            modifier = Modifier.testTag("vid_vault_sort_btn")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort Videos")
                        }
                        DropdownMenu(
                            expanded = sortDropdownExpanded,
                            onDismissRequest = { sortDropdownExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Newest First") },
                                onClick = { viewModel.onSortModeChanged(VaultSortMode.DATE_DESC); sortDropdownExpanded = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Name (A–Z)") },
                                onClick = { viewModel.onSortModeChanged(VaultSortMode.NAME_ASC); sortDropdownExpanded = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Largest First") },
                                onClick = { viewModel.onSortModeChanged(VaultSortMode.SIZE_DESC); sortDropdownExpanded = false }
                            )
                        }
                    }

                    IconButton(
                        onClick = { viewModel.toggleViewMode() },
                        modifier = Modifier.testTag("vid_vault_view_toggle_btn")
                    ) {
                        Icon(
                            imageVector = if (uiState.isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                            contentDescription = "Toggle Grid or List"
                        )
                    }

                    Box {
                        Button(
                            onClick = { importMenuExpanded = true },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00CEC9), contentColor = Color.Black),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier.testTag("vid_vault_add_videos_btn")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        DropdownMenu(
                            expanded = importMenuExpanded,
                            onDismissRequest = { importMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Default.VideoLibrary, contentDescription = null) },
                                text = { Text("Pick from Gallery (Videos)") },
                                onClick = {
                                    importMenuExpanded = false
                                    galleryVideoPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                    )
                                },
                                modifier = Modifier.testTag("vid_import_gallery")
                            )
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
                                text = { Text("All Video Formats (MP4, MKV, MOV, AVI…)") },
                                onClick = {
                                    importMenuExpanded = false
                                    allFormatVideoPicker.launch(arrayOf("video/*"))
                                },
                                modifier = Modifier.testTag("vid_import_all_formats")
                            )
                        }
                    }
                }

                // Multi-Select Action Bar (NO SSI, NO Slideshow — Includes Folder Move/Copy, Export to Phone, Share, Delete)
                if (uiState.isMultiSelectActive) {
                    Surface(
                        color = Color(0xFF6C5CE7).copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, Color(0xFF6C5CE7).copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${uiState.selectedVideoIds.size} video(s) selected",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    TextButton(onClick = { viewModel.selectAllDisplayed() }) {
                                        Icon(Icons.Default.SelectAll, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("All", fontSize = 11.sp)
                                    }
                                    TextButton(onClick = { viewModel.clearSelection() }) {
                                        Text("Cancel", fontSize = 11.sp)
                                    }
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.setFolderActionDialogOpen(true, isCopy = false) },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.testTag("vid_multi_move_btn")
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Move", fontSize = 11.sp)
                                }
                                OutlinedButton(
                                    onClick = { viewModel.setFolderActionDialogOpen(true, isCopy = true) },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.testTag("vid_multi_copy_btn")
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Copy", fontSize = 11.sp)
                                }
                                OutlinedButton(
                                    onClick = { viewModel.setMobileStorageDialogOpen(true) },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.testTag("vid_multi_export_btn")
                                ) {
                                    Icon(Icons.Default.PhoneAndroid, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Phone", fontSize = 11.sp)
                                }
                                Button(
                                    onClick = {
                                        apsItemsToSend = viewModel.buildApsItemsForSelected()
                                        if (apsItemsToSend.isNotEmpty()) {
                                            showApsModal = true
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C5CE7), contentColor = Color.White),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.testTag("vid_vault_aps_send_btn")
                                ) {
                                    Icon(Icons.Default.Radar, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("APS", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = { viewModel.shareSelectedVideos(context) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00CEC9), contentColor = Color.Black),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.testTag("vid_multi_share_btn")
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Share", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = { viewModel.setDeleteConfirmDialogOpen(true) },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.testTag("vid_multi_delete_btn")
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Delete", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                // Video Gallery (Grid or List)
                if (uiState.displayedVideos.isEmpty()) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Movie,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "No videos in this vault folder yet",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tap '+ Add' to encrypt MP4, MKV, MOV, AVI, WEBM & more directly to storage (up to 30 GB, ultra-low RAM).",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else if (uiState.isGridView) {
                    val cols = settings.gridColumns.coerceIn(2, 4)
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(cols),
                        contentPadding = PaddingValues(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f).testTag("vid_vault_grid")
                    ) {
                        itemsIndexed(uiState.displayedVideos, key = { _, v -> v.id }) { index, video ->
                            VidVaultGridCard(
                                video = video,
                                isSelected = uiState.selectedVideoIds.contains(video.id),
                                onClick = {
                                    if (uiState.isMultiSelectActive) {
                                        viewModel.toggleVideoSelection(video.id)
                                    } else {
                                        viewModel.openPlayerModal(index)
                                    }
                                },
                                onLongClick = { viewModel.toggleVideoSelection(video.id) },
                                onShare = { viewModel.shareSingleVideo(context, video.id) },
                                onRename = { viewModel.openRenameDialog(video) }
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f).testTag("vid_vault_list")
                    ) {
                        itemsIndexed(uiState.displayedVideos, key = { _, v -> v.id }) { index, video ->
                            VidVaultListRow(
                                video = video,
                                isSelected = uiState.selectedVideoIds.contains(video.id),
                                onClick = {
                                    if (uiState.isMultiSelectActive) {
                                        viewModel.toggleVideoSelection(video.id)
                                    } else {
                                        viewModel.openPlayerModal(index)
                                    }
                                },
                                onLongClick = { viewModel.toggleVideoSelection(video.id) },
                                onShare = { viewModel.shareSingleVideo(context, video.id) },
                                onRename = { viewModel.openRenameDialog(video) }
                            )
                        }
                    }
                }
            }
        }

        // Fullscreen Video Player Modal
        uiState.activeModalIndex?.let { index ->
            VidVaultPlayerModal(
                videos = uiState.displayedVideos,
                currentIndex = index,
                createDirectDataSource = { videoId -> viewModel.createDirectMediaDataSource(videoId) },
                prepareFallbackStorageFile = { videoId -> viewModel.prepareTempStorageFile(videoId) },
                onShare = { video -> viewModel.shareSingleVideo(context, video.id) },
                onClose = { viewModel.closePlayerModal() },
                onNext = { viewModel.nextVideo() },
                onPrevious = { viewModel.previousVideo() }
            )
        }

        // APS Vault-to-Vault Modal (VID Vault Only — auto-pops on incoming VID Vault requests!)
        val incomingVidRequest = apsState.pendingIncomingRequest != null && apsState.activeReceiveVaultType == ApsVaultType.VID_VAULT
        if (uiState.isUnlocked && (showApsModal || incomingVidRequest)) {
            ApsTransferModal(
                vaultType = ApsVaultType.VID_VAULT,
                itemsToSend = apsItemsToSend,
                receiverTargetProvider = { viewModel.getApsReceiverTarget() },
                onTransferSuccess = {
                    viewModel.clearSelection()
                },
                onClose = {
                    showApsModal = false
                    apsItemsToSend = emptyList()
                }
            )
        }
    }

    // DIALOGS
    if (uiState.isCreateDialogOpen) {
        CreateVidVaultDialog(
            onDismiss = { viewModel.setCreateDialogOpen(false) },
            onCreate = { name, pass, cap -> viewModel.createVault(name, pass, cap) }
        )
    }

    if (uiState.showRecoveryKeyDialog && uiState.generatedRecoveryKey != null) {
        val clipboard = LocalClipboardManager.current
        AlertDialog(
            onDismissRequest = { viewModel.dismissRecoveryKeyDialog() },
            icon = { Icon(Icons.Default.Key, contentDescription = null, tint = Color(0xFF00CEC9)) },
            title = { Text("Save Emergency Recovery Key", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Store this recovery key safely. It can unlock your .vidvault container if you ever forget your password.",
                        fontSize = 12.sp
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = uiState.generatedRecoveryKey ?: "",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color(0xFF00CEC9),
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        clipboard.setText(AnnotatedString(uiState.generatedRecoveryKey ?: ""))
                        Toast.makeText(context, "Recovery key copied!", Toast.LENGTH_SHORT).show()
                        viewModel.dismissRecoveryKeyDialog()
                    }
                ) {
                    Text("Copy & Close")
                }
            }
        )
    }

    if (uiState.isVaultInfoDialogOpen && uiState.metadata != null) {
        val meta = uiState.metadata!!
        val dateFmt = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }
        val deletedCount = meta.records.count { it.isDeleted }
        AlertDialog(
            onDismissRequest = { viewModel.setVaultInfoDialogOpen(false) },
            title = { Text("VID Vault Info", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Container: ${uiState.selectedVaultName}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text("Location: ${uiState.vaultLocationPath}", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    Text("Active Videos: ${meta.activeVideoCount}", fontSize = 12.sp)
                    Text("Deleted (Reclaimable): $deletedCount", fontSize = 12.sp)
                    Text("Used Storage: ${formatBytesCompact(meta.totalUsedBytes)}", fontSize = 12.sp)
                    Text("Max Capacity: ${formatBytesCompact(meta.maxCapacityBytes)}", fontSize = 12.sp)
                    Text("Encryption: AES-256-GCM Index + AES-256-CTR Direct Disk Stream", fontSize = 11.sp, color = Color(0xFF00CEC9))
                    Text("Created: ${dateFmt.format(Date(meta.createdAt))}", fontSize = 11.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.setVaultInfoDialogOpen(false) }) { Text("Close") }
            }
        )
    }

    if (uiState.isCreateFolderDialogOpen) {
        var folderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { viewModel.setCreateFolderDialogOpen(false) },
            title = { Text("New VID Vault Folder") },
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
                Button(onClick = { viewModel.createFolder(folderName) }, enabled = folderName.isNotBlank()) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.setCreateFolderDialogOpen(false) }) { Text("Cancel") }
            }
        )
    }

    if (uiState.isRenameDialogOpen) {
        var newName by remember(uiState.renamingVideoId) { mutableStateOf(uiState.renamingVideoName) }
        AlertDialog(
            onDismissRequest = { viewModel.dismissRenameDialog() },
            title = { Text("Rename Video") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("New File Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = { viewModel.confirmRenameVideo(newName) }, enabled = newName.isNotBlank()) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissRenameDialog() }) { Text("Cancel") }
            }
        )
    }

    if (uiState.isFolderActionDialogOpen) {
        AlertDialog(
            onDismissRequest = { viewModel.setFolderActionDialogOpen(false) },
            title = { Text(if (uiState.isFolderActionCopy) "Copy Videos to Folder" else "Move Videos to Folder") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    uiState.folders.forEach { folder ->
                        OutlinedButton(
                            onClick = { viewModel.moveOrCopySelectedToFolder(folder.id, uiState.isFolderActionCopy) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(folder.name)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { viewModel.setFolderActionDialogOpen(false) }) { Text("Cancel") }
            }
        )
    }

    if (uiState.isMobileStorageDialogOpen) {
        AlertDialog(
            onDismissRequest = { viewModel.setMobileStorageDialogOpen(false) },
            title = { Text("Export to Mobile Storage") },
            text = {
                Text(
                    "Stream-decrypt ${uiState.selectedVideoIds.size} selected video(s) directly to Movies/VIDVault on your phone storage (uses 64 KB RAM).",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { viewModel.exportSelectedToMobileStorage(deleteFromVaultAfter = false) }) {
                        Text("Copy to Phone")
                    }
                    Button(onClick = { viewModel.exportSelectedToMobileStorage(deleteFromVaultAfter = true) }) {
                        Text("Move to Phone")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.setMobileStorageDialogOpen(false) }) { Text("Cancel") }
            }
        )
    }

    if (uiState.isDeleteConfirmDialogOpen) {
        AlertDialog(
            onDismissRequest = { viewModel.setDeleteConfirmDialogOpen(false) },
            title = { Text("Delete Selected Videos?") },
            text = {
                Text(
                    "Remove ${uiState.selectedVideoIds.size} video(s) from this VID Vault? Run 'Compact' afterward to physically reclaim storage.",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.deleteSelectedVideos() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.setDeleteConfirmDialogOpen(false) }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VidVaultGridCard(
    video: VaultVideo,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit
) {
    val context = LocalContext.current
    val extLabel = video.name.substringAfterLast('.', "VID").uppercase(Locale.getDefault()).take(4)

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (isSelected) 2.5.dp else 1.dp,
            color = if (isSelected) Color(0xFF00CEC9) else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .testTag("vid_grid_item_${video.id}")
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.15f)
                    .background(Color(0xFF101420)),
                contentAlignment = Alignment.Center
            ) {
                if (video.thumbnailFile != null && video.thumbnailFile.exists()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(video.thumbnailFile)
                            .size(256)
                            .bitmapConfig(Bitmap.Config.RGB_565)
                            .crossfade(false)
                            .build(),
                        contentDescription = video.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Default.Movie,
                        contentDescription = video.name,
                        tint = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(40.dp)
                    )
                }

                // Centered Play Pill
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .border(1.5.dp, Color(0xFF00CEC9), CircleShape)
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Play Video",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Format Badge (Top Left)
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Black.copy(alpha = 0.72f),
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp)
                ) {
                    Text(
                        text = extLabel,
                        color = Color(0xFF00CEC9),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }

                // Selection Check or Quick Share (Top Right)
                if (isSelected) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Selected",
                        tint = Color(0xFF00CEC9),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(22.dp)
                    )
                } else {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(5.dp)
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.65f))
                            .clickable(onClick = onShare)
                            .testTag("vid_item_share_${video.id}")
                    ) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = "Share Video",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                // Duration Pill (Bottom Right)
                if (video.durationMs > 0L) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color.Black.copy(alpha = 0.78f),
                        modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)
                    ) {
                        Text(
                            text = formatDurationMs(video.durationMs),
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = video.name,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = formatBytesCompact(video.sizeBytes),
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Rename",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable(onClick = onRename)
                        .padding(2.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VidVaultListRow(
    video: VaultVideo,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit
) {
    val context = LocalContext.current
    val extLabel = video.name.substringAfterLast('.', "VID").uppercase(Locale.getDefault()).take(5)

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) Color(0xFF00CEC9) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .testTag("vid_list_item_${video.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF101420)),
                contentAlignment = Alignment.Center
            ) {
                if (video.thumbnailFile != null && video.thumbnailFile.exists()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(video.thumbnailFile)
                            .size(160)
                            .bitmapConfig(Bitmap.Config.RGB_565)
                            .crossfade(false)
                            .build(),
                        contentDescription = video.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(Icons.Default.Movie, contentDescription = null, tint = Color.White.copy(alpha = 0.5f))
                }
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(2.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "$extLabel • ${formatBytesCompact(video.sizeBytes)}${if (video.durationMs > 0L) " • ${formatDurationMs(video.durationMs)}" else ""}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onShare) {
                Icon(Icons.Default.Share, contentDescription = "Share Video", tint = Color(0xFF00CEC9))
            }
            IconButton(onClick = onRename) {
                Icon(Icons.Default.Edit, contentDescription = "Rename Video")
            }
        }
    }
}

@Composable
private fun CreateVidVaultDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String, Long) -> Unit
) {
    var name by remember { mutableStateOf("MyVideoVault") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var selectedCapacity by remember { mutableLongStateOf(VidVaultEngine.CAPACITY_30GB) }
    var localError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Encrypted VID Vault", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Vault Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("create_vid_vault_name")
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; localError = null },
                    label = { Text("Password (min 4 chars)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().testTag("create_vid_vault_pass")
                )
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it; localError = null },
                    label = { Text("Confirm Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().testTag("create_vid_vault_confirm")
                )

                Text("Max Container Capacity (Dynamic Storage):", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                listOf(
                    VidVaultEngine.CAPACITY_10GB to "10 GB",
                    VidVaultEngine.CAPACITY_20GB to "20 GB",
                    VidVaultEngine.CAPACITY_30GB to "30 GB (Max)"
                ).forEach { (cap, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedCapacity = cap }
                            .padding(vertical = 2.dp)
                    ) {
                        RadioButton(selected = selectedCapacity == cap, onClick = { selectedCapacity = cap })
                        Text(label, fontSize = 13.sp)
                    }
                }

                localError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        name.isBlank() -> localError = "Enter a vault name."
                        password.length < 4 -> localError = "Password must be at least 4 characters."
                        password != confirmPassword -> localError = "Passwords do not match."
                        else -> onCreate(name, password, selectedCapacity)
                    }
                },
                modifier = Modifier.testTag("create_vid_vault_submit")
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun formatBytesCompact(bytes: Long): String {
    val kb = 1024.0
    val mb = kb * 1024.0
    val gb = mb * 1024.0
    return when {
        bytes >= gb -> String.format(Locale.getDefault(), "%.2f GB", bytes / gb)
        bytes >= mb -> String.format(Locale.getDefault(), "%.1f MB", bytes / mb)
        bytes >= kb -> String.format(Locale.getDefault(), "%.0f KB", bytes / kb)
        else -> "$bytes B"
    }
}
