package com.example.util

import android.content.Context
import android.os.Environment
import java.io.File

object AppStorageHelper {

    /**
     * Autocreates and returns the dedicated storage folder in Android/media/<packageName>.
     * This directory allows direct file read/write without RAM buffering and without
     * requiring MANAGE_EXTERNAL_STORAGE permissions.
     */
    fun getDedicatedMediaDir(context: Context): File {
        val mediaDirs = try {
            context.externalMediaDirs
        } catch (_: Throwable) {
            null
        }

        val primaryMediaDir = mediaDirs?.firstOrNull() ?: run {
            val extDir = Environment.getExternalStorageDirectory()
            File(extDir, "Android/media/${context.packageName}")
        }

        val fallbackDir = context.getExternalFilesDir(null) ?: context.filesDir

        val dir = try {
            if (!primaryMediaDir.exists()) {
                primaryMediaDir.mkdirs()
            }
            if (primaryMediaDir.canWrite()) primaryMediaDir else fallbackDir
        } catch (_: Throwable) {
            fallbackDir
        }

        return dir
    }

    /**
     * Dedicated EX Vault folder on internal storage (in Android/media).
     * Accessible directly by phone file managers.
     */
    fun getExVaultDir(context: Context): File {
        val dir = File(getDedicatedMediaDir(context), "EX_Vault")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Dedicated security directory in Android/media/<packageName>/security.
     * Stores encrypted credentials, 6-digit PIN hashes, biometric keys, and security configurations.
     */
    fun getSecurityDir(context: Context): File {
        val dir = File(getDedicatedMediaDir(context), "security")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Dedicated folder for registered fingerprint backups in Android/media/<packageName>/security/fingerprint.
     */
    fun getFingerprintDir(context: Context): File {
        val dir = File(getSecurityDir(context), "fingerprint")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Dedicated folder for face lock registration data and biometric calibration in Android/media/<packageName>/facelock.
     */
    fun getFaceLockDir(context: Context): File {
        val dir = File(getDedicatedMediaDir(context), "facelock")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Dedicated folder for user profile data and profile photo in Android/media/<packageName>/profile.
     */
    fun getProfileMediaDir(context: Context): File {
        val dir = File(getDedicatedMediaDir(context), "profile")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Dedicated folder for SShow .secure files.
     */
    fun getSShowSecureDir(context: Context): File {
        val dir = File(getDedicatedMediaDir(context), "SShow_Secure")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Dedicated folder for SShow decrypted images during active session.
     */
    fun getSShowWorkDir(context: Context): File {
        val dir = File(getDedicatedMediaDir(context), "SShow_Decrypted")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Dedicated folder for SShow persistent stored images.
     */
    fun getSShowStoredDir(context: Context): File {
        val dir = File(getDedicatedMediaDir(context), "SShow_Stored")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Dedicated folder for SShow shared image codes.
     */
    fun getSShowSharedDir(context: Context): File {
        val dir = File(getDedicatedMediaDir(context), "SShow_Shared")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Dedicated folder for generated DAT files.
     */
    fun getVaultStorageDir(context: Context): File {
        val dir = File(getDedicatedMediaDir(context), "Vault_Containers")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Dedicated folder for Dual Combined Vault storage in Android/media/<packageName>/Dual_Vault.
     */
    fun getDualVaultDir(context: Context): File {
        val dir = File(getDedicatedMediaDir(context), "Dual_Vault")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Dedicated custom app logo file in Android/media/<packageName>/app_logo.png.
     */
    fun getAppLogoFile(context: Context): File {
        return File(getDedicatedMediaDir(context), "app_logo.png")
    }

    /**
     * Calculate total storage occupied by dedicated folders.
     */
    fun getTotalStorageSizeBytes(context: Context): Long {
        return try {
            val root = getDedicatedMediaDir(context)
            calculateDirSize(root)
        } catch (_: Throwable) {
            0L
        }
    }

    private fun calculateDirSize(dir: File): Long {
        var size = 0L
        val list = dir.listFiles() ?: return 0L
        for (f in list) {
            size += if (f.isDirectory) calculateDirSize(f) else f.length()
        }
        return size
    }

    /**
     * Cleans temporary decrypted workspaces to free up storage.
     */
    fun clearTempWorkspaces(context: Context) {
        try {
            getSShowWorkDir(context).listFiles()?.forEach { it.delete() }
            File(context.cacheDir, "sshow_temp").deleteRecursively()
            File(context.cacheDir, "vault_temp").deleteRecursively()
        } catch (_: Throwable) {}
    }
}
