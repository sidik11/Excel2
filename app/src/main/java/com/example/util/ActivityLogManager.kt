package com.example.util

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class ActivityLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val formattedTime: String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp)),
    val type: String, // UNLOCK_PIN, UNLOCK_BIO, UNLOCK_FACE, UNLOCK_FAIL, LOCK_MANUAL, LOCK_TIMEOUT, LOCK_PANIC, DUAL_VAULT_CONNECT, DUAL_VAULT_DISCONNECT, DUAL_VAULT_UPLOAD, DUAL_VAULT_DELETE, FILE_MANAGER_ACCESS, SETTING_CHANGE
    val title: String,
    val description: String,
    val details: String = "",
    val severity: String = "INFO" // INFO, SUCCESS, WARNING, DANGER
)

object ActivityLogManager {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val _logs = MutableStateFlow<List<ActivityLogEntry>>(emptyList())
    val logs: StateFlow<List<ActivityLogEntry>> = _logs.asStateFlow()

    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true
        scope.launch {
            loadLogs(context)
        }
    }

    private fun getActivityFile(context: Context): File {
        val dedicatedDir = AppStorageHelper.getDedicatedMediaDir(context)
        val activityDir = File(dedicatedDir, "activity")
        if (!activityDir.exists()) {
            activityDir.mkdirs()
        }
        return File(activityDir, "activity.json")
    }

    private fun getFallbackFile(context: Context): File {
        val fallbackDir = File(context.filesDir, "activity")
        if (!fallbackDir.exists()) {
            fallbackDir.mkdirs()
        }
        return File(fallbackDir, "activity.json")
    }

    private fun loadLogs(context: Context) {
        try {
            val file = getActivityFile(context)
            val fallback = getFallbackFile(context)
            val targetFile = if (file.exists() && file.length() > 0) file else fallback

            if (!targetFile.exists() || targetFile.length() == 0L) {
                _logs.value = emptyList()
                return
            }

            val content = targetFile.readText(Charsets.UTF_8)
            val array = JSONArray(content)
            val list = mutableListOf<ActivityLogEntry>()

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    ActivityLogEntry(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        formattedTime = obj.optString("formattedTime", ""),
                        type = obj.optString("type", "INFO"),
                        title = obj.optString("title", "Event"),
                        description = obj.optString("description", ""),
                        details = obj.optString("details", ""),
                        severity = obj.optString("severity", "INFO")
                    )
                )
            }
            _logs.value = list.sortedByDescending { it.timestamp }
        } catch (_: Throwable) {
            _logs.value = emptyList()
        }
    }

    fun log(
        context: Context,
        type: String,
        title: String,
        description: String,
        details: String = "",
        severity: String = "INFO"
    ) {
        val entry = ActivityLogEntry(
            type = type,
            title = title,
            description = description,
            details = details,
            severity = severity
        )

        scope.launch {
            try {
                val current = _logs.value.toMutableList()
                current.add(0, entry)
                // Limit to recent 2000 log entries
                val trimmed = if (current.size > 2000) current.take(2000) else current
                _logs.value = trimmed

                val array = JSONArray()
                trimmed.forEach { item ->
                    val obj = JSONObject().apply {
                        put("id", item.id)
                        put("timestamp", item.timestamp)
                        put("formattedTime", item.formattedTime)
                        put("type", item.type)
                        put("title", item.title)
                        put("description", item.description)
                        put("details", item.details)
                        put("severity", item.severity)
                    }
                    array.put(obj)
                }

                val jsonString = array.toString(2)
                try {
                    val file = getActivityFile(context)
                    file.writeText(jsonString, Charsets.UTF_8)
                } catch (_: Throwable) {}

                try {
                    val fallback = getFallbackFile(context)
                    fallback.writeText(jsonString, Charsets.UTF_8)
                } catch (_: Throwable) {}
            } catch (_: Throwable) {}
        }
    }

    fun clearLogs(context: Context) {
        scope.launch {
            _logs.value = emptyList()
            try {
                val file = getActivityFile(context)
                if (file.exists()) file.delete()
                val fallback = getFallbackFile(context)
                if (fallback.exists()) fallback.delete()
            } catch (_: Throwable) {}
        }
    }
}
