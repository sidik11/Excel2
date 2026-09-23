package com.example.util

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri

data class VaultActiveSession(
    val isUnlocked: Boolean,
    val datUri: Uri?,
    val datFileName: String?,
    val pin: String?,
    val exVaultUri: Uri?,
    val exVaultPath: String?,
    val unlockTimeMillis: Long
)

object VaultSessionManager {

    private const val PREFS_NAME = "vault_session_prefs"
    private const val KEY_IS_UNLOCKED = "session_is_unlocked"
    private const val KEY_DAT_URI = "session_dat_uri"
    private const val KEY_DAT_NAME = "session_dat_name"
    private const val KEY_PIN = "session_pin"
    private const val KEY_EX_VAULT_URI = "session_ex_vault_uri"
    private const val KEY_EX_VAULT_PATH = "session_ex_vault_path"
    private const val KEY_TIMESTAMP = "session_timestamp"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveSession(
        context: Context,
        datUri: Uri?,
        datFileName: String?,
        pin: String,
        exVaultUri: Uri?,
        exVaultPath: String?
    ) {
        try {
            getPrefs(context).edit()
                .putBoolean(KEY_IS_UNLOCKED, true)
                .putString(KEY_DAT_URI, datUri?.toString())
                .putString(KEY_DAT_NAME, datFileName)
                .putString(KEY_PIN, pin)
                .putString(KEY_EX_VAULT_URI, exVaultUri?.toString())
                .putString(KEY_EX_VAULT_PATH, exVaultPath)
                .putLong(KEY_TIMESTAMP, System.currentTimeMillis())
                .apply()
        } catch (_: Throwable) {}
    }

    fun getSession(context: Context): VaultActiveSession {
        return try {
            val p = getPrefs(context)
            val isUnlocked = p.getBoolean(KEY_IS_UNLOCKED, false)
            val datUriStr = p.getString(KEY_DAT_URI, null)
            val datName = p.getString(KEY_DAT_NAME, null)
            val pin = p.getString(KEY_PIN, null)
            val exUriStr = p.getString(KEY_EX_VAULT_URI, null)
            val exPath = p.getString(KEY_EX_VAULT_PATH, null)
            val ts = p.getLong(KEY_TIMESTAMP, 0L)

            VaultActiveSession(
                isUnlocked = isUnlocked,
                datUri = datUriStr?.let { Uri.parse(it) },
                datFileName = datName,
                pin = pin,
                exVaultUri = exUriStr?.let { Uri.parse(it) },
                exVaultPath = exPath,
                unlockTimeMillis = ts
            )
        } catch (_: Throwable) {
            VaultActiveSession(false, null, null, null, null, null, 0L)
        }
    }

    fun clearSession(context: Context) {
        try {
            getPrefs(context).edit().clear().apply()
        } catch (_: Throwable) {}
    }
}
