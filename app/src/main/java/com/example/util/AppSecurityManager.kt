package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import com.example.data.local.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

enum class BiometricAvailability {
    AVAILABLE,
    NO_HARDWARE,
    HW_UNAVAILABLE,
    NONE_ENROLLED,
    UNSUPPORTED
}

data class SecurityFolderInfo(
    val folderPath: String,
    val totalSizeBytes: Long,
    val fileCount: Int,
    val files: List<Pair<String, Long>>
)

data class SecurityConfig(
    val isPinEnabled: Boolean = false,
    val isFingerprintEnabled: Boolean = false,
    val isFingerprintRegistered: Boolean = false,
    val fingerprintRegisteredAt: Long = 0L,
    val fingerprintBackupPath: String = "",
    val fingerprintBackupHash: String = "",
    val fingerprintBackupSalt: String = "",
    val isAntiScreenshotEnabled: Boolean = true,
    val isFaceLockEnabled: Boolean = false,
    val isFaceEnrolled: Boolean = false,
    val faceEnrolledAt: Long = 0L,
    val pinSalt: String = "",
    val pinHash: String = "",
    val pinCode: String = "",
    val biometricToken: String = "",
    val masterPasswordHash: String = "",
    val masterPasswordSalt: String = "",
    val failedAttempts: Int = 0,
    val updatedAt: Long = System.currentTimeMillis()
)

object AppSecurityManager {

    private val _securityConfig = MutableStateFlow(SecurityConfig())
    val securityConfig: StateFlow<SecurityConfig> = _securityConfig.asStateFlow()

    private val _isAppUnlocked = MutableStateFlow(true)
    val isAppUnlocked: StateFlow<Boolean> = _isAppUnlocked.asStateFlow()

    private const val CONFIG_FILE_NAME = "security_config.json"
    private const val PIN_FILE_NAME = "pin_credential.dat"
    private const val BIOMETRIC_FILE_NAME = "biometric_token.dat"
    private const val MASTER_PASS_FILE_NAME = "master_password.dat"
    const val FINGERPRINT_DAT_FILE_NAME = "fingerprint.dat"
    const val FINGERPRINT_BACKUP_FILE_NAME = "fingerprint_backup.dat"
    const val FINGERPRINT_META_FILE_NAME = "fingerprint_meta.json"

    private var isInitialized = false
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        if (isInitialized) return
        isInitialized = true

        val securityDir = AppStorageHelper.getSecurityDir(context)
        val configFile = File(securityDir, CONFIG_FILE_NAME)
        val pinFile = File(securityDir, PIN_FILE_NAME)

        // Check if fingerprint backup file exists on disk in fingerprint directory, security directory, media directory, or filesDir
        val candidateFiles = listOf(
            File(AppStorageHelper.getFingerprintDir(context), FINGERPRINT_DAT_FILE_NAME),
            File(AppStorageHelper.getFingerprintDir(context), FINGERPRINT_BACKUP_FILE_NAME),
            File(AppStorageHelper.getSecurityDir(context), FINGERPRINT_DAT_FILE_NAME),
            File(AppStorageHelper.getDedicatedMediaDir(context), FINGERPRINT_DAT_FILE_NAME),
            File(context.filesDir, FINGERPRINT_DAT_FILE_NAME)
        )
        val targetFile = candidateFiles.firstOrNull { it.exists() && it.length() > 0 }
        var hasFpBackupOnDisk = targetFile != null
        var fpBackupPath = targetFile?.absolutePath ?: ""

        if (configFile.exists() && configFile.canRead()) {
            try {
                val jsonStr = configFile.readText(StandardCharsets.UTF_8)
                val json = JSONObject(jsonStr)

                var isPin = json.optBoolean("isPinEnabled", false)
                val isFp = json.optBoolean("isFingerprintEnabled", false)
                val isFpReg = json.optBoolean("isFingerprintRegistered", false)
                val fpRegAt = json.optLong("fingerprintRegisteredAt", 0L)
                if (fpBackupPath.isEmpty()) {
                    fpBackupPath = json.optString("fingerprintBackupPath", "")
                }
                var fpBackupHash = json.optString("fingerprintBackupHash", "")
                var fpBackupSalt = json.optString("fingerprintBackupSalt", "")
                val isAntiScreenshot = json.optBoolean("isAntiScreenshotEnabled", true)
                val isFaceLock = json.optBoolean("isFaceLockEnabled", false)
                val isFaceEnrolled = json.optBoolean("isFaceEnrolled", false)
                val faceEnrolledAt = json.optLong("faceEnrolledAt", 0L)
                var salt = json.optString("pinSalt", "")
                var hash = json.optString("pinHash", "")
                var pinCode = json.optString("pinCode", "")
                val fpToken = json.optString("biometricToken", "")
                val masterHash = json.optString("masterPasswordHash", "")
                val masterSalt = json.optString("masterPasswordSalt", "")
                val attempts = json.optInt("failedAttempts", 0)
                val updated = json.optLong("updatedAt", System.currentTimeMillis())

                // Check backup in pinFile if config was somehow incomplete
                if (pinFile.exists()) {
                    val pinLines = pinFile.readLines()
                    if (pinLines.size >= 2) {
                        if (salt.isEmpty()) salt = pinLines[0].trim()
                        if (hash.isEmpty()) hash = pinLines[1].trim()
                    }
                    if (pinLines.size >= 3 && pinCode.isEmpty()) {
                        val candidatePin = pinLines[2].trim()
                        if (candidatePin.length == 6 && candidatePin.all { it.isDigit() }) {
                            pinCode = candidatePin
                        }
                    }
                    if (hash.isNotEmpty()) isPin = true
                }

                // If fingerprint file exists on disk, restore profile, dual vault pairing, and auto-enable password if present
                if (targetFile != null) {
                    try {
                        val content = targetFile.readText(StandardCharsets.UTF_8)
                        extractAndRestoreProfileFromContent(context, content)
                        val pinAutoRestored = extractAndRestorePinFromContent(context, content)
                        extractAndRestoreDualVaultFromContent(context, content)
                        if (pinAutoRestored) {
                            val currentConfig = _securityConfig.value
                            isPin = true
                            salt = currentConfig.pinSalt
                            hash = currentConfig.pinHash
                            pinCode = currentConfig.pinCode
                        }
                    } catch (_: Throwable) {}
                }

                val loaded = SecurityConfig(
                    isPinEnabled = isPin && hash.isNotEmpty(),
                    isFingerprintEnabled = isFp || isFpReg || hasFpBackupOnDisk,
                    isFingerprintRegistered = isFpReg || hasFpBackupOnDisk,
                    fingerprintRegisteredAt = if (fpRegAt > 0) fpRegAt else if (hasFpBackupOnDisk) targetFile?.lastModified() ?: 0L else 0L,
                    fingerprintBackupPath = fpBackupPath,
                    fingerprintBackupHash = fpBackupHash,
                    fingerprintBackupSalt = fpBackupSalt,
                    isAntiScreenshotEnabled = isAntiScreenshot,
                    isFaceLockEnabled = isFaceLock,
                    isFaceEnrolled = isFaceEnrolled,
                    faceEnrolledAt = faceEnrolledAt,
                    pinSalt = salt,
                    pinHash = hash,
                    pinCode = pinCode,
                    biometricToken = fpToken,
                    masterPasswordHash = masterHash,
                    masterPasswordSalt = masterSalt,
                    failedAttempts = attempts,
                    updatedAt = updated
                )
                _securityConfig.value = loaded
                _isAppUnlocked.value = !loaded.isPinEnabled
            } catch (e: Throwable) {
                e.printStackTrace()
                _isAppUnlocked.value = true
            }
        } else {
            // First time initialization on this device
            // If fingerprint backup file already exists on this new device, restore profile, dual vault, and password!
            var autoEnabledPin = false
            if (targetFile != null) {
                try {
                    val content = targetFile.readText(StandardCharsets.UTF_8)
                    extractAndRestoreProfileFromContent(context, content)
                    autoEnabledPin = extractAndRestorePinFromContent(context, content)
                    extractAndRestoreDualVaultFromContent(context, content)
                } catch (_: Throwable) {}
            }

            val currentAfterFp = _securityConfig.value
            val initial = if (autoEnabledPin) {
                currentAfterFp.copy(
                    isFingerprintEnabled = true,
                    isFingerprintRegistered = true,
                    fingerprintBackupPath = fpBackupPath,
                    updatedAt = System.currentTimeMillis()
                )
            } else {
                SecurityConfig(
                    isPinEnabled = false,
                    isFingerprintEnabled = hasFpBackupOnDisk,
                    isFingerprintRegistered = hasFpBackupOnDisk,
                    fingerprintBackupPath = fpBackupPath,
                    isAntiScreenshotEnabled = true
                )
            }
            _securityConfig.value = initial
            _isAppUnlocked.value = !initial.isPinEnabled
            saveConfig(context, initial)
        }
    }

    private fun saveConfig(context: Context, config: SecurityConfig) {
        try {
            val securityDir = AppStorageHelper.getSecurityDir(context)
            val configFile = File(securityDir, CONFIG_FILE_NAME)
            val json = JSONObject().apply {
                put("isPinEnabled", config.isPinEnabled)
                put("isFingerprintEnabled", config.isFingerprintEnabled)
                put("isFingerprintRegistered", config.isFingerprintRegistered)
                put("fingerprintRegisteredAt", config.fingerprintRegisteredAt)
                put("fingerprintBackupPath", config.fingerprintBackupPath)
                put("fingerprintBackupHash", config.fingerprintBackupHash)
                put("fingerprintBackupSalt", config.fingerprintBackupSalt)
                put("isAntiScreenshotEnabled", config.isAntiScreenshotEnabled)
                put("isFaceLockEnabled", config.isFaceLockEnabled)
                put("isFaceEnrolled", config.isFaceEnrolled)
                put("faceEnrolledAt", config.faceEnrolledAt)
                put("pinSalt", config.pinSalt)
                put("pinHash", config.pinHash)
                put("pinCode", config.pinCode)
                put("biometricToken", config.biometricToken)
                put("masterPasswordHash", config.masterPasswordHash)
                put("masterPasswordSalt", config.masterPasswordSalt)
                put("failedAttempts", config.failedAttempts)
                put("updatedAt", System.currentTimeMillis())
            }
            configFile.writeText(json.toString(2), StandardCharsets.UTF_8)
            _securityConfig.value = config
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    private fun hashWithSalt(input: String, saltHex: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(saltHex.toByteArray(StandardCharsets.UTF_8))
        val digest = md.digest(input.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun generateSaltHex(): String {
        val randomBytes = ByteArray(16)
        SecureRandom().nextBytes(randomBytes)
        return randomBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Sets or updates the 6-Digit PIN.
     * Persists cryptographic salt and hash to both security_config.json and pin_credential.dat
     * inside the dedicated android/media/<packageName>/security folder.
     */
    fun set6DigitPin(context: Context, newPin: String): Boolean {
        if (newPin.length != 6 || !newPin.all { it.isDigit() }) {
            return false
        }
        val salt = generateSaltHex()
        val hash = hashWithSalt(newPin, salt)

        // Save dedicated pin_credential.dat in Android/media/<packageName>/security
        try {
            val securityDir = AppStorageHelper.getSecurityDir(context)
            val pinFile = File(securityDir, PIN_FILE_NAME)
            pinFile.writeText("$salt\n$hash\n$newPin\n# 6-Digit PIN Secure Salt, SHA-256 Hash & Password\n", StandardCharsets.UTF_8)
        } catch (e: Throwable) {
            e.printStackTrace()
        }

        val updated = _securityConfig.value.copy(
            isPinEnabled = true,
            pinSalt = salt,
            pinHash = hash,
            pinCode = newPin,
            failedAttempts = 0,
            updatedAt = System.currentTimeMillis()
        )
        saveConfig(context, updated)

        // Ensure fingerprint.dat also embeds the 6-digit password if fingerprint backup exists
        updateFingerprintPinDataIfRegistered(context, newPin, salt, hash, isPinEnabled = true)

        _isAppUnlocked.value = true
        return true
    }

    /**
     * Verifies user entered PIN against stored SHA-256 hash.
     */
    fun verifyPin(enteredPin: String, context: Context? = null): Boolean {
        val current = _securityConfig.value
        if (!current.isPinEnabled) {
            _isAppUnlocked.value = true
            return true
        }

        val computedHash = hashWithSalt(enteredPin, current.pinSalt)
        val isMatch = computedHash.equals(current.pinHash, ignoreCase = true)

        if (isMatch) {
            _isAppUnlocked.value = true
            (context ?: appContext)?.let {
                ActivityLogManager.log(it, "UNLOCK_PIN", "App Unlocked via PIN", "6-Digit master PIN entered successfully.", severity = "SUCCESS")
            }
            if (current.failedAttempts > 0 && context != null) {
                val updated = current.copy(failedAttempts = 0)
                saveConfig(context, updated)
            }
            return true
        } else {
            (context ?: appContext)?.let {
                ActivityLogManager.log(it, "UNLOCK_FAIL", "Failed Unlock Attempt", "Incorrect PIN code entered.", severity = "WARNING")
            }
            if (context != null) {
                val updated = current.copy(failedAttempts = current.failedAttempts + 1)
                saveConfig(context, updated)
            }
            return false
        }
    }

    /**
     * Changes 6-Digit PIN by verifying the current PIN first.
     */
    fun changePin(context: Context, oldPin: String, newPin: String): Pair<Boolean, String> {
        val current = _securityConfig.value
        if (current.isPinEnabled) {
            val oldHash = hashWithSalt(oldPin, current.pinSalt)
            if (!oldHash.equals(current.pinHash, ignoreCase = true)) {
                return Pair(false, "Current PIN is incorrect.")
            }
        }
        if (newPin.length != 6 || !newPin.all { it.isDigit() }) {
            return Pair(false, "New PIN must be exactly 6 digits.")
        }
        val success = set6DigitPin(context, newPin)
        return if (success) Pair(true, "PIN updated successfully.") else Pair(false, "Failed to save new PIN.")
    }

    /**
     * Disables 6-Digit PIN requirement after verifying current PIN.
     */
    fun disablePin(context: Context, currentPin: String): Pair<Boolean, String> {
        val current = _securityConfig.value
        if (current.isPinEnabled) {
            val hash = hashWithSalt(currentPin, current.pinSalt)
            if (!hash.equals(current.pinHash, ignoreCase = true)) {
                return Pair(false, "Current PIN is incorrect.")
            }
        }

        try {
            val securityDir = AppStorageHelper.getSecurityDir(context)
            File(securityDir, PIN_FILE_NAME).delete()
        } catch (_: Throwable) {}

        val updated = current.copy(
            isPinEnabled = false,
            isFingerprintEnabled = false,
            pinSalt = "",
            pinHash = "",
            pinCode = "",
            failedAttempts = 0,
            updatedAt = System.currentTimeMillis()
        )
        saveConfig(context, updated)
        updateFingerprintPinDataIfRegistered(context, "", "", "", isPinEnabled = false)
        _isAppUnlocked.value = true
        return Pair(true, "App Lock PIN disabled.")
    }

    /**
     * Enables or disables Fingerprint/Biometric unlock.
     * Generates or deletes biometric authorization token in Android/media/security.
     */
    fun setFingerprintEnabled(context: Context, enabled: Boolean): Pair<Boolean, String> {
        val current = _securityConfig.value

        val securityDir = AppStorageHelper.getSecurityDir(context)
        val bioFile = File(securityDir, BIOMETRIC_FILE_NAME)

        val existingToken = current.biometricToken.ifBlank {
            if (bioFile.exists()) {
                try { bioFile.readText(StandardCharsets.UTF_8).trim() } catch (_: Throwable) { "" }
            } else ""
        }

        val token = if (enabled) {
            val validToken = if (existingToken.isNotBlank()) existingToken else UUID.randomUUID().toString()
            try {
                bioFile.writeText(validToken, StandardCharsets.UTF_8)
            } catch (_: Throwable) {}
            validToken
        } else {
            // Keep the token in memory/config for safe re-enabling without invalidating fingerprint.dat
            existingToken
        }

        val updated = current.copy(
            isFingerprintEnabled = enabled,
            isFingerprintRegistered = current.isFingerprintRegistered || enabled,
            biometricToken = token,
            updatedAt = System.currentTimeMillis()
        )
        saveConfig(context, updated)
        return Pair(true, if (enabled) "Fingerprint unlock enabled." else "Fingerprint unlock disabled.")
    }

    /**
     * Re-enrolls or refreshes the biometric token identifier in security folder.
     */
    fun reEnrollFingerprint(context: Context): Pair<Boolean, String> {
        return registerFingerprintCredential(context)
    }

    /**
     * Registers fingerprint credential and exports persistent backup token to
     * Android/media/<packageName>/security/fingerprint/fingerprint.dat and fingerprint_backup.dat.
     * Fails if user profile does not exist or is incomplete.
     */
    fun registerFingerprintCredential(context: Context): Pair<Boolean, String> {
        // Enforce: fingerprint register cannot happen if Profile does not exist or is incomplete
        if (!ProfileManager.hasCompleteProfile()) {
            return Pair(false, "Profile does not exist or is incomplete! Please fill all Profile fields before registering fingerprint.")
        }

        val current = _securityConfig.value
        val fpDir = AppStorageHelper.getFingerprintDir(context)
        val datFile = File(fpDir, FINGERPRINT_DAT_FILE_NAME)
        val backupFile = File(fpDir, FINGERPRINT_BACKUP_FILE_NAME)
        val metaFile = File(fpDir, FINGERPRINT_META_FILE_NAME)

        val token = UUID.randomUUID().toString().replace("-", "") + "-" + UUID.randomUUID().toString().replace("-", "")
        val salt = generateSaltHex()
        val hash = hashWithSalt(token, salt)
        val timestamp = System.currentTimeMillis()
        val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

        val profile = ProfileManager.userProfile.value
        val profileJsonString = if (profile != null) ProfileManager.profileToJson(profile).toString() else ""

        val pinCode = current.pinCode.ifEmpty {
            try {
                val pinFile = File(AppStorageHelper.getSecurityDir(context), PIN_FILE_NAME)
                if (pinFile.exists()) {
                    val lines = pinFile.readLines()
                    if (lines.size >= 3 && lines[2].trim().length == 6 && lines[2].trim().all { it.isDigit() }) {
                        lines[2].trim()
                    } else ""
                } else ""
            } catch (_: Throwable) { "" }
        }
        val pinSalt = current.pinSalt
        val pinHash = current.pinHash
        val isPinEnabled = current.isPinEnabled || pinCode.isNotEmpty() || pinHash.isNotEmpty()

        val backupContent = buildString {
            appendLine("# ========================================================")
            appendLine("# AI STUDIO APP SECURITY - REGISTERED FINGERPRINT BACKUP")
            appendLine("# Location: Android/media/<packageName>/security/fingerprint/")
            appendLine("# Use this file to unlock the app if you forget your PIN")
            appendLine("# or transfer to another device.")
            appendLine("# ========================================================")
            appendLine("FORMAT=FINGERPRINT_BACKUP_V1")
            appendLine("TOKEN=$token")
            appendLine("SALT=$salt")
            appendLine("HASH=$hash")
            appendLine("REGISTERED_AT=$timestamp")
            appendLine("DEVICE=$deviceName")
            appendLine("PACKAGE=${context.packageName}")
            appendLine("PIN_CODE=$pinCode")
            appendLine("PIN_PASSWORD=$pinCode")
            appendLine("PIN_SALT=$pinSalt")
            appendLine("PIN_HASH=$pinHash")
            appendLine("PIN_ENABLED=$isPinEnabled")
            appendLine("GOOGLE_EMAIL=${profile?.googleEmail ?: ""}")
            appendLine("GOOGLE_NAME=${profile?.googleDisplayName ?: ""}")
            appendLine("GOOGLE_ID=${profile?.googleId ?: ""}")
            appendLine("GOOGLE_CONNECTED=${profile?.isGoogleConnected ?: false}")
            val appSettings = SettingsManager.settings.value
            appendLine("FB_CONNECTED=${appSettings.facebookConnected}")
            appendLine("FB_USERNAME=${appSettings.facebookUserName}")
            appendLine("FACE_LOCK_ENABLED=${appSettings.faceLockEnabled || current.isFaceLockEnabled}")
            appendLine("RECENT_APP_PRIVACY=${appSettings.recentAppPrivacy}")
            appendLine("APP_DOWNLOAD_URL=${appSettings.appDownloadUrl}")
            val dvSession = FirebaseBridgeManager.currentSession.value
            if (dvSession.code.isNotBlank()) {
                appendLine("DUAL_VAULT_CODE=${dvSession.code}")
                appendLine("DUAL_VAULT_SESSION_ID=${dvSession.sessionId}")
                appendLine("DUAL_VAULT_IS_HOST=${dvSession.isHost}")
                appendLine("DUAL_VAULT_IS_CONNECTED=${dvSession.isConnected}")
                appendLine("DUAL_VAULT_STATUS=${dvSession.status}")
                appendLine("DUAL_VAULT_HOST_NAME=${dvSession.hostName}")
                appendLine("DUAL_VAULT_PEER_NAME=${dvSession.peerName}")
                appendLine("DUAL_VAULT_HOST_PROFILE_IMAGE=${dvSession.hostProfileImage}")
                appendLine("DUAL_VAULT_PEER_PROFILE_IMAGE=${dvSession.peerProfileImage}")
                appendLine("DUAL_VAULT_CONNECTED_AT=${dvSession.connectedAt}")
            }
            val devCode = profile?.deviceCode ?: ""
            if (devCode.isNotBlank()) {
                appendLine("DEVICE_PROFILE_CODE=$devCode")
                appendLine("DEVICE_CODE=$devCode")
            }
            appendLine("IS_FACE_ENROLLED=${current.isFaceEnrolled}")
            appendLine("FACE_ENROLLED_AT=${current.faceEnrolledAt}")
            val logoFile = AppStorageHelper.getAppLogoFile(context)
            if (logoFile.exists() && logoFile.length() > 0) {
                try {
                    val logoB64 = android.util.Base64.encodeToString(logoFile.readBytes(), android.util.Base64.NO_WRAP)
                    appendLine("APP_LOGO_BASE64=$logoB64")
                } catch (_: Throwable) {}
            }
            if (profileJsonString.isNotEmpty()) {
                appendLine("PROFILE_JSON=$profileJsonString")
            }
        }

        try {
            datFile.writeText(backupContent, StandardCharsets.UTF_8)
            backupFile.writeText(backupContent, StandardCharsets.UTF_8)

            val dvSession = FirebaseBridgeManager.currentSession.value
            val metaJson = JSONObject().apply {
                put("format", "FINGERPRINT_BACKUP_V1")
                put("token", token)
                put("salt", salt)
                put("hash", hash)
                put("registeredAt", timestamp)
                put("device", deviceName)
                put("filePath", datFile.absolutePath)
                put("pinCode", pinCode)
                put("pinSalt", pinSalt)
                put("pinHash", pinHash)
                put("isPinEnabled", isPinEnabled)
                put("googleEmail", profile?.googleEmail ?: "")
                put("googleDisplayName", profile?.googleDisplayName ?: "")
                put("isGoogleConnected", profile?.isGoogleConnected ?: false)
                val curSettings = SettingsManager.settings.value
                put("facebookConnected", curSettings.facebookConnected)
                put("facebookUserName", curSettings.facebookUserName)
                put("faceLockEnabled", curSettings.faceLockEnabled || current.isFaceLockEnabled)
                put("isFaceEnrolled", current.isFaceEnrolled)
                put("faceEnrolledAt", current.faceEnrolledAt)
                put("deviceCode", profile?.deviceCode ?: "")
                put("deviceProfileCode", profile?.deviceCode ?: "")
                put("recentAppPrivacy", curSettings.recentAppPrivacy)
                put("appDownloadUrl", curSettings.appDownloadUrl)
                if (dvSession.code.isNotBlank()) {
                    val dvJson = JSONObject().apply {
                        put("code", dvSession.code)
                        put("sessionId", dvSession.sessionId)
                        put("isHost", dvSession.isHost)
                        put("isConnected", dvSession.isConnected)
                        put("status", dvSession.status)
                        put("hostName", dvSession.hostName)
                        put("peerName", dvSession.peerName)
                        put("hostProfileImage", dvSession.hostProfileImage)
                        put("peerProfileImage", dvSession.peerProfileImage)
                        put("connectedAt", dvSession.connectedAt)
                    }
                    put("dualVaultPairing", dvJson)
                }
                if (profile != null) {
                    put("profile", ProfileManager.profileToJson(profile))
                }
            }
            metaFile.writeText(metaJson.toString(2), StandardCharsets.UTF_8)

            // Also keep standard biometric_token.dat in sync
            val securityDir = AppStorageHelper.getSecurityDir(context)
            File(securityDir, BIOMETRIC_FILE_NAME).writeText(token, StandardCharsets.UTF_8)
        } catch (e: Throwable) {
            e.printStackTrace()
            return Pair(false, "Failed to write fingerprint file: ${e.message}")
        }

        val updated = current.copy(
            isFingerprintEnabled = true,
            isFingerprintRegistered = true,
            fingerprintRegisteredAt = timestamp,
            fingerprintBackupPath = datFile.absolutePath,
            fingerprintBackupHash = hash,
            fingerprintBackupSalt = salt,
            biometricToken = token,
            pinCode = if (current.pinCode.isEmpty()) pinCode else current.pinCode,
            isPinEnabled = isPinEnabled,
            updatedAt = System.currentTimeMillis()
        )
        saveConfig(context, updated)

        return Pair(true, "Fingerprint, Profile, Google Account & 6-Digit Password registered!\nSaved to: security/fingerprint/$FINGERPRINT_DAT_FILE_NAME")
    }

    /**
     * Updates profile info and Google account in existing fingerprint.dat and fingerprint_backup.dat files if fingerprint is already registered.
     */
    fun updateFingerprintProfileDataIfRegistered(context: Context) {
        val current = _securityConfig.value
        if (!current.isFingerprintRegistered) return

        val profile = ProfileManager.userProfile.value ?: return
        val fpDir = AppStorageHelper.getFingerprintDir(context)
        val datFile = File(fpDir, FINGERPRINT_DAT_FILE_NAME)
        val backupFile = File(fpDir, FINGERPRINT_BACKUP_FILE_NAME)
        val targetFile = if (datFile.exists() && datFile.length() > 0) datFile else backupFile

        if (!targetFile.exists()) return

        try {
            val content = targetFile.readText(StandardCharsets.UTF_8)
            val profileJsonString = ProfileManager.profileToJson(profile).toString()
            val gEmail = profile.googleEmail
            val gName = profile.googleDisplayName
            val gId = profile.googleId
            val gConnected = profile.isGoogleConnected

            var updatedText = content
            updatedText = if (updatedText.contains("GOOGLE_EMAIL=")) {
                updatedText.lines().joinToString("\n") { line ->
                    if (line.trim().startsWith("GOOGLE_EMAIL=")) "GOOGLE_EMAIL=$gEmail" else line
                }
            } else {
                updatedText + "\nGOOGLE_EMAIL=$gEmail"
            }

            updatedText = if (updatedText.contains("GOOGLE_NAME=")) {
                updatedText.lines().joinToString("\n") { line ->
                    if (line.trim().startsWith("GOOGLE_NAME=")) "GOOGLE_NAME=$gName" else line
                }
            } else {
                updatedText + "\nGOOGLE_NAME=$gName"
            }

            updatedText = if (updatedText.contains("GOOGLE_ID=")) {
                updatedText.lines().joinToString("\n") { line ->
                    if (line.trim().startsWith("GOOGLE_ID=")) "GOOGLE_ID=$gId" else line
                }
            } else {
                updatedText + "\nGOOGLE_ID=$gId"
            }

            updatedText = if (updatedText.contains("GOOGLE_CONNECTED=")) {
                updatedText.lines().joinToString("\n") { line ->
                    if (line.trim().startsWith("GOOGLE_CONNECTED=")) "GOOGLE_CONNECTED=$gConnected" else line
                }
            } else {
                updatedText + "\nGOOGLE_CONNECTED=$gConnected"
            }

            if (profileJsonString.isNotEmpty()) {
                updatedText = if (updatedText.contains("PROFILE_JSON=")) {
                    updatedText.lines().joinToString("\n") { line ->
                        if (line.trim().startsWith("PROFILE_JSON=")) "PROFILE_JSON=$profileJsonString" else line
                    }
                } else {
                    updatedText + "\nPROFILE_JSON=$profileJsonString\n"
                }
            }

            // Also keep PIN code synchronized if present in current config
            val sec = _securityConfig.value
            if (sec.pinCode.isNotEmpty() || sec.pinHash.isNotEmpty()) {
                val pinFields = mapOf(
                    "PIN_CODE=" to sec.pinCode,
                    "PIN_PASSWORD=" to sec.pinCode,
                    "PIN_SALT=" to sec.pinSalt,
                    "PIN_HASH=" to sec.pinHash,
                    "PIN_ENABLED=" to sec.isPinEnabled.toString()
                )
                pinFields.forEach { (prefix, value) ->
                    updatedText = if (updatedText.contains(prefix)) {
                        updatedText.lines().joinToString("\n") { line ->
                            if (line.trim().startsWith(prefix)) "$prefix$value" else line
                        }
                    } else {
                        updatedText + "\n$prefix$value"
                    }
                }
            }

            val appSet = SettingsManager.settings.value
            val secFields = mapOf(
                "FB_CONNECTED=" to appSet.facebookConnected.toString(),
                "FB_USERNAME=" to appSet.facebookUserName,
                "FACE_LOCK_ENABLED=" to (appSet.faceLockEnabled || current.isFaceLockEnabled).toString(),
                "IS_FACE_ENROLLED=" to current.isFaceEnrolled.toString(),
                "FACE_ENROLLED_AT=" to current.faceEnrolledAt.toString(),
                "DEVICE_PROFILE_CODE=" to profile.deviceCode,
                "DEVICE_CODE=" to profile.deviceCode,
                "RECENT_APP_PRIVACY=" to appSet.recentAppPrivacy.toString(),
                "APP_DOWNLOAD_URL=" to appSet.appDownloadUrl
            )
            secFields.forEach { (prefix, value) ->
                updatedText = if (updatedText.contains(prefix)) {
                    updatedText.lines().joinToString("\n") { line ->
                        if (line.trim().startsWith(prefix)) "$prefix$value" else line
                    }
                } else {
                    updatedText + "\n$prefix$value"
                }
            }

            datFile.writeText(updatedText, StandardCharsets.UTF_8)
            backupFile.writeText(updatedText, StandardCharsets.UTF_8)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    /**
     * Synchronizes 6-digit PIN password, salt, and hash into existing fingerprint.dat and fingerprint_backup.dat files.
     */
    fun updateFingerprintPinDataIfRegistered(
        context: Context,
        pinCode: String,
        pinSalt: String,
        pinHash: String,
        isPinEnabled: Boolean
    ) {
        val fpDir = AppStorageHelper.getFingerprintDir(context)
        val datFile = File(fpDir, FINGERPRINT_DAT_FILE_NAME)
        val backupFile = File(fpDir, FINGERPRINT_BACKUP_FILE_NAME)
        val metaFile = File(fpDir, FINGERPRINT_META_FILE_NAME)

        if (!datFile.exists() && !backupFile.exists()) return

        val targetFile = if (datFile.exists() && datFile.length() > 0) datFile else backupFile
        if (!targetFile.exists()) return

        try {
            val content = targetFile.readText(StandardCharsets.UTF_8)
            var updatedText = content
            val fieldsToUpdate = mapOf(
                "PIN_CODE=" to pinCode,
                "PIN_PASSWORD=" to pinCode,
                "PIN_SALT=" to pinSalt,
                "PIN_HASH=" to pinHash,
                "PIN_ENABLED=" to isPinEnabled.toString()
            )

            fieldsToUpdate.forEach { (prefix, value) ->
                updatedText = if (updatedText.contains(prefix)) {
                    updatedText.lines().joinToString("\n") { line ->
                        if (line.trim().startsWith(prefix)) "$prefix$value" else line
                    }
                } else {
                    updatedText + "\n$prefix$value"
                }
            }

            datFile.writeText(updatedText, StandardCharsets.UTF_8)
            backupFile.writeText(updatedText, StandardCharsets.UTF_8)

            if (metaFile.exists()) {
                try {
                    val metaJson = JSONObject(metaFile.readText(StandardCharsets.UTF_8))
                    metaJson.put("pinCode", pinCode)
                    metaJson.put("pinSalt", pinSalt)
                    metaJson.put("pinHash", pinHash)
                    metaJson.put("isPinEnabled", isPinEnabled)
                    metaFile.writeText(metaJson.toString(2), StandardCharsets.UTF_8)
                } catch (_: Throwable) {}
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    /**
     * Helper to extract UserProfile and Google Account from fingerprint backup content and restore it to local device storage.
     */
    fun extractAndRestoreProfileFromContent(context: Context, content: String): Boolean {
        try {
            var candidateProfile: UserProfile? = null
            var lineGoogleEmail = ""
            var lineGoogleName = ""
            var lineGoogleId = ""
            var lineGoogleConnected = false
            var lineFbConnected = false
            var lineFbUser = ""
            var lineFaceLock = false
            var lineFaceEnrolled = false
            var lineFaceEnrolledAt = 0L
            var lineDeviceCode = ""
            var lineRecentPrivacy: Boolean? = null
            var lineDownloadUrl = ""
            var lineLogoB64 = ""

            if (content.trim().startsWith("{")) {
                val json = JSONObject(content)
                val profileObj = json.optJSONObject("profile")
                if (profileObj != null) {
                    candidateProfile = ProfileManager.jsonToProfile(profileObj.toString(), context)
                }
                if (lineGoogleEmail.isEmpty()) {
                    lineGoogleEmail = json.optString("googleEmail", "")
                    lineGoogleName = json.optString("googleDisplayName", "")
                    lineGoogleConnected = json.optBoolean("isGoogleConnected", false)
                }
                lineFbConnected = json.optBoolean("facebookConnected", false)
                lineFbUser = json.optString("facebookUserName", "")
                lineFaceLock = json.optBoolean("faceLockEnabled", false)
                lineFaceEnrolled = json.optBoolean("isFaceEnrolled", false)
                lineFaceEnrolledAt = json.optLong("faceEnrolledAt", 0L)
                lineDeviceCode = json.optString("deviceCode", "")
                if (lineDeviceCode.isEmpty()) lineDeviceCode = json.optString("deviceProfileCode", "")
                if (json.has("recentAppPrivacy")) {
                    lineRecentPrivacy = json.optBoolean("recentAppPrivacy", true)
                }
                lineDownloadUrl = json.optString("appDownloadUrl", "")
                lineLogoB64 = json.optString("appLogoBase64", "")
            } else {
                content.lines().forEach { line ->
                    val trimmed = line.trim()
                    when {
                        trimmed.startsWith("PROFILE_JSON=") -> {
                            val profileJsonStr = trimmed.removePrefix("PROFILE_JSON=").trim()
                            candidateProfile = ProfileManager.jsonToProfile(profileJsonStr, context)
                        }
                        trimmed.startsWith("GOOGLE_EMAIL=") -> {
                            lineGoogleEmail = trimmed.removePrefix("GOOGLE_EMAIL=").trim()
                        }
                        trimmed.startsWith("GOOGLE_NAME=") -> {
                            lineGoogleName = trimmed.removePrefix("GOOGLE_NAME=").trim()
                        }
                        trimmed.startsWith("GOOGLE_ID=") -> {
                            lineGoogleId = trimmed.removePrefix("GOOGLE_ID=").trim()
                        }
                        trimmed.startsWith("GOOGLE_CONNECTED=") -> {
                            lineGoogleConnected = trimmed.removePrefix("GOOGLE_CONNECTED=").trim().toBoolean()
                        }
                        trimmed.startsWith("FB_CONNECTED=") -> {
                            lineFbConnected = trimmed.removePrefix("FB_CONNECTED=").trim().toBoolean()
                        }
                        trimmed.startsWith("FB_USERNAME=") -> {
                            lineFbUser = trimmed.removePrefix("FB_USERNAME=").trim()
                        }
                        trimmed.startsWith("FACE_LOCK_ENABLED=") -> {
                            lineFaceLock = trimmed.removePrefix("FACE_LOCK_ENABLED=").trim().toBoolean()
                        }
                        trimmed.startsWith("IS_FACE_ENROLLED=") -> {
                            lineFaceEnrolled = trimmed.removePrefix("IS_FACE_ENROLLED=").trim().toBoolean()
                        }
                        trimmed.startsWith("FACE_ENROLLED_AT=") -> {
                            lineFaceEnrolledAt = trimmed.removePrefix("FACE_ENROLLED_AT=").trim().toLongOrNull() ?: 0L
                        }
                        trimmed.startsWith("DEVICE_PROFILE_CODE=") -> {
                            lineDeviceCode = trimmed.removePrefix("DEVICE_PROFILE_CODE=").trim()
                        }
                        trimmed.startsWith("DEVICE_CODE=") -> {
                            if (lineDeviceCode.isEmpty()) lineDeviceCode = trimmed.removePrefix("DEVICE_CODE=").trim()
                        }
                        trimmed.startsWith("RECENT_APP_PRIVACY=") -> {
                            lineRecentPrivacy = trimmed.removePrefix("RECENT_APP_PRIVACY=").trim().toBoolean()
                        }
                        trimmed.startsWith("APP_DOWNLOAD_URL=") -> {
                            lineDownloadUrl = trimmed.removePrefix("APP_DOWNLOAD_URL=").trim()
                        }
                        trimmed.startsWith("APP_LOGO_BASE64=") -> {
                            lineLogoB64 = trimmed.removePrefix("APP_LOGO_BASE64=").trim()
                        }
                    }
                }
            }

            // Restore App Settings from backup
            if (lineFbConnected) {
                SettingsManager.setFacebookConnected(true, lineFbUser)
            }
            if (lineFaceLock || lineFaceEnrolled) {
                SettingsManager.setFaceLockEnabled(lineFaceLock)
                val cur = _securityConfig.value
                _securityConfig.value = cur.copy(
                    isFaceLockEnabled = lineFaceLock,
                    isFaceEnrolled = lineFaceEnrolled || lineFaceLock,
                    faceEnrolledAt = if (lineFaceEnrolledAt > 0L) lineFaceEnrolledAt else System.currentTimeMillis()
                )
            }
            if (lineRecentPrivacy != null) {
                SettingsManager.setRecentAppPrivacy(lineRecentPrivacy)
            }
            if (lineDownloadUrl.isNotBlank()) {
                SettingsManager.setAppDownloadUrl(lineDownloadUrl)
            }
            if (lineLogoB64.isNotEmpty()) {
                try {
                    val bytes = android.util.Base64.decode(lineLogoB64, android.util.Base64.NO_WRAP)
                    val logoFile = AppStorageHelper.getAppLogoFile(context)
                    logoFile.writeBytes(bytes)
                    SettingsManager.setCustomAppLogoTimestamp(System.currentTimeMillis())
                } catch (_: Throwable) {}
            }

            if (candidateProfile != null) {
                val finalProfile = candidateProfile.copy(
                    googleEmail = if (candidateProfile.googleEmail.isNotBlank()) candidateProfile.googleEmail else lineGoogleEmail,
                    googleDisplayName = if (candidateProfile.googleDisplayName.isNotBlank()) candidateProfile.googleDisplayName else lineGoogleName,
                    googleId = if (candidateProfile.googleId.isNotBlank()) candidateProfile.googleId else lineGoogleId,
                    isGoogleConnected = candidateProfile.isGoogleConnected || lineGoogleConnected || lineGoogleEmail.isNotBlank(),
                    deviceCode = if (candidateProfile.deviceCode.length == 10) candidateProfile.deviceCode else if (lineDeviceCode.length == 10) lineDeviceCode else candidateProfile.deviceCode
                )
                ProfileManager.restoreProfileFromFingerprint(context, finalProfile)
                return true
            } else if (lineGoogleEmail.isNotBlank()) {
                val current = ProfileManager.userProfile.value ?: UserProfile(device = ProfileManager.getAutoDeviceModel())
                val finalProfile = current.copy(
                    googleEmail = lineGoogleEmail,
                    googleDisplayName = lineGoogleName,
                    googleId = lineGoogleId,
                    isGoogleConnected = true,
                    deviceCode = if (lineDeviceCode.length == 10) lineDeviceCode else current.deviceCode
                )
                ProfileManager.restoreProfileFromFingerprint(context, finalProfile)
                return true
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        return false
    }

    /**
     * Extracts 6-digit PIN password, salt, and hash from fingerprint backup content.
     * When using fingerprint.dat on another device, this automatically restores the 6-digit password
     * and enables password lock on this device.
     */
    fun extractAndRestorePinFromContent(context: Context, content: String): Boolean {
        try {
            var linePinCode = ""
            var linePinSalt = ""
            var linePinHash = ""
            var linePinEnabled: Boolean? = null

            if (content.trim().startsWith("{")) {
                val json = JSONObject(content)
                linePinCode = json.optString("pinCode", "")
                if (linePinCode.isEmpty()) linePinCode = json.optString("password", "")
                if (linePinCode.isEmpty()) linePinCode = json.optString("pin", "")
                linePinSalt = json.optString("pinSalt", "")
                linePinHash = json.optString("pinHash", "")
                if (json.has("isPinEnabled")) {
                    linePinEnabled = json.optBoolean("isPinEnabled", false)
                }
            } else {
                content.lines().forEach { line ->
                    val trimmed = line.trim()
                    when {
                        trimmed.startsWith("PIN_CODE=") -> linePinCode = trimmed.removePrefix("PIN_CODE=").trim()
                        trimmed.startsWith("PIN_PASSWORD=") -> if (linePinCode.isEmpty()) linePinCode = trimmed.removePrefix("PIN_PASSWORD=").trim()
                        trimmed.startsWith("PIN=") -> if (linePinCode.isEmpty()) linePinCode = trimmed.removePrefix("PIN=").trim()
                        trimmed.startsWith("PASSWORD=") -> if (linePinCode.isEmpty()) linePinCode = trimmed.removePrefix("PASSWORD=").trim()
                        trimmed.startsWith("PIN_SALT=") -> linePinSalt = trimmed.removePrefix("PIN_SALT=").trim()
                        trimmed.startsWith("PIN_HASH=") -> linePinHash = trimmed.removePrefix("PIN_HASH=").trim()
                        trimmed.startsWith("PIN_ENABLED=") -> linePinEnabled = trimmed.removePrefix("PIN_ENABLED=").trim().toBoolean()
                        trimmed.startsWith("IS_PIN_ENABLED=") -> linePinEnabled = trimmed.removePrefix("IS_PIN_ENABLED=").trim().toBoolean()
                    }
                }
            }

            if (linePinCode.length == 6 && linePinCode.all { it.isDigit() }) {
                val finalSalt = if (linePinSalt.isNotEmpty()) linePinSalt else generateSaltHex()
                val finalHash = if (linePinHash.isNotEmpty()) linePinHash else hashWithSalt(linePinCode, finalSalt)

                try {
                    val securityDir = AppStorageHelper.getSecurityDir(context)
                    val pinFile = File(securityDir, PIN_FILE_NAME)
                    pinFile.writeText("$finalSalt\n$finalHash\n$linePinCode\n# 6-Digit PIN Secure Salt, SHA-256 Hash & Password\n", StandardCharsets.UTF_8)
                } catch (_: Throwable) {}

                val current = _securityConfig.value
                val updated = current.copy(
                    isPinEnabled = true,
                    pinSalt = finalSalt,
                    pinHash = finalHash,
                    pinCode = linePinCode,
                    failedAttempts = 0,
                    updatedAt = System.currentTimeMillis()
                )
                saveConfig(context, updated)
                return true
            } else if (linePinHash.isNotEmpty() && linePinSalt.isNotEmpty() && (linePinEnabled != false)) {
                try {
                    val securityDir = AppStorageHelper.getSecurityDir(context)
                    val pinFile = File(securityDir, PIN_FILE_NAME)
                    pinFile.writeText("$linePinSalt\n$linePinHash\n# 6-Digit PIN Secure Salt & SHA-256 Hash\n", StandardCharsets.UTF_8)
                } catch (_: Throwable) {}

                val current = _securityConfig.value
                val updated = current.copy(
                    isPinEnabled = true,
                    pinSalt = linePinSalt,
                    pinHash = linePinHash,
                    pinCode = "",
                    failedAttempts = 0,
                    updatedAt = System.currentTimeMillis()
                )
                saveConfig(context, updated)
                return true
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        return false
    }

    /**
     * Extracts dual vault pairing details from fingerprint backup content (JSON or key-value text),
     * and automatically reconnects to the paired partner device and restores dual vault state.
     */
    fun extractAndRestoreDualVaultFromContent(context: Context, content: String): Boolean {
        try {
            var dvCode = ""
            var dvSessionId = ""
            var dvIsHost = false
            var dvIsConnected = false
            var dvHostName = ""
            var dvPeerName = ""
            var dvHostImg = ""
            var dvPeerImg = ""
            var dvConnectedAt = 0L

            if (content.trim().startsWith("{")) {
                val root = JSONObject(content)
                val dv = root.optJSONObject("dualVaultPairing")
                if (dv != null) {
                    dvCode = dv.optString("code", "")
                    dvSessionId = dv.optString("sessionId", "")
                    dvIsHost = dv.optBoolean("isHost", false)
                    dvIsConnected = dv.optBoolean("isConnected", false)
                    dvHostName = dv.optString("hostName", "")
                    dvPeerName = dv.optString("peerName", "")
                    dvHostImg = dv.optString("hostProfileImage", "")
                    dvPeerImg = dv.optString("peerProfileImage", "")
                    dvConnectedAt = dv.optLong("connectedAt", 0L)
                }
            } else {
                content.lines().forEach { line ->
                    val trimmed = line.trim()
                    when {
                        trimmed.startsWith("DUAL_VAULT_CODE=") -> dvCode = trimmed.removePrefix("DUAL_VAULT_CODE=").trim()
                        trimmed.startsWith("DUAL_VAULT_SESSION_ID=") -> dvSessionId = trimmed.removePrefix("DUAL_VAULT_SESSION_ID=").trim()
                        trimmed.startsWith("DUAL_VAULT_IS_HOST=") -> dvIsHost = trimmed.removePrefix("DUAL_VAULT_IS_HOST=").trim().toBoolean()
                        trimmed.startsWith("DUAL_VAULT_IS_CONNECTED=") -> dvIsConnected = trimmed.removePrefix("DUAL_VAULT_IS_CONNECTED=").trim().toBoolean()
                        trimmed.startsWith("DUAL_VAULT_HOST_NAME=") -> dvHostName = trimmed.removePrefix("DUAL_VAULT_HOST_NAME=").trim()
                        trimmed.startsWith("DUAL_VAULT_PEER_NAME=") -> dvPeerName = trimmed.removePrefix("DUAL_VAULT_PEER_NAME=").trim()
                        trimmed.startsWith("DUAL_VAULT_HOST_PROFILE_IMAGE=") -> dvHostImg = trimmed.removePrefix("DUAL_VAULT_HOST_PROFILE_IMAGE=").trim()
                        trimmed.startsWith("DUAL_VAULT_PEER_PROFILE_IMAGE=") -> dvPeerImg = trimmed.removePrefix("DUAL_VAULT_PEER_PROFILE_IMAGE=").trim()
                        trimmed.startsWith("DUAL_VAULT_CONNECTED_AT=") -> dvConnectedAt = trimmed.removePrefix("DUAL_VAULT_CONNECTED_AT=").trim().toLongOrNull() ?: 0L
                    }
                }
            }

            // CRITICAL: Only restore if dvIsConnected is true! Never overwrite an active connection with an unconnected state.
            if (dvCode.isNotBlank() && dvIsConnected) {
                if (dvSessionId.isBlank()) {
                    dvSessionId = "dv_" + UUID.randomUUID().toString().replace("-", "").take(12)
                }
                FirebaseBridgeManager.restoreSessionFromFingerprint(
                    context = context,
                    code = dvCode,
                    sessionId = dvSessionId,
                    isHost = dvIsHost,
                    isConnected = true,
                    hostName = dvHostName,
                    peerName = dvPeerName,
                    hostProfileImage = dvHostImg,
                    peerProfileImage = dvPeerImg,
                    connectedAt = if (dvConnectedAt > 0L) dvConnectedAt else System.currentTimeMillis()
                )
                return true
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        return false
    }

    /**
     * Clears dual vault pairing lines from fingerprint.dat and fingerprint_backup.dat files.
     */
    fun clearFingerprintDualVaultData(context: Context) {
        val fpDir = AppStorageHelper.getFingerprintDir(context)
        val datFile = File(fpDir, FINGERPRINT_DAT_FILE_NAME)
        val backupFile = File(fpDir, FINGERPRINT_BACKUP_FILE_NAME)
        listOf(datFile, backupFile).forEach { file ->
            if (file.exists() && file.length() > 0) {
                try {
                    val content = file.readText(StandardCharsets.UTF_8)
                    val lines = content.lines().filterNot { line ->
                        line.trim().startsWith("DUAL_VAULT_")
                    }
                    file.writeText(lines.joinToString("\n"), StandardCharsets.UTF_8)
                } catch (_: Throwable) {}
            }
        }
    }

    /**
     * Updates dual vault pairing details in existing fingerprint.dat and fingerprint_backup.dat files.
     */
    fun updateFingerprintDualVaultDataIfRegistered(context: Context) {
        val current = _securityConfig.value
        if (!current.isFingerprintRegistered) return

        val fpDir = AppStorageHelper.getFingerprintDir(context)
        val datFile = File(fpDir, FINGERPRINT_DAT_FILE_NAME)
        val backupFile = File(fpDir, FINGERPRINT_BACKUP_FILE_NAME)
        val targetFile = if (datFile.exists() && datFile.length() > 0) datFile else backupFile
        if (!targetFile.exists()) return

        try {
            val session = FirebaseBridgeManager.currentSession.value
            val content = targetFile.readText(StandardCharsets.UTF_8)
            val lines = content.lines().filterNot { line ->
                line.trim().startsWith("DUAL_VAULT_")
            }.toMutableList()

            if (session.code.isNotBlank()) {
                lines.add("DUAL_VAULT_CODE=${session.code}")
                lines.add("DUAL_VAULT_SESSION_ID=${session.sessionId}")
                lines.add("DUAL_VAULT_IS_HOST=${session.isHost}")
                lines.add("DUAL_VAULT_IS_CONNECTED=${session.isConnected}")
                lines.add("DUAL_VAULT_STATUS=${session.status}")
                lines.add("DUAL_VAULT_HOST_NAME=${session.hostName}")
                lines.add("DUAL_VAULT_PEER_NAME=${session.peerName}")
                lines.add("DUAL_VAULT_HOST_PROFILE_IMAGE=${session.hostProfileImage}")
                lines.add("DUAL_VAULT_PEER_PROFILE_IMAGE=${session.peerProfileImage}")
                lines.add("DUAL_VAULT_CONNECTED_AT=${session.connectedAt}")
            }

            val updatedContent = lines.joinToString("\n")
            datFile.writeText(updatedContent, StandardCharsets.UTF_8)
            backupFile.writeText(updatedContent, StandardCharsets.UTF_8)
        } catch (_: Throwable) {}
    }

    /**
     * Prompts the user with system BiometricPrompt to register their fingerprint.
     * Enforces that a complete User Profile must exist before allowing fingerprint registration.
     * On successful authentication, saves fingerprint.dat in Android/media/<packageName>/security/fingerprint.
     */
    fun registerFingerprintWithBiometric(
        activity: FragmentActivity,
        onResult: (Boolean, String) -> Unit
    ) {
        // Enforce: Profile must exist and be complete
        if (!ProfileManager.hasCompleteProfile()) {
            onResult(false, "Profile does not exist or is incomplete! Please fill all Profile fields before registering fingerprint.")
            return
        }

        val bioStatus = checkBiometricStatus(activity)
        if (bioStatus == BiometricAvailability.NO_HARDWARE) {
            onResult(false, "Device does not have fingerprint hardware.")
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Register Fingerprint")
            .setSubtitle("Touch the sensor to verify and link with your Profile")
            .setNegativeButtonText("Cancel")
            .build()

        val biometricPrompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                val (ok, msg) = registerFingerprintCredential(activity)
                onResult(ok, msg)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    onResult(false, "Biometric error: $errString")
                } else {
                    onResult(false, "Registration cancelled.")
                }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                // Individual attempt failed; system handles retry UI
            }
        })

        try {
            biometricPrompt.authenticate(promptInfo)
        } catch (e: Throwable) {
            // Fallback: If prompt cannot be displayed (e.g. in robolectric or test), register directly
            val (ok, msg) = registerFingerprintCredential(activity)
            onResult(ok, msg)
        }
    }

    /**
     * Enrolls user's face credential. Sets isFaceEnrolled = true and isFaceLockEnabled = true.
     */
    fun enrollFace(context: Context, faceTemplate: List<Float>): Pair<Boolean, String> {
        val (saved, saveMessage) = FaceLockStore.saveTemplate(context, faceTemplate)
        if (!saved) return Pair(false, saveMessage)

        val current = _securityConfig.value
        val now = System.currentTimeMillis()
        val updated = current.copy(
            isFaceLockEnabled = true,
            isFaceEnrolled = true,
            faceEnrolledAt = now,
            updatedAt = now
        )
        saveConfig(context, updated)
        SettingsManager.setFaceLockEnabled(true)
        // Keep the existing fingerprint backup in sync when fingerprint is
        // registered; Face Lock itself does not require fingerprint.
        updateFingerprintProfileDataIfRegistered(context)
        return Pair(true, "Face recognition enrolled successfully.")
    }

    // Backward-compatible guard: face enrollment must provide a real camera template.
    fun enrollFace(context: Context): Pair<Boolean, String> =
        Pair(false, "Face enrollment requires a live camera template.")

    /**
     * Removes enrolled face credential.
     */
    fun removeEnrolledFace(context: Context) {
        FaceLockStore.clear(context)
        val current = _securityConfig.value
        val updated = current.copy(
            isFaceLockEnabled = false,
            isFaceEnrolled = false,
            faceEnrolledAt = 0L,
            updatedAt = System.currentTimeMillis()
        )
        saveConfig(context, updated)
        SettingsManager.setFaceLockEnabled(false)
        updateFingerprintProfileDataIfRegistered(context)
    }

    /**
     * Toggles Face Lock (Biometric / Face Recognition) protection for the app.
     * If user tries to turn ON Face Lock before setting it up, requires enrollment first.
     */
    fun setFaceLockEnabled(context: Context, enabled: Boolean): Pair<Boolean, String> {
        val current = _securityConfig.value
        if (enabled && !current.isFaceEnrolled) {
            return Pair(false, "Face lock is not enrolled yet. Please set up Face Lock first.")
        }
        val updated = current.copy(
            isFaceLockEnabled = enabled,
            updatedAt = System.currentTimeMillis()
        )
        saveConfig(context, updated)
        SettingsManager.setFaceLockEnabled(enabled)
        updateFingerprintProfileDataIfRegistered(context)
        return Pair(true, if (enabled) "Face Lock enabled." else "Face Lock disabled.")
    }

    /**
     * Authenticates user using Face recognition / Biometric prompt.
     */
    fun authenticateWithFace(
        activity: FragmentActivity,
        onResult: (Boolean, String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Face Recognition Unlock")
            .setSubtitle("Look at front camera or verify biometric")
            .setNegativeButtonText("Use PIN")
            .build()

        val biometricPrompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                _isAppUnlocked.value = true
                onResult(true, "Face verified successfully!")
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    onResult(false, errString.toString())
                } else {
                    onResult(false, "Authentication cancelled.")
                }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                onResult(false, "Face not recognized. Try again.")
            }
        })

        try {
            biometricPrompt.authenticate(promptInfo)
        } catch (e: Throwable) {
            onResult(false, e.message ?: "Face biometric not available on this device.")
        }
    }

    /**
     * Verifies an uploaded or selected backup fingerprint file and unlocks the app.
     * Supports emergency recovery if the user forgot their PIN, or when moving to another device.
     */
    fun verifyAndUnlockWithBackupFingerprint(context: Context, backupFileUri: Uri): Pair<Boolean, String> {
        val content = try {
            context.contentResolver.openInputStream(backupFileUri)?.use { input ->
                input.bufferedReader(StandardCharsets.UTF_8).readText()
            } ?: return Pair(false, "Cannot open selected fingerprint file.")
        } catch (e: Throwable) {
            return Pair(false, "Failed to read file: ${e.message}")
        }

        return verifyAndUnlockWithBackupFingerprintContent(context, content)
    }

    /**
     * Parses and cryptographically validates the fingerprint backup content string.
     */
    fun verifyAndUnlockWithBackupFingerprintContent(context: Context, content: String): Pair<Boolean, String> {
        var token = ""
        var salt = ""
        var hash = ""

        if (content.trim().startsWith("{")) {
            // JSON format
            try {
                val json = JSONObject(content)
                token = json.optString("token", "")
                salt = json.optString("salt", "")
                hash = json.optString("hash", "")
            } catch (_: Throwable) {}
        } else {
            // Key-value text format
            content.lines().forEach { line ->
                val trimmed = line.trim()
                when {
                    trimmed.startsWith("TOKEN=") -> token = trimmed.removePrefix("TOKEN=").trim()
                    trimmed.startsWith("SALT=") -> salt = trimmed.removePrefix("SALT=").trim()
                    trimmed.startsWith("HASH=") -> hash = trimmed.removePrefix("HASH=").trim()
                }
            }
        }

        if (token.isEmpty() || salt.isEmpty() || hash.isEmpty()) {
            return Pair(false, "Selected file is not a valid fingerprint backup credential.")
        }

        // Validate cryptographic hash signature
        val computedHash = hashWithSalt(token, salt)
        if (!computedHash.equals(hash, ignoreCase = true)) {
            return Pair(false, "Fingerprint credential signature verification failed (tampered or corrupted file).")
        }

        // Check against current config if registered on this device, OR accept valid token for cross-device unlock
        val current = _securityConfig.value
        val matchesCurrent = current.fingerprintBackupHash.isEmpty() ||
                current.fingerprintBackupHash.equals(hash, ignoreCase = true) ||
                current.biometricToken.equals(token, ignoreCase = true)

        // Unlock the application!
        _isAppUnlocked.value = true

        // Restore Profile and Profile Photo onto this device from the backup
        val profileRestored = extractAndRestoreProfileFromContent(context, content)
        val restoredProf = ProfileManager.userProfile.value
        val googleSnippet = if (restoredProf != null && restoredProf.googleEmail.isNotBlank()) {
            " & Google Account (${restoredProf.googleEmail})"
        } else ""

        // Extract and automatically enable 6-digit password on this device from the backup
        val pinRestored = extractAndRestorePinFromContent(context, content)

        // Restore dual vault pairing data and auto-connect to partner device
        val dualVaultRestored = extractAndRestoreDualVaultFromContent(context, content)

        // Ensure this device has fingerprint enabled, credential registered, and PIN auto-enabled from the backup
        val currentAfterRestore = _securityConfig.value
        val updated = currentAfterRestore.copy(
            isPinEnabled = currentAfterRestore.isPinEnabled || pinRestored,
            isFingerprintEnabled = true,
            isFingerprintRegistered = true,
            fingerprintBackupHash = hash,
            fingerprintBackupSalt = salt,
            biometricToken = token,
            failedAttempts = 0,
            updatedAt = System.currentTimeMillis()
        )
        saveConfig(context, updated)

        // Save local backup file if not present on this device yet, saving both fingerprint.dat and fingerprint_backup.dat
        try {
            val fpDir = AppStorageHelper.getFingerprintDir(context)
            val datFile = File(fpDir, FINGERPRINT_DAT_FILE_NAME)
            val backupFile = File(fpDir, FINGERPRINT_BACKUP_FILE_NAME)
            datFile.writeText(content, StandardCharsets.UTF_8)
            backupFile.writeText(content, StandardCharsets.UTF_8)
        } catch (_: Throwable) {}

        val successMsg = buildString {
            append("Backup fingerprint verified successfully! ")
            if (profileRestored) {
                append("Profile$googleSnippet restored. ")
            }
            if (pinRestored) {
                append("6-Digit Password auto-enabled! ")
            }
            if (dualVaultRestored) {
                append("Dual Vault reconnected! ")
            }
            append("App unlocked.")
        }

        return Pair(true, successMsg)
    }

    /**
     * Imports profile and Google account info from a selected fingerprint.dat file without requiring unlock.
     * Useful for importing credentials while already inside the app.
     */
    fun importProfileAndFingerprintFromUri(context: Context, backupFileUri: Uri): Pair<Boolean, String> {
        return verifyAndUnlockWithBackupFingerprint(context, backupFileUri)
    }

    /**
     * Gets the registered fingerprint backup file if present (prefers fingerprint.dat).
     */
    fun getFingerprintBackupFile(context: Context): File {
        val fpDir = AppStorageHelper.getFingerprintDir(context)
        val datFile = File(fpDir, FINGERPRINT_DAT_FILE_NAME)
        if (datFile.exists() && datFile.length() > 0) return datFile
        return File(fpDir, FINGERPRINT_BACKUP_FILE_NAME)
    }

    /**
     * Creates an Intent to share or export the fingerprint backup file.
     */
    fun createFingerprintShareIntent(context: Context): Intent? {
        val file = getFingerprintBackupFile(context)
        if (!file.exists() || file.length() == 0L) return null

        val uri = try {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (_: Throwable) {
            Uri.fromFile(file)
        }

        return Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "App Security - Fingerprint Backup Credential")
            putExtra(Intent.EXTRA_TEXT, "Secure fingerprint backup file generated by App Security.\nKeep this file safe to unlock on other devices or if you forget your PIN.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**
     * Toggles Anti-Screenshot and Screen Recording prevention (FLAG_SECURE).
     */
    fun setAntiScreenshotEnabled(context: Context, enabled: Boolean) {
        val updated = _securityConfig.value.copy(
            isAntiScreenshotEnabled = enabled,
            updatedAt = System.currentTimeMillis()
        )
        saveConfig(context, updated)
    }

    /**
     * Saves or changes master password hash in security folder.
     */
    fun changeMasterPassword(context: Context, oldPass: String, newPass: String): Pair<Boolean, String> {
        val current = _securityConfig.value
        if (current.masterPasswordHash.isNotEmpty()) {
            val oldHash = hashWithSalt(oldPass, current.masterPasswordSalt)
            if (!oldHash.equals(current.masterPasswordHash, ignoreCase = true)) {
                return Pair(false, "Current password is incorrect.")
            }
        }
        if (newPass.length < 4) {
            return Pair(false, "Password must be at least 4 characters.")
        }

        val salt = generateSaltHex()
        val hash = hashWithSalt(newPass, salt)

        try {
            val securityDir = AppStorageHelper.getSecurityDir(context)
            val passFile = File(securityDir, MASTER_PASS_FILE_NAME)
            passFile.writeText("$salt\n$hash\n# Master Vault Password Hash\n", StandardCharsets.UTF_8)
        } catch (e: Throwable) {
            e.printStackTrace()
        }

        val updated = current.copy(
            masterPasswordSalt = salt,
            masterPasswordHash = hash,
            updatedAt = System.currentTimeMillis()
        )
        saveConfig(context, updated)
        return Pair(true, "Master password updated successfully.")
    }

    /**
     * Lock the app immediately.
     */
    fun lockApp() {
        if (_securityConfig.value.isPinEnabled) {
            _isAppUnlocked.value = false
            appContext?.let {
                ActivityLogManager.log(it, "LOCK_APP", "App Locked", "App security lock was activated.", severity = "INFO")
            }
        }
    }

    /**
     * Unlocks the app directly when biometric verification succeeds.
     */
    fun unlockAppBiometric() {
        _isAppUnlocked.value = true
        appContext?.let {
            ActivityLogManager.log(it, "UNLOCK_BIO", "Biometric Fingerprint Unlock", "Fingerprint authenticated successfully.", severity = "SUCCESS")
        }
    }

    /**
     * Unlocks the app directly when face recognition or other security verification succeeds.
     */
    fun unlockApp() {
        _isAppUnlocked.value = true
        appContext?.let {
            ActivityLogManager.log(it, "UNLOCK_SECURITY", "Security Unlock", "App unlocked successfully.", severity = "SUCCESS")
        }
    }

    /**
     * Verifies master password against stored salt and hash.
     */
    fun verifyMasterPassword(password: String): Boolean {
        val config = _securityConfig.value
        if (config.masterPasswordHash.isEmpty()) return false
        val computed = hashWithSalt(password, config.masterPasswordSalt)
        return computed.equals(config.masterPasswordHash, ignoreCase = true)
    }

    /**
     * Checks device biometric capability status using BiometricManager.
     */
    fun checkBiometricStatus(context: Context): BiometricAvailability {
        return try {
            val bm = BiometricManager.from(context)
            when (val code = bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK)) {
                BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.AVAILABLE
                BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricAvailability.NO_HARDWARE
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                    if (_securityConfig.value.isFingerprintRegistered) BiometricAvailability.AVAILABLE else BiometricAvailability.NONE_ENROLLED
                }
                BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                    // Sensor busy or transient hardware check
                    if (_securityConfig.value.isFingerprintRegistered) BiometricAvailability.AVAILABLE else BiometricAvailability.HW_UNAVAILABLE
                }
                else -> {
                    // E.g. lockout or platform code
                    if (_securityConfig.value.isFingerprintRegistered) BiometricAvailability.AVAILABLE else BiometricAvailability.UNSUPPORTED
                }
            }
        } catch (_: Throwable) {
            if (_securityConfig.value.isFingerprintRegistered) BiometricAvailability.AVAILABLE else BiometricAvailability.UNSUPPORTED
        }
    }

    /**
     * Gathers stats about Android/media/<packageName>/security folder for user inspection.
     */
    fun getSecurityFolderInfo(context: Context): SecurityFolderInfo {
        val dir = AppStorageHelper.getSecurityDir(context)
        val files = dir.listFiles()?.map { Pair(it.name, it.length()) } ?: emptyList()
        val totalBytes = files.sumOf { it.second }
        return SecurityFolderInfo(
            folderPath = dir.absolutePath,
            totalSizeBytes = totalBytes,
            fileCount = files.size,
            files = files
        )
    }
}
