package com.example.util

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.ByteArrayOutputStream
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.UUID

data class SsiHostInfo(
    val isRunning: Boolean = false,
    val localIp: String = "127.0.0.1",
    val port: Int = 8080,
    val pin: String = "",
    val activeClients: Int = 0
) {
    val fullUrl: String
        get() = "http://$localIp:$port"
}

object SsiHostServer {

    private val _hostState = MutableStateFlow(SsiHostInfo())
    val hostState: StateFlow<SsiHostInfo> = _hostState.asStateFlow()

    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var activeSessionToken: String = UUID.randomUUID().toString().replace("-", "")
    private var activePin: String = ""

    fun isRunning(): Boolean = _hostState.value.isRunning

    /**
     * Starts the embedded HTTP server on an available port with the specified 4-digit PIN.
     */
    fun start(context: Context, pin: String, requestedPort: Int = 8080): Boolean {
        if (_hostState.value.isRunning) {
            stop()
        }

        activePin = pin.trim()
        activeSessionToken = UUID.randomUUID().toString().replace("-", "")

        val ip = getLocalIpAddress(context)
        var port = requestedPort
        var socket: ServerSocket? = null

        // Try ports starting from requestedPort
        for (p in requestedPort..(requestedPort + 10)) {
            try {
                socket = ServerSocket(p)
                port = p
                break
            } catch (_: Throwable) {
                // Port busy, try next
            }
        }

        if (socket == null) {
            return false
        }

        serverSocket = socket
        _hostState.update {
            SsiHostInfo(
                isRunning = true,
                localIp = ip,
                port = port,
                pin = activePin,
                activeClients = 0
            )
        }

        serverJob = scope.launch {
            try {
                while (isActive && !socket.isClosed) {
                    val clientSocket = try {
                        socket.accept()
                    } catch (_: Throwable) {
                        break
                    }
                    launch {
                        handleClient(clientSocket)
                    }
                }
            } finally {
                stopInternal()
            }
        }

        return true
    }

    /**
     * Stops the embedded server and disconnects any active web clients.
     */
    fun stop() {
        stopInternal()
    }

    private fun stopInternal() {
        try {
            serverSocket?.close()
        } catch (_: Throwable) {}
        serverSocket = null

        serverJob?.cancel()
        serverJob = null

        activePin = ""
        activeSessionToken = ""

        _hostState.update {
            SsiHostInfo(
                isRunning = false,
                localIp = "127.0.0.1",
                port = 8080,
                pin = "",
                activeClients = 0
            )
        }
    }

    private fun handleClient(socket: Socket) {
        socket.use { s ->
            try {
                s.soTimeout = 15000
                val reader = BufferedReader(InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8))
                val output = s.getOutputStream()

                val requestLine = reader.readLine() ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2) return

                val method = parts[0].uppercase()
                val fullPath = parts[1]

                // Parse headers
                val headers = mutableMapOf<String, String>()
                var line: String? = reader.readLine()
                var contentLength = 0
                while (!line.isNullOrEmpty()) {
                    val colonIdx = line.indexOf(':')
                    if (colonIdx > 0) {
                        val key = line.substring(0, colonIdx).trim().lowercase()
                        val value = line.substring(colonIdx + 1).trim()
                        headers[key] = value
                        if (key == "content-length") {
                            contentLength = value.toIntOrNull() ?: 0
                        }
                    }
                    line = reader.readLine()
                }

                // Parse body if present (e.g. for POST /api/verify)
                val body = if (contentLength > 0 && contentLength < 1024 * 1024) {
                    val bodyChars = CharArray(contentLength)
                    var readTotal = 0
                    while (readTotal < contentLength) {
                        val read = reader.read(bodyChars, readTotal, contentLength - readTotal)
                        if (read == -1) break
                        readTotal += read
                    }
                    String(bodyChars, 0, readTotal)
                } else {
                    ""
                }

                val path = fullPath.substringBefore('?')
                val query = fullPath.substringAfter('?', "")
                val queryParams = parseQueryParams(query)

                when {
                    method == "OPTIONS" -> {
                        sendCorsPreflight(output)
                    }
                    path == "/" || path == "/index.html" -> {
                        sendHtmlApp(output)
                    }
                    path == "/api/verify" && method == "POST" -> {
                        handleVerifyPin(body, output)
                    }
                    path == "/api/status" -> {
                        handleStatus(output)
                    }
                    path == "/api/images" -> {
                        handleImagesList(queryParams, headers, output)
                    }
                    path.startsWith("/api/image/") -> {
                        val idxStr = path.removePrefix("/api/image/")
                        handleSingleImage(idxStr, queryParams, headers, output)
                    }
                    else -> {
                        send404(output)
                    }
                }
            } catch (_: Throwable) {
                // Client disconnected or socket error
            }
        }
    }

    private fun parseQueryParams(query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()
        return query.split("&").mapNotNull {
            val idx = it.indexOf('=')
            if (idx > 0) {
                it.substring(0, idx) to it.substring(idx + 1)
            } else null
        }.toMap()
    }

    private fun isAuthorized(params: Map<String, String>, headers: Map<String, String>): Boolean {
        if (activeSessionToken.isEmpty()) return false
        val tokenFromQuery = params["token"]
        val tokenFromHeader = headers["x-session-token"]
        return (tokenFromQuery == activeSessionToken || tokenFromHeader == activeSessionToken)
    }

    private fun handleVerifyPin(body: String, output: OutputStream) {
        val json = try { JSONObject(body) } catch (_: Throwable) { JSONObject() }
        val submittedPin = json.optString("pin", "").trim()

        if (submittedPin.isNotEmpty() && submittedPin == activePin) {
            val resp = JSONObject()
            resp.put("success", true)
            resp.put("token", activeSessionToken)
            sendJsonResponse(output, 200, resp.toString())
        } else {
            val resp = JSONObject()
            resp.put("success", false)
            resp.put("error", "Incorrect 4-digit PIN. Please try again.")
            sendJsonResponse(output, 401, resp.toString())
        }
    }

    private fun handleStatus(output: OutputStream) {
        val resp = JSONObject()
        resp.put("hosting", _hostState.value.isRunning)
        resp.put("imagesCount", SsiManager.importedImages.value.size)
        sendJsonResponse(output, 200, resp.toString())
    }

    private fun handleImagesList(params: Map<String, String>, headers: Map<String, String>, output: OutputStream) {
        if (!isAuthorized(params, headers)) {
            sendUnauthorized(output)
            return
        }

        val images = SsiManager.importedImages.value
        val arr = JSONArray()
        images.forEachIndexed { index, img ->
            val obj = JSONObject()
            obj.put("id", index)
            obj.put("name", img.name)
            obj.put("sizeBytes", img.sizeBytes)
            arr.put(obj)
        }
        val resp = JSONObject()
        resp.put("success", true)
        resp.put("images", arr)
        sendJsonResponse(output, 200, resp.toString())
    }

    private fun handleSingleImage(idxStr: String, params: Map<String, String>, headers: Map<String, String>, output: OutputStream) {
        if (!isAuthorized(params, headers)) {
            sendUnauthorized(output)
            return
        }

        val index = idxStr.toIntOrNull() ?: return send404(output)
        val images = SsiManager.importedImages.value
        if (index !in images.indices) {
            return send404(output)
        }

        val image = images[index]
        val file = image.file
        if (!file.exists() || file.length() == 0L) {
            return send404(output)
        }

        val qualitySetting = SettingsManager.settings.value.ssiStreamQuality
        val (fileBytes, mimeType) = when (qualitySetting) {
            "ULTRA_FAST" -> compressImageForStreaming(file, maxDimension = 1080, quality = 70)
                ?: (file.readBytes() to getFileMime(file))
            "BALANCED" -> compressImageForStreaming(file, maxDimension = 1920, quality = 85)
                ?: (file.readBytes() to getFileMime(file))
            else -> file.readBytes() to getFileMime(file)
        }

        val iv = ByteArray(8).apply { java.security.SecureRandom().nextBytes(this) }
        val ivHex = iv.joinToString("") { "%02x".format(it) }
        val keyString = "$activeSessionToken:$activePin:$ivHex"
        val keyBytes = keyString.toByteArray(Charsets.UTF_8)
        val encryptedBytes = rc4(fileBytes, keyBytes)
        val totalLength = iv.size + encryptedBytes.size

        val header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: application/octet-stream\r\n" +
                "Content-Disposition: attachment; filename=\"payload.enc\"\r\n" +
                "Content-Length: $totalLength\r\n" +
                "X-Image-Mime: $mimeType\r\n" +
                "Cache-Control: no-store, no-cache, must-revalidate, max-age=0, private\r\n" +
                "Pragma: no-cache\r\n" +
                "Expires: 0\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Expose-Headers: X-Image-Mime\r\n" +
                "X-Content-Type-Options: nosniff\r\n" +
                "Connection: close\r\n\r\n"

        output.write(header.toByteArray(StandardCharsets.US_ASCII))
        output.write(iv)
        output.write(encryptedBytes)
        output.flush()
    }

    private fun getFileMime(file: File): String {
        return when (file.extension.lowercase()) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            else -> "image/jpeg"
        }
    }

    private fun compressImageForStreaming(file: File, maxDimension: Int, quality: Int): Pair<ByteArray, String>? {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(file.absolutePath, options)
            if (options.outWidth <= 0 || options.outHeight <= 0) return null

            var sampleSize = 1
            var w = options.outWidth
            var h = options.outHeight
            while (w > maxDimension * 2 || h > maxDimension * 2) {
                sampleSize *= 2
                w /= 2
                h /= 2
            }

            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val originalBitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOpts) ?: return null

            val finalBitmap = if (originalBitmap.width > maxDimension || originalBitmap.height > maxDimension) {
                val ratio = minOf(maxDimension.toFloat() / originalBitmap.width, maxDimension.toFloat() / originalBitmap.height)
                val targetW = (originalBitmap.width * ratio).toInt().coerceAtLeast(1)
                val targetH = (originalBitmap.height * ratio).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(originalBitmap, targetW, targetH, true).also {
                    if (it != originalBitmap) originalBitmap.recycle()
                }
            } else {
                originalBitmap
            }

            val baos = ByteArrayOutputStream()
            val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
            finalBitmap.compress(format, quality, baos)
            finalBitmap.recycle()
            baos.toByteArray() to "image/webp"
        } catch (_: Throwable) {
            null
        }
    }

    private fun rc4(data: ByteArray, key: ByteArray): ByteArray {
        val s = IntArray(256) { it }
        var j = 0
        for (i in 0 until 256) {
            j = (j + s[i] + (key[i % key.size].toInt() and 0xff)) and 0xff
            val tmp = s[i]
            s[i] = s[j]
            s[j] = tmp
        }

        var i = 0
        j = 0
        val out = ByteArray(data.size)
        for (k in data.indices) {
            i = (i + 1) and 0xff
            j = (j + s[i]) and 0xff
            val tmp = s[i]
            s[i] = s[j]
            s[j] = tmp
            val kByte = s[(s[i] + s[j]) and 0xff]
            out[k] = (data[k].toInt() xor kByte).toByte()
        }
        return out
    }

    private fun sendJsonResponse(output: OutputStream, statusCode: Int, json: String) {
        val statusText = if (statusCode == 200) "OK" else if (statusCode == 401) "Unauthorized" else "Error"
        val bytes = json.toByteArray(StandardCharsets.UTF_8)
        val response = "HTTP/1.1 $statusCode $statusText\r\n" +
                "Content-Type: application/json; charset=utf-8\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Cache-Control: no-store, no-cache, must-revalidate, max-age=0, private\r\n" +
                "Pragma: no-cache\r\n" +
                "Expires: 0\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Connection: close\r\n\r\n"
        output.write(response.toByteArray(StandardCharsets.US_ASCII))
        output.write(bytes)
        output.flush()
    }

    private fun sendUnauthorized(output: OutputStream) {
        sendJsonResponse(output, 401, "{\"success\":false,\"error\":\"PIN authentication required\"}")
    }

    private fun send404(output: OutputStream) {
        val msg = "404 Not Found"
        val response = "HTTP/1.1 404 Not Found\r\n" +
                "Content-Type: text/plain\r\n" +
                "Content-Length: ${msg.length}\r\n" +
                "Connection: close\r\n\r\n" + msg
        output.write(response.toByteArray(StandardCharsets.US_ASCII))
        output.flush()
    }

    private fun sendCorsPreflight(output: OutputStream) {
        val response = "HTTP/1.1 204 No Content\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
                "Access-Control-Allow-Headers: Content-Type, X-Session-Token\r\n" +
                "Connection: close\r\n\r\n"
        output.write(response.toByteArray(StandardCharsets.US_ASCII))
        output.flush()
    }

    private fun sendHtmlApp(output: OutputStream) {
        val html = SsiWebClientHtml.getHtml()
        val bytes = html.toByteArray(StandardCharsets.UTF_8)
        val response = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/html; charset=utf-8\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Cache-Control: no-store, no-cache, must-revalidate, max-age=0, private\r\n" +
                "Pragma: no-cache\r\n" +
                "Expires: 0\r\n" +
                "Connection: close\r\n\r\n"
        output.write(response.toByteArray(StandardCharsets.US_ASCII))
        output.write(bytes)
        output.flush()
    }

    private fun getLocalIpAddress(context: Context): String {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val wifiInfo = wifiManager?.connectionInfo
            val ipInt = wifiInfo?.ipAddress ?: 0
            if (ipInt != 0) {
                return String.format(
                    java.util.Locale.US,
                    "%d.%d.%d.%d",
                    ipInt and 0xff,
                    ipInt shr 8 and 0xff,
                    ipInt shr 16 and 0xff,
                    ipInt shr 24 and 0xff
                )
            }

            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                if (intf.isLoopback || !intf.isUp) continue
                val addresses = intf.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val host = addr.hostAddress
                        if (host != null && !host.startsWith("127.")) {
                            return host
                        }
                    }
                }
            }
        } catch (_: Throwable) {}
        return "127.0.0.1"
    }
}
