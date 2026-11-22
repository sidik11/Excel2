package com.example.util

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.UUID

data class ApsTransferHistoryEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestampMs: Long = System.currentTimeMillis(),
    val direction: String, // "SENT" or "RECEIVED"
    val vaultType: String, // "VID_VAULT" or "IMG_VAULT"
    val transport: String, // "WIFI_LAN" or "BLUETOOTH"
    val status: String, // "COMPLETED", "FAILED", "REJECTED", "MISMATCH"
    val peerDeviceId: String,
    val peerProfileName: String,
    val peerDeviceModel: String = "",
    val peerAvatarBase64: String = "",
    val fileCount: Int = 0,
    val fileNames: List<String> = emptyList(),
    val totalBytes: Long = 0L,
    val durationMs: Long = 0L,
    val securityCode: String = "",
    val errorMessage: String = ""
)

/**
 * Persistent local storage for all APS Direct Vault-to-Vault transfer history (Sent & Received).
 * Stored inside the app's private Security directory using atomic write-and-rename.
 */
object ApsTransferHistoryManager {

    private const val FILE_NAME = "aps_transfer_history.json"
    private const val MAX_ENTRIES = 250

    private val lock = Any()
    private val _entries = MutableStateFlow<List<ApsTransferHistoryEntry>>(emptyList())
    val entries: StateFlow<List<ApsTransferHistoryEntry>> = _entries.asStateFlow()

    @Volatile
    private var loaded = false

    private fun getHistoryFile(context: Context): File {
        val dir = AppStorageHelper.getSecurityDir(context)
        if (!dir.exists()) dir.mkdirs()
        return File(dir, FILE_NAME)
    }

    fun load(context: Context): List<ApsTransferHistoryEntry> {
        synchronized(lock) {
            val file = getHistoryFile(context)
            if (!file.exists()) {
                loaded = true
                _entries.value = emptyList()
                return emptyList()
            }
            return try {
                val raw = file.readText(StandardCharsets.UTF_8)
                val arr = JSONArray(raw)
                val parsed = mutableListOf<ApsTransferHistoryEntry>()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val namesArr = obj.optJSONArray("fileNames")
                    val names = mutableListOf<String>()
                    if (namesArr != null) {
                        for (j in 0 until namesArr.length()) {
                            val n = namesArr.optString(j)
                            if (n.isNotBlank()) names.add(n)
                        }
                    }
                    parsed.add(
                        ApsTransferHistoryEntry(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            timestampMs = obj.optLong("timestampMs", 0L),
                            direction = obj.optString("direction", "SENT"),
                            vaultType = obj.optString("vaultType", "VID_VAULT"),
                            transport = obj.optString("transport", "WIFI_LAN"),
                            status = obj.optString("status", "COMPLETED"),
                            peerDeviceId = obj.optString("peerDeviceId", ""),
                            peerProfileName = obj.optString("peerProfileName", "Unknown Device"),
                            peerDeviceModel = obj.optString("peerDeviceModel", ""),
                            peerAvatarBase64 = obj.optString("peerAvatarBase64", ""),
                            fileCount = obj.optInt("fileCount", 0),
                            fileNames = names,
                            totalBytes = obj.optLong("totalBytes", 0L),
                            durationMs = obj.optLong("durationMs", 0L),
                            securityCode = obj.optString("securityCode", ""),
                            errorMessage = obj.optString("errorMessage", "")
                        )
                    )
                }
                val sorted = parsed.sortedByDescending { it.timestampMs }
                loaded = true
                _entries.value = sorted
                sorted
            } catch (_: Throwable) {
                loaded = true
                _entries.value = emptyList()
                emptyList()
            }
        }
    }

    fun record(context: Context, entry: ApsTransferHistoryEntry) {
        synchronized(lock) {
            if (!loaded) {
                load(context)
            }
            val safeAvatar = if (entry.peerAvatarBase64.length <= 24_000) entry.peerAvatarBase64 else ""
            val cleanEntry = entry.copy(
                peerAvatarBase64 = safeAvatar,
                fileNames = entry.fileNames.take(40)
            )
            val updated = (listOf(cleanEntry) + _entries.value).take(MAX_ENTRIES)
            _entries.value = updated
            saveLocked(context, updated)
        }
    }

    fun deleteEntry(context: Context, entryId: String) {
        synchronized(lock) {
            if (!loaded) load(context)
            val updated = _entries.value.filterNot { it.id == entryId }
            _entries.value = updated
            saveLocked(context, updated)
        }
    }

    fun clearAll(context: Context) {
        synchronized(lock) {
            _entries.value = emptyList()
            loaded = true
            try {
                val file = getHistoryFile(context)
                if (file.exists()) file.delete()
            } catch (_: Throwable) {}
        }
    }

    private fun saveLocked(context: Context, list: List<ApsTransferHistoryEntry>) {
        try {
            val arr = JSONArray()
            list.forEach { e ->
                val namesArr = JSONArray()
                e.fileNames.forEach { namesArr.put(it) }
                arr.put(
                    JSONObject()
                        .put("id", e.id)
                        .put("timestampMs", e.timestampMs)
                        .put("direction", e.direction)
                        .put("vaultType", e.vaultType)
                        .put("transport", e.transport)
                        .put("status", e.status)
                        .put("peerDeviceId", e.peerDeviceId)
                        .put("peerProfileName", e.peerProfileName)
                        .put("peerDeviceModel", e.peerDeviceModel)
                        .put("peerAvatarBase64", e.peerAvatarBase64)
                        .put("fileCount", e.fileCount)
                        .put("fileNames", namesArr)
                        .put("totalBytes", e.totalBytes)
                        .put("durationMs", e.durationMs)
                        .put("securityCode", e.securityCode)
                        .put("errorMessage", e.errorMessage)
                )
            }
            val target = getHistoryFile(context)
            val tmp = File(target.parentFile, "$FILE_NAME.tmp")
            tmp.writeText(arr.toString(), StandardCharsets.UTF_8)
            if (!tmp.renameTo(target)) {
                target.writeText(arr.toString(), StandardCharsets.UTF_8)
                tmp.delete()
            }
        } catch (_: Throwable) {}
    }
}
