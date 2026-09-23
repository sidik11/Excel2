package com.example.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.example.util.AppSecurityManager
import com.example.util.QrCodeGenerator
import com.example.util.SettingsManager
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

@Composable
fun ShareAppQrDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val currentSettings by SettingsManager.settings.collectAsState()

    var downloadUrlInput by remember { mutableStateOf(currentSettings.appDownloadUrl) }
    var activeQrUrl by remember { mutableStateOf(currentSettings.appDownloadUrl) }
    var showUrlEditor by remember { mutableStateOf(false) }
    var isSharingApk by remember { mutableStateOf(false) }

    val qrBitmap: Bitmap? = remember(activeQrUrl) {
        if (activeQrUrl.isBlank()) null
        else QrCodeGenerator.generateQrBitmap(
            content = activeQrUrl,
            sizePx = 640,
            foregroundColor = android.graphics.Color.BLACK,
            backgroundColor = android.graphics.Color.WHITE
        )
    }

    fun copyUrl() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("App URL", activeQrUrl))
        Toast.makeText(context, "Download URL copied to clipboard!", Toast.LENGTH_SHORT).show()
    }

    fun testDownloadInBrowser() {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(activeQrUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Throwable) {
            Toast.makeText(context, "Could not open browser: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareUrl() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Download Excel & Image Vault")
            putExtra(
                Intent.EXTRA_TEXT,
                "Scan or open this download link to install Excel & Image Vault:\n$activeQrUrl"
            )
        }
        context.startActivity(Intent.createChooser(intent, "Share App Download Link"))
    }

    fun shareApkFile() {
        isSharingApk = true
        try {
            val srcDir = context.applicationInfo.sourceDir
            val srcFile = File(srcDir)
            if (!srcFile.exists()) {
                Toast.makeText(context, "APK file not found on device.", Toast.LENGTH_SHORT).show()
                isSharingApk = false
                return
            }
            val exportDir = File(context.cacheDir, "apk_export")
            exportDir.mkdirs()
            val targetApk = File(exportDir, "ExcelImageVault.apk")
            FileInputStream(srcFile).use { input ->
                FileOutputStream(targetApk).use { output ->
                    input.copyTo(output)
                }
            }
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                targetApk
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_STREAM, apkUri)
                putExtra(Intent.EXTRA_SUBJECT, "Excel & Image Vault APK")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share / Install APK on Another Device"))
        } catch (e: Throwable) {
            Toast.makeText(context, "Failed to share APK: ${e.message}", Toast.LENGTH_LONG).show()
        } finally {
            isSharingApk = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .wrapContentHeight()
                .clip(RoundedCornerShape(20.dp)),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.QrCode2,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                "App Download QR & Install",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Scan with any camera or QR scanner",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("btn_close_qr_dialog")) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)

                // QR Code Container
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    border = BorderStroke(3.dp, MaterialTheme.colorScheme.primary),
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .size(230.dp)
                        .padding(6.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (qrBitmap != null) {
                            Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = "App Download QR Code",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp)
                            )
                        } else {
                            CircularProgressIndicator()
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Point any phone camera or QR scanner at this code to immediately download or open this app.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Current URL box with Edit toggle
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Target Download Link:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            TextButton(
                                onClick = { showUrlEditor = !showUrlEditor },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    if (showUrlEditor) "Close Editor" else "Edit Link",
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Text(
                            text = activeQrUrl,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2
                        )

                        if (showUrlEditor) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = downloadUrlInput,
                                onValueChange = { downloadUrlInput = it },
                                label = { Text("Enter APK Direct URL or Web Link", fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("input_custom_download_url")
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        val trimmed = downloadUrlInput.trim()
                                        if (trimmed.isNotBlank()) {
                                            activeQrUrl = trimmed
                                            SettingsManager.setAppDownloadUrl(trimmed)
                                            AppSecurityManager.updateFingerprintProfileDataIfRegistered(context)
                                            showUrlEditor = false
                                            Toast.makeText(context, "Download link updated and saved!", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.weight(1f).testTag("btn_save_download_url")
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Apply & Save", fontSize = 11.sp)
                                }

                                OutlinedButton(
                                    onClick = {
                                        val defaultUrl = "https://ais-pre-jgxqbiezgnewblvh6iilnv-571171211889.asia-southeast1.run.app"
                                        downloadUrlInput = defaultUrl
                                        activeQrUrl = defaultUrl
                                        SettingsManager.setAppDownloadUrl(defaultUrl)
                                        AppSecurityManager.updateFingerprintProfileDataIfRegistered(context)
                                        showUrlEditor = false
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Reset Default", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Action Buttons Row 1: Test Download & Copy Link
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { testDownloadInBrowser() },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.weight(1f).testTag("btn_test_download_browser")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Test Download", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { copyUrl() },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).testTag("btn_copy_qr_url")
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy Link", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Action Buttons Row 2: Share Direct APK File & Share Link
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { shareApkFile() },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF00B894)),
                        enabled = !isSharingApk,
                        modifier = Modifier.weight(1f).testTag("btn_share_apk_direct")
                    ) {
                        Icon(Icons.Default.InstallMobile, contentDescription = null, tint = Color(0xFF00B894), modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Send App APK", fontSize = 11.sp, color = Color(0xFF00B894), fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = { shareUrl() },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).testTag("btn_share_qr_url")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Share Link", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

