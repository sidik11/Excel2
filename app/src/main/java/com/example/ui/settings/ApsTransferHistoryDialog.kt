package com.example.ui.settings

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.util.ApsTransferHistoryEntry
import com.example.util.ApsTransferHistoryManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class ApsHistoryFilter(val label: String) {
    ALL("All"),
    SENT("Sent"),
    RECEIVED("Received"),
    VID_VAULT("VID Vault"),
    IMG_VAULT("IMG Vault"),
    ISSUES("Failed / Blocked")
}

@Composable
fun ApsTransferHistoryDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val entries by ApsTransferHistoryManager.entries.collectAsStateWithLifecycle()
    var activeFilter by remember { mutableStateOf(ApsHistoryFilter.ALL) }
    var showClearConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        ApsTransferHistoryManager.load(context)
    }

    val filtered = remember(entries, activeFilter) {
        when (activeFilter) {
            ApsHistoryFilter.ALL -> entries
            ApsHistoryFilter.SENT -> entries.filter { it.direction == "SENT" }
            ApsHistoryFilter.RECEIVED -> entries.filter { it.direction == "RECEIVED" }
            ApsHistoryFilter.VID_VAULT -> entries.filter { it.vaultType == "VID_VAULT" }
            ApsHistoryFilter.IMG_VAULT -> entries.filter { it.vaultType == "IMG_VAULT" }
            ApsHistoryFilter.ISSUES -> entries.filter { it.status != "COMPLETED" }
        }
    }

    val completedCount = remember(entries) { entries.count { it.status == "COMPLETED" } }
    val sentCount = remember(entries) { entries.count { it.direction == "SENT" && it.status == "COMPLETED" } }
    val recvCount = remember(entries) { entries.count { it.direction == "RECEIVED" && it.status == "COMPLETED" } }
    val totalBytesMoved = remember(entries) { entries.filter { it.status == "COMPLETED" }.sumOf { it.totalBytes } }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f))
                .navigationBarsPadding()
                .padding(horizontal = 10.dp, vertical = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.5.dp, Color(0xFF6C5CE7).copy(alpha = 0.65f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 680.dp)
                    .fillMaxHeight(0.92f)
                    .testTag("aps_transfer_history_dialog")
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Top Header
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color(0xFF6C5CE7).copy(alpha = 0.22f),
                                        Color(0xFF00CEC9).copy(alpha = 0.16f)
                                    )
                                )
                            )
                            .padding(horizontal = 14.dp, vertical = 12.dp)
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
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            Brush.linearGradient(listOf(Color(0xFF6C5CE7), Color(0xFF00CEC9)))
                                        )
                                ) {
                                    Icon(
                                        Icons.Default.History,
                                        contentDescription = "APS Transfer History",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "APS Transfer History",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${entries.size} records • $completedCount verified • ${formatHistoryBytes(totalBytesMoved)}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (entries.isNotEmpty()) {
                                    IconButton(
                                        onClick = { showClearConfirm = true },
                                        modifier = Modifier.testTag("btn_clear_aps_history")
                                    ) {
                                        Icon(
                                            Icons.Default.DeleteSweep,
                                            contentDescription = "Clear History",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = onDismiss,
                                    modifier = Modifier.testTag("btn_close_aps_history")
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Close History")
                                }
                            }
                        }
                    }

                    // Stats Strip
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HistoryStatBox(
                            label = "Sent",
                            value = "$sentCount",
                            accent = Color(0xFF6C5CE7),
                            modifier = Modifier.weight(1f)
                        )
                        HistoryStatBox(
                            label = "Received",
                            value = "$recvCount",
                            accent = Color(0xFF00CEC9),
                            modifier = Modifier.weight(1f)
                        )
                        HistoryStatBox(
                            label = "Data Moved",
                            value = formatHistoryBytes(totalBytesMoved),
                            accent = Color(0xFF00B894),
                            modifier = Modifier.weight(1.2f)
                        )
                    }

                    // Filter Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ApsHistoryFilter.entries.forEach { filter ->
                            val selected = activeFilter == filter
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) Color(0xFF6C5CE7).copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(1.dp, if (selected) Color(0xFF6C5CE7) else Color.Transparent),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { activeFilter = filter }
                                    .testTag("chip_history_filter_${filter.name}")
                            ) {
                                Text(
                                    text = filter.label,
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selected) Color(0xFF6C5CE7) else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(top = 6.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                    )

                    // History List
                    if (filtered.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(28.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.History,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                                    modifier = Modifier.size(54.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "No APS transfers recorded yet",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Every encrypted IMG Vault and VID Vault transfer over Wi-Fi or Bluetooth will appear here with peer details, file names, and verification status.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(filtered, key = { it.id }) { entry ->
                                ApsHistoryEntryCard(
                                    entry = entry,
                                    onDelete = { ApsTransferHistoryManager.deleteEntry(context, entry.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear All APS Transfer History?", fontWeight = FontWeight.Bold) },
            text = { Text("This removes all saved transfer logs from Settings. Your encrypted IMG Vault and VID Vault files are untouched.") },
            confirmButton = {
                Button(
                    onClick = {
                        ApsTransferHistoryManager.clearAll(context)
                        showClearConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("btn_confirm_clear_aps_history")
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun HistoryStatBox(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = accent.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 14.sp,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = label,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ApsHistoryEntryCard(
    entry: ApsTransferHistoryEntry,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val isSent = entry.direction == "SENT"
    val isVid = entry.vaultType == "VID_VAULT"
    val isOk = entry.status == "COMPLETED"
    val vaultAccent = if (isVid) Color(0xFF00CEC9) else Color(0xFF6C5CE7)
    val statusColor = when (entry.status) {
        "COMPLETED" -> Color(0xFF00B894)
        "MISMATCH" -> Color(0xFFFDCB6E)
        else -> MaterialTheme.colorScheme.error
    }

    val avatarBitmap = remember(entry.peerAvatarBase64) {
        if (entry.peerAvatarBase64.isNotBlank()) {
            try {
                val bytes = Base64.decode(entry.peerAvatarBase64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            } catch (_: Throwable) {
                null
            }
        } else null
    }

    val formattedTime = remember(entry.timestampMs) {
        try {
            SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(entry.timestampMs))
        } catch (_: Throwable) {
            ""
        }
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .testTag("aps_history_card_${entry.id}")
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Peer Avatar with Direction Badge
                Box(modifier = Modifier.size(46.dp)) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(vaultAccent.copy(alpha = 0.2f))
                            .border(1.5.dp, vaultAccent, CircleShape)
                    ) {
                        if (avatarBitmap != null) {
                            Image(
                                bitmap = avatarBitmap,
                                contentDescription = entry.peerProfileName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text(
                                text = entry.peerProfileName.take(2).uppercase(Locale.getDefault()),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = vaultAccent
                            )
                        }
                    }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(18.dp)
                            .align(Alignment.BottomEnd)
                            .clip(CircleShape)
                            .background(if (isSent) Color(0xFF6C5CE7) else Color(0xFF00CEC9))
                            .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isSent) Icons.Default.CallMade else Icons.Default.CallReceived,
                            contentDescription = entry.direction,
                            tint = Color.White,
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isSent) "Sent to ${entry.peerProfileName}" else "Received from ${entry.peerProfileName}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                    Text(
                        text = buildString {
                            if (entry.peerDeviceId.isNotBlank()) append("ID: ${entry.peerDeviceId}")
                            if (entry.peerDeviceModel.isNotBlank()) {
                                if (isNotEmpty()) append(" • ")
                                append(entry.peerDeviceModel)
                            }
                        },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = formattedTime,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete log entry",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }

            // Badges Row
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status Pill
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = statusColor.copy(alpha = 0.16f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Icon(
                            imageVector = if (isOk) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = when (entry.status) {
                                "COMPLETED" -> "Verified"
                                "MISMATCH" -> "Vault Mismatch"
                                "REJECTED" -> "Declined"
                                else -> "Failed"
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                    }
                }

                // Vault Type Pill
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = vaultAccent.copy(alpha = 0.16f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Icon(
                            imageVector = if (isVid) Icons.Default.Movie else Icons.Default.Image,
                            contentDescription = null,
                            tint = vaultAccent,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = if (isVid) "VID Vault" else "IMG Vault",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = vaultAccent
                        )
                    }
                }

                // Transport Pill
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Icon(
                            imageVector = if (entry.transport == "BLUETOOTH") Icons.Default.Bluetooth else Icons.Default.Wifi,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = if (entry.transport == "BLUETOOTH") "Bluetooth" else "Wi-Fi",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // File Count & Size
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Text(
                        text = "${entry.fileCount} file(s) • ${formatHistoryBytes(entry.totalBytes)}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }

                if (entry.securityCode.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(11.dp), tint = Color(0xFF00B894))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "SAS ${entry.securityCode}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }

            if (entry.errorMessage.isNotBlank() && !isOk) {
                Text(
                    text = entry.errorMessage,
                    fontSize = 11.sp,
                    color = statusColor
                )
            }

            // Expandable file names list
            if (entry.fileNames.isNotEmpty()) {
                val preview = if (expanded) entry.fileNames else entry.fileNames.take(3)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    preview.forEach { fileName ->
                        Text(
                            text = "• $fileName",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (!expanded && entry.fileNames.size > 3) {
                        Text(
                            text = "+${entry.fileNames.size - 3} more file(s) — tap to expand",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

private fun formatHistoryBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format(Locale.US, "%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format(Locale.US, "%.2f GB", gb)
}
