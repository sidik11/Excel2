package com.example.util

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import javax.crypto.spec.SecretKeySpec

data class VaultActiveSession(
    val isUnlocked: Boolean,
    val vaultUri: Uri?,
    val vaultFilePath: String?,
    val vaultFileName: String?,
    val unlockTimeMillis: Long
)

object VaultSessionManager {

    private const val PREFS_NAME = "img_vault_session_prefs"
    private const val KEY_IS_UNLOCKED = "session_is_unlocked"
    private const val KEY_VAULT_URI = "session_vault_uri"
    private const val KEY_VAULT_PATH = "session_vault_path"
    private const val KEY_VAULT_NAME = "session_vault_name"
    private const val KEY_TIMESTAMP = "session_timestamp"

    // Transient in-memory master key for active session; never written to plaintext shared prefs
    @Volatile
    var activeMasterKey: SecretKeySpec? = null
        private set

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveSession(
        context: Context,
        vaultUri: Uri?,
        vaultFilePath: String?,
        vaultFileName: String?,
        masterKey: SecretKeySpec?
    ) {
        try {
            activeMasterKey = masterKey
            getPrefs(context).edit()
                .putBoolean(KEY_IS_UNLOCKED, true)
                .putString(KEY_VAULT_URI, vaultUri?.toString())
                .putString(KEY_VAULT_PATH, vaultFilePath)
                .putString(KEY_VAULT_NAME, vaultFileName)
                .putLong(KEY_TIMESTAMP, System.currentTimeMillis())
                .apply()
        } catch (_: Throwable) {}
    }

    fun getSession(context: Context): VaultActiveSession {
        return try {
            val p = getPrefs(context)
            val isUnlocked = p.getBoolean(KEY_IS_UNLOCKED, false)
            val uriStr = p.getString(KEY_VAULT_URI, null)
            val pathStr = p.getString(KEY_VAULT_PATH, null)
            val nameStr = p.getString(KEY_VAULT_NAME, null)
            val ts = p.getLong(KEY_TIMESTAMP, 0L)

            VaultActiveSession(
                isUnlocked = isUnlocked && activeMasterKey != null,
                vaultUri = uriStr?.let { Uri.parse(it) },
                vaultFilePath = pathStr,
                vaultFileName = nameStr,
                unlockTimeMillis = ts
            )
        } catch (_: Throwable) {
            VaultActiveSession(false, null, null, null, 0L)
        }
    }

    fun clearSession(context: Context) {
        try {
            activeMasterKey = null
            getPrefs(context).edit().clear().apply()
            ImgVaultEngine.clearDecryptedCache(context)
        } catch (_: Throwable) {}
    }
}
