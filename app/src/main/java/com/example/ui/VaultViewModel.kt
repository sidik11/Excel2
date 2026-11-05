package com.example.ui

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.util.AppStorageHelper
import com.example.util.ImgVaultEngine
import com.example.util.ImgVaultFolder
import com.example.util.ImgVaultMetadata
import com.example.util.ImgVaultRecord
import com.example.util.ImgVaultResult
import com.example.util.SsiManager
import com.example.util.VaultContainerInfo
import com.example.util.VaultImage
import com.example.util.VaultSessionManager
import com.example.util.VaultSortMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import javax.crypto.spec.SecretKeySpec

enum class VaultStatus { LOCKED, UNLOCKED }

data class VaultUiState(
    // Vault Selection & Location
    val selectedVaultFile: File? = null,
    val selectedVaultName: String? = null,
    val selectedLocationDir: File? = null,
    val availableVaultFiles: List<VaultContainerInfo> = emptyList(),

    // Authentication
    val passwordInput: String = "",
    val isPasswordVisible: Boolean = false,
    val isUnlocked: Boolean = false,
    val vaultStatus: VaultStatus = VaultStatus.LOCKED,

    // Container Metadata & Content
    val metadata: ImgVaultMetadata? = null,
    val folders: List<ImgVaultFolder> = emptyList(),
    val currentFolderId: String? = null, // null = all images
    val images: List<VaultImage> = emptyList(),

    // Gallery Controls
    val searchQuery: String = "",
    val sortMode: VaultSortMode = VaultSortMode.DATE_DESC,
    val isGridView: Boolean = true,
    val selectedImageIds: Set<String> = emptySet(),
    val isMultiSelectActive: Boolean = false,

    // Processing & Status
    val isProcessing: Boolean = false,
    val progressPercent: Float = 0f,
    val progressLabel: String = "",
    val statusMessage: String? = null,
    val isError: Boolean = false,
    val snackbarMessage: String? = null,

    // Dialogs
    val isCreateDialogOpen: Boolean = false,
    val isVaultInfoDialogOpen: Boolean = false,
    val isCreateFolderDialogOpen: Boolean = false,
    val isRenameDialogOpen: Boolean = false,
    val renameTargetImage: VaultImage? = null,
    val isMoveDialogOpen: Boolean = false,
    val isFolderActionDialogOpen: Boolean = false,
    val isMobileStorageDialogOpen: Boolean = false,
    val isDeleteConfirmDialogOpen: Boolean = false,
    val generatedRecoveryKey: String? = null,
    val showRecoveryKeyDialog: Boolean = false,

    // Fullscreen Modal Viewer & Slideshow
    val activeModalIndex: Int? = null,
    val isSlideshowPlaying: Boolean = false,
    val isModalPaused: Boolean = false,
    val slideshowProgress: Float = 0f
) {
    val displayedImages: List<VaultImage> get() {
        var list = images
        if (currentFolderId != null) {
            list = list.filter { it.folderId == currentFolderId }
        }
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase()
            list = list.filter { it.name.lowercase().contains(q) }
        }
        return when (sortMode) {
            VaultSortMode.NAME_ASC -> list.sortedBy { it.name.lowercase() }
            VaultSortMode.DATE_DESC -> list.sortedByDescending { it.createdAt }
            VaultSortMode.SIZE_DESC -> list.sortedByDescending { it.sizeBytes }
        }
    }
}

class VaultViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(VaultUiState())
    val uiState: StateFlow<VaultUiState> = _uiState.asStateFlow()

    private var activeMasterKey: SecretKeySpec? = null
    private var slideshowJob: Job? = null

    init {
        val context = getApplication<Application>()
        val defaultDir = AppStorageHelper.getImgVaultDir(context)
        _uiState.update { it.copy(selectedLocationDir = defaultDir) }
        scanAvailableVaults(defaultDir)
        restoreSessionIfActive()
    }

    fun reloadSession() {
        restoreSessionIfActive()
    }

    private fun restoreSessionIfActive() {
        val context = getApplication<Application>()
        val session = VaultSessionManager.getSession(context)
        val key = VaultSessionManager.activeMasterKey
        if (session.isUnlocked && session.vaultFilePath != null && key != null) {
            val file = File(session.vaultFilePath)
            if (file.exists()) {
                activeMasterKey = key
                _uiState.update {
                    it.copy(
                        selectedVaultFile = file,
                        selectedVaultName = file.name,
                        isUnlocked = true,
                        vaultStatus = VaultStatus.UNLOCKED
                    )
                }
                loadVaultData(file, key)
            }
        }
    }

    fun scanAvailableVaults(dir: File? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val targetDir = dir ?: _uiState.value.selectedLocationDir ?: AppStorageHelper.getImgVaultDir(context)
            val files = targetDir.listFiles { f ->
                f.isFile && f.name.endsWith(ImgVaultEngine.EXTENSION, ignoreCase = true)
            }?.sortedByDescending { it.lastModified() } ?: emptyList()

            val infoList = files.map { file ->
                VaultContainerInfo(
                    fileName = file.name,
                    file = file,
                    uri = Uri.fromFile(file),
                    sizeBytes = file.length()
                )
            }

            _uiState.update { state ->
                val selected = state.selectedVaultFile ?: infoList.firstOrNull()?.file
                state.copy(
                    selectedLocationDir = targetDir,
                    availableVaultFiles = infoList,
                    selectedVaultFile = selected,
                    selectedVaultName = selected?.name
                )
            }
        }
    }

    fun onChooseLocation(directory: File) {
        _uiState.update { it.copy(selectedLocationDir = directory) }
        scanAvailableVaults(directory)
    }

    fun onSelectVaultFile(file: File) {
        _uiState.update {
            it.copy(
                selectedVaultFile = file,
                selectedVaultName = file.name,
                passwordInput = "",
                statusMessage = null,
                isError = false
            )
        }
    }

    fun setPasswordInput(input: String) {
        _uiState.update { it.copy(passwordInput = input, statusMessage = null) }
    }

    fun togglePasswordVisibility() {
        _uiState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    fun openCreateDialog() {
        _uiState.update { it.copy(isCreateDialogOpen = true) }
    }

    fun closeCreateDialog() {
        _uiState.update { it.copy(isCreateDialogOpen = false) }
    }

    fun openVaultInfoDialog() {
        _uiState.update { it.copy(isVaultInfoDialogOpen = true) }
    }

    fun closeVaultInfoDialog() {
        _uiState.update { it.copy(isVaultInfoDialogOpen = false) }
    }

    fun openCreateFolderDialog() {
        _uiState.update { it.copy(isCreateFolderDialogOpen = true) }
    }

    fun closeCreateFolderDialog() {
        _uiState.update { it.copy(isCreateFolderDialogOpen = false) }
    }

    fun openRenameDialog(image: VaultImage) {
        _uiState.update { it.copy(isRenameDialogOpen = true, renameTargetImage = image) }
    }

    fun closeRenameDialog() {
        _uiState.update { it.copy(isRenameDialogOpen = false, renameTargetImage = null) }
    }

    fun openMoveDialog() {
        _uiState.update { it.copy(isMoveDialogOpen = true) }
    }

    fun closeMoveDialog() {
        _uiState.update { it.copy(isMoveDialogOpen = false) }
    }

    fun openFolderActionDialog() {
        if (_uiState.value.selectedImageIds.isNotEmpty()) {
            _uiState.update { it.copy(isFolderActionDialogOpen = true) }
        }
    }

    fun closeFolderActionDialog() {
        _uiState.update { it.copy(isFolderActionDialogOpen = false) }
    }

    fun openMobileStorageDialog() {
        if (_uiState.value.selectedImageIds.isNotEmpty()) {
            _uiState.update { it.copy(isMobileStorageDialogOpen = true) }
        }
    }

    fun closeMobileStorageDialog() {
        _uiState.update { it.copy(isMobileStorageDialogOpen = false) }
    }

    fun openDeleteConfirmDialog() {
        if (_uiState.value.selectedImageIds.isNotEmpty()) {
            _uiState.update { it.copy(isDeleteConfirmDialogOpen = true) }
        }
    }

    fun closeDeleteConfirmDialog() {
        _uiState.update { it.copy(isDeleteConfirmDialogOpen = false) }
    }

    fun closeRecoveryKeyDialog() {
        _uiState.update { it.copy(showRecoveryKeyDialog = false) }
    }

    fun createVault(
        vaultName: String,
        password: String,
        confirmPass: String,
        capacityBytes: Long
    ) {
        val context = getApplication<Application>()
        if (vaultName.isBlank()) {
            _uiState.update { it.copy(statusMessage = "Please enter a vault name.", isError = true) }
            return
        }
        if (password.length < 6) {
            _uiState.update { it.copy(statusMessage = "Password must be at least 6 characters.", isError = true) }
            return
        }
        if (password != confirmPass) {
            _uiState.update { it.copy(statusMessage = "Passwords do not match.", isError = true) }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    progressPercent = 0.2f,
                    progressLabel = "Generating cryptographic keys & container…",
                    statusMessage = null
                )
            }

            val targetDir = _uiState.value.selectedLocationDir ?: AppStorageHelper.getImgVaultDir(context)
            val cleanName = vaultName.trim().let {
                if (it.endsWith(ImgVaultEngine.EXTENSION, ignoreCase = true)) it else "$it${ImgVaultEngine.EXTENSION}"
            }
            val targetFile = File(targetDir, cleanName)

            if (targetFile.exists()) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        statusMessage = "A vault named '$cleanName' already exists in this folder.",
                        isError = true
                    )
                }
                return@launch
            }

            val recoveryKey = ImgVaultEngine.generateRecoveryKey()
            val result = ImgVaultEngine.createVault(
                context = context,
                targetFile = targetFile,
                vaultName = cleanName.removeSuffix(ImgVaultEngine.EXTENSION),
                password = password,
                recoveryKey = recoveryKey,
                maxCapacityBytes = capacityBytes
            )

            when (result) {
                is ImgVaultResult.Success -> {
                    scanAvailableVaults(targetDir)
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            isCreateDialogOpen = false,
                            selectedVaultFile = targetFile,
                            selectedVaultName = targetFile.name,
                            generatedRecoveryKey = recoveryKey,
                            showRecoveryKeyDialog = true,
                            statusMessage = "✓ Vault created successfully!",
                            isError = false
                        )
                    }
                    // Auto-unlock new vault
                    unlockVaultWithPassword(password, targetFile)
                }
                is ImgVaultResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            statusMessage = result.message,
                            isError = true
                        )
                    }
                }
            }
        }
    }

    fun unlockVault() {
        val file = _uiState.value.selectedVaultFile
        val pass = _uiState.value.passwordInput
        if (file == null) {
            _uiState.update { it.copy(statusMessage = "Please select a vault container file first.", isError = true) }
            return
        }
        if (pass.isBlank()) {
            _uiState.update { it.copy(statusMessage = "Please enter the vault password.", isError = true) }
            return
        }
        unlockVaultWithPassword(pass, file)
    }

    private fun unlockVaultWithPassword(password: String, file: File) {
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    progressPercent = 0.3f,
                    progressLabel = "Verifying password & decrypting index…",
                    statusMessage = null
                )
            }

            val result = ImgVaultEngine.openVault(context, file, password)
            when (result) {
                is ImgVaultResult.Success -> {
                    val (key, metadata) = result.data
                    activeMasterKey = key
                    VaultSessionManager.saveSession(
                        context = context,
                        vaultUri = Uri.fromFile(file),
                        vaultFilePath = file.absolutePath,
                        vaultFileName = file.name,
                        masterKey = key
                    )

                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            isUnlocked = true,
                            vaultStatus = VaultStatus.UNLOCKED,
                            metadata = metadata,
                            folders = metadata.folders,
                            statusMessage = "✓ Vault unlocked",
                            isError = false
                        )
                    }

                    loadVaultData(file, key)
                }
                is ImgVaultResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            statusMessage = result.message,
                            isError = true
                        )
                    }
                }
            }
        }
    }

    private fun loadVaultData(file: File, key: SecretKeySpec) {
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            val metadataResult = ImgVaultEngine.openVault(context, file, _uiState.value.passwordInput)
            val meta = if (metadataResult is ImgVaultResult.Success) metadataResult.data.second else _uiState.value.metadata
            val activeRecords = meta?.activeRecords ?: emptyList()

            val vaultImages = activeRecords.map { rec ->
                val thumbFile = ImgVaultEngine.getThumbnail(context, file, key, rec)
                VaultImage(
                    id = rec.id,
                    name = rec.name,
                    file = thumbFile ?: file, // thumbnail for gallery display
                    sizeBytes = rec.sizeBytes,
                    folderId = rec.folderId,
                    createdAt = rec.createdAt,
                    mimeType = rec.mimeType,
                    thumbnailFile = thumbFile
                )
            }

            _uiState.update {
                it.copy(
                    metadata = meta,
                    folders = meta?.folders ?: listOf(ImgVaultFolder(id = "default", name = "Main Gallery")),
                    images = vaultImages
                )
            }
        }
    }

    /**
     * Locks the vault immediately. It will remain locked until manually unlocked.
     */
    fun lockVault() {
        val context = getApplication<Application>()
        activeMasterKey = null
        VaultSessionManager.clearSession(context)
        slideshowJob?.cancel()
        _uiState.update {
            it.copy(
                isUnlocked = false,
                vaultStatus = VaultStatus.LOCKED,
                images = emptyList(),
                metadata = null,
                selectedImageIds = emptySet(),
                isMultiSelectActive = false,
                activeModalIndex = null,
                isSlideshowPlaying = false,
                statusMessage = "Vault locked.",
                isError = false
            )
        }
    }

    /**
     * Locks vault and clears selected file and location session.
     */
    fun lockAndClearSession() {
        lockVault()
        _uiState.update {
            it.copy(
                selectedVaultFile = null,
                selectedVaultName = null,
                passwordInput = ""
            )
        }
    }

    fun importImages(uris: List<Uri>) {
        val file = _uiState.value.selectedVaultFile ?: return
        val key = activeMasterKey ?: return
        val context = getApplication<Application>()
        if (uris.isEmpty()) return

        val limitedUris = if (uris.size > 500) {
            _uiState.update { it.copy(snackbarMessage = "Selected ${uris.size} images; importing first 500.") }
            uris.take(500)
        } else {
            uris
        }

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    progressPercent = 0f,
                    progressLabel = "Securing ${limitedUris.size} images (up to 500)…"
                )
            }

            val targetFolder = _uiState.value.currentFolderId
            val result = ImgVaultEngine.importImagesBatch(
                context = context,
                vaultFile = file,
                masterKey = key,
                sourceUris = limitedUris,
                targetFolderId = targetFolder,
                onProgress = { current, total, label ->
                    val pct = if (total > 0) current.toFloat() / total else 0f
                    _uiState.update {
                        it.copy(
                            progressPercent = pct,
                            progressLabel = label
                        )
                    }
                }
            )

            when (result) {
                is ImgVaultResult.Success -> {
                    val (successCount, totalAttempted) = result.data
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            statusMessage = "✓ $successCount of $totalAttempted images secured in vault!",
                            isError = false,
                            snackbarMessage = "Added $successCount photos to IMG Vault"
                        )
                    }
                }
                is ImgVaultResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            statusMessage = result.message,
                            isError = true,
                            snackbarMessage = result.message
                        )
                    }
                }
            }

            loadVaultData(file, key)
        }
    }

    fun deleteSelectedImages() {
        val file = _uiState.value.selectedVaultFile ?: return
        val key = activeMasterKey ?: return
        val selectedIds = _uiState.value.selectedImageIds
        if (selectedIds.isEmpty()) return

        closeDeleteConfirmDialog()
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    progressLabel = "Deleting ${selectedIds.size} images…"
                )
            }

            val res = ImgVaultEngine.markDeleted(file, key, selectedIds)
            _uiState.update {
                it.copy(
                    isProcessing = false,
                    selectedImageIds = emptySet(),
                    isMultiSelectActive = false,
                    statusMessage = "Deleted ${selectedIds.size} images.",
                    snackbarMessage = "Deleted ${selectedIds.size} images from vault. Run Compact to reclaim space."
                )
            }
            loadVaultData(file, key)
        }
    }

    fun renameImage(newName: String) {
        val file = _uiState.value.selectedVaultFile ?: return
        val key = activeMasterKey ?: return
        val target = _uiState.value.renameTargetImage ?: return
        if (newName.isBlank()) return

        viewModelScope.launch(Dispatchers.IO) {
            ImgVaultEngine.renameImage(file, key, target.id, newName.trim())
            closeRenameDialog()
            loadVaultData(file, key)
        }
    }

    fun moveSelectedImages(targetFolderId: String?) {
        val file = _uiState.value.selectedVaultFile ?: return
        val key = activeMasterKey ?: return
        val selectedIds = _uiState.value.selectedImageIds
        if (selectedIds.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            ImgVaultEngine.moveImages(file, key, selectedIds, targetFolderId)
            closeFolderActionDialog()
            closeMoveDialog()
            _uiState.update { 
                it.copy(
                    selectedImageIds = emptySet(), 
                    isMultiSelectActive = false,
                    snackbarMessage = "Moved ${selectedIds.size} image(s) to folder"
                ) 
            }
            loadVaultData(file, key)
        }
    }

    fun copySelectedImages(targetFolderId: String?) {
        val file = _uiState.value.selectedVaultFile ?: return
        val key = activeMasterKey ?: return
        val selectedIds = _uiState.value.selectedImageIds
        if (selectedIds.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            ImgVaultEngine.copyImages(file, key, selectedIds, targetFolderId)
            closeFolderActionDialog()
            _uiState.update { 
                it.copy(
                    selectedImageIds = emptySet(), 
                    isMultiSelectActive = false,
                    snackbarMessage = "Copied ${selectedIds.size} image(s) within vault"
                ) 
            }
            loadVaultData(file, key)
        }
    }

    fun createFolder(folderName: String) {
        val file = _uiState.value.selectedVaultFile ?: return
        val key = activeMasterKey ?: return
        if (folderName.isBlank()) return

        viewModelScope.launch(Dispatchers.IO) {
            ImgVaultEngine.createFolder(file, key, folderName.trim())
            closeCreateFolderDialog()
            loadVaultData(file, key)
        }
    }

    fun compactVault() {
        val file = _uiState.value.selectedVaultFile ?: return
        val key = activeMasterKey ?: return
        val context = getApplication<Application>()

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    progressPercent = 0.1f,
                    progressLabel = "Compacting container to reclaim free space…"
                )
            }

            val res = ImgVaultEngine.compactVault(context, file, key) { pct, label ->
                _uiState.update { it.copy(progressPercent = pct, progressLabel = label) }
            }

            when (res) {
                is ImgVaultResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            statusMessage = "✓ Compaction complete! Free space reclaimed.",
                            snackbarMessage = "Vault storage compacted successfully"
                        )
                    }
                    loadVaultData(file, key)
                }
                is ImgVaultResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            statusMessage = res.message,
                            isError = true
                        )
                    }
                }
            }
        }
    }

    fun exportSelectedImages(deleteFromVaultAfterExport: Boolean = false) {
        val file = _uiState.value.selectedVaultFile ?: return
        val key = activeMasterKey ?: return
        val context = getApplication<Application>()
        val selectedIds = _uiState.value.selectedImageIds
        val records = _uiState.value.metadata?.records?.filter { selectedIds.contains(it.id) } ?: emptyList()
        if (records.isEmpty()) return

        closeMobileStorageDialog()
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    progressPercent = 0f,
                    progressLabel = if (deleteFromVaultAfterExport)
                        "Moving ${records.size} images to mobile storage…"
                    else
                        "Copying ${records.size} images to mobile storage…"
                )
            }

            var exported = 0
            records.forEachIndexed { i, rec ->
                _uiState.update {
                    it.copy(
                        progressPercent = (i.toFloat() / records.size),
                        progressLabel = "Saving ${rec.name} (${i + 1}/${records.size})…"
                    )
                }
                val decRes = ImgVaultEngine.decryptImageRecord(context, file, key, rec)
                if (decRes is ImgVaultResult.Success) {
                    saveToPublicPictures(context, decRes.data, rec.name, rec.mimeType)
                    exported++
                }
            }

            if (deleteFromVaultAfterExport && exported > 0) {
                ImgVaultEngine.markDeleted(file, key, selectedIds)
            }

            _uiState.update {
                it.copy(
                    isProcessing = false,
                    selectedImageIds = emptySet(),
                    isMultiSelectActive = false,
                    statusMessage = if (deleteFromVaultAfterExport)
                        "✓ Moved $exported images to Pictures/IMGVault (deleted from vault)."
                    else
                        "✓ Copied $exported images to Pictures/IMGVault.",
                    snackbarMessage = if (deleteFromVaultAfterExport)
                        "Moved $exported images to phone gallery and deleted from vault"
                    else
                        "Copied $exported images to phone gallery (Pictures/IMGVault)"
                )
            }
            if (deleteFromVaultAfterExport) {
                loadVaultData(file, key)
            }
        }
    }

    private fun saveToPublicPictures(context: Context, sourceFile: File, name: String, mime: String) {
        try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, mime)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/IMGVault")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    FileInputStream(sourceFile).use { fis -> fis.copyTo(os) }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    context.contentResolver.update(uri, values, null, null)
                }
            }
        } catch (_: Throwable) {}
    }

    fun shareSelectedImages(context: Context) {
        val file = _uiState.value.selectedVaultFile ?: return
        val key = activeMasterKey ?: return
        val selectedIds = _uiState.value.selectedImageIds
        val records = _uiState.value.metadata?.records?.filter { selectedIds.contains(it.id) } ?: emptyList()
        if (records.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            val uris = arrayListOf<Uri>()
            records.forEach { rec ->
                val decRes = ImgVaultEngine.decryptImageRecord(context, file, key, rec)
                if (decRes is ImgVaultResult.Success) {
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        decRes.data
                    )
                    uris.add(uri)
                }
            }

            if (uris.isNotEmpty()) {
                val intent = if (uris.size == 1) {
                    Intent(Intent.ACTION_SEND).apply {
                        type = "image/*"
                        putExtra(Intent.EXTRA_STREAM, uris.first())
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                } else {
                    Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                        type = "image/*"
                        putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                }
                val chooser = Intent.createChooser(intent, "Share Encrypted Vault Images").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            }
        }
    }

    /**
     * Feature: "Add one button to import image to SSI ok"
     * Decrypts the chosen image from IMG Vault and directly imports it to SSI (media_imported).
     */
    fun importImageToSsi(image: VaultImage) {
        val file = _uiState.value.selectedVaultFile ?: return
        val key = activeMasterKey ?: return
        val context = getApplication<Application>()
        val record = _uiState.value.metadata?.records?.firstOrNull { it.id == image.id } ?: return

        viewModelScope.launch(Dispatchers.IO) {
            val decRes = ImgVaultEngine.decryptImageRecord(context, file, key, record)
            if (decRes is ImgVaultResult.Success) {
                val tempDecryptedFile = decRes.data
                val result = SsiManager.importImage(
                    context = context,
                    sourceUriString = Uri.fromFile(tempDecryptedFile).toString(),
                    preferredFileName = image.name
                )
                if (result.isSuccess) {
                    _uiState.update {
                        it.copy(snackbarMessage = "✓ Imported ${image.name} to SSI")
                    }
                } else {
                    _uiState.update {
                        it.copy(snackbarMessage = "Failed to import to SSI: ${result.exceptionOrNull()?.message}")
                    }
                }
            }
        }
    }

    // Modal & Slideshow
    fun openModal(index: Int) {
        val image = _uiState.value.displayedImages.getOrNull(index) ?: return
        val file = _uiState.value.selectedVaultFile ?: return
        val key = activeMasterKey ?: return
        val context = getApplication<Application>()
        val record = _uiState.value.metadata?.records?.firstOrNull { it.id == image.id }

        viewModelScope.launch(Dispatchers.IO) {
            // Decrypt high-res image for fullscreen viewer
            if (record != null) {
                val decRes = ImgVaultEngine.decryptImageRecord(context, file, key, record)
                if (decRes is ImgVaultResult.Success) {
                    val updatedList = _uiState.value.images.map {
                        if (it.id == image.id) it.copy(file = decRes.data) else it
                    }
                    _uiState.update { it.copy(images = updatedList, activeModalIndex = index) }
                    return@launch
                }
            }
            _uiState.update { it.copy(activeModalIndex = index) }
        }
    }

    fun closeModal() {
        slideshowJob?.cancel()
        _uiState.update { it.copy(activeModalIndex = null, isSlideshowPlaying = false) }
    }

    fun nextImage() {
        val count = _uiState.value.displayedImages.size
        if (count == 0) return
        val nextIdx = ((_uiState.value.activeModalIndex ?: 0) + 1) % count
        openModal(nextIdx)
    }

    fun previousImage() {
        val count = _uiState.value.displayedImages.size
        if (count == 0) return
        val prevIdx = ((_uiState.value.activeModalIndex ?: 0) - 1 + count) % count
        openModal(prevIdx)
    }

    fun toggleSlideshow() {
        val isPlaying = _uiState.value.isSlideshowPlaying
        if (isPlaying) {
            slideshowJob?.cancel()
            _uiState.update { it.copy(isSlideshowPlaying = false) }
        } else {
            _uiState.update { it.copy(isSlideshowPlaying = true, isModalPaused = false) }
            startSlideshow()
        }
    }

    fun togglePause() {
        _uiState.update { it.copy(isModalPaused = !it.isModalPaused) }
    }

    private fun startSlideshow() {
        slideshowJob?.cancel()
        slideshowJob = viewModelScope.launch {
            while (isActive && _uiState.value.isSlideshowPlaying) {
                if (!_uiState.value.isModalPaused) {
                    for (step in 1..40) {
                        delay(50L)
                        _uiState.update { it.copy(slideshowProgress = step / 40f) }
                    }
                    nextImage()
                } else {
                    delay(200L)
                }
            }
        }
    }

    // View Modes, Search, Sort & Multi-select
    fun setFolder(folderId: String?) {
        _uiState.update { it.copy(currentFolderId = folderId, selectedImageIds = emptySet()) }
    }

    fun setSearchQuery(q: String) {
        _uiState.update { it.copy(searchQuery = q) }
    }

    fun setSortMode(mode: VaultSortMode) {
        _uiState.update { it.copy(sortMode = mode) }
    }

    fun toggleGridView() {
        _uiState.update { it.copy(isGridView = !it.isGridView) }
    }

    fun startMultiSelect(id: String) {
        val current = _uiState.value.selectedImageIds.toMutableSet()
        current.add(id)
        _uiState.update {
            it.copy(
                selectedImageIds = current,
                isMultiSelectActive = true
            )
        }
    }

    fun toggleSelectImage(id: String) {
        val current = _uiState.value.selectedImageIds.toMutableSet()
        if (current.contains(id)) current.remove(id) else current.add(id)
        _uiState.update {
            it.copy(
                selectedImageIds = current,
                isMultiSelectActive = current.isNotEmpty()
            )
        }
    }

    fun selectAll() {
        val allIds = _uiState.value.displayedImages.map { it.id }.toSet()
        _uiState.update {
            it.copy(selectedImageIds = allIds, isMultiSelectActive = allIds.isNotEmpty())
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedImageIds = emptySet(), isMultiSelectActive = false) }
    }

    fun clearSnackbarMessage() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun clearSnackbar() {
        clearSnackbarMessage()
    }

    override fun onCleared() {
        super.onCleared()
        slideshowJob?.cancel()
    }
}
