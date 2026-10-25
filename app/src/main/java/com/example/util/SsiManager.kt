package com.example.util

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets

data class SsiImage(
    val file: File,
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val lastModified: Long
)

object SsiManager {

    private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp", "avif")
    private const val ORDER_FILE_NAME = "ssi_order.json"

    private val _importedImages = MutableStateFlow<List<SsiImage>>(emptyList())
    val importedImages: StateFlow<List<SsiImage>> = _importedImages.asStateFlow()

    fun init(context: Context) {
        refresh(context)
    }

    private fun loadSavedOrder(context: Context): List<String> {
        return try {
            val orderFile = File(AppStorageHelper.getMediaImportedDir(context), ORDER_FILE_NAME)
            if (!orderFile.exists() || orderFile.length() == 0L) return emptyList()
            val text = orderFile.readText(StandardCharsets.UTF_8)
            val jsonArray = JSONArray(text)
            val list = mutableListOf<String>()
            for (i in 0 until jsonArray.length()) {
                list.add(jsonArray.getString(i))
            }
            list
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun saveOrder(context: Context, orderedNames: List<String>) {
        try {
            val dir = AppStorageHelper.getMediaImportedDir(context)
            if (!dir.exists()) dir.mkdirs()
            val orderFile = File(dir, ORDER_FILE_NAME)
            val jsonArray = JSONArray()
            orderedNames.forEach { jsonArray.put(it) }
            orderFile.writeText(jsonArray.toString(), StandardCharsets.UTF_8)
            MediaZipEncryptionHelper.syncToEncryptedArchiveAsync(context)
        } catch (_: Throwable) {}
    }

    fun refresh(context: Context) {
        try {
            val dir = AppStorageHelper.getMediaImportedDir(context)
            val rawFiles = dir.listFiles()?.filter { file ->
                file.isFile && file.length() > 0 && IMAGE_EXTENSIONS.contains(file.extension.lowercase())
            } ?: emptyList()

            val savedOrder = loadSavedOrder(context)
            val sortedFiles = if (savedOrder.isEmpty()) {
                rawFiles.sortedByDescending { it.lastModified() }
            } else {
                rawFiles.sortedWith { f1, f2 ->
                    val idx1 = savedOrder.indexOf(f1.name)
                    val idx2 = savedOrder.indexOf(f2.name)
                    when {
                        idx1 != -1 && idx2 != -1 -> idx1.compareTo(idx2)
                        idx1 != -1 -> -1 // existing ordered files keep priority
                        idx2 != -1 -> 1
                        else -> f2.lastModified().compareTo(f1.lastModified())
                    }
                }
            }

            val list = sortedFiles.map { file ->
                SsiImage(
                    file = file,
                    uri = Uri.fromFile(file),
                    name = file.name,
                    sizeBytes = file.length(),
                    lastModified = file.lastModified()
                )
            }
            _importedImages.value = list
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Moves an image from one position in the list to another and saves the order persistently.
     */
    fun moveImage(context: Context, fromIndex: Int, toIndex: Int): Boolean {
        val current = _importedImages.value.toMutableList()
        if (fromIndex in current.indices && toIndex in current.indices && fromIndex != toIndex) {
            val item = current.removeAt(fromIndex)
            current.add(toIndex, item)
            _importedImages.value = current
            saveOrder(context, current.map { it.name })
            return true
        }
        return false
    }

    /**
     * Swaps two images in the list and saves the order persistently.
     */
    fun swapImages(context: Context, index1: Int, index2: Int): Boolean {
        val current = _importedImages.value.toMutableList()
        if (index1 in current.indices && index2 in current.indices && index1 != index2) {
            val temp = current[index1]
            current[index1] = current[index2]
            current[index2] = temp
            _importedImages.value = current
            saveOrder(context, current.map { it.name })
            return true
        }
        return false
    }

    /**
     * Replaces the entire image list with a new order and persists it.
     */
    fun reorderImages(context: Context, newOrder: List<SsiImage>) {
        _importedImages.value = newOrder
        saveOrder(context, newOrder.map { it.name })
    }

    suspend fun importImage(
        context: Context,
        sourceUriString: String,
        preferredFileName: String,
        code: String = "",
        name: String = "",
        colour: String = ""
    ): Result<SsiImage> = withContext(Dispatchers.IO) {
        try {
            val dir = AppStorageHelper.getMediaImportedDir(context)
            if (!dir.exists()) dir.mkdirs()

            val srcUri = Uri.parse(sourceUriString)

            // Determine safe clean filename
            val rawName = when {
                preferredFileName.isNotBlank() -> preferredFileName
                code.isNotBlank() -> "$code.jpg"
                else -> "image_${System.currentTimeMillis()}.jpg"
            }
            val cleanExt = rawName.substringAfterLast('.', "jpg").ifEmpty { "jpg" }
            val cleanBase = rawName.substringBeforeLast('.').replace(Regex("[^a-zA-Z0-9._-]"), "_")

            var targetFile = File(dir, "$cleanBase.$cleanExt")
            if (targetFile.exists()) {
                targetFile = File(dir, "${cleanBase}_${System.currentTimeMillis()}.$cleanExt")
            }

            var stream: InputStream? = null
            try {
                if (srcUri.scheme == "file") {
                    val directFile = File(srcUri.path ?: "")
                    if (directFile.exists()) {
                        directFile.inputStream().use { input ->
                            FileOutputStream(targetFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    } else {
                        stream = context.contentResolver.openInputStream(srcUri)
                    }
                } else {
                    stream = context.contentResolver.openInputStream(srcUri)
                }

                stream?.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
            } finally {
                stream?.close()
            }

            if (!targetFile.exists() || targetFile.length() == 0L) {
                return@withContext Result.failure(Exception("Failed to save imported image to media_imported"))
            }

            refresh(context)
            MediaZipEncryptionHelper.syncToEncryptedArchiveAsync(context)

            val ssiImage = SsiImage(
                file = targetFile,
                uri = Uri.fromFile(targetFile),
                name = targetFile.name,
                sizeBytes = targetFile.length(),
                lastModified = targetFile.lastModified()
            )

            Result.success(ssiImage)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    fun clearAll(context: Context): Int {
        val dir = AppStorageHelper.getMediaImportedDir(context)
        var count = 0
        try {
            dir.listFiles()?.forEach { file ->
                if (file.isFile && file.delete()) {
                    count++
                }
            }
            File(dir, ORDER_FILE_NAME).delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        refresh(context)
        MediaZipEncryptionHelper.syncToEncryptedArchiveAsync(context)
        return count
    }

    fun deleteImage(context: Context, file: File): Boolean {
        val deleted = try {
            file.delete()
        } catch (e: Exception) {
            false
        }
        if (deleted) {
            val saved = loadSavedOrder(context).filter { it != file.name }
            saveOrder(context, saved)
        }
        refresh(context)
        if (deleted) {
            MediaZipEncryptionHelper.syncToEncryptedArchiveAsync(context)
        }
        return deleted
    }

    fun getTotalSizeBytes(context: Context): Long {
        val dir = AppStorageHelper.getMediaImportedDir(context)
        return dir.listFiles()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L
    }
}
