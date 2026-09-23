package com.example.util

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.util.Base64
import coil.Coil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class VaultImage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val file: File,
    val sizeBytes: Long,
    val docUri: Uri? = null
)

data class VaultContainerInfo(
    val fileName: String,
    val uri: Uri,
    val sizeBytes: Long
)

object VaultManager {

    private const val ITERATION_COUNT = 100_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_LENGTH = 16
    private const val IV_LENGTH = 12
    private const val TAG_LENGTH_BITS = 128
    private const val HEADER_SIZE = 4 // Legacy 4 bytes for UInt32 Little Endian data size

    // Version 3 Streaming format (prevents Conscrypt GCM 512MB RAM allocation crash)
    private const val MAGIC_V3 = 0x564C5433 // "VLT3"
    private const val V3_HEADER_SIZE = 12 // 4 bytes magic + 8 bytes data payload size
    private const val V3_IV_LENGTH = 16 // AES CBC IV

    private class LimitedInputStream(private val wrapped: InputStream, private var limit: Long) : InputStream() {
        override fun read(): Int {
            if (limit <= 0) return -1
            val result = wrapped.read()
            if (result != -1) limit--
            return result
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (limit <= 0) return -1
            val toRead = minOf(len.toLong(), limit).toInt()
            val read = wrapped.read(b, off, toRead)
            if (read > 0) limit -= read
            return read
        }

        override fun available(): Int {
            return minOf(wrapped.available().toLong(), limit).toInt()
        }
    }

    fun getExtractDir(context: Context): File {
        return File(AppStorageHelper.getDedicatedMediaDir(context), "vault_extracted").apply { mkdirs() }
    }

    private fun deriveKey(pin: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATION_COUNT, KEY_LENGTH_BITS)
        val secretKey = factory.generateSecret(spec)
        return SecretKeySpec(secretKey.encoded, "AES")
    }

    suspend fun encryptData(plaintext: ByteArray, pin: String): ByteArray = withContext(Dispatchers.Default) {
        val salt = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_LENGTH).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(pin, salt)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        val ciphertext = cipher.doFinal(plaintext)

        val result = ByteArray(SALT_LENGTH + IV_LENGTH + ciphertext.size)
        System.arraycopy(salt, 0, result, 0, SALT_LENGTH)
        System.arraycopy(iv, 0, result, SALT_LENGTH, IV_LENGTH)
        System.arraycopy(ciphertext, 0, result, SALT_LENGTH + IV_LENGTH, ciphertext.size)
        result
    }

    suspend fun decryptData(combined: ByteArray, pin: String): ByteArray = withContext(Dispatchers.Default) {
        if (combined.size < SALT_LENGTH + IV_LENGTH) {
            throw IllegalArgumentException("Data is too short to be a valid encrypted vault.")
        }
        val salt = combined.copyOfRange(0, SALT_LENGTH)
        val iv = combined.copyOfRange(SALT_LENGTH, SALT_LENGTH + IV_LENGTH)
        val ciphertext = combined.copyOfRange(SALT_LENGTH + IV_LENGTH, combined.size)

        val key = deriveKey(pin, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        cipher.doFinal(ciphertext)
    }

    suspend fun generateDatFile(
        context: Context,
        folderUri: Uri?,
        pin: String,
        targetSizeBytes: Long,
        onProgress: (Float, String) -> Unit
    ): Pair<String, Uri> = withContext(Dispatchers.IO) {
        onProgress(0.1f, "Initializing secure vault…")

        val fileName = "vault_${System.currentTimeMillis()}.dat"

        // Destination OutputStream: either SAF Document in folder or local app storage
        val (outputStream, resultUri) = if (folderUri != null) {
            val rootDocId = DocumentsContract.getTreeDocumentId(folderUri)
            val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, rootDocId)
            val newDocUri = DocumentsContract.createDocument(
                context.contentResolver,
                parentDocUri,
                "application/octet-stream",
                fileName
            ) ?: throw IllegalArgumentException("Cannot create .dat file in selected folder.")
            val os = context.contentResolver.openOutputStream(newDocUri)
                ?: throw IllegalArgumentException("Cannot open output stream.")
            Pair(os, newDocUri)
        } else {
            val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "vaults").apply { mkdirs() }
            val file = File(dir, fileName)
            Pair(FileOutputStream(file), Uri.fromFile(file))
        }

        onProgress(0.3f, "Encrypting empty vault container…")
        val emptyZipBytes = java.io.ByteArrayOutputStream().apply {
            ZipOutputStream(this).use { /* empty zip */ }
        }.toByteArray()

        val salt = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(V3_IV_LENGTH).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(pin, salt)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iv))
        val ciphertext = cipher.doFinal(emptyZipBytes)

        val totalDataPayloadSize = (SALT_LENGTH + V3_IV_LENGTH + ciphertext.size).toLong()
        if (V3_HEADER_SIZE + totalDataPayloadSize > targetSizeBytes) {
            throw IllegalArgumentException("Target size is too small for encrypted payload.")
        }

        onProgress(0.6f, "Allocating container ($fileName)…")
        outputStream.use { os ->
            val headerBuf = ByteBuffer.allocate(V3_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
            headerBuf.putInt(MAGIC_V3)
            headerBuf.putLong(totalDataPayloadSize)
            os.write(headerBuf.array())

            os.write(salt)
            os.write(iv)
            os.write(ciphertext)

            val remainingPadding = targetSizeBytes - (V3_HEADER_SIZE + totalDataPayloadSize)
            if (remainingPadding > 0) {
                val padChunk = ByteArray(64 * 1024) { 0xAA.toByte() }
                var written = 0L
                while (written < remainingPadding) {
                    val toWrite = minOf(padChunk.size.toLong(), remainingPadding - written).toInt()
                    os.write(padChunk, 0, toWrite)
                    written += toWrite
                }
            }
            os.flush()
        }

        onProgress(1.0f, "Vault created successfully!")
        Pair(fileName, resultUri)
    }

    suspend fun unlockAndExtract(
        context: Context,
        datUri: Uri,
        pin: String,
        onProgress: (Float, String) -> Unit
    ): List<VaultImage> = withContext(Dispatchers.IO) {
        onProgress(0.1f, "Reading vault container…")

        val inputStream: InputStream = context.contentResolver.openInputStream(datUri)
            ?: throw IllegalArgumentException("Cannot open .dat file.")

        // Read first 4 bytes to detect format (V3 MAGIC vs Legacy UInt32 dataSize)
        val first4 = ByteArray(4)
        val read4 = inputStream.read(first4)
        if (read4 < 4) {
            inputStream.close()
            throw IllegalArgumentException("Corrupted .dat file header.")
        }

        val magicOrDataSize = ByteBuffer.wrap(first4).order(ByteOrder.LITTLE_ENDIAN).int
        val isV3 = (magicOrDataSize == MAGIC_V3)

        val extractDir = getExtractDir(context)
        extractDir.listFiles()?.forEach { it.delete() }
        val imageList = mutableListOf<VaultImage>()

        if (isV3) {
            onProgress(0.2f, "Reading streaming secure container…")
            val sizeBytes = ByteArray(8)
            val readSize = inputStream.read(sizeBytes)
            if (readSize < 8) {
                inputStream.close()
                throw IllegalArgumentException("Corrupted V3 header.")
            }
            val totalDataPayloadSize = ByteBuffer.wrap(sizeBytes).order(ByteOrder.LITTLE_ENDIAN).long

            val salt = ByteArray(SALT_LENGTH)
            if (inputStream.read(salt) < SALT_LENGTH) {
                inputStream.close()
                throw IllegalArgumentException("Corrupted salt in vault.")
            }
            val iv = ByteArray(V3_IV_LENGTH)
            if (inputStream.read(iv) < V3_IV_LENGTH) {
                inputStream.close()
                throw IllegalArgumentException("Corrupted IV in vault.")
            }

            val cipherBytesToRead = totalDataPayloadSize - (SALT_LENGTH + V3_IV_LENGTH)
            if (cipherBytesToRead <= 0) {
                inputStream.close()
                throw IllegalArgumentException("Corrupted payload length.")
            }

            onProgress(0.35f, "Deriving encryption key…")
            val key = deriveKey(pin, salt)
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv))

            onProgress(0.5f, "Decrypting and unpacking images…")
            val limitedStream = LimitedInputStream(inputStream, cipherBytesToRead)
            val cis = CipherInputStream(limitedStream, cipher)

            // Direct streaming ZIP extraction from cipher stream (Zero RAM buffer!)
            try {
                val zis = java.util.zip.ZipInputStream(cis)
                val zipBuf = ByteArray(64 * 1024)
                var entry = zis.nextEntry
                var counter = 1
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val name = entry.name.substringAfterLast("/").ifBlank { "vault_img_$counter.jpg" }
                        val file = File(extractDir, name)
                        FileOutputStream(file).use { fos ->
                            var r: Int
                            while (zis.read(zipBuf).also { r = it } != -1) {
                                fos.write(zipBuf, 0, r)
                            }
                        }
                        imageList.add(VaultImage(name = name, file = file, sizeBytes = file.length()))
                        counter++
                        if (counter % 25 == 0) {
                            onProgress(0.5f + (counter.toFloat() / (counter + 100)) * 0.45f, "Unpacked $counter images…")
                        }
                    }
                    zis.closeEntry()
                    entry = try {
                        zis.nextEntry
                    } catch (_: Throwable) {
                        null
                    }
                }
                zis.close()
            } catch (t: Throwable) {
                inputStream.close()
                throw IllegalArgumentException("Wrong PIN ($pin) or corrupted vault data.")
            }

            try { inputStream.close() } catch (_: Throwable) {}
            onProgress(1.0f, "Unlocked ${imageList.size} images.")
            imageList
        } else {
            // Legacy V1/V2 container fallback
            val dataSize = magicOrDataSize
            if (dataSize <= 0 || dataSize.toLong() > 2048L * 1024L * 1024L) {
                inputStream.close()
                throw IllegalArgumentException("Invalid data length in vault ($dataSize bytes).")
            }

            onProgress(0.2f, "Reading salt and IV…")
            val salt = ByteArray(SALT_LENGTH)
            if (inputStream.read(salt) < SALT_LENGTH) {
                inputStream.close()
                throw IllegalArgumentException("Corrupted salt in vault payload.")
            }
            val iv = ByteArray(IV_LENGTH)
            if (inputStream.read(iv) < IV_LENGTH) {
                inputStream.close()
                throw IllegalArgumentException("Corrupted IV in vault payload.")
            }

            val cipherBytesToRead = dataSize - (SALT_LENGTH + IV_LENGTH)
            if (cipherBytesToRead <= 0) {
                inputStream.close()
                throw IllegalArgumentException("Corrupted payload size.")
            }

            onProgress(0.35f, "Deriving encryption key…")
            val key = deriveKey(pin, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))

            val tempDecryptedFile = File(context.cacheDir, "temp_dec_${System.currentTimeMillis()}.bin")
            try {
                onProgress(0.45f, "Decrypting legacy vault stream…")
                var remaining = cipherBytesToRead
                val inBuf = ByteArray(64 * 1024)
                FileOutputStream(tempDecryptedFile).use { fos ->
                    while (remaining > 0) {
                        val toRead = minOf(inBuf.size, remaining)
                        val r = inputStream.read(inBuf, 0, toRead)
                        if (r == -1) break
                        remaining -= r
                        val out = try {
                            cipher.update(inBuf, 0, r)
                        } catch (_: Throwable) {
                            throw IllegalArgumentException("Wrong PIN ($pin) or corrupted vault data.")
                        }
                        if (out != null && out.isNotEmpty()) {
                            fos.write(out)
                        }
                    }
                    val finalBytes = try {
                        cipher.doFinal()
                    } catch (_: Throwable) {
                        throw IllegalArgumentException("Wrong PIN ($pin) or corrupted vault data.")
                    }
                    if (finalBytes != null && finalBytes.isNotEmpty()) {
                        fos.write(finalBytes)
                    }
                }
                try { inputStream.close() } catch (_: Throwable) {}

                onProgress(0.7f, "Unpacking legacy images…")
                val isZip = FileInputStream(tempDecryptedFile).use { fis ->
                    val magic = ByteArray(4)
                    val readCount = fis.read(magic)
                    readCount == 4 && magic[0] == 0x50.toByte() && magic[1] == 0x4B.toByte() &&
                            magic[2] == 0x03.toByte() && magic[3] == 0x04.toByte()
                }

                if (isZip) {
                    val zis = java.util.zip.ZipInputStream(FileInputStream(tempDecryptedFile))
                    val zipBuf = ByteArray(64 * 1024)
                    var entry = zis.nextEntry
                    var counter = 1
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            val name = entry.name.substringAfterLast("/").ifBlank { "vault_img_$counter.jpg" }
                            val file = File(extractDir, name)
                            FileOutputStream(file).use { fos ->
                                var r: Int
                                while (zis.read(zipBuf).also { r = it } != -1) {
                                    fos.write(zipBuf, 0, r)
                                }
                            }
                            imageList.add(VaultImage(name = name, file = file, sizeBytes = file.length()))
                            counter++
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                    zis.close()
                } else {
                    // Legacy JSON fallback
                    FileInputStream(tempDecryptedFile).use { fis ->
                        val reader = android.util.JsonReader(java.io.InputStreamReader(fis, Charsets.UTF_8))
                        reader.beginObject()
                        while (reader.hasNext()) {
                            if (reader.nextName() == "images") {
                                reader.beginArray()
                                var counter = 1
                                while (reader.hasNext()) {
                                    reader.beginObject()
                                    var imgName = "vault_img_$counter.jpg"
                                    var dataUrl = ""
                                    while (reader.hasNext()) {
                                        when (reader.nextName()) {
                                            "name" -> imgName = reader.nextString()
                                            "dataUrl" -> dataUrl = reader.nextString()
                                            else -> reader.skipValue()
                                        }
                                    }
                                    reader.endObject()
                                    if (dataUrl.isNotEmpty()) {
                                        val base64Data = if (dataUrl.contains(",")) dataUrl.substringAfter(",") else dataUrl
                                        val file = File(extractDir, imgName)
                                        val bytes = Base64.decode(base64Data, Base64.DEFAULT)
                                        file.writeBytes(bytes)
                                        imageList.add(VaultImage(name = imgName, file = file, sizeBytes = file.length()))
                                        counter++
                                    }
                                }
                                reader.endArray()
                            } else {
                                reader.skipValue()
                            }
                        }
                        reader.endObject()
                    }
                }

                onProgress(1.0f, "Unlocked ${imageList.size} images.")
                imageList
            } finally {
                tempDecryptedFile.delete()
            }
        }
    }

    private suspend fun saveVaultInternal(
        context: Context,
        datUri: Uri,
        pin: String,
        images: List<VaultImage>,
        explicitTargetSizeBytes: Long? = null,
        onProgress: (Float, String) -> Unit
    ): Long = withContext(Dispatchers.IO) {
        onProgress(0.1f, "Compressing ${images.size} images…")

        val tempZipFile = File(context.cacheDir, "temp_vault_pack_${System.currentTimeMillis()}.zip")
        val tempEncryptedFile = File(context.cacheDir, "temp_vault_enc_${System.currentTimeMillis()}.bin")

        try {
            val total = images.size
            val usedEntryNames = mutableSetOf<String>()
            ZipOutputStream(FileOutputStream(tempZipFile).buffered()).use { zos ->
                val buf = ByteArray(64 * 1024)
                for ((idx, img) in images.withIndex()) {
                    if (img.file.exists() && img.file.length() > 0) {
                        var entryName = img.name.ifBlank { "img_$idx.jpg" }
                        var counter = 1
                        val base = entryName.substringBeforeLast('.')
                        val ext = entryName.substringAfterLast('.', "")
                        while (usedEntryNames.contains(entryName)) {
                            entryName = if (ext.isNotEmpty()) "${base}_$counter.$ext" else "${base}_$counter"
                            counter++
                        }
                        usedEntryNames.add(entryName)

                        val entry = ZipEntry(entryName)
                        zos.putNextEntry(entry)
                        FileInputStream(img.file).use { fis ->
                            var r: Int
                            while (fis.read(buf).also { r = it } != -1) {
                                zos.write(buf, 0, r)
                            }
                        }
                        zos.closeEntry()
                    }
                    if (total > 10 && (idx % 10 == 0 || idx == total - 1)) {
                        onProgress(0.1f + (idx.toFloat() / total) * 0.35f, "Compressed ${idx + 1} of $total images…")
                    }
                }
            }

            onProgress(0.5f, "Encrypting vault…")
            val salt = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
            val iv = ByteArray(V3_IV_LENGTH).also { SecureRandom().nextBytes(it) }
            val key = deriveKey(pin, salt)

            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iv))

            // Pure streaming encryption without storing the 500MB ciphertext in RAM
            FileOutputStream(tempEncryptedFile).buffered().use { fos ->
                CipherOutputStream(fos, cipher).use { cos ->
                    FileInputStream(tempZipFile).buffered().use { fis ->
                        val buf = ByteArray(64 * 1024)
                        var r: Int
                        while (fis.read(buf).also { r = it } != -1) {
                            cos.write(buf, 0, r)
                        }
                    }
                }
            }

            val cipherLength = tempEncryptedFile.length()
            val totalDataPayloadSize = (SALT_LENGTH + V3_IV_LENGTH).toLong() + cipherLength

            // Read or assign total container size
            var totalContainerSize = explicitTargetSizeBytes ?: 0L
            if (totalContainerSize <= 0L) {
                context.contentResolver.openFileDescriptor(datUri, "r")?.use { pfd ->
                    totalContainerSize = pfd.statSize
                }
            }
            val minRequiredSize = V3_HEADER_SIZE.toLong() + totalDataPayloadSize
            if (totalContainerSize < minRequiredSize) {
                // Auto-expand with 2MB headroom so large collections never fail
                totalContainerSize = minRequiredSize + (2L * 1024 * 1024)
            }

            onProgress(0.75f, "Writing to .dat container…")
            val outputStream = context.contentResolver.openOutputStream(datUri, "wt")
                ?: throw IllegalArgumentException("Cannot open .dat file for writing.")

            outputStream.use { os ->
                // Write V3 Header
                val headerBuffer = ByteBuffer.allocate(V3_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
                headerBuffer.putInt(MAGIC_V3)
                headerBuffer.putLong(totalDataPayloadSize)
                os.write(headerBuffer.array())

                // Write Salt and IV
                os.write(salt)
                os.write(iv)

                // Stream encrypted data from temp file in 64KB chunks
                val copyBuf = ByteArray(64 * 1024)
                FileInputStream(tempEncryptedFile).use { fis ->
                    var r: Int
                    while (fis.read(copyBuf).also { r = it } != -1) {
                        os.write(copyBuf, 0, r)
                    }
                }

                // Padding 0xAA in 64KB blocks without high RAM consumption
                val remainingPadding = totalContainerSize - (V3_HEADER_SIZE.toLong() + totalDataPayloadSize)
                if (remainingPadding > 0) {
                    val padChunk = ByteArray(64 * 1024) { 0xAA.toByte() }
                    var written = 0L
                    while (written < remainingPadding) {
                        val toWrite = minOf(padChunk.size.toLong(), remainingPadding - written).toInt()
                        os.write(padChunk, 0, toWrite)
                        written += toWrite
                    }
                }
                os.flush()
            }

            onProgress(1.0f, "Saved ${images.size} images successfully!")
            totalContainerSize
        } finally {
            tempZipFile.delete()
            tempEncryptedFile.delete()
        }
    }

    suspend fun saveVault(
        context: Context,
        datUri: Uri,
        pin: String,
        images: List<VaultImage>,
        onProgress: (Float, String) -> Unit
    ): Long = withContext(Dispatchers.IO) {
        saveVaultInternal(
            context = context,
            datUri = datUri,
            pin = pin,
            images = images,
            explicitTargetSizeBytes = null,
            onProgress = onProgress
        )
    }

    suspend fun resizeVault(
        context: Context,
        datUri: Uri,
        pin: String,
        images: List<VaultImage>,
        newTargetSizeBytes: Long,
        onProgress: (Float, String) -> Unit
    ) = withContext(Dispatchers.IO) {
        onProgress(0.2f, "Preparing resized container (${newTargetSizeBytes / (1024 * 1024)} MB)…")
        saveVaultInternal(
            context = context,
            datUri = datUri,
            pin = pin,
            images = images,
            explicitTargetSizeBytes = newTargetSizeBytes,
            onProgress = onProgress
        )
        onProgress(1.0f, "Resized to ${newTargetSizeBytes / (1024 * 1024)} MB!")
    }

    suspend fun resetPassword(
        context: Context,
        datUri: Uri,
        newPin: String,
        images: List<VaultImage>,
        onProgress: (Float, String) -> Unit
    ) = withContext(Dispatchers.IO) {
        onProgress(0.2f, "Re-encrypting with new PIN…")
        saveVault(context, datUri, newPin, images) { pct, msg ->
            onProgress(pct, msg)
        }
    }

    suspend fun exportToZip(
        context: Context,
        images: List<VaultImage>
    ): File = withContext(Dispatchers.IO) {
        val zipFile = File(context.cacheDir, "vault_export_${System.currentTimeMillis()}.zip")
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            for (img in images) {
                if (img.file.exists()) {
                    zos.putNextEntry(ZipEntry(img.name))
                    FileInputStream(img.file).use { fis ->
                        fis.copyTo(zos)
                    }
                    zos.closeEntry()
                }
            }
        }
        zipFile
    }

    suspend fun exportAllToGallery(context: Context, images: List<VaultImage>): Int = withContext(Dispatchers.IO) {
        var exported = 0
        for (img in images) {
            if (!img.file.exists()) continue
            try {
                val mime = when (img.file.extension.lowercase()) {
                    "png" -> "image/png"
                    "webp" -> "image/webp"
                    "gif" -> "image/gif"
                    else -> "image/jpeg"
                }
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, img.name)
                    put(android.provider.MediaStore.Images.Media.MIME_TYPE, mime)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/VaultImages")
                        put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
                    }
                }
                val uri = context.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        FileInputStream(img.file).use { fis ->
                            fis.copyTo(os)
                        }
                    }
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        values.clear()
                        values.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
                        context.contentResolver.update(uri, values, null, null)
                    }
                    exported++
                }
            } catch (e: Exception) {
                // Ignore individual copy failure
            }
        }
        exported
    }

    suspend fun saveZipToDownloads(context: Context, zipFile: File): Uri? = withContext(Dispatchers.IO) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Downloads.DISPLAY_NAME, zipFile.name)
                    put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/zip")
                    put(android.provider.MediaStore.Downloads.RELATIVE_PATH, "Download/VaultExports")
                    put(android.provider.MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        FileInputStream(zipFile).use { fis -> fis.copyTo(os) }
                    }
                    values.clear()
                    values.put(android.provider.MediaStore.Downloads.IS_PENDING, 0)
                    context.contentResolver.update(uri, values, null, null)
                    return@withContext uri
                }
            } else {
                val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                val targetFile = File(downloadsDir, zipFile.name)
                zipFile.copyTo(targetFile, overwrite = true)
                return@withContext Uri.fromFile(targetFile)
            }
        } catch (e: Exception) {
            // Fallback
        }
        null
    }

    fun shareZipFile(context: Context, zipFile: File) {
        try {
            val contentUri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                zipFile
            )
            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(android.content.Intent.EXTRA_STREAM, contentUri)
                putExtra(android.content.Intent.EXTRA_SUBJECT, "Vault Images Export")
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = android.content.Intent.createChooser(shareIntent, "Share or Save Vault ZIP").apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            // Fallback
        }
    }

    fun getOrCreateExVaultFolder(context: Context, folderUri: Uri?): Pair<Uri, String> {
        val dedicatedExVaultDir = AppStorageHelper.getExVaultDir(context)

        if (folderUri == null) {
            return Pair(Uri.fromFile(dedicatedExVaultDir), "Android/media/${context.packageName}/EX_Vault")
        }

        if (folderUri.scheme == "file") {
            val dir = File(folderUri.path ?: "")
            val targetDir = if (dir.isDirectory) dir else (dir.parentFile ?: dir)
            val exVault = File(targetDir, "EX Vault").apply { mkdirs() }
            return Pair(Uri.fromFile(exVault), "${targetDir.name}/EX Vault")
        }

        // Handle SAF tree Uri
        try {
            val rootDocId = DocumentsContract.getTreeDocumentId(folderUri)
            val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, rootDocId)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(folderUri, rootDocId)

            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            )

            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameCol)
                    val mime = cursor.getString(mimeCol)
                    if (name == "EX Vault" && mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        val docId = cursor.getString(idCol)
                        val uri = DocumentsContract.buildDocumentUriUsingTree(folderUri, docId)
                        val folderName = folderUri.lastPathSegment?.substringAfterLast(':') ?: "Storage"
                        return Pair(uri, "$folderName/EX Vault")
                    }
                }
            }

            // Not found, create directory
            val createdUri = DocumentsContract.createDocument(
                context.contentResolver,
                parentDocUri,
                DocumentsContract.Document.MIME_TYPE_DIR,
                "EX Vault"
            )
            val folderName = folderUri.lastPathSegment?.substringAfterLast(':') ?: "Storage"
            return Pair(createdUri ?: parentDocUri, "$folderName/EX Vault")
        } catch (_: Throwable) {
            return Pair(Uri.fromFile(dedicatedExVaultDir), "Android/media/${context.packageName}/EX_Vault")
        }
    }

    suspend fun syncImagesToExVault(
        context: Context,
        exVaultUri: Uri,
        images: List<VaultImage>,
        onProgress: ((Float, String) -> Unit)? = null
    ): List<VaultImage> = withContext(Dispatchers.IO) {
        val updatedImages = mutableListOf<VaultImage>()
        if (exVaultUri.scheme == "file") {
            val exVaultDir = File(exVaultUri.path ?: "")
            exVaultDir.mkdirs()
            images.forEachIndexed { i, img ->
                val target = File(exVaultDir, img.name)
                if (img.file.exists() && img.file.absolutePath != target.absolutePath) {
                    img.file.copyTo(target, overwrite = true)
                }
                updatedImages.add(img.copy(file = target, sizeBytes = target.length(), docUri = Uri.fromFile(target)))
                onProgress?.invoke(0.8f + 0.18f * ((i + 1).toFloat() / maxOf(images.size, 1)), "Extracted to EX Vault…")
            }
            return@withContext updatedImages
        }

        // SAF Tree URI
        try {
            val treeUri = exVaultUri
            val docId = DocumentsContract.getDocumentId(exVaultUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)

            val existingFiles = mutableMapOf<String, String>()
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME
            )
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val id = cursor.getString(idCol)
                    val name = cursor.getString(nameCol)
                    if (name != null) existingFiles[name] = id
                }
            }

            val total = images.size
            images.forEachIndexed { i, img ->
                val mime = when (img.file.extension.lowercase()) {
                    "png" -> "image/png"
                    "webp" -> "image/webp"
                    "gif" -> "image/gif"
                    else -> "image/jpeg"
                }

                val targetDocUri: Uri? = if (existingFiles.containsKey(img.name)) {
                    DocumentsContract.buildDocumentUriUsingTree(treeUri, existingFiles[img.name]!!)
                } else {
                    DocumentsContract.createDocument(context.contentResolver, exVaultUri, mime, img.name)
                }

                if (targetDocUri != null && img.file.exists()) {
                    context.contentResolver.openOutputStream(targetDocUri, "wt")?.use { os ->
                        FileInputStream(img.file).use { fis -> fis.copyTo(os) }
                    }
                }
                updatedImages.add(img.copy(docUri = targetDocUri))
                onProgress?.invoke(0.8f + 0.18f * ((i + 1).toFloat() / maxOf(total, 1)), "Extracted to EX Vault (${i + 1}/$total)…")
            }
        } catch (e: Exception) {
            return@withContext images
        }
        updatedImages
    }

    suspend fun detectAndScanExVault(
        context: Context,
        exVaultUri: Uri
    ): List<VaultImage> = withContext(Dispatchers.IO) {
        val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "avif")
        val extractDir = getExtractDir(context)
        val resultList = mutableListOf<VaultImage>()

        if (exVaultUri.scheme == "file") {
            val dir = File(exVaultUri.path ?: "")
            if (dir.exists() && dir.isDirectory) {
                try {
                    dir.walkTopDown().filter { it.isFile && it.extension.lowercase() in imageExtensions && it.length() > 0 }.forEach { file ->
                        resultList.add(
                            VaultImage(
                                name = file.name,
                                file = file,
                                sizeBytes = file.length(),
                                docUri = Uri.fromFile(file)
                            )
                        )
                    }
                } catch (_: Throwable) {}
            }
            return@withContext resultList.distinctBy { it.file.absolutePath }.sortedBy { it.name }
        }

        // Tree URI
        try {
            val docId = DocumentsContract.getDocumentId(exVaultUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(exVaultUri, docId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE
            )

            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)

                while (cursor.moveToNext()) {
                    val childDocId = cursor.getString(idCol)
                    val name = cursor.getString(nameCol) ?: continue
                    val mime = cursor.getString(mimeCol) ?: ""
                    val size = cursor.getLong(sizeCol)
                    val ext = name.substringAfterLast('.', "").lowercase()

                    val isImg = mime.startsWith("image/") || ext in imageExtensions
                    if (isImg && size > 0) {
                        val childDocUri = DocumentsContract.buildDocumentUriUsingTree(exVaultUri, childDocId)
                        val localCachedFile = File(extractDir, name)

                        if (!localCachedFile.exists() || localCachedFile.length() != size) {
                            context.contentResolver.openInputStream(childDocUri)?.use { input ->
                                FileOutputStream(localCachedFile).use { output ->
                                    input.copyTo(output)
                                }
                            }
                        }

                        resultList.add(
                            VaultImage(
                                name = name,
                                file = localCachedFile,
                                sizeBytes = localCachedFile.length(),
                                docUri = childDocUri
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            extractDir.listFiles()?.forEach { file ->
                val ext = file.extension.lowercase()
                if (ext in imageExtensions && file.length() > 0) {
                    resultList.add(VaultImage(name = file.name, file = file, sizeBytes = file.length()))
                }
            }
        }

        resultList.sortedBy { it.name }
    }

    suspend fun cleanExVaultFolder(context: Context, exVaultUri: Uri) = withContext(Dispatchers.IO) {
        if (exVaultUri.scheme == "file") {
            try {
                val dir = File(exVaultUri.path ?: "")
                if (dir.exists()) {
                    dir.listFiles()?.forEach { it.delete() }
                }
            } catch (_: Exception) {}
            return@withContext
        }

        try {
            val docId = DocumentsContract.getDocumentId(exVaultUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(exVaultUri, docId)
            val projection = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val docUrisToDelete = mutableListOf<Uri>()

            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                while (cursor.moveToNext()) {
                    val childId = cursor.getString(idCol)
                    docUrisToDelete.add(DocumentsContract.buildDocumentUriUsingTree(exVaultUri, childId))
                }
            }

            docUrisToDelete.forEach { docUri ->
                try {
                    DocumentsContract.deleteDocument(context.contentResolver, docUri)
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }

    /**
     * Completely deletes the EX Vault folder itself and all files within it across SAF, file directories,
     * external storage, internal storage, and dedicated media folders.
     */
    suspend fun deleteExVaultFolderCompletely(
        context: Context,
        exVaultUri: Uri? = null,
        parentFolderUri: Uri? = null
    ) = withContext(Dispatchers.IO) {
        // 1. Delete from provided URI or active session URI
        val targetUri = exVaultUri ?: VaultSessionManager.getSession(context).exVaultUri

        if (targetUri != null) {
            if (targetUri.scheme == "file") {
                try {
                    val dir = File(targetUri.path ?: "")
                    if (dir.exists()) {
                        dir.deleteRecursively()
                    }
                } catch (_: Throwable) {}
            } else {
                try {
                    val docId = DocumentsContract.getDocumentId(targetUri)
                    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(targetUri, docId)
                    val projection = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val docUrisToDelete = mutableListOf<Uri>()

                    context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                        val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                        while (cursor.moveToNext()) {
                            val childId = cursor.getString(idCol)
                            docUrisToDelete.add(DocumentsContract.buildDocumentUriUsingTree(targetUri, childId))
                        }
                    }

                    for (childDocUri in docUrisToDelete) {
                        try {
                            DocumentsContract.deleteDocument(context.contentResolver, childDocUri)
                        } catch (_: Throwable) {}
                    }

                    // Delete the folder document itself
                    try {
                        DocumentsContract.deleteDocument(context.contentResolver, targetUri)
                    } catch (_: Throwable) {}
                } catch (_: Throwable) {}
            }
        }

        // 2. Scan parent document tree for any "EX Vault" or "EX_Vault" directory documents and delete them
        val treeUri = parentFolderUri ?: VaultSessionManager.getSession(context).datUri
        if (treeUri != null && treeUri.scheme != "file") {
            try {
                val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, rootDocId)
                val projection = arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                )

                val exVaultDocUris = mutableListOf<Uri>()
                context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(nameCol)
                        val mime = cursor.getString(mimeCol)
                        if ((name == "EX Vault" || name == "EX_Vault") && mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            val childId = cursor.getString(idCol)
                            exVaultDocUris.add(DocumentsContract.buildDocumentUriUsingTree(treeUri, childId))
                        }
                    }
                }

                for (folderDocUri in exVaultDocUris) {
                    try {
                        val fDocId = DocumentsContract.getDocumentId(folderDocUri)
                        val fChildrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(folderDocUri, fDocId)
                        val childIds = mutableListOf<Uri>()
                        context.contentResolver.query(fChildrenUri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)?.use { c ->
                            val col = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                            while (c.moveToNext()) {
                                childIds.add(DocumentsContract.buildDocumentUriUsingTree(folderDocUri, c.getString(col)))
                            }
                        }
                        childIds.forEach { try { DocumentsContract.deleteDocument(context.contentResolver, it) } catch (_: Throwable) {} }
                        DocumentsContract.deleteDocument(context.contentResolver, folderDocUri)
                    } catch (_: Throwable) {}
                }
            } catch (_: Throwable) {}
        }

        // 3. Scan and delete all filesystem directory targets
        val targetDirs = listOf(
            AppStorageHelper.getExVaultDir(context),
            File(AppStorageHelper.getDedicatedMediaDir(context), "EX Vault"),
            File(AppStorageHelper.getDedicatedMediaDir(context), "EX_Vault"),
            File(context.filesDir, "EX Vault"),
            File(context.filesDir, "EX_Vault"),
            File(context.cacheDir, "EX Vault"),
            File(context.cacheDir, "EX_Vault"),
            context.getExternalFilesDir(null)?.let { File(it, "EX Vault") },
            context.getExternalFilesDir(null)?.let { File(it, "EX_Vault") },
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "EX Vault"),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "EX_Vault"),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "EX Vault"),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "EX_Vault"),
            File(Environment.getExternalStorageDirectory(), "EX Vault"),
            File(Environment.getExternalStorageDirectory(), "EX_Vault")
        )

        for (dir in targetDirs) {
            try {
                if (dir != null && dir.exists()) {
                    dir.deleteRecursively()
                }
            } catch (_: Throwable) {}
        }

        // 4. Clean memory and cache
        cleanExtractedFiles(context)
        AppStorageHelper.clearTempWorkspaces(context)
    }

    fun cleanExtractedFiles(context: Context) {
        try {
            getExtractDir(context).deleteRecursively()
            Coil.imageLoader(context).memoryCache?.clear()
            System.gc()
        } catch (_: Exception) {}
    }
}
