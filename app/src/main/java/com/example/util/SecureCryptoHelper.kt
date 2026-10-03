package com.example.util

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Base64InputStream
import android.util.Base64OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

data class SShowImage(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val file: File,
    val sizeBytes: Long
)

data class SharedImageRecord(
    val code: String,
    val file: File,
    val filename: String,
    val timestamp: Long
)

object SecureCryptoHelper {

    private val SALTED_HEADER = "Salted__".toByteArray(StandardCharsets.US_ASCII)

    fun getSShowExtractDir(context: Context): File {
        return AppStorageHelper.getSShowWorkDir(context)
    }

    fun getSShowStoredDir(context: Context): File {
        return AppStorageHelper.getSShowStoredDir(context)
    }

    fun getSShowSharedDir(context: Context): File {
        return AppStorageHelper.getSShowSharedDir(context)
    }

    /**
     * OpenSSL / CryptoJS EVP_BytesToKey implementation with MD5.
     * Generates 32-byte key (AES-256) and 16-byte IV.
     */
    private fun evpBytesToKey(password: String, salt: ByteArray): Pair<ByteArray, ByteArray> {
        val passBytes = password.toByteArray(StandardCharsets.UTF_8)
        val md = MessageDigest.getInstance("MD5")
        val keyAndIv = ByteArray(48) // 32 bytes key + 16 bytes IV
        var generated = 0
        var prevDigest = ByteArray(0)

        while (generated < 48) {
            md.reset()
            if (prevDigest.isNotEmpty()) {
                md.update(prevDigest)
            }
            md.update(passBytes)
            md.update(salt)
            prevDigest = md.digest()

            val toCopy = minOf(prevDigest.size, 48 - generated)
            System.arraycopy(prevDigest, 0, keyAndIv, generated, toCopy)
            generated += toCopy
        }

        val key = keyAndIv.copyOfRange(0, 32)
        val iv = keyAndIv.copyOfRange(32, 48)
        return Pair(key, iv)
    }

    /**
     * Encrypt a list of image files into a .secure file (OpenSSL / CryptoJS AES-256-CBC format).
     * Uses DIRECT DISK STREAMING (zero-RAM buffering) to prevent Out-Of-Memory errors.
     * Returns Pair(password, outputFile)
     */
    suspend fun encryptImagesToSecureFile(
        context: Context,
        imageFiles: List<File>,
        customName: String,
        providedPin: String? = null,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Pair<String, File> = withContext(Dispatchers.IO) {
        onProgress(0.1f, "Initializing secure storage stream…")

        // 3-digit password (e.g. "482")
        val pin = providedPin ?: (100 + SecureRandom().nextInt(900)).toString()
        val reversedPin = pin.reversed()

        val salt = ByteArray(8).also { SecureRandom().nextBytes(it) }
        val (key, iv) = evpBytesToKey(pin, salt)

        val cleanName = customName.ifBlank { "SecureImages" }.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val fileName = "$cleanName-$reversedPin.secure"
        val outDir = AppStorageHelper.getSShowSecureDir(context)
        val outFile = File(outDir, fileName)

        onProgress(0.3f, "Encrypting & streaming directly to storage…")

        try {
            FileOutputStream(outFile).use { fos ->
                Base64OutputStream(fos, Base64.NO_WRAP).use { b64os ->
                    // 1. Write Salted header & salt
                    b64os.write(SALTED_HEADER)
                    b64os.write(salt)
                    b64os.flush()

                    // 2. Stream cipher output directly through Base64 stream
                    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))

                    CipherOutputStream(b64os, cipher).use { cos ->
                        ZipOutputStream(cos).use { zos ->
                            val buf = ByteArray(32 * 1024)
                            val total = imageFiles.size
                            for ((idx, file) in imageFiles.withIndex()) {
                                if (file.exists() && file.length() > 0) {
                                    val entry = ZipEntry(file.name)
                                    zos.putNextEntry(entry)
                                    FileInputStream(file).use { fis ->
                                        var r: Int
                                        while (fis.read(buf).also { r = it } != -1) {
                                            zos.write(buf, 0, r)
                                        }
                                    }
                                    zos.closeEntry()
                                }
                                if (total > 5 && idx % 3 == 0) {
                                    onProgress(0.3f + (idx.toFloat() / total) * 0.6f, "Encrypted ${idx + 1} of $total images…")
                                }
                            }
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            outFile.delete()
            throw IllegalArgumentException("Encryption failed: ${t.message}")
        }

        onProgress(1.0f, "File created: $fileName")
        Pair(pin, outFile)
    }

    /**
     * Decrypt a .secure file using a 3-digit PIN.
     * Uses DIRECT DISK STREAMING (Base64 -> Cipher -> ZIP -> Disk) without loading into RAM.
     */
    suspend fun decryptSecureFile(
        context: Context,
        secureUri: Uri,
        pin: String,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): List<SShowImage> = withContext(Dispatchers.IO) {
        onProgress(0.1f, "Opening encrypted file…")

        val inputStream: InputStream = try {
            context.contentResolver.openInputStream(secureUri)
                ?: throw IllegalArgumentException("Cannot open secure file.")
        } catch (t: Throwable) {
            throw IllegalArgumentException("Cannot access file: ${t.message}")
        }

        val extractDir = getSShowExtractDir(context)
        // Clean out previous session files
        try {
            extractDir.listFiles()?.forEach { it.delete() }
        } catch (_: Throwable) {}

        val imageList = mutableListOf<SShowImage>()

        try {
            // Stream decoding Base64 directly from storage
            Base64InputStream(inputStream, Base64.DEFAULT).use { b64is ->
                onProgress(0.25f, "Verifying header and salt…")

                val header = ByteArray(8)
                var readHeader = 0
                while (readHeader < 8) {
                    val r = b64is.read(header, readHeader, 8 - readHeader)
                    if (r == -1) break
                    readHeader += r
                }

                if (readHeader < 8 || !header.contentEquals(SALTED_HEADER)) {
                    throw IllegalArgumentException("Not a valid .secure file (missing Salted__ signature).")
                }

                val salt = ByteArray(8)
                var readSalt = 0
                while (readSalt < 8) {
                    val r = b64is.read(salt, readSalt, 8 - readSalt)
                    if (r == -1) break
                    readSalt += r
                }

                if (readSalt < 8) {
                    throw IllegalArgumentException("Corrupted salt in .secure file.")
                }

                onProgress(0.45f, "Deriving key for PIN $pin…")
                val (key, iv) = evpBytesToKey(pin, salt)

                val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))

                onProgress(0.6f, "Decrypting archive directly to storage…")

                CipherInputStream(b64is, cipher).use { cis ->
                    ZipInputStream(cis).use { zis ->
                        val buf = ByteArray(32 * 1024)
                        var counter = 1

                        while (true) {
                            val entry = try {
                                zis.nextEntry
                            } catch (t: Throwable) {
                                throw IllegalArgumentException("Incorrect PIN ($pin) or corrupted data.")
                            } ?: break

                            if (!entry.isDirectory) {
                                val cleanName = entry.name.substringAfterLast("/").ifBlank { "image_$counter.jpg" }
                                val targetFile = File(extractDir, cleanName)
                                FileOutputStream(targetFile).use { fos ->
                                    var r: Int
                                    while (true) {
                                        val bytesRead = try {
                                            zis.read(buf).also { r = it }
                                        } catch (t: Throwable) {
                                            throw IllegalArgumentException("Incorrect PIN ($pin) or corrupted data.")
                                        }
                                        if (bytesRead == -1) break
                                        fos.write(buf, 0, bytesRead)
                                    }
                                }
                                if (targetFile.exists() && targetFile.length() > 0) {
                                    imageList.add(
                                        SShowImage(
                                            name = cleanName,
                                            file = targetFile,
                                            sizeBytes = targetFile.length()
                                        )
                                    )
                                    counter++
                                }
                            }
                            zis.closeEntry()
                        }
                    }
                }
            }
        } catch (e: IllegalArgumentException) {
            // Clean partial extractions on wrong password
            extractDir.listFiles()?.forEach { it.delete() }
            throw e
        } catch (t: Throwable) {
            extractDir.listFiles()?.forEach { it.delete() }
            throw IllegalArgumentException("Incorrect PIN ($pin) or corrupted file.")
        }

        if (imageList.isEmpty()) {
            throw IllegalArgumentException("No images found in container or incorrect PIN.")
        }

        onProgress(1.0f, "Unlocked ${imageList.size} images!")
        imageList
    }

    /**
     * Copy a file to the system Downloads folder so the user has the latest updated file.
     */
    fun exportToPublicDownloads(context: Context, sourceFile: File, displayName: String): Uri? {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = context.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        FileInputStream(sourceFile).use { fis -> fis.copyTo(os) }
                    }
                }
                uri
            } else {
                @Suppress("DEPRECATION")
                val dlDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                if (!dlDir.exists()) dlDir.mkdirs()
                val target = File(dlDir, displayName)
                FileInputStream(sourceFile).use { fis ->
                    FileOutputStream(target).use { fos -> fis.copyTo(fos) }
                }
                Uri.fromFile(target)
            }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Add images to an existing .secure file.
     * Safely isolates incoming images from session directory cleanup,
     * re-encrypts the full combined set, and updates the source SAF URI if writable.
     */
    suspend fun addImagesToExistingSecure(
        context: Context,
        secureUri: Uri,
        newImages: List<File>,
        pin: String,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): File = withContext(Dispatchers.IO) {
        onProgress(0.1f, "Staging incoming images safely…")
        // 1. Isolate new images so decrypt's directory wipe can NEVER touch them
        val isolatedDir = File(context.cacheDir, "sshow_add_isolated_${UUID.randomUUID()}").apply { mkdirs() }
        val preservedNewFiles = mutableListOf<File>()
        val buf = ByteArray(32 * 1024)

        for ((idx, srcFile) in newImages.withIndex()) {
            if (srcFile.exists() && srcFile.length() > 0) {
                val targetName = "add_${System.currentTimeMillis()}_${idx}_${srcFile.name}"
                val targetFile = File(isolatedDir, targetName)
                FileInputStream(srcFile).use { fis ->
                    FileOutputStream(targetFile).use { fos ->
                        var r: Int
                        while (fis.read(buf).also { r = it } != -1) {
                            fos.write(buf, 0, r)
                        }
                    }
                }
                preservedNewFiles.add(targetFile)
            }
        }

        onProgress(0.25f, "Decrypting existing container…")
        val currentImages = decryptSecureFile(context, secureUri, pin, onProgress)

        // 2. Copy preserved new images into active extractDir so user can preview all immediately
        val extractDir = getSShowExtractDir(context)
        val finalFiles = mutableListOf<File>()
        finalFiles.addAll(currentImages.map { it.file })

        for (newFile in preservedNewFiles) {
            val inExtract = File(extractDir, newFile.name)
            FileInputStream(newFile).use { fis ->
                FileOutputStream(inExtract).use { fos ->
                    var r: Int
                    while (fis.read(buf).also { r = it } != -1) {
                        fos.write(buf, 0, r)
                    }
                }
            }
            finalFiles.add(inExtract)
        }

        onProgress(0.5f, "Re-encrypting all ${finalFiles.size} images…")
        val baseName = secureUri.lastPathSegment?.substringAfterLast("/")?.substringBeforeLast(".secure")?.substringBeforeLast("-") ?: "UpdatedSecure"
        val (_, outFile) = encryptImagesToSecureFile(context, finalFiles, baseName, pin, onProgress)

        // 3. Write back directly to the original SAF Document URI if it has write permissions
        try {
            context.contentResolver.openOutputStream(secureUri, "wt")?.use { os ->
                FileInputStream(outFile).use { fis ->
                    fis.copyTo(os)
                }
            }
        } catch (_: Throwable) {
            // If SAF URI is read-only, outFile in SShowSecureDir is ready
        }

        // 4. Also copy the updated file to the device Downloads folder for instant access
        exportToPublicDownloads(context, outFile, outFile.name)

        // 5. Clean up staging folder
        try {
            isolatedDir.deleteRecursively()
        } catch (_: Throwable) {}

        onProgress(1.0f, "Updated container created with ${finalFiles.size} images!")
        outFile
    }

    /**
     * Store active decrypted images in internal storage permanently.
     */
    suspend fun storeInLocalStorage(context: Context, images: List<SShowImage>): Int = withContext(Dispatchers.IO) {
        val storedDir = getSShowStoredDir(context)
        var count = 0
        val buf = ByteArray(32 * 1024)
        for (img in images) {
            if (img.file.exists()) {
                val target = File(storedDir, img.name)
                FileInputStream(img.file).use { fis ->
                    FileOutputStream(target).use { fos ->
                        var r: Int
                        while (fis.read(buf).also { r = it } != -1) {
                            fos.write(buf, 0, r)
                        }
                    }
                }
                count++
            }
        }
        count
    }

    /**
     * Merge active images into storage without duplicates.
     */
    suspend fun mergeToLocalStorage(context: Context, images: List<SShowImage>): Int = withContext(Dispatchers.IO) {
        val storedDir = getSShowStoredDir(context)
        val buf = ByteArray(32 * 1024)
        for (img in images) {
            if (img.file.exists()) {
                val target = File(storedDir, img.name)
                if (!target.exists()) {
                    FileInputStream(img.file).use { fis ->
                        FileOutputStream(target).use { fos ->
                            var r: Int
                            while (fis.read(buf).also { r = it } != -1) {
                                fos.write(buf, 0, r)
                            }
                        }
                    }
                }
            }
        }
        storedDir.listFiles()?.count { it.isFile && it.length() > 0 } ?: 0
    }

    /**
     * Load stored images from device storage.
     */
    suspend fun loadFromLocalStorage(context: Context): List<SShowImage> = withContext(Dispatchers.IO) {
        val storedDir = getSShowStoredDir(context)
        val files = storedDir.listFiles()?.filter { it.isFile && it.length() > 0 } ?: emptyList()
        files.map {
            SShowImage(
                name = it.name,
                file = it,
                sizeBytes = it.length()
            )
        }
    }

    /**
     * Share a single image with a generated 5-character alphanumeric code.
     */
    suspend fun shareImageWithCode(context: Context, imageFile: File): String = withContext(Dispatchers.IO) {
        val sharedDir = getSShowSharedDir(context)
        val chars = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
        val code = (1..5).map { chars[SecureRandom().nextInt(chars.length)] }.joinToString("")
        val ext = imageFile.extension.ifBlank { "jpg" }
        val target = File(sharedDir, "$code.$ext")

        val buf = ByteArray(32 * 1024)
        FileInputStream(imageFile).use { fis ->
            FileOutputStream(target).use { fos ->
                var r: Int
                while (fis.read(buf).also { r = it } != -1) {
                    fos.write(buf, 0, r)
                }
            }
        }
        code
    }

    /**
     * Retrieve shared image by 5-character code.
     */
    suspend fun getSharedImageByCode(context: Context, code: String): File? = withContext(Dispatchers.IO) {
        val sharedDir = getSShowSharedDir(context)
        val cleanCode = code.trim()
        val match = sharedDir.listFiles()?.firstOrNull { it.name.substringBeforeLast(".").equals(cleanCode, ignoreCase = true) }
        match?.takeIf { it.exists() && it.length() > 0 }
    }
}
