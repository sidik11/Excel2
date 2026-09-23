package com.example.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.ControlsSection
import com.example.ui.components.HeaderBar
import com.example.ui.components.ImageGallery
import com.example.ui.components.ModalImageViewer
import com.example.ui.theme.DarkBorder

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import com.example.ui.sshow.SShowScreen
import com.example.ui.sshow.SShowViewModel
import com.example.ui.settings.SettingsScreen
import com.example.ui.settings.UserManualDialog
import com.example.ui.settings.DualVaultConnectDialog
import com.example.util.FirebaseBridgeManager
import com.example.ui.profile.ProfileDialog
import com.example.util.AppSecurityManager
import com.example.util.ProfileManager

enum class MainAppTab {
    EXCEL_CATALOG,
    IMAGE_VAULT,
    SSHOW,
    DUAL_VAULT,
    SETTINGS
}

@Composable
fun MainScreen(
    viewModel: MainViewModel = viewModel(),
    vaultViewModel: VaultViewModel = viewModel(),
    sshowViewModel: SShowViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val vaultUiState by vaultViewModel.uiState.collectAsStateWithLifecycle()
    val securityConfig by AppSecurityManager.securityConfig.collectAsStateWithLifecycle()
    val dualSession by FirebaseBridgeManager.currentSession.collectAsStateWithLifecycle()
    val userProfile by ProfileManager.userProfile.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var currentTab by remember { mutableStateOf(MainAppTab.EXCEL_CATALOG) }
    var showManualDialog by remember { mutableStateOf(false) }
    var showProfileDialog by remember { mutableStateOf(false) }

    // Enforce mandatory profile completion and Google connection:
    // For new user: prompt to fill profile form and connect Google account on open.
    // For existing user: prompt to connect Google account when user logins/enters app.
    LaunchedEffect(userProfile) {
        val p = userProfile
        if (p == null || p.fullName.isBlank() || !p.isGoogleAccountConnected()) {
            showProfileDialog = true
        }
    }

    // Push notification permission launcher (Android 13+)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.triggerTestNotification()
        }
    }

    // Excel file picker (SAF OpenDocument)
    val excelPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.onExcelSelected(uri)
        }
    }

    // Folder picker (SAF OpenDocumentTree)
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            viewModel.onFolderSelected(treeUri)
        }
    }

    // User message snackbar trigger
    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearUserMessage()
        }
    }

    LaunchedEffect(vaultUiState.snackbarMessage) {
        vaultUiState.snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            vaultViewModel.clearSnackbar()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Header Bar
            HeaderBar(
                excelName = uiState.excelName,
                folderName = uiState.folderName,
                isPermanentSaved = uiState.isPermanentSaved,
                onLoadSample = { viewModel.loadSampleData() },
                onTestNotification = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        viewModel.triggerTestNotification()
                    }
                },
                onClearCache = { viewModel.clearAllCache() },
                onOpenManual = { showManualDialog = true },
                onOpenProfile = { showProfileDialog = true }
            )

            // Top Section Switcher (Excel Catalog, DAT Vault, SShow, Settings)
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(4.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val isExcel = currentTab == MainAppTab.EXCEL_CATALOG
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .height(36.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isExcel) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { currentTab = MainAppTab.EXCEL_CATALOG }
                            .padding(horizontal = 12.dp)
                            .testTag("nav_excel_catalog_tab")
                    ) {
                        Text(
                            text = "📊 Excel",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isExcel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    val isVault = currentTab == MainAppTab.IMAGE_VAULT
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .height(36.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isVault) Brush.horizontalGradient(listOf(Color(0xFF6C5CE7), Color(0xFF00CEC9)))
                                else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                            )
                            .clickable { currentTab = MainAppTab.IMAGE_VAULT }
                            .padding(horizontal = 12.dp)
                            .testTag("nav_image_vault_tab")
                    ) {
                        Text(
                            text = "🔐 DAT Vault",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isVault) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    val isSShow = currentTab == MainAppTab.SSHOW
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .height(36.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSShow) Brush.horizontalGradient(listOf(Color(0xFFFF7675), Color(0xFFFF9900)))
                                else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                            )
                            .clickable { currentTab = MainAppTab.SSHOW }
                            .padding(horizontal = 12.dp)
                            .testTag("nav_sshow_tab")
                    ) {
                        Text(
                            text = "🛡️ SShow",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isSShow) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (dualSession.isConnected) {
                        val isDualVault = currentTab == MainAppTab.DUAL_VAULT
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .height(36.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isDualVault) Brush.horizontalGradient(listOf(Color(0xFF00B894), Color(0xFF0984E3)))
                                    else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                                )
                                .clickable { currentTab = MainAppTab.DUAL_VAULT }
                                .padding(horizontal = 12.dp)
                                .testTag("nav_dual_vault_tab")
                        ) {
                            Text(
                                text = "🔗 Dual Vault",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isDualVault) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    val isSettings = currentTab == MainAppTab.SETTINGS
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .height(36.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSettings) MaterialTheme.colorScheme.secondary else Color.Transparent)
                            .clickable { currentTab = MainAppTab.SETTINGS }
                            .padding(horizontal = 12.dp)
                            .testTag("nav_settings_tab")
                    ) {
                        Text(
                            text = "⚙️ Settings",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isSettings) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (securityConfig.isPinEnabled) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .height(36.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f))
                                .clickable { AppSecurityManager.lockApp() }
                                .padding(horizontal = 10.dp)
                                .testTag("nav_quick_lock_app")
                        ) {
                            Text(
                                text = "🔒 Lock",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                thickness = 1.dp,
                color = DarkBorder
            )

            // Content Based on Active Tab
            when (currentTab) {
                MainAppTab.EXCEL_CATALOG -> {
                    Column(modifier = Modifier.weight(1f)) {
                        // Controls Section (Buttons, Dropdowns, Status)
                        ControlsSection(
                            hasExcel = uiState.excelName != null || uiState.totalRowsInCache > 0,
                            hasFolder = uiState.folderName != null || uiState.totalImagesInCache > 0,
                            selectedName = uiState.selectedName,
                            selectedColour = uiState.selectedColour,
                            availableNames = uiState.availableNames,
                            availableColours = uiState.availableColours,
                            statusText = uiState.statusText,
                            isLoading = uiState.isLoading,
                            loadingMessage = uiState.loadingMessage,
                            onSelectExcelClick = {
                                excelPickerLauncher.launch(
                                    arrayOf(
                                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                        "application/vnd.ms-excel",
                                        "text/csv",
                                        "text/comma-separated-values",
                                        "*/*"
                                    )
                                )
                            },
                            onSelectFolderClick = {
                                folderPickerLauncher.launch(null)
                            },
                            onNameSelect = { viewModel.onNameFilterChanged(it) },
                            onColourSelect = { viewModel.onColourFilterChanged(it) }
                        )

                        // Gallery Section
                        ImageGallery(
                            images = uiState.displayedImages,
                            hasExcel = uiState.excelName != null || uiState.totalRowsInCache > 0,
                            hasFolder = uiState.folderName != null || uiState.totalImagesInCache > 0,
                            onImageClick = { index -> viewModel.openModal(index) },
                            onLoadSampleClick = { viewModel.loadSampleData() },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                MainAppTab.IMAGE_VAULT -> {
                    VaultScreen(
                        viewModel = vaultViewModel,
                        modifier = Modifier.weight(1f)
                    )
                }

                MainAppTab.SSHOW -> {
                    SShowScreen(
                        viewModel = sshowViewModel,
                        modifier = Modifier.weight(1f)
                    )
                }

                MainAppTab.DUAL_VAULT -> {
                    // The paired vault is opened as a full dialog so the tab
                    // never exposes the pairing-code controls while connected.
                    DualVaultConnectDialog(
                        onDismiss = { currentTab = if (dualSession.isConnected) MainAppTab.DUAL_VAULT else MainAppTab.SSHOW }
                    )
                }

                MainAppTab.SETTINGS -> {
                    SettingsScreen(
                        onRestoreComplete = {
                            viewModel.reloadPersistedData()
                            vaultViewModel.reloadSession()
                            sshowViewModel.checkStoredImages()
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Fullscreen Modal Viewer for Excel Catalog
        uiState.activeModalIndex?.let { index ->
            ModalImageViewer(
                images = uiState.displayedImages,
                currentIndex = index,
                isSlideshowActive = uiState.isSlideshowPlaying,
                onClose = { viewModel.closeModal() },
                onNext = { viewModel.nextImage() },
                onPrevious = { viewModel.previousImage() },
                onToggleSlideshow = { viewModel.toggleSlideshow() }
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
    }
}
