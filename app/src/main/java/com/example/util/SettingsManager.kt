package com.example.util

import android.content.Context
import android.content.SharedPreferences
import com.example.data.AppSettings
import com.example.data.AppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

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
    private const val KEY_SHAKE_TO_LOCK = "key_shake_to_lock"
    private const val KEY_SHAKE_DURATION = "key_shake_duration_sec"
    private const val KEY_DUAL_VAULT_SLIDESHOW_INTERVAL = "key_dual_vault_slideshow_interval"
    private const val KEY_LAUNCHER_DISGUISE = "key_launcher_disguise"
    private const val KEY_LAUNCHER_CUSTOM_ICON_URI = "key_launcher_custom_icon_uri"
    private const val KEY_PANIC_CUSTOM_IMAGE = "key_panic_custom_image"
    private const val KEY_SRA_INTERVAL = "key_sra_slideshow_interval"
    private const val KEY_SRA_AUTO_LOOP = "key_sra_auto_loop"
    private const val KEY_SRA_SHOW_NAME_BADGE = "key_sra_show_name_badge"
    private const val KEY_SRA_PAUSE_ON_TAP = "key_sra_pause_on_tap"
    private const val KEY_SRA_TRANSITION = "key_sra_transition"
    private const val KEY_SSI_INTERVAL = "key_ssi_slideshow_interval"
    private const val KEY_SSI_AUTO_LOOP = "key_ssi_auto_loop"
    private const val KEY_AUTO_ZIP_MEDIA = "key_auto_zip_media"
    private const val KEY_DELETE_RAW_MEDIA_AFTER_ZIP = "key_delete_raw_media_after_zip"
    private const val KEY_RNAME_PREFIX = "key_rname_prefix"
    private const val KEY_RNAME_PADDING = "key_rname_padding"
    private const val KEY_RNAME_START_NUM = "key_rname_start_num"
    private const val KEY_RNAME_CONFIRM = "key_rname_confirm"
    private const val KEY_CUSTOM_ACCENT_COLOR = "key_custom_accent_color"
    private const val KEY_GRID_DENSITY = "key_grid_density"
    private const val KEY_UI_ANIM_SPEED = "key_ui_anim_speed"
    private const val KEY_DEFAULT_START_TAB = "key_default_start_tab"
    private const val KEY_HQ_RENDERING = "key_hq_rendering"
    private const val KEY_SHOW_STORAGE_HEADER = "key_show_storage_header"
    private const val KEY_FLIP_TO_PANIC = "key_flip_to_panic"
    private const val KEY_VOLUME_BUTTON_PANIC = "key_volume_button_panic"
    private const val KEY_LAUNCHER_PRESET = "key_launcher_preset"
    private const val KEY_SSI_STREAM_QUALITY = "key_ssi_stream_quality"

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
        val shakeToLock = p.getBoolean(KEY_SHAKE_TO_LOCK, false)
        val shakeDuration = p.getFloat(KEY_SHAKE_DURATION, 1.0f)
        val dvInterval = p.getInt(KEY_DUAL_VAULT_SLIDESHOW_INTERVAL, 3)

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
            faceLockEnabled = faceLock,
            shakeToLockEnabled = shakeToLock,
            shakeToLockDurationSeconds = shakeDuration,
            dualVaultSlideshowIntervalSeconds = dvInterval,
            launcherDisguiseName = p.getString(KEY_LAUNCHER_DISGUISE, "Excel") ?: "Excel",
            launcherCustomIconUri = p.getString(KEY_LAUNCHER_CUSTOM_ICON_URI, "") ?: "",
            panicCustomImageUri = p.getString(KEY_PANIC_CUSTOM_IMAGE, "") ?: "",
            sraSlideshowIntervalSeconds = p.getFloat(KEY_SRA_INTERVAL, 2.0f),
            sraAutoLoop = p.getBoolean(KEY_SRA_AUTO_LOOP, true),
            sraShowNameBadge = p.getBoolean(KEY_SRA_SHOW_NAME_BADGE, true),
            sraPauseOnTap = p.getBoolean(KEY_SRA_PAUSE_ON_TAP, true),
            sraTransition = p.getString(KEY_SRA_TRANSITION, "fade") ?: "fade",
            ssiSlideshowIntervalSeconds = p.getFloat(KEY_SSI_INTERVAL, 2.0f),
            ssiAutoLoop = p.getBoolean(KEY_SSI_AUTO_LOOP, true),
            autoZipMediaOnLock = p.getBoolean(KEY_AUTO_ZIP_MEDIA, false),
            deleteRawMediaAfterZip = p.getBoolean(KEY_DELETE_RAW_MEDIA_AFTER_ZIP, false),
            rnameSequencePrefix = p.getString(KEY_RNAME_PREFIX, "VHot") ?: "VHot",
            rnamePaddingDigits = p.getInt(KEY_RNAME_PADDING, 4),
            rnameStartNumber = p.getInt(KEY_RNAME_START_NUM, 1),
            rnameConfirmBeforeRename = p.getBoolean(KEY_RNAME_CONFIRM, true),
            customAccentColor = p.getString(KEY_CUSTOM_ACCENT_COLOR, "DEFAULT") ?: "DEFAULT",
            galleryGridDensity = p.getString(KEY_GRID_DENSITY, "STANDARD") ?: "STANDARD",
            uiAnimationSpeed = p.getString(KEY_UI_ANIM_SPEED, "NORMAL") ?: "NORMAL",
            defaultStartTab = p.getString(KEY_DEFAULT_START_TAB, "EXCEL") ?: "EXCEL",
            highQualityRendering = p.getBoolean(KEY_HQ_RENDERING, true),
            showStorageStatsHeader = p.getBoolean(KEY_SHOW_STORAGE_HEADER, true),
            flipToPanicEnabled = p.getBoolean(KEY_FLIP_TO_PANIC, true),
            volumeButtonPanicEnabled = p.getBoolean(KEY_VOLUME_BUTTON_PANIC, true),
            launcherDisguisePreset = p.getString(KEY_LAUNCHER_PRESET, "EXCEL") ?: "EXCEL",
            ssiStreamQuality = p.getString(KEY_SSI_STREAM_QUALITY, "ULTRA_FAST") ?: "ULTRA_FAST"
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

    fun setShakeToLockEnabled(enabled: Boolean) {
        prefs?.edit()?.putBoolean(KEY_SHAKE_TO_LOCK, enabled)?.apply()
        _settings.value = _settings.value.copy(shakeToLockEnabled = enabled)
    }

    fun setShakeToLockDurationSeconds(seconds: Float) {
        prefs?.edit()?.putFloat(KEY_SHAKE_DURATION, seconds)?.apply()
        _settings.value = _settings.value.copy(shakeToLockDurationSeconds = seconds)
    }

    fun setDualVaultSlideshowIntervalSeconds(seconds: Int) {
        prefs?.edit()?.putInt(KEY_DUAL_VAULT_SLIDESHOW_INTERVAL, seconds)?.apply()
        _settings.value = _settings.value.copy(dualVaultSlideshowIntervalSeconds = seconds)
    }

    fun setLauncherDisguise(disguiseName: String) {
        prefs?.edit()?.putString(KEY_LAUNCHER_DISGUISE, disguiseName)?.apply()
        _settings.value = _settings.value.copy(launcherDisguiseName = disguiseName)
    }

    fun setLauncherCustomIconUri(uri: String) {
        prefs?.edit()?.putString(KEY_LAUNCHER_CUSTOM_ICON_URI, uri)?.apply()
        _settings.value = _settings.value.copy(launcherCustomIconUri = uri)
    }

    fun setPanicCustomImageUri(uri: String) {
        prefs?.edit()?.putString(KEY_PANIC_CUSTOM_IMAGE, uri)?.apply()
        _settings.value = _settings.value.copy(panicCustomImageUri = uri)
    }

    // SRA Settings
    fun setSraSlideshowIntervalSeconds(seconds: Float) {
        prefs?.edit()?.putFloat(KEY_SRA_INTERVAL, seconds)?.apply()
        _settings.value = _settings.value.copy(sraSlideshowIntervalSeconds = seconds)
    }

    fun setSraAutoLoop(enabled: Boolean) {
        prefs?.edit()?.putBoolean(KEY_SRA_AUTO_LOOP, enabled)?.apply()
        _settings.value = _settings.value.copy(sraAutoLoop = enabled)
    }

    fun setSraShowNameBadge(enabled: Boolean) {
        prefs?.edit()?.putBoolean(KEY_SRA_SHOW_NAME_BADGE, enabled)?.apply()
        _settings.value = _settings.value.copy(sraShowNameBadge = enabled)
    }

    fun setSraPauseOnTap(enabled: Boolean) {
        prefs?.edit()?.putBoolean(KEY_SRA_PAUSE_ON_TAP, enabled)?.apply()
        _settings.value = _settings.value.copy(sraPauseOnTap = enabled)
    }

    fun setSraTransition(transition: String) {
        prefs?.edit()?.putString(KEY_SRA_TRANSITION, transition)?.apply()
        _settings.value = _settings.value.copy(sraTransition = transition)
    }

    // SSI Settings
    fun setSsiSlideshowIntervalSeconds(seconds: Float) {
        prefs?.edit()?.putFloat(KEY_SSI_INTERVAL, seconds)?.apply()
        _settings.value = _settings.value.copy(ssiSlideshowIntervalSeconds = seconds)
    }

    fun setSsiAutoLoop(enabled: Boolean) {
        prefs?.edit()?.putBoolean(KEY_SSI_AUTO_LOOP, enabled)?.apply()
        _settings.value = _settings.value.copy(ssiAutoLoop = enabled)
    }

    // Media Folder ZIP Encryption Settings
    fun setAutoZipMediaOnLock(enabled: Boolean) {
        prefs?.edit()?.putBoolean(KEY_AUTO_ZIP_MEDIA, enabled)?.apply()
        _settings.value = _settings.value.copy(autoZipMediaOnLock = enabled)
    }

    fun setDeleteRawMediaAfterZip(enabled: Boolean) {
        prefs?.edit()?.putBoolean(KEY_DELETE_RAW_MEDIA_AFTER_ZIP, enabled)?.apply()
        _settings.value = _settings.value.copy(deleteRawMediaAfterZip = enabled)
    }

    // Rname Settings
    fun setRnameSequencePrefix(prefix: String) {
        prefs?.edit()?.putString(KEY_RNAME_PREFIX, prefix)?.apply()
        _settings.value = _settings.value.copy(rnameSequencePrefix = prefix)
    }

    fun setRnamePaddingDigits(digits: Int) {
        prefs?.edit()?.putInt(KEY_RNAME_PADDING, digits)?.apply()
        _settings.value = _settings.value.copy(rnamePaddingDigits = digits)
    }

    fun setRnameStartNumber(startNum: Int) {
        prefs?.edit()?.putInt(KEY_RNAME_START_NUM, startNum)?.apply()
        _settings.value = _settings.value.copy(rnameStartNumber = startNum)
    }

    fun setRnameConfirmBeforeRename(enabled: Boolean) {
        prefs?.edit()?.putBoolean(KEY_RNAME_CONFIRM, enabled)?.apply()
        _settings.value = _settings.value.copy(rnameConfirmBeforeRename = enabled)
    }

    // Customization Settings
    fun setCustomAccentColor(accent: String) {
        prefs?.edit()?.putString(KEY_CUSTOM_ACCENT_COLOR, accent)?.apply()
        _settings.value = _settings.value.copy(customAccentColor = accent)
    }

    fun setGalleryGridDensity(density: String) {
        prefs?.edit()?.putString(KEY_GRID_DENSITY, density)?.apply()
        _settings.value = _settings.value.copy(galleryGridDensity = density)
    }

    fun setUiAnimationSpeed(speed: String) {
        prefs?.edit()?.putString(KEY_UI_ANIM_SPEED, speed)?.apply()
        _settings.value = _settings.value.copy(uiAnimationSpeed = speed)
    }

    fun setDefaultStartTab(tab: String) {
        prefs?.edit()?.putString(KEY_DEFAULT_START_TAB, tab)?.apply()
        _settings.value = _settings.value.copy(defaultStartTab = tab)
    }

    fun setHighQualityRendering(enabled: Boolean) {
        prefs?.edit()?.putBoolean(KEY_HQ_RENDERING, enabled)?.apply()
        _settings.value = _settings.value.copy(highQualityRendering = enabled)
    }

    fun setShowStorageStatsHeader(enabled: Boolean) {
        prefs?.edit()?.putBoolean(KEY_SHOW_STORAGE_HEADER, enabled)?.apply()
        _settings.value = _settings.value.copy(showStorageStatsHeader = enabled)
    }

    fun setFlipToPanicEnabled(enabled: Boolean) {
        prefs?.edit()?.putBoolean(KEY_FLIP_TO_PANIC, enabled)?.apply()
        _settings.value = _settings.value.copy(flipToPanicEnabled = enabled)
    }

    fun setVolumeButtonPanicEnabled(enabled: Boolean) {
        prefs?.edit()?.putBoolean(KEY_VOLUME_BUTTON_PANIC, enabled)?.apply()
        _settings.value = _settings.value.copy(volumeButtonPanicEnabled = enabled)
    }

    fun setLauncherDisguisePreset(preset: String) {
        prefs?.edit()?.putString(KEY_LAUNCHER_PRESET, preset)?.apply()
        _settings.value = _settings.value.copy(launcherDisguisePreset = preset)
    }

    fun setSsiStreamQuality(quality: String) {
        prefs?.edit()?.putString(KEY_SSI_STREAM_QUALITY, quality)?.apply()
        _settings.value = _settings.value.copy(ssiStreamQuality = quality)
    }

    /**
     * Serializes complete settings into a JSON representation for fingerprint.dat backup.
     */
    fun settingsToJson(s: AppSettings = _settings.value): JSONObject {
        return JSONObject().apply {
            put("theme", s.theme.name)
            put("isLowRamMode", s.isLowRamMode)
            put("isDirectFolderAccess", s.isDirectFolderAccess)
            put("gridColumns", s.gridColumns)
            put("slideshowSpeedMs", s.slideshowSpeedMs)
            put("fastForwardSpeedMs", s.fastForwardSpeedMs)
            put("autoSyncExVault", s.autoSyncExVault)
            put("keepVaultUnlockedAcrossRestarts", s.keepVaultUnlockedAcrossRestarts)
            put("useInternalStorageReplaceRam", s.useInternalStorageReplaceRam)
            put("dedicatedMediaFolderEnabled", s.dedicatedMediaFolderEnabled)
            put("thumbnailQuality", s.thumbnailQuality)
            put("slideshowLoop", s.slideshowLoop)
            put("highContrastBorders", s.highContrastBorders)
            put("imageFitMode", s.imageFitMode)
            put("autoLockTimeoutMinutes", s.autoLockTimeoutMinutes)
            put("showFileInfoOverlay", s.showFileInfoOverlay)
            put("hapticFeedback", s.hapticFeedback)
            put("cleanExVaultOnLock", s.cleanExVaultOnLock)
            put("slideshowTransition", s.slideshowTransition)
            put("recentAppPrivacy", s.recentAppPrivacy)
            put("customAppLogoTimestamp", s.customAppLogoTimestamp)
            put("facebookConnected", s.facebookConnected)
            put("facebookUserName", s.facebookUserName)
            put("appDownloadUrl", s.appDownloadUrl)
            put("faceLockEnabled", s.faceLockEnabled)
            put("shakeToLockEnabled", s.shakeToLockEnabled)
            put("shakeToLockDurationSeconds", s.shakeToLockDurationSeconds.toDouble())
            put("panicCustomImageUri", s.panicCustomImageUri)
            put("dualVaultSlideshowIntervalSeconds", s.dualVaultSlideshowIntervalSeconds)
            put("launcherDisguiseName", s.launcherDisguiseName)
            put("launcherCustomIconUri", s.launcherCustomIconUri)
            put("sraSlideshowIntervalSeconds", s.sraSlideshowIntervalSeconds.toDouble())
            put("sraAutoLoop", s.sraAutoLoop)
            put("sraShowNameBadge", s.sraShowNameBadge)
            put("sraPauseOnTap", s.sraPauseOnTap)
            put("sraTransition", s.sraTransition)
            put("ssiSlideshowIntervalSeconds", s.ssiSlideshowIntervalSeconds.toDouble())
            put("ssiAutoLoop", s.ssiAutoLoop)
            put("autoZipMediaOnLock", s.autoZipMediaOnLock)
            put("deleteRawMediaAfterZip", s.deleteRawMediaAfterZip)
            put("rnameSequencePrefix", s.rnameSequencePrefix)
            put("rnamePaddingDigits", s.rnamePaddingDigits)
            put("rnameStartNumber", s.rnameStartNumber)
            put("rnameConfirmBeforeRename", s.rnameConfirmBeforeRename)
            put("customAccentColor", s.customAccentColor)
            put("galleryGridDensity", s.galleryGridDensity)
            put("uiAnimationSpeed", s.uiAnimationSpeed)
            put("defaultStartTab", s.defaultStartTab)
            put("highQualityRendering", s.highQualityRendering)
            put("showStorageStatsHeader", s.showStorageStatsHeader)
            put("flipToPanicEnabled", s.flipToPanicEnabled)
            put("volumeButtonPanicEnabled", s.volumeButtonPanicEnabled)
            put("launcherDisguisePreset", s.launcherDisguisePreset)
            put("ssiStreamQuality", s.ssiStreamQuality)
        }
    }

    /**
     * Restores all settings from a JSONObject (e.g. from fingerprint.dat).
     */
    fun restoreSettingsFromJson(json: JSONObject) {
        val p = prefs ?: return
        val editor = p.edit()

        if (json.has("theme")) {
            val themeName = json.optString("theme", _settings.value.theme.name)
            val theme = try { AppTheme.valueOf(themeName) } catch (_: Throwable) { _settings.value.theme }
            editor.putString(KEY_THEME, theme.name)
        }
        if (json.has("isLowRamMode")) editor.putBoolean(KEY_LOW_RAM, json.optBoolean("isLowRamMode", true))
        if (json.has("isDirectFolderAccess")) editor.putBoolean(KEY_DIRECT_FOLDER, json.optBoolean("isDirectFolderAccess", true))
        if (json.has("gridColumns")) editor.putInt(KEY_GRID_COLUMNS, json.optInt("gridColumns", 3))
        if (json.has("slideshowSpeedMs")) editor.putLong(KEY_SLIDESHOW_SPEED, json.optLong("slideshowSpeedMs", 1500L))
        if (json.has("fastForwardSpeedMs")) editor.putLong(KEY_FAST_FORWARD_SPEED, json.optLong("fastForwardSpeedMs", 300L))
        if (json.has("autoSyncExVault")) editor.putBoolean(KEY_AUTO_SYNC_EX, json.optBoolean("autoSyncExVault", true))
        if (json.has("keepVaultUnlockedAcrossRestarts")) editor.putBoolean(KEY_KEEP_VAULT_OPEN, json.optBoolean("keepVaultUnlockedAcrossRestarts", true))
        if (json.has("useInternalStorageReplaceRam")) editor.putBoolean(KEY_INTERNAL_STORAGE_RAM, json.optBoolean("useInternalStorageReplaceRam", true))
        if (json.has("dedicatedMediaFolderEnabled")) editor.putBoolean(KEY_DEDICATED_FOLDER, json.optBoolean("dedicatedMediaFolderEnabled", true))
        if (json.has("thumbnailQuality")) editor.putString(KEY_THUMBNAIL_QUALITY, json.optString("thumbnailQuality", "medium"))
        if (json.has("slideshowLoop")) editor.putBoolean(KEY_SLIDESHOW_LOOP, json.optBoolean("slideshowLoop", true))
        if (json.has("highContrastBorders")) editor.putBoolean(KEY_HIGH_CONTRAST, json.optBoolean("highContrastBorders", true))
        if (json.has("imageFitMode")) editor.putString(KEY_IMAGE_FIT_MODE, json.optString("imageFitMode", "fit"))
        if (json.has("autoLockTimeoutMinutes")) editor.putInt(KEY_AUTO_LOCK_TIMEOUT, json.optInt("autoLockTimeoutMinutes", 0))
        if (json.has("showFileInfoOverlay")) editor.putBoolean(KEY_SHOW_FILE_INFO, json.optBoolean("showFileInfoOverlay", true))
        if (json.has("hapticFeedback")) editor.putBoolean(KEY_HAPTIC_FEEDBACK, json.optBoolean("hapticFeedback", true))
        if (json.has("cleanExVaultOnLock")) editor.putBoolean(KEY_CLEAN_EX_ON_LOCK, json.optBoolean("cleanExVaultOnLock", true))
        if (json.has("slideshowTransition")) editor.putString(KEY_SLIDESHOW_TRANSITION, json.optString("slideshowTransition", "fade"))
        if (json.has("recentAppPrivacy")) editor.putBoolean(KEY_RECENT_APP_PRIVACY, json.optBoolean("recentAppPrivacy", true))
        if (json.has("facebookConnected")) editor.putBoolean(KEY_FB_CONNECTED, json.optBoolean("facebookConnected", false))
        if (json.has("facebookUserName")) editor.putString(KEY_FB_USERNAME, json.optString("facebookUserName", ""))
        if (json.has("appDownloadUrl")) editor.putString(KEY_APP_DOWNLOAD_URL, json.optString("appDownloadUrl", ""))
        if (json.has("faceLockEnabled")) editor.putBoolean(KEY_FACE_LOCK_ENABLED, json.optBoolean("faceLockEnabled", false))
        if (json.has("shakeToLockEnabled")) editor.putBoolean(KEY_SHAKE_TO_LOCK, json.optBoolean("shakeToLockEnabled", false))
        if (json.has("shakeToLockDurationSeconds")) editor.putFloat(KEY_SHAKE_DURATION, json.optDouble("shakeToLockDurationSeconds", 1.5).toFloat())
        if (json.has("panicCustomImageUri")) editor.putString(KEY_PANIC_CUSTOM_IMAGE, json.optString("panicCustomImageUri", ""))
        if (json.has("dualVaultSlideshowIntervalSeconds")) editor.putInt(KEY_DUAL_VAULT_SLIDESHOW_INTERVAL, json.optInt("dualVaultSlideshowIntervalSeconds", 3))
        if (json.has("launcherDisguiseName")) editor.putString(KEY_LAUNCHER_DISGUISE, json.optString("launcherDisguiseName", "Excel"))
        if (json.has("launcherCustomIconUri")) editor.putString(KEY_LAUNCHER_CUSTOM_ICON_URI, json.optString("launcherCustomIconUri", ""))
        if (json.has("sraSlideshowIntervalSeconds")) editor.putFloat(KEY_SRA_INTERVAL, json.optDouble("sraSlideshowIntervalSeconds", 2.0).toFloat())
        if (json.has("sraAutoLoop")) editor.putBoolean(KEY_SRA_AUTO_LOOP, json.optBoolean("sraAutoLoop", true))
        if (json.has("sraShowNameBadge")) editor.putBoolean(KEY_SRA_SHOW_NAME_BADGE, json.optBoolean("sraShowNameBadge", true))
        if (json.has("sraPauseOnTap")) editor.putBoolean(KEY_SRA_PAUSE_ON_TAP, json.optBoolean("sraPauseOnTap", true))
        if (json.has("sraTransition")) editor.putString(KEY_SRA_TRANSITION, json.optString("sraTransition", "fade"))
        if (json.has("ssiSlideshowIntervalSeconds")) editor.putFloat(KEY_SSI_INTERVAL, json.optDouble("ssiSlideshowIntervalSeconds", 2.0).toFloat())
        if (json.has("ssiAutoLoop")) editor.putBoolean(KEY_SSI_AUTO_LOOP, json.optBoolean("ssiAutoLoop", true))
        if (json.has("autoZipMediaOnLock")) editor.putBoolean(KEY_AUTO_ZIP_MEDIA, json.optBoolean("autoZipMediaOnLock", false))
        if (json.has("deleteRawMediaAfterZip")) editor.putBoolean(KEY_DELETE_RAW_MEDIA_AFTER_ZIP, json.optBoolean("deleteRawMediaAfterZip", false))
        if (json.has("rnameSequencePrefix")) editor.putString(KEY_RNAME_PREFIX, json.optString("rnameSequencePrefix", "VHot"))
        if (json.has("rnamePaddingDigits")) editor.putInt(KEY_RNAME_PADDING, json.optInt("rnamePaddingDigits", 4))
        if (json.has("rnameStartNumber")) editor.putInt(KEY_RNAME_START_NUM, json.optInt("rnameStartNumber", 1))
        if (json.has("rnameConfirmBeforeRename")) editor.putBoolean(KEY_RNAME_CONFIRM, json.optBoolean("rnameConfirmBeforeRename", true))
        if (json.has("customAccentColor")) editor.putString(KEY_CUSTOM_ACCENT_COLOR, json.optString("customAccentColor", "DEFAULT"))
        if (json.has("galleryGridDensity")) editor.putString(KEY_GRID_DENSITY, json.optString("galleryGridDensity", "STANDARD"))
        if (json.has("uiAnimationSpeed")) editor.putString(KEY_UI_ANIM_SPEED, json.optString("uiAnimationSpeed", "NORMAL"))
        if (json.has("defaultStartTab")) editor.putString(KEY_DEFAULT_START_TAB, json.optString("defaultStartTab", "EXCEL"))
        if (json.has("highQualityRendering")) editor.putBoolean(KEY_HQ_RENDERING, json.optBoolean("highQualityRendering", true))
        if (json.has("showStorageStatsHeader")) editor.putBoolean(KEY_SHOW_STORAGE_HEADER, json.optBoolean("showStorageStatsHeader", true))
        if (json.has("flipToPanicEnabled")) editor.putBoolean(KEY_FLIP_TO_PANIC, json.optBoolean("flipToPanicEnabled", true))
        if (json.has("volumeButtonPanicEnabled")) editor.putBoolean(KEY_VOLUME_BUTTON_PANIC, json.optBoolean("volumeButtonPanicEnabled", true))
        if (json.has("launcherDisguisePreset")) editor.putString(KEY_LAUNCHER_PRESET, json.optString("launcherDisguisePreset", "EXCEL"))
        if (json.has("ssiStreamQuality")) editor.putString(KEY_SSI_STREAM_QUALITY, json.optString("ssiStreamQuality", "ULTRA_FAST"))

        editor.apply()
        loadSettings()
    }

    /**
     * Restores settings from key-value pairs parsed from fingerprint.dat lines.
     */
    fun restoreSettingsFromKeyValues(props: Map<String, String>) {
        val p = prefs ?: return
        val editor = p.edit()

        props["APP_THEME"]?.let {
            try { editor.putString(KEY_THEME, AppTheme.valueOf(it).name) } catch (_: Throwable) {}
        }
        props["LOW_RAM_MODE"]?.let { editor.putBoolean(KEY_LOW_RAM, it.toBoolean()) }
        props["DIRECT_FOLDER_ACCESS"]?.let { editor.putBoolean(KEY_DIRECT_FOLDER, it.toBoolean()) }
        props["GRID_COLUMNS"]?.toIntOrNull()?.let { editor.putInt(KEY_GRID_COLUMNS, it) }
        props["SLIDESHOW_SPEED_MS"]?.toLongOrNull()?.let { editor.putLong(KEY_SLIDESHOW_SPEED, it) }
        props["FAST_FORWARD_SPEED_MS"]?.toLongOrNull()?.let { editor.putLong(KEY_FAST_FORWARD_SPEED, it) }
        props["AUTO_SYNC_EX_VAULT"]?.let { editor.putBoolean(KEY_AUTO_SYNC_EX, it.toBoolean()) }
        props["KEEP_VAULT_OPEN"]?.let { editor.putBoolean(KEY_KEEP_VAULT_OPEN, it.toBoolean()) }
        props["USE_INTERNAL_STORAGE_RAM"]?.let { editor.putBoolean(KEY_INTERNAL_STORAGE_RAM, it.toBoolean()) }
        props["DEDICATED_MEDIA_FOLDER"]?.let { editor.putBoolean(KEY_DEDICATED_FOLDER, it.toBoolean()) }
        props["THUMBNAIL_QUALITY"]?.let { editor.putString(KEY_THUMBNAIL_QUALITY, it) }
        props["SLIDESHOW_LOOP"]?.let { editor.putBoolean(KEY_SLIDESHOW_LOOP, it.toBoolean()) }
        props["HIGH_CONTRAST_BORDERS"]?.let { editor.putBoolean(KEY_HIGH_CONTRAST, it.toBoolean()) }
        props["IMAGE_FIT_MODE"]?.let { editor.putString(KEY_IMAGE_FIT_MODE, it) }
        props["AUTO_LOCK_TIMEOUT_MINUTES"]?.toIntOrNull()?.let { editor.putInt(KEY_AUTO_LOCK_TIMEOUT, it) }
        props["SHOW_FILE_INFO_OVERLAY"]?.let { editor.putBoolean(KEY_SHOW_FILE_INFO, it.toBoolean()) }
        props["HAPTIC_FEEDBACK"]?.let { editor.putBoolean(KEY_HAPTIC_FEEDBACK, it.toBoolean()) }
        props["CLEAN_EX_ON_LOCK"]?.let { editor.putBoolean(KEY_CLEAN_EX_ON_LOCK, it.toBoolean()) }
        props["SLIDESHOW_TRANSITION"]?.let { editor.putString(KEY_SLIDESHOW_TRANSITION, it) }
        props["RECENT_APP_PRIVACY"]?.let { editor.putBoolean(KEY_RECENT_APP_PRIVACY, it.toBoolean()) }
        props["FB_CONNECTED"]?.let { editor.putBoolean(KEY_FB_CONNECTED, it.toBoolean()) }
        props["FB_USERNAME"]?.let { editor.putString(KEY_FB_USERNAME, it) }
        props["APP_DOWNLOAD_URL"]?.let { editor.putString(KEY_APP_DOWNLOAD_URL, it) }
        props["FACE_LOCK_ENABLED"]?.let { editor.putBoolean(KEY_FACE_LOCK_ENABLED, it.toBoolean()) }
        props["SHAKE_TO_LOCK_ENABLED"]?.let { editor.putBoolean(KEY_SHAKE_TO_LOCK, it.toBoolean()) }
        props["SHAKE_TO_LOCK_DURATION"]?.toFloatOrNull()?.let { editor.putFloat(KEY_SHAKE_DURATION, it) }
        props["PANIC_CUSTOM_IMAGE_URI"]?.let { editor.putString(KEY_PANIC_CUSTOM_IMAGE, it) }
        props["DUAL_VAULT_INTERVAL"]?.toIntOrNull()?.let { editor.putInt(KEY_DUAL_VAULT_SLIDESHOW_INTERVAL, it) }
        props["LAUNCHER_DISGUISE_NAME"]?.let { editor.putString(KEY_LAUNCHER_DISGUISE, it) }
        props["LAUNCHER_CUSTOM_ICON_URI"]?.let { editor.putString(KEY_LAUNCHER_CUSTOM_ICON_URI, it) }
        props["SRA_INTERVAL"]?.toFloatOrNull()?.let { editor.putFloat(KEY_SRA_INTERVAL, it) }
        props["SRA_AUTO_LOOP"]?.let { editor.putBoolean(KEY_SRA_AUTO_LOOP, it.toBoolean()) }
        props["SRA_SHOW_NAME_BADGE"]?.let { editor.putBoolean(KEY_SRA_SHOW_NAME_BADGE, it.toBoolean()) }
        props["SRA_PAUSE_ON_TAP"]?.let { editor.putBoolean(KEY_SRA_PAUSE_ON_TAP, it.toBoolean()) }
        props["SRA_TRANSITION"]?.let { editor.putString(KEY_SRA_TRANSITION, it) }
        props["SSI_INTERVAL"]?.toFloatOrNull()?.let { editor.putFloat(KEY_SSI_INTERVAL, it) }
        props["SSI_AUTO_LOOP"]?.let { editor.putBoolean(KEY_SSI_AUTO_LOOP, it.toBoolean()) }
        props["AUTO_ZIP_MEDIA_ON_LOCK"]?.let { editor.putBoolean(KEY_AUTO_ZIP_MEDIA, it.toBoolean()) }
        props["DELETE_RAW_MEDIA_AFTER_ZIP"]?.let { editor.putBoolean(KEY_DELETE_RAW_MEDIA_AFTER_ZIP, it.toBoolean()) }
        props["RNAME_PREFIX"]?.let { editor.putString(KEY_RNAME_PREFIX, it) }
        props["RNAME_PADDING"]?.toIntOrNull()?.let { editor.putInt(KEY_RNAME_PADDING, it) }
        props["RNAME_START_NUM"]?.toIntOrNull()?.let { editor.putInt(KEY_RNAME_START_NUM, it) }
        props["RNAME_CONFIRM"]?.let { editor.putBoolean(KEY_RNAME_CONFIRM, it.toBoolean()) }
        props["CUSTOM_ACCENT_COLOR"]?.let { editor.putString(KEY_CUSTOM_ACCENT_COLOR, it) }
        props["GALLERY_GRID_DENSITY"]?.let { editor.putString(KEY_GRID_DENSITY, it) }
        props["UI_ANIMATION_SPEED"]?.let { editor.putString(KEY_UI_ANIM_SPEED, it) }
        props["DEFAULT_START_TAB"]?.let { editor.putString(KEY_DEFAULT_START_TAB, it) }
        props["HIGH_QUALITY_RENDERING"]?.let { editor.putBoolean(KEY_HQ_RENDERING, it.toBoolean()) }
        props["SHOW_STORAGE_HEADER"]?.let { editor.putBoolean(KEY_SHOW_STORAGE_HEADER, it.toBoolean()) }
        props["FLIP_TO_PANIC_ENABLED"]?.let { editor.putBoolean(KEY_FLIP_TO_PANIC, it.toBoolean()) }
        props["VOLUME_BUTTON_PANIC_ENABLED"]?.let { editor.putBoolean(KEY_VOLUME_BUTTON_PANIC, it.toBoolean()) }
        props["LAUNCHER_DISGUISE_PRESET"]?.let { editor.putString(KEY_LAUNCHER_PRESET, it) }
        props["SSI_STREAM_QUALITY"]?.let { editor.putString(KEY_SSI_STREAM_QUALITY, it) }

        editor.apply()
        loadSettings()
    }
}
