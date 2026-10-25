package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.AccentGreen
import com.example.ui.theme.PrimaryBlue
import com.example.util.AppSecurityManager
import com.example.util.AppStorageHelper
import com.example.util.MediaZipEncryptionHelper
import com.example.util.SettingsManager
import com.example.util.SsiManager
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun MediaZipEncryptDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isProcessing by remember { mutableStateOf(false) }
    var operationStatus by remember { mutableStateOf<String?>(null) }
    var isSuccess by remember { mutableStateOf(true) }

    val activePassword = remember(context) { MediaZipEncryptionHelper.getAppLockPassword(context) }
    var deleteSourceAfterZip by remember { mutableStateOf(SettingsManager.settings.value.deleteRawMediaAfterZip) }

    val mediaDir = remember(context) { AppStorageHelper.getMediaImportedDir(context) }
    val rawFilesCount = remember(mediaDir) { mediaDir.listFiles()?.filter { it.isFile }?.size ?: 0 }
    val rawTotalSize = remember(mediaDir) { mediaDir.listFiles()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L }

    val zipFile = remember(context) { MediaZipEncryptionHelper.getDefaultEncryptedZipFile(context) }
    var zipExists by remember { mutableStateOf(zipFile.exists() && zipFile.length() > 0) }
    var zipSize by remember { mutableStateOf(if (zipFile.exists()) zipFile.length() else 0L) }

    Dialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 24.dp)
                .testTag("dialog_media_zip_encryption")
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = PrimaryBlue.copy(alpha = 0.15f),
                            shape = CircleShape,
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.FolderZip,
                                    contentDescription = null,
                                    tint = PrimaryBlue,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Media Folder ZIP Encryption",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Standard AES-256 Protected Archive",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        enabled = !isProcessing,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Explanatory Note
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Zipping packages the entire media folder into a standard password-protected .zip file. If anyone opens it using Files by Google, Samsung My Files, ZArchiver, or on PC/Mac, it will prompt for your App Lock password.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // App Lock Password Card
                Surface(
                    color = AccentGreen.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, AccentGreen.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Key, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Archive Password (Same as App Lock)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Current Password: ",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Surface(
                                color = Color.Black.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = activePassword,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = AccentGreen,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Storage Status Card
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Folder & Archive Status",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "• Raw media: $rawFilesCount file(s) (${formatBytes(rawTotalSize)}) in ${mediaDir.name}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (zipExists) {
                                "• Encrypted ZIP: Ready (${formatBytes(zipSize)}) in Android/media"
                            } else {
                                "• Encrypted ZIP: Not created yet"
                            },
                            fontSize = 12.sp,
                            color = if (zipExists) AccentGreen else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Option: Delete raw files after zipping
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = deleteSourceAfterZip,
                        onCheckedChange = {
                            deleteSourceAfterZip = it
                            SettingsManager.setDeleteRawMediaAfterZip(it)
                        },
                        colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.testTag("checkbox_delete_after_zip")
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Delete unencrypted files after zipping",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Only the password-protected ZIP will remain on storage.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Processing Indicator
                AnimatedVisibility(visible = isProcessing) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = PrimaryBlue, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Processing encryption…", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }

                // Operation Status Alert
                operationStatus?.let { status ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = if (isSuccess) AccentGreen.copy(alpha = 0.12f) else MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Info,
                                contentDescription = null,
                                tint = if (isSuccess) AccentGreen else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = status,
                                fontSize = 12.sp,
                                color = if (isSuccess) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Actions
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Action 1: Zip Now
                    Button(
                        onClick = {
                            isProcessing = true
                            operationStatus = null
                            scope.launch {
                                val result = MediaZipEncryptionHelper.zipMediaFolder(
                                    context = context,
                                    deleteSourceAfterZip = deleteSourceAfterZip
                                )
                                isProcessing = false
                                isSuccess = result.success
                                operationStatus = result.message
                                if (result.success) {
                                    val f = MediaZipEncryptionHelper.getDefaultEncryptedZipFile(context)
                                    zipExists = f.exists()
                                    zipSize = f.length()
                                    Toast.makeText(context, "Encrypted ZIP ready!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        enabled = !isProcessing && (rawFilesCount > 0 || !zipExists),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("btn_zip_now")
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Zip & Encrypt Entire Folder", fontWeight = FontWeight.Bold)
                    }

                    // Action 2: Export to Downloads
                    OutlinedButton(
                        onClick = {
                            isProcessing = true
                            operationStatus = null
                            scope.launch {
                                val result = MediaZipEncryptionHelper.exportZipToPublicDownloads(context)
                                isProcessing = false
                                isSuccess = result.isSuccess
                                if (result.isSuccess) {
                                    val file = result.getOrNull()
                                    operationStatus = "✓ Exported to Downloads/${file?.name}. Open it in any file manager to test!"
                                    Toast.makeText(context, "Exported to Downloads!", Toast.LENGTH_LONG).show()
                                } else {
                                    operationStatus = "Export failed: ${result.exceptionOrNull()?.message}"
                                }
                            }
                        },
                        enabled = !isProcessing,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("btn_export_zip_downloads")
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Export Protected ZIP to Downloads", fontWeight = FontWeight.SemiBold)
                    }

                    // Action 3: Decrypt & Restore (if zip exists)
                    if (zipExists) {
                        OutlinedButton(
                            onClick = {
                                isProcessing = true
                                operationStatus = null
                                scope.launch {
                                    val result = MediaZipEncryptionHelper.unzipMediaFolder(context = context)
                                    isProcessing = false
                                    isSuccess = result.success
                                    operationStatus = result.message
                                    if (result.success) {
                                        Toast.makeText(context, "Restored media files!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            enabled = !isProcessing,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("btn_unzip_restore")
                        ) {
                            Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Decrypt & Restore Files from ZIP", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1.0) {
        String.format(Locale.US, "%.2f MB", mb)
    } else {
        String.format(Locale.US, "%.1f KB", kb)
    }
}
