package com.example.data

enum class AppTheme(val displayName: String, val description: String) {
    CYBERPUNK_GREEN("Matrix Green", "Terminal black & green hacker aesthetic"),
    AMOLED_DARK("AMOLED Dark", "Pure deep black for OLED screens & battery saving"),
    CYBER_PURPLE("Cyber Neon", "Futuristic purple & neon cyan glow"),
    OCEAN_BLUE("Ocean Deep", "Rich navy blue & electric azure"),
    CRIMSON_NIGHT("Crimson Night", "Obsidian dark with bold crimson accents"),
    MODERN_LIGHT("Modern Light", "Clean, high-contrast light design")
}

data class AppSettings(
    val theme: AppTheme = AppTheme.CYBER_PURPLE,
    val isLowRamMode: Boolean = true,
    val isDirectFolderAccess: Boolean = true,
    val gridColumns: Int = 3, // 2, 3, 4, 5
    val slideshowSpeedMs: Long = 1500L,
    val fastForwardSpeedMs: Long = 300L,
    val autoSyncExVault: Boolean = true,
    val keepVaultUnlockedAcrossRestarts: Boolean = true,
    val useInternalStorageReplaceRam: Boolean = true,
    val dedicatedMediaFolderEnabled: Boolean = true,
    val thumbnailQuality: String = "medium", // "low", "medium", "original"
    val slideshowLoop: Boolean = true,
    val highContrastBorders: Boolean = true,
    val imageFitMode: String = "fit", // "fit", "crop"
    val autoLockTimeoutMinutes: Int = 0, // 0 = Never / Keep Unlocked, 5, 15, 30
    val showFileInfoOverlay: Boolean = true,
    val hapticFeedback: Boolean = true,
    val cleanExVaultOnLock: Boolean = true,
    val slideshowTransition: String = "fade", // "fade", "slide", "instant"
    val recentAppPrivacy: Boolean = true,
    val customAppLogoTimestamp: Long = 0L,
    val facebookConnected: Boolean = false,
    val facebookUserName: String = "",
    val appDownloadUrl: String = "https://ais-pre-jgxqbiezgnewblvh6iilnv-571171211889.asia-southeast1.run.app",
    val faceLockEnabled: Boolean = false,
    val shakeToLockEnabled: Boolean = false,
    val shakeToLockDurationSeconds: Float = 1.5f, // 0.5s to 5.0s
    val panicCustomImageUri: String = "",
    val dualVaultSlideshowIntervalSeconds: Int = 3, // 1, 2, 3, 4, 5 seconds
    val launcherDisguiseName: String = "Excel", // "Excel", "Calculator", "Notes", "Weather", "Clock", "Custom"
    val launcherCustomIconUri: String = "",
    // SRA (Slideshow Rearrange) Settings
    val sraSlideshowIntervalSeconds: Float = 2.0f, // 1.0s to 5.0s
    val sraAutoLoop: Boolean = true,
    val sraShowNameBadge: Boolean = true,
    val sraPauseOnTap: Boolean = true,
    val sraTransition: String = "fade", // "fade", "slide", "instant"
    // SSI (Saved Slideshow Images) Settings
    val ssiSlideshowIntervalSeconds: Float = 2.0f, // 1.0s to 5.0s
    val ssiAutoLoop: Boolean = true,
    // Rname (VHot Renamer) Settings
    val rnameSequencePrefix: String = "VHot",
    val rnamePaddingDigits: Int = 4, // 0001
    val rnameStartNumber: Int = 1,
    val rnameConfirmBeforeRename: Boolean = true,
    // App Customization Settings
    val customAccentColor: String = "DEFAULT", // "DEFAULT", "INDIGO", "EMERALD", "SKY", "ROSE", "AMBER", "CYAN", "VIOLET"
    val galleryGridDensity: String = "STANDARD", // "COMPACT", "STANDARD", "COZY"
    val uiAnimationSpeed: String = "NORMAL", // "FAST", "NORMAL", "RELAXED", "OFF"
    val defaultStartTab: String = "EXCEL", // "EXCEL", "VAULT", "SSHOW", "DUAL_VAULT", "SRA", "RNAME", "SETTINGS"
    val highQualityRendering: Boolean = true,
    val showStorageStatsHeader: Boolean = true
)
