package com.example.ui.sshow

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.util.SecureCryptoHelper
import com.example.util.SShowImage
import com.example.util.SettingsManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

sealed interface SShowDialog {
    data object None : SShowDialog
    data class EncryptFilename(val pendingImageUris: List<Uri>) : SShowDialog
    data class DecryptPassword(val secureUri: Uri) : SShowDialog
    data class AddImagesPassword(val secureUri: Uri, val imageUris: List<Uri>) : SShowDialog
    data object ShareOptions : SShowDialog
    data class SharePasswordDisplay(val code: String) : SShowDialog
    data object ViewSharedInput : SShowDialog
    data class SharedImageView(val imageFile: File, val code: String) : SShowDialog
    data object PreviewGrid : SShowDialog
}

data class SShowUiState(
    val images: List<SShowImage> = emptyList(),
    val storedCount: Int = 0,
    val selectedSecureFileName: String? = null,
    val lastGeneratedPin: String? = null,
    val lastGeneratedFileName: String? = null,
    val isLoading: Boolean = false,
    val loadingProgress: Float = 0f,
    val loadingMessage: String = "",
    val statusMessage: String? = null,
    val dialog: SShowDialog = SShowDialog.None,
    val isSlideshowActive: Boolean = false,
    val currentSlideIndex: Int = 0,
    val isSlideshowPaused: Boolean = false,
    val isFastForward: Boolean = false,
    val currentSlideSpeedMs: Long = 1500L,
    val previewFullScreenIndex: Int? = null
)

class SShowViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SShowUiState())
    val uiState: StateFlow<SShowUiState> = _uiState.asStateFlow()

    private var slideshowJob: Job? = null

    init {
        checkStoredImages()
    }

    fun checkStoredImages() {
        viewModelScope.launch {
            val stored = SecureCryptoHelper.loadFromLocalStorage(getApplication())
            _uiState.value = _uiState.value.copy(storedCount = stored.size)
        }
    }

    fun onImagesSelectedForEncrypt(uris: List<Uri>) {
        if (uris.isEmpty()) return
        _uiState.value = _uiState.value.copy(dialog = SShowDialog.EncryptFilename(uris))
    }

    fun onConfirmEncrypt(filename: String, uris: List<Uri>) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                dialog = SShowDialog.None,
                isLoading = true,
                loadingProgress = 0.1f,
                loadingMessage = "Preparing images for encryption…"
            )
            try {
                val tempFiles = copyUrisToTempFiles(uris)
                if (tempFiles.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        statusMessage = "❌ No valid images could be read."
                    )
                    return@launch
                }
                val (pin, outFile) = SecureCryptoHelper.encryptImagesToSecureFile(
                    context = getApplication(),
                    imageFiles = tempFiles,
                    customName = filename,
                    onProgress = { progress, msg ->
                        _uiState.value = _uiState.value.copy(loadingProgress = progress, loadingMessage = msg)
                    }
                )
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    lastGeneratedPin = pin,
                    lastGeneratedFileName = outFile.name,
                    statusMessage = "✅ File encrypted & saved as ${outFile.name}\nPassword: $pin"
                )
            } catch (t: Throwable) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    statusMessage = "❌ Encryption failed: ${t.message ?: "Unknown error"}"
                )
            }
        }
    }

    fun onSecureFileSelected(uri: Uri) {
        _uiState.value = _uiState.value.copy(
            selectedSecureFileName = uri.lastPathSegment?.substringAfterLast("/"),
            dialog = SShowDialog.DecryptPassword(uri)
        )
    }

    fun onConfirmDecrypt(secureUri: Uri, pin: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                dialog = SShowDialog.None,
                isLoading = true,
                loadingProgress = 0.1f,
                loadingMessage = "Decrypting file…"
            )
            try {
                val images = SecureCryptoHelper.decryptSecureFile(
                    context = getApplication(),
                    secureUri = secureUri,
                    pin = pin,
                    onProgress = { progress, msg ->
                        _uiState.value = _uiState.value.copy(loadingProgress = progress, loadingMessage = msg)
                    }
                )
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    images = images,
                    statusMessage = "✅ Successfully decrypted ${images.size} images!"
                )
            } catch (t: Throwable) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    statusMessage = "❌ ${t.message ?: "Incorrect password or corrupted file."}"
                )
            }
        }
    }

    fun onAddImagesSelected(secureUri: Uri?, imageUris: List<Uri>) {
        if (secureUri == null) {
            _uiState.value = _uiState.value.copy(statusMessage = "Please select a .secure file first!")
            return
        }
        if (imageUris.isEmpty()) {
            _uiState.value = _uiState.value.copy(statusMessage = "Please select images to add!")
            return
        }
        _uiState.value = _uiState.value.copy(dialog = SShowDialog.AddImagesPassword(secureUri, imageUris))
    }

    fun onConfirmAddImages(secureUri: Uri, imageUris: List<Uri>, pin: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                dialog = SShowDialog.None,
                isLoading = true,
                loadingProgress = 0.1f,
                loadingMessage = "Adding images and re-encrypting…"
            )
            try {
                val tempFiles = copyUrisToTempFiles(imageUris)
                val updatedFile = SecureCryptoHelper.addImagesToExistingSecure(
                    context = getApplication(),
                    secureUri = secureUri,
                    newImages = tempFiles,
                    pin = pin,
                    onProgress = { progress, msg ->
                        _uiState.value = _uiState.value.copy(loadingProgress = progress, loadingMessage = msg)
                    }
                )
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    statusMessage = "✅ Added ${imageUris.size} images to ${updatedFile.name}!"
                )
            } catch (t: Throwable) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    statusMessage = "❌ Failed: ${t.message ?: "Incorrect password or error"}"
                )
            }
        }
    }

    fun storeInStorage() {
        val current = _uiState.value.images
        if (current.isEmpty()) return
        viewModelScope.launch {
            val storedCount = SecureCryptoHelper.storeInLocalStorage(getApplication(), current)
            _uiState.value = _uiState.value.copy(
                storedCount = storedCount,
                statusMessage = "💾 Stored $storedCount images in device storage!"
            )
        }
    }

    fun mergeToStorage() {
        val current = _uiState.value.images
        if (current.isEmpty()) return
        viewModelScope.launch {
            val total = SecureCryptoHelper.mergeToLocalStorage(getApplication(), current)
            _uiState.value = _uiState.value.copy(
                storedCount = total,
                statusMessage = "🔄 Merged! Total $total images in device storage."
            )
        }
    }

    fun loadFromStorage() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, loadingMessage = "Loading from storage…")
            val stored = SecureCryptoHelper.loadFromLocalStorage(getApplication())
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                images = stored,
                storedCount = stored.size,
                statusMessage = if (stored.isNotEmpty()) "↓ Loaded ${stored.size} images from storage!" else "No images found in storage."
            )
        }
    }

    fun exportStoredToEncrypted(filename: String = "StoredImages") {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, loadingMessage = "Exporting stored images…")
            try {
                val stored = SecureCryptoHelper.loadFromLocalStorage(getApplication())
                if (stored.isEmpty()) {
                    _uiState.value = _uiState.value.copy(isLoading = false, statusMessage = "No stored images to export!")
                    return@launch
                }
                val (pin, outFile) = SecureCryptoHelper.encryptImagesToSecureFile(
                    context = getApplication(),
                    imageFiles = stored.map { it.file },
                    customName = filename
                )
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    lastGeneratedPin = pin,
                    lastGeneratedFileName = outFile.name,
                    statusMessage = "📤 Stored images exported to ${outFile.name}!\nPassword: $pin"
                )
            } catch (t: Throwable) {
                _uiState.value = _uiState.value.copy(isLoading = false, statusMessage = "Export failed: ${t.message ?: "Unknown error"}")
            }
        }
    }

    fun startSlideshow() {
        if (_uiState.value.images.isEmpty()) return
        val defaultSpeed = SettingsManager.settings.value.slideshowSpeedMs
        _uiState.value = _uiState.value.copy(
            isSlideshowActive = true,
            currentSlideIndex = 0,
            isSlideshowPaused = false,
            isFastForward = false,
            currentSlideSpeedMs = defaultSpeed
        )
        startSlideshowLoop()
    }

    private fun startSlideshowLoop() {
        slideshowJob?.cancel()
        slideshowJob = viewModelScope.launch {
            while (_uiState.value.isSlideshowActive) {
                val speed = _uiState.value.currentSlideSpeedMs
                delay(speed)
                if (!_uiState.value.isSlideshowPaused && _uiState.value.images.isNotEmpty()) {
                    val next = (_uiState.value.currentSlideIndex + 1) % _uiState.value.images.size
                    _uiState.value = _uiState.value.copy(currentSlideIndex = next)
                }
            }
        }
    }

    fun toggleSlideshowPause() {
        _uiState.value = _uiState.value.copy(isSlideshowPaused = !_uiState.value.isSlideshowPaused)
    }

    fun toggleFastForward() {
        val newFF = !_uiState.value.isFastForward
        val newSpeed = if (newFF) {
            SettingsManager.settings.value.fastForwardSpeedMs
        } else {
            SettingsManager.settings.value.slideshowSpeedMs
        }
        _uiState.value = _uiState.value.copy(isFastForward = newFF, currentSlideSpeedMs = newSpeed)
        startSlideshowLoop()
    }

    fun nextSlide() {
        val total = _uiState.value.images.size
        if (total > 0) {
            _uiState.value = _uiState.value.copy(currentSlideIndex = (_uiState.value.currentSlideIndex + 1) % total)
        }
    }

    fun previousSlide() {
        val total = _uiState.value.images.size
        if (total > 0) {
            _uiState.value = _uiState.value.copy(currentSlideIndex = (_uiState.value.currentSlideIndex - 1 + total) % total)
        }
    }

    fun stopSlideshow() {
        slideshowJob?.cancel()
        _uiState.value = _uiState.value.copy(isSlideshowActive = false)
    }

    fun showPreviewGrid() {
        _uiState.value = _uiState.value.copy(dialog = SShowDialog.PreviewGrid)
    }

    fun showShareOptions() {
        _uiState.value = _uiState.value.copy(dialog = SShowDialog.ShareOptions)
    }

    fun showViewSharedInput() {
        _uiState.value = _uiState.value.copy(dialog = SShowDialog.ViewSharedInput)
    }

    fun openPreviewFullScreen(index: Int) {
        _uiState.value = _uiState.value.copy(previewFullScreenIndex = index)
    }

    fun closePreviewFullScreen() {
        _uiState.value = _uiState.value.copy(previewFullScreenIndex = null)
    }

    fun nextPreviewFullScreen() {
        val state = _uiState.value
        if (state.images.isEmpty()) return
        val current = state.previewFullScreenIndex ?: 0
        val next = (current + 1) % state.images.size
        _uiState.value = _uiState.value.copy(previewFullScreenIndex = next)
    }

    fun prevPreviewFullScreen() {
        val state = _uiState.value
        if (state.images.isEmpty()) return
        val current = state.previewFullScreenIndex ?: 0
        val prev = if (current - 1 < 0) state.images.size - 1 else current - 1
        _uiState.value = _uiState.value.copy(previewFullScreenIndex = prev)
    }

    fun onShareImageSelected(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, loadingMessage = "Creating image share code…")
            try {
                val tempFiles = copyUrisToTempFiles(listOf(uri))
                val code = SecureCryptoHelper.shareImageWithCode(getApplication(), tempFiles.first())
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    dialog = SShowDialog.SharePasswordDisplay(code)
                )
            } catch (t: Throwable) {
                _uiState.value = _uiState.value.copy(isLoading = false, statusMessage = "Share failed: ${t.message ?: "Unknown error"}")
            }
        }
    }

    fun onViewSharedSubmit(code: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, loadingMessage = "Searching shared image…")
            try {
                val file = SecureCryptoHelper.getSharedImageByCode(getApplication(), code)
                if (file != null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        dialog = SShowDialog.SharedImageView(file, code)
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        statusMessage = "❌ No image found with code '$code'"
                    )
                }
            } catch (t: Throwable) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    statusMessage = "❌ Search error: ${t.message ?: "Unknown error"}"
                )
            }
        }
    }

    fun closeDialog() {
        _uiState.value = _uiState.value.copy(dialog = SShowDialog.None)
    }

    fun clearStatus() {
        _uiState.value = _uiState.value.copy(statusMessage = null)
    }

    private fun copyUrisToTempFiles(uris: List<Uri>): List<File> {
        val context = getApplication<Application>()
        val tempDir = com.example.util.AppStorageHelper.getSShowWorkDir(context)
        val files = mutableListOf<File>()
        val buf = ByteArray(32 * 1024)
        for ((idx, uri) in uris.withIndex()) {
            try {
                val name = "img_${System.currentTimeMillis()}_$idx.jpg"
                val target = File(tempDir, name)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(target).use { output ->
                        var r: Int
                        while (input.read(buf).also { r = it } != -1) {
                            output.write(buf, 0, r)
                        }
                    }
                }
                if (target.exists() && target.length() > 0) {
                    files.add(target)
                }
            } catch (_: Throwable) {}
        }
        return files
    }
}
