package com.example.util

import android.content.Context
import android.content.SharedPreferences
import com.example.data.AppSettings
import com.example.data.AppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object SettingsManager {
    private const val PREFS_NAME = "app_settings_prefs"
    private const val KEY_THEME = "key_app_theme"
    private const val KEY_LOW_RAM = "key_low_ram_mode"
    private const val KEY_DIRECT_FOLDER = "key_direct_folder_access"
    private const val KEY_GRID_COLUMNS = "key_grid_columns"
    private const val KEY_SLIDESHOW_SPEED = "key_slideshow_speed"
    private const val KEY_FAST_FORWARD_SPEED = "key_fast_forward_speed"
    private const val KEY_AUTO_SYNC_EX = "key_auto_sync_ex"
    private const val KEY_KEEP_VAULT_OPEN = "key_keep_vault_open"
    private const val KEY_INTERNAL_STORAGE_RAM = "key_internal_storage_replace_ram"
    private const val KEY_DEDICATED_FOLDER = "key_dedicated_media_folder"
    private const val KEY_THUMBNAIL_QUALITY = "key_thumbnail_quality"
    private const val KEY_SLIDESHOW_LOOP = "key_slideshow_loop"
    private const val KEY_HIGH_CONTRAST = "key_high_contrast_borders"
    private const val KEY_IMAGE_FIT_MODE = "key_image_fit_mode"
    private const val KEY_AUTO_LOCK_TIMEOUT = "key_auto_lock_timeout"
    private const val KEY_SHOW_FILE_INFO = "key_show_file_info"
    private const val KEY_HAPTIC_FEEDBACK = "key_haptic_feedback"
    private const val KEY_CLEAN_EX_ON_LOCK = "key_clean_ex_on_lock"
    private const val KEY_SLIDESHOW_TRANSITION = "key_slideshow_transition"
    private const val KEY_RECENT_APP_PRIVACY = "key_recent_app_privacy"
    private const val KEY_APP_LOGO_TS = "key_custom_app_logo_ts"
    private const val KEY_FB_CONNECTED = "key_fb_connected"
    private const val KEY_FB_USERNAME = "key_fb_username"
    private const val KEY_APP_DOWNLOAD_URL = "key_app_download_url"
    private const val KEY_FACE_LOCK_ENABLED = "key_face_lock_enabled"

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            loadSettings()
        }
    }

    private fun loadSettings() {
        val p = prefs ?: return
        val themeName = p.getString(KEY_THEME, AppTheme.CYBER_PURPLE.name) ?: AppTheme.CYBER_PURPLE.name
        val theme = try {
            AppTheme.valueOf(themeName)
        } catch (_: Exception) {
            AppTheme.CYBER_PURPLE
        }
        val lowRam = p.getBoolean(KEY_LOW_RAM, true)
        val directFolder = p.getBoolean(KEY_DIRECT_FOLDER, true)
        val columns = p.getInt(KEY_GRID_COLUMNS, 3)
        val speed = p.getLong(KEY_SLIDESHOW_SPEED, 1500L)
        val ffSpeed = p.getLong(KEY_FAST_FORWARD_SPEED, 300L)
        val autoSync = p.getBoolean(KEY_AUTO_SYNC_EX, true)
        val keepOpen = p.getBoolean(KEY_KEEP_VAULT_OPEN, true)
        val storageReplaceRam = p.getBoolean(KEY_INTERNAL_STORAGE_RAM, true)
        val dedicatedFolder = p.getBoolean(KEY_DEDICATED_FOLDER, true)
        val quality = p.getString(KEY_THUMBNAIL_QUALITY, "medium") ?: "medium"
        val loop = p.getBoolean(KEY_SLIDESHOW_LOOP, true)
        val contrast = p.getBoolean(KEY_HIGH_CONTRAST, true)
        val fitMode = p.getString(KEY_IMAGE_FIT_MODE, "fit") ?: "fit"
        val autoLock = p.getInt(KEY_AUTO_LOCK_TIMEOUT, 0)
        val showInfo = p.getBoolean(KEY_SHOW_FILE_INFO, true)
        val haptic = p.getBoolean(KEY_HAPTIC_FEEDBACK, true)
        val cleanEx = p.getBoolean(KEY_CLEAN_EX_ON_LOCK, true)
        val transition = p.getString(KEY_SLIDESHOW_TRANSITION, "fade") ?: "fade"
        val recentPrivacy = p.getBoolean(KEY_RECENT_APP_PRIVACY, true)
        val logoTs = p.getLong(KEY_APP_LOGO_TS, 0L)
        val fbConnected = p.getBoolean(KEY_FB_CONNECTED, false)
        val fbUser = p.getString(KEY_FB_USERNAME, "") ?: ""
        val downloadUrl = p.getString(KEY_APP_DOWNLOAD_URL, "https://ais-pre-jgxqbiezgnewblvh6iilnv-571171211889.asia-southeast1.run.app") ?: "https://ais-pre-jgxqbiezgnewblvh6iilnv-571171211889.asia-southeast1.run.app"
        val faceLock = p.getBoolean(KEY_FACE_LOCK_ENABLED, false)

        _settings.value = AppSettings(
            theme = theme,
            isLowRamMode = lowRam,
            isDirectFolderAccess = directFolder,
            gridColumns = columns,
            slideshowSpeedMs = speed,
            fastForwardSpeedMs = ffSpeed,
            autoSyncExVault = autoSync,
            keepVaultUnlockedAcrossRestarts = keepOpen,
            useInternalStorageReplaceRam = storageReplaceRam,
            dedicatedMediaFolderEnabled = dedicatedFolder,
            thumbnailQuality = quality,
            slideshowLoop = loop,
            highContrastBorders = contrast,
            imageFitMode = fitMode,
            autoLockTimeoutMinutes = autoLock,
            showFileInfoOverlay = showInfo,
            hapticFeedback = haptic,
            cleanExVaultOnLock = cleanEx,
            slideshowTransition = transition,
            recentAppPrivacy = recentPrivacy,
            customAppLogoTimestamp = logoTs,
            facebookConnected = fbConnected,
            facebookUserName = fbUser,
            appDownloadUrl = downloadUrl,
            faceLockEnabled = faceLock
        )
    }

    fun setTheme(theme: AppTheme) {
        prefs?.edit()?.putString(KEY_THEME, theme.name)?.apply()
        _settings.value = _settings.value.copy(theme = theme)
    }

    fun setLowRamMode(enabled: Boolean) {
        prefs?.edit()?.putBoolean(KEY_LOW_RAM, enabled)?.apply()
        _settings.value = _settings.value.copy(isLowRamMode = enabled)
    }

    fun setDirectFolderAccess(enabled: Boolean) {
        prefs?.edit()?.putBoolean(KEY_DIRECT_FOLDER, enabled)?.apply()
        _settings.value = _settings.value.copy(isDirectFolderAccess = enabled)
    }

    fun setGridColumns(columns: Int) {
        prefs?.edit()?.putInt(KEY_GRID_COLUMNS, columns)?.apply()
        _settings.value = _settings.value.copy(gridColumns = columns)
    }

    fun setSlideshowSpeedMs(speedMs: Long) {
        prefs?.edit()?.putLong(KEY_SLIDESHOW_SPEED, speedMs)?.apply()
        _settings.value = _settings.value.copy(slideshowSpeedMs = speedMs)
    }

    fun setFastForwardSpeedMs(speedMs: Long) {
        prefs?.edit()?.putLong(KEY_FAST_FORWARD_SPEED, speedMs)?.apply()
        _settings.value = _settings.value.copy(fastForwardSpeedMs = speedMs)
    }

    fun setAutoSyncExVault(enabled: Boolean) {
        prefs?.edit()?.putBoolean(KEY_AUTO_SYNC_EX, enabled)?.apply()
        _settings.value = _settings.value.copy(autoSyncExVault = enabled)
    }

    fun setKeepVaultUnlockedAcrossRestarts(enabled: Boolean) {
        prefs?.edit()?.putBoolean(KEY_KEEP_VAULT_OPEN, enabled)?.apply()
        _settings.value = _settings.value.copy(keepVaultUnlockedAcrossRestarts = enabled)
    }

    fun setUseInternalStorageReplaceRam(enabled: Boolean) {
        prefs?.edit()?.putBoolean(KEY_INTERNAL_STORAGE_RAM, enabled)?.apply()
        _settings.value = _settings.value.copy(useInternalStorageReplaceRam = enabled)
    }

    fun setDedicatedMediaFolderEnabled(enabled: Boolean) {
        prefs?.edit()?.putBoolean(KEY_DEDICATED_FOLDER, enabled)?.apply()
        _settings.value = _settings.value.copy(dedicatedMediaFolderEnabled = enabled)
    }

    fun setThumbnailQuality(quality: String) {
        prefs?.edit()?.putString(KEY_THUMBNAIL_QUALITY, quality)?.apply()
        _settings.value = _settings.value.copy(thumbnailQuality = quality)
    }

    fun setSlideshowLoop(enabled: Boolean) {
        prefs?.edit()?.putBoolean(KEY_SLIDESHOW_LOOP, enabled)?.apply()
        _settings.value = _settings.value.copy(slideshowLoop = enabled)
    }

    fun setHighContrastBorders(enabled: Boolean) {
        prefs?.edit()?.putBoolean(KEY_HIGH_CONTRAST, enabled)?.apply()
        _settings.value = _settings.value.copy(highContrastBorders = enabled)
    }

    fun setImageFitMode(mode: String) {
        prefs?.edit()?.putString(KEY_IMAGE_FIT_MODE, mode)?.apply()
        _settings.value = _settings.value.copy(imageFitMode = mode)
    }

    fun setAutoLockTimeoutMinutes(minutes: Int) {
        prefs?.edit()?.putInt(KEY_AUTO_LOCK_TIMEOUT, minutes)?.apply()
        _settings.value = _settings.value.copy(autoLockTimeoutMinutes = minutes)
    }

    fun setShowFileInfoOverlay(enabled: Boolean) {
        prefs?.edit()?.putBoolean(KEY_SHOW_FILE_INFO, enabled)?.apply()
        _settings.value = _settings.value.copy(showFileInfoOverlay = enabled)
    }

    fun setHapticFeedback(enabled: Boolean) {
        prefs?.edit()?.putBoolean(KEY_HAPTIC_FEEDBACK, enabled)?.apply()
        _settings.value = _settings.value.copy(hapticFeedback = enabled)
    }

    fun setCleanExVaultOnLock(enabled: Boolean) {
        prefs?.edit()?.putBoolean(KEY_CLEAN_EX_ON_LOCK, enabled)?.apply()
        _settings.value = _settings.value.copy(cleanExVaultOnLock = enabled)
    }

    fun setSlideshowTransition(transition: String) {
        prefs?.edit()?.putString(KEY_SLIDESHOW_TRANSITION, transition)?.apply()
        _settings.value = _settings.value.copy(slideshowTransition = transition)
    }

    fun setRecentAppPrivacy(enabled: Boolean) {
        prefs?.edit()?.putBoolean(KEY_RECENT_APP_PRIVACY, enabled)?.apply()
        _settings.value = _settings.value.copy(recentAppPrivacy = enabled)
    }

    fun setCustomAppLogoTimestamp(timestamp: Long) {
        prefs?.edit()?.putLong(KEY_APP_LOGO_TS, timestamp)?.apply()
        _settings.value = _settings.value.copy(customAppLogoTimestamp = timestamp)
    }

    fun setFacebookConnected(connected: Boolean, userName: String = "") {
        prefs?.edit()?.putBoolean(KEY_FB_CONNECTED, connected)?.putString(KEY_FB_USERNAME, userName)?.apply()
        _settings.value = _settings.value.copy(facebookConnected = connected, facebookUserName = userName)
    }

    fun setAppDownloadUrl(url: String) {
        prefs?.edit()?.putString(KEY_APP_DOWNLOAD_URL, url)?.apply()
        _settings.value = _settings.value.copy(appDownloadUrl = url)
    }

    fun setFaceLockEnabled(enabled: Boolean) {
        prefs?.edit()?.putBoolean(KEY_FACE_LOCK_ENABLED, enabled)?.apply()
        _settings.value = _settings.value.copy(faceLockEnabled = enabled)
    }
}
