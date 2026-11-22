package com.example.util

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Base64
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

enum class ApsVaultType(val wireCode: String, val displayName: String) {
    IMG_VAULT("IMG_VAULT", "IMG Vault"),
    VID_VAULT("VID_VAULT", "VID Vault");

    companion object {
        fun fromWireCode(code: String?): ApsVaultType? = entries.find { it.wireCode == code }
    }
}

enum class ApsTransportType(val label: String) {
    WIFI_LAN("Wi-Fi / Hotspot"),
    BLUETOOTH("Bluetooth")
}

enum class ApsTransferPhase {
    IDLE,
    DISCOVERING,
    CONNECTING,
    AWAITING_APPROVAL,
    TRANSFERRING,
    COMPLETED,
    FAILED
}

data class ApsPeerDevice(
    val idKey: String,
    val deviceId: String,
    val profileName: String,
    val deviceModel: String,
    val avatarBase64: String = "",
    val vaultType: ApsVaultType?,
    val transport: ApsTransportType,
    val hostAddress: String = "",
    val port: Int = 0,
    val bluetoothAddress: String = "",
    val lastSeenMs: Long = System.currentTimeMillis()
)

data class ApsTransferItem(
    val id: String,
    val name: String,
    val sizeBytes: Long,
    val mimeType: String,
    val durationMs: Long = 0L,
    val vaultType: ApsVaultType,
    val thumbnailProvider: (suspend () -> ByteArray?)? = null,
    val streamPlaintextTo: suspend (OutputStream) -> Boolean
)

data class ApsIncomingRequest(
    val senderDeviceId: String,
    val senderProfileName: String,
    val senderDeviceModel: String,
    val senderAvatarBase64: String,
    val vaultType: ApsVaultType,
    val itemCount: Int,
    val totalBytes: Long,
    val securityCode: String,
    val transport: ApsTransportType
)

data class ApsReceiverVaultTarget(
    val vaultType: ApsVaultType,
    val vaultFile: File,
    val masterKey: SecretKeySpec,
    val targetFolderId: String? = null,
    val onIngestComplete: () -> Unit = {}
)

data class ApsUiState(
    val isReceiveEnabled: Boolean = false,
    val activeReceiveVaultType: ApsVaultType? = null,
    val localDeviceId: String = "",
    val localProfileName: String = "",
    val localDeviceModel: String = "",
    val localAvatarBase64: String = "",
    val localIpAddress: String = "",
    val localTcpPort: Int = 0,
    val isBluetoothListening: Boolean = false,
    val discoveredPeers: List<ApsPeerDevice> = emptyList(),
    val phase: ApsTransferPhase = ApsTransferPhase.IDLE,
    val statusMessage: String = "",
    val errorMessage: String? = null,
    val securityCode: String = "",
    val currentFileName: String = "",
    val currentFileIndex: Int = 0,
    val totalFiles: Int = 0,
    val transferredBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val speedBytesPerSec: Long = 0L,
    val pendingIncomingRequest: ApsIncomingRequest? = null,
    val lastCompletedSummary: String? = null,
    val activePeerDeviceId: String = "",
    val activePeerProfileName: String = "",
    val activePeerDeviceModel: String = "",
    val activePeerAvatarBase64: String = "",
    val activeTransport: ApsTransportType? = null,
    val isSendingActive: Boolean = false
)

/**
 * High-Security Direct Vault-to-Vault APS (Authenticated Peer Sync) Engine.
 *
 * Security & Architecture:
 * - Ephemeral ECDH P-256 key exchange per transfer session + 6-digit SAS verification code.
 * - Strict Vault-Type enforcement: VID_VAULT <-> VID_VAULT and IMG_VAULT <-> IMG_VAULT ONLY.
 *   Cross-type transfers (e.g., VID Vault sending to IMG Vault APS) are cryptographically bound
 *   and rejected immediately during handshake with ERR_VAULT_TYPE_MISMATCH.
 * - 64 KB fixed streaming chunks using AES-256-CTR + per-file SHA-256 integrity verification.
 * - Direct Vault-to-Vault ingestion: files never touch phone gallery, Downloads, or cloud.
 * - Dual Transport: Same Wi-Fi/Hotspot (NSD + UDP LAN beacon + Direct TCP) & Bluetooth RFCOMM.
 */
object ApsTransferEngine {

    const val PROTOCOL_MAGIC = "APS1"
    const val PROTOCOL_AVATAR_PEEK = "APSA"
    const val ERR_VAULT_TYPE_MISMATCH = "ERR_VAULT_TYPE_MISMATCH"
    const val ERR_VAULT_LOCKED = "ERR_VAULT_LOCKED"
    const val ERR_REJECTED = "ERR_REJECTED"
    const val ERR_HASH_MISMATCH = "ERR_HASH_MISMATCH"
    const val BEACON_PORT = 48731
    private const val NSD_SERVICE_TYPE = "_aistudioaps._tcp."
    private const val STREAM_BUFFER_SIZE = 64 * 1024
    private const val MAX_APS_CHUNK_SIZE = 512 * 1024
    val APS_BT_UUID: UUID = UUID.fromString("8e4f2c10-9a71-4b2d-8f13-5c9e7a3b1d02")

    private val secureRandom = SecureRandom()
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(ApsUiState())
    val state: StateFlow<ApsUiState> = _state.asStateFlow()

    @Volatile
    private var receiverTarget: ApsReceiverVaultTarget? = null
    private var tcpServerSocket: ServerSocket? = null
    private var btServerSocket: BluetoothServerSocket? = null
    private var tcpAcceptJob: Job? = null
    private var btAcceptJob: Job? = null
    private var beaconSendJob: Job? = null
    private var beaconListenJob: Job? = null
    private var transferJob: Job? = null
    private var activeClientSocket: Socket? = null
    private var activeBtSocket: BluetoothSocket? = null
    private var pendingApprovalDeferred: CompletableDeferred<Boolean>? = null

    private var nsdManager: NsdManager? = null
    private var nsdRegistrationListener: NsdManager.RegistrationListener? = null
    private var nsdDiscoveryListener: NsdManager.DiscoveryListener? = null

    // Optional hook for automated tests to bypass interactive receiver approval dialog
    @Volatile
    internal var testAutoApproveIncoming: Boolean? = null

    // ---- Identity Helpers ----

    fun getLocalIdentity(context: Context): Triple<String, String, String> {
        val profile = ProfileManager.userProfile.value
        val deviceId = profile?.deviceCode?.takeIf { it.isNotBlank() }
            ?: getOrCreateFallbackDeviceId(context)
        val profileName = profile?.fullName?.takeIf { it.isNotBlank() } ?: "Vault User"
        val deviceModel = profile?.device?.takeIf { it.isNotBlank() } ?: ProfileManager.getAutoDeviceModel()
        return Triple(deviceId, profileName, deviceModel)
    }

    private fun getOrCreateFallbackDeviceId(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences("aps_identity_prefs", Context.MODE_PRIVATE)
        val existing = prefs.getString("aps_device_id", null)
        if (!existing.isNullOrBlank()) return existing
        val generated = "APS-" + UUID.randomUUID().toString().replace("-", "").take(8).uppercase()
        prefs.edit().putString("aps_device_id", generated).apply()
        return generated
    }

    @Volatile
    private var cachedAvatarSourceHash: Int = 0
    @Volatile
    private var cachedAvatarCompactB64: String = ""
    private val inFlightAvatarFetches = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    /**
     * Always produces a crisp, lightweight 160x160 JPEG Base64 avatar (~6-14 KB) from the user's
     * profile photo so sender and receiver reliably see each other's photo over Wi-Fi and Bluetooth.
     */
    fun getLocalAvatarSnippet(): String {
        val raw = ProfileManager.userProfile.value?.profileImageBase64 ?: ""
        if (raw.isBlank()) {
            cachedAvatarSourceHash = 0
            cachedAvatarCompactB64 = ""
            return ""
        }
        val sig = raw.hashCode() * 31 + raw.length
        if (sig == cachedAvatarSourceHash && cachedAvatarCompactB64.isNotBlank()) {
            return cachedAvatarCompactB64
        }
        return try {
            val decoded = Base64.decode(raw, Base64.DEFAULT)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(decoded, 0, decoded.size, bounds)
            var sample = 1
            while (bounds.outWidth / sample > 320 || bounds.outHeight / sample > 320) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = BitmapFactory.decodeByteArray(decoded, 0, decoded.size, opts)
            if (bmp == null) {
                if (raw.length <= 20_000) raw else ""
            } else {
                val minSide = minOf(bmp.width, bmp.height).coerceAtLeast(1)
                val x = (bmp.width - minSide) / 2
                val y = (bmp.height - minSide) / 2
                val cropped = Bitmap.createBitmap(bmp, x, y, minSide, minSide)
                val targetDim = minOf(minSide, 160)
                val scaled = if (cropped.width != targetDim) {
                    Bitmap.createScaledBitmap(cropped, targetDim, targetDim, true)
                } else {
                    cropped
                }
                val baos = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, 78, baos)
                if (scaled != cropped) scaled.recycle()
                if (cropped != bmp) cropped.recycle()
                bmp.recycle()
                val compact = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                cachedAvatarSourceHash = sig
                cachedAvatarCompactB64 = compact
                compact
            }
        } catch (_: Throwable) {
            if (raw.length <= 20_000) raw else ""
        }
    }

    fun getLocalWifiIpAddress(): String {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            var fallback: String? = null
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                if (!intf.isUp || intf.isLoopback) continue
                val addrs = intf.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val ip = addr.hostAddress ?: continue
                        if (intf.name.startsWith("wlan") || intf.name.startsWith("ap") || intf.name.startsWith("swlan")) {
                            return ip
                        }
                        if (fallback == null) fallback = ip
                    }
                }
            }
            fallback ?: "127.0.0.1"
        } catch (_: Throwable) {
            "127.0.0.1"
        }
    }

    fun hasBluetoothPermissions(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    // ---- Crypto Primitives (ECDH P-256 + SHA-256 + AES-256-CTR) ----

    internal fun generateEphemeralEcKeyPair(): KeyPair {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"), secureRandom)
        return kpg.generateKeyPair()
    }

    internal fun deriveSessionKeyAndSas(
        myKeyPair: KeyPair,
        peerPublicKeyEncoded: ByteArray,
        vaultType: ApsVaultType
    ): Pair<SecretKeySpec, String> {
        val kf = KeyFactory.getInstance("EC")
        val peerPubKey = kf.generatePublic(X509EncodedKeySpec(peerPublicKeyEncoded))
        val ka = KeyAgreement.getInstance("ECDH")
        ka.init(myKeyPair.private)
        ka.doPhase(peerPubKey, true)
        val sharedSecret = ka.generateSecret()

        val keyMd = MessageDigest.getInstance("SHA-256")
        keyMd.update(sharedSecret)
        keyMd.update("APS_V1_KEY_".toByteArray(StandardCharsets.UTF_8))
        keyMd.update(vaultType.wireCode.toByteArray(StandardCharsets.UTF_8))
        val aesKeyBytes = keyMd.digest()

        val sasMd = MessageDigest.getInstance("SHA-256")
        sasMd.update(sharedSecret)
        sasMd.update("APS_V1_SAS_".toByteArray(StandardCharsets.UTF_8))
        sasMd.update(vaultType.wireCode.toByteArray(StandardCharsets.UTF_8))
        val sasDigest = sasMd.digest()
        val sasInt = (((sasDigest[0].toInt() and 0x7F) shl 24) or
            ((sasDigest[1].toInt() and 0xFF) shl 16) or
            ((sasDigest[2].toInt() and 0xFF) shl 8) or
            (sasDigest[3].toInt() and 0xFF)) % 1_000_000
        val sasCode = "%06d".format(sasInt)

        sharedSecret.fill(0)
        return Pair(SecretKeySpec(aesKeyBytes, "AES"), sasCode)
    }

    // ---- Receive Mode (TCP Server + NSD + UDP Beacon + Bluetooth RFCOMM Server) ----

    fun startReceiveMode(
        context: Context,
        target: ApsReceiverVaultTarget
    ) {
        stopReceiveMode()
        receiverTarget = target

        val (devId, profName, devModel) = getLocalIdentity(context)
        val ip = getLocalWifiIpAddress()

        val server = try {
            ServerSocket(0).apply { reuseAddress = true }
        } catch (e: Throwable) {
            _state.update {
                it.copy(
                    isReceiveEnabled = false,
                    errorMessage = "Could not start APS Wi-Fi listener: ${e.message}"
                )
            }
            return
        }
        tcpServerSocket = server
        val port = server.localPort

        _state.update {
            it.copy(
                isReceiveEnabled = true,
                activeReceiveVaultType = target.vaultType,
                localDeviceId = devId,
                localProfileName = profName,
                localDeviceModel = devModel,
                localAvatarBase64 = getLocalAvatarSnippet(),
                localIpAddress = ip,
                localTcpPort = port,
                errorMessage = null,
                statusMessage = "Listening for ${target.vaultType.displayName} APS transfers on $ip:$port"
            )
        }

        // 1. Accept incoming TCP Wi-Fi/Hotspot connections
        tcpAcceptJob = engineScope.launch {
            while (isActive && !server.isClosed) {
                try {
                    val client = server.accept()
                    client.tcpNoDelay = true
                    launch {
                        handleIncomingConnection(
                            context = context.applicationContext,
                            input = BufferedInputStream(client.getInputStream(), STREAM_BUFFER_SIZE),
                            output = BufferedOutputStream(client.getOutputStream(), STREAM_BUFFER_SIZE),
                            transport = ApsTransportType.WIFI_LAN,
                            closeConn = { try { client.close() } catch (_: Throwable) {} }
                        )
                    }
                } catch (_: Throwable) {
                    if (server.isClosed) break
                }
            }
        }

        // 2. Register NSD service + UDP LAN beacon for instant Wi-Fi/Hotspot discovery
        registerNsdService(context.applicationContext, devId, target.vaultType, port)
        startBeaconBroadcaster(devId, profName, devModel, target.vaultType, port)

        // 3. Start Bluetooth RFCOMM listener if Bluetooth is enabled & permitted
        startBluetoothServerIfPermitted(context.applicationContext)
    }

    fun stopReceiveMode() {
        receiverTarget = null
        pendingApprovalDeferred?.complete(false)
        pendingApprovalDeferred = null

        beaconSendJob?.cancel()
        beaconSendJob = null
        tcpAcceptJob?.cancel()
        tcpAcceptJob = null
        btAcceptJob?.cancel()
        btAcceptJob = null

        try { tcpServerSocket?.close() } catch (_: Throwable) {}
        tcpServerSocket = null
        try { btServerSocket?.close() } catch (_: Throwable) {}
        btServerSocket = null

        unregisterNsdService()

        _state.update {
            it.copy(
                isReceiveEnabled = false,
                activeReceiveVaultType = null,
                localTcpPort = 0,
                isBluetoothListening = false,
                pendingIncomingRequest = null
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun startBluetoothServerIfPermitted(context: Context) {
        if (!hasBluetoothPermissions(context)) return
        val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return
        val adapter = btManager.adapter ?: return
        if (!adapter.isEnabled) return

        try {
            val serverSock = adapter.listenUsingRfcommWithServiceRecord("ApsVaultSync", APS_BT_UUID)
            btServerSocket = serverSock
            _state.update { it.copy(isBluetoothListening = true) }

            btAcceptJob = engineScope.launch {
                while (isActive) {
                    try {
                        val socket = serverSock.accept()
                        launch {
                            handleIncomingConnection(
                                context = context,
                                input = BufferedInputStream(socket.inputStream, STREAM_BUFFER_SIZE),
                                output = BufferedOutputStream(socket.outputStream, STREAM_BUFFER_SIZE),
                                transport = ApsTransportType.BLUETOOTH,
                                closeConn = { try { socket.close() } catch (_: Throwable) {} }
                            )
                        }
                    } catch (_: Throwable) {
                        break
                    }
                }
            }
        } catch (_: Throwable) {
            _state.update { it.copy(isBluetoothListening = false) }
        }
    }

    // ---- Discovery (Wi-Fi/Hotspot UDP + NSD + Paired Bluetooth Devices) ----

    fun startDiscovery(context: Context) {
        val (devId, profName, devModel) = getLocalIdentity(context)
        _state.update {
            it.copy(
                localDeviceId = devId,
                localProfileName = profName,
                localDeviceModel = devModel,
                localAvatarBase64 = getLocalAvatarSnippet(),
                localIpAddress = getLocalWifiIpAddress(),
                phase = if (it.phase == ApsTransferPhase.IDLE) ApsTransferPhase.DISCOVERING else it.phase
            )
        }

        startBeaconListener(devId)
        startNsdDiscovery(context.applicationContext, devId)
        refreshBluetoothPeers(context.applicationContext)
    }

    fun stopDiscovery() {
        beaconListenJob?.cancel()
        beaconListenJob = null
        stopNsdDiscovery()
        if (_state.value.phase == ApsTransferPhase.DISCOVERING) {
            _state.update { it.copy(phase = ApsTransferPhase.IDLE) }
        }
    }

    @SuppressLint("MissingPermission")
    fun refreshBluetoothPeers(context: Context) {
        if (!hasBluetoothPermissions(context)) return
        engineScope.launch {
            try {
                val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return@launch
                val adapter = btManager.adapter ?: return@launch
                if (!adapter.isEnabled) return@launch

                val bonded = adapter.bondedDevices ?: emptySet()
                val btPeers = bonded.mapNotNull { device ->
                    val addr = device.address ?: return@mapNotNull null
                    val name = device.name?.takeIf { it.isNotBlank() } ?: "Bluetooth Device"
                    ApsPeerDevice(
                        idKey = "BT_$addr",
                        deviceId = addr.takeLast(8),
                        profileName = name,
                        deviceModel = "Paired Bluetooth Device",
                        vaultType = null, // Verified strictly on handshake!
                        transport = ApsTransportType.BLUETOOTH,
                        bluetoothAddress = addr,
                        lastSeenMs = System.currentTimeMillis()
                    )
                }

                _state.update { state ->
                    val nonBt = state.discoveredPeers.filter { it.transport != ApsTransportType.BLUETOOTH }
                    state.copy(discoveredPeers = (nonBt + btPeers).sortedByDescending { it.transport == ApsTransportType.WIFI_LAN })
                }
            } catch (_: Throwable) {}
        }
    }

    private fun startBeaconBroadcaster(
        deviceId: String,
        profileName: String,
        deviceModel: String,
        vaultType: ApsVaultType,
        tcpPort: Int
    ) {
        beaconSendJob?.cancel()
        beaconSendJob = engineScope.launch {
            val payload = JSONObject().apply {
                put("magic", "APS_BEACON_V1")
                put("deviceId", deviceId)
                put("profileName", profileName)
                put("deviceModel", deviceModel)
                put("vaultType", vaultType.wireCode)
                put("tcpPort", tcpPort)
            }.toString().toByteArray(StandardCharsets.UTF_8)

            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket().apply { broadcast = true }
                while (isActive && receiverTarget != null) {
                    try {
                        val bcastTargets = collectBroadcastAddresses()
                        bcastTargets.forEach { targetAddr ->
                            val pkt = DatagramPacket(payload, payload.size, targetAddr, BEACON_PORT)
                            socket.send(pkt)
                        }
                    } catch (_: Throwable) {}
                    delay(2000L)
                }
            } catch (_: Throwable) {
            } finally {
                try { socket?.close() } catch (_: Throwable) {}
            }
        }
    }

    private fun collectBroadcastAddresses(): List<InetAddress> {
        val result = mutableSetOf<InetAddress>()
        try {
            result.add(InetAddress.getByName("255.255.255.255"))
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                if (!intf.isUp || intf.isLoopback) continue
                intf.interfaceAddresses.forEach { ia ->
                    ia.broadcast?.let { result.add(it) }
                }
            }
        } catch (_: Throwable) {}
        return result.toList()
    }

    private fun startBeaconListener(localDeviceId: String) {
        beaconListenJob?.cancel()
        beaconListenJob = engineScope.launch {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(InetSocketAddress(BEACON_PORT))
                    soTimeout = 3000
                }
                val buf = ByteArray(2048)
                while (isActive) {
                    try {
                        val packet = DatagramPacket(buf, buf.size)
                        socket.receive(packet)
                        val msg = String(packet.data, 0, packet.length, StandardCharsets.UTF_8)
                        val json = JSONObject(msg)
                        if (json.optString("magic") == "APS_BEACON_V1") {
                            val peerId = json.optString("deviceId")
                            if (peerId.isNotBlank() && peerId != localDeviceId) {
                                val peer = ApsPeerDevice(
                                    idKey = "WIFI_$peerId",
                                    deviceId = peerId,
                                    profileName = json.optString("profileName", "Vault User"),
                                    deviceModel = json.optString("deviceModel", "Android Device"),
                                    vaultType = ApsVaultType.fromWireCode(json.optString("vaultType")),
                                    transport = ApsTransportType.WIFI_LAN,
                                    hostAddress = packet.address.hostAddress ?: "",
                                    port = json.optInt("tcpPort", 0),
                                    lastSeenMs = System.currentTimeMillis()
                                )
                                upsertDiscoveredPeer(peer)
                            }
                        }
                    } catch (_: java.net.SocketTimeoutException) {
                        pruneStaleWifiPeers()
                    } catch (_: Throwable) {}
                }
            } catch (_: Throwable) {
            } finally {
                try { socket?.close() } catch (_: Throwable) {}
            }
        }
    }

    private fun upsertDiscoveredPeer(peer: ApsPeerDevice) {
        if (peer.transport == ApsTransportType.WIFI_LAN && (peer.hostAddress.isBlank() || peer.port <= 0)) return
        var needsAvatarFetch = false
        _state.update { state ->
            val existing = state.discoveredPeers.find { it.idKey == peer.idKey }
            val mergedAvatar = peer.avatarBase64.ifBlank { existing?.avatarBase64 ?: "" }
            if (peer.transport == ApsTransportType.WIFI_LAN && mergedAvatar.isBlank()) {
                needsAvatarFetch = true
            }
            val mergedPeer = peer.copy(avatarBase64 = mergedAvatar)
            val list = state.discoveredPeers.filterNot { it.idKey == peer.idKey } + mergedPeer
            state.copy(discoveredPeers = list.sortedByDescending { it.transport == ApsTransportType.WIFI_LAN })
        }
        if (needsAvatarFetch) {
            fetchPeerAvatarAsync(peer)
        }
    }

    private fun fetchPeerAvatarAsync(peer: ApsPeerDevice) {
        if (!inFlightAvatarFetches.add(peer.idKey)) return
        engineScope.launch {
            var sock: Socket? = null
            try {
                sock = Socket()
                sock.connect(InetSocketAddress(peer.hostAddress, peer.port), 2_500)
                sock.soTimeout = 2_500
                val dout = DataOutputStream(BufferedOutputStream(sock.getOutputStream(), 1024))
                val din = DataInputStream(BufferedInputStream(sock.getInputStream(), 8192))
                dout.write(PROTOCOL_AVATAR_PEEK.toByteArray(StandardCharsets.US_ASCII))
                dout.flush()
                val reply = readJsonFrame(din)
                if (reply.optString("status") == "OK") {
                    val avatarB64 = reply.optString("avatarBase64", "")
                    val name = reply.optString("profileName", "").ifBlank { peer.profileName }
                    val model = reply.optString("deviceModel", "").ifBlank { peer.deviceModel }
                    if (avatarB64.isNotBlank()) {
                        _state.update { state ->
                            val updated = state.discoveredPeers.map { existing ->
                                if (existing.idKey == peer.idKey) {
                                    existing.copy(
                                        avatarBase64 = avatarB64,
                                        profileName = name,
                                        deviceModel = model
                                    )
                                } else existing
                            }
                            state.copy(discoveredPeers = updated)
                        }
                    }
                }
            } catch (_: Throwable) {
            } finally {
                try { sock?.close() } catch (_: Throwable) {}
                inFlightAvatarFetches.remove(peer.idKey)
            }
        }
    }

    private fun pruneStaleWifiPeers() {
        val cutoff = System.currentTimeMillis() - 12_000L
        _state.update { state ->
            val fresh = state.discoveredPeers.filter {
                it.transport == ApsTransportType.BLUETOOTH || it.lastSeenMs >= cutoff
            }
            if (fresh.size != state.discoveredPeers.size) state.copy(discoveredPeers = fresh) else state
        }
    }

    private fun registerNsdService(context: Context, deviceId: String, vaultType: ApsVaultType, port: Int) {
        try {
            val mgr = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
            nsdManager = mgr
            val serviceInfo = NsdServiceInfo().apply {
                serviceName = "APS_${vaultType.wireCode}_${deviceId.take(10)}"
                serviceType = NSD_SERVICE_TYPE
                setPort(port)
            }
            val listener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(info: NsdServiceInfo) {}
                override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {}
                override fun onServiceUnregistered(info: NsdServiceInfo) {}
                override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {}
            }
            nsdRegistrationListener = listener
            mgr.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (_: Throwable) {}
    }

    private fun unregisterNsdService() {
        try {
            val mgr = nsdManager
            val listener = nsdRegistrationListener
            if (mgr != null && listener != null) {
                mgr.unregisterService(listener)
            }
        } catch (_: Throwable) {}
        nsdRegistrationListener = null
    }

    private fun startNsdDiscovery(context: Context, localDeviceId: String) {
        stopNsdDiscovery()
        try {
            val mgr = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
            nsdManager = mgr
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(regType: String) {}
                override fun onServiceFound(service: NsdServiceInfo) {
                    if (!service.serviceName.startsWith("APS_")) return
                    if (service.serviceName.endsWith(localDeviceId.take(10))) return
                    try {
                        @Suppress("DEPRECATION")
                        mgr.resolveService(service, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                            override fun onServiceResolved(resolved: NsdServiceInfo) {
                                val host = resolved.host?.hostAddress ?: return
                                val parts = resolved.serviceName.split("_")
                                val vt = when {
                                    resolved.serviceName.contains("VID_VAULT") -> ApsVaultType.VID_VAULT
                                    resolved.serviceName.contains("IMG_VAULT") -> ApsVaultType.IMG_VAULT
                                    else -> null
                                }
                                val idPart = parts.lastOrNull() ?: host
                                upsertDiscoveredPeer(
                                    ApsPeerDevice(
                                        idKey = "WIFI_$idPart",
                                        deviceId = idPart,
                                        profileName = "Nearby Vault ($idPart)",
                                        deviceModel = "Same Wi-Fi Device",
                                        vaultType = vt,
                                        transport = ApsTransportType.WIFI_LAN,
                                        hostAddress = host,
                                        port = resolved.port,
                                        lastSeenMs = System.currentTimeMillis()
                                    )
                                )
                            }
                        })
                    } catch (_: Throwable) {}
                }

                override fun onServiceLost(service: NsdServiceInfo) {}
                override fun onDiscoveryStopped(serviceType: String) {}
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            }
            nsdDiscoveryListener = listener
            mgr.discoverServices(NSD_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (_: Throwable) {}
    }

    private fun stopNsdDiscovery() {
        try {
            val mgr = nsdManager
            val listener = nsdDiscoveryListener
            if (mgr != null && listener != null) {
                mgr.stopServiceDiscovery(listener)
            }
        } catch (_: Throwable) {}
        nsdDiscoveryListener = null
    }

    // ---- Incoming Connection Handler (Receiver Side) ----

    fun approveIncomingRequest() {
        pendingApprovalDeferred?.complete(true)
    }

    fun rejectIncomingRequest() {
        pendingApprovalDeferred?.complete(false)
    }

    private suspend fun handleIncomingConnection(
        context: Context,
        input: InputStream,
        output: OutputStream,
        transport: ApsTransportType,
        closeConn: () -> Unit
    ) {
        val din = DataInputStream(input)
        val dout = DataOutputStream(output)
        var sessionKey: SecretKeySpec? = null
        val stagingDir = File(context.cacheDir, "aps_staging").apply { mkdirs() }
        var historyLogged = false
        var senderDeviceId = "Unknown"
        var senderProfileName = "Unknown User"
        var senderModel = "Android"
        var senderAvatar = ""
        var resolvedVaultType: ApsVaultType = receiverTarget?.vaultType ?: ApsVaultType.VID_VAULT
        var expectedItems = 0
        var expectedBytes = 0L
        var activeSasCode = ""
        val receivedFileNames = mutableListOf<String>()
        var transferStartMs = System.currentTimeMillis()

        try {
            val magicBytes = ByteArray(4)
            din.readFully(magicBytes)
            val magic = String(magicBytes, StandardCharsets.US_ASCII)

            // Fast lightweight profile photo peek during discovery so sender sees receiver's photo before tapping Send
            if (magic == PROTOCOL_AVATAR_PEEK) {
                val (_, myName, myModel) = getLocalIdentity(context)
                writeJsonFrame(
                    dout,
                    JSONObject()
                        .put("status", "OK")
                        .put("avatarBase64", getLocalAvatarSnippet())
                        .put("profileName", myName)
                        .put("deviceModel", myModel)
                )
                historyLogged = true
                return
            }

            if (magic != PROTOCOL_MAGIC) {
                writeJsonFrame(dout, JSONObject().put("status", "ERR_BAD_MAGIC").put("message", "Invalid APS protocol header."))
                historyLogged = true
                return
            }

            val hello = readJsonFrame(din)
            val senderVaultType = ApsVaultType.fromWireCode(hello.optString("vaultType"))
            senderDeviceId = hello.optString("deviceId", "Unknown")
            senderProfileName = hello.optString("profileName", "Unknown User")
            senderModel = hello.optString("deviceModel", "Android")
            senderAvatar = hello.optString("avatarBase64", "")
            val itemCount = hello.optInt("itemCount", 0)
            val totalBytes = hello.optLong("totalBytes", 0L)
            expectedItems = itemCount
            expectedBytes = totalBytes
            val senderPubKeyBytes = Base64.decode(hello.optString("ecdhPubKey", ""), Base64.NO_WRAP)

            val target = receiverTarget
            if (target == null || !target.vaultFile.exists()) {
                writeJsonFrame(
                    dout,
                    JSONObject()
                        .put("status", ERR_VAULT_LOCKED)
                        .put("message", "Receiver vault is locked or APS Receive Mode is turned off.")
                )
                historyLogged = true
                return
            }
            resolvedVaultType = target.vaultType

            // CRITICAL ENFORCEMENT: VID Vault <-> VID Vault and IMG Vault <-> IMG Vault ONLY!
            if (senderVaultType == null || senderVaultType != target.vaultType) {
                val expectedName = senderVaultType?.displayName ?: "Unknown Vault"
                val rejectMsg = "Connection failed: $expectedName files can only transfer to $expectedName " +
                    "(receiver currently has ${target.vaultType.displayName} APS enabled)."
                writeJsonFrame(
                    dout,
                    JSONObject()
                        .put("status", ERR_VAULT_TYPE_MISMATCH)
                        .put("message", rejectMsg)
                        .put("receiverVaultType", target.vaultType.wireCode)
                )
                ActivityLogManager.log(
                    context,
                    "APS_MISMATCH",
                    "APS Transfer Rejected (Vault Mismatch)",
                    "$senderProfileName ($senderDeviceId) tried sending $expectedName to ${target.vaultType.displayName}.",
                    severity = "WARNING"
                )
                ApsTransferHistoryManager.record(
                    context,
                    ApsTransferHistoryEntry(
                        direction = "RECEIVED",
                        vaultType = target.vaultType.wireCode,
                        transport = transport.name,
                        status = "MISMATCH",
                        peerDeviceId = senderDeviceId,
                        peerProfileName = senderProfileName,
                        peerDeviceModel = senderModel,
                        peerAvatarBase64 = senderAvatar,
                        fileCount = itemCount,
                        totalBytes = totalBytes,
                        errorMessage = rejectMsg
                    )
                )
                historyLogged = true
                _state.update {
                    it.copy(
                        errorMessage = rejectMsg,
                        statusMessage = "Blocked mismatched transfer from $senderProfileName"
                    )
                }
                return
            }

            // Ephemeral ECDH key agreement bound to the validated VaultType
            val myEcPair = generateEphemeralEcKeyPair()
            val (derivedKey, sasCode) = deriveSessionKeyAndSas(myEcPair, senderPubKeyBytes, target.vaultType)
            sessionKey = derivedKey
            activeSasCode = sasCode

            val (myId, myName, myModel) = getLocalIdentity(context)
            val myAvatar = getLocalAvatarSnippet()
            writeJsonFrame(
                dout,
                JSONObject()
                    .put("status", "OK_VERIFY")
                    .put("vaultType", target.vaultType.wireCode)
                    .put("deviceId", myId)
                    .put("profileName", myName)
                    .put("deviceModel", myModel)
                    .put("avatarBase64", myAvatar)
                    .put("ecdhPubKey", Base64.encodeToString(myEcPair.public.encoded, Base64.NO_WRAP))
            )

            // Wait for receiver approval (or test hook)
            val approved: Boolean = testAutoApproveIncoming ?: run {
                val deferred = CompletableDeferred<Boolean>()
                pendingApprovalDeferred = deferred
                _state.update {
                    it.copy(
                        phase = ApsTransferPhase.AWAITING_APPROVAL,
                        securityCode = sasCode,
                        localAvatarBase64 = myAvatar,
                        activePeerDeviceId = senderDeviceId,
                        activePeerProfileName = senderProfileName,
                        activePeerDeviceModel = senderModel,
                        activePeerAvatarBase64 = senderAvatar,
                        activeTransport = transport,
                        isSendingActive = false,
                        pendingIncomingRequest = ApsIncomingRequest(
                            senderDeviceId = senderDeviceId,
                            senderProfileName = senderProfileName,
                            senderDeviceModel = senderModel,
                            senderAvatarBase64 = senderAvatar,
                            vaultType = target.vaultType,
                            itemCount = itemCount,
                            totalBytes = totalBytes,
                            securityCode = sasCode,
                            transport = transport
                        ),
                        statusMessage = "Incoming ${target.vaultType.displayName} transfer from $senderProfileName"
                    )
                }
                deferred.await()
            }
            pendingApprovalDeferred = null

            if (!approved) {
                writeJsonFrame(dout, JSONObject().put("status", ERR_REJECTED).put("message", "Receiver declined the APS transfer."))
                ApsTransferHistoryManager.record(
                    context,
                    ApsTransferHistoryEntry(
                        direction = "RECEIVED",
                        vaultType = target.vaultType.wireCode,
                        transport = transport.name,
                        status = "REJECTED",
                        peerDeviceId = senderDeviceId,
                        peerProfileName = senderProfileName,
                        peerDeviceModel = senderModel,
                        peerAvatarBase64 = senderAvatar,
                        fileCount = itemCount,
                        totalBytes = totalBytes,
                        securityCode = sasCode,
                        errorMessage = "Declined by receiver"
                    )
                )
                historyLogged = true
                _state.update {
                    it.copy(
                        phase = ApsTransferPhase.IDLE,
                        pendingIncomingRequest = null,
                        statusMessage = "Declined transfer from $senderProfileName"
                    )
                }
                return
            }

            writeJsonFrame(dout, JSONObject().put("status", "APPROVED"))
            transferStartMs = System.currentTimeMillis()

            _state.update {
                it.copy(
                    phase = ApsTransferPhase.TRANSFERRING,
                    pendingIncomingRequest = null,
                    securityCode = sasCode,
                    localAvatarBase64 = myAvatar,
                    activePeerDeviceId = senderDeviceId,
                    activePeerProfileName = senderProfileName,
                    activePeerDeviceModel = senderModel,
                    activePeerAvatarBase64 = senderAvatar,
                    activeTransport = transport,
                    isSendingActive = false,
                    totalFiles = itemCount,
                    totalBytes = totalBytes,
                    transferredBytes = 0L,
                    currentFileIndex = 0,
                    errorMessage = null,
                    statusMessage = "Receiving $itemCount encrypted file(s) directly into ${target.vaultType.displayName}…"
                )
            }

            val startTime = System.currentTimeMillis()
            var totalReceivedBytes = 0L
            var receivedCount = 0
            var encBuf = ByteArray(STREAM_BUFFER_SIZE + 64)
            var decBuf = ByteArray(STREAM_BUFFER_SIZE + 96)

            for (idx in 0 until itemCount) {
                val itemMeta = readJsonFrame(din)
                val fileName = itemMeta.optString("name", "aps_file_${idx + 1}")
                val mimeType = itemMeta.optString("mimeType", if (target.vaultType == ApsVaultType.VID_VAULT) "video/mp4" else "image/jpeg")
                val durationMs = itemMeta.optLong("durationMs", 0L)
                val ivBytes = Base64.decode(itemMeta.optString("ivB64", ""), Base64.NO_WRAP)
                val senderThumbB64 = itemMeta.optString("thumbB64", "")
                val senderThumbBytes = if (senderThumbB64.isNotBlank()) {
                    try { Base64.decode(senderThumbB64, Base64.NO_WRAP) } catch (_: Throwable) { null }
                } else null
                receivedFileNames.add(fileName)

                _state.update {
                    it.copy(
                        currentFileIndex = idx + 1,
                        currentFileName = fileName,
                        statusMessage = "Receiving (${idx + 1}/$itemCount): $fileName"
                    )
                }

                val cipher = Cipher.getInstance("AES/CTR/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, derivedKey, IvParameterSpec(ivBytes))
                val sha256 = MessageDigest.getInstance("SHA-256")

                val ext = fileName.substringAfterLast('.', if (target.vaultType == ApsVaultType.VID_VAULT) "mp4" else "jpg").ifBlank { "bin" }
                val stagingFile = File(stagingDir, "aps_in_${UUID.randomUUID()}.$ext")

                try {
                    var lastUiUpdate = 0L
                    FileOutputStream(stagingFile).use { fos ->
                        while (true) {
                            val chunkLen = din.readInt()
                            if (chunkLen == 0) break
                            if (chunkLen < 0 || chunkLen > MAX_APS_CHUNK_SIZE) {
                                throw IllegalStateException("Invalid APS encrypted chunk length: $chunkLen")
                            }
                            if (chunkLen > encBuf.size) {
                                encBuf = ByteArray(chunkLen + 64)
                                decBuf = ByteArray(chunkLen + 96)
                            }
                            din.readFully(encBuf, 0, chunkLen)
                            val decLen = cipher.update(encBuf, 0, chunkLen, decBuf, 0)
                            if (decLen > 0) {
                                fos.write(decBuf, 0, decLen)
                                sha256.update(decBuf, 0, decLen)
                            }
                            totalReceivedBytes += chunkLen

                            val now = System.currentTimeMillis()
                            if (now - lastUiUpdate > 200L) {
                                lastUiUpdate = now
                                val elapsedSec = ((now - startTime) / 1000.0).coerceAtLeast(0.1)
                                _state.update {
                                    it.copy(
                                        transferredBytes = totalReceivedBytes,
                                        speedBytesPerSec = (totalReceivedBytes / elapsedSec).toLong()
                                    )
                                }
                            }
                        }
                        val finalDec = cipher.doFinal()
                        if (finalDec != null && finalDec.isNotEmpty()) {
                            fos.write(finalDec)
                            sha256.update(finalDec)
                        }
                    }

                    // Verify SHA-256 integrity hash sent by sender
                    val expectedSha = ByteArray(32)
                    din.readFully(expectedSha)
                    val actualSha = sha256.digest()
                    if (!actualSha.contentEquals(expectedSha)) {
                        writeJsonFrame(
                            dout,
                            JSONObject().put("status", ERR_HASH_MISMATCH).put("message", "Integrity check failed for $fileName")
                        )
                        throw IllegalStateException("SHA-256 integrity mismatch for $fileName")
                    }

                    // Ingest directly into the unlocked target vault container!
                    val ingestOk = when (target.vaultType) {
                        ApsVaultType.VID_VAULT -> {
                            VidVaultEngine.importVideoFromFile(
                                context = context,
                                vaultFile = target.vaultFile,
                                masterKey = target.masterKey,
                                sourceFile = stagingFile,
                                displayName = fileName,
                                mimeType = mimeType,
                                durationHintMs = durationMs,
                                targetFolderId = target.targetFolderId,
                                preExtractedThumbnailBytes = senderThumbBytes
                            ) is VidVaultResult.Success
                        }
                        ApsVaultType.IMG_VAULT -> {
                            ImgVaultEngine.importImageFromFile(
                                context = context,
                                vaultFile = target.vaultFile,
                                masterKey = target.masterKey,
                                sourceFile = stagingFile,
                                displayName = fileName,
                                mimeType = mimeType,
                                targetFolderId = target.targetFolderId
                            ) is ImgVaultResult.Success
                        }
                    }

                    if (!ingestOk) {
                        writeJsonFrame(
                            dout,
                            JSONObject().put("status", "ITEM_ERR").put("message", "Vault ingest failed for $fileName (check vault capacity).")
                        )
                        throw IllegalStateException("Vault ingest failed for $fileName")
                    }

                    receivedCount++
                    writeJsonFrame(dout, JSONObject().put("status", "ITEM_OK"))
                } finally {
                    // Zero plaintext footprint: immediately wipe staging file after vault ingestion
                    try { stagingFile.delete() } catch (_: Throwable) {}
                }
            }

            ActivityLogManager.log(
                context,
                "APS_RECEIVED",
                "APS Vault-to-Vault Received ($receivedCount)",
                "Received $receivedCount file(s) directly into ${target.vaultType.displayName} from $senderProfileName ($senderDeviceId).",
                severity = "SUCCESS"
            )
            ApsTransferHistoryManager.record(
                context,
                ApsTransferHistoryEntry(
                    direction = "RECEIVED",
                    vaultType = target.vaultType.wireCode,
                    transport = transport.name,
                    status = "COMPLETED",
                    peerDeviceId = senderDeviceId,
                    peerProfileName = senderProfileName,
                    peerDeviceModel = senderModel,
                    peerAvatarBase64 = senderAvatar,
                    fileCount = receivedCount,
                    fileNames = receivedFileNames,
                    totalBytes = totalReceivedBytes,
                    durationMs = (System.currentTimeMillis() - transferStartMs).coerceAtLeast(1L),
                    securityCode = activeSasCode
                )
            )
            historyLogged = true

            engineScope.launch(Dispatchers.Main) {
                try { target.onIngestComplete() } catch (_: Throwable) {}
            }

            _state.update {
                it.copy(
                    phase = ApsTransferPhase.COMPLETED,
                    transferredBytes = totalReceivedBytes,
                    lastCompletedSummary = "Received $receivedCount file(s) directly into ${target.vaultType.displayName} from $senderProfileName!",
                    statusMessage = "Transfer complete ($receivedCount file(s) secured in vault)"
                )
            }
        } catch (e: Throwable) {
            if (!historyLogged) {
                ApsTransferHistoryManager.record(
                    context,
                    ApsTransferHistoryEntry(
                        direction = "RECEIVED",
                        vaultType = resolvedVaultType.wireCode,
                        transport = transport.name,
                        status = "FAILED",
                        peerDeviceId = senderDeviceId,
                        peerProfileName = senderProfileName,
                        peerDeviceModel = senderModel,
                        peerAvatarBase64 = senderAvatar,
                        fileCount = expectedItems,
                        fileNames = receivedFileNames,
                        totalBytes = expectedBytes,
                        securityCode = activeSasCode,
                        errorMessage = e.message ?: "Incoming APS transfer failed."
                    )
                )
            }
            _state.update {
                it.copy(
                    phase = ApsTransferPhase.FAILED,
                    pendingIncomingRequest = null,
                    errorMessage = e.message ?: "Incoming APS transfer failed."
                )
            }
        } finally {
            try { sessionKey?.encoded?.fill(0) } catch (_: Throwable) {}
            stagingDir.listFiles()?.forEach { try { it.delete() } catch (_: Throwable) {} }
            closeConn()
        }
    }

    // ---- Outgoing Transfer (Sender Side — Wi-Fi TCP or Bluetooth RFCOMM) ----

    fun sendItemsToPeer(
        context: Context,
        peer: ApsPeerDevice,
        vaultType: ApsVaultType,
        items: List<ApsTransferItem>,
        onComplete: ((Boolean, String) -> Unit)? = null
    ) {
        if (items.isEmpty()) return

        // Pre-flight mismatch guard if peer's advertised vaultType is already known and mismatched
        if (peer.vaultType != null && peer.vaultType != vaultType) {
            val msg = "Connection failed: ${vaultType.displayName} files can only transfer to ${vaultType.displayName} " +
                "(${peer.profileName} is running ${peer.vaultType.displayName} APS)."
            _state.update {
                it.copy(
                    phase = ApsTransferPhase.FAILED,
                    errorMessage = msg,
                    statusMessage = "Vault type mismatch blocked."
                )
            }
            onComplete?.invoke(false, msg)
            return
        }

        transferJob?.cancel()
        transferJob = engineScope.launch {
            val result = executeSendToPeerSuspend(context.applicationContext, peer, vaultType, items)
            withContext(Dispatchers.Main) {
                onComplete?.invoke(result.first, result.second)
            }
        }
    }

    @SuppressLint("MissingPermission")
    internal suspend fun executeSendToPeerSuspend(
        context: Context,
        peer: ApsPeerDevice,
        vaultType: ApsVaultType,
        items: List<ApsTransferItem>
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        var tcpSocket: Socket? = null
        var btSocket: BluetoothSocket? = null
        val totalBytes = items.sumOf { it.sizeBytes }
        val fileNamesList = items.map { it.name }
        var resolvedPeerName = peer.profileName
        var resolvedPeerId = peer.deviceId
        var resolvedPeerModel = peer.deviceModel
        var resolvedPeerAvatar = peer.avatarBase64
        var resolvedSasCode = ""
        var historyLogged = false
        val sendStartMs = System.currentTimeMillis()

        try {
            val myAvatar = getLocalAvatarSnippet()
            _state.update {
                it.copy(
                    phase = ApsTransferPhase.CONNECTING,
                    errorMessage = null,
                    lastCompletedSummary = null,
                    securityCode = "",
                    localAvatarBase64 = myAvatar,
                    activePeerDeviceId = peer.deviceId,
                    activePeerProfileName = peer.profileName,
                    activePeerDeviceModel = peer.deviceModel,
                    activePeerAvatarBase64 = peer.avatarBase64,
                    activeTransport = peer.transport,
                    isSendingActive = true,
                    totalFiles = items.size,
                    currentFileIndex = 0,
                    currentFileName = "",
                    transferredBytes = 0L,
                    totalBytes = totalBytes,
                    speedBytesPerSec = 0L,
                    statusMessage = "Connecting to ${peer.profileName} via ${peer.transport.label}…"
                )
            }

            val (input, output) = when (peer.transport) {
                ApsTransportType.WIFI_LAN -> {
                    val sock = Socket()
                    sock.connect(InetSocketAddress(peer.hostAddress, peer.port), 10_000)
                    sock.tcpNoDelay = true
                    tcpSocket = sock
                    activeClientSocket = sock
                    BufferedInputStream(sock.getInputStream(), STREAM_BUFFER_SIZE) to
                        BufferedOutputStream(sock.getOutputStream(), STREAM_BUFFER_SIZE)
                }
                ApsTransportType.BLUETOOTH -> {
                    if (!hasBluetoothPermissions(context)) {
                        throw SecurityException("Bluetooth permission is required to transfer over Bluetooth.")
                    }
                    val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
                        ?: throw IllegalStateException("Bluetooth is not available on this device.")
                    val adapter = btManager.adapter
                        ?: throw IllegalStateException("Bluetooth adapter unavailable.")
                    val remote = adapter.getRemoteDevice(peer.bluetoothAddress)
                    val sock = remote.createRfcommSocketToServiceRecord(APS_BT_UUID)
                    try { adapter.cancelDiscovery() } catch (_: Throwable) {}
                    sock.connect()
                    btSocket = sock
                    activeBtSocket = sock
                    BufferedInputStream(sock.inputStream, STREAM_BUFFER_SIZE) to
                        BufferedOutputStream(sock.outputStream, STREAM_BUFFER_SIZE)
                }
            }

            val din = DataInputStream(input)
            val dout = DataOutputStream(output)

            val myEcPair = generateEphemeralEcKeyPair()
            val (myId, myName, myModel) = getLocalIdentity(context)

            // Handshake Step 1: Send APS1 magic + metadata + vaultType + ECDH public key
            dout.write(PROTOCOL_MAGIC.toByteArray(StandardCharsets.US_ASCII))
            writeJsonFrame(
                dout,
                JSONObject()
                    .put("vaultType", vaultType.wireCode)
                    .put("deviceId", myId)
                    .put("profileName", myName)
                    .put("deviceModel", myModel)
                    .put("avatarBase64", getLocalAvatarSnippet())
                    .put("itemCount", items.size)
                    .put("totalBytes", totalBytes)
                    .put("ecdhPubKey", Base64.encodeToString(myEcPair.public.encoded, Base64.NO_WRAP))
            )

            // Handshake Step 2: Read Receiver's Vault-Type validation & ECDH public key
            val reply = readJsonFrame(din)
            val replyStatus = reply.optString("status")
            if (replyStatus != "OK_VERIFY") {
                val errMsg = reply.optString("message", "Receiver rejected connection ($replyStatus).")
                ApsTransferHistoryManager.record(
                    context,
                    ApsTransferHistoryEntry(
                        direction = "SENT",
                        vaultType = vaultType.wireCode,
                        transport = peer.transport.name,
                        status = if (replyStatus == ERR_VAULT_TYPE_MISMATCH) "MISMATCH" else "FAILED",
                        peerDeviceId = resolvedPeerId,
                        peerProfileName = resolvedPeerName,
                        peerDeviceModel = resolvedPeerModel,
                        peerAvatarBase64 = resolvedPeerAvatar,
                        fileCount = items.size,
                        fileNames = fileNamesList,
                        totalBytes = totalBytes,
                        errorMessage = errMsg
                    )
                )
                historyLogged = true
                _state.update {
                    it.copy(
                        phase = ApsTransferPhase.FAILED,
                        errorMessage = errMsg,
                        statusMessage = "Transfer failed"
                    )
                }
                return@withContext Pair(false, errMsg)
            }

            // Double-check receiver's declared vaultType matches sender's vaultType!
            val receiverVaultType = ApsVaultType.fromWireCode(reply.optString("vaultType"))
            if (receiverVaultType != vaultType) {
                val errMsg = "Connection failed: Receiver vault type mismatch."
                ApsTransferHistoryManager.record(
                    context,
                    ApsTransferHistoryEntry(
                        direction = "SENT",
                        vaultType = vaultType.wireCode,
                        transport = peer.transport.name,
                        status = "MISMATCH",
                        peerDeviceId = resolvedPeerId,
                        peerProfileName = resolvedPeerName,
                        peerDeviceModel = resolvedPeerModel,
                        peerAvatarBase64 = resolvedPeerAvatar,
                        fileCount = items.size,
                        fileNames = fileNamesList,
                        totalBytes = totalBytes,
                        errorMessage = errMsg
                    )
                )
                historyLogged = true
                _state.update { it.copy(phase = ApsTransferPhase.FAILED, errorMessage = errMsg) }
                return@withContext Pair(false, errMsg)
            }

            val peerPubBytes = Base64.decode(reply.optString("ecdhPubKey", ""), Base64.NO_WRAP)
            val (sessionKey, sasCode) = deriveSessionKeyAndSas(myEcPair, peerPubBytes, vaultType)
            resolvedSasCode = sasCode
            resolvedPeerName = reply.optString("profileName", "").ifBlank { peer.profileName }
            resolvedPeerId = reply.optString("deviceId", "").ifBlank { peer.deviceId }
            resolvedPeerModel = reply.optString("deviceModel", "").ifBlank { peer.deviceModel }
            resolvedPeerAvatar = reply.optString("avatarBase64", "").ifBlank { peer.avatarBase64 }

            _state.update {
                it.copy(
                    phase = ApsTransferPhase.AWAITING_APPROVAL,
                    securityCode = sasCode,
                    activePeerDeviceId = resolvedPeerId,
                    activePeerProfileName = resolvedPeerName,
                    activePeerDeviceModel = resolvedPeerModel,
                    activePeerAvatarBase64 = resolvedPeerAvatar,
                    statusMessage = "Verify code $sasCode on $resolvedPeerName's screen and tap Accept…"
                )
            }

            val approval = readJsonFrame(din)
            if (approval.optString("status") != "APPROVED") {
                val msg = approval.optString("message", "Transfer declined by receiver.")
                ApsTransferHistoryManager.record(
                    context,
                    ApsTransferHistoryEntry(
                        direction = "SENT",
                        vaultType = vaultType.wireCode,
                        transport = peer.transport.name,
                        status = "REJECTED",
                        peerDeviceId = resolvedPeerId,
                        peerProfileName = resolvedPeerName,
                        peerDeviceModel = resolvedPeerModel,
                        peerAvatarBase64 = resolvedPeerAvatar,
                        fileCount = items.size,
                        fileNames = fileNamesList,
                        totalBytes = totalBytes,
                        securityCode = sasCode,
                        errorMessage = msg
                    )
                )
                historyLogged = true
                _state.update { it.copy(phase = ApsTransferPhase.FAILED, errorMessage = msg) }
                return@withContext Pair(false, msg)
            }

            // Step 3: Stream each file directly from Sender's Vault -> AES-256-CTR -> Socket (64-256 KB chunks!)
            _state.update {
                it.copy(
                    phase = ApsTransferPhase.TRANSFERRING,
                    statusMessage = "Streaming encrypted ${vaultType.displayName} data…"
                )
            }

            val startTime = System.currentTimeMillis()
            var totalSent = 0L

            items.forEachIndexed { idx, item ->
                _state.update {
                    it.copy(
                        currentFileIndex = idx + 1,
                        currentFileName = item.name,
                        statusMessage = "Sending (${idx + 1}/${items.size}): ${item.name}"
                    )
                }

                val ivBytes = ByteArray(16).also { secureRandom.nextBytes(it) }
                val thumbBytes = try {
                    item.thumbnailProvider?.invoke()?.takeIf { it.isNotEmpty() && it.size <= 96_000 }
                } catch (_: Throwable) { null }
                val itemMetaJson = JSONObject()
                    .put("name", item.name)
                    .put("sizeBytes", item.sizeBytes)
                    .put("mimeType", item.mimeType)
                    .put("durationMs", item.durationMs)
                    .put("ivB64", Base64.encodeToString(ivBytes, Base64.NO_WRAP))
                if (thumbBytes != null) {
                    itemMetaJson.put("thumbB64", Base64.encodeToString(thumbBytes, Base64.NO_WRAP))
                }
                writeJsonFrame(dout, itemMetaJson)

                val cipher = Cipher.getInstance("AES/CTR/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, sessionKey, IvParameterSpec(ivBytes))
                val sha256 = MessageDigest.getInstance("SHA-256")
                val configuredSendChunk = (SettingsManager.settings.value.apsTransferBufferKb.coerceIn(64, 256)) * 1024
                val cipherBuf = ByteArray(configuredSendChunk + 32)
                var lastUiUpdate = 0L

                val encryptingPipe = object : OutputStream() {
                    private val singleByte = ByteArray(1)
                    override fun write(b: Int) {
                        singleByte[0] = b.toByte()
                        write(singleByte, 0, 1)
                    }

                    override fun write(b: ByteArray, off: Int, len: Int) {
                        if (len <= 0) return
                        sha256.update(b, off, len)
                        var offset = off
                        var rem = len
                        while (rem > 0) {
                            val step = minOf(rem, configuredSendChunk)
                            val encLen = cipher.update(b, offset, step, cipherBuf, 0)
                            if (encLen > 0) {
                                dout.writeInt(encLen)
                                dout.write(cipherBuf, 0, encLen)
                                totalSent += encLen
                            }
                            offset += step
                            rem -= step
                        }
                        val now = System.currentTimeMillis()
                        if (now - lastUiUpdate > 200L) {
                            lastUiUpdate = now
                            val elapsedSec = ((now - startTime) / 1000.0).coerceAtLeast(0.1)
                            _state.update {
                                it.copy(
                                    transferredBytes = totalSent,
                                    speedBytesPerSec = (totalSent / elapsedSec).toLong()
                                )
                            }
                        }
                    }
                }

                val streamOk = item.streamPlaintextTo(encryptingPipe)
                if (!streamOk) {
                    throw IllegalStateException("Failed to stream '${item.name}' from local vault.")
                }

                val finalEnc = cipher.doFinal()
                if (finalEnc != null && finalEnc.isNotEmpty()) {
                    dout.writeInt(finalEnc.size)
                    dout.write(finalEnc)
                    totalSent += finalEnc.size
                }

                // Chunked stream EOF sentinel (0 length), followed by 32-byte SHA-256 integrity hash
                dout.writeInt(0)
                dout.write(sha256.digest())
                dout.flush()

                val ack = readJsonFrame(din)
                if (ack.optString("status") != "ITEM_OK") {
                    val err = ack.optString("message", "Receiver reported error on ${item.name}")
                    throw IllegalStateException(err)
                }
            }

            try { sessionKey.encoded.fill(0) } catch (_: Throwable) {}

            val summary = "Sent ${items.size} ${vaultType.displayName} file(s) directly to $resolvedPeerName's Vault!"
            ActivityLogManager.log(
                context,
                "APS_SENT",
                "APS Vault-to-Vault Sent (${items.size})",
                "Transferred ${items.size} file(s) from ${vaultType.displayName} to $resolvedPeerName ($resolvedPeerId).",
                severity = "SUCCESS"
            )
            ApsTransferHistoryManager.record(
                context,
                ApsTransferHistoryEntry(
                    direction = "SENT",
                    vaultType = vaultType.wireCode,
                    transport = peer.transport.name,
                    status = "COMPLETED",
                    peerDeviceId = resolvedPeerId,
                    peerProfileName = resolvedPeerName,
                    peerDeviceModel = resolvedPeerModel,
                    peerAvatarBase64 = resolvedPeerAvatar,
                    fileCount = items.size,
                    fileNames = fileNamesList,
                    totalBytes = totalSent,
                    durationMs = (System.currentTimeMillis() - sendStartMs).coerceAtLeast(1L),
                    securityCode = resolvedSasCode
                )
            )
            historyLogged = true
            _state.update {
                it.copy(
                    phase = ApsTransferPhase.COMPLETED,
                    transferredBytes = totalSent,
                    lastCompletedSummary = summary,
                    statusMessage = "Transfer complete!"
                )
            }
            Pair(true, summary)
        } catch (e: Throwable) {
            val msg = e.message ?: "APS connection failed."
            if (!historyLogged) {
                ApsTransferHistoryManager.record(
                    context,
                    ApsTransferHistoryEntry(
                        direction = "SENT",
                        vaultType = vaultType.wireCode,
                        transport = peer.transport.name,
                        status = "FAILED",
                        peerDeviceId = resolvedPeerId,
                        peerProfileName = resolvedPeerName,
                        peerDeviceModel = resolvedPeerModel,
                        peerAvatarBase64 = resolvedPeerAvatar,
                        fileCount = items.size,
                        fileNames = fileNamesList,
                        totalBytes = totalBytes,
                        securityCode = resolvedSasCode,
                        errorMessage = msg
                    )
                )
            }
            _state.update {
                it.copy(
                    phase = ApsTransferPhase.FAILED,
                    errorMessage = msg,
                    statusMessage = "Transfer failed"
                )
            }
            Pair(false, msg)
        } finally {
            try { tcpSocket?.close() } catch (_: Throwable) {}
            try { btSocket?.close() } catch (_: Throwable) {}
            activeClientSocket = null
            activeBtSocket = null
        }
    }

    fun cancelActiveTransfer() {
        transferJob?.cancel()
        transferJob = null
        pendingApprovalDeferred?.complete(false)
        pendingApprovalDeferred = null
        try { activeClientSocket?.close() } catch (_: Throwable) {}
        try { activeBtSocket?.close() } catch (_: Throwable) {}
        activeClientSocket = null
        activeBtSocket = null
        _state.update {
            it.copy(
                phase = ApsTransferPhase.IDLE,
                pendingIncomingRequest = null,
                statusMessage = "Transfer cancelled."
            )
        }
    }

    fun resetStatus() {
        _state.update {
            it.copy(
                phase = if (it.isReceiveEnabled) ApsTransferPhase.IDLE else ApsTransferPhase.DISCOVERING,
                errorMessage = null,
                lastCompletedSummary = null,
                securityCode = "",
                transferredBytes = 0L,
                totalBytes = 0L
            )
        }
    }

    // ---- Framed JSON Helpers ----

    private fun writeJsonFrame(dout: DataOutputStream, json: JSONObject) {
        val bytes = json.toString().toByteArray(StandardCharsets.UTF_8)
        dout.writeInt(bytes.size)
        dout.write(bytes)
        dout.flush()
    }

    private fun readJsonFrame(din: DataInputStream): JSONObject {
        val len = din.readInt()
        if (len <= 0 || len > 512 * 1024) {
            throw IllegalStateException("Invalid APS control frame length: $len")
        }
        val bytes = ByteArray(len)
        din.readFully(bytes)
        return JSONObject(String(bytes, StandardCharsets.UTF_8))
    }
}
