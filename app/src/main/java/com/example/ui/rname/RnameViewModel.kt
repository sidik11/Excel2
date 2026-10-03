package com.example.ui.rname

import android.app.Application
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.util.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class RnameItem(
    val id: String = UUID.randomUUID().toString(),
    val originalName: String,
    val newName: String,
    val isNumbered: Boolean,
    val existingNumber: Int? = null,
    val uri: Uri,
    val file: File? = null,
    val documentFile: DocumentFile? = null
)

data class RnameFolderInfo(
    val folderName: String,
    val sequenceRange: String,
    val highestNumber: String,
    val missingNumbers: String
)

data class RnameUiState(
    val folderName: String? = null,
    val totalCount: Int = 0,
    val numberedCount: Int = 0,
    val unrenamedCount: Int = 0,
    val nextNumberStr: String = "0001",
    val folderInfo: RnameFolderInfo? = null,
    val previewItems: List<RnameItem> = emptyList(),
    val isRenaming: Boolean = false,
    val renameProgress: Float = 0f,
    val renameStatus: String? = null,
    val showConfirmDialog: Boolean = false,
    val isLoading: Boolean = false,
    val currentTreeUri: Uri? = null
)

class RnameViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(RnameUiState())
    val uiState: StateFlow<RnameUiState> = _uiState.asStateFlow()

    private var allScannedFiles: List<RnameRawFile> = emptyList()

    private data class RnameRawFile(
        val name: String,
        val uri: Uri,
        val file: File? = null,
        val documentFile: DocumentFile? = null
    )

    init {
        // Load default from app media folder if available
        loadDefaultSampleFolder()
    }

    private fun loadDefaultSampleFolder() {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val sshowDir = com.example.util.AppStorageHelper.getSShowStoredDir(context)
            val files = sshowDir.listFiles()?.filter { it.isFile && isImageExtension(it.name) } ?: emptyList()
            if (files.isNotEmpty()) {
                allScannedFiles = files.map { file ->
                    RnameRawFile(
                        name = file.name,
                        uri = Uri.fromFile(file),
                        file = file
                    )
                }
                computeAnalysis("SShow Stored Images")
            }
        }
    }

    fun onFolderSelected(treeUri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, renameStatus = "Scanning selected folder…", currentTreeUri = treeUri)
            val context = getApplication<Application>()
            try {
                val rootDoc = DocumentFile.fromTreeUri(context, treeUri)
                if (rootDoc != null && rootDoc.isDirectory) {
                    val folderName = rootDoc.name ?: "Selected Folder"
                    val docFiles = rootDoc.listFiles().filter { it.isFile && isImageExtension(it.name ?: "") }
                    allScannedFiles = docFiles.map { doc ->
                        RnameRawFile(
                            name = doc.name ?: "image.jpg",
                            uri = doc.uri,
                            documentFile = doc
                        )
                    }
                    computeAnalysis(folderName)
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false, renameStatus = "Could not open folder.")
                }
            } catch (t: Throwable) {
                _uiState.value = _uiState.value.copy(isLoading = false, renameStatus = "Error: ${t.message}")
            }
        }
    }

    fun onImagesSelected(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, renameStatus = "Analyzing selected images…", currentTreeUri = null)
            val context = getApplication<Application>()
            val cacheDir = File(context.cacheDir, "rname_staging").apply { mkdirs() }
            val rawFiles = mutableListOf<RnameRawFile>()
            val buf = ByteArray(32 * 1024)

            for ((idx, uri) in uris.withIndex()) {
                val name = getFileNameFromUri(context, uri) ?: "image_${System.currentTimeMillis()}_$idx.jpg"
                val localFile = File(cacheDir, name)
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
                        rawFiles.add(RnameRawFile(name = name, uri = Uri.fromFile(localFile), file = localFile))
                    }
                } catch (_: Throwable) {
                    rawFiles.add(RnameRawFile(name = name, uri = uri))
                }
            }

            allScannedFiles = rawFiles
            computeAnalysis("Selected Images (${rawFiles.size})")
        }
    }

    /**
     * Exact Sequence Continuation Algorithm from attached HTML:
     * - Sequence pattern: ^Prefix(\d+)\.[^.]+$ (case-insensitive)
     * - Numbered files sorted ascending by number
     * - Highest existing number = highest or 0
     * - Next number = highest + 1
     * - Unrenamed files sorted natural alphanumeric
     * - New names: Prefix + String(nextNumber++).padStart(padding, '0') + '.' + extension
     */
    private fun computeAnalysis(folderName: String) {
        val settings = SettingsManager.settings.value
        val prefix = settings.rnameSequencePrefix.ifBlank { "VHot" }
        val padding = settings.rnamePaddingDigits.coerceIn(2, 8)
        val pattern = Regex("^${Regex.escape(prefix)}(\\d+)\\.[^.]+$", RegexOption.IGNORE_CASE)

        val numbered = mutableListOf<Pair<RnameRawFile, Int>>()
        val unrenamed = mutableListOf<RnameRawFile>()

        for (file in allScannedFiles) {
            val match = pattern.find(file.name)
            if (match != null) {
                val num = match.groupValues[1].toIntOrNull() ?: 0
                numbered.add(Pair(file, num))
            } else {
                unrenamed.add(file)
            }
        }

        numbered.sortBy { it.second }
        val highest = numbered.lastOrNull()?.second ?: 0
        var nextNumber = highest + 1

        // Natural alphanumeric sort for unrenamed files
        unrenamed.sortWith(compareBy(NaturalOrderComparator) { it.name })

        val previewItems = mutableListOf<RnameItem>()
        for (file in unrenamed) {
            val ext = file.name.substringAfterLast(".", "jpg")
            val formattedNum = nextNumber.toString().padStart(padding, '0')
            val newName = "$prefix$formattedNum.$ext"
            previewItems.add(
                RnameItem(
                    originalName = file.name,
                    newName = newName,
                    isNumbered = false,
                    uri = file.uri,
                    file = file.file,
                    documentFile = file.documentFile
                )
            )
            nextNumber++
        }

        // Detect missing numbers in sequence 1..highest
        val existingSet = numbered.map { it.second }.toSet()
        val missing = mutableListOf<Int>()
        for (i in 1..highest) {
            if (i !in existingSet) missing.add(i)
        }

        val folderInfo = RnameFolderInfo(
            folderName = folderName,
            sequenceRange = if (highest > 0) {
                val endNum = (nextNumber - 1).coerceAtLeast(highest)
                "${prefix}0001 → $prefix${endNum.toString().padStart(padding, '0')}"
            } else {
                "${prefix}0001 → ${prefix}XXXX"
            },
            highestNumber = if (highest > 0) "$prefix${highest.toString().padStart(padding, '0')}" else "None",
            missingNumbers = if (missing.isEmpty()) "None" else missing.take(10).joinToString(", ") { "$prefix${it.toString().padStart(padding, '0')}" } + (if (missing.size > 10) " (+${missing.size - 10} more)" else "")
        )

        val nextFormatted = (highest + 1).toString().padStart(padding, '0')

        _uiState.value = _uiState.value.copy(
            folderName = folderName,
            totalCount = allScannedFiles.size,
            numberedCount = numbered.size,
            unrenamedCount = unrenamed.size,
            nextNumberStr = nextFormatted,
            folderInfo = folderInfo,
            previewItems = previewItems,
            isLoading = false,
            renameStatus = if (previewItems.isNotEmpty()) "Found ${previewItems.size} image(s) to rename" else "All images are already in sequence."
        )
    }

    fun onRenameClicked() {
        if (_uiState.value.previewItems.isEmpty()) return
        if (SettingsManager.settings.value.rnameConfirmBeforeRename) {
            _uiState.value = _uiState.value.copy(showConfirmDialog = true)
        } else {
            performRename()
        }
    }

    fun dismissConfirmDialog() {
        _uiState.value = _uiState.value.copy(showConfirmDialog = false)
    }

    fun confirmRename() {
        _uiState.value = _uiState.value.copy(showConfirmDialog = false)
        performRename()
    }

    private fun performRename() {
        val itemsToRename = _uiState.value.previewItems
        if (itemsToRename.isEmpty()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isRenaming = true,
                renameProgress = 0f,
                renameStatus = "Renaming 0 / ${itemsToRename.size} images…"
            )

            var successCount = 0
            withContext(Dispatchers.IO) {
                for ((idx, item) in itemsToRename.withIndex()) {
                    var success = false
                    try {
                        if (item.documentFile != null && item.documentFile.exists()) {
                            success = item.documentFile.renameTo(item.newName)
                        } else if (item.file != null && item.file.exists()) {
                            val target = File(item.file.parentFile, item.newName)
                            success = item.file.renameTo(target)
                        }
                    } catch (_: Throwable) {
                        success = false
                    }

                    if (success) successCount++

                    val progress = (idx + 1).toFloat() / itemsToRename.size.toFloat()
                    _uiState.value = _uiState.value.copy(
                        renameProgress = progress,
                        renameStatus = "Renamed ${idx + 1} / ${itemsToRename.size}: ${item.newName}"
                    )
                }
            }

            _uiState.value = _uiState.value.copy(
                isRenaming = false,
                renameProgress = 1f,
                renameStatus = "✓ Successfully renamed $successCount image(s)!"
            )

            // Re-scan folder to update stats
            val currentTree = _uiState.value.currentTreeUri
            if (currentTree != null) {
                onFolderSelected(currentTree)
            } else {
                loadDefaultSampleFolder()
            }
        }
    }

    fun clearStatus() {
        _uiState.value = _uiState.value.copy(renameStatus = null)
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

/**
 * Natural order string comparator for filenames like file1, file2, file10.
 */
object NaturalOrderComparator : Comparator<String> {
    override fun compare(a: String, b: String): Int {
        var ia = 0
        var ib = 0
        val nza = a.length
        val nzb = b.length

        while (ia < nza && ib < nzb) {
            val ca = a[ia]
            val cb = b[ib]

            if (ca.isDigit() && cb.isDigit()) {
                var startA = ia
                while (ia < nza && a[ia].isDigit()) ia++
                var startB = ib
                while (ib < nzb && b[ib].isDigit()) ib++

                val numA = a.substring(startA, ia).toLongOrNull() ?: 0L
                val numB = b.substring(startB, ib).toLongOrNull() ?: 0L

                if (numA != numB) {
                    return numA.compareTo(numB)
                }
            } else {
                val diff = ca.lowercaseChar().compareTo(cb.lowercaseChar())
                if (diff != 0) return diff
                ia++
                ib++
            }
        }
        return (nza - ia).compareTo(nzb - ib)
    }
}
