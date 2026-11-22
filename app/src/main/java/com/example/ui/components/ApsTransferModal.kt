package com.example.ui.components

import android.Manifest
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.settings.ApsTransferHistoryDialog
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
    val clipboard = LocalClipboardManager.current
    val apsState by ApsTransferEngine.state.collectAsStateWithLifecycle()
    val settings by com.example.util.SettingsManager.settings.collectAsStateWithLifecycle()
    val isSendMode = itemsToSend.isNotEmpty()

    var transportFilter by remember(settings.apsPreferredTransport) {
        mutableStateOf(
            when (settings.apsPreferredTransport) {
                "WIFI_ONLY" -> ApsTransportFilter.WIFI
                "BLUETOOTH_ONLY" -> ApsTransportFilter.BLUETOOTH
                else -> ApsTransportFilter.ALL
            }
        )
    }
    var showManualConnect by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }
    var manualIp by remember { mutableStateOf("") }
    var manualPort by remember { mutableStateOf("") }
    var btPermGranted by remember { mutableStateOf(ApsTransferEngine.hasBluetoothPermissions(context)) }

    val btPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        btPermGranted = results.values.all { it }
        if (btPermGranted) {
            ApsTransferEngine.refreshBluetoothPeers(context)
        }
    }

    val accent = if (vaultType == ApsVaultType.VID_VAULT) Color(0xFF00CEC9) else Color(0xFF6C5CE7)
    val secondaryAccent = if (vaultType == ApsVaultType.VID_VAULT) Color(0xFF6C5CE7) else Color(0xFF00CEC9)
    val accentContentColor = if (vaultType == ApsVaultType.VID_VAULT) Color.Black else Color.White

    LaunchedEffect(isSendMode, vaultType) {
        if (isSendMode) {
            ApsTransferEngine.resetStatus()
            ApsTransferEngine.startDiscovery(context)
        } else {
            val target = receiverTargetProvider()
            if (target != null && (!apsState.isReceiveEnabled || apsState.activeReceiveVaultType != vaultType)) {
                ApsTransferEngine.startReceiveMode(context, target)
            }
        }
    }

    DisposableEffect(isSendMode) {
        onDispose {
            if (isSendMode) {
                ApsTransferEngine.stopDiscovery()
            }
        }
    }

    BackHandler {
        if (apsState.phase == ApsTransferPhase.TRANSFERRING || apsState.phase == ApsTransferPhase.CONNECTING) {
            ApsTransferEngine.cancelActiveTransfer()
        }
        onClose()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.78f))
            .navigationBarsPadding()
            .imePadding()
            .clickable(enabled = false) {}
            .padding(horizontal = 10.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 640.dp)
                .fillMaxHeight(0.92f)
        ) {
            val isCompact = maxWidth < 365.dp || settings.uiLayoutScaleMode == "COMPACT"

            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.5.dp, accent.copy(alpha = 0.65f)),
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("aps_transfer_modal")
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // ===== TOP HERO HEADER =====
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        accent.copy(alpha = 0.24f),
                                        secondaryAccent.copy(alpha = 0.16f)
                                    )
                                )
                            )
                            .padding(horizontal = if (isCompact) 12.dp else 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                ApsProfileAvatar(
                                    avatarBase64 = apsState.localAvatarBase64.ifBlank { ApsTransferEngine.getLocalAvatarSnippet() },
                                    name = apsState.localProfileName.ifBlank { "You" },
                                    size = if (isCompact) 40.dp else 46.dp,
                                    ringColor = accent,
                                    showOnlineDot = apsState.isReceiveEnabled
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = if (isSendMode) "APS Direct Send" else "APS Vault Receive",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = if (isCompact) 14.sp else 16.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = accent.copy(alpha = 0.22f)
                                        ) {
                                            Text(
                                                text = if (vaultType == ApsVaultType.VID_VAULT) "VID" else "IMG",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = accent,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "${apsState.localProfileName.ifBlank { "Vault User" }} • ${apsState.localDeviceId.ifBlank { "APS" }}",
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                    }
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { showHistoryDialog = true },
                                    modifier = Modifier.testTag("aps_open_history_btn")
                                ) {
                                    Icon(
                                        Icons.Default.History,
                                        contentDescription = "APS Transfer History",
                                        tint = accent
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        if (apsState.phase == ApsTransferPhase.TRANSFERRING || apsState.phase == ApsTransferPhase.CONNECTING) {
                                            ApsTransferEngine.cancelActiveTransfer()
                                        }
                                        onClose()
                                    },
                                    modifier = Modifier.testTag("aps_modal_close_btn")
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Close APS")
                                }
                            }
                        }
                    }

                    // Strict Vault Isolation Pill Bar
                    Surface(
                        color = accent.copy(alpha = 0.1f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Strict ${vaultType.displayName} ↔ ${vaultType.displayName} • ECDH P-256 + AES-256-CTR",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    // ===== SCROLLABLE BODY =====
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = if (isCompact) 12.dp else 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val incoming = apsState.pendingIncomingRequest
                        val myAvatarB64 = apsState.localAvatarBase64.ifBlank { ApsTransferEngine.getLocalAvatarSnippet() }
                        val myName = apsState.localProfileName.ifBlank { "You" }
                        val myId = apsState.localDeviceId

                        when {
                            // 1) RECEIVER INCOMING APPROVAL PROMPT (Shows BOTH Profile Photos!)
                            incoming != null && incoming.vaultType == vaultType -> {
                                IncomingApprovalView(
                                    request = incoming,
                                    myName = myName,
                                    myDeviceId = myId,
                                    myAvatarBase64 = myAvatarB64,
                                    accent = accent,
                                    isCompact = isCompact,
                                    onAccept = { ApsTransferEngine.approveIncomingRequest() },
                                    onReject = { ApsTransferEngine.rejectIncomingRequest() }
                                )
                            }

                            // 2) CONNECTING / SENDER WAITING FOR RECEIVER APPROVAL / TRANSFERRING
                            apsState.phase == ApsTransferPhase.CONNECTING ||
                                apsState.phase == ApsTransferPhase.AWAITING_APPROVAL ||
                                apsState.phase == ApsTransferPhase.TRANSFERRING -> {
                                ActiveTransferView(
                                    state = apsState,
                                    vaultType = vaultType,
                                    accent = accent,
                                    secondaryAccent = secondaryAccent,
                                    isCompact = isCompact,
                                    onCancel = { ApsTransferEngine.cancelActiveTransfer() }
                                )
                            }

                            // 3) COMPLETED TRANSFER VIEW (Shows BOTH Profile Photos!)
                            apsState.phase == ApsTransferPhase.COMPLETED -> {
                                CompletedTransferView(
                                    state = apsState,
                                    accent = accent,
                                    isCompact = isCompact,
                                    onViewHistory = { showHistoryDialog = true },
                                    onDone = {
                                        ApsTransferEngine.resetStatus()
                                        onTransferSuccess()
                                        onClose()
                                    }
                                )
                            }

                            // 4) FAILED / VAULT MISMATCH VIEW
                            apsState.phase == ApsTransferPhase.FAILED && !apsState.errorMessage.isNullOrBlank() -> {
                                FailedTransferView(
                                    errorMessage = apsState.errorMessage ?: "APS transfer failed.",
                                    accent = accent,
                                    accentContentColor = accentContentColor,
                                    onRetry = {
                                        ApsTransferEngine.resetStatus()
                                        if (isSendMode) {
                                            ApsTransferEngine.startDiscovery(context)
                                        }
                                    }
                                )
                            }

                            // 5) SEND MODE: PEER DISCOVERY LIST WITH RECEIVER PROFILE PHOTOS
                            isSendMode -> {
                                val totalSize = remember(itemsToSend) { itemsToSend.sumOf { it.sizeBytes } }

                                // Selected Payload Summary Card
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = accent.copy(alpha = 0.12f),
                                    border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(accent.copy(alpha = 0.22f))
                                        ) {
                                            Icon(
                                                imageVector = if (vaultType == ApsVaultType.VID_VAULT) Icons.Default.Movie else Icons.Default.Image,
                                                contentDescription = null,
                                                tint = accent,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Ready to send ${itemsToSend.size} ${vaultType.displayName} file(s)",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${formatApsBytes(totalSize)} • Streams directly Vault → Vault (Ultra-Low RAM)",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }

                                // Transport Filter Segmented Row + Refresh
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    listOf(
                                        ApsTransportFilter.ALL to "All",
                                        ApsTransportFilter.WIFI to "Wi-Fi",
                                        ApsTransportFilter.BLUETOOTH to "Bluetooth"
                                    ).forEach { (filter, label) ->
                                        val selected = transportFilter == filter
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (selected) accent else MaterialTheme.colorScheme.surfaceVariant,
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(10.dp))
                                                .clickable {
                                                    transportFilter = filter
                                                    if (filter == ApsTransportFilter.BLUETOOTH && !btPermGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                                        btPermissionLauncher.launch(
                                                            arrayOf(
                                                                Manifest.permission.BLUETOOTH_CONNECT,
                                                                Manifest.permission.BLUETOOTH_SCAN
                                                            )
                                                        )
                                                    }
                                                }
                                                .testTag("aps_filter_${filter.name.lowercase()}")
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier.padding(vertical = 9.dp)
                                            ) {
                                                Text(
                                                    text = label,
                                                    fontSize = 11.sp,
                                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (selected) accentContentColor else MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                    IconButton(
                                        onClick = { ApsTransferEngine.startDiscovery(context) },
                                        modifier = Modifier.size(40.dp).testTag("aps_refresh_peers_btn")
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = "Rescan Devices", tint = accent)
                                    }
                                }

                                if (!btPermGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    OutlinedButton(
                                        onClick = {
                                            btPermissionLauncher.launch(
                                                arrayOf(
                                                    Manifest.permission.BLUETOOTH_CONNECT,
                                                    Manifest.permission.BLUETOOTH_SCAN
                                                )
                                            )
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth().testTag("aps_bt_perm_btn")
                                    ) {
                                        Icon(Icons.Default.Bluetooth, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Enable Bluetooth Device Discovery", fontSize = 12.sp)
                                    }
                                }

                                val filteredPeers = apsState.discoveredPeers.filter { peer ->
                                    when (transportFilter) {
                                        ApsTransportFilter.ALL -> true
                                        ApsTransportFilter.WIFI -> peer.transport == ApsTransportType.WIFI_LAN
                                        ApsTransportFilter.BLUETOOTH -> peer.transport == ApsTransportType.BLUETOOTH
                                    }
                                }

                                if (filteredPeers.isEmpty()) {
                                    // Animated Radar Searching Card
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                        border = BorderStroke(1.dp, accent.copy(alpha = 0.28f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.padding(22.dp)
                                        ) {
                                            PulsingRadarAvatar(
                                                avatarBase64 = myAvatarB64,
                                                name = myName,
                                                accent = accent
                                            )
                                            Spacer(modifier = Modifier.height(14.dp))
                                            Text(
                                                text = "Scanning for Nearby ${vaultType.displayName} Devices…",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                textAlign = TextAlign.Center
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Open ${vaultType.displayName} on your other mobile and tap 'APS' to turn on Receive Mode on the same Wi-Fi, Hotspot, or Bluetooth.",
                                                fontSize = 11.sp,
                                                textAlign = TextAlign.Center,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                } else {
                                    Text(
                                        text = "NEARBY DEVICES (${filteredPeers.size})",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    filteredPeers.forEach { peer ->
                                        ApsPeerCard(
                                            peer = peer,
                                            senderVaultType = vaultType,
                                            accent = accent,
                                            accentContentColor = accentContentColor,
                                            onSelect = {
                                                ApsTransferEngine.sendItemsToPeer(
                                                    context = context,
                                                    peer = peer,
                                                    vaultType = vaultType,
                                                    items = itemsToSend
                                                )
                                            }
                                        )
                                    }
                                }

                                // Manual Direct Wi-Fi IP:Port Fallback
                                TextButton(
                                    onClick = { showManualConnect = !showManualConnect },
                                    modifier = Modifier.align(Alignment.CenterHorizontally).testTag("aps_manual_toggle_btn")
                                ) {
                                    Icon(Icons.Default.Lan, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (showManualConnect) "Hide Direct IP:Port Connect" else "Can't see device? Connect by IP:Port",
                                        fontSize = 12.sp
                                    )
                                }

                                if (showManualConnect) {
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text("Direct Wi-Fi / Hotspot Connect", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                OutlinedTextField(
                                                    value = manualIp,
                                                    onValueChange = { manualIp = it },
                                                    label = { Text("Receiver IP", fontSize = 11.sp) },
                                                    placeholder = { Text("192.168.1.25", fontSize = 11.sp) },
                                                    singleLine = true,
                                                    modifier = Modifier.weight(1.5f).testTag("aps_manual_ip_input")
                                                )
                                                OutlinedTextField(
                                                    value = manualPort,
                                                    onValueChange = { manualPort = it.filter { ch -> ch.isDigit() } },
                                                    label = { Text("Port", fontSize = 11.sp) },
                                                    placeholder = { Text("45123", fontSize = 11.sp) },
                                                    singleLine = true,
                                                    modifier = Modifier.weight(1f).testTag("aps_manual_port_input")
                                                )
                                            }
                                            Button(
                                                onClick = {
                                                    val portNum = manualPort.toIntOrNull() ?: 0
                                                    if (manualIp.isNotBlank() && portNum in 1..65535) {
                                                        val manualPeer = ApsPeerDevice(
                                                            idKey = "MANUAL_${manualIp}_$portNum",
                                                            deviceId = "MANUAL",
                                                            profileName = "Direct ($manualIp)",
                                                            deviceModel = "Direct Wi-Fi Target",
                                                            vaultType = null,
                                                            transport = ApsTransportType.WIFI_LAN,
                                                            hostAddress = manualIp.trim(),
                                                            port = portNum
                                                        )
                                                        ApsTransferEngine.sendItemsToPeer(
                                                            context = context,
                                                            peer = manualPeer,
                                                            vaultType = vaultType,
                                                            items = itemsToSend
                                                        )
                                                    } else {
                                                        Toast.makeText(context, "Enter a valid Receiver IP and Port", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = accentContentColor),
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier.fillMaxWidth().height(46.dp).testTag("aps_manual_connect_btn")
                                            ) {
                                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Connect & Send Securely", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            }

                            // 6) RECEIVE MODE DASHBOARD (Shows Receiver's Own Profile Photo in Radar Pulse!)
                            else -> {
                                val isReceivingThisVault = apsState.isReceiveEnabled && apsState.activeReceiveVaultType == vaultType

                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = if (isReceivingThisVault) accent.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    border = BorderStroke(
                                        1.5.dp,
                                        if (isReceivingThisVault) accent else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(18.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        if (isReceivingThisVault) {
                                            PulsingRadarAvatar(
                                                avatarBase64 = myAvatarB64,
                                                name = myName,
                                                accent = accent
                                            )
                                        } else {
                                            ApsProfileAvatar(
                                                avatarBase64 = myAvatarB64,
                                                name = myName,
                                                size = 76.dp,
                                                ringColor = MaterialTheme.colorScheme.outline,
                                                showOnlineDot = false
                                            )
                                        }

                                        Text(
                                            text = if (isReceivingThisVault) {
                                                "Ready to Receive ${vaultType.displayName}"
                                            } else {
                                                "${vaultType.displayName} APS Receive is OFF"
                                            },
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 16.sp,
                                            textAlign = TextAlign.Center
                                        )

                                        Text(
                                            text = myName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = accent
                                        )

                                        // Copyable Device ID Chip
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            border = BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(10.dp))
                                                .clickable {
                                                    clipboard.setText(AnnotatedString(myId))
                                                    Toast.makeText(context, "Device ID copied: $myId", Toast.LENGTH_SHORT).show()
                                                }
                                                .testTag("aps_copy_device_id_btn")
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Text(
                                                    text = "Device ID: $myId",
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Icon(
                                                    Icons.Default.ContentCopy,
                                                    contentDescription = "Copy Device ID",
                                                    tint = accent,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }

                                        if (isReceivingThisVault) {
                                            // Live Wi-Fi & Bluetooth Channel Chips
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Surface(
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = MaterialTheme.colorScheme.surface,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Column(
                                                        modifier = Modifier.padding(10.dp),
                                                        horizontalAlignment = Alignment.CenterHorizontally
                                                    ) {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Icon(Icons.Default.Wifi, contentDescription = null, tint = Color(0xFF00B894), modifier = Modifier.size(15.dp))
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Text("Wi-Fi / Hotspot", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                                        }
                                                        Spacer(modifier = Modifier.height(2.dp))
                                                        Text(
                                                            text = "${apsState.localIpAddress}:${apsState.localTcpPort}",
                                                            fontFamily = FontFamily.Monospace,
                                                            fontSize = 11.sp,
                                                            color = accent,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }

                                                Surface(
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = MaterialTheme.colorScheme.surface,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Column(
                                                        modifier = Modifier.padding(10.dp),
                                                        horizontalAlignment = Alignment.CenterHorizontally
                                                    ) {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Icon(
                                                                Icons.Default.Bluetooth,
                                                                contentDescription = null,
                                                                tint = if (apsState.isBluetoothListening) Color(0xFF00B894) else MaterialTheme.colorScheme.onSurfaceVariant,
                                                                modifier = Modifier.size(15.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Text("Bluetooth", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                                        }
                                                        Spacer(modifier = Modifier.height(2.dp))
                                                        Text(
                                                            text = if (apsState.isBluetoothListening) "Listening" else "Off / Unpermitted",
                                                            fontSize = 11.sp,
                                                            color = if (apsState.isBluetoothListening) Color(0xFF00B894) else MaterialTheme.colorScheme.onSurfaceVariant,
                                                            maxLines = 1
                                                        )
                                                    }
                                                }
                                            }

                                            Text(
                                                text = "Other devices will see your profile photo and Device ID in their ${vaultType.displayName} APS list. Any wrong-vault transfer attempt is automatically rejected.",
                                                fontSize = 11.sp,
                                                textAlign = TextAlign.Center,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )

                                            OutlinedButton(
                                                onClick = { ApsTransferEngine.stopReceiveMode() },
                                                shape = RoundedCornerShape(12.dp),
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
                                                modifier = Modifier.fillMaxWidth().height(46.dp).testTag("aps_stop_receive_btn")
                                            ) {
                                                Text("Turn Off APS Receive Mode", fontWeight = FontWeight.Bold)
                                            }
                                        } else {
                                            Button(
                                                onClick = {
                                                    val target = receiverTargetProvider()
                                                    if (target != null) {
                                                        ApsTransferEngine.startReceiveMode(context, target)
                                                    } else {
                                                        Toast.makeText(context, "Unlock your ${vaultType.displayName} first.", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                shape = RoundedCornerShape(12.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = accentContentColor),
                                                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("aps_start_receive_btn")
                                            ) {
                                                Icon(Icons.Default.Radar, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Enable ${vaultType.displayName} APS Receive", fontWeight = FontWeight.Bold)
                                            }
                                        }

                                        if (!btPermGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                            TextButton(
                                                onClick = {
                                                    btPermissionLauncher.launch(
                                                        arrayOf(
                                                            Manifest.permission.BLUETOOTH_CONNECT,
                                                            Manifest.permission.BLUETOOTH_SCAN
                                                        )
                                                    )
                                                }
                                            ) {
                                                Icon(Icons.Default.Bluetooth, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Grant Bluetooth Permission for BT Receive", fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showHistoryDialog) {
        ApsTransferHistoryDialog(onDismiss = { showHistoryDialog = false })
    }
}

/**
 * Reusable circular profile avatar with gradient ring and initials fallback.
 */
@Composable
private fun ApsProfileAvatar(
    avatarBase64: String,
    name: String,
    size: Dp,
    ringColor: Color,
    showOnlineDot: Boolean = false
) {
    val bitmap = remember(avatarBase64) {
        if (avatarBase64.isNotBlank()) {
            try {
                val bytes = Base64.decode(avatarBase64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            } catch (_: Throwable) {
                null
            }
        } else null
    }

    val initials = remember(name) {
        val parts = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        when {
            parts.size >= 2 -> "${parts[0].first()}${parts[1].first()}".uppercase(Locale.getDefault())
            parts.size == 1 -> parts[0].take(2).uppercase(Locale.getDefault())
            else -> "VU"
        }
    }

    Box(modifier = Modifier.size(size)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(ringColor.copy(alpha = 0.28f), Color(0xFF1A1F36))
                    )
                )
                .border(2.dp, ringColor, CircleShape)
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "$name profile photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                )
            } else {
                Text(
                    text = initials,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = (size.value * 0.34f).sp,
                    color = ringColor
                )
            }
        }
        if (showOnlineDot) {
            Box(
                modifier = Modifier
                    .size(13.dp)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(Color(0xFF00B894))
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
            )
        }
    }
}

@Composable
private fun PulsingRadarAvatar(
    avatarBase64: String,
    name: String,
    accent: Color
) {
    val transition = rememberInfiniteTransition(label = "aps_radar")
    val pulseScale by transition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.48f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_scale"
    )
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_alpha"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(116.dp)
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .scale(pulseScale)
                .alpha(pulseAlpha)
                .clip(CircleShape)
                .border(2.5.dp, accent, CircleShape)
                .background(accent.copy(alpha = 0.14f))
        )
        ApsProfileAvatar(
            avatarBase64 = avatarBase64,
            name = name,
            size = 76.dp,
            ringColor = accent,
            showOnlineDot = true
        )
    }
}

/**
 * Dual-profile visual banner showing BOTH Sender & Receiver profile photos side-by-side!
 */
@Composable
private fun DualProfileTransferBanner(
    leftName: String,
    leftSub: String,
    leftAvatarB64: String,
    rightName: String,
    rightSub: String,
    rightAvatarB64: String,
    transport: ApsTransportType?,
    accent: Color,
    secondaryAccent: Color,
    isCompact: Boolean,
    isAnimated: Boolean
) {
    val avatarSize = if (isCompact) 56.dp else 66.dp
    val transition = rememberInfiniteTransition(label = "aps_beam")
    val beamAlpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "beam_alpha"
    )

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth().testTag("aps_dual_profile_banner")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (isCompact) 10.dp else 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left User (Sender)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                ApsProfileAvatar(
                    avatarBase64 = leftAvatarB64,
                    name = leftName,
                    size = avatarSize,
                    ringColor = accent,
                    showOnlineDot = true
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = leftName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = leftSub,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }

            // Center Encrypted Transfer Bridge
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 6.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = accent.copy(alpha = if (isAnimated) beamAlpha * 0.28f else 0.2f),
                    border = BorderStroke(1.5.dp, accent.copy(alpha = if (isAnimated) beamAlpha else 0.7f)),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Encrypted transfer direction",
                            tint = accent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (transport == ApsTransportType.BLUETOOTH) Icons.Default.Bluetooth else Icons.Default.Wifi,
                        contentDescription = null,
                        tint = secondaryAccent,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = Color(0xFF00B894),
                        modifier = Modifier.size(11.dp)
                    )
                }
                Text(
                    text = "AES-256",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00B894)
                )
            }

            // Right User (Receiver)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                ApsProfileAvatar(
                    avatarBase64 = rightAvatarB64,
                    name = rightName,
                    size = avatarSize,
                    ringColor = secondaryAccent,
                    showOnlineDot = true
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = rightName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = rightSub,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun ApsPeerCard(
    peer: ApsPeerDevice,
    senderVaultType: ApsVaultType,
    accent: Color,
    accentContentColor: Color,
    onSelect: () -> Unit
) {
    val isMismatch = peer.vaultType != null && peer.vaultType != senderVaultType
    val borderColor = if (isMismatch) MaterialTheme.colorScheme.error.copy(alpha = 0.65f) else accent.copy(alpha = 0.45f)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        border = BorderStroke(1.2.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onSelect)
            .testTag("aps_peer_card_${peer.deviceId}")
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ApsProfileAvatar(
                avatarBase64 = peer.avatarBase64,
                name = peer.profileName,
                size = 52.dp,
                ringColor = if (isMismatch) MaterialTheme.colorScheme.error else accent,
                showOnlineDot = !isMismatch
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = peer.profileName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "ID: ${peer.deviceId} • ${peer.deviceModel}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(5.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = if (peer.transport == ApsTransportType.WIFI_LAN) Icons.Default.Wifi else Icons.Default.Bluetooth,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = if (peer.transport == ApsTransportType.WIFI_LAN) "Wi-Fi" else "Bluetooth",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    peer.vaultType?.let { vt ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isMismatch) MaterialTheme.colorScheme.error.copy(alpha = 0.2f) else Color(0xFF00B894).copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = if (isMismatch) "${vt.displayName} (Mismatch)" else vt.displayName,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isMismatch) MaterialTheme.colorScheme.error else Color(0xFF00B894),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = onSelect,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isMismatch) MaterialTheme.colorScheme.error else accent,
                    contentColor = if (isMismatch) Color.White else accentContentColor
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier.height(40.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Send", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun IncomingApprovalView(
    request: ApsIncomingRequest,
    myName: String,
    myDeviceId: String,
    myAvatarBase64: String,
    accent: Color,
    isCompact: Boolean,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Mutual Profile Photos: Sender -> Receiver (You)
        DualProfileTransferBanner(
            leftName = request.senderProfileName,
            leftSub = request.senderDeviceId,
            leftAvatarB64 = request.senderAvatarBase64,
            rightName = "$myName (You)",
            rightSub = myDeviceId,
            rightAvatarB64 = myAvatarBase64,
            transport = request.transport,
            accent = accent,
            secondaryAccent = Color(0xFF00B894),
            isCompact = isCompact,
            isAnimated = true
        )

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = accent.copy(alpha = 0.1f),
            border = BorderStroke(1.5.dp, accent),
            modifier = Modifier.fillMaxWidth().testTag("aps_incoming_approval_card")
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Incoming ${request.vaultType.displayName} Transfer",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    color = accent
                )
                Text(
                    text = "${request.senderProfileName} wants to send ${request.itemCount} encrypted file(s) (${formatApsBytes(request.totalBytes)}) directly into your ${request.vaultType.displayName}.",
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Split 6-Digit SAS Security Code Box
                SasCodeCard(securityCode = request.securityCode, accent = accent)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onReject,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                        modifier = Modifier.weight(1f).height(48.dp).testTag("aps_reject_btn")
                    ) {
                        Text("Decline", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = onAccept,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00B894), contentColor = Color.Black),
                        modifier = Modifier.weight(1.3f).height(48.dp).testTag("aps_accept_btn")
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Verify & Accept", fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveTransferView(
    state: com.example.util.ApsUiState,
    vaultType: ApsVaultType,
    accent: Color,
    secondaryAccent: Color,
    isCompact: Boolean,
    onCancel: () -> Unit
) {
    val myAvatar = state.localAvatarBase64.ifBlank { ApsTransferEngine.getLocalAvatarSnippet() }
    val myName = state.localProfileName.ifBlank { "You" }
    val peerName = state.activePeerProfileName.ifBlank { "Peer Device" }

    val leftName = if (state.isSendingActive) "$myName (You)" else peerName
    val leftSub = if (state.isSendingActive) state.localDeviceId else state.activePeerDeviceId
    val leftAvatar = if (state.isSendingActive) myAvatar else state.activePeerAvatarBase64

    val rightName = if (state.isSendingActive) peerName else "$myName (You)"
    val rightSub = if (state.isSendingActive) state.activePeerDeviceId else state.localDeviceId
    val rightAvatar = if (state.isSendingActive) state.activePeerAvatarBase64 else myAvatar

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Mutual Profile Photos Banner!
        DualProfileTransferBanner(
            leftName = leftName,
            leftSub = leftSub,
            leftAvatarB64 = leftAvatar,
            rightName = rightName,
            rightSub = rightSub,
            rightAvatarB64 = rightAvatar,
            transport = state.activeTransport,
            accent = accent,
            secondaryAccent = secondaryAccent,
            isCompact = isCompact,
            isAnimated = true
        )

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
            border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth().testTag("aps_active_transfer_card")
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (state.phase == ApsTransferPhase.CONNECTING || state.phase == ApsTransferPhase.AWAITING_APPROVAL) {
                    CircularProgressIndicator(
                        color = accent,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(40.dp)
                    )
                }

                Text(
                    text = state.statusMessage.ifBlank { "Securing Vault-to-Vault tunnel…" },
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )

                if (state.securityCode.isNotBlank()) {
                    SasCodeCard(securityCode = state.securityCode, accent = accent)
                }

                if (state.phase == ApsTransferPhase.TRANSFERRING) {
                    val fraction = if (state.totalBytes > 0L) {
                        (state.transferredBytes.toFloat() / state.totalBytes.toFloat()).coerceIn(0f, 1f)
                    } else 0f
                    val pct = (fraction * 100).toInt()

                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "File ${state.currentFileIndex} / ${state.totalFiles}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = accent
                            )
                            Text(
                                text = "$pct%",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp,
                                color = accent
                            )
                        }

                        LinearProgressIndicator(
                            progress = { fraction },
                            color = accent,
                            trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )

                        if (state.currentFileName.isNotBlank()) {
                            Text(
                                text = state.currentFileName,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${formatApsBytes(state.transferredBytes)} / ${formatApsBytes(state.totalBytes)}",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${formatApsBytes(state.speedBytesPerSec)}/s",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF00B894)
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = onCancel,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth().height(46.dp).testTag("aps_cancel_btn")
                ) {
                    Text("Cancel Transfer", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun CompletedTransferView(
    state: com.example.util.ApsUiState,
    accent: Color,
    isCompact: Boolean,
    onViewHistory: () -> Unit,
    onDone: () -> Unit
) {
    val myAvatar = state.localAvatarBase64.ifBlank { ApsTransferEngine.getLocalAvatarSnippet() }
    val myName = state.localProfileName.ifBlank { "You" }
    val peerName = state.activePeerProfileName.ifBlank { "Peer Device" }

    val leftName = if (state.isSendingActive) "$myName (You)" else peerName
    val leftSub = if (state.isSendingActive) state.localDeviceId else state.activePeerDeviceId
    val leftAvatar = if (state.isSendingActive) myAvatar else state.activePeerAvatarBase64

    val rightName = if (state.isSendingActive) peerName else "$myName (You)"
    val rightSub = if (state.isSendingActive) state.activePeerDeviceId else state.localDeviceId
    val rightAvatar = if (state.isSendingActive) state.activePeerAvatarBase64 else myAvatar

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (state.activePeerProfileName.isNotBlank()) {
            DualProfileTransferBanner(
                leftName = leftName,
                leftSub = leftSub,
                leftAvatarB64 = leftAvatar,
                rightName = rightName,
                rightSub = rightSub,
                rightAvatarB64 = rightAvatar,
                transport = state.activeTransport,
                accent = Color(0xFF00B894),
                secondaryAccent = accent,
                isCompact = isCompact,
                isAnimated = false
            )
        }

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF00B894).copy(alpha = 0.14f),
            border = BorderStroke(1.5.dp, Color(0xFF00B894)),
            modifier = Modifier.fillMaxWidth().testTag("aps_completed_card")
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Transfer Complete",
                    tint = Color(0xFF00B894),
                    modifier = Modifier.size(50.dp)
                )
                Text(
                    text = "Vault-to-Vault Transfer Complete!",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    color = Color(0xFF00B894),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = state.lastCompletedSummary ?: "All files verified with SHA-256 and encrypted directly into the target vault.",
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onViewHistory,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(46.dp)
                    ) {
                        Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("History", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = onDone,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00B894), contentColor = Color.Black),
                        modifier = Modifier.weight(1.3f).height(46.dp).testTag("aps_done_btn")
                    ) {
                        Text("Done", fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun FailedTransferView(
    errorMessage: String,
    accent: Color,
    accentContentColor: Color,
    onRetry: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.error),
        modifier = Modifier.fillMaxWidth().testTag("aps_failed_card")
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                Icons.Default.ErrorOutline,
                contentDescription = "Transfer Failed",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = "APS Connection Failed",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.error
            )
            Text(
                text = errorMessage,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Button(
                onClick = onRetry,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = accentContentColor),
                modifier = Modifier.fillMaxWidth().height(46.dp).testTag("aps_retry_btn")
            ) {
                Text("Back to Devices", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SasCodeCard(
    securityCode: String,
    accent: Color
) {
    val formatted = if (securityCode.length == 6) {
        "${securityCode.substring(0, 3)}  ${securityCode.substring(3)}"
    } else {
        securityCode
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFF00B894), modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "MATCH 6-DIGIT SECURITY CODE ON BOTH PHONES",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatted,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 28.sp,
                letterSpacing = 5.sp,
                color = accent,
                modifier = Modifier.testTag("aps_sas_code_text")
            )
        }
    }
}

private fun formatApsBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format(Locale.US, "%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format(Locale.US, "%.2f GB", gb)
}
