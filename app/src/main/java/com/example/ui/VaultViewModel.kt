package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.util.AppStorageHelper
import com.example.util.NotificationHelper
import com.example.util.SettingsManager
import com.example.util.StorageHelper
import com.example.util.VaultContainerInfo
import com.example.util.VaultImage
import com.example.util.VaultManager
import com.example.util.VaultSessionManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

enum class VaultTab { CREATE, ACCESS }
enum class VaultStatus { LOCKED, UNLOCKED, SLIDESHOW }

data class VaultUiState(
    val currentTab: VaultTab = VaultTab.ACCESS,

    // Create panel
    val createFolderUri: Uri? = null,
    val createFolderName: String? = null,
    val createSizeMB: Long = 5,
    val createPasswordInput: String = "",
    val createMessage: String? = null,
    val isCreateError: Boolean = false,

    // Access panel
    val accessFolderUri: Uri? = null,
    val accessFolderName: String? = null,
    val availableDatFiles: List<VaultContainerInfo> = emptyList(),
    val selectedDatUri: Uri? = null,
    val selectedDatName: String? = null,
    val accessPasswordInput: String = "",
    val isUnlocked: Boolean = false,
    val unlockedPin: String? = null,
    val vaultStatus: VaultStatus = VaultStatus.LOCKED,
    val accessMessage: String? = null,
    val isAccessError: Boolean = false,
    val extractedPath: String = "—",
    val exVaultFolderUri: Uri? = null,
    val exVaultFolderName: String = "EX Vault",

    // Progress
    val isProcessing: Boolean = false,
    val progressPercent: Float = 0f,
    val progressLabel: String = "",

    // Images
    val images: List<VaultImage> = emptyList(),

    // Extra controls
    val isPasswordResetVisible: Boolean = false,
    val newPasswordInput: String = "",
    val isResizeVisible: Boolean = false,
    val resizeSizeMB: Long = 25,

    // Modal & Slideshow
    val activeModalIndex: Int? = null,
    val isSlideshowPlaying: Boolean = false,
    val isModalPaused: Boolean = false,
    val slideshowProgress: Float = 0f,
    val snackbarMessage: String? = null
)

class VaultViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(VaultUiState())
    val uiState: StateFlow<VaultUiState> = _uiState.asStateFlow()

    private var slideshowJob: Job? = null
    private val SLIDESHOW_STEP_MS = 50L
    private val SLIDESHOW_TOTAL_MS = 2000L

    init {
        restoreSessionIfActive()
    }

    fun reloadSession() {
        restoreSessionIfActive()
    }

    private fun restoreSessionIfActive() {
        val context = getApplication<Application>()
        viewModelScope.launch {
            try {
                val session = VaultSessionManager.getSession(context)
                val shouldKeepUnlocked = SettingsManager.settings.value.keepVaultUnlockedAcrossRestarts
                if (shouldKeepUnlocked && session.isUnlocked && session.datUri != null && session.pin != null && session.pin.isNotEmpty()) {
                    val exVaultUri = session.exVaultUri ?: Uri.fromFile(AppStorageHelper.getExVaultDir(context))
                    val exPath = session.exVaultPath ?: "Android/media/${context.packageName}/EX_Vault"

                    val images = try {
                        VaultManager.detectAndScanExVault(context, exVaultUri)
                    } catch (_: Throwable) {
                        emptyList()
                    }

                    _uiState.update {
                        it.copy(
                            isUnlocked = true,
                            vaultStatus = VaultStatus.UNLOCKED,
                            selectedDatUri = session.datUri,
                            selectedDatName = session.datFileName,
                            unlockedPin = session.pin,
                            exVaultFolderUri = exVaultUri,
                            exVaultFolderName = exPath,
                            extractedPath = exPath,
                            images = images,
                            isPasswordResetVisible = true,
                            isResizeVisible = true,
                            accessMessage = "🔓 Vault restored from active session (${images.size} image(s)).",
                            isAccessError = false
                        )
                    }
                } else {
                    VaultManager.cleanExtractedFiles(context)
                }
            } catch (_: Throwable) {
                VaultManager.cleanExtractedFiles(context)
            }
        }
    }

    fun setTab(tab: VaultTab) {
        stopSlideshow()
        _uiState.update { it.copy(currentTab = tab) }
    }

    // --- CREATE PANEL ---

    fun onCreateFolderSelected(uri: Uri) {
        val context = getApplication<Application>()
        StorageHelper.takePersistablePermission(context, uri)
        val folderName = uri.lastPathSegment?.substringAfterLast(':') ?: "Folder"
        _uiState.update {
            it.copy(
                createFolderUri = uri,
                createFolderName = folderName,
                createMessage = "Folder selected: $folderName",
                isCreateError = false
            )
        }
    }

    fun setCreateSize(sizeMB: Long) {
        _uiState.update { it.copy(createSizeMB = sizeMB) }
    }

    fun setCreatePassword(pin: String) {
        if (pin.length <= 8 && pin.all { it.isDigit() }) {
            _uiState.update { it.copy(createPasswordInput = pin) }
        }
    }

    fun generateDatFile() {
        val state = _uiState.value
        val pin = state.createPasswordInput.trim()
        if (pin.length < 4 || pin.length > 8) {
            _uiState.update {
                it.copy(
                    createMessage = "PIN must be 4 to 8 digits.",
                    isCreateError = true
                )
            }
            return
        }

        val context = getApplication<Application>()
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    progressPercent = 0.1f,
                    progressLabel = "Generating DAT file…",
                    createMessage = "Generating encrypted container…"
                )
            }

            try {
                val targetBytes = state.createSizeMB * 1024 * 1024
                val (fileName, fileUri) = VaultManager.generateDatFile(
                    context = context,
                    folderUri = state.createFolderUri,
                    pin = pin,
                    targetSizeBytes = targetBytes
                ) { pct, label ->
                    _uiState.update { it.copy(progressPercent = pct, progressLabel = label) }
                }

                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        createMessage = "Created \"$fileName\" (${state.createSizeMB} MB) successfully!",
                        isCreateError = false,
                        createPasswordInput = ""
                    )
                }

                NotificationHelper.showNotification(
                    context = context,
                    title = "Secure DAT Vault Created",
                    message = "Generated $fileName (${state.createSizeMB} MB) with AES-GCM encryption.",
                    notificationId = NotificationHelper.NOTIFICATION_TEST
                )

                // If created in the folder, auto-select it for access
                if (state.createFolderUri != null) {
                    refreshAccessFolderDatFiles(state.createFolderUri)
                    _uiState.update {
                        it.copy(
                            selectedDatUri = fileUri,
                            selectedDatName = fileName
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        createMessage = "Generation failed: ${e.message}",
                        isCreateError = true
                    )
                }
            }
        }
    }

    // --- ACCESS PANEL ---

    fun onAccessFolderSelected(uri: Uri) {
        val context = getApplication<Application>()
        StorageHelper.takePersistablePermission(context, uri)
        val folderName = uri.lastPathSegment?.substringAfterLast(':') ?: "Folder"
        _uiState.update {
            it.copy(
                accessFolderUri = uri,
                accessFolderName = folderName,
                accessMessage = "Folder chosen: $folderName",
                isAccessError = false
            )
        }
        refreshAccessFolderDatFiles(uri)
    }

    private fun refreshAccessFolderDatFiles(folderUri: Uri) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            val datList = mutableListOf<VaultContainerInfo>()
            try {
                val rootDocId = DocumentsContract.getTreeDocumentId(folderUri)
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(folderUri, rootDocId)
                val projection = arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_SIZE
                )
                context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val sizeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
                    while (cursor.moveToNext()) {
                        val docId = cursor.getString(idCol)
                        val name = cursor.getString(nameCol) ?: continue
                        val size = cursor.getLong(sizeCol)
                        if (name.endsWith(".dat", ignoreCase = true)) {
                            val docUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, docId)
                            datList.add(VaultContainerInfo(fileName = name, uri = docUri, sizeBytes = size))
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore folder read error
            }

            _uiState.update { state ->
                val matchingSelected = datList.find { it.uri == state.selectedDatUri }
                state.copy(
                    availableDatFiles = datList,
                    selectedDatUri = matchingSelected?.uri ?: datList.firstOrNull()?.uri,
                    selectedDatName = matchingSelected?.fileName ?: datList.firstOrNull()?.fileName
                )
            }
        }
    }

    fun onDirectDatFilePicked(uri: Uri) {
        val context = getApplication<Application>()
        StorageHelper.takePersistablePermission(context, uri)

        var fileName = "vault.dat"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx != -1 && cursor.moveToFirst()) {
                fileName = cursor.getString(idx) ?: fileName
            }
        }

        _uiState.update {
            it.copy(
                selectedDatUri = uri,
                selectedDatName = fileName,
                accessMessage = "Selected $fileName. Enter PIN to unlock.",
                isAccessError = false
            )
        }
    }

    fun onDatFileDropdownSelected(uri: Uri) {
        val item = _uiState.value.availableDatFiles.find { it.uri == uri }
        _uiState.update {
            it.copy(
                selectedDatUri = uri,
                selectedDatName = item?.fileName ?: "vault.dat",
                accessMessage = "Selected ${item?.fileName}. Enter PIN to unlock.",
                isAccessError = false
            )
        }
    }

    fun setAccessPassword(pin: String) {
        if (pin.length <= 8 && pin.all { it.isDigit() }) {
            _uiState.update { it.copy(accessPasswordInput = pin) }
        }
    }

    fun unlockVault() {
        val state = _uiState.value
        val datUri = state.selectedDatUri
        if (datUri == null) {
            _uiState.update { it.copy(accessMessage = "Please select a .dat file first.", isAccessError = true) }
            return
        }
        val pin = state.accessPasswordInput.trim()
        if (pin.length < 4 || pin.length > 8) {
            _uiState.update { it.copy(accessMessage = "PIN must be 4 to 8 digits.", isAccessError = true) }
            return
        }

        val context = getApplication<Application>()
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    progressPercent = 0.1f,
                    progressLabel = "Decrypting vault…",
                    accessMessage = "Decrypting container…"
                )
            }

            try {
                val decryptedImages = VaultManager.unlockAndExtract(context, datUri, pin) { pct, label ->
                    _uiState.update { it.copy(progressPercent = pct, progressLabel = label) }
                }

                _uiState.update {
                    it.copy(
                        progressPercent = 0.75f,
                        progressLabel = "Extracting to EX Vault folder…"
                    )
                }

                // Locate or create the "EX Vault" folder in the granted folder or Downloads
                val targetFolder = state.accessFolderUri ?: state.createFolderUri
                val (exVaultUri, exVaultPath) = VaultManager.getOrCreateExVaultFolder(context, targetFolder)

                // Sync decrypted images into "EX Vault" folder so user can see and edit them in their phone's file manager
                val syncedImages = VaultManager.syncImagesToExVault(context, exVaultUri, decryptedImages) { pct, label ->
                    _uiState.update { it.copy(progressPercent = pct, progressLabel = label) }
                }

                VaultSessionManager.saveSession(
                    context = context,
                    datUri = datUri,
                    datFileName = state.selectedDatName,
                    pin = pin,
                    exVaultUri = exVaultUri,
                    exVaultPath = exVaultPath
                )

                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        isUnlocked = true,
                        unlockedPin = pin,
                        images = syncedImages,
                        vaultStatus = VaultStatus.UNLOCKED,
                        accessPasswordInput = "",
                        isPasswordResetVisible = true,
                        isResizeVisible = true,
                        exVaultFolderUri = exVaultUri,
                        exVaultFolderName = exVaultPath,
                        extractedPath = exVaultPath,
                        accessMessage = "Unlocked! Extracted ${syncedImages.size} image(s) to '$exVaultPath' on your device.",
                        isAccessError = false
                    )
                }

                NotificationHelper.showNotification(
                    context = context,
                    title = "Vault Unlocked",
                    message = "Successfully unlocked ${state.selectedDatName ?: "vault"} to $exVaultPath.",
                    notificationId = NotificationHelper.NOTIFICATION_TEST
                )
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        accessMessage = "Unlock failed: ${t.message ?: "Incorrect PIN or corrupted vault data."}",
                        isAccessError = true
                    )
                }
            }
        }
    }

    fun syncFromExVault() {
        val state = _uiState.value
        val exVaultUri = state.exVaultFolderUri ?: return
        if (!state.isUnlocked) return

        val context = getApplication<Application>()
        viewModelScope.launch {
            try {
                val detectedImages = VaultManager.detectAndScanExVault(context, exVaultUri)
                if (detectedImages.isNotEmpty() || state.images.isNotEmpty()) {
                    _uiState.update {
                        it.copy(
                            images = detectedImages,
                            accessMessage = "🔄 Synced with EX Vault: ${detectedImages.size} image(s) detected.",
                            snackbarMessage = "EX Vault updated (${detectedImages.size} images)"
                        )
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun lockAndClean() {
        stopSlideshow()
        val state = _uiState.value
        val context = getApplication<Application>()

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    progressPercent = 0.1f,
                    progressLabel = "Detecting updates in EX Vault…",
                    accessMessage = "Scanning EX Vault & auto-updating container…"
                )
            }

            var savedCount = state.images.size
            try {
                val exVaultUri = state.exVaultFolderUri
                val datUri = state.selectedDatUri
                val pin = state.unlockedPin

                if (exVaultUri != null && datUri != null && pin != null) {
                    // Auto-detect any added, edited, or deleted files in EX Vault
                    val detectedImages = VaultManager.detectAndScanExVault(context, exVaultUri)
                    val imagesToSave = if (detectedImages.isNotEmpty()) detectedImages else state.images
                    savedCount = imagesToSave.size

                    // Auto-update container with latest changes and forward granular progress
                    _uiState.update {
                        it.copy(
                            progressPercent = 0.25f,
                            progressLabel = "Saving $savedCount image(s) to vault container…"
                        )
                    }
                    VaultManager.saveVault(context, datUri, pin, imagesToSave) { pct, label ->
                        _uiState.update {
                            it.copy(
                                progressPercent = 0.25f + (pct * 0.55f),
                                progressLabel = label
                            )
                        }
                    }

                    // Clean plaintext images from EX Vault folder
                    _uiState.update {
                        it.copy(
                            progressPercent = 0.85f,
                            progressLabel = "Cleaning plaintext files from EX Vault…"
                        )
                    }
                    VaultManager.cleanExVaultFolder(context, exVaultUri)
                }

                VaultManager.cleanExtractedFiles(context)
                VaultSessionManager.clearSession(context)

                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        isUnlocked = false,
                        unlockedPin = null,
                        vaultStatus = VaultStatus.LOCKED,
                        images = emptyList(),
                        isPasswordResetVisible = false,
                        isResizeVisible = false,
                        newPasswordInput = "",
                        extractedPath = "—",
                        accessMessage = "🔒 Vault locked & cleaned. Successfully saved $savedCount image(s) from EX Vault.",
                        isAccessError = false,
                        activeModalIndex = null
                    )
                }
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        accessMessage = "❌ Lock failed: ${t.message ?: "Unknown error"}. Your files are kept safe in EX Vault folder.",
                        isAccessError = true
                    )
                }
            }
        }
    }

    // --- ACTIONS: ADD / SAVE / EXPORT / REORDER / DELETE ---

    fun addImages(uris: List<Uri>) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    progressPercent = 0.2f,
                    progressLabel = "Adding ${uris.size} image(s)…"
                )
            }

            val extractDir = VaultManager.getExtractDir(context)
            val currentList = _uiState.value.images.toMutableList()

            for ((idx, uri) in uris.withIndex()) {
                var fileName = "img_${System.currentTimeMillis()}_$idx.jpg"
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx != -1 && cursor.moveToFirst()) {
                        fileName = cursor.getString(nameIdx) ?: fileName
                    }
                }

                var targetFile = File(extractDir, fileName)
                var count = 1
                while (targetFile.exists()) {
                    val base = fileName.substringBeforeLast('.')
                    val ext = fileName.substringAfterLast('.', "jpg")
                    targetFile = File(extractDir, "${base}_$count.$ext")
                    count++
                }

                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }

                currentList.add(VaultImage(name = targetFile.name, file = targetFile, sizeBytes = targetFile.length()))
            }

            // Also sync new images to EX Vault if available
            val exVaultUri = _uiState.value.exVaultFolderUri
            if (exVaultUri != null) {
                VaultManager.syncImagesToExVault(context, exVaultUri, currentList)
            }

            _uiState.update {
                it.copy(
                    isProcessing = false,
                    images = currentList,
                    accessMessage = "Added ${uris.size} image(s). Synced to EX Vault.",
                    isAccessError = false
                )
            }
        }
    }

    fun deleteImage(index: Int) {
        val currentList = _uiState.value.images.toMutableList()
        val context = getApplication<Application>()
        if (index in currentList.indices) {
            val removed = currentList.removeAt(index)
            removed.file.delete()
            if (removed.docUri != null) {
                try {
                    DocumentsContract.deleteDocument(context.contentResolver, removed.docUri)
                } catch (_: Exception) {}
            }
            _uiState.update {
                it.copy(
                    images = currentList,
                    accessMessage = "Deleted ${removed.name}. Tap Save to persist.",
                    isAccessError = false
                )
            }
        }
    }

    fun moveImage(fromIndex: Int, toIndex: Int) {
        val currentList = _uiState.value.images.toMutableList()
        if (fromIndex in currentList.indices && toIndex in currentList.indices) {
            val item = currentList.removeAt(fromIndex)
            currentList.add(toIndex, item)
            _uiState.update {
                it.copy(
                    images = currentList,
                    accessMessage = "Reordered images. Tap Save to persist.",
                    isAccessError = false
                )
            }
        }
    }

    fun saveVault() {
        val state = _uiState.value
        val datUri = state.selectedDatUri ?: return
        val pin = state.unlockedPin ?: return

        val context = getApplication<Application>()
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    progressPercent = 0.1f,
                    progressLabel = "Detecting & saving changes…",
                    accessMessage = "Auto-syncing EX Vault & encrypting…"
                )
            }

            try {
                // Auto-detect any changes in EX Vault
                val exVaultUri = state.exVaultFolderUri
                val imagesToSave = if (exVaultUri != null) {
                    val detected = VaultManager.detectAndScanExVault(context, exVaultUri)
                    if (detected.isNotEmpty()) detected else state.images
                } else {
                    state.images
                }

                val totalBytes = VaultManager.saveVault(context, datUri, pin, imagesToSave) { pct, label ->
                    _uiState.update { it.copy(progressPercent = pct, progressLabel = label) }
                }

                if (exVaultUri != null) {
                    VaultManager.syncImagesToExVault(context, exVaultUri, imagesToSave)
                }

                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        images = imagesToSave,
                        accessMessage = "Saved ${imagesToSave.size} images to container & synced EX Vault (${totalBytes / (1024 * 1024)} MB).",
                        isAccessError = false
                    )
                }

                NotificationHelper.showNotification(
                    context = context,
                    title = "Vault Saved",
                    message = "Saved ${imagesToSave.size} items to ${state.selectedDatName ?: "container"}.",
                    notificationId = NotificationHelper.NOTIFICATION_TEST
                )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        accessMessage = "Save failed: ${e.message}",
                        isAccessError = true
                    )
                }
            }
        }
    }

    fun exportToZip() {
        val state = _uiState.value
        if (state.images.isEmpty()) return

        val context = getApplication<Application>()
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    progressPercent = 0.3f,
                    progressLabel = "Creating ZIP archive…"
                )
            }

            try {
                val zipFile = VaultManager.exportToZip(context, state.images)
                // Save directly to Downloads folder so it's visible in Files/Downloads app
                val downloadsUri = VaultManager.saveZipToDownloads(context, zipFile)
                // Trigger share sheet so user can send to Drive, WhatsApp, or Save to Device
                VaultManager.shareZipFile(context, zipFile)

                val locationText = if (downloadsUri != null) "Saved to Downloads & opened Share" else "Ready to share"
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        accessMessage = "ZIP Export: $locationText (${zipFile.length() / 1024} KB)",
                        snackbarMessage = "Exported ${state.images.size} images to Downloads & Share sheet"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        accessMessage = "Export failed: ${e.message}",
                        isAccessError = true
                    )
                }
            }
        }
    }

    fun exportToGallery() {
        val state = _uiState.value
        if (state.images.isEmpty()) return

        val context = getApplication<Application>()
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    progressPercent = 0.2f,
                    progressLabel = "Exporting to phone gallery…"
                )
            }

            try {
                val count = VaultManager.exportAllToGallery(context, state.images)
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        accessMessage = "Exported $count images to your phone's Pictures/VaultImages folder!",
                        snackbarMessage = "Saved $count photos to Gallery (Pictures/VaultImages)"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        accessMessage = "Gallery export failed: ${e.message}",
                        isAccessError = true
                    )
                }
            }
        }
    }

    fun setNewPassword(pin: String) {
        if (pin.length <= 8 && pin.all { it.isDigit() }) {
            _uiState.update { it.copy(newPasswordInput = pin) }
        }
    }

    fun resetPassword() {
        val state = _uiState.value
        val datUri = state.selectedDatUri ?: return
        val newPin = state.newPasswordInput.trim()

        if (newPin.length < 4 || newPin.length > 8) {
            _uiState.update { it.copy(accessMessage = "New PIN must be 4 to 8 digits.", isAccessError = true) }
            return
        }

        val context = getApplication<Application>()
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    progressPercent = 0.2f,
                    progressLabel = "Updating encryption PIN…"
                )
            }

            try {
                VaultManager.resetPassword(context, datUri, newPin, state.images) { pct, label ->
                    _uiState.update { it.copy(progressPercent = pct, progressLabel = label) }
                }

                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        unlockedPin = newPin,
                        newPasswordInput = "",
                        accessMessage = "PIN reset to $newPin successfully!",
                        isAccessError = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        accessMessage = "Reset failed: ${e.message}",
                        isAccessError = true
                    )
                }
            }
        }
    }

    fun setResizeSizeMB(sizeMB: Long) {
        _uiState.update { it.copy(resizeSizeMB = sizeMB) }
    }

    fun resizeVault() {
        val state = _uiState.value
        val datUri = state.selectedDatUri ?: return
        val pin = state.unlockedPin ?: return
        val targetBytes = state.resizeSizeMB * 1024 * 1024

        val context = getApplication<Application>()
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    progressPercent = 0.1f,
                    progressLabel = "Resizing container to ${state.resizeSizeMB} MB…"
                )
            }

            try {
                VaultManager.resizeVault(context, datUri, pin, state.images, targetBytes) { pct, label ->
                    _uiState.update { it.copy(progressPercent = pct, progressLabel = label) }
                }

                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        accessMessage = "Vault resized to ${state.resizeSizeMB} MB successfully!",
                        isAccessError = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        accessMessage = "Resize failed: ${e.message}",
                        isAccessError = true
                    )
                }
            }
        }
    }

    // --- MODAL & SLIDESHOW ---

    fun openModal(index: Int) {
        _uiState.update {
            it.copy(
                activeModalIndex = index,
                isModalPaused = false
            )
        }
    }

    fun closeModal() {
        stopSlideshow()
        _uiState.update {
            it.copy(
                activeModalIndex = null,
                isModalPaused = false
            )
        }
    }

    fun nextSlide() {
        _uiState.update { state ->
            val total = state.images.size
            if (total == 0) return@update state
            val nextIdx = ((state.activeModalIndex ?: 0) + 1) % total
            state.copy(activeModalIndex = nextIdx, slideshowProgress = 0f)
        }
    }

    fun prevSlide() {
        _uiState.update { state ->
            val total = state.images.size
            if (total == 0) return@update state
            val prevIdx = ((state.activeModalIndex ?: 0) - 1 + total) % total
            state.copy(activeModalIndex = prevIdx, slideshowProgress = 0f)
        }
    }

    fun toggleSlideshow() {
        val isPlaying = _uiState.value.isSlideshowPlaying
        if (isPlaying) {
            stopSlideshow()
        } else {
            startSlideshow()
        }
    }

    fun toggleModalPause() {
        _uiState.update { it.copy(isModalPaused = !it.isModalPaused) }
    }

    private fun startSlideshow() {
        stopSlideshow()
        if (_uiState.value.images.size < 2) return

        if (_uiState.value.activeModalIndex == null) {
            _uiState.update { it.copy(activeModalIndex = 0) }
        }

        _uiState.update {
            it.copy(
                isSlideshowPlaying = true,
                isModalPaused = false,
                vaultStatus = VaultStatus.SLIDESHOW,
                slideshowProgress = 0f
            )
        }

        slideshowJob = viewModelScope.launch {
            var elapsed = 0L
            while (isActive) {
                delay(SLIDESHOW_STEP_MS)
                if (!_uiState.value.isModalPaused) {
                    elapsed += SLIDESHOW_STEP_MS
                    val progress = (elapsed.toFloat() / SLIDESHOW_TOTAL_MS).coerceIn(0f, 1f)
                    _uiState.update { it.copy(slideshowProgress = progress) }

                    if (elapsed >= SLIDESHOW_TOTAL_MS) {
                        elapsed = 0L
                        nextSlide()
                    }
                }
            }
        }
    }

    private fun stopSlideshow() {
        slideshowJob?.cancel()
        slideshowJob = null
        _uiState.update {
            it.copy(
                isSlideshowPlaying = false,
                isModalPaused = false,
                vaultStatus = if (it.isUnlocked) VaultStatus.UNLOCKED else VaultStatus.LOCKED,
                slideshowProgress = 0f
            )
        }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        stopSlideshow()
        VaultManager.cleanExtractedFiles(getApplication())
    }
}
