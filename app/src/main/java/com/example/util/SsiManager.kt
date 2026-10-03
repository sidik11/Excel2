package com.example.util

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

data class SsiImage(
    val file: File,
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val lastModified: Long
)

object SsiManager {

    private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp", "avif")

    private val _importedImages = MutableStateFlow<List<SsiImage>>(emptyList())
    val importedImages: StateFlow<List<SsiImage>> = _importedImages.asStateFlow()

    fun init(context: Context) {
        refresh(context)
    }

    fun refresh(context: Context) {
        try {
            val dir = AppStorageHelper.getMediaImportedDir(context)
            val files = dir.listFiles()?.filter { file ->
                file.isFile && file.length() > 0 && IMAGE_EXTENSIONS.contains(file.extension.lowercase())
            }?.sortedByDescending { it.lastModified() } ?: emptyList()

            val list = files.map { file ->
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
        } catch (e: Exception) {
            e.printStackTrace()
        }
        refresh(context)
        return count
    }

    fun deleteImage(context: Context, file: File): Boolean {
        val deleted = try {
            file.delete()
        } catch (e: Exception) {
            false
        }
        refresh(context)
        return deleted
    }

    fun getTotalSizeBytes(context: Context): Long {
        val dir = AppStorageHelper.getMediaImportedDir(context)
        return dir.listFiles()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L
    }
}
