package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class VidVaultFolder(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

data class VidVaultRecord(
    val id: String = UUID.randomUUID().toString(),
    val folderId: String? = null,
    val name: String,
    val sizeBytes: Long,
    val durationMs: Long = 0L,
    val mimeType: String = "video/mp4",
    val createdAt: Long = System.currentTimeMillis(),
    val recordOffset: Long = 0L,
    val recordLength: Long = 0L,
    val videoDataOffset: Long = 0L,
    val videoDataLength: Long = 0L,
    val isDeleted: Boolean = false,
    val hasThumbnail: Boolean = true
)

data class VidVaultMetadata(
    val name: String,
    val maxCapacityBytes: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val folders: List<VidVaultFolder>,
    val records: List<VidVaultRecord>,
    val totalUsedBytes: Long
) {
    val activeRecords: List<VidVaultRecord> get() = records.filter { !it.isDeleted }
    val activeVideoCount: Int get() = activeRecords.size
    val availableBytes: Long get() = (maxCapacityBytes - totalUsedBytes).coerceAtLeast(0L)
}

data class VaultVideo(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val sizeBytes: Long,
    val durationMs: Long = 0L,
    val folderId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val mimeType: String = "video/mp4",
    val thumbnailFile: File? = null
)

sealed class VidVaultResult<out T> {
    data class Success<T>(val data: T) : VidVaultResult<T>()
    data class Error(val message: String, val cause: Throwable? = null) : VidVaultResult<Nothing>()
}

/**
 * Ultra-low-RAM Encrypted Video Vault Engine (.vidvault).
 *
 * Architecture:
 * - Header (256 bytes fixed) + Encrypted Index: AES-256-GCM authenticated encryption.
 * - Video Payloads: AES-256-CTR with per-video random 16-byte IV, streamed directly
 *   to/from storage in fixed 64 KB chunks so even multi-GB 4K videos use < 256 KB RAM.
 * - Direct Playback: Custom [EncryptedVideoDataSource] (Android MediaDataSource)
 *   reads and decrypts random-access slices directly from disk with zero plaintext copy
 *   and zero full-file RAM buffering.
 * - Max container capacity: up to 30 GB (32,212,254,720 bytes).
 */
object VidVaultEngine {

    const val EXTENSION = ".vidvault"
    const val CAPACITY_10GB = 10L * 1024L * 1024L * 1024L
    const val CAPACITY_20GB = 20L * 1024L * 1024L * 1024L
    const val CAPACITY_30GB = 30L * 1024L * 1024L * 1024L

    private const val MAGIC_HEADER = 0x56494456 // "VIDV"
    private const val MAGIC_RECORD = 0x56524543 // "VREC"
    private const val FORMAT_VERSION = 1

    private const val KDF_ITERATIONS = 100_000
    private const val KEY_LEN_BITS = 256
    private const val SALT_LEN = 16
    private const val GCM_IV_LEN = 12
    private const val CTR_IV_LEN = 16
    private const val GCM_TAG_BITS = 128
    private const val ENC_KEY_LEN = 48 // 32 bytes AES-256 master key + 16 bytes GCM tag
    private const val HEADER_INDEX_POINTER_OFFSET = 168L // 4+4+8 + (16+12+48) + (16+12+48) = 168
    private const val HEADER_FIXED_SIZE = 256

    // Record Header Size:
    // magic(4) + id(36) + isDeleted(4) + ctrIv(16) + videoLen(8) + thumbFlag(4) + thumbIv(12) + thumbLen(4) = 88 bytes
    private const val RECORD_HEADER_SIZE = 88

    // Fixed 64 KB streaming buffer to ensure minimal RAM footprint on all operations
    private const val STREAM_BUFFER_SIZE = 64 * 1024

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

    private fun encryptGcmBlock(plaintext: ByteArray, key: SecretKeySpec): Pair<ByteArray, ByteArray> {
        val iv = ByteArray(GCM_IV_LEN).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val ciphertext = cipher.doFinal(plaintext)
        return Pair(iv, ciphertext)
    }

    private fun decryptGcmBlock(ciphertext: ByteArray, iv: ByteArray, key: SecretKeySpec): ByteArray {
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
     * Increments a 16-byte big-endian AES-CTR IV by [blockCount] 16-byte AES blocks.
     */
    internal fun incrementCtrIv(baseIv: ByteArray, blockCount: Long): ByteArray {
        val result = baseIv.copyOf()
        var carry = blockCount
        var i = result.size - 1
        while (i >= 0 && carry > 0L) {
            val sum = (result[i].toInt() and 0xFF).toLong() + (carry and 0xFFL)
            result[i] = (sum and 0xFFL).toByte()
            carry = (carry ushr 8) + (sum ushr 8)
            i--
        }
        return result
    }

    /**
     * Initializes a brand-new .vidvault container on disk.
     * Does NOT pre-allocate 30 GB; grows dynamically as videos are added.
     */
    suspend fun createVault(
        context: Context,
        targetFile: File,
        vaultName: String,
        password: String,
        recoveryKey: String,
        maxCapacityBytes: Long = CAPACITY_30GB
    ): VidVaultResult<File> = withContext(Dispatchers.IO) {
        try {
            val clampedCapacity = maxCapacityBytes.coerceIn(1L * 1024 * 1024 * 1024, CAPACITY_30GB)
            if (targetFile.exists() && targetFile.length() > 0) {
                return@withContext VidVaultResult.Error("Target file already exists. Choose a different name or location.")
            }
            targetFile.parentFile?.mkdirs()

            val masterKeyBytes = ByteArray(32).also { secureRandom.nextBytes(it) }
            val masterKey = SecretKeySpec(masterKeyBytes, "AES")

            val passSalt = ByteArray(SALT_LEN).also { secureRandom.nextBytes(it) }
            val passKey = deriveKey(password, passSalt)
            val (passIv, passEncKey) = encryptGcmBlock(masterKeyBytes, passKey)

            val recKeyClean = recoveryKey.replace("-", "").trim()
            val recSalt = ByteArray(SALT_LEN).also { secureRandom.nextBytes(it) }
            val recKey = deriveKey(recKeyClean, recSalt)
            val (recIv, recEncKey) = encryptGcmBlock(masterKeyBytes, recKey)

            val initialMetadata = VidVaultMetadata(
                name = vaultName,
                maxCapacityBytes = clampedCapacity,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                folders = listOf(VidVaultFolder(id = "default", name = "Main Videos")),
                records = emptyList(),
                totalUsedBytes = 0L
            )

            val indexJson = serializeMetadata(initialMetadata).toByteArray(StandardCharsets.UTF_8)
            val (indexIv, indexCipher) = encryptGcmBlock(indexJson, masterKey)

            RandomAccessFile(targetFile, "rw").use { raf ->
                raf.setLength(0)

                val headerBuf = ByteBuffer.allocate(HEADER_FIXED_SIZE).order(ByteOrder.LITTLE_ENDIAN)
                headerBuf.putInt(MAGIC_HEADER)
                headerBuf.putInt(FORMAT_VERSION)
                headerBuf.putLong(clampedCapacity)

                headerBuf.put(passSalt)
                headerBuf.put(passIv)
                headerBuf.put(passEncKey)

                headerBuf.put(recSalt)
                headerBuf.put(recIv)
                headerBuf.put(recEncKey)

                val indexOffset = HEADER_FIXED_SIZE.toLong()
                headerBuf.putLong(indexOffset)
                headerBuf.putLong((GCM_IV_LEN + 4 + indexCipher.size).toLong())
                headerBuf.putLong(0L)

                raf.seek(0)
                raf.write(headerBuf.array())

                raf.seek(indexOffset)
                val indexBuf = ByteBuffer.allocate(GCM_IV_LEN + 4 + indexCipher.size).order(ByteOrder.LITTLE_ENDIAN)
                indexBuf.put(indexIv)
                indexBuf.putInt(indexCipher.size)
                indexBuf.put(indexCipher)
                raf.write(indexBuf.array())
            }

            VidVaultResult.Success(targetFile)
        } catch (e: Throwable) {
            VidVaultResult.Error("Failed to create video vault: ${e.message}", e)
        }
    }

    /**
     * Verifies password or recovery key and decrypts the authenticated index.
     */
    suspend fun openVault(
        context: Context,
        vaultFile: File,
        passwordOrRecoveryKey: String
    ): VidVaultResult<Pair<SecretKeySpec, VidVaultMetadata>> = withContext(Dispatchers.IO) {
        try {
            if (!vaultFile.exists() || vaultFile.length() < HEADER_FIXED_SIZE) {
                return@withContext VidVaultResult.Error("Video vault file is too small or missing.")
            }

            RandomAccessFile(vaultFile, "r").use { raf ->
                val headerBytes = ByteArray(HEADER_FIXED_SIZE)
                raf.seek(0)
                raf.readFully(headerBytes)

                val header = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN)
                val magic = header.getInt()
                if (magic != MAGIC_HEADER) {
                    return@withContext VidVaultResult.Error("Not a valid VID Vault file (invalid header).")
                }
                header.getInt() // version
                header.getLong() // capacity

                val passSalt = header.readBytes(SALT_LEN)
                val passIv = header.readBytes(GCM_IV_LEN)
                val passEncKey = header.readBytes(ENC_KEY_LEN)

                val recSalt = header.readBytes(SALT_LEN)
                val recIv = header.readBytes(GCM_IV_LEN)
                val recEncKey = header.readBytes(ENC_KEY_LEN)

                val indexOffset = header.getLong()
                header.getLong() // indexLength
                header.getLong() // totalUsed

                var masterKey: SecretKeySpec? = null
                try {
                    val passKey = deriveKey(passwordOrRecoveryKey, passSalt)
                    val masterBytes = decryptGcmBlock(passEncKey, passIv, passKey)
                    if (masterBytes.size == 32) {
                        masterKey = SecretKeySpec(masterBytes, "AES")
                    }
                } catch (_: Throwable) {}

                if (masterKey == null) {
                    val cleanRec = passwordOrRecoveryKey.replace("-", "").trim()
                    try {
                        val recKey = deriveKey(cleanRec, recSalt)
                        val masterBytes = decryptGcmBlock(recEncKey, recIv, recKey)
                        if (masterBytes.size == 32) {
                            masterKey = SecretKeySpec(masterBytes, "AES")
                        }
                    } catch (_: Throwable) {}
                }

                val finalKey = masterKey
                    ?: return@withContext VidVaultResult.Error("Incorrect password or recovery key.")

                if (indexOffset <= 0 || indexOffset >= vaultFile.length()) {
                    return@withContext VidVaultResult.Error("Corrupted video vault index pointer.")
                }

                raf.seek(indexOffset)
                val idxHeader = ByteArray(GCM_IV_LEN + 4)
                raf.readFully(idxHeader)
                val idxBuf = ByteBuffer.wrap(idxHeader).order(ByteOrder.LITTLE_ENDIAN)
                val indexIv = idxBuf.readBytes(GCM_IV_LEN)
                val cipherLen = idxBuf.getInt()
                if (cipherLen <= 0 || cipherLen > 20 * 1024 * 1024) {
                    return@withContext VidVaultResult.Error("Corrupted video vault index size.")
                }
                val indexCipher = ByteArray(cipherLen)
                raf.readFully(indexCipher)

                val decryptedIndexJson = String(decryptGcmBlock(indexCipher, indexIv, finalKey), StandardCharsets.UTF_8)
                val metadata = parseMetadata(decryptedIndexJson)

                VidVaultResult.Success(Pair(finalKey, metadata))
            }
        } catch (e: javax.crypto.AEADBadTagException) {
            VidVaultResult.Error("Incorrect password or corrupted video vault container.")
        } catch (e: Throwable) {
            VidVaultResult.Error("Error opening video vault: ${e.message}", e)
        }
    }

    /**
     * Reads metadata directly using an already unlocked master key (no PBKDF2 delay).
     */
    suspend fun readMetadata(
        vaultFile: File,
        masterKey: SecretKeySpec
    ): VidVaultMetadata? = withContext(Dispatchers.IO) {
        readMetadataDirect(vaultFile, masterKey)
    }

    /**
     * Streams a batch of videos directly from storage into the encrypted .vidvault container
     * using a fixed 64 KB buffer (zero full-file RAM buffering!).
     * Supports all video formats (.mp4, .mkv, .mov, .avi, .webm, .3gp, .m4v, etc.).
     */
    suspend fun importVideosBatch(
        context: Context,
        vaultFile: File,
        masterKey: SecretKeySpec,
        sourceUris: List<Uri>,
        targetFolderId: String? = null,
        onProgress: ((Int, Int, Float, String) -> Unit)? = null
    ): VidVaultResult<Pair<Int, Int>> = withContext(Dispatchers.IO) {
        try {
            if (sourceUris.isEmpty()) {
                return@withContext VidVaultResult.Success(Pair(0, 0))
            }

            val currentMetadata = readMetadataDirect(vaultFile, masterKey)
                ?: return@withContext VidVaultResult.Error("Could not read video vault index.")

            var remainingCapacity = currentMetadata.maxCapacityBytes - currentMetadata.totalUsedBytes
            if (remainingCapacity <= 0L) {
                val maxGb = currentMetadata.maxCapacityBytes / (1024L * 1024L * 1024L)
                return@withContext VidVaultResult.Error("Video vault capacity reached ($maxGb GB max).")
            }

            val targetFolder = targetFolderId ?: currentMetadata.folders.firstOrNull()?.id
            val totalCount = sourceUris.size.coerceAtMost(200)
            val urisToProcess = sourceUris.take(totalCount)

            // Reusable 64 KB buffers -> constant ultra-low RAM usage even for multi-GB videos
            val readBuffer = ByteArray(STREAM_BUFFER_SIZE)
            val cipherBuffer = ByteArray(STREAM_BUFFER_SIZE + 32)

            RandomAccessFile(vaultFile, "rw").use { raf ->
                raf.seek(HEADER_INDEX_POINTER_OFFSET)
                val ptrBytes = ByteArray(8)
                raf.readFully(ptrBytes)
                val oldIndexOffset = ByteBuffer.wrap(ptrBytes).order(ByteOrder.LITTLE_ENDIAN).long

                var currentRecordOffset = oldIndexOffset
                val newRecords = mutableListOf<VidVaultRecord>()
                var totalAddedBytes = 0L
                var successCount = 0

                for ((index, uri) in urisToProcess.withIndex()) {
                    onProgress?.invoke(
                        index + 1,
                        totalCount,
                        index.toFloat() / totalCount.coerceAtLeast(1),
                        "Preparing video ${index + 1} of $totalCount…"
                    )

                    var originalName = "video_${System.currentTimeMillis()}_$index.mp4"
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
                            context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
                        } catch (_: Throwable) { 0L }
                    }

                    if (sourceSize > 0L && sourceSize > remainingCapacity) {
                        continue
                    }

                    val detectedMime = resolveVideoMimeType(context, uri, originalName)

                    // Extract low-RAM thumbnail and duration directly from storage via MediaMetadataRetriever
                    val (thumbBytes, durationMs) = extractVideoThumbnailAndDuration(context, uri)
                    val (thumbIv, thumbCipher) = if (thumbBytes.isNotEmpty()) {
                        encryptGcmBlock(thumbBytes, masterKey)
                    } else {
                        ByteArray(GCM_IV_LEN) to ByteArray(0)
                    }

                    val recordId = UUID.randomUUID().toString()
                    val recordStartOffset = currentRecordOffset
                    val videoDataStartOffset = recordStartOffset + RECORD_HEADER_SIZE

                    // Generate random 16-byte IV for AES-256-CTR streaming
                    val ctrIv = ByteArray(CTR_IV_LEN).also { secureRandom.nextBytes(it) }
                    val cipher = Cipher.getInstance("AES/CTR/NoPadding")
                    cipher.init(Cipher.ENCRYPT_MODE, masterKey, IvParameterSpec(ctrIv))

                    // Reserve space for 88-byte record header, then stream-encrypt video directly to disk
                    raf.seek(videoDataStartOffset)
                    var videoBytesWritten = 0L
                    var streamFailed = false
                    var lastProgressUpdate = 0L

                    try {
                        val inputStream = context.contentResolver.openInputStream(uri)
                        if (inputStream == null) {
                            streamFailed = true
                        } else {
                            inputStream.use { input ->
                                while (true) {
                                    val bytesRead = input.read(readBuffer)
                                    if (bytesRead == -1) break
                                    if (bytesRead > 0) {
                                        if (videoBytesWritten + bytesRead > remainingCapacity) {
                                            streamFailed = true
                                            break
                                        }
                                        val encLen = cipher.update(readBuffer, 0, bytesRead, cipherBuffer, 0)
                                        if (encLen > 0) {
                                            raf.write(cipherBuffer, 0, encLen)
                                            videoBytesWritten += encLen
                                        }
                                        val now = System.currentTimeMillis()
                                        if (now - lastProgressUpdate > 250L) {
                                            lastProgressUpdate = now
                                            val fileFrac = if (sourceSize > 0L) {
                                                (videoBytesWritten.toFloat() / sourceSize).coerceIn(0f, 1f)
                                            } else 0.5f
                                            val overallPct = (index + fileFrac) / totalCount.coerceAtLeast(1)
                                            val mbDone = videoBytesWritten / (1024 * 1024)
                                            onProgress?.invoke(
                                                index + 1,
                                                totalCount,
                                                overallPct,
                                                "Streaming to storage: $originalName (${mbDone} MB)…"
                                            )
                                        }
                                    }
                                }
                                if (!streamFailed) {
                                    val finalBytes = cipher.doFinal()
                                    if (finalBytes != null && finalBytes.isNotEmpty()) {
                                        raf.write(finalBytes)
                                        videoBytesWritten += finalBytes.size
                                    }
                                }
                            }
                        }
                    } catch (_: Throwable) {
                        streamFailed = true
                    }

                    if (streamFailed || videoBytesWritten <= 0L) {
                        // Roll back partially written file tail safely
                        raf.setLength(currentRecordOffset)
                        continue
                    }

                    // Write encrypted thumbnail right after video ciphertext
                    if (thumbCipher.isNotEmpty()) {
                        raf.write(thumbCipher)
                    }

                    val recordEndOffset = raf.filePointer
                    val recordTotalLen = recordEndOffset - recordStartOffset

                    // Seek back to recordStartOffset and write finalized 88-byte record header
                    raf.seek(recordStartOffset)
                    val recHeaderBuf = ByteBuffer.allocate(RECORD_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
                    recHeaderBuf.putInt(MAGIC_RECORD)
                    val idBytes = recordId.padEnd(36, ' ').toByteArray(StandardCharsets.US_ASCII)
                    recHeaderBuf.put(idBytes)
                    recHeaderBuf.putInt(0) // active
                    recHeaderBuf.put(ctrIv)
                    recHeaderBuf.putLong(videoBytesWritten)
                    recHeaderBuf.putInt(if (thumbCipher.isNotEmpty()) 1 else 0)
                    recHeaderBuf.put(thumbIv)
                    recHeaderBuf.putInt(thumbCipher.size)
                    raf.write(recHeaderBuf.array())

                    val newRecord = VidVaultRecord(
                        id = recordId,
                        folderId = targetFolder,
                        name = originalName,
                        sizeBytes = videoBytesWritten,
                        durationMs = durationMs,
                        mimeType = detectedMime,
                        createdAt = System.currentTimeMillis(),
                        recordOffset = recordStartOffset,
                        recordLength = recordTotalLen,
                        videoDataOffset = videoDataStartOffset,
                        videoDataLength = videoBytesWritten,
                        isDeleted = false,
                        hasThumbnail = thumbCipher.isNotEmpty()
                    )
                    newRecords.add(newRecord)
                    cacheDecryptedThumbnail(context, recordId, thumbBytes)

                    currentRecordOffset = recordEndOffset
                    totalAddedBytes += videoBytesWritten
                    remainingCapacity -= videoBytesWritten
                    successCount++
                }

                if (newRecords.isNotEmpty()) {
                    val newIndexOffset = currentRecordOffset
                    val updatedMetadata = currentMetadata.copy(
                        records = currentMetadata.records + newRecords,
                        totalUsedBytes = currentMetadata.totalUsedBytes + totalAddedBytes,
                        updatedAt = System.currentTimeMillis()
                    )
                    val indexJson = serializeMetadata(updatedMetadata).toByteArray(StandardCharsets.UTF_8)
                    val (indexIv, indexCipher) = encryptGcmBlock(indexJson, masterKey)

                    raf.seek(newIndexOffset)
                    val indexBuf = ByteBuffer.allocate(GCM_IV_LEN + 4 + indexCipher.size).order(ByteOrder.LITTLE_ENDIAN)
                    indexBuf.put(indexIv)
                    indexBuf.putInt(indexCipher.size)
                    indexBuf.put(indexCipher)
                    raf.write(indexBuf.array())

                    raf.seek(HEADER_INDEX_POINTER_OFFSET)
                    val updateBuf = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
                    updateBuf.putLong(newIndexOffset)
                    updateBuf.putLong(indexBuf.array().size.toLong())
                    updateBuf.putLong(updatedMetadata.totalUsedBytes)
                    raf.write(updateBuf.array())
                }

                VidVaultResult.Success(Pair(successCount, urisToProcess.size))
            }
        } catch (e: Throwable) {
            VidVaultResult.Error("Video import failed: ${e.message}", e)
        }
    }

    /**
     * Creates a zero-RAM direct disk streaming [MediaDataSource] for instant playback and seeking
     * straight out of the encrypted .vidvault container without decrypting to RAM or copying the video.
     */
    fun createDirectDiskMediaDataSource(
        vaultFile: File,
        masterKey: SecretKeySpec,
        record: VidVaultRecord
    ): EncryptedVideoDataSource? {
        return try {
            RandomAccessFile(vaultFile, "r").use { raf ->
                raf.seek(record.recordOffset)
                val headerBytes = ByteArray(RECORD_HEADER_SIZE)
                raf.readFully(headerBytes)
                val buf = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN)
                val magic = buf.getInt()
                if (magic != MAGIC_RECORD) return null
                buf.readBytes(36) // id
                buf.getInt() // isDeleted
                val ctrIv = buf.readBytes(CTR_IV_LEN)
                val videoLen = buf.getLong()
                val dataOffset = record.recordOffset + RECORD_HEADER_SIZE

                EncryptedVideoDataSource(
                    vaultFile = vaultFile,
                    masterKey = masterKey,
                    baseIv = ctrIv,
                    dataOffset = dataOffset,
                    dataLength = videoLen
                )
            }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Streams a video record from the encrypted container to a temporary file on storage
     * using a fixed 64 KB buffer (for sharing, exporting to Movies/VIDVault, or fallback playback).
     */
    suspend fun streamDecryptVideoToFile(
        context: Context,
        vaultFile: File,
        masterKey: SecretKeySpec,
        record: VidVaultRecord,
        onProgress: ((Float) -> Unit)? = null
    ): VidVaultResult<File> = withContext(Dispatchers.IO) {
        try {
            val tempDir = AppStorageHelper.getVidVaultTempDir(context)
            val ext = record.name.substringAfterLast('.', "mp4").ifEmpty { "mp4" }
            val safeName = "vid_${record.id}.$ext"
            val targetFile = File(tempDir, safeName)

            if (targetFile.exists() && targetFile.length() == record.sizeBytes && record.sizeBytes > 0L) {
                return@withContext VidVaultResult.Success(targetFile)
            }

            RandomAccessFile(vaultFile, "r").use { raf ->
                raf.seek(record.recordOffset)
                val headerBytes = ByteArray(RECORD_HEADER_SIZE)
                raf.readFully(headerBytes)
                val buf = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN)
                val magic = buf.getInt()
                if (magic != MAGIC_RECORD) {
                    return@withContext VidVaultResult.Error("Video record header mismatch.")
                }
                buf.readBytes(36) // id
                buf.getInt() // isDeleted
                val ctrIv = buf.readBytes(CTR_IV_LEN)
                val videoLen = buf.getLong()

                val cipher = Cipher.getInstance("AES/CTR/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, masterKey, IvParameterSpec(ctrIv))

                val readBuf = ByteArray(STREAM_BUFFER_SIZE)
                val outBuf = ByteArray(STREAM_BUFFER_SIZE + 32)
                var remaining = videoLen
                var processed = 0L

                raf.seek(record.recordOffset + RECORD_HEADER_SIZE)
                FileOutputStream(targetFile).use { fos ->
                    while (remaining > 0L) {
                        val toRead = minOf(remaining, STREAM_BUFFER_SIZE.toLong()).toInt()
                        val read = raf.read(readBuf, 0, toRead)
                        if (read <= 0) break
                        val decLen = cipher.update(readBuf, 0, read, outBuf, 0)
                        if (decLen > 0) {
                            fos.write(outBuf, 0, decLen)
                        }
                        remaining -= read
                        processed += read
                        if (videoLen > 0L) {
                            onProgress?.invoke((processed.toFloat() / videoLen).coerceIn(0f, 1f))
                        }
                    }
                    val finalChunk = cipher.doFinal()
                    if (finalChunk != null && finalChunk.isNotEmpty()) {
                        fos.write(finalChunk)
                    }
                }
            }

            VidVaultResult.Success(targetFile)
        } catch (e: Throwable) {
            VidVaultResult.Error("Failed to stream-decrypt video: ${e.message}", e)
        }
    }

    /**
     * Streams a video record directly into any OutputStream (e.g. MediaStore Video URI)
     * with zero intermediate temp file and only 64 KB RAM!
     */
    suspend fun streamDecryptVideoToOutputStream(
        vaultFile: File,
        masterKey: SecretKeySpec,
        record: VidVaultRecord,
        outputStream: java.io.OutputStream,
        onProgress: ((Float) -> Unit)? = null
    ): VidVaultResult<Long> = withContext(Dispatchers.IO) {
        try {
            RandomAccessFile(vaultFile, "r").use { raf ->
                raf.seek(record.recordOffset)
                val headerBytes = ByteArray(RECORD_HEADER_SIZE)
                raf.readFully(headerBytes)
                val buf = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN)
                val magic = buf.getInt()
                if (magic != MAGIC_RECORD) {
                    return@withContext VidVaultResult.Error("Video record header mismatch.")
                }
                buf.readBytes(36)
                buf.getInt()
                val ctrIv = buf.readBytes(CTR_IV_LEN)
                val videoLen = buf.getLong()

                val cipher = Cipher.getInstance("AES/CTR/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, masterKey, IvParameterSpec(ctrIv))

                val readBuf = ByteArray(STREAM_BUFFER_SIZE)
                val outBuf = ByteArray(STREAM_BUFFER_SIZE + 32)
                var remaining = videoLen
                var totalWritten = 0L

                raf.seek(record.recordOffset + RECORD_HEADER_SIZE)
                while (remaining > 0L) {
                    val toRead = minOf(remaining, STREAM_BUFFER_SIZE.toLong()).toInt()
                    val read = raf.read(readBuf, 0, toRead)
                    if (read <= 0) break
                    val decLen = cipher.update(readBuf, 0, read, outBuf, 0)
                    if (decLen > 0) {
                        outputStream.write(outBuf, 0, decLen)
                        totalWritten += decLen
                    }
                    remaining -= read
                    if (videoLen > 0L) {
                        onProgress?.invoke((totalWritten.toFloat() / videoLen).coerceIn(0f, 1f))
                    }
                }
                val finalChunk = cipher.doFinal()
                if (finalChunk != null && finalChunk.isNotEmpty()) {
                    outputStream.write(finalChunk)
                    totalWritten += finalChunk.size
                }
                outputStream.flush()
                VidVaultResult.Success(totalWritten)
            }
        } catch (e: Throwable) {
            VidVaultResult.Error("Failed to export video stream: ${e.message}", e)
        }
    }

    /**
     * Retrieves decrypted JPEG thumbnail from disk cache or reads tiny encrypted thumbnail directly from container.
     */
    suspend fun getThumbnail(
        context: Context,
        vaultFile: File,
        masterKey: SecretKeySpec,
        record: VidVaultRecord
    ): File? = withContext(Dispatchers.IO) {
        try {
            val thumbDir = File(context.cacheDir, "vid_vault_thumbs").apply { mkdirs() }
            val cachedFile = File(thumbDir, "vthumb_${record.id}.jpg")
            if (cachedFile.exists() && cachedFile.length() > 0) {
                return@withContext cachedFile
            }

            RandomAccessFile(vaultFile, "r").use { raf ->
                raf.seek(record.recordOffset)
                val headerBytes = ByteArray(RECORD_HEADER_SIZE)
                raf.readFully(headerBytes)
                val buf = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN)

                val magic = buf.getInt()
                if (magic != MAGIC_RECORD) return@withContext null
                buf.readBytes(36) // id
                buf.getInt() // isDeleted
                buf.readBytes(CTR_IV_LEN) // ctrIv
                val videoLen = buf.getLong()
                buf.getInt() // thumbFlag
                val thumbIv = buf.readBytes(GCM_IV_LEN)
                val thumbLen = buf.getInt()

                if (thumbLen <= 0 || thumbLen > 2 * 1024 * 1024) return@withContext null
                raf.seek(record.recordOffset + RECORD_HEADER_SIZE + videoLen)
                val thumbCipher = ByteArray(thumbLen)
                raf.readFully(thumbCipher)

                val thumbBytes = decryptGcmBlock(thumbCipher, thumbIv, masterKey)
                FileOutputStream(cachedFile).use { it.write(thumbBytes) }
                cachedFile
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun extractVideoThumbnailAndDuration(context: Context, uri: Uri): Pair<ByteArray, Long> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: 0L

            val targetTimeUs = if (durationMs > 2000L) 1_000_000L else 0L
            val frame: Bitmap? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever.getScaledFrameAtTime(
                    targetTimeUs,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    320,
                    320
                )
            } else {
                retriever.getFrameAtTime(targetTimeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }

            val bytes = if (frame != null) {
                val maxDim = 256
                val scaled = if (frame.width > maxDim || frame.height > maxDim) {
                    val ratio = minOf(maxDim.toFloat() / frame.width, maxDim.toFloat() / frame.height)
                    Bitmap.createScaledBitmap(
                        frame,
                        (frame.width * ratio).toInt().coerceAtLeast(1),
                        (frame.height * ratio).toInt().coerceAtLeast(1),
                        true
                    )
                } else {
                    frame
                }
                val baos = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, 72, baos)
                if (scaled != frame) scaled.recycle()
                frame.recycle()
                baos.toByteArray()
            } else {
                ByteArray(0)
            }
            Pair(bytes, durationMs)
        } catch (_: Throwable) {
            Pair(ByteArray(0), 0L)
        } finally {
            try {
                retriever.release()
            } catch (_: Throwable) {}
        }
    }

    private fun resolveVideoMimeType(context: Context, uri: Uri, name: String): String {
        val resolverType = try { context.contentResolver.getType(uri) } catch (_: Throwable) { null }
        if (!resolverType.isNullOrBlank() && resolverType.startsWith("video/")) {
            return resolverType
        }
        return when (name.substringAfterLast('.', "").lowercase()) {
            "mp4", "m4v" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "mov" -> "video/quicktime"
            "avi" -> "video/x-msvideo"
            "3gp" -> "video/3gpp"
            "flv" -> "video/x-flv"
            "wmv" -> "video/x-ms-wmv"
            "ts" -> "video/mp2t"
            "mpeg", "mpg" -> "video/mpeg"
            else -> "video/*"
        }
    }

    private fun cacheDecryptedThumbnail(context: Context, recordId: String, bytes: ByteArray) {
        try {
            if (bytes.isEmpty()) return
            val thumbDir = File(context.cacheDir, "vid_vault_thumbs").apply { mkdirs() }
            File(thumbDir, "vthumb_$recordId.jpg").writeBytes(bytes)
        } catch (_: Throwable) {}
    }

    fun clearDecryptedCache(context: Context) {
        try {
            File(context.cacheDir, "vid_vault_thumbs").deleteRecursively()
            File(context.cacheDir, "vid_vault_temp").deleteRecursively()
        } catch (_: Throwable) {}
    }

    fun clearTempVideoCacheOnly(context: Context) {
        try {
            File(context.cacheDir, "vid_vault_temp").deleteRecursively()
        } catch (_: Throwable) {}
    }

    suspend fun markDeleted(
        vaultFile: File,
        masterKey: SecretKeySpec,
        recordIds: Set<String>
    ): VidVaultResult<VidVaultMetadata> = withContext(Dispatchers.IO) {
        try {
            val current = readMetadataDirect(vaultFile, masterKey)
                ?: return@withContext VidVaultResult.Error("Could not read video vault index.")
            val updatedRecords = current.records.map {
                if (recordIds.contains(it.id)) it.copy(isDeleted = true) else it
            }
            val updated = current.copy(records = updatedRecords, updatedAt = System.currentTimeMillis())
            writeIndexOnly(vaultFile, masterKey, updated)
            VidVaultResult.Success(updated)
        } catch (e: Throwable) {
            VidVaultResult.Error("Failed to delete videos: ${e.message}", e)
        }
    }

    suspend fun renameVideo(
        vaultFile: File,
        masterKey: SecretKeySpec,
        recordId: String,
        newName: String
    ): VidVaultResult<VidVaultMetadata> = withContext(Dispatchers.IO) {
        try {
            val current = readMetadataDirect(vaultFile, masterKey)
                ?: return@withContext VidVaultResult.Error("Could not read video vault index.")
            val updatedRecords = current.records.map {
                if (it.id == recordId) it.copy(name = newName) else it
            }
            val updated = current.copy(records = updatedRecords, updatedAt = System.currentTimeMillis())
            writeIndexOnly(vaultFile, masterKey, updated)
            VidVaultResult.Success(updated)
        } catch (e: Throwable) {
            VidVaultResult.Error("Failed to rename video: ${e.message}", e)
        }
    }

    suspend fun moveVideos(
        vaultFile: File,
        masterKey: SecretKeySpec,
        recordIds: Set<String>,
        targetFolderId: String?
    ): VidVaultResult<VidVaultMetadata> = withContext(Dispatchers.IO) {
        try {
            val current = readMetadataDirect(vaultFile, masterKey)
                ?: return@withContext VidVaultResult.Error("Could not read video vault index.")
            val updatedRecords = current.records.map {
                if (recordIds.contains(it.id)) it.copy(folderId = targetFolderId) else it
            }
            val updated = current.copy(records = updatedRecords, updatedAt = System.currentTimeMillis())
            writeIndexOnly(vaultFile, masterKey, updated)
            VidVaultResult.Success(updated)
        } catch (e: Throwable) {
            VidVaultResult.Error("Failed to move videos: ${e.message}", e)
        }
    }

    suspend fun copyVideos(
        vaultFile: File,
        masterKey: SecretKeySpec,
        recordIds: Set<String>,
        targetFolderId: String?
    ): VidVaultResult<VidVaultMetadata> = withContext(Dispatchers.IO) {
        try {
            val current = readMetadataDirect(vaultFile, masterKey)
                ?: return@withContext VidVaultResult.Error("Could not read video vault index.")
            val copiedRecords = current.records.filter { recordIds.contains(it.id) }.map { rec ->
                rec.copy(
                    id = UUID.randomUUID().toString(),
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
            VidVaultResult.Success(updated)
        } catch (e: Throwable) {
            VidVaultResult.Error("Failed to copy videos: ${e.message}", e)
        }
    }

    suspend fun createFolder(
        vaultFile: File,
        masterKey: SecretKeySpec,
        folderName: String
    ): VidVaultResult<VidVaultMetadata> = withContext(Dispatchers.IO) {
        try {
            val current = readMetadataDirect(vaultFile, masterKey)
                ?: return@withContext VidVaultResult.Error("Could not read video vault index.")
            val newFolder = VidVaultFolder(name = folderName)
            val updated = current.copy(
                folders = current.folders + newFolder,
                updatedAt = System.currentTimeMillis()
            )
            writeIndexOnly(vaultFile, masterKey, updated)
            VidVaultResult.Success(updated)
        } catch (e: Throwable) {
            VidVaultResult.Error("Failed to create folder: ${e.message}", e)
        }
    }

    /**
     * Ultra-low-RAM streaming compaction: copies active encrypted video records directly
     * disk-to-disk in 64 KB chunks so even a 30 GB vault compacts with < 128 KB RAM.
     */
    suspend fun compactVault(
        context: Context,
        vaultFile: File,
        masterKey: SecretKeySpec,
        onProgress: ((Float, String) -> Unit)? = null
    ): VidVaultResult<VidVaultMetadata> = withContext(Dispatchers.IO) {
        try {
            onProgress?.invoke(0.05f, "Inspecting deleted video records…")
            val current = readMetadataDirect(vaultFile, masterKey)
                ?: return@withContext VidVaultResult.Error("Could not read video vault index.")

            val activeRecords = current.activeRecords
            if (activeRecords.size == current.records.size) {
                return@withContext VidVaultResult.Success(current)
            }

            val tempFile = File(vaultFile.parentFile, "${vaultFile.name}.compact.tmp")
            if (tempFile.exists()) tempFile.delete()

            val copyBuffer = ByteArray(STREAM_BUFFER_SIZE)

            RandomAccessFile(vaultFile, "r").use { srcRaf ->
                val headerBytes = ByteArray(HEADER_FIXED_SIZE)
                srcRaf.seek(0)
                srcRaf.readFully(headerBytes)

                RandomAccessFile(tempFile, "rw").use { dstRaf ->
                    dstRaf.write(headerBytes)

                    var currentOffset = HEADER_FIXED_SIZE.toLong()
                    val newRecords = mutableListOf<VidVaultRecord>()
                    var totalUsed = 0L

                    activeRecords.forEachIndexed { idx, rec ->
                        val pct = 0.1f + (0.8f * (idx.toFloat() / activeRecords.size.coerceAtLeast(1)))
                        onProgress?.invoke(pct, "Streaming active video ${idx + 1}/${activeRecords.size} on storage…")

                        srcRaf.seek(rec.recordOffset)
                        dstRaf.seek(currentOffset)

                        var remaining = rec.recordLength
                        while (remaining > 0L) {
                            val chunk = minOf(remaining, STREAM_BUFFER_SIZE.toLong()).toInt()
                            val read = srcRaf.read(copyBuffer, 0, chunk)
                            if (read <= 0) break
                            dstRaf.write(copyBuffer, 0, read)
                            remaining -= read
                        }

                        val updatedRec = rec.copy(
                            recordOffset = currentOffset,
                            recordLength = rec.recordLength,
                            videoDataOffset = currentOffset + RECORD_HEADER_SIZE
                        )
                        newRecords.add(updatedRec)
                        currentOffset += rec.recordLength
                        totalUsed += rec.sizeBytes
                    }

                    onProgress?.invoke(0.92f, "Writing compacted index…")
                    val updatedMetadata = current.copy(
                        records = newRecords,
                        totalUsedBytes = totalUsed,
                        updatedAt = System.currentTimeMillis()
                    )

                    val indexJson = serializeMetadata(updatedMetadata).toByteArray(StandardCharsets.UTF_8)
                    val (indexIv, indexCipher) = encryptGcmBlock(indexJson, masterKey)

                    val indexBuf = ByteBuffer.allocate(GCM_IV_LEN + 4 + indexCipher.size).order(ByteOrder.LITTLE_ENDIAN)
                    indexBuf.put(indexIv)
                    indexBuf.putInt(indexCipher.size)
                    indexBuf.put(indexCipher)
                    dstRaf.seek(currentOffset)
                    dstRaf.write(indexBuf.array())

                    dstRaf.seek(HEADER_INDEX_POINTER_OFFSET)
                    val updateBuf = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
                    updateBuf.putLong(currentOffset)
                    updateBuf.putLong(indexBuf.array().size.toLong())
                    updateBuf.putLong(totalUsed)
                    dstRaf.write(updateBuf.array())
                }
            }

            if (vaultFile.delete()) {
                tempFile.renameTo(vaultFile)
            }

            onProgress?.invoke(1.0f, "Video vault compacted!")
            val finalized = readMetadataDirect(vaultFile, masterKey) ?: current
            VidVaultResult.Success(finalized)
        } catch (e: Throwable) {
            VidVaultResult.Error("Compaction failed: ${e.message}", e)
        }
    }

    private fun writeIndexOnly(
        vaultFile: File,
        masterKey: SecretKeySpec,
        metadata: VidVaultMetadata
    ) {
        RandomAccessFile(vaultFile, "rw").use { raf ->
            val indexOffset = raf.length()
            val indexJson = serializeMetadata(metadata).toByteArray(StandardCharsets.UTF_8)
            val (indexIv, indexCipher) = encryptGcmBlock(indexJson, masterKey)

            val indexBuf = ByteBuffer.allocate(GCM_IV_LEN + 4 + indexCipher.size).order(ByteOrder.LITTLE_ENDIAN)
            indexBuf.put(indexIv)
            indexBuf.putInt(indexCipher.size)
            indexBuf.put(indexCipher)
            raf.seek(indexOffset)
            raf.write(indexBuf.array())

            raf.seek(HEADER_INDEX_POINTER_OFFSET)
            val updateBuf = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
            updateBuf.putLong(indexOffset)
            updateBuf.putLong(indexBuf.array().size.toLong())
            updateBuf.putLong(metadata.totalUsedBytes)
            raf.write(updateBuf.array())
        }
    }

    private fun readMetadataDirect(vaultFile: File, masterKey: SecretKeySpec): VidVaultMetadata? {
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

                val decryptedIndexJson = String(decryptGcmBlock(indexCipher, indexIv, masterKey), StandardCharsets.UTF_8)
                parseMetadata(decryptedIndexJson)
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun serializeMetadata(metadata: VidVaultMetadata): String {
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
            ro.put("durationMs", r.durationMs)
            ro.put("mimeType", r.mimeType)
            ro.put("createdAt", r.createdAt)
            ro.put("recordOffset", r.recordOffset)
            ro.put("recordLength", r.recordLength)
            ro.put("videoDataOffset", r.videoDataOffset)
            ro.put("videoDataLength", r.videoDataLength)
            ro.put("isDeleted", r.isDeleted)
            ro.put("hasThumbnail", r.hasThumbnail)
            recordsArr.put(ro)
        }
        root.put("records", recordsArr)

        return root.toString()
    }

    private fun parseMetadata(jsonStr: String): VidVaultMetadata {
        val root = JSONObject(jsonStr)
        val name = root.optString("name", "My Video Vault")
        val maxCapacity = root.optLong("maxCapacityBytes", CAPACITY_30GB)
        val createdAt = root.optLong("createdAt", System.currentTimeMillis())
        val updatedAt = root.optLong("updatedAt", System.currentTimeMillis())
        val totalUsed = root.optLong("totalUsedBytes", 0L)

        val folders = mutableListOf<VidVaultFolder>()
        val foldersArr = root.optJSONArray("folders")
        if (foldersArr != null) {
            for (i in 0 until foldersArr.length()) {
                val fo = foldersArr.getJSONObject(i)
                folders.add(
                    VidVaultFolder(
                        id = fo.optString("id", UUID.randomUUID().toString()),
                        name = fo.optString("name", "Folder"),
                        createdAt = fo.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
        }
        if (folders.isEmpty()) {
            folders.add(VidVaultFolder(id = "default", name = "Main Videos"))
        }

        val records = mutableListOf<VidVaultRecord>()
        val recordsArr = root.optJSONArray("records")
        if (recordsArr != null) {
            for (i in 0 until recordsArr.length()) {
                val ro = recordsArr.getJSONObject(i)
                records.add(
                    VidVaultRecord(
                        id = ro.optString("id", UUID.randomUUID().toString()),
                        folderId = ro.optString("folderId", "").ifEmpty { null },
                        name = ro.optString("name", "video.mp4"),
                        sizeBytes = ro.optLong("sizeBytes", 0L),
                        durationMs = ro.optLong("durationMs", 0L),
                        mimeType = ro.optString("mimeType", "video/mp4"),
                        createdAt = ro.optLong("createdAt", System.currentTimeMillis()),
                        recordOffset = ro.optLong("recordOffset", 0L),
                        recordLength = ro.optLong("recordLength", 0L),
                        videoDataOffset = ro.optLong("videoDataOffset", 0L),
                        videoDataLength = ro.optLong("videoDataLength", 0L),
                        isDeleted = ro.optBoolean("isDeleted", false),
                        hasThumbnail = ro.optBoolean("hasThumbnail", true)
                    )
                )
            }
        }

        return VidVaultMetadata(
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

/**
 * Direct-from-storage random-access encrypted MediaDataSource.
 * Decrypts only the exact bytes requested by Android's MediaPlayer on-the-fly
 * using AES-256-CTR block counter arithmetic. Uses constant ~64 KB RAM regardless of video size.
 */
class EncryptedVideoDataSource(
    vaultFile: File,
    private val masterKey: SecretKeySpec,
    private val baseIv: ByteArray,
    private val dataOffset: Long,
    private val dataLength: Long
) : MediaDataSource() {

    private val raf = RandomAccessFile(vaultFile, "r")
    private val lock = Any()
    private val maxChunk = 64 * 1024
    private val encBuf = ByteArray(maxChunk + 16)
    private val decBuf = ByteArray(maxChunk + 32)
    @Volatile
    private var isClosed = false

    override fun getSize(): Long = dataLength

    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (isClosed || position >= dataLength) return -1
        if (size <= 0) return 0

        synchronized(lock) {
            if (isClosed || position >= dataLength) return -1

            val bytesToRead = minOf(size.toLong(), dataLength - position, maxChunk.toLong()).toInt()
            val blockIndex = position / 16L
            val skipInBlock = (position % 16L).toInt()
            val alignedDiskOffset = dataOffset + (blockIndex * 16L)
            val totalReadLen = skipInBlock + bytesToRead

            raf.seek(alignedDiskOffset)
            var actuallyRead = 0
            while (actuallyRead < totalReadLen) {
                val r = raf.read(encBuf, actuallyRead, totalReadLen - actuallyRead)
                if (r == -1) break
                actuallyRead += r
            }
            if (actuallyRead <= skipInBlock) return -1

            val counterIv = VidVaultEngine.incrementCtrIv(baseIv, blockIndex)
            val cipher = Cipher.getInstance("AES/CTR/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, masterKey, IvParameterSpec(counterIv))
            val decLen = cipher.update(encBuf, 0, actuallyRead, decBuf, 0)
            val usable = (decLen - skipInBlock).coerceAtMost(bytesToRead)
            if (usable <= 0) return -1

            System.arraycopy(decBuf, skipInBlock, buffer, offset, usable)
            return usable
        }
    }

    override fun close() {
        synchronized(lock) {
            if (!isClosed) {
                isClosed = true
                try {
                    raf.close()
                } catch (_: Throwable) {}
            }
        }
    }
}
