package com.example.ui.components

import android.Manifest
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.util.ApsIncomingRequest
import com.example.util.ApsPeerDevice
import com.example.util.ApsReceiverVaultTarget
import com.example.util.ApsTransferEngine
import com.example.util.ApsTransferItem
import com.example.util.ApsTransferPhase
import com.example.util.ApsTransportType
import com.example.util.ApsVaultType
import java.util.Locale

private enum class ApsTransportFilter { ALL, WIFI, BLUETOOTH }

@Composable
fun ApsTransferModal(
    vaultType: ApsVaultType,
    itemsToSend: List<ApsTransferItem>,
    receiverTargetProvider: () -> ApsReceiverVaultTarget?,
    onTransferSuccess: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val apsState by ApsTransferEngine.state.collectAsStateWithLifecycle()
    val isSendMode = itemsToSend.isNotEmpty()

    var transportFilter by remember { mutableStateOf(ApsTransportFilter.ALL) }
    var showManualConnect by remember { mutableStateOf(false) }
    var manualIp by remember { mutableStateOf("") }
    var manualPort by remember { mutableStateOf("") }
    var btPermGranted by remember { mutableStateOf(ApsTransferEngine.hasBluetoothPermissions(context)) }

    val btPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        btPermGranted = results.values.all { it }
        if (btPermGranted) {
            ApsTransferEngine.refreshBluetoothPeers(context)
            // If receive mode is already active, restart receive so Bluetooth RFCOMM listener attaches
            if (apsState.isReceiveEnabled) {
                receiverTargetProvider()?.let { target ->
                    ApsTransferEngine.startReceiveMode(context, target)
                }
            }
        }
    }

    BackHandler {
        if (apsState.phase == ApsTransferPhase.TRANSFERRING || apsState.phase == ApsTransferPhase.CONNECTING) {
            ApsTransferEngine.cancelActiveTransfer()
        } else {
            onClose()
        }
    }

    LaunchedEffect(Unit) {
        ApsTransferEngine.resetStatus()
        ApsTransferEngine.startDiscovery(context)
        if (!isSendMode && !apsState.isReceiveEnabled) {
            receiverTargetProvider()?.let { target ->
                ApsTransferEngine.startReceiveMode(context, target)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            ApsTransferEngine.stopDiscovery()
        }
    }

    val accentColor = if (vaultType == ApsVaultType.VID_VAULT) Color(0xFF00CEC9) else Color(0xFF6C5CE7)
    val accentGradient = if (vaultType == ApsVaultType.VID_VAULT) {
        listOf(Color(0xFF00CEC9), Color(0xFF6C5CE7))
    } else {
        listOf(Color(0xFF6C5CE7), Color(0xFF0984E3))
    }

    val filteredPeers = remember(apsState.discoveredPeers, transportFilter) {
        when (transportFilter) {
            ApsTransportFilter.ALL -> apsState.discoveredPeers
            ApsTransportFilter.WIFI -> apsState.discoveredPeers.filter { it.transport == ApsTransportType.WIFI_LAN }
            ApsTransportFilter.BLUETOOTH -> apsState.discoveredPeers.filter { it.transport == ApsTransportType.BLUETOOTH }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xF50B1020))
            .statusBarsPadding()
            .testTag("aps_transfer_modal_root")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Header
            Surface(
                color = Color(0xFF131A2E),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Brush.linearGradient(accentGradient))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Radar,
                            contentDescription = "APS Vault-to-Vault",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "APS Vault-to-Vault",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = accentColor.copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, accentColor)
                            ) {
                                Text(
                                    text = vaultType.displayName.uppercase(Locale.getDefault()),
                                    color = accentColor,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = if (isSendMode) {
                                "Sending ${itemsToSend.size} item(s) (${formatApsBytes(itemsToSend.sumOf { it.sizeBytes })}) • ECDH + AES-256"
                            } else {
                                "Direct Vault-to-Vault Receive • Zero Gallery Exposure"
                            },
                            color = Color.White.copy(alpha = 0.72f),
                            fontSize = 11.sp
                        )
                    }

                    IconButton(
                        onClick = {
                            if (apsState.phase == ApsTransferPhase.TRANSFERRING) {
                                ApsTransferEngine.cancelActiveTransfer()
                            }
                            onClose()
                        },
                        modifier = Modifier.testTag("aps_modal_close_btn")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close APS", tint = Color.White)
                    }
                }
            }

            HorizontalDivider(color = accentColor.copy(alpha = 0.35f))

            LazyColumn(
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Strict Vault Isolation Security Banner
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = accentColor.copy(alpha = 0.1f),
                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (vaultType == ApsVaultType.VID_VAULT) Icons.Default.Movie else Icons.Default.Image,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (vaultType == ApsVaultType.VID_VAULT) {
                                    "Strict VID Vault Mode: Videos transfer ONLY to another unlocked VID Vault. If the receiver has IMG Vault APS enabled, the connection is automatically rejected."
                                } else {
                                    "Strict IMG Vault Mode: Images transfer ONLY to another unlocked IMG Vault. If the receiver has VID Vault APS enabled, the connection is automatically rejected."
                                },
                                color = Color.White.copy(alpha = 0.88f),
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                // Local Identity & Receive Mode Card
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF161F36),
                        border = BorderStroke(
                            1.dp,
                            if (apsState.isReceiveEnabled) Color(0xFF00B894) else Color.White.copy(alpha = 0.12f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Your Device: ${apsState.localProfileName.ifBlank { "Vault User" }}",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "Device ID: ${apsState.localDeviceId} • ${apsState.localDeviceModel}",
                                        color = Color(0xFF00CEC9),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = if (apsState.isReceiveEnabled) "RECEIVE ON" else "RECEIVE OFF",
                                        color = if (apsState.isReceiveEnabled) Color(0xFF00B894) else Color.White.copy(alpha = 0.5f),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Switch(
                                        checked = apsState.isReceiveEnabled && apsState.activeReceiveVaultType == vaultType,
                                        onCheckedChange = { enabled ->
                                            if (enabled) {
                                                receiverTargetProvider()?.let { target ->
                                                    ApsTransferEngine.startReceiveMode(context, target)
                                                }
                                            } else {
                                                ApsTransferEngine.stopReceiveMode()
                                            }
                                        },
                                        colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF00B894)),
                                        modifier = Modifier.testTag("aps_receive_mode_switch")
                                    )
                                }
                            }

                            if (apsState.isReceiveEnabled && apsState.activeReceiveVaultType == vaultType) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF00B894).copy(alpha = 0.14f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(
                                            text = "Listening for ${vaultType.displayName} APS on:",
                                            color = Color(0xFF00B894),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Wi-Fi / Hotspot: ${apsState.localIpAddress}:${apsState.localTcpPort}" +
                                                if (apsState.isBluetoothListening) "  •  Bluetooth: READY" else "",
                                            color = Color.White,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            } else if (apsState.isReceiveEnabled && apsState.activeReceiveVaultType != vaultType) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Note: Receive Mode is currently active for ${apsState.activeReceiveVaultType?.displayName}. Toggle ON to switch receiver to ${vaultType.displayName}.",
                                    color = Color(0xFFFDCB6E),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                // Incoming Request Approval Card (Receiver Side)
                apsState.pendingIncomingRequest?.let { req ->
                    item {
                        ApsIncomingRequestCard(
                            request = req,
                            onApprove = { ApsTransferEngine.approveIncomingRequest() },
                            onReject = { ApsTransferEngine.rejectIncomingRequest() }
                        )
                    }
                }

                // Live Transfer Progress / Completed / Error Banner
                if (apsState.phase == ApsTransferPhase.CONNECTING ||
                    apsState.phase == ApsTransferPhase.AWAITING_APPROVAL ||
                    apsState.phase == ApsTransferPhase.TRANSFERRING ||
                    apsState.phase == ApsTransferPhase.COMPLETED ||
                    apsState.errorMessage != null
                ) {
                    item {
                        ApsTransferStatusCard(
                            state = apsState,
                            accentColor = accentColor,
                            onCancel = { ApsTransferEngine.cancelActiveTransfer() },
                            onDismissStatus = { ApsTransferEngine.resetStatus() }
                        )
                    }
                }

                // Send Section: Nearby Devices + Bluetooth + Direct IP
                if (isSendMode) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Nearby Devices (Device ID & Profile)",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = { showManualConnect = !showManualConnect }) {
                                    Text(
                                        text = if (showManualConnect) "Hide IP" else "Direct IP",
                                        color = accentColor,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        ApsTransferEngine.startDiscovery(context)
                                        ApsTransferEngine.refreshBluetoothPeers(context)
                                    },
                                    modifier = Modifier.size(34.dp).testTag("aps_refresh_peers_btn")
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Scan Again", tint = accentColor)
                                }
                            }
                        }
                    }

                    // Transport Filter Chips (All / Wi-Fi / Bluetooth)
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = transportFilter == ApsTransportFilter.ALL,
                                onClick = { transportFilter = ApsTransportFilter.ALL },
                                label = { Text("All", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = accentColor,
                                    selectedLabelColor = Color.Black
                                ),
                                modifier = Modifier.testTag("aps_filter_all")
                            )
                            FilterChip(
                                selected = transportFilter == ApsTransportFilter.WIFI,
                                onClick = { transportFilter = ApsTransportFilter.WIFI },
                                leadingIcon = { Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(14.dp)) },
                                label = { Text("Wi-Fi / Hotspot", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = accentColor,
                                    selectedLabelColor = Color.Black
                                ),
                                modifier = Modifier.testTag("aps_filter_wifi")
                            )
                            FilterChip(
                                selected = transportFilter == ApsTransportFilter.BLUETOOTH,
                                onClick = {
                                    transportFilter = ApsTransportFilter.BLUETOOTH
                                    if (!btPermGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                        btPermissionLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.BLUETOOTH_CONNECT,
                                                Manifest.permission.BLUETOOTH_SCAN
                                            )
                                        )
                                    } else {
                                        ApsTransferEngine.refreshBluetoothPeers(context)
                                    }
                                },
                                leadingIcon = { Icon(Icons.Default.Bluetooth, contentDescription = null, modifier = Modifier.size(14.dp)) },
                                label = { Text("Bluetooth", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = accentColor,
                                    selectedLabelColor = Color.Black
                                ),
                                modifier = Modifier.testTag("aps_filter_bt")
                            )
                        }
                    }

                    // Bluetooth Permission Prompt Banner (Android 12+)
                    if (!btPermGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF1F2A44),
                                border = BorderStroke(1.dp, Color(0xFF0984E3).copy(alpha = 0.6f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Bluetooth, contentDescription = null, tint = Color(0xFF74B9FF))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Allow Bluetooth to see & transfer to paired devices offline.",
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontSize = 11.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Button(
                                        onClick = {
                                            btPermissionLauncher.launch(
                                                arrayOf(
                                                    Manifest.permission.BLUETOOTH_CONNECT,
                                                    Manifest.permission.BLUETOOTH_SCAN
                                                )
                                            )
                                        },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.testTag("aps_grant_bt_btn")
                                    ) {
                                        Text("Enable", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }

                    // Direct IP:Port Connect Box
                    if (showManualConnect) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF161F36),
                                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Direct Wi-Fi / Hotspot Connect",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            value = manualIp,
                                            onValueChange = { manualIp = it },
                                            label = { Text("Receiver IP (e.g. 192.168.1.5)", fontSize = 11.sp) },
                                            singleLine = true,
                                            modifier = Modifier.weight(1.5f).testTag("aps_manual_ip")
                                        )
                                        OutlinedTextField(
                                            value = manualPort,
                                            onValueChange = { manualPort = it.filter { ch -> ch.isDigit() } },
                                            label = { Text("Port", fontSize = 11.sp) },
                                            singleLine = true,
                                            modifier = Modifier.weight(0.8f).testTag("aps_manual_port")
                                        )
                                    }
                                    Button(
                                        onClick = {
                                            val portInt = manualPort.toIntOrNull() ?: 0
                                            if (manualIp.isNotBlank() && portInt > 0) {
                                                val manualPeer = ApsPeerDevice(
                                                    idKey = "MANUAL_$manualIp:$portInt",
                                                    deviceId = "$manualIp:$portInt",
                                                    profileName = "Direct Device ($manualIp)",
                                                    deviceModel = "Wi-Fi / Hotspot",
                                                    vaultType = null, // Enforced strictly during handshake!
                                                    transport = ApsTransportType.WIFI_LAN,
                                                    hostAddress = manualIp.trim(),
                                                    port = portInt
                                                )
                                                ApsTransferEngine.sendItemsToPeer(
                                                    context = context,
                                                    peer = manualPeer,
                                                    vaultType = vaultType,
                                                    items = itemsToSend
                                                ) { ok, _ ->
                                                    if (ok) onTransferSuccess()
                                                }
                                            }
                                        },
                                        enabled = manualIp.isNotBlank() && (manualPort.toIntOrNull() ?: 0) > 0 &&
                                            apsState.phase != ApsTransferPhase.TRANSFERRING,
                                        colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = Color.Black),
                                        modifier = Modifier.fillMaxWidth().testTag("aps_manual_send_btn")
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Connect & Send Encrypted", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    // Peer List or Scanning Empty State
                    if (filteredPeers.isEmpty()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFF141B2D),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(24.dp)
                                ) {
                                    CircularProgressIndicator(
                                        color = accentColor,
                                        strokeWidth = 2.5.dp,
                                        modifier = Modifier.size(34.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "Scanning for nearby ${vaultType.displayName} APS devices…",
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "On the receiving phone: unlock ${vaultType.displayName}, tap 'APS Receive', and keep both devices on the same Wi-Fi, Hotspot, or paired via Bluetooth.",
                                        color = Color.White.copy(alpha = 0.65f),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    } else {
                        items(filteredPeers, key = { it.idKey }) { peer ->
                            ApsPeerCard(
                                peer = peer,
                                senderVaultType = vaultType,
                                accentColor = accentColor,
                                isBusy = apsState.phase == ApsTransferPhase.CONNECTING ||
                                    apsState.phase == ApsTransferPhase.AWAITING_APPROVAL ||
                                    apsState.phase == ApsTransferPhase.TRANSFERRING,
                                onSelectPeer = {
                                    ApsTransferEngine.sendItemsToPeer(
                                        context = context,
                                        peer = peer,
                                        vaultType = vaultType,
                                        items = itemsToSend
                                    ) { ok, _ ->
                                        if (ok) onTransferSuccess()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ApsPeerCard(
    peer: ApsPeerDevice,
    senderVaultType: ApsVaultType,
    accentColor: Color,
    isBusy: Boolean,
    onSelectPeer: () -> Unit
) {
    val isMismatch = peer.vaultType != null && peer.vaultType != senderVaultType
    val borderColor = when {
        isMismatch -> Color(0xFFFF7675).copy(alpha = 0.75f)
        peer.vaultType == senderVaultType -> Color(0xFF00B894).copy(alpha = 0.75f)
        else -> accentColor.copy(alpha = 0.45f)
    }

    val avatarBitmap = remember(peer.avatarBase64) {
        if (peer.avatarBase64.isNotBlank()) {
            try {
                val bytes = Base64.decode(peer.avatarBase64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            } catch (_: Throwable) { null }
        } else null
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF161F36),
        border = BorderStroke(1.2.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isBusy, onClick = onSelectPeer)
            .testTag("aps_peer_card_${peer.deviceId}")
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(borderColor.copy(alpha = 0.2f))
            ) {
                if (avatarBitmap != null) {
                    Image(
                        bitmap = avatarBitmap,
                        contentDescription = peer.profileName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = peer.profileName,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = peer.profileName,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "ID: ${peer.deviceId} • ${peer.deviceModel}",
                    color = Color.White.copy(alpha = 0.7f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        shape = RoundedCornerShape(5.dp),
                        color = Color.White.copy(alpha = 0.1f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = if (peer.transport == ApsTransportType.WIFI_LAN) Icons.Default.Wifi else Icons.Default.Bluetooth,
                                contentDescription = null,
                                tint = Color(0xFF74B9FF),
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = peer.transport.label,
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    val (vtText, vtColor) = when {
                        isMismatch -> "${peer.vaultType!!.displayName} (Mismatch)" to Color(0xFFFF7675)
                        peer.vaultType == senderVaultType -> "${peer.vaultType.displayName} Ready" to Color(0xFF00B894)
                        else -> "Verify on Connect" to Color(0xFFFDCB6E)
                    }
                    Surface(
                        shape = RoundedCornerShape(5.dp),
                        color = vtColor.copy(alpha = 0.18f)
                    ) {
                        Text(
                            text = vtText,
                            color = vtColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = onSelectPeer,
                enabled = !isBusy,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isMismatch) Color(0xFFFF7675) else accentColor,
                    contentColor = if (isMismatch) Color.White else Color.Black
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("aps_send_to_${peer.deviceId}")
            ) {
                Text(
                    text = if (isMismatch) "Blocked" else "Send",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ApsIncomingRequestCard(
    request: ApsIncomingRequest,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1A2540),
        border = BorderStroke(2.dp, Color(0xFF00CEC9)),
        modifier = Modifier.fillMaxWidth().testTag("aps_incoming_request_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Shield, contentDescription = null, tint = Color(0xFF00CEC9))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Incoming ${request.vaultType.displayName} APS Transfer",
                    color = Color(0xFF00CEC9),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
            Text(
                text = "From: ${request.senderProfileName} (ID: ${request.senderDeviceId} • ${request.senderDeviceModel})",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${request.itemCount} encrypted file(s) • ${formatApsBytes(request.totalBytes)} • via ${request.transport.label}",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 12.sp
            )

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF0E1525),
                border = BorderStroke(1.dp, Color(0xFF00CEC9).copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text("VERIFY 6-DIGIT SECURITY CODE", color = Color.White.copy(alpha = 0.65f), fontSize = 10.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = request.securityCode.chunked(3).joinToString(" "),
                        color = Color(0xFF00CEC9),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 26.sp,
                        letterSpacing = 3.sp
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onReject,
                    border = BorderStroke(1.dp, Color(0xFFFF7675)),
                    modifier = Modifier.weight(1f).testTag("aps_reject_incoming_btn")
                ) {
                    Text("Reject", color = Color(0xFFFF7675), fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onApprove,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00B894), contentColor = Color.Black),
                    modifier = Modifier.weight(1.3f).testTag("aps_accept_incoming_btn")
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Accept into Vault", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ApsTransferStatusCard(
    state: com.example.util.ApsUiState,
    accentColor: Color,
    onCancel: () -> Unit,
    onDismissStatus: () -> Unit
) {
    val isError = state.phase == ApsTransferPhase.FAILED || state.errorMessage != null
    val isDone = state.phase == ApsTransferPhase.COMPLETED
    val borderColor = when {
        isError -> Color(0xFFFF7675)
        isDone -> Color(0xFF00B894)
        else -> accentColor
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF161F36),
        border = BorderStroke(1.5.dp, borderColor),
        modifier = Modifier.fillMaxWidth().testTag("aps_status_card")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = when {
                        isError -> Icons.Default.ErrorOutline
                        isDone -> Icons.Default.CheckCircle
                        else -> Icons.Default.Security
                    },
                    contentDescription = null,
                    tint = borderColor
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when {
                        isError -> "APS Transfer Failed"
                        isDone -> "Vault-to-Vault Complete"
                        state.phase == ApsTransferPhase.AWAITING_APPROVAL -> "Awaiting Receiver Approval"
                        else -> "Encrypted Vault-to-Vault Streaming"
                    },
                    color = borderColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f)
                )
                if (isError || isDone) {
                    TextButton(onClick = onDismissStatus) {
                        Text("Dismiss", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                    }
                }
            }

            if (state.securityCode.isNotBlank() && !isError) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF0E1525),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("ECDH Security Code:", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                        Text(
                            text = state.securityCode.chunked(3).joinToString(" "),
                            color = accentColor,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp
                        )
                    }
                }
            }

            Text(
                text = state.errorMessage ?: state.lastCompletedSummary ?: state.statusMessage,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 12.sp
            )

            if (state.phase == ApsTransferPhase.TRANSFERRING) {
                val frac = if (state.totalBytes > 0L) {
                    (state.transferredBytes.toFloat() / state.totalBytes.toFloat()).coerceIn(0f, 1f)
                } else 0.1f
                LinearProgressIndicator(
                    progress = { frac },
                    color = accentColor,
                    trackColor = Color.White.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "File ${state.currentFileIndex}/${state.totalFiles}: ${state.currentFileName}",
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${formatApsBytes(state.speedBytesPerSec)}/s",
                        color = accentColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }

            if (state.phase == ApsTransferPhase.CONNECTING ||
                state.phase == ApsTransferPhase.AWAITING_APPROVAL ||
                state.phase == ApsTransferPhase.TRANSFERRING
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    border = BorderStroke(1.dp, Color(0xFFFF7675)),
                    modifier = Modifier.fillMaxWidth().testTag("aps_cancel_btn")
                ) {
                    Text("Cancel Transfer", color = Color(0xFFFF7675), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun formatApsBytes(bytes: Long): String {
    val kb = 1024.0
    val mb = kb * 1024.0
    val gb = mb * 1024.0
    return when {
        bytes >= gb -> String.format(Locale.getDefault(), "%.2f GB", bytes / gb)
        bytes >= mb -> String.format(Locale.getDefault(), "%.1f MB", bytes / mb)
        bytes >= kb -> String.format(Locale.getDefault(), "%.0f KB", bytes / kb)
        else -> "$bytes B"
    }
}
