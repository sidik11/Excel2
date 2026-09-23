package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Base64
import com.example.data.local.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets

object ProfileManager {

    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile: StateFlow<UserProfile?> = _userProfile.asStateFlow()

    private const val PROFILE_FILE_NAME = "user_profile.json"
    private const val PROFILE_PHOTO_FILE_NAME = "profile_photo.jpg"

    private var isInitialized = false

    /**
     * Auto-fetches device name/model string.
     */
    fun getAutoDeviceModel(): String {
        val manufacturer = Build.MANUFACTURER.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        val model = Build.MODEL
        return if (model.startsWith(manufacturer, ignoreCase = true)) {
            model
        } else {
            "$manufacturer $model"
        }.trim()
    }

    /**
     * Initializes the ProfileManager, loading saved profile from internal and media storage.
     */
    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true
        loadProfile(context)
    }

    /**
     * Reloads profile from storage (both media folder and internal storage).
     */
    fun loadProfile(context: Context): UserProfile? {
        val mediaDir = AppStorageHelper.getProfileMediaDir(context)
        val mediaFile = File(mediaDir, PROFILE_FILE_NAME)
        val internalFile = File(context.filesDir, PROFILE_FILE_NAME)

        // Prefer media folder, fall back to internal filesDir
        val fileToRead = if (mediaFile.exists() && mediaFile.length() > 0) {
            mediaFile
        } else if (internalFile.exists() && internalFile.length() > 0) {
            internalFile
        } else {
            null
        }

        if (fileToRead != null) {
            try {
                val jsonStr = fileToRead.readText(StandardCharsets.UTF_8)
                val profile = jsonToProfile(jsonStr, context)
                if (profile != null && (profile.fullName.isNotBlank() || profile.emailId.isNotBlank() || profile.googleEmail.isNotBlank())) {
                    _userProfile.value = profile
                    return profile
                }
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }

        // If no profile was found in profile files, check if a fingerprint.dat exists on this device to restore
        try {
            val fpDatFile = File(AppStorageHelper.getFingerprintDir(context), AppSecurityManager.FINGERPRINT_DAT_FILE_NAME)
            val fpBackupFile = File(AppStorageHelper.getFingerprintDir(context), AppSecurityManager.FINGERPRINT_BACKUP_FILE_NAME)
            val targetFpFile = if (fpDatFile.exists() && fpDatFile.length() > 0) fpDatFile else if (fpBackupFile.exists() && fpBackupFile.length() > 0) fpBackupFile else null
            if (targetFpFile != null) {
                val content = targetFpFile.readText(StandardCharsets.UTF_8)
                AppSecurityManager.extractAndRestoreProfileFromContent(context, content)
                val restored = _userProfile.value
                if (restored != null) {
                    return restored
                }
            }
        } catch (_: Throwable) {}

        _userProfile.value = null
        return null
    }

    /**
     * Saves or updates the user profile to disk (both in media directory and app files).
     * Saves user_profile.json and decodes profile picture to profile_photo.jpg in media directory.
     */
    fun saveProfile(context: Context, profile: UserProfile, enforceComplete: Boolean = true): Pair<Boolean, String> {
        if (enforceComplete) {
            val missing = profile.getFirstMissingField()
            if (missing != null) {
                return Pair(false, "$missing is required.")
            }
        }

        try {
            val mediaDir = AppStorageHelper.getProfileMediaDir(context)
            val mediaPhotoFile = File(mediaDir, PROFILE_PHOTO_FILE_NAME)

            // Save image bytes to media folder
            if (profile.profileImageBase64.isNotBlank()) {
                try {
                    val imageBytes = Base64.decode(profile.profileImageBase64, Base64.DEFAULT)
                    mediaPhotoFile.writeBytes(imageBytes)
                } catch (_: Throwable) {}
            }

            val code = if (profile.deviceCode.length == 10) profile.deviceCode else generateDeviceProfileCode(
                email = if (profile.googleEmail.isNotBlank()) profile.googleEmail else profile.emailId,
                phone = profile.phoneNumber
            )

            val completeProfile = profile.copy(
                deviceCode = code,
                profileImagePath = if (mediaPhotoFile.exists()) mediaPhotoFile.absolutePath else profile.profileImagePath,
                device = if (profile.device.isBlank()) getAutoDeviceModel() else profile.device,
                updatedAt = System.currentTimeMillis()
            )

            val json = profileToJson(completeProfile)
            val jsonStr = json.toString(2)

            // 1. Save in Android/media/<packageName>/profile/user_profile.json
            val mediaJsonFile = File(mediaDir, PROFILE_FILE_NAME)
            mediaJsonFile.writeText(jsonStr, StandardCharsets.UTF_8)

            // 2. Save in app internal filesDir for redundancy
            val internalJsonFile = File(context.filesDir, PROFILE_FILE_NAME)
            internalJsonFile.writeText(jsonStr, StandardCharsets.UTF_8)

            _userProfile.value = completeProfile
            return Pair(true, "Profile saved successfully!")
        } catch (e: Throwable) {
            e.printStackTrace()
            return Pair(false, "Failed to save profile: ${e.message}")
        }
    }

    /**
     * Connects or updates the Google account for the user profile.
     */
    fun connectGoogleAccount(
        context: Context,
        email: String,
        displayName: String = "",
        googleId: String = "",
        photoUrl: String = ""
    ): Pair<Boolean, String> {
        val current = _userProfile.value ?: UserProfile(device = getAutoDeviceModel())
        val updated = current.copy(
            googleEmail = email.trim(),
            googleDisplayName = if (displayName.isNotBlank()) displayName.trim() else current.googleDisplayName,
            googleId = if (googleId.isNotBlank()) googleId.trim() else current.googleId,
            googleProfilePicUrl = if (photoUrl.isNotBlank()) photoUrl.trim() else current.googleProfilePicUrl,
            isGoogleConnected = true,
            emailId = if (current.emailId.isBlank()) email.trim() else current.emailId,
            fullName = if (current.fullName.isBlank() && displayName.isNotBlank()) displayName.trim() else current.fullName
        )

        val (ok, msg) = saveProfile(context, updated, enforceComplete = false)
        if (ok) {
            // Update fingerprint.dat immediately with the new Google account info
            AppSecurityManager.updateFingerprintProfileDataIfRegistered(context)
            return Pair(true, "Google account connected successfully: $email")
        }
        return Pair(false, msg)
    }

    /**
     * Disconnects Google account from the profile.
     */
    fun disconnectGoogleAccount(context: Context): Pair<Boolean, String> {
        val current = _userProfile.value ?: return Pair(false, "No profile exists.")
        val updated = current.copy(
            googleEmail = "",
            googleDisplayName = "",
            googleId = "",
            googleProfilePicUrl = "",
            isGoogleConnected = false
        )
        val (ok, msg) = saveProfile(context, updated, enforceComplete = false)
        if (ok) {
            AppSecurityManager.updateFingerprintProfileDataIfRegistered(context)
            return Pair(true, "Google account disconnected.")
        }
        return Pair(false, msg)
    }

    /**
     * Generates a 10-digit profile device code:
     * - First 5 letters of Gmail ID
     * - Last 2 digits of phone number
     * - 3 unique alphanumeric code characters
     */
    fun generateDeviceProfileCode(email: String, phone: String, existingCode: String = ""): String {
        if (existingCode.length == 10) return existingCode
        val emailToUse = email.ifBlank { "guest" }
        val userPart = emailToUse.substringBefore("@").filter { it.isLetterOrDigit() }.lowercase()
        val first5 = (userPart + "abcde").take(5)
        val phoneDigits = phone.filter { it.isDigit() }
        val last2 = if (phoneDigits.length >= 2) phoneDigits.takeLast(2) else "00"
        val unique3 = ((100..999).random()).toString()
        return "$first5$last2$unique3"
    }

    /**
     * Exports profile as a JSON object (used for embedding into fingerprint.dat).
     */
    fun profileToJson(profile: UserProfile): JSONObject {
        return JSONObject().apply {
            put("fullName", profile.fullName)
            put("dateOfBirth", profile.dateOfBirth)
            put("phoneNumber", profile.phoneNumber)
            put("emailId", profile.emailId)
            put("device", profile.device)
            put("village", profile.village)
            put("district", profile.district)
            put("state", profile.state)
            put("country", profile.country)
            put("pincode", profile.pincode)
            put("profileImageBase64", profile.profileImageBase64)
            put("profileImagePath", profile.profileImagePath)
            put("googleEmail", profile.googleEmail)
            put("googleDisplayName", profile.googleDisplayName)
            put("googleId", profile.googleId)
            put("googleProfilePicUrl", profile.googleProfilePicUrl)
            put("isGoogleConnected", profile.isGoogleConnected)
            put("deviceCode", profile.deviceCode)
            put("updatedAt", profile.updatedAt)
        }
    }

    /**
     * Parses JSON string into UserProfile, restoring image file into media directory if needed.
     */
    fun jsonToProfile(jsonStr: String, context: Context?): UserProfile? {
        return try {
            val json = JSONObject(jsonStr)
            val imgBase64 = json.optString("profileImageBase64", "")
            var imgPath = json.optString("profileImagePath", "")

            // If we have base64 and context, ensure media file exists
            if (imgBase64.isNotBlank() && context != null) {
                val mediaDir = AppStorageHelper.getProfileMediaDir(context)
                val photoFile = File(mediaDir, PROFILE_PHOTO_FILE_NAME)
                try {
                    val bytes = Base64.decode(imgBase64, Base64.DEFAULT)
                    photoFile.writeBytes(bytes)
                    imgPath = photoFile.absolutePath
                } catch (_: Throwable) {}
            }

            val gEmail = json.optString("googleEmail", "")
            val isGConnected = json.optBoolean("isGoogleConnected", false) || gEmail.isNotBlank()
            val phone = json.optString("phoneNumber", "")
            var devCode = json.optString("deviceCode", "")
            if (devCode.length != 10) {
                devCode = generateDeviceProfileCode(gEmail.ifBlank { json.optString("emailId", "") }, phone)
            }

            UserProfile(
                fullName = json.optString("fullName", ""),
                dateOfBirth = json.optString("dateOfBirth", ""),
                phoneNumber = phone,
                emailId = json.optString("emailId", ""),
                device = json.optString("device", ""),
                village = json.optString("village", ""),
                district = json.optString("district", ""),
                state = json.optString("state", ""),
                country = json.optString("country", ""),
                pincode = json.optString("pincode", ""),
                profileImageBase64 = imgBase64,
                profileImagePath = imgPath,
                googleEmail = gEmail,
                googleDisplayName = json.optString("googleDisplayName", ""),
                googleId = json.optString("googleId", ""),
                googleProfilePicUrl = json.optString("googleProfilePicUrl", ""),
                isGoogleConnected = isGConnected,
                deviceCode = devCode,
                updatedAt = json.optLong("updatedAt", 0L)
            )
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Imports and activates a UserProfile restored from a fingerprint.dat file on this device.
     */
    fun restoreProfileFromFingerprint(context: Context, profile: UserProfile) {
        saveProfile(context, profile, enforceComplete = false)
    }

    /**
     * Checks if a valid, complete profile exists.
     */
    fun hasCompleteProfile(): Boolean {
        val p = _userProfile.value
        return p != null && p.isComplete()
    }

    /**
     * Deletes existing profile from storage and clears memory state.
     */
    fun deleteProfile(context: Context) {
        try {
            val mediaDir = AppStorageHelper.getProfileMediaDir(context)
            File(mediaDir, PROFILE_FILE_NAME).delete()
            File(mediaDir, PROFILE_PHOTO_FILE_NAME).delete()
            val internalJsonFile = File(context.filesDir, PROFILE_FILE_NAME)
            internalJsonFile.delete()
        } catch (_: Throwable) {}
        _userProfile.value = null
    }

    /**
     * Helper to read a Uri into Base64 encoded JPEG string (downscaling if needed).
     */
    fun uriToBase64(context: Context, uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            // Downscale to max 600x600 for performance and sensible backup file size
            val maxDim = 600
            val width = bitmap.width
            val height = bitmap.height
            val scaledBitmap = if (width > maxDim || height > maxDim) {
                val ratio = width.toFloat() / height.toFloat()
                val targetW: Int
                val targetH: Int
                if (ratio > 1f) {
                    targetW = maxDim
                    targetH = (maxDim / ratio).toInt()
                } else {
                    targetH = maxDim
                    targetW = (maxDim * ratio).toInt()
                }
                Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
            } else {
                bitmap
            }

            val baos = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos)
            val bytes = baos.toByteArray()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Throwable) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Helper to decode a Base64 string back into a Bitmap.
     */
    fun base64ToBitmap(base64: String): Bitmap? {
        if (base64.isBlank()) return null
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Throwable) {
            e.printStackTrace()
            null
        }
    }
}
