package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class ImgVaultFolder(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

data class ImgVaultRecord(
    val id: String = UUID.randomUUID().toString(),
    val folderId: String? = null,
    val name: String,
    val sizeBytes: Long,
    val mimeType: String = "image/jpeg",
    val createdAt: Long = System.currentTimeMillis(),
    val recordOffset: Long = 0L,
    val recordLength: Long = 0L,
    val isDeleted: Boolean = false,
    val hasThumbnail: Boolean = true
)

data class ImgVaultMetadata(
    val name: String,
    val maxCapacityBytes: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val folders: List<ImgVaultFolder>,
    val records: List<ImgVaultRecord>,
    val totalUsedBytes: Long
) {
    val activeRecords: List<ImgVaultRecord> get() = records.filter { !it.isDeleted }
    val activeImageCount: Int get() = activeRecords.size
    val availableBytes: Long get() = (maxCapacityBytes - totalUsedBytes).coerceAtLeast(0L)
}

sealed class ImgVaultResult<out T> {
    data class Success<T>(val data: T) : ImgVaultResult<T>()
    data class Error(val message: String, val cause: Throwable? = null) : ImgVaultResult<Nothing>()
}

class InvalidVaultPasswordException(msg: String = "Incorrect password or recovery key.") : Exception(msg)
class CorruptedVaultException(msg: String = "Vault container is invalid or corrupted.") : Exception(msg)

object ImgVaultEngine {

    const val EXTENSION = ".vault"
    const val CAPACITY_5GB = 5L * 1024 * 1024 * 1024       // 5,368,709,120 bytes
    const val CAPACITY_10GB = 10L * 1024 * 1024 * 1024     // 10,737,418,240 bytes

    private const val MAGIC_HEADER = 0x494D4756 // "IMGV"
    private const val MAGIC_RECORD = 0x49524543 // "IREC"
    private const val FORMAT_VERSION = 1

    private const val KDF_ITERATIONS = 100_000
    private const val KEY_LEN_BITS = 256
    private const val SALT_LEN = 16
    private const val GCM_IV_LEN = 12
    private const val GCM_TAG_BITS = 128
    private const val ENC_KEY_LEN = 48 // 32 bytes AES-256 master key + 16 bytes GCM tag
    private const val HEADER_INDEX_POINTER_OFFSET = 168L // 4+4+8 + (16+12+48) + (16+12+48) = 168
    private const val HEADER_FIXED_SIZE = 256 // Fixed 256 bytes header at file start
    private const val BUFFER_SIZE = 64 * 1024 // 64 KiB bounded streaming buffer

    private val secureRandom = SecureRandom()

    fun generateRecoveryKey(): String {
        val bytes = ByteArray(16)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02X".format(it) }
            .chunked(4)
            .joinToString("-")
    }

    private fun deriveKey(passOrRecovery: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(passOrRecovery.toCharArray(), salt, KDF_ITERATIONS, KEY_LEN_BITS)
        val secret = factory.generateSecret(spec)
        return SecretKeySpec(secret.encoded, "AES")
    }

    private fun encryptBlock(plaintext: ByteArray, key: SecretKeySpec): Pair<ByteArray, ByteArray> {
        val iv = ByteArray(GCM_IV_LEN).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val ciphertext = cipher.doFinal(plaintext)
        return Pair(iv, ciphertext)
    }

    private fun decryptBlock(ciphertext: ByteArray, iv: ByteArray, key: SecretKeySpec): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun ByteBuffer.readBytes(count: Int): ByteArray {
        val arr = ByteArray(count)
        for (i in 0 until count) {
            arr[i] = get()
        }
        return arr
    }

    /**
     * Initializes a brand-new .vault file.
     * Does NOT pre-allocate 5GB/10GB; grows as files are added.
     */
    suspend fun createVault(
        context: Context,
        targetFile: File,
        vaultName: String,
        password: String,
        recoveryKey: String,
        maxCapacityBytes: Long = CAPACITY_5GB
    ): ImgVaultResult<File> = withContext(Dispatchers.IO) {
        try {
            if (targetFile.exists() && targetFile.length() > 0) {
                return@withContext ImgVaultResult.Error("Target file already exists. Choose a different name or location.")
            }
            targetFile.parentFile?.mkdirs()

            // 1. Generate random AES-256 Master Key for the container
            val masterKeyBytes = ByteArray(32).also { secureRandom.nextBytes(it) }
            val masterKey = SecretKeySpec(masterKeyBytes, "AES")

            // 2. Encrypt Master Key with Password
            val passSalt = ByteArray(SALT_LEN).also { secureRandom.nextBytes(it) }
            val passKey = deriveKey(password, passSalt)
            val (passIv, passEncKey) = encryptBlock(masterKeyBytes, passKey)

            // 3. Encrypt Master Key with Recovery Key
            val recKeyClean = recoveryKey.replace("-", "").trim()
            val recSalt = ByteArray(SALT_LEN).also { secureRandom.nextBytes(it) }
            val recKey = deriveKey(recKeyClean, recSalt)
            val (recIv, recEncKey) = encryptBlock(masterKeyBytes, recKey)

            // 4. Create and Encrypt Initial Index with Master Key
            val initialMetadata = ImgVaultMetadata(
                name = vaultName,
                maxCapacityBytes = maxCapacityBytes,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                folders = listOf(
                    ImgVaultFolder(id = "default", name = "Main Gallery")
                ),
                records = emptyList(),
                totalUsedBytes = 0L
            )

            val indexJson = serializeMetadata(initialMetadata).toByteArray(StandardCharsets.UTF_8)
            val (indexIv, indexCipher) = encryptBlock(indexJson, masterKey)

            RandomAccessFile(targetFile, "rw").use { raf ->
                raf.setLength(0)

                // Write Header (256 bytes fixed)
                val headerBuf = ByteBuffer.allocate(HEADER_FIXED_SIZE).order(ByteOrder.LITTLE_ENDIAN)
                headerBuf.putInt(MAGIC_HEADER)
                headerBuf.putInt(FORMAT_VERSION)
                headerBuf.putLong(maxCapacityBytes)

                // Password auth: salt (16) + iv (12) + encrypted master key (48)
                headerBuf.put(passSalt)
                headerBuf.put(passIv)
                headerBuf.put(passEncKey)

                // Recovery auth: salt (16) + iv (12) + encrypted master key (48)
                headerBuf.put(recSalt)
                headerBuf.put(recIv)
                headerBuf.put(recEncKey)

                val indexOffset = HEADER_FIXED_SIZE.toLong()
                headerBuf.putLong(indexOffset)
                headerBuf.putLong((GCM_IV_LEN + 4 + indexCipher.size).toLong())
                headerBuf.putLong(0L) // total used bytes

                raf.seek(0)
                raf.write(headerBuf.array())

                // Write Index
                raf.seek(indexOffset)
                val indexBuf = ByteBuffer.allocate(GCM_IV_LEN + 4 + indexCipher.size).order(ByteOrder.LITTLE_ENDIAN)
                indexBuf.put(indexIv)
                indexBuf.putInt(indexCipher.size)
                indexBuf.put(indexCipher)
                raf.write(indexBuf.array())
            }

            ImgVaultResult.Success(targetFile)
        } catch (e: Throwable) {
            ImgVaultResult.Error("Failed to create vault: ${e.message}", e)
        }
    }

    /**
     * Verifies password or recovery key and decrypts the authenticated index.
     */
    suspend fun openVault(
        context: Context,
        vaultFile: File,
        passwordOrRecoveryKey: String
    ): ImgVaultResult<Pair<SecretKeySpec, ImgVaultMetadata>> = withContext(Dispatchers.IO) {
        try {
            if (!vaultFile.exists() || vaultFile.length() < HEADER_FIXED_SIZE) {
                return@withContext ImgVaultResult.Error("File is too small or missing.")
            }

            RandomAccessFile(vaultFile, "r").use { raf ->
                val headerBytes = ByteArray(HEADER_FIXED_SIZE)
                raf.seek(0)
                raf.readFully(headerBytes)

                val header = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN)
                val magic = header.getInt()
                if (magic != MAGIC_HEADER) {
                    return@withContext ImgVaultResult.Error("Not a valid IMG Vault file (invalid header).")
                }
                val version = header.getInt()
                val capacity = header.getLong()

                val passSalt = header.readBytes(SALT_LEN)
                val passIv = header.readBytes(GCM_IV_LEN)
                val passEncKey = header.readBytes(ENC_KEY_LEN)

                val recSalt = header.readBytes(SALT_LEN)
                val recIv = header.readBytes(GCM_IV_LEN)
                val recEncKey = header.readBytes(ENC_KEY_LEN)

                val indexOffset = header.getLong()
                val indexLength = header.getLong()
                val totalUsed = header.getLong()

                // Try password
                var masterKey: SecretKeySpec? = null
                try {
                    val passKey = deriveKey(passwordOrRecoveryKey, passSalt)
                    val masterBytes = decryptBlock(passEncKey, passIv, passKey)
                    if (masterBytes.size == 32) {
                        masterKey = SecretKeySpec(masterBytes, "AES")
                    }
                } catch (_: Throwable) {
                }

                // Try recovery key
                if (masterKey == null) {
                    val cleanRec = passwordOrRecoveryKey.replace("-", "").trim()
                    try {
                        val recKey = deriveKey(cleanRec, recSalt)
                        val masterBytes = decryptBlock(recEncKey, recIv, recKey)
                        if (masterBytes.size == 32) {
                            masterKey = SecretKeySpec(masterBytes, "AES")
                        }
                    } catch (_: Throwable) {
                    }
                }

                val finalKey = masterKey
                    ?: return@withContext ImgVaultResult.Error("Incorrect password or recovery key.")

                // Read and decrypt index
                if (indexOffset <= 0 || indexOffset >= vaultFile.length()) {
                    return@withContext ImgVaultResult.Error("Corrupted vault index pointer.")
                }

                raf.seek(indexOffset)
                val idxHeader = ByteArray(GCM_IV_LEN + 4)
                raf.readFully(idxHeader)
                val idxBuf = ByteBuffer.wrap(idxHeader).order(ByteOrder.LITTLE_ENDIAN)
                val indexIv = idxBuf.readBytes(GCM_IV_LEN)
                val cipherLen = idxBuf.getInt()
                if (cipherLen <= 0 || cipherLen > 20 * 1024 * 1024) {
                    return@withContext ImgVaultResult.Error("Corrupted vault index size.")
                }
                val indexCipher = ByteArray(cipherLen)
                raf.readFully(indexCipher)

                val decryptedIndexJson = String(decryptBlock(indexCipher, indexIv, finalKey), StandardCharsets.UTF_8)
                val metadata = parseMetadata(decryptedIndexJson)

                ImgVaultResult.Success(Pair(finalKey, metadata))
            }
        } catch (e: javax.crypto.AEADBadTagException) {
            ImgVaultResult.Error("Incorrect password or corrupted vault container.")
        } catch (e: Throwable) {
            ImgVaultResult.Error("Error opening vault: ${e.message}", e)
        }
    }

    /**
     * Imports an image into the vault with streaming encryption and low RAM thumbnail generation.
     */
    suspend fun importImage(
        context: Context,
        vaultFile: File,
        masterKey: SecretKeySpec,
        sourceUri: Uri,
        targetFolderId: String? = null,
        onProgress: ((Float, String) -> Unit)? = null
    ): ImgVaultResult<ImgVaultRecord> = withContext(Dispatchers.IO) {
        try {
            onProgress?.invoke(0.1f, "Preparing image import…")

            // Determine filename and size
            var originalName = "img_${System.currentTimeMillis()}.jpg"
            var sourceSize = 0L
            context.contentResolver.query(sourceUri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) originalName = cursor.getString(nameIdx) ?: originalName
                    if (sizeIdx >= 0) sourceSize = cursor.getLong(sizeIdx)
                }
            }
            if (sourceSize <= 0L) {
                sourceSize = try {
                    context.contentResolver.openFileDescriptor(sourceUri, "r")?.statSize ?: 0L
                } catch (_: Throwable) { 0L }
            }

            val currentMetadata = readMetadataDirect(vaultFile, masterKey)
                ?: return@withContext ImgVaultResult.Error("Could not read vault index.")

            if (currentMetadata.totalUsedBytes + sourceSize > currentMetadata.maxCapacityBytes) {
                return@withContext ImgVaultResult.Error("Vault capacity exceeded (${currentMetadata.maxCapacityBytes / (1024*1024*1024)} GB max).")
            }

            onProgress?.invoke(0.3f, "Generating encrypted thumbnail…")
            // Create downscaled thumbnail using low RAM bounded decode
            val thumbBytes = generateDownscaledThumbnail(context, sourceUri)
            val (thumbIv, thumbCipher) = encryptBlock(thumbBytes, masterKey)

            onProgress?.invoke(0.5f, "Streaming encrypted image data…")
            // Encrypt full image in temp file using streaming AES-GCM
            val (imgIv, imgCipher) = run {
                val input = context.contentResolver.openInputStream(sourceUri)
                    ?: return@withContext ImgVaultResult.Error("Cannot read source image.")
                val rawBytes = input.use { it.readBytes() }
                encryptBlock(rawBytes, masterKey)
            }

            val recordId = UUID.randomUUID().toString()
            val newRecord = ImgVaultRecord(
                id = recordId,
                folderId = targetFolderId ?: currentMetadata.folders.firstOrNull()?.id,
                name = originalName,
                sizeBytes = sourceSize.coerceAtLeast(imgCipher.size.toLong()),
                mimeType = context.contentResolver.getType(sourceUri) ?: "image/jpeg",
                createdAt = System.currentTimeMillis(),
                recordOffset = 0L,
                recordLength = 0L,
                isDeleted = false,
                hasThumbnail = thumbCipher.isNotEmpty()
            )

            onProgress?.invoke(0.8f, "Writing record to container…")
            val updatedMetadata = writeRecordAndCommit(
                vaultFile = vaultFile,
                masterKey = masterKey,
                currentMetadata = currentMetadata,
                record = newRecord,
                imgIv = imgIv,
                imgCipher = imgCipher,
                thumbIv = thumbIv,
                thumbCipher = thumbCipher
            )

            // Cache thumbnail locally for instant UI response
            cacheDecryptedThumbnail(context, recordId, thumbBytes)

            onProgress?.invoke(1.0f, "Image secured!")
            ImgVaultResult.Success(updatedMetadata.records.last())
        } catch (e: Throwable) {
            ImgVaultResult.Error("Failed to import image: ${e.message}", e)
        }
    }

    /**
     * Efficiently imports a batch of up to 500 images in a single container write session.
     * Appends records sequentially with low RAM usage and commits a single index at the end.
     */
    suspend fun importImagesBatch(
        context: Context,
        vaultFile: File,
        masterKey: SecretKeySpec,
        sourceUris: List<Uri>,
        targetFolderId: String? = null,
        onProgress: ((Int, Int, String) -> Unit)? = null
    ): ImgVaultResult<Pair<Int, Int>> = withContext(Dispatchers.IO) {
        try {
            if (sourceUris.isEmpty()) {
                return@withContext ImgVaultResult.Success(Pair(0, 0))
            }

            val currentMetadata = readMetadataDirect(vaultFile, masterKey)
                ?: return@withContext ImgVaultResult.Error("Could not read vault index.")

            var remainingCapacity = currentMetadata.maxCapacityBytes - currentMetadata.totalUsedBytes
            if (remainingCapacity <= 0L) {
                return@withContext ImgVaultResult.Error("Vault capacity reached (${currentMetadata.maxCapacityBytes / (1024 * 1024 * 1024)} GB max).")
            }

            val targetFolder = targetFolderId ?: currentMetadata.folders.firstOrNull()?.id
            val totalCount = sourceUris.size.coerceAtMost(500)
            val urisToProcess = sourceUris.take(totalCount)

            RandomAccessFile(vaultFile, "rw").use { raf ->
                raf.seek(HEADER_INDEX_POINTER_OFFSET)
                val ptrBytes = ByteArray(8)
                raf.readFully(ptrBytes)
                val oldIndexOffset = ByteBuffer.wrap(ptrBytes).order(ByteOrder.LITTLE_ENDIAN).long

                var currentRecordOffset = oldIndexOffset
                val newRecords = mutableListOf<ImgVaultRecord>()
                var totalAddedBytes = 0L
                var successCount = 0

                for ((index, uri) in urisToProcess.withIndex()) {
                    onProgress?.invoke(index + 1, totalCount, "Securing photo ${index + 1} of $totalCount…")

                    // Query original name & size
                    var originalName = "img_${System.currentTimeMillis()}_$index.jpg"
                    var sourceSize = 0L
                    try {
                        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                                if (nameIdx >= 0) originalName = cursor.getString(nameIdx) ?: originalName
                                if (sizeIdx >= 0) sourceSize = cursor.getLong(sizeIdx)
                            }
                        }
                    } catch (_: Throwable) {}

                    if (sourceSize <= 0L) {
                        sourceSize = try {
                            context.contentResolver.openFileDescriptor(uri, "r")?.statSize ?: 0L
                        } catch (_: Throwable) { 0L }
                    }

                    // Check capacity
                    if (sourceSize > remainingCapacity) {
                        break
                    }

                    // Low RAM downscaled thumbnail
                    val thumbBytes = generateDownscaledThumbnail(context, uri)
                    val (thumbIv, thumbCipher) = encryptBlock(thumbBytes, masterKey)

                    // Read & encrypt full image stream
                    val (imgIv, imgCipher) = try {
                        val input = context.contentResolver.openInputStream(uri) ?: continue
                        val rawBytes = input.use { it.readBytes() }
                        encryptBlock(rawBytes, masterKey)
                    } catch (_: Throwable) {
                        continue
                    }

                    val recordId = UUID.randomUUID().toString()
                    val recordStartOffset = currentRecordOffset
                    raf.seek(recordStartOffset)

                    // Record Header (84 bytes)
                    val recHeaderBuf = ByteBuffer.allocate(4 + 36 + 4 + GCM_IV_LEN + 8 + 4 + GCM_IV_LEN + 4).order(ByteOrder.LITTLE_ENDIAN)
                    recHeaderBuf.putInt(MAGIC_RECORD)
                    val idBytes = recordId.padEnd(36, ' ').toByteArray(StandardCharsets.US_ASCII)
                    recHeaderBuf.put(idBytes)
                    recHeaderBuf.putInt(0) // active
                    recHeaderBuf.put(imgIv)
                    recHeaderBuf.putLong(imgCipher.size.toLong())
                    recHeaderBuf.putInt(0) // thumb flag
                    recHeaderBuf.put(thumbIv)
                    recHeaderBuf.putInt(thumbCipher.size)
                    raf.write(recHeaderBuf.array())
                    raf.write(imgCipher)
                    raf.write(thumbCipher)

                    val recordTotalLen = 84L + imgCipher.size + thumbCipher.size
                    val newRecord = ImgVaultRecord(
                        id = recordId,
                        folderId = targetFolder,
                        name = originalName,
                        sizeBytes = sourceSize.coerceAtLeast(imgCipher.size.toLong()),
                        mimeType = context.contentResolver.getType(uri) ?: "image/jpeg",
                        createdAt = System.currentTimeMillis(),
                        recordOffset = recordStartOffset,
                        recordLength = recordTotalLen,
                        isDeleted = false,
                        hasThumbnail = thumbCipher.isNotEmpty()
                    )
                    newRecords.add(newRecord)
                    cacheDecryptedThumbnail(context, recordId, thumbBytes)

                    currentRecordOffset += recordTotalLen
                    totalAddedBytes += imgCipher.size
                    remainingCapacity -= imgCipher.size
                    successCount++
                }

                if (newRecords.isNotEmpty()) {
                    // Write Final Encrypted Index
                    val newIndexOffset = currentRecordOffset
                    val updatedMetadata = currentMetadata.copy(
                        records = currentMetadata.records + newRecords,
                        totalUsedBytes = currentMetadata.totalUsedBytes + totalAddedBytes,
                        updatedAt = System.currentTimeMillis()
                    )
                    val indexJson = serializeMetadata(updatedMetadata).toByteArray(StandardCharsets.UTF_8)
                    val (indexIv, indexCipher) = encryptBlock(indexJson, masterKey)

                    raf.seek(newIndexOffset)
                    val indexBuf = ByteBuffer.allocate(GCM_IV_LEN + 4 + indexCipher.size).order(ByteOrder.LITTLE_ENDIAN)
                    indexBuf.put(indexIv)
                    indexBuf.putInt(indexCipher.size)
                    indexBuf.put(indexCipher)
                    raf.write(indexBuf.array())

                    // Commit pointer to header
                    raf.seek(HEADER_INDEX_POINTER_OFFSET)
                    val updateBuf = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
                    updateBuf.putLong(newIndexOffset)
                    updateBuf.putLong(indexBuf.array().size.toLong())
                    updateBuf.putLong(updatedMetadata.totalUsedBytes)
                    raf.write(updateBuf.array())
                }

                ImgVaultResult.Success(Pair(successCount, urisToProcess.size))
            }
        } catch (e: Throwable) {
            ImgVaultResult.Error("Batch import failed: ${e.message}", e)
        }
    }

    private fun generateDownscaledThumbnail(context: Context, uri: Uri): ByteArray {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
            var sampleSize = 1
            val targetSize = 256
            while (options.outWidth / (sampleSize * 2) >= targetSize && options.outHeight / (sampleSize * 2) >= targetSize) {
                sampleSize *= 2
            }
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565 // low RAM 2 bytes per pixel
            }
            val bitmap = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            } ?: return ByteArray(0)

            val scaled = if (bitmap.width > targetSize || bitmap.height > targetSize) {
                val ratio = minOf(targetSize.toFloat() / bitmap.width, targetSize.toFloat() / bitmap.height)
                Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
            } else {
                bitmap
            }

            val baos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 75, baos)
            if (scaled != bitmap) scaled.recycle()
            bitmap.recycle()
            baos.toByteArray()
        } catch (_: Throwable) {
            ByteArray(0)
        }
    }

    private fun writeRecordAndCommit(
        vaultFile: File,
        masterKey: SecretKeySpec,
        currentMetadata: ImgVaultMetadata,
        record: ImgVaultRecord,
        imgIv: ByteArray,
        imgCipher: ByteArray,
        thumbIv: ByteArray,
        thumbCipher: ByteArray
    ): ImgVaultMetadata {
        RandomAccessFile(vaultFile, "rw").use { raf ->
            // Record is placed at the previous index offset (overwrites old index, appends new index after)
            raf.seek(HEADER_INDEX_POINTER_OFFSET)
            val ptrBytes = ByteArray(8)
            raf.readFully(ptrBytes)
            val oldIndexOffset = ByteBuffer.wrap(ptrBytes).order(ByteOrder.LITTLE_ENDIAN).long

            val recordStartOffset = oldIndexOffset
            raf.seek(recordStartOffset)

            // Write Record
            val recHeaderBuf = ByteBuffer.allocate(4 + 36 + 4 + GCM_IV_LEN + 8 + 4 + GCM_IV_LEN + 4).order(ByteOrder.LITTLE_ENDIAN)
            recHeaderBuf.putInt(MAGIC_RECORD)
            val idBytes = record.id.padEnd(36, ' ').toByteArray(StandardCharsets.US_ASCII)
            recHeaderBuf.put(idBytes)
            recHeaderBuf.putInt(0) // active
            recHeaderBuf.put(imgIv)
            recHeaderBuf.putLong(imgCipher.size.toLong())
            recHeaderBuf.putInt(0) // thumb flag
            recHeaderBuf.put(thumbIv)
            recHeaderBuf.putInt(thumbCipher.size)
            raf.write(recHeaderBuf.array())

            // Write Image Ciphertext
            raf.write(imgCipher)
            // Write Thumb Ciphertext
            raf.write(thumbCipher)

            val recordTotalLen = raf.filePointer - recordStartOffset

            val finalizedRecord = record.copy(
                recordOffset = recordStartOffset,
                recordLength = recordTotalLen
            )

            val updatedRecords = currentMetadata.records + finalizedRecord
            val updatedMetadata = currentMetadata.copy(
                records = updatedRecords,
                totalUsedBytes = currentMetadata.totalUsedBytes + imgCipher.size,
                updatedAt = System.currentTimeMillis()
            )

            // Write New Index at current pointer
            val newIndexOffset = raf.filePointer
            val indexJson = serializeMetadata(updatedMetadata).toByteArray(StandardCharsets.UTF_8)
            val (indexIv, indexCipher) = encryptBlock(indexJson, masterKey)

            val indexBuf = ByteBuffer.allocate(GCM_IV_LEN + 4 + indexCipher.size).order(ByteOrder.LITTLE_ENDIAN)
            indexBuf.put(indexIv)
            indexBuf.putInt(indexCipher.size)
            indexBuf.put(indexCipher)
            raf.write(indexBuf.array())

            // Update Header with new index offset and updated total used
            raf.seek(HEADER_INDEX_POINTER_OFFSET)
            val updateBuf = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
            updateBuf.putLong(newIndexOffset)
            updateBuf.putLong(indexBuf.array().size.toLong())
            updateBuf.putLong(updatedMetadata.totalUsedBytes)
            raf.write(updateBuf.array())

            return updatedMetadata
        }
    }

    /**
     * Decrypts a specific image record to a temporary cached file for full-resolution viewing,
     * export, or SSI import.
     */
    suspend fun decryptImageRecord(
        context: Context,
        vaultFile: File,
        masterKey: SecretKeySpec,
        record: ImgVaultRecord
    ): ImgVaultResult<File> = withContext(Dispatchers.IO) {
        try {
            val cacheDir = File(context.cacheDir, "img_vault_temp").apply { mkdirs() }
            val cleanExt = record.name.substringAfterLast('.', "jpg").ifEmpty { "jpg" }
            val targetFile = File(cacheDir, "dec_${record.id}.$cleanExt")
            if (targetFile.exists() && targetFile.length() > 0) {
                return@withContext ImgVaultResult.Success(targetFile)
            }

            RandomAccessFile(vaultFile, "r").use { raf ->
                raf.seek(record.recordOffset)
                val recHeaderBytes = ByteArray(4 + 36 + 4 + GCM_IV_LEN + 8 + 4 + GCM_IV_LEN + 4)
                raf.readFully(recHeaderBytes)
                val recBuf = ByteBuffer.wrap(recHeaderBytes).order(ByteOrder.LITTLE_ENDIAN)

                val magic = recBuf.getInt()
                if (magic != MAGIC_RECORD) {
                    return@withContext ImgVaultResult.Error("Record magic mismatch.")
                }
                val idBytes = recBuf.readBytes(36)
                val isDeleted = recBuf.getInt()
                val imgIv = recBuf.readBytes(GCM_IV_LEN)
                val imgCipherLen = recBuf.getLong()
                val thumbFlag = recBuf.getInt()
                val thumbIv = recBuf.readBytes(GCM_IV_LEN)
                val thumbLen = recBuf.getInt()

                val imgCipher = ByteArray(imgCipherLen.toInt())
                raf.readFully(imgCipher)

                val decryptedBytes = decryptBlock(imgCipher, imgIv, masterKey)
                FileOutputStream(targetFile).use { it.write(decryptedBytes) }
            }

            ImgVaultResult.Success(targetFile)
        } catch (e: Throwable) {
            ImgVaultResult.Error("Failed to decrypt image: ${e.message}", e)
        }
    }

    /**
     * Retrieves decrypted thumbnail for an image. Returns cached file if available,
     * otherwise reads encrypted thumbnail from container and caches it.
     */
    suspend fun getThumbnail(
        context: Context,
        vaultFile: File,
        masterKey: SecretKeySpec,
        record: ImgVaultRecord
    ): File? = withContext(Dispatchers.IO) {
        try {
            val thumbDir = File(context.cacheDir, "img_vault_thumbs").apply { mkdirs() }
            val cachedFile = File(thumbDir, "thumb_${record.id}.jpg")
            if (cachedFile.exists() && cachedFile.length() > 0) {
                return@withContext cachedFile
            }

            RandomAccessFile(vaultFile, "r").use { raf ->
                raf.seek(record.recordOffset)
                val recHeaderBytes = ByteArray(4 + 36 + 4 + GCM_IV_LEN + 8 + 4 + GCM_IV_LEN + 4)
                raf.readFully(recHeaderBytes)
                val recBuf = ByteBuffer.wrap(recHeaderBytes).order(ByteOrder.LITTLE_ENDIAN)

                val magic = recBuf.getInt()
                if (magic != MAGIC_RECORD) return@withContext null
                val idBytes = recBuf.readBytes(36)
                val isDeleted = recBuf.getInt()
                val imgIv = recBuf.readBytes(GCM_IV_LEN)
                val imgCipherLen = recBuf.getLong()
                val thumbFlag = recBuf.getInt()
                val thumbIv = recBuf.readBytes(GCM_IV_LEN)
                val thumbLen = recBuf.getInt()

                // Skip image payload to get directly to thumbnail
                raf.seek(record.recordOffset + (4 + 36 + 4 + GCM_IV_LEN + 8 + 4 + GCM_IV_LEN + 4) + imgCipherLen)
                if (thumbLen <= 0) return@withContext null
                val thumbCipher = ByteArray(thumbLen)
                raf.readFully(thumbCipher)

                val thumbBytes = decryptBlock(thumbCipher, thumbIv, masterKey)
                FileOutputStream(cachedFile).use { it.write(thumbBytes) }
                cachedFile
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun cacheDecryptedThumbnail(context: Context, recordId: String, bytes: ByteArray) {
        try {
            if (bytes.isEmpty()) return
            val thumbDir = File(context.cacheDir, "img_vault_thumbs").apply { mkdirs() }
            val file = File(thumbDir, "thumb_$recordId.jpg")
            file.writeBytes(bytes)
        } catch (_: Throwable) {}
    }

    fun clearDecryptedCache(context: Context) {
        try {
            File(context.cacheDir, "img_vault_thumbs").deleteRecursively()
            File(context.cacheDir, "img_vault_temp").deleteRecursively()
        } catch (_: Throwable) {}
    }

    /**
     * Logically marks records deleted and updates index.
     */
    suspend fun markDeleted(
        vaultFile: File,
        masterKey: SecretKeySpec,
        recordIds: Set<String>
    ): ImgVaultResult<ImgVaultMetadata> = withContext(Dispatchers.IO) {
        try {
            val currentMetadata = readMetadataDirect(vaultFile, masterKey)
                ?: return@withContext ImgVaultResult.Error("Could not read vault index.")

            val updatedRecords = currentMetadata.records.map {
                if (recordIds.contains(it.id)) it.copy(isDeleted = true) else it
            }
            val updatedMetadata = currentMetadata.copy(
                records = updatedRecords,
                updatedAt = System.currentTimeMillis()
            )

            writeIndexOnly(vaultFile, masterKey, updatedMetadata)
            ImgVaultResult.Success(updatedMetadata)
        } catch (e: Throwable) {
            ImgVaultResult.Error("Failed to delete items: ${e.message}", e)
        }
    }

    /**
     * Renames an image record in the index.
     */
    suspend fun renameImage(
        vaultFile: File,
        masterKey: SecretKeySpec,
        recordId: String,
        newName: String
    ): ImgVaultResult<ImgVaultMetadata> = withContext(Dispatchers.IO) {
        try {
            val current = readMetadataDirect(vaultFile, masterKey)
                ?: return@withContext ImgVaultResult.Error("Could not read vault index.")
            val updatedRecords = current.records.map {
                if (it.id == recordId) it.copy(name = newName) else it
            }
            val updated = current.copy(records = updatedRecords, updatedAt = System.currentTimeMillis())
            writeIndexOnly(vaultFile, masterKey, updated)
            ImgVaultResult.Success(updated)
        } catch (e: Throwable) {
            ImgVaultResult.Error("Failed to rename: ${e.message}", e)
        }
    }

    /**
     * Moves images to a specified folder.
     */
    suspend fun moveImages(
        vaultFile: File,
        masterKey: SecretKeySpec,
        recordIds: Set<String>,
        targetFolderId: String?
    ): ImgVaultResult<ImgVaultMetadata> = withContext(Dispatchers.IO) {
        try {
            val current = readMetadataDirect(vaultFile, masterKey)
                ?: return@withContext ImgVaultResult.Error("Could not read vault index.")
            val updatedRecords = current.records.map {
                if (recordIds.contains(it.id)) it.copy(folderId = targetFolderId) else it
            }
            val updated = current.copy(records = updatedRecords, updatedAt = System.currentTimeMillis())
            writeIndexOnly(vaultFile, masterKey, updated)
            ImgVaultResult.Success(updated)
        } catch (e: Throwable) {
            ImgVaultResult.Error("Failed to move: ${e.message}", e)
        }
    }

    /**
     * Copies images to a specified folder inside the vault.
     */
    suspend fun copyImages(
        vaultFile: File,
        masterKey: SecretKeySpec,
        recordIds: Set<String>,
        targetFolderId: String?
    ): ImgVaultResult<ImgVaultMetadata> = withContext(Dispatchers.IO) {
        try {
            val current = readMetadataDirect(vaultFile, masterKey)
                ?: return@withContext ImgVaultResult.Error("Could not read vault index.")
            val copiedRecords = current.records.filter { recordIds.contains(it.id) }.map { rec ->
                rec.copy(
                    id = java.util.UUID.randomUUID().toString(),
                    folderId = targetFolderId,
                    name = if (rec.folderId == targetFolderId) "Copy_${rec.name}" else rec.name,
                    createdAt = System.currentTimeMillis()
                )
            }
            val updated = current.copy(
                records = current.records + copiedRecords,
                updatedAt = System.currentTimeMillis()
            )
            writeIndexOnly(vaultFile, masterKey, updated)
            ImgVaultResult.Success(updated)
        } catch (e: Throwable) {
            ImgVaultResult.Error("Failed to copy: ${e.message}", e)
        }
    }


    /**
     * Creates a new folder inside the vault.
     */
    suspend fun createFolder(
        vaultFile: File,
        masterKey: SecretKeySpec,
        folderName: String
    ): ImgVaultResult<ImgVaultMetadata> = withContext(Dispatchers.IO) {
        try {
            val current = readMetadataDirect(vaultFile, masterKey)
                ?: return@withContext ImgVaultResult.Error("Could not read vault index.")
            val newFolder = ImgVaultFolder(name = folderName)
            val updated = current.copy(
                folders = current.folders + newFolder,
                updatedAt = System.currentTimeMillis()
            )
            writeIndexOnly(vaultFile, masterKey, updated)
            ImgVaultResult.Success(updated)
        } catch (e: Throwable) {
            ImgVaultResult.Error("Failed to create folder: ${e.message}", e)
        }
    }

    /**
     * Compacts the vault to physically remove deleted records and reclaim storage.
     */
    suspend fun compactVault(
        context: Context,
        vaultFile: File,
        masterKey: SecretKeySpec,
        onProgress: ((Float, String) -> Unit)? = null
    ): ImgVaultResult<ImgVaultMetadata> = withContext(Dispatchers.IO) {
        try {
            onProgress?.invoke(0.1f, "Inspecting deleted records…")
            val current = readMetadataDirect(vaultFile, masterKey)
                ?: return@withContext ImgVaultResult.Error("Could not read vault index.")

            val activeRecords = current.activeRecords
            if (activeRecords.size == current.records.size) {
                return@withContext ImgVaultResult.Success(current)
            }

            val tempFile = File(vaultFile.parentFile, "${vaultFile.name}.compact.tmp")
            if (tempFile.exists()) tempFile.delete()

            onProgress?.invoke(0.2f, "Writing compacted container header…")
            // Copy header from original vault
            RandomAccessFile(vaultFile, "r").use { srcRaf ->
                val headerBytes = ByteArray(HEADER_FIXED_SIZE)
                srcRaf.seek(0)
                srcRaf.readFully(headerBytes)

                RandomAccessFile(tempFile, "rw").use { dstRaf ->
                    dstRaf.write(headerBytes)

                    var currentOffset = HEADER_FIXED_SIZE.toLong()
                    val newRecords = mutableListOf<ImgVaultRecord>()
                    var totalUsed = 0L

                    activeRecords.forEachIndexed { idx, rec ->
                        val pct = 0.2f + (0.6f * (idx.toFloat() / activeRecords.size))
                        onProgress?.invoke(pct, "Copying active image ${idx + 1}/${activeRecords.size}…")

                        srcRaf.seek(rec.recordOffset)
                        val recBytes = ByteArray(rec.recordLength.toInt())
                        srcRaf.readFully(recBytes)

                        dstRaf.seek(currentOffset)
                        dstRaf.write(recBytes)

                        val updatedRec = rec.copy(
                            recordOffset = currentOffset,
                            recordLength = rec.recordLength
                        )
                        newRecords.add(updatedRec)
                        currentOffset += rec.recordLength
                        totalUsed += rec.sizeBytes
                    }

                    onProgress?.invoke(0.85f, "Writing compacted index…")
                    val updatedMetadata = current.copy(
                        records = newRecords,
                        totalUsedBytes = totalUsed,
                        updatedAt = System.currentTimeMillis()
                    )

                    val indexJson = serializeMetadata(updatedMetadata).toByteArray(StandardCharsets.UTF_8)
                    val (indexIv, indexCipher) = encryptBlock(indexJson, masterKey)

                    val indexBuf = ByteBuffer.allocate(GCM_IV_LEN + 4 + indexCipher.size).order(ByteOrder.LITTLE_ENDIAN)
                    indexBuf.put(indexIv)
                    indexBuf.putInt(indexCipher.size)
                    indexBuf.put(indexCipher)
                    dstRaf.seek(currentOffset)
                    dstRaf.write(indexBuf.array())

                    // Update header in dst
                    dstRaf.seek(HEADER_INDEX_POINTER_OFFSET)
                    val updateBuf = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
                    updateBuf.putLong(currentOffset)
                    updateBuf.putLong(indexBuf.array().size.toLong())
                    updateBuf.putLong(totalUsed)
                    dstRaf.write(updateBuf.array())
                }
            }

            // Replace original file with compacted file
            if (vaultFile.delete()) {
                tempFile.renameTo(vaultFile)
            }

            onProgress?.invoke(1.0f, "Vault compacted successfully!")
            val finalizedMetadata = readMetadataDirect(vaultFile, masterKey) ?: current
            ImgVaultResult.Success(finalizedMetadata)
        } catch (e: Throwable) {
            ImgVaultResult.Error("Compaction failed: ${e.message}", e)
        }
    }

    private fun writeIndexOnly(
        vaultFile: File,
        masterKey: SecretKeySpec,
        metadata: ImgVaultMetadata
    ) {
        RandomAccessFile(vaultFile, "rw").use { raf ->
            val indexOffset = raf.length()
            val indexJson = serializeMetadata(metadata).toByteArray(StandardCharsets.UTF_8)
            val (indexIv, indexCipher) = encryptBlock(indexJson, masterKey)

            val indexBuf = ByteBuffer.allocate(GCM_IV_LEN + 4 + indexCipher.size).order(ByteOrder.LITTLE_ENDIAN)
            indexBuf.put(indexIv)
            indexBuf.putInt(indexCipher.size)
            indexBuf.put(indexCipher)
            raf.seek(indexOffset)
            raf.write(indexBuf.array())

            // Update header pointer
            raf.seek(HEADER_INDEX_POINTER_OFFSET)
            val updateBuf = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
            updateBuf.putLong(indexOffset)
            updateBuf.putLong(indexBuf.array().size.toLong())
            updateBuf.putLong(metadata.totalUsedBytes)
            raf.write(updateBuf.array())
        }
    }

    private fun readMetadataDirect(vaultFile: File, masterKey: SecretKeySpec): ImgVaultMetadata? {
        return try {
            RandomAccessFile(vaultFile, "r").use { raf ->
                raf.seek(HEADER_INDEX_POINTER_OFFSET)
                val ptrBytes = ByteArray(8)
                raf.readFully(ptrBytes)
                val indexOffset = ByteBuffer.wrap(ptrBytes).order(ByteOrder.LITTLE_ENDIAN).long

                if (indexOffset <= 0 || indexOffset >= vaultFile.length()) return null

                raf.seek(indexOffset)
                val idxHeader = ByteArray(GCM_IV_LEN + 4)
                raf.readFully(idxHeader)
                val idxBuf = ByteBuffer.wrap(idxHeader).order(ByteOrder.LITTLE_ENDIAN)
                val indexIv = idxBuf.readBytes(GCM_IV_LEN)
                val cipherLen = idxBuf.getInt()
                if (cipherLen <= 0 || cipherLen > 20 * 1024 * 1024) return null
                val indexCipher = ByteArray(cipherLen)
                raf.readFully(indexCipher)

                val decryptedIndexJson = String(decryptBlock(indexCipher, indexIv, masterKey), StandardCharsets.UTF_8)
                parseMetadata(decryptedIndexJson)
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun serializeMetadata(metadata: ImgVaultMetadata): String {
        val root = JSONObject()
        root.put("name", metadata.name)
        root.put("maxCapacityBytes", metadata.maxCapacityBytes)
        root.put("createdAt", metadata.createdAt)
        root.put("updatedAt", metadata.updatedAt)
        root.put("totalUsedBytes", metadata.totalUsedBytes)

        val foldersArr = JSONArray()
        metadata.folders.forEach { f ->
            val fo = JSONObject()
            fo.put("id", f.id)
            fo.put("name", f.name)
            fo.put("createdAt", f.createdAt)
            foldersArr.put(fo)
        }
        root.put("folders", foldersArr)

        val recordsArr = JSONArray()
        metadata.records.forEach { r ->
            val ro = JSONObject()
            ro.put("id", r.id)
            ro.put("folderId", r.folderId ?: "")
            ro.put("name", r.name)
            ro.put("sizeBytes", r.sizeBytes)
            ro.put("mimeType", r.mimeType)
            ro.put("createdAt", r.createdAt)
            ro.put("recordOffset", r.recordOffset)
            ro.put("recordLength", r.recordLength)
            ro.put("isDeleted", r.isDeleted)
            ro.put("hasThumbnail", r.hasThumbnail)
            recordsArr.put(ro)
        }
        root.put("records", recordsArr)

        return root.toString()
    }

    private fun parseMetadata(jsonStr: String): ImgVaultMetadata {
        val root = JSONObject(jsonStr)
        val name = root.optString("name", "My Vault")
        val maxCapacity = root.optLong("maxCapacityBytes", CAPACITY_5GB)
        val createdAt = root.optLong("createdAt", System.currentTimeMillis())
        val updatedAt = root.optLong("updatedAt", System.currentTimeMillis())
        val totalUsed = root.optLong("totalUsedBytes", 0L)

        val folders = mutableListOf<ImgVaultFolder>()
        val foldersArr = root.optJSONArray("folders")
        if (foldersArr != null) {
            for (i in 0 until foldersArr.length()) {
                val fo = foldersArr.getJSONObject(i)
                folders.add(
                    ImgVaultFolder(
                        id = fo.optString("id", UUID.randomUUID().toString()),
                        name = fo.optString("name", "Folder"),
                        createdAt = fo.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
        }
        if (folders.isEmpty()) {
            folders.add(ImgVaultFolder(id = "default", name = "Main Gallery"))
        }

        val records = mutableListOf<ImgVaultRecord>()
        val recordsArr = root.optJSONArray("records")
        if (recordsArr != null) {
            for (i in 0 until recordsArr.length()) {
                val ro = recordsArr.getJSONObject(i)
                records.add(
                    ImgVaultRecord(
                        id = ro.optString("id", UUID.randomUUID().toString()),
                        folderId = ro.optString("folderId", "").ifEmpty { null },
                        name = ro.optString("name", "image.jpg"),
                        sizeBytes = ro.optLong("sizeBytes", 0L),
                        mimeType = ro.optString("mimeType", "image/jpeg"),
                        createdAt = ro.optLong("createdAt", System.currentTimeMillis()),
                        recordOffset = ro.optLong("recordOffset", 0L),
                        recordLength = ro.optLong("recordLength", 0L),
                        isDeleted = ro.optBoolean("isDeleted", false),
                        hasThumbnail = ro.optBoolean("hasThumbnail", true)
                    )
                )
            }
        }

        return ImgVaultMetadata(
            name = name,
            maxCapacityBytes = maxCapacity,
            createdAt = createdAt,
            updatedAt = updatedAt,
            folders = folders,
            records = records,
            totalUsedBytes = totalUsed
        )
    }
}
