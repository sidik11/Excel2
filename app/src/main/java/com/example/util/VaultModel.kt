package com.example.util

import android.net.Uri
import java.io.File
import java.util.UUID

enum class VaultSortMode {
    NAME_ASC,
    DATE_DESC,
    SIZE_DESC
}

data class VaultImage(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val file: File,
    val sizeBytes: Long,
    val folderId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val mimeType: String = "image/jpeg",
    val thumbnailFile: File? = null,
    val docUri: Uri? = null
)

data class VaultContainerInfo(
    val fileName: String,
    val file: File,
    val uri: Uri,
    val sizeBytes: Long,
    val capacityBytes: Long = ImgVaultEngine.CAPACITY_5GB
)
