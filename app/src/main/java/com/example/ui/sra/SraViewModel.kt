package com.example.ui.sra

import android.app.Application
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.util.SettingsManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.random.Random

data class SraImageItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val uri: Uri,
    val file: File? = null,
    val sizeBytes: Long = 0L,
    val documentFile: DocumentFile? = null
)

data class SraUiState(
    val images: List<SraImageItem> = emptyList(),
    val currentIndex: Int = 0,
    val isPlaying: Boolean = false,
    val progress: Float = 0f,
    val isModalOpen: Boolean = false,
    val statusMessage: String? = null,
    val isLoading: Boolean = false,
    val folderName: String? = null
)

class SraViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SraUiState())
    val uiState: StateFlow<SraUiState> = _uiState.asStateFlow()

    private var slideshowJob: Job? = null

    init {
        // Load default images from app media folder if available
        loadDefaultSampleImages()
    }

    private fun loadDefaultSampleImages() {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val sshowDir = com.example.util.AppStorageHelper.getSShowStoredDir(context)
            val files = sshowDir.listFiles()?.filter { it.isFile && isImageExtension(it.name) } ?: emptyList()
            if (files.isNotEmpty()) {
                val items = files.map { file ->
                    SraImageItem(
                        name = file.name,
                        uri = Uri.fromFile(file),
                        file = file,
                        sizeBytes = file.length()
                    )
                }
                _uiState.value = _uiState.value.copy(
                    images = items,
                    folderName = "SShow Stored Images"
                )
            }
        }
    }

    fun onFolderSelected(treeUri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, statusMessage = "Scanning folder…")
            val context = getApplication<Application>()
            try {
                val rootDoc = DocumentFile.fromTreeUri(context, treeUri)
                if (rootDoc != null && rootDoc.isDirectory) {
                    val folderName = rootDoc.name ?: "Selected Folder"
                    val docFiles = rootDoc.listFiles().filter { it.isFile && isImageExtension(it.name ?: "") }
                    val items = docFiles.map { doc ->
                        SraImageItem(
                            name = doc.name ?: "image.jpg",
                            uri = doc.uri,
                            sizeBytes = doc.length(),
                            documentFile = doc
                        )
                    }
                    _uiState.value = _uiState.value.copy(
                        images = items,
                        currentIndex = 0,
                        isPlaying = false,
                        progress = 0f,
                        folderName = folderName,
                        isLoading = false,
                        statusMessage = if (items.isNotEmpty()) "Loaded ${items.size} images from $folderName" else "No image files found in $folderName"
                    )
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false, statusMessage = "Could not open folder.")
                }
            } catch (t: Throwable) {
                _uiState.value = _uiState.value.copy(isLoading = false, statusMessage = "Error: ${t.message}")
            }
        }
    }

    fun onImagesSelected(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, statusMessage = "Importing selected images…")
            val context = getApplication<Application>()
            val cacheDir = File(context.cacheDir, "sra_images").apply { mkdirs() }
            val items = mutableListOf<SraImageItem>()
            val buf = ByteArray(32 * 1024)

            for ((idx, uri) in uris.withIndex()) {
                val fileName = getFileNameFromUri(context, uri) ?: "image_${System.currentTimeMillis()}_$idx.jpg"
                val localFile = File(cacheDir, fileName)
                try {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(localFile).use { output ->
                            var r: Int
                            while (input.read(buf).also { r = it } != -1) {
                                output.write(buf, 0, r)
                            }
                        }
                    }
                    if (localFile.exists()) {
                        items.add(
                            SraImageItem(
                                name = fileName,
                                uri = Uri.fromFile(localFile),
                                file = localFile,
                                sizeBytes = localFile.length()
                            )
                        )
                    }
                } catch (_: Throwable) {
                    items.add(
                        SraImageItem(
                            name = fileName,
                            uri = uri,
                            sizeBytes = 0L
                        )
                    )
                }
            }

            _uiState.value = _uiState.value.copy(
                images = items,
                currentIndex = 0,
                isPlaying = false,
                progress = 0f,
                folderName = "Imported (${items.size} items)",
                isLoading = false,
                statusMessage = "Loaded ${items.size} images"
            )
        }
    }

    /**
     * Fisher-Yates shuffle algorithm from original HTML code:
     * for (let i = images.length - 1; i > 0; i--) {
     *   const j = Math.floor(Math.random() * (i + 1));
     *   [images[i], images[j]] = [images[j], images[i]];
     * }
     * currentIndex = 0;
     */
    fun rearrange() {
        val currentList = _uiState.value.images.toMutableList()
        if (currentList.size <= 1) return

        for (i in currentList.size - 1 downTo 1) {
            val j = Random.nextInt(i + 1)
            val temp = currentList[i]
            currentList[i] = currentList[j]
            currentList[j] = temp
        }

        _uiState.value = _uiState.value.copy(
            images = currentList,
            currentIndex = 0,
            progress = 0f,
            statusMessage = "🔀 Rearranged ${currentList.size} images"
        )
    }

    fun togglePlayPause() {
        val playing = !_uiState.value.isPlaying
        if (playing) {
            startSlideshow()
        } else {
            pauseSlideshow()
        }
    }

    fun startSlideshow() {
        if (_uiState.value.images.isEmpty()) return
        slideshowJob?.cancel()
        _uiState.value = _uiState.value.copy(isPlaying = true)

        slideshowJob = viewModelScope.launch {
            val stepIntervalMs = 50L
            while (_uiState.value.isPlaying && _uiState.value.images.isNotEmpty()) {
                val intervalSec = SettingsManager.settings.value.sraSlideshowIntervalSeconds.coerceIn(1.0f, 5.0f)
                val totalMs = (intervalSec * 1000f).toLong()
                var elapsedMs = 0L

                while (elapsedMs < totalMs && _uiState.value.isPlaying) {
                    delay(stepIntervalMs)
                    elapsedMs += stepIntervalMs
                    val frac = (elapsedMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
                    _uiState.value = _uiState.value.copy(progress = frac)
                }

                if (!_uiState.value.isPlaying) break

                val count = _uiState.value.images.size
                val nextIndex = _uiState.value.currentIndex + 1
                val autoLoop = SettingsManager.settings.value.sraAutoLoop

                if (nextIndex < count) {
                    _uiState.value = _uiState.value.copy(currentIndex = nextIndex, progress = 0f)
                } else if (autoLoop) {
                    _uiState.value = _uiState.value.copy(currentIndex = 0, progress = 0f)
                } else {
                    pauseSlideshow()
                    break
                }
            }
        }
    }

    fun pauseSlideshow() {
        slideshowJob?.cancel()
        slideshowJob = null
        _uiState.value = _uiState.value.copy(isPlaying = false, progress = 0f)
    }

    fun selectImage(index: Int) {
        val safeIndex = index.coerceIn(0, (_uiState.value.images.size - 1).coerceAtLeast(0))
        _uiState.value = _uiState.value.copy(currentIndex = safeIndex, isModalOpen = true)
    }

    fun nextImage() {
        val count = _uiState.value.images.size
        if (count == 0) return
        val next = (_uiState.value.currentIndex + 1) % count
        _uiState.value = _uiState.value.copy(currentIndex = next, progress = 0f)
    }

    fun prevImage() {
        val count = _uiState.value.images.size
        if (count == 0) return
        val prev = if (_uiState.value.currentIndex - 1 < 0) count - 1 else _uiState.value.currentIndex - 1
        _uiState.value = _uiState.value.copy(currentIndex = prev, progress = 0f)
    }

    fun closeModal() {
        _uiState.value = _uiState.value.copy(isModalOpen = false)
    }

    fun clearStatus() {
        _uiState.value = _uiState.value.copy(statusMessage = null)
    }

    override fun onCleared() {
        super.onCleared()
        slideshowJob?.cancel()
    }

    companion object {
        fun isImageExtension(name: String): Boolean {
            val ext = name.substringAfterLast(".", "").lowercase()
            return ext in listOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "avif", "tif", "tiff")
        }

        private fun getFileNameFromUri(context: android.content.Context, uri: Uri): String? {
            var result: String? = null
            if (uri.scheme == "content") {
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (index != -1) {
                            result = it.getString(index)
                        }
                    }
                }
            }
            if (result == null) {
                result = uri.path
                val cut = result?.lastIndexOf('/')
                if (cut != null && cut != -1) {
                    result = result?.substring(cut + 1)
                }
            }
            return result
        }
    }
}
