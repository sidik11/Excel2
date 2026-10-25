package com.example.util

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.CompressionLevel
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import java.io.File
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ZipOperationResult(
    val success: Boolean,
    val zipFile: File? = null,
    val fileCount: Int = 0,
    val totalSizeBytes: Long = 0L,
    val message: String = ""
)

object MediaZipEncryptionHelper {

    private const val MASTER_ZIP_NAME = "all_media_encrypted.zip"
    private const val LEGACY_ZIP_NAME = "media_imported_encrypted.zip"

    private val helperScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Resolves the user's active App Lock password/PIN.
     * Guaranteed never to be empty: falls back to active 6-digit PIN, stored PIN, or "123456".
     */
    fun getAppLockPassword(context: Context): String {
        val cfg = AppSecurityManager.securityConfig.value
        if (cfg.pinCode.isNotBlank()) {
            return cfg.pinCode
        }

        try {
            val secDir = AppStorageHelper.getSecurityDir(context)
            val pinFile = File(secDir, "pin_credential.dat")
            if (pinFile.exists()) {
                val lines = pinFile.readLines()
                if (lines.size >= 3) {
                    val p = lines[2].trim()
                    if (p.isNotBlank()) return p
                }
            }
        } catch (_: Throwable) {}

        return "123456"
    }

    /**
     * Dedicated location for the encrypted ZIP inside Android/media/<packageName>/encrypt.
     * This is the master encrypted vault file that external file managers see.
     */
    fun getDefaultEncryptedZipFile(context: Context): File {
        val encryptDir = AppStorageHelper.getEncryptedMediaDir(context)
        return File(encryptDir, MASTER_ZIP_NAME)
    }

    /**
     * Checks if the encrypted ZIP archive currently exists on disk.
     */
    fun doesEncryptedZipExist(context: Context): Boolean {
        val file = getDefaultEncryptedZipFile(context)
        if (file.exists() && file.length() > 0) return true
        val mirror = File(AppStorageHelper.getEncryptedMediaDir(context), LEGACY_ZIP_NAME)
        if (mirror.exists() && mirror.length() > 0) return true
        val legacy = File(AppStorageHelper.getDedicatedMediaDir(context), LEGACY_ZIP_NAME)
        return legacy.exists() && legacy.length() > 0
    }

    /**
     * Automatically migrates legacy unencrypted files from older versions,
     * keeps internal sandbox working copies for instant normal display inside the app,
     * and packages ALL media into Android/media/<packageName>/encrypt/all_media_encrypted.zip
     * protected with AES-256 and the user's active App Lock password.
     *
     * External file managers see ONLY the encrypted folder and password-protected archive.
     * Inside the app, all images and files display normally without manual steps.
     */
    fun autoMigrateAndEncryptAllMedia(context: Context): Boolean {
        return try {
            val password = getAppLockPassword(context)

            // Step 1: Migration for older users updating the app
            // Move legacy public files into the secure internal sandbox
            val legacyMediaDir = AppStorageHelper.getLegacyMediaImportedDir(context)
            val internalMediaDir = AppStorageHelper.getMediaImportedDir(context)
            if (legacyMediaDir.exists() && legacyMediaDir.absolutePath != internalMediaDir.absolutePath) {
                val legacyFiles = legacyMediaDir.listFiles() ?: emptyArray()
                for (file in legacyFiles) {
                    try {
                        val destFile = File(internalMediaDir, file.name)
                        if (!destFile.exists() || destFile.length() == 0L) {
                            file.copyTo(destFile, overwrite = true)
                        }
                        file.delete()
                    } catch (_: Throwable) {}
                }
                try { legacyMediaDir.delete() } catch (_: Throwable) {}
            }

            val legacyExVaultDir = AppStorageHelper.getLegacyExVaultDir(context)
            val internalExVaultDir = AppStorageHelper.getExVaultDir(context)
            if (legacyExVaultDir.exists() && legacyExVaultDir.absolutePath != internalExVaultDir.absolutePath) {
                val legacyFiles = legacyExVaultDir.listFiles() ?: emptyArray()
                for (file in legacyFiles) {
                    try {
                        val destFile = File(internalExVaultDir, file.name)
                        if (!destFile.exists() || destFile.length() == 0L) {
                            file.copyTo(destFile, overwrite = true)
                        }
                        file.delete()
                    } catch (_: Throwable) {}
                }
                try { legacyExVaultDir.delete() } catch (_: Throwable) {}
            }

            val legacySShowDir = AppStorageHelper.getLegacySShowStoredDir(context)
            val internalSShowDir = AppStorageHelper.getSShowStoredDir(context)
            if (legacySShowDir.exists() && legacySShowDir.absolutePath != internalSShowDir.absolutePath) {
                val legacyFiles = legacySShowDir.listFiles() ?: emptyArray()
                for (file in legacyFiles) {
                    try {
                        val destFile = File(internalSShowDir, file.name)
                        if (!destFile.exists() || destFile.length() == 0L) {
                            file.copyTo(destFile, overwrite = true)
                        }
                        file.delete()
                    } catch (_: Throwable) {}
                }
                try { legacySShowDir.delete() } catch (_: Throwable) {}
            }

            // Step 2: If internal storage is empty, but encrypted archive exists, restore it for normal in-app display!
            val masterZip = getDefaultEncryptedZipFile(context)
            val mirrorZip = File(AppStorageHelper.getEncryptedMediaDir(context), LEGACY_ZIP_NAME)
            val oldRootZip = File(AppStorageHelper.getDedicatedMediaDir(context), LEGACY_ZIP_NAME)
            val activeZip = when {
                masterZip.exists() && masterZip.length() > 0L -> masterZip
                mirrorZip.exists() && mirrorZip.length() > 0L -> mirrorZip
                oldRootZip.exists() && oldRootZip.length() > 0L -> oldRootZip
                else -> null
            }

            val internalFiles = internalMediaDir.listFiles()?.filter { it.isFile } ?: emptyList()
            if (internalFiles.isEmpty() && activeZip != null) {
                try {
                    val zipArchive = ZipFile(activeZip, password.toCharArray())
                    if (zipArchive.isValidZipFile) {
                        zipArchive.extractAll(context.filesDir.absolutePath)
                        val nested = File(internalMediaDir, "media_imported")
                        if (nested.exists() && nested.isDirectory) {
                            nested.listFiles()?.forEach { f ->
                                val target = File(internalMediaDir, f.name)
                                if (!target.exists()) f.copyTo(target, overwrite = true)
                                f.delete()
                            }
                            nested.delete()
                        }
                        SsiManager.refresh(context)
                    }
                } catch (_: Throwable) {}
            }

            // Step 3: Package all internal media into Android/media/<packageName>/encrypt/all_media_encrypted.zip
            val encryptDir = AppStorageHelper.getEncryptedMediaDir(context)
            if (!encryptDir.exists()) encryptDir.mkdirs()

            // Write clear instruction text for external file managers
            try {
                val readme = File(encryptDir, "README_ENCRYPTED.txt")
                readme.writeText(
                    "App Media Vault (AES-256 Encrypted)\n" +
                    "===================================\n" +
                    "All application media files are protected inside all_media_encrypted.zip.\n" +
                    "To open or view these files in third-party file managers or on a PC,\n" +
                    "enter your App Lock 6-digit password / PIN.\n",
                    StandardCharsets.UTF_8
                )
            } catch (_: Throwable) {}

            val targetZip = getDefaultEncryptedZipFile(context)
            val tempZip = File(encryptDir, "temp_vault.zip")
            if (tempZip.exists()) tempZip.delete()

            val zipArchive = ZipFile(tempZip, password.toCharArray())
            val zipParams = ZipParameters().apply {
                isEncryptFiles = true
                encryptionMethod = EncryptionMethod.AES
                aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
                compressionMethod = CompressionMethod.DEFLATE
                compressionLevel = CompressionLevel.NORMAL
            }

            var hasEntries = false

            // Add internal media imported directory
            if (internalMediaDir.exists() && (internalMediaDir.listFiles()?.isNotEmpty() == true)) {
                zipArchive.addFolder(internalMediaDir, zipParams)
                hasEntries = true
            }

            // Add internal EX Vault directory if it has files
            if (internalExVaultDir.exists() && (internalExVaultDir.listFiles()?.isNotEmpty() == true)) {
                zipArchive.addFolder(internalExVaultDir, zipParams)
                hasEntries = true
            }

            // Add internal SShow Stored directory if it has files
            if (internalSShowDir.exists() && (internalSShowDir.listFiles()?.isNotEmpty() == true)) {
                zipArchive.addFolder(internalSShowDir, zipParams)
                hasEntries = true
            }

            // For new users with no images yet: create a vault file so the encrypted file exists from day 1!
            if (!hasEntries) {
                val initNote = File(encryptDir, ".vault_init")
                try {
                    initNote.writeText("App Media Vault initialized. Encrypted with App Lock password.\n", StandardCharsets.UTF_8)
                    zipArchive.addFile(initNote, zipParams)
                    initNote.delete()
                } catch (_: Throwable) {}
            }

            if (tempZip.exists() && tempZip.length() > 0L) {
                if (targetZip.exists()) targetZip.delete()
                tempZip.renameTo(targetZip)

                // Also keep legacy name mirror inside encrypt/
                try {
                    val mirror = File(encryptDir, LEGACY_ZIP_NAME)
                    targetZip.copyTo(mirror, overwrite = true)
                } catch (_: Throwable) {}
            }

            true
        } catch (e: Throwable) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Asynchronously triggers auto-migration and encrypted archive synchronization.
     */
    fun syncToEncryptedArchiveAsync(context: Context) {
        val appCtx = context.applicationContext
        helperScope.launch {
            autoMigrateAndEncryptAllMedia(appCtx)
        }
    }

    /**
     * Backward-compatible suspend function to zip media folder.
     */
    suspend fun zipMediaFolder(
        context: Context,
        sourceDir: File = AppStorageHelper.getMediaImportedDir(context),
        targetZip: File = getDefaultEncryptedZipFile(context),
        password: String = getAppLockPassword(context),
        deleteSourceAfterZip: Boolean = false
    ): ZipOperationResult = withContext(Dispatchers.IO) {
        autoMigrateAndEncryptAllMedia(context)
        val file = getDefaultEncryptedZipFile(context)
        val count = AppStorageHelper.getMediaImportedDir(context).listFiles()?.filter { it.isFile }?.size ?: 0
        ZipOperationResult(
            success = file.exists() && file.length() > 0L,
            zipFile = file,
            fileCount = count,
            totalSizeBytes = file.length(),
            message = "Media encrypted in ${file.name} with App Lock password."
        )
    }

    /**
     * Backward-compatible suspend function to unzip media folder.
     */
    suspend fun unzipMediaFolder(
        context: Context,
        sourceZip: File = getDefaultEncryptedZipFile(context),
        destDir: File = AppStorageHelper.getMediaImportedDir(context),
        password: String = getAppLockPassword(context)
    ): ZipOperationResult = withContext(Dispatchers.IO) {
        try {
            if (!sourceZip.exists() || sourceZip.length() == 0L) {
                return@withContext ZipOperationResult(
                    success = false,
                    message = "Encrypted ZIP file does not exist on disk."
                )
            }

            if (!destDir.exists()) destDir.mkdirs()

            val zipFile = ZipFile(sourceZip, password.toCharArray())
            if (!zipFile.isValidZipFile) {
                return@withContext ZipOperationResult(
                    success = false,
                    message = "Invalid or corrupted ZIP archive."
                )
            }

            zipFile.extractAll(context.filesDir.absolutePath)
            val nested = File(destDir, "media_imported")
            if (nested.exists() && nested.isDirectory) {
                nested.listFiles()?.forEach { f ->
                    val target = File(destDir, f.name)
                    if (!target.exists()) f.copyTo(target, overwrite = true)
                    f.delete()
                }
                nested.delete()
            }
            SsiManager.refresh(context)

            val restoredFiles = destDir.listFiles()?.filter { it.isFile }?.size ?: 0
            ZipOperationResult(
                success = true,
                zipFile = sourceZip,
                fileCount = restoredFiles,
                totalSizeBytes = sourceZip.length(),
                message = "Successfully decrypted & restored $restoredFiles file(s) into app."
            )
        } catch (e: Exception) {
            e.printStackTrace()
            ZipOperationResult(
                success = false,
                message = "Decryption failed. Please verify password. (${e.localizedMessage ?: e.message})"
            )
        }
    }

    /**
     * Exports a copy of the encrypted ZIP to the public Downloads folder so the user can easily
     * copy it to PC or test opening with external apps like ZArchiver or Files by Google.
     */
    suspend fun exportZipToPublicDownloads(context: Context): Result<File> = withContext(Dispatchers.IO) {
        try {
            autoMigrateAndEncryptAllMedia(context)
            val sourceZip = getDefaultEncryptedZipFile(context)
            if (!sourceZip.exists() || sourceZip.length() == 0L) {
                return@withContext Result.failure(Exception("Encrypted media vault file not found."))
            }

            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()

            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val targetFile = File(downloadsDir, "All_Media_Encrypted_$timeStamp.zip")

            sourceZip.copyTo(targetFile, overwrite = true)
            Result.success(targetFile)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Hook called on App Lock: ensures encrypted archive is updated.
     */
    fun onAppLocked(context: Context) {
        syncToEncryptedArchiveAsync(context)
    }

    /**
     * Hook called on App Unlock: auto-migrates and encrypts all media,
     * ensuring internal app view has all images and external view has only encrypted files!
     */
    fun onAppUnlocked(context: Context) {
        syncToEncryptedArchiveAsync(context)
    }
}
