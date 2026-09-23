package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.example.data.local.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit

data class DualVaultFileInfo(
    val fileName: String,
    val fileSizeBytes: Long,
    val addedBy: String,
    val addedTimestamp: Long,
    val localFile: File? = null
)

data class DualVaultSession(
    val code: String = "",
    val sessionId: String = "",
    val isHost: Boolean = false,
    val isConnected: Boolean = false,
    val status: String = "IDLE", // IDLE, WAITING, CONNECTED, DISCONNECTED
    val hostName: String = "",
    val peerName: String = "",
    val connectedAt: Long = 0L,
    val dualVaultFiles: List<DualVaultFileInfo> = emptyList(),
    val lastSyncTimestamp: Long = 0L,
    val isSyncing: Boolean = false
)

object FirebaseBridgeManager {

    private const val RTDB_BASE_URL = "https://imagefeed-45d0e-default-rtdb.firebaseio.com"
    private const val PREFS_NAME = "dual_vault_prefs"

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private val managerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _currentSession = MutableStateFlow(DualVaultSession())
    val currentSession: StateFlow<DualVaultSession> = _currentSession.asStateFlow()

    private var isPolling = false
    private var isLiveSyncing = false
    private var isInitialized = false

    /**
     * Initializes FirebaseBridgeManager and restores active connection if previously paired.
     * Connection remains persistent until a user explicitly taps Disconnect.
     */
    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isConnected = prefs.getBoolean("is_connected", false)
        if (isConnected) {
            val code = prefs.getString("code", "") ?: ""
            val sessionId = prefs.getString("session_id", "") ?: ""
            val isHost = prefs.getBoolean("is_host", false)
            val hostName = prefs.getString("host_name", "") ?: ""
            val peerName = prefs.getString("peer_name", "") ?: ""
            val connectedAt = prefs.getLong("connected_at", 0L)
            if (code.isNotBlank() && sessionId.isNotBlank()) {
                _currentSession.value = DualVaultSession(
                    code = code,
                    sessionId = sessionId,
                    isHost = isHost,
                    isConnected = true,
                    status = "CONNECTED",
                    hostName = hostName,
                    peerName = peerName,
                    connectedAt = connectedAt
                )
                refreshDualVaultFiles(context)
                startLiveSync(context, sessionId)
            }
        }
    }

    private fun persistSession(context: Context, session: DualVaultSession) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("is_connected", session.isConnected)
            .putString("code", session.code)
            .putString("session_id", session.sessionId)
            .putBoolean("is_host", session.isHost)
            .putString("host_name", session.hostName)
            .putString("peer_name", session.peerName)
            .putLong("connected_at", session.connectedAt)
            .apply()
    }

    private fun clearPersistedSession(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }

    /**
     * Generates a 10-character code matching the requested format:
     * - First 5 letters of Gmail ID
     * - Last 2 digits of phone number
     * - 3 unique code characters
     */
    fun generate10DigitCode(profile: UserProfile? = ProfileManager.userProfile.value): String {
        if (profile != null && profile.deviceCode.length == 10) {
            return profile.deviceCode
        }
        val emailToUse = profile?.googleEmail?.ifBlank { profile.emailId } ?: "guest"
        val emailPrefix = (emailToUse.substringBefore("@").filter { it.isLetterOrDigit() }.lowercase() + "abcde").take(5)
        val phoneDigits = (profile?.phoneNumber ?: "").filter { it.isDigit() }
        val last2Phone = if (phoneDigits.length >= 2) phoneDigits.takeLast(2) else "00"
        val unique3 = ((100..999).random()).toString()
        return "$emailPrefix$last2Phone$unique3"
    }

    /**
     * Host creates a 10-character pairing room on Firebase RTDB.
     * Enforces single active pairing: cannot create another if already connected.
     */
    suspend fun createPairingRoom(context: Context): Result<String> = withContext(Dispatchers.IO) {
        if (_currentSession.value.isConnected) {
            return@withContext Result.failure(Exception("Dual pairing is already connected. Please disconnect your current pairing before creating a new one."))
        }

        try {
            val code = generate10DigitCode()
            val sessionId = "dv_" + UUID.randomUUID().toString().replace("-", "").take(12)
            val profile = ProfileManager.userProfile.value
            val hostName = profile?.fullName?.takeIf { it.isNotBlank() } ?: "User_${code.takeLast(4)}"
            val deviceId = getDeviceId(context)

            val payload = JSONObject().apply {
                put("code", code)
                put("sessionId", sessionId)
                put("status", "WAITING")
                put("hostId", deviceId)
                put("hostName", hostName)
                put("createdAt", System.currentTimeMillis())
            }

            val url = "$RTDB_BASE_URL/dual_vault_bridge/$code.json"
            val body = payload.toString().toRequestBody(jsonMediaType)
            val request = Request.Builder().url(url).put(body).build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Failed to register code on bridge: HTTP ${response.code}"))
            }

            _currentSession.value = DualVaultSession(
                code = code,
                sessionId = sessionId,
                isHost = true,
                isConnected = false,
                status = "WAITING",
                hostName = hostName
            )

            // Start polling for peer to join
            startPollingForConnection(context, code, sessionId, isHost = true)

            Result.success(code)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    /**
     * Peer joins an existing 10-character pairing room on Firebase RTDB.
     * Enforces mutual exclusivity: cannot connect if already connected,
     * and rejects joining if room already has 2 devices connected.
     */
    suspend fun joinPairingRoom(context: Context, enteredCode: String): Result<DualVaultSession> = withContext(Dispatchers.IO) {
        if (_currentSession.value.isConnected) {
            return@withContext Result.failure(Exception("Dual pairing is already connected. Please disconnect your current pairing before joining another."))
        }

        val trimmedCode = enteredCode.trim()
        if (trimmedCode.length != 10) {
            return@withContext Result.failure(Exception("Code must be exactly 10 characters."))
        }

        try {
            val getUrl = "$RTDB_BASE_URL/dual_vault_bridge/$trimmedCode.json"
            val getRequest = Request.Builder().url(getUrl).get().build()
            val getResponse = httpClient.newCall(getRequest).execute()
            val responseBody = getResponse.body?.string()

            if (!getResponse.isSuccessful || responseBody == null || responseBody == "null") {
                return@withContext Result.failure(Exception("Invalid or expired 10-character code."))
            }

            val roomJson = JSONObject(responseBody)
            val currentStatus = roomJson.optString("status", "")
            if (currentStatus == "DISCONNECTED") {
                return@withContext Result.failure(Exception("This pairing room has already closed."))
            }
            if (currentStatus == "CONNECTED" || roomJson.optString("peerId", "").isNotBlank()) {
                return@withContext Result.failure(Exception("This pairing room already has 2 devices connected. Only one pairing is allowed at a time."))
            }

            val sessionId = roomJson.optString("sessionId", "dv_session")
            val hostName = roomJson.optString("hostName", "Peer User")
            val profile = ProfileManager.userProfile.value
            val peerName = profile?.fullName?.takeIf { it.isNotBlank() } ?: "Peer_${trimmedCode.takeLast(4)}"
            val deviceId = getDeviceId(context)

            // Update status to CONNECTED via PATCH
            val updateJson = JSONObject().apply {
                put("status", "CONNECTED")
                put("peerId", deviceId)
                put("peerName", peerName)
                put("connectedAt", System.currentTimeMillis())
            }

            val patchRequest = Request.Builder()
                .url(getUrl)
                .patch(updateJson.toString().toRequestBody(jsonMediaType))
                .build()

            val patchResponse = httpClient.newCall(patchRequest).execute()
            if (!patchResponse.isSuccessful) {
                return@withContext Result.failure(Exception("Failed to join room: HTTP ${patchResponse.code}"))
            }

            val session = DualVaultSession(
                code = trimmedCode,
                sessionId = sessionId,
                isHost = false,
                isConnected = true,
                status = "CONNECTED",
                hostName = hostName,
                peerName = peerName,
                connectedAt = System.currentTimeMillis()
            )
            _currentSession.value = session
            persistSession(context, session)

            // Refresh dual vault local storage
            refreshDualVaultFiles(context)

            // Start live 10-second auto-synchronization loop
            startLiveSync(context, sessionId)

            Result.success(session)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    /**
     * Polls Firebase bridge to detect when peer connects.
     */
    private fun startPollingForConnection(context: Context, code: String, sessionId: String, isHost: Boolean) {
        if (isPolling) return
        isPolling = true

        managerScope.launch {
            var attempts = 0
            while (isPolling && attempts < 150) { // Poll for up to 5 minutes
                delay(2000L)
                attempts++
                try {
                    val url = "$RTDB_BASE_URL/dual_vault_bridge/$code.json"
                    val request = Request.Builder().url(url).get().build()
                    val response = httpClient.newCall(request).execute()
                    val body = response.body?.string()
                    if (response.isSuccessful && body != null && body != "null") {
                        val json = JSONObject(body)
                        val status = json.optString("status")
                        if (status == "CONNECTED") {
                            val hostName = json.optString("hostName", "Host User")
                            val peerName = json.optString("peerName", "Peer User")
                            val connectedSession = _currentSession.value.copy(
                                isConnected = true,
                                status = "CONNECTED",
                                hostName = hostName,
                                peerName = peerName,
                                connectedAt = json.optLong("connectedAt", System.currentTimeMillis())
                            )
                            _currentSession.value = connectedSession
                            persistSession(context, connectedSession)
                            isPolling = false
                            // Peer joined! Start live background image syncing (every 10s)
                            startLiveSync(context, sessionId)
                            break
                        } else if (status == "DISCONNECTED") {
                            _currentSession.value = _currentSession.value.copy(
                                isConnected = false,
                                status = "DISCONNECTED"
                            )
                            clearPersistedSession(context)
                            isPolling = false
                            break
                        }
                    }
                } catch (_: Throwable) {}
            }
            isPolling = false
        }
    }

    /**
     * Continuously syncs images across the Firebase Bridge between connected peers.
     * Runs every 10 seconds.
     * - Auto-checks if peer disconnected (mutual disconnect)
     * - Auto-syncs image deletions (if one user deletes, it auto-deletes on the other device)
     * - Auto-downloads newly shared photos from peer
     */
    fun startLiveSync(context: Context, sessionId: String) {
        if (isLiveSyncing || sessionId.isBlank()) return
        isLiveSyncing = true

        managerScope.launch {
            val deviceId = getDeviceId(context)
            val dualDir = AppStorageHelper.getDualVaultDir(context)

            // Initial refresh of local storage
            refreshDualVaultFiles(context)

            while (isLiveSyncing && _currentSession.value.isConnected) {
                var filesChanged = false
                val code = _currentSession.value.code

                try {
                    // 1. Check if peer initiated Disconnect (Mutual Disconnect)
                    if (code.isNotBlank()) {
                        val checkUrl = "$RTDB_BASE_URL/dual_vault_bridge/$code.json"
                        val checkReq = Request.Builder().url(checkUrl).get().build()
                        val checkResp = httpClient.newCall(checkReq).execute()
                        val checkBody = checkResp.body?.string()
                        if (checkResp.isSuccessful && checkBody != null && checkBody != "null") {
                            val statusJson = JSONObject(checkBody)
                            if (statusJson.optString("status") == "DISCONNECTED") {
                                clearPersistedSession(context)
                                _currentSession.value = DualVaultSession(status = "DISCONNECTED")
                                isLiveSyncing = false
                                break
                            }
                        }
                    }

                    // 2. Auto-sync deletions: fetch deleted images tombstones
                    val deletedUrl = "$RTDB_BASE_URL/dual_vault_sessions/$sessionId/deleted_images.json"
                    val delReq = Request.Builder().url(deletedUrl).get().build()
                    val delResp = httpClient.newCall(delReq).execute()
                    val delBody = delResp.body?.string()
                    val deletedFileNames = mutableSetOf<String>()

                    if (delResp.isSuccessful && delBody != null && delBody != "null") {
                        val delJson = JSONObject(delBody)
                        val delKeys = delJson.keys()
                        while (delKeys.hasNext()) {
                            val key = delKeys.next()
                            val item = delJson.optJSONObject(key) ?: continue
                            val fileName = item.optString("fileName", "")
                            if (fileName.isNotBlank()) {
                                deletedFileNames.add(fileName)
                                val localFile = File(dualDir, fileName)
                                if (localFile.exists()) {
                                    localFile.delete()
                                    filesChanged = true
                                }
                            }
                        }
                    }

                    // 3. Auto-sync new images: fetch shared images from peer
                    val url = "$RTDB_BASE_URL/dual_vault_sessions/$sessionId/shared_images.json"
                    val req = Request.Builder().url(url).get().build()
                    val resp = httpClient.newCall(req).execute()
                    val body = resp.body?.string()

                    if (resp.isSuccessful && body != null && body != "null") {
                        val rootJson = JSONObject(body)
                        val keys = rootJson.keys()

                        while (keys.hasNext()) {
                            val key = keys.next()
                            val item = rootJson.optJSONObject(key) ?: continue
                            val senderId = item.optString("senderId", "")
                            val fileName = item.optString("fileName", "")
                            val base64Data = item.optString("base64Data", "")

                            // If this file was marked deleted, remove it if it exists and skip
                            if (deletedFileNames.contains(fileName)) {
                                val target = File(dualDir, fileName)
                                if (target.exists()) {
                                    target.delete()
                                    filesChanged = true
                                }
                                continue
                            }

                            // Only process images uploaded by peer that we don't have yet
                            if (senderId != deviceId && fileName.isNotBlank() && base64Data.isNotBlank()) {
                                val targetFile = File(dualDir, fileName)
                                if (!targetFile.exists() || targetFile.length() == 0L) {
                                    try {
                                        val bytes = Base64.decode(base64Data, Base64.DEFAULT)
                                        targetFile.writeBytes(bytes)
                                        filesChanged = true
                                    } catch (_: Throwable) {}
                                }
                            }
                        }
                    }

                    if (filesChanged) {
                        refreshDualVaultFiles(context)
                    }

                    _currentSession.value = _currentSession.value.copy(
                        lastSyncTimestamp = System.currentTimeMillis()
                    )
                } catch (_: Throwable) {}

                // Sync interval: 10 seconds exactly
                delay(10_000L)
            }
            isLiveSyncing = false
        }
    }

    /**
     * Deletes an image from the Dual Vault storage AND notifies the Firebase bridge
     * so that the connected peer automatically deletes it too (auto-sync delete).
     */
    suspend fun deleteImageFromDualVault(context: Context, fileName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val dualDir = AppStorageHelper.getDualVaultDir(context)
            val file = File(dualDir, fileName)
            if (file.exists()) {
                file.delete()
            }

            val session = _currentSession.value
            val deviceId = getDeviceId(context)

            if (session.sessionId.isNotBlank()) {
                // Post tombstone to deleted_images in RTDB
                val deleteKey = fileName.replace(".", "_").replace("#", "_").replace("$", "_").replace("[", "_").replace("]", "_")
                val tombstonePayload = JSONObject().apply {
                    put("fileName", fileName)
                    put("deletedBy", deviceId)
                    put("timestamp", System.currentTimeMillis())
                }
                val url = "$RTDB_BASE_URL/dual_vault_sessions/${session.sessionId}/deleted_images/$deleteKey.json"
                val body = tombstonePayload.toString().toRequestBody(jsonMediaType)
                val req = Request.Builder().url(url).put(body).build()
                try {
                    httpClient.newCall(req).execute()
                } catch (_: Throwable) {}
            }

            refreshDualVaultFiles(context)
            _currentSession.value = _currentSession.value.copy(lastSyncTimestamp = System.currentTimeMillis())
            true
        } catch (e: Throwable) {
            false
        }
    }

    /**
     * Manual sync trigger to instantly fetch any updates from Firebase bridge.
     */
    suspend fun syncNow(context: Context): Boolean = withContext(Dispatchers.IO) {
        val session = _currentSession.value
        if (!session.isConnected || session.sessionId.isBlank()) return@withContext false

        _currentSession.value = _currentSession.value.copy(isSyncing = true)
        var syncSuccess = false

        try {
            val deviceId = getDeviceId(context)
            val dualDir = AppStorageHelper.getDualVaultDir(context)
            val url = "$RTDB_BASE_URL/dual_vault_sessions/${session.sessionId}/shared_images.json"
            val req = Request.Builder().url(url).get().build()
            val resp = httpClient.newCall(req).execute()
            val body = resp.body?.string()

            if (resp.isSuccessful && body != null && body != "null") {
                val rootJson = JSONObject(body)
                var newImages = false
                val keys = rootJson.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val item = rootJson.optJSONObject(key) ?: continue
                    val senderId = item.optString("senderId", "")
                    val fileName = item.optString("fileName", "")
                    val base64Data = item.optString("base64Data", "")

                    if (senderId != deviceId && fileName.isNotBlank() && base64Data.isNotBlank()) {
                        val targetFile = File(dualDir, fileName)
                        if (!targetFile.exists() || targetFile.length() == 0L) {
                            try {
                                val bytes = Base64.decode(base64Data, Base64.DEFAULT)
                                targetFile.writeBytes(bytes)
                                newImages = true
                            } catch (_: Throwable) {}
                        }
                    }
                }
                if (newImages) {
                    refreshDualVaultFiles(context)
                }
                syncSuccess = true
            }
        } catch (_: Throwable) {}

        _currentSession.value = _currentSession.value.copy(
            isSyncing = false,
            lastSyncTimestamp = System.currentTimeMillis()
        )
        refreshDualVaultFiles(context)
        syncSuccess
    }

    /**
     * Refresh local files in Dual Vault media storage (Android/media/<package>/Dual_Vault).
     */
    fun refreshDualVaultFiles(context: Context) {
        try {
            val dualDir = AppStorageHelper.getDualVaultDir(context)
            val files = dualDir.listFiles()?.filter { it.isFile && it.length() > 0 }?.map { file ->
                DualVaultFileInfo(
                    fileName = file.name,
                    fileSizeBytes = file.length(),
                    addedBy = if (_currentSession.value.isHost) _currentSession.value.hostName else _currentSession.value.peerName,
                    addedTimestamp = file.lastModified(),
                    localFile = file
                )
            }?.sortedByDescending { it.addedTimestamp } ?: emptyList()

            _currentSession.value = _currentSession.value.copy(dualVaultFiles = files)
        } catch (_: Throwable) {}
    }

    /**
     * Adds an image to the Dual Combined Vault storage (Android/media/<package>/Dual_Vault)
     * AND automatically uploads it to Firebase Bridge so the connected peer receives it immediately!
     */
    suspend fun addImageToDualVault(context: Context, sourceUri: Uri, originalName: String? = null): Result<File> = withContext(Dispatchers.IO) {
        try {
            val dualDir = AppStorageHelper.getDualVaultDir(context)
            val safeName = (originalName ?: "dual_${System.currentTimeMillis()}_${(100..999).random()}.jpg").replace(" ", "_")
            val targetFile = File(dualDir, safeName)

            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext Result.failure(Exception("Cannot read source image."))

            val session = _currentSession.value
            val deviceId = getDeviceId(context)

            // If session is active and connected, push the image through Firebase Bridge to connected peer!
            if (session.sessionId.isNotBlank()) {
                val optimizedBytes = getOptimizedImageBytes(targetFile)
                val base64Data = Base64.encodeToString(optimizedBytes, Base64.NO_WRAP)
                val fileKey = "img_${System.currentTimeMillis()}_${(1000..9999).random()}"

                val bridgePayload = JSONObject().apply {
                    put("id", fileKey)
                    put("fileName", targetFile.name)
                    put("fileSizeBytes", targetFile.length())
                    put("senderId", deviceId)
                    put("senderName", if (session.isHost) session.hostName else session.peerName)
                    put("timestamp", System.currentTimeMillis())
                    put("base64Data", base64Data)
                }

                val postUrl = "$RTDB_BASE_URL/dual_vault_sessions/${session.sessionId}/shared_images/$fileKey.json"
                val body = bridgePayload.toString().toRequestBody(jsonMediaType)
                val req = Request.Builder().url(postUrl).put(body).build()

                try {
                    httpClient.newCall(req).execute()
                } catch (_: Throwable) {}
            }

            refreshDualVaultFiles(context)
            Result.success(targetFile)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    /**
     * Efficiently reads or compresses image bytes for fast and lightweight Firebase Bridge transfer.
     */
    private fun getOptimizedImageBytes(file: File): ByteArray {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)
            var sampleSize = 1
            val maxDim = maxOf(options.outWidth, options.outHeight)
            while (maxDim / sampleSize > 1400) {
                sampleSize *= 2
            }
            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
            if (bitmap != null) {
                val stream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 82, stream)
                stream.toByteArray()
            } else {
                file.readBytes()
            }
        } catch (_: Throwable) {
            file.readBytes()
        }
    }

    /**
     * Closes the connection and leaves the room.
     * Triggers mutual disconnect so both devices disconnect automatically.
     */
    suspend fun disconnect(context: Context) = withContext(Dispatchers.IO) {
        isPolling = false
        isLiveSyncing = false
        val session = _currentSession.value
        clearPersistedSession(context)
        if (session.code.isNotBlank()) {
            try {
                val url = "$RTDB_BASE_URL/dual_vault_bridge/${session.code}.json"
                val payload = JSONObject().apply {
                    put("status", "DISCONNECTED")
                    put("disconnectedAt", System.currentTimeMillis())
                }
                val req = Request.Builder().url(url).patch(payload.toString().toRequestBody(jsonMediaType)).build()
                httpClient.newCall(req).execute()
            } catch (_: Throwable) {}
        }
        if (session.sessionId.isNotBlank()) {
            try {
                val statusUrl = "$RTDB_BASE_URL/dual_vault_sessions/${session.sessionId}/status.json"
                val statusPayload = JSONObject().apply { put("status", "DISCONNECTED") }
                val req = Request.Builder().url(statusUrl).put(statusPayload.toString().toRequestBody(jsonMediaType)).build()
                httpClient.newCall(req).execute()
            } catch (_: Throwable) {}
        }
        _currentSession.value = DualVaultSession(status = "DISCONNECTED")
    }

    private fun getDeviceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var id = prefs.getString("device_uuid", null)
        if (id == null) {
            id = UUID.randomUUID().toString().take(8)
            prefs.edit().putString("device_uuid", id).apply()
        }
        return id
    }
}
