package com.example.ui

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import com.example.util.AppStorageHelper
import com.example.util.ApsReceiverVaultTarget
import com.example.util.ApsTransferEngine
import com.example.util.ApsTransferItem
import com.example.util.ApsVaultType
import com.example.util.EncryptedVideoDataSource
import com.example.util.SettingsManager
import com.example.util.VaultContainerInfo
import com.example.util.VaultSortMode
import com.example.util.VaultVideo
import com.example.util.VidVaultEngine
import com.example.util.VidVaultFolder
import com.example.util.VidVaultMetadata
import com.example.util.VidVaultRecord
import com.example.util.VidVaultResult
import com.example.util.VidVaultSessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.crypto.spec.SecretKeySpec

data class VidVaultUiState(
    val isUnlocked: Boolean = false,
    val isLoading: Boolean = false,
    val loadingMessage: String = "",
    val progressFraction: Float = 0f,
    val errorMessage: String? = null,
    val snackbarMessage: String? = null,
    val availableVaults: List<VaultContainerInfo> = emptyList(),
    val selectedVaultFile: File? = null,
    val selectedVaultName: String = "",
    val metadata: VidVaultMetadata? = null,
    val folders: List<VidVaultFolder> = emptyList(),
    val selectedFolderId: String? = null,
    val displayedVideos: List<VaultVideo> = emptyList(),
    val searchQuery: String = "",
    val sortMode: VaultSortMode = VaultSortMode.DATE_DESC,
    val isGridView: Boolean = true,
    val selectedVideoIds: Set<String> = emptySet(),
    val isMultiSelectActive: Boolean = false,
    val activeModalIndex: Int? = null,
    val isCreateDialogOpen: Boolean = false,
    val isVaultInfoDialogOpen: Boolean = false,
    val isCreateFolderDialogOpen: Boolean = false,
    val isRenameDialogOpen: Boolean = false,
    val renamingVideoId: String? = null,
    val renamingVideoName: String = "",
    val isFolderActionDialogOpen: Boolean = false,
    val isFolderActionCopy: Boolean = false,
    val isMobileStorageDialogOpen: Boolean = false,
    val isDeleteConfirmDialogOpen: Boolean = false,
    val generatedRecoveryKey: String? = null,
    val showRecoveryKeyDialog: Boolean = false,
    val vaultLocationPath: String = ""
)

class VidVaultViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(VidVaultUiState())
    val uiState: StateFlow<VidVaultUiState> = _uiState.asStateFlow()

    private var activeMasterKey: SecretKeySpec? = null
    private var activeVaultFile: File? = null
    private var activeMetadata: VidVaultMetadata? = null
    private var allLoadedVideos: List<VaultVideo> = emptyList()

    init {
        val ctx = getApplication<Application>()
        val vaultDir = AppStorageHelper.getVidVaultDir(ctx)
        _uiState.update { it.copy(vaultLocationPath = vaultDir.absolutePath) }
        scanAvailableVaults()
        restoreSessionIfActive()
    }

    fun scanAvailableVaults() {
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<Application>()
            val vaultDir = AppStorageHelper.getVidVaultDir(ctx)
            val files = vaultDir.listFiles { f ->
                f.isFile && f.name.endsWith(VidVaultEngine.EXTENSION, ignoreCase = true)
            }?.sortedByDescending { it.lastModified() } ?: emptyList()

            val containers = files.map { file ->
                VaultContainerInfo(
                    fileName = file.name,
                    file = file,
                    uri = Uri.fromFile(file),
                    sizeBytes = file.length(),
                    capacityBytes = VidVaultEngine.CAPACITY_30GB
                )
            }

            _uiState.update { state ->
                val chosen = state.selectedVaultFile?.takeIf { it.exists() }
                    ?: containers.firstOrNull()?.file
                state.copy(
                    availableVaults = containers,
                    selectedVaultFile = chosen,
                    selectedVaultName = chosen?.name ?: "",
                    vaultLocationPath = vaultDir.absolutePath
                )
            }
        }
    }

    fun selectVaultContainer(container: VaultContainerInfo) {
        _uiState.update {
            it.copy(
                selectedVaultFile = container.file,
                selectedVaultName = container.fileName,
                errorMessage = null
            )
        }
    }

    fun importExternalVaultUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<Application>()
            _uiState.update {
                it.copy(
                    isLoading = true,
                    loadingMessage = "Importing .vidvault container to storage…",
                    progressFraction = 0.2f
                )
            }
            try {
                var fileName = "imported_${System.currentTimeMillis()}${VidVaultEngine.EXTENSION}"
                ctx.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) {
                            val name = cursor.getString(idx)
                            if (!name.isNullOrBlank()) fileName = name
                        }
                    }
                }
                if (!fileName.endsWith(VidVaultEngine.EXTENSION, ignoreCase = true)) {
                    fileName += VidVaultEngine.EXTENSION
                }

                val targetFile = File(AppStorageHelper.getVidVaultDir(ctx), fileName)
                // Stream copy using 64 KB buffer (zero full-file RAM buffering)
                val buf = ByteArray(64 * 1024)
                ctx.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        while (true) {
                            val r = input.read(buf)
                            if (r == -1) break
                            output.write(buf, 0, r)
                        }
                    }
                }

                scanAvailableVaults()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        loadingMessage = "",
                        progressFraction = 0f,
                        selectedVaultFile = targetFile,
                        selectedVaultName = targetFile.name,
                        snackbarMessage = "Loaded video vault: ${targetFile.name}"
                    )
                }
            } catch (e: Throwable) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        loadingMessage = "",
                        errorMessage = "Could not load .vidvault file: ${e.message}"
                    )
                }
            }
        }
    }

    private fun restoreSessionIfActive() {
        val ctx = getApplication<Application>()
        if (!SettingsManager.settings.value.keepVidVaultUnlockedAcrossRestarts) return

        val session = VidVaultSessionManager.getSession(ctx)
        val key = VidVaultSessionManager.activeMasterKey
        val path = session.vaultFilePath
        if (session.isUnlocked && key != null && !path.isNullOrBlank()) {
            val file = File(path)
            if (file.exists()) {
                viewModelScope.launch {
                    val meta = VidVaultEngine.readMetadata(file, key)
                    if (meta != null) {
                        activeMasterKey = key
                        activeVaultFile = file
                        activeMetadata = meta
                        loadVideosForCurrentVault(meta, file, key)
                    }
                }
            }
        }
    }

    fun reloadSession() {
        scanAvailableVaults()
        restoreSessionIfActive()
    }

    fun createVault(vaultName: String, password: String, capacityBytes: Long) {
        val cleanName = vaultName.trim().ifEmpty { "MyVideoVault" }
        val sanitizedFileName = cleanName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .let { if (it.endsWith(VidVaultEngine.EXTENSION, ignoreCase = true)) it else "$it${VidVaultEngine.EXTENSION}" }

        if (password.length < 4) {
            _uiState.update { it.copy(errorMessage = "Password must be at least 4 characters.") }
            return
        }

        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val targetFile = File(AppStorageHelper.getVidVaultDir(ctx), sanitizedFileName)
            val recoveryKey = VidVaultEngine.generateRecoveryKey()

            _uiState.update {
                it.copy(
                    isLoading = true,
                    loadingMessage = "Initializing AES-256 VID Vault container…",
                    progressFraction = 0.4f,
                    errorMessage = null
                )
            }

            when (val res = VidVaultEngine.createVault(ctx, targetFile, cleanName, password, recoveryKey, capacityBytes)) {
                is VidVaultResult.Success -> {
                    scanAvailableVaults()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            loadingMessage = "",
                            progressFraction = 0f,
                            isCreateDialogOpen = false,
                            selectedVaultFile = targetFile,
                            selectedVaultName = targetFile.name,
                            generatedRecoveryKey = recoveryKey,
                            showRecoveryKeyDialog = true,
                            snackbarMessage = "VID Vault '${targetFile.name}' created!"
                        )
                    }
                    // Automatically unlock the newly created vault
                    unlockVault(password)
                }
                is VidVaultResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            loadingMessage = "",
                            progressFraction = 0f,
                            errorMessage = res.message
                        )
                    }
                }
            }
        }
    }

    fun unlockVault(passwordOrRecoveryKey: String) {
        val file = _uiState.value.selectedVaultFile
        if (file == null || !file.exists()) {
            _uiState.update { it.copy(errorMessage = "Please select or create a .vidvault container first.") }
            return
        }
        if (passwordOrRecoveryKey.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please enter your password or recovery key.") }
            return
        }

        viewModelScope.launch {
            val ctx = getApplication<Application>()
            _uiState.update {
                it.copy(
                    isLoading = true,
                    loadingMessage = "Decrypting VID Vault header & index…",
                    progressFraction = 0.5f,
                    errorMessage = null
                )
            }

            when (val res = VidVaultEngine.openVault(ctx, file, passwordOrRecoveryKey)) {
                is VidVaultResult.Success -> {
                    val (masterKey, meta) = res.data
                    activeMasterKey = masterKey
                    activeVaultFile = file
                    activeMetadata = meta

                    if (SettingsManager.settings.value.keepVidVaultUnlockedAcrossRestarts) {
                        VidVaultSessionManager.saveSession(
                            context = ctx,
                            vaultUri = Uri.fromFile(file),
                            vaultFilePath = file.absolutePath,
                            vaultFileName = file.name,
                            masterKey = masterKey
                        )
                    }

                    loadVideosForCurrentVault(meta, file, masterKey)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            loadingMessage = "",
                            progressFraction = 0f,
                            snackbarMessage = "VID Vault unlocked (${meta.activeVideoCount} videos)"
                        )
                    }
                }
                is VidVaultResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            loadingMessage = "",
                            progressFraction = 0f,
                            errorMessage = res.message
                        )
                    }
                }
            }
        }
    }

    private suspend fun loadVideosForCurrentVault(
        meta: VidVaultMetadata,
        vaultFile: File,
        masterKey: SecretKeySpec
    ) {
        val ctx = getApplication<Application>()
        activeMetadata = meta

        val activeRecords = meta.activeRecords
        // Initial fast list using any already cached thumbnails on disk
        val thumbDir = File(ctx.cacheDir, "vid_vault_thumbs")
        allLoadedVideos = activeRecords.map { rec ->
            val cachedThumb = File(thumbDir, "vthumb_${rec.id}.jpg").takeIf { it.exists() && it.length() > 0 }
            VaultVideo(
                id = rec.id,
                name = rec.name,
                sizeBytes = rec.sizeBytes,
                durationMs = rec.durationMs,
                folderId = rec.folderId,
                createdAt = rec.createdAt,
                mimeType = rec.mimeType,
                thumbnailFile = cachedThumb
            )
        }

        _uiState.update { state ->
            state.copy(
                isUnlocked = true,
                metadata = meta,
                folders = meta.folders,
                selectedVaultFile = vaultFile,
                selectedVaultName = vaultFile.name
            )
        }
        applyFiltersAndSort()

        // Lazily hydrate missing small JPEG thumbnails on IO thread (ultra-low RAM)
        // Also auto-repairs already-received videos whose hasThumbnail was previously false!
        viewModelScope.launch(Dispatchers.IO) {
            val updatedList = allLoadedVideos.toMutableList()
            for (i in activeRecords.indices) {
                if (activeMasterKey == null) break
                val rec = activeRecords[i]
                if (updatedList[i].thumbnailFile == null) {
                    val thumb = if (rec.hasThumbnail) {
                        VidVaultEngine.getThumbnail(ctx, vaultFile, masterKey, rec)
                            ?: VidVaultEngine.generateAndCacheMissingThumbnail(ctx, vaultFile, masterKey, rec)
                    } else {
                        VidVaultEngine.generateAndCacheMissingThumbnail(ctx, vaultFile, masterKey, rec)
                    }
                    if (thumb != null && activeMasterKey != null) {
                        updatedList[i] = updatedList[i].copy(thumbnailFile = thumb)
                        allLoadedVideos = updatedList.toList()
                        applyFiltersAndSort()
                    }
                }
            }
        }
    }

    private fun applyFiltersAndSort() {
        val state = _uiState.value
        val query = state.searchQuery.trim().lowercase()
        val folderId = state.selectedFolderId

        var filtered = allLoadedVideos
        if (folderId != null) {
            filtered = filtered.filter { it.folderId == folderId }
        }
        if (query.isNotEmpty()) {
            filtered = filtered.filter { it.name.lowercase().contains(query) }
        }

        val sorted = when (state.sortMode) {
            VaultSortMode.NAME_ASC -> filtered.sortedBy { it.name.lowercase() }
            VaultSortMode.DATE_DESC -> filtered.sortedByDescending { it.createdAt }
            VaultSortMode.SIZE_DESC -> filtered.sortedByDescending { it.sizeBytes }
        }

        _uiState.update { it.copy(displayedVideos = sorted) }
    }

    fun importVideos(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val file = activeVaultFile ?: return
        val key = activeMasterKey ?: return

        viewModelScope.launch {
            val ctx = getApplication<Application>()
            _uiState.update {
                it.copy(
                    isLoading = true,
                    loadingMessage = "Streaming ${uris.size} video(s) directly to encrypted storage…",
                    progressFraction = 0.05f,
                    errorMessage = null
                )
            }

            val res = VidVaultEngine.importVideosBatch(
                context = ctx,
                vaultFile = file,
                masterKey = key,
                sourceUris = uris,
                targetFolderId = _uiState.value.selectedFolderId
            ) { current, total, pct, msg ->
                _uiState.update {
                    it.copy(
                        loadingMessage = "[$current/$total] $msg",
                        progressFraction = pct
                    )
                }
            }

            when (res) {
                is VidVaultResult.Success -> {
                    val (imported, attempted) = res.data
                    val updatedMeta = VidVaultEngine.readMetadata(file, key)
                    if (updatedMeta != null) {
                        loadVideosForCurrentVault(updatedMeta, file, key)
                    }
                    scanAvailableVaults()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            loadingMessage = "",
                            progressFraction = 0f,
                            snackbarMessage = "Encrypted $imported of $attempted video(s) directly to storage!"
                        )
                    }
                }
                is VidVaultResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            loadingMessage = "",
                            progressFraction = 0f,
                            errorMessage = res.message
                        )
                    }
                }
            }
        }
    }

    fun lockVault() {
        val ctx = getApplication<Application>()
        if (SettingsManager.settings.value.apsAutoStopOnLock &&
            ApsTransferEngine.state.value.activeReceiveVaultType == ApsVaultType.VID_VAULT
        ) {
            ApsTransferEngine.stopReceiveMode()
        }
        activeMasterKey = null
        activeMetadata = null
        allLoadedVideos = emptyList()
        VidVaultEngine.clearTempVideoCacheOnly(ctx)
        _uiState.update {
            it.copy(
                isUnlocked = false,
                metadata = null,
                folders = emptyList(),
                selectedFolderId = null,
                displayedVideos = emptyList(),
                selectedVideoIds = emptySet(),
                isMultiSelectActive = false,
                activeModalIndex = null,
                snackbarMessage = "VID Vault locked."
            )
        }
    }

    fun lockAndClearSession() {
        val ctx = getApplication<Application>()
        if (ApsTransferEngine.state.value.activeReceiveVaultType == ApsVaultType.VID_VAULT) {
            ApsTransferEngine.stopReceiveMode()
        }
        VidVaultSessionManager.clearSession(ctx)
        activeMasterKey = null
        activeMetadata = null
        allLoadedVideos = emptyList()
        _uiState.update {
            it.copy(
                isUnlocked = false,
                metadata = null,
                folders = emptyList(),
                selectedFolderId = null,
                displayedVideos = emptyList(),
                selectedVideoIds = emptySet(),
                isMultiSelectActive = false,
                activeModalIndex = null,
                snackbarMessage = "VID Vault locked & all temporary cache wiped!"
            )
        }
    }

    fun createFolder(folderName: String) {
        val clean = folderName.trim()
        if (clean.isEmpty()) return
        val file = activeVaultFile ?: return
        val key = activeMasterKey ?: return

        viewModelScope.launch {
            when (val res = VidVaultEngine.createFolder(file, key, clean)) {
                is VidVaultResult.Success -> {
                    loadVideosForCurrentVault(res.data, file, key)
                    _uiState.update {
                        it.copy(
                            isCreateFolderDialogOpen = false,
                            snackbarMessage = "Folder '$clean' created."
                        )
                    }
                }
                is VidVaultResult.Error -> {
                    _uiState.update { it.copy(errorMessage = res.message) }
                }
            }
        }
    }

    fun selectFolder(folderId: String?) {
        _uiState.update { it.copy(selectedFolderId = folderId) }
        applyFiltersAndSort()
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        applyFiltersAndSort()
    }

    fun onSortModeChanged(mode: VaultSortMode) {
        _uiState.update { it.copy(sortMode = mode) }
        applyFiltersAndSort()
    }

    fun toggleViewMode() {
        _uiState.update { it.copy(isGridView = !it.isGridView) }
    }

    fun toggleVideoSelection(videoId: String) {
        _uiState.update { state ->
            val updated = state.selectedVideoIds.toMutableSet()
            if (updated.contains(videoId)) updated.remove(videoId) else updated.add(videoId)
            state.copy(
                selectedVideoIds = updated,
                isMultiSelectActive = updated.isNotEmpty()
            )
        }
    }

    fun selectAllDisplayed() {
        _uiState.update { state ->
            val allIds = state.displayedVideos.map { it.id }.toSet()
            state.copy(
                selectedVideoIds = allIds,
                isMultiSelectActive = allIds.isNotEmpty()
            )
        }
    }

    fun clearSelection() {
        _uiState.update {
            it.copy(
                selectedVideoIds = emptySet(),
                isMultiSelectActive = false
            )
        }
    }

    fun deleteSelectedVideos() {
        val ids = _uiState.value.selectedVideoIds
        if (ids.isEmpty()) return
        val file = activeVaultFile ?: return
        val key = activeMasterKey ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isDeleteConfirmDialogOpen = false, isLoading = true, loadingMessage = "Removing videos…") }
            when (val res = VidVaultEngine.markDeleted(file, key, ids)) {
                is VidVaultResult.Success -> {
                    loadVideosForCurrentVault(res.data, file, key)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            loadingMessage = "",
                            selectedVideoIds = emptySet(),
                            isMultiSelectActive = false,
                            snackbarMessage = "Deleted ${ids.size} video(s). Run Compact to reclaim disk space."
                        )
                    }
                }
                is VidVaultResult.Error -> {
                    _uiState.update { it.copy(isLoading = false, loadingMessage = "", errorMessage = res.message) }
                }
            }
        }
    }

    fun openRenameDialog(video: VaultVideo) {
        _uiState.update {
            it.copy(
                isRenameDialogOpen = true,
                renamingVideoId = video.id,
                renamingVideoName = video.name
            )
        }
    }

    fun dismissRenameDialog() {
        _uiState.update {
            it.copy(isRenameDialogOpen = false, renamingVideoId = null, renamingVideoName = "")
        }
    }

    fun confirmRenameVideo(newName: String) {
        val clean = newName.trim()
        val id = _uiState.value.renamingVideoId ?: return
        if (clean.isEmpty()) return
        val file = activeVaultFile ?: return
        val key = activeMasterKey ?: return

        viewModelScope.launch {
            when (val res = VidVaultEngine.renameVideo(file, key, id, clean)) {
                is VidVaultResult.Success -> {
                    loadVideosForCurrentVault(res.data, file, key)
                    _uiState.update {
                        it.copy(
                            isRenameDialogOpen = false,
                            renamingVideoId = null,
                            renamingVideoName = "",
                            snackbarMessage = "Renamed to '$clean'"
                        )
                    }
                }
                is VidVaultResult.Error -> {
                    _uiState.update { it.copy(errorMessage = res.message) }
                }
            }
        }
    }

    fun moveOrCopySelectedToFolder(targetFolderId: String?, isCopy: Boolean) {
        val ids = _uiState.value.selectedVideoIds
        if (ids.isEmpty()) return
        val file = activeVaultFile ?: return
        val key = activeMasterKey ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isFolderActionDialogOpen = false, isLoading = true, loadingMessage = if (isCopy) "Copying…" else "Moving…") }
            val res = if (isCopy) {
                VidVaultEngine.copyVideos(file, key, ids, targetFolderId)
            } else {
                VidVaultEngine.moveVideos(file, key, ids, targetFolderId)
            }
            when (res) {
                is VidVaultResult.Success -> {
                    loadVideosForCurrentVault(res.data, file, key)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            loadingMessage = "",
                            selectedVideoIds = emptySet(),
                            isMultiSelectActive = false,
                            snackbarMessage = if (isCopy) "Copied ${ids.size} video(s)." else "Moved ${ids.size} video(s)."
                        )
                    }
                }
                is VidVaultResult.Error -> {
                    _uiState.update { it.copy(isLoading = false, loadingMessage = "", errorMessage = res.message) }
                }
            }
        }
    }

    /**
     * Exports selected videos directly into Mobile Storage (Movies/VIDVault) using zero-RAM
     * 64 KB disk-to-disk streaming (no intermediate temp file, no RAM spike).
     */
    fun exportSelectedToMobileStorage(deleteFromVaultAfter: Boolean) {
        val ids = _uiState.value.selectedVideoIds
        if (ids.isEmpty()) return
        val file = activeVaultFile ?: return
        val key = activeMasterKey ?: return
        val meta = activeMetadata ?: return

        viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<Application>()
            val records = meta.activeRecords.filter { ids.contains(it.id) }
            if (records.isEmpty()) return@launch

            _uiState.update {
                it.copy(
                    isMobileStorageDialogOpen = false,
                    isLoading = true,
                    loadingMessage = "Streaming ${records.size} video(s) to Movies/VIDVault…",
                    progressFraction = 0.05f
                )
            }

            var exportedCount = 0
            val exportedIds = mutableSetOf<String>()

            records.forEachIndexed { idx, rec ->
                _uiState.update {
                    it.copy(
                        loadingMessage = "Exporting ${idx + 1}/${records.size}: ${rec.name}",
                        progressFraction = idx.toFloat() / records.size.coerceAtLeast(1)
                    )
                }
                val saved = exportSingleRecordToMediaStore(ctx, file, key, rec)
                if (saved) {
                    exportedCount++
                    exportedIds.add(rec.id)
                }
            }

            if (deleteFromVaultAfter && exportedIds.isNotEmpty()) {
                val delRes = VidVaultEngine.markDeleted(file, key, exportedIds)
                if (delRes is VidVaultResult.Success) {
                    loadVideosForCurrentVault(delRes.data, file, key)
                }
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    loadingMessage = "",
                    progressFraction = 0f,
                    selectedVideoIds = emptySet(),
                    isMultiSelectActive = false,
                    snackbarMessage = if (deleteFromVaultAfter) {
                        "Moved $exportedCount video(s) to Movies/VIDVault!"
                    } else {
                        "Copied $exportedCount video(s) to Movies/VIDVault!"
                    }
                )
            }
        }
    }

    private suspend fun exportSingleRecordToMediaStore(
        context: Context,
        vaultFile: File,
        masterKey: SecretKeySpec,
        record: VidVaultRecord
    ): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, record.name)
                    put(MediaStore.Video.Media.MIME_TYPE, record.mimeType.ifBlank { "video/mp4" })
                    put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/VIDVault")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return false

                var ok = false
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    val res = VidVaultEngine.streamDecryptVideoToOutputStream(vaultFile, masterKey, record, out)
                    ok = res is VidVaultResult.Success
                }
                val doneValues = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
                context.contentResolver.update(uri, doneValues, null, null)
                ok
            } else {
                val moviesDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                    "VIDVault"
                ).apply { mkdirs() }
                val dest = File(moviesDir, record.name)
                FileOutputStream(dest).use { out ->
                    VidVaultEngine.streamDecryptVideoToOutputStream(vaultFile, masterKey, record, out) is VidVaultResult.Success
                }
            }
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Shares one or more selected videos using FileProvider + Android Share Sheet.
     * Streams encrypted video(s) directly to storage in 64 KB chunks so RAM remains minimal.
     */
    fun shareSelectedVideos(activityContext: Context) {
        val ids = _uiState.value.selectedVideoIds
        if (ids.isEmpty()) return
        shareVideoIds(activityContext, ids)
    }

    fun shareSingleVideo(activityContext: Context, videoId: String) {
        shareVideoIds(activityContext, setOf(videoId))
    }

    private fun shareVideoIds(activityContext: Context, ids: Set<String>) {
        val file = activeVaultFile ?: return
        val key = activeMasterKey ?: return
        val meta = activeMetadata ?: return

        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val records = meta.activeRecords.filter { ids.contains(it.id) }
            if (records.isEmpty()) return@launch

            _uiState.update {
                it.copy(
                    isLoading = true,
                    loadingMessage = "Preparing ${records.size} video(s) on storage for sharing…",
                    progressFraction = 0.1f
                )
            }

            val shareUris = arrayListOf<Uri>()
            var lastMime = "video/*"

            withContext(Dispatchers.IO) {
                records.forEachIndexed { idx, rec ->
                    _uiState.update {
                        it.copy(
                            loadingMessage = "Streaming for share (${idx + 1}/${records.size}): ${rec.name}",
                            progressFraction = idx.toFloat() / records.size.coerceAtLeast(1)
                        )
                    }
                    when (val res = VidVaultEngine.streamDecryptVideoToFile(ctx, file, key, rec)) {
                        is VidVaultResult.Success -> {
                            val tempFile = res.data
                            val uri = FileProvider.getUriForFile(
                                ctx,
                                "${ctx.packageName}.fileprovider",
                                tempFile
                            )
                            shareUris.add(uri)
                            lastMime = rec.mimeType.ifBlank { "video/*" }
                        }
                        is VidVaultResult.Error -> {}
                    }
                }
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    loadingMessage = "",
                    progressFraction = 0f
                )
            }

            if (shareUris.isEmpty()) {
                _uiState.update { it.copy(errorMessage = "Could not prepare video(s) for sharing.") }
                return@launch
            }

            try {
                val shareIntent = if (shareUris.size == 1) {
                    Intent(Intent.ACTION_SEND).apply {
                        type = lastMime
                        putExtra(Intent.EXTRA_STREAM, shareUris[0])
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                } else {
                    Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                        type = "video/*"
                        putParcelableArrayListExtra(Intent.EXTRA_STREAM, shareUris)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                }
                val chooser = Intent.createChooser(shareIntent, "Share Video${if (shareUris.size > 1) "s" else ""}")
                if (activityContext !is android.app.Activity) {
                    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                activityContext.startActivity(chooser)
            } catch (e: Throwable) {
                _uiState.update { it.copy(errorMessage = "Share failed: ${e.message}") }
            }
        }
    }

    fun compactVault() {
        val file = activeVaultFile ?: return
        val key = activeMasterKey ?: return

        viewModelScope.launch {
            val ctx = getApplication<Application>()
            _uiState.update {
                it.copy(
                    isLoading = true,
                    loadingMessage = "Compacting VID Vault on storage…",
                    progressFraction = 0.05f
                )
            }

            when (
                val res = VidVaultEngine.compactVault(ctx, file, key) { pct, msg ->
                    _uiState.update { it.copy(progressFraction = pct, loadingMessage = msg) }
                }
            ) {
                is VidVaultResult.Success -> {
                    loadVideosForCurrentVault(res.data, file, key)
                    scanAvailableVaults()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            loadingMessage = "",
                            progressFraction = 0f,
                            snackbarMessage = "VID Vault compacted! Reclaimed deleted space."
                        )
                    }
                }
                is VidVaultResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            loadingMessage = "",
                            progressFraction = 0f,
                            errorMessage = res.message
                        )
                    }
                }
            }
        }
    }

    /**
     * Direct-from-disk zero-RAM MediaDataSource for instant playback.
     */
    fun createDirectMediaDataSource(videoId: String): EncryptedVideoDataSource? {
        val file = activeVaultFile ?: return null
        val key = activeMasterKey ?: return null
        val rec = activeMetadata?.activeRecords?.find { it.id == videoId } ?: return null
        return VidVaultEngine.createDirectDiskMediaDataSource(file, key, rec)
    }

    /**
     * Media3 ExoPlayer zero-RAM DataSource.Factory for hardware + software-fallback playback.
     */
    @OptIn(UnstableApi::class)
    fun createExoDataSourceFactory(videoId: String): DataSource.Factory? {
        val file = activeVaultFile ?: return null
        val key = activeMasterKey ?: return null
        val rec = activeMetadata?.activeRecords?.find { it.id == videoId } ?: return null
        return VidVaultEngine.createExoDataSourceFactory(file, key, rec)
    }

    /**
     * Fallback storage-streamed decryption if direct MediaDataSource fails for a specific codec/container
     * or if the user disables Direct Disk Stream in VID Vault Settings.
     */
    suspend fun prepareTempStorageFile(videoId: String): File? {
        val ctx = getApplication<Application>()
        val file = activeVaultFile ?: return null
        val key = activeMasterKey ?: return null
        val rec = activeMetadata?.activeRecords?.find { it.id == videoId } ?: return null
        return when (val res = VidVaultEngine.streamDecryptVideoToFile(ctx, file, key, rec)) {
            is VidVaultResult.Success -> res.data
            is VidVaultResult.Error -> null
        }
    }

    fun openPlayerModal(index: Int) {
        if (index in _uiState.value.displayedVideos.indices) {
            _uiState.update { it.copy(activeModalIndex = index) }
        }
    }

    fun closePlayerModal() {
        val ctx = getApplication<Application>()
        _uiState.update { it.copy(activeModalIndex = null) }
        viewModelScope.launch(Dispatchers.IO) {
            VidVaultEngine.clearTempVideoCacheOnly(ctx)
        }
    }

    fun nextVideo() {
        _uiState.update { state ->
            val current = state.activeModalIndex ?: return@update state
            val size = state.displayedVideos.size
            if (size <= 1) return@update state
            state.copy(activeModalIndex = (current + 1) % size)
        }
    }

    fun previousVideo() {
        _uiState.update { state ->
            val current = state.activeModalIndex ?: return@update state
            val size = state.displayedVideos.size
            if (size <= 1) return@update state
            state.copy(activeModalIndex = if (current - 1 < 0) size - 1 else current - 1)
        }
    }

    // Dialog toggles
    fun setCreateDialogOpen(open: Boolean) = _uiState.update { it.copy(isCreateDialogOpen = open) }
    fun setVaultInfoDialogOpen(open: Boolean) = _uiState.update { it.copy(isVaultInfoDialogOpen = open) }
    fun setCreateFolderDialogOpen(open: Boolean) = _uiState.update { it.copy(isCreateFolderDialogOpen = open) }
    fun setFolderActionDialogOpen(open: Boolean, isCopy: Boolean = false) =
        _uiState.update { it.copy(isFolderActionDialogOpen = open, isFolderActionCopy = isCopy) }
    fun setMobileStorageDialogOpen(open: Boolean) = _uiState.update { it.copy(isMobileStorageDialogOpen = open) }
    fun setDeleteConfirmDialogOpen(open: Boolean) = _uiState.update { it.copy(isDeleteConfirmDialogOpen = open) }
    fun dismissRecoveryKeyDialog() = _uiState.update { it.copy(showRecoveryKeyDialog = false, generatedRecoveryKey = null) }
    fun clearError() = _uiState.update { it.copy(errorMessage = null) }
    fun clearSnackbar() = _uiState.update { it.copy(snackbarMessage = null) }

    // ---- APS (Direct Vault-to-Vault High-Security Transfer) ----

    fun buildApsItemsForSelected(): List<ApsTransferItem> {
        val file = activeVaultFile ?: return emptyList()
        val key = activeMasterKey ?: return emptyList()
        val meta = activeMetadata ?: return emptyList()
        val ids = _uiState.value.selectedVideoIds
        if (ids.isEmpty()) return emptyList()

        val ctx = getApplication<Application>()
        return meta.activeRecords.filter { ids.contains(it.id) }.map { rec ->
            ApsTransferItem(
                id = rec.id,
                name = rec.name,
                sizeBytes = rec.sizeBytes,
                mimeType = rec.mimeType,
                durationMs = rec.durationMs,
                vaultType = ApsVaultType.VID_VAULT,
                thumbnailProvider = {
                    try {
                        val thumbFile = VidVaultEngine.getThumbnail(ctx, file, key, rec)
                            ?: VidVaultEngine.generateAndCacheMissingThumbnail(ctx, file, key, rec)
                        val rawBytes = thumbFile?.takeIf { it.exists() && it.length() in 1L..96_000L }?.readBytes()
                        rawBytes
                    } catch (_: Throwable) {
                        null
                    }
                },
                streamPlaintextTo = { outStream ->
                    VidVaultEngine.streamDecryptVideoToOutputStream(
                        vaultFile = file,
                        masterKey = key,
                        record = rec,
                        outputStream = outStream
                    ) is VidVaultResult.Success
                }
            )
        }
    }

    fun getApsReceiverTarget(): ApsReceiverVaultTarget? {
        val file = activeVaultFile ?: return null
        val key = activeMasterKey ?: return null
        return ApsReceiverVaultTarget(
            vaultType = ApsVaultType.VID_VAULT,
            vaultFile = file,
            masterKey = key,
            targetFolderId = _uiState.value.selectedFolderId,
            onIngestComplete = { reloadUnlockedVaultFromDisk() }
        )
    }

    fun reloadUnlockedVaultFromDisk() {
        val file = activeVaultFile ?: return
        val key = activeMasterKey ?: return
        viewModelScope.launch {
            val updated = VidVaultEngine.readMetadata(file, key)
            if (updated != null) {
                loadVideosForCurrentVault(updated, file, key)
                scanAvailableVaults()
                _uiState.update { it.copy(snackbarMessage = "VID Vault updated with received APS videos!") }
            }
        }
    }
}
