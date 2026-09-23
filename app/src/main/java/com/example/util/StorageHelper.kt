package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

data class ScannedImage(
    val fileName: String,
    val fileUri: String,
    val relativePath: String,
    val normalizedCode: String,
    val code: String,
    val lastModified: Long
)

object StorageHelper {
    private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp", "avif")

    /**
     * Takes persistable URI permission so access survives app restarts and reboots.
     */
    fun takePersistablePermission(context: Context, uri: Uri): Boolean {
        return try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
            true
        } catch (e: Exception) {
            try {
                val readFlag = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, readFlag)
                true
            } catch (e2: Exception) {
                false
            }
        }
    }

    /**
     * Checks if the given URI has persisted read permission.
     */
    fun hasPersistedPermission(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.persistedUriPermissions.any {
                it.uri == uri && it.isReadPermission
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Scans an image tree folder selected by the user via ActivityResultContracts.OpenDocumentTree.
     * Recursively walks subdirectories up to maxDepth.
     */
    fun scanTreeUri(
        context: Context,
        treeUri: Uri,
        onProgress: (scannedCount: Int, foundImages: Int) -> Unit = { _, _ -> }
    ): List<ScannedImage> {
        val results = mutableListOf<ScannedImage>()
        var scanned = 0
        var found = 0

        fun walk(parentDocId: String, currentPath: String, depth: Int) {
            if (depth > 5) return

            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                treeUri,
                parentDocId
            )

            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
            )

            try {
                context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    val dateCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                    while (cursor.moveToNext()) {
                        scanned++
                        val docId = cursor.getString(idCol)
                        val name = cursor.getString(nameCol) ?: continue
                        val mime = cursor.getString(mimeCol) ?: ""
                        val lastMod = cursor.getLong(dateCol)

                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            walk(docId, "$currentPath$name/", depth + 1)
                        } else {
                            val ext = name.substringAfterLast('.', "").lowercase()
                            if (ext in IMAGE_EXTENSIONS || mime.startsWith("image/")) {
                                val stem = name.substringBeforeLast('.')
                                val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)

                                results.add(
                                    ScannedImage(
                                        fileName = name,
                                        fileUri = docUri.toString(),
                                        relativePath = "$currentPath$name",
                                        normalizedCode = ExcelParser.normalizeCode(stem),
                                        code = stem,
                                        lastModified = lastMod
                                    )
                                )
                                found++
                            }
                        }

                        if (scanned % 50 == 0) {
                            onProgress(scanned, found)
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore inaccessible subdirectories gracefully
            }
        }

        try {
            val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
            walk(rootDocId, "", 0)
        } catch (e: Exception) {
            // Fallback for document ID retrieval
        }

        return results
    }

    /**
     * Generates sample images into the app internal storage directory so that the user
     * can immediately experience the full app with matching codes and colors.
     */
    fun createSampleImages(context: Context): List<ScannedImage> {
        val sampleItems = ExcelParser.getSampleCatalog()
        val sampleDir = File(context.filesDir, "sample_images")
        if (!sampleDir.exists()) {
            sampleDir.mkdirs()
        }

        val colorHexMap = mapOf(
            "NAVY BLUE" to 0xFF1E3A8A.toInt(),
            "APRICOT/YELLOW" to 0xFFF59E0B.toInt(),
            "ROSE PINK" to 0xFFEC4899.toInt(),
            "EMERALD GREEN" to 0xFF10B981.toInt(),
            "BLACK" to 0xFF18181B.toInt(),
            "BURGUNDY" to 0xFF831843.toInt(),
            "WHITE/GOLD" to 0xFFD97706.toInt(),
            "LAVENDER" to 0xFF8B5CF6.toInt(),
            "SKY BLUE" to 0xFF0284C7.toInt(),
            "ROYAL BLUE" to 0xFF2563EB.toInt(),
            "PEACH" to 0xFFFB923C.toInt(),
            "CORAL" to 0xFFF43F5E.toInt(),
            "MINT GREEN" to 0xFF14B8A6.toInt()
        )

        val scannedList = mutableListOf<ScannedImage>()

        for (item in sampleItems) {
            val fileName = "${item.code}.jpg"
            val file = File(sampleDir, fileName)

            if (!file.exists()) {
                val bitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)

                val bgColor = colorHexMap[item.colour] ?: 0xFF3B82F6.toInt()
                canvas.drawColor(bgColor)

                // Draw decorative card background inside image
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                paint.color = 0x33FFFFFF
                canvas.drawRoundRect(RectF(24f, 24f, 376f, 376f), 24f, 24f, paint)

                // Inner frame
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 4f
                paint.color = 0x66FFFFFF
                canvas.drawRoundRect(RectF(36f, 36f, 364f, 364f), 16f, 16f, paint)

                // Code text
                paint.style = Paint.Style.FILL
                paint.color = Color.WHITE
                paint.textSize = 38f
                paint.isFakeBoldText = true
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText(item.code, 200f, 190f, paint)

                // Name text
                paint.textSize = 24f
                paint.color = 0xEEFFFFFF.toInt()
                canvas.drawText(item.name, 200f, 240f, paint)

                // Colour text
                paint.textSize = 18f
                paint.color = 0xBBFFFFFF.toInt()
                canvas.drawText(item.colour, 200f, 280f, paint)

                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
                bitmap.recycle()
            }

            val fileUri = Uri.fromFile(file).toString()
            scannedList.add(
                ScannedImage(
                    fileName = fileName,
                    fileUri = fileUri,
                    relativePath = "sample_images/$fileName",
                    normalizedCode = ExcelParser.normalizeCode(item.code),
                    code = item.code,
                    lastModified = file.lastModified()
                )
            )
        }

        return scannedList
    }
}
