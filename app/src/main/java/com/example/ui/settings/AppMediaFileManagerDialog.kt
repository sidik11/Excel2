package com.example.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.ui.components.ZoomableBox
import com.example.util.AppSecurityManager
import com.example.util.AppStorageHelper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AppMediaFileManagerDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val rootMediaDir = remember { AppStorageHelper.getDedicatedMediaDir(context) }
    val securityConfig by AppSecurityManager.securityConfig.collectAsState()

    var isAuthenticated by remember { mutableStateOf(false) }
    var passwordInput by remember { mutableStateOf("") }
    var authError by remember { mutableStateOf<String?>(null) }

    var currentDir by remember { mutableStateOf(rootMediaDir) }
    var previewImageFile by remember { mutableStateOf<File?>(null) }
    var fileDetailTarget by remember { mutableStateOf<File?>(null) }

    fun checkAuth() {
        val pin = passwordInput.trim()
        if (pin.isEmpty()) {
            authError = "Please enter your password / PIN"
            return
        }
        val isPinValid = AppSecurityManager.verifyPin(enteredPin = pin, context = context)
        val isMasterValid = AppSecurityManager.verifyMasterPassword(pin)
        if (isPinValid || isMasterValid) {
            isAuthenticated = true
            authError = null
            com.example.util.ActivityLogManager.log(
                context,
                "FILE_MANAGER_ACCESS",
                "App Media File Manager Unlocked",
                "Authenticated with password/PIN to browse internal media files.",
                severity = "INFO"
            )
        } else if (!securityConfig.isPinEnabled && pin.length >= 4) {
            // If PIN not configured, allow access
            isAuthenticated = true
            authError = null
            com.example.util.ActivityLogManager.log(
                context,
                "FILE_MANAGER_ACCESS",
                "App Media File Manager Opened",
                "Opened media file manager.",
                severity = "INFO"
            )
        } else {
            authError = "Incorrect password or PIN."
        }
    }

    androidx.activity.compose.BackHandler {
        if (previewImageFile != null) {
            previewImageFile = null
        } else if (fileDetailTarget != null) {
            fileDetailTarget = null
        } else if (currentDir.absolutePath != rootMediaDir.absolutePath) {
            currentDir.parentFile?.let { currentDir = it }
        } else {
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(20.dp)),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
            tonalElevation = 8.dp
        ) {
            if (!isAuthenticated) {
                // PASSWORD PROMPT VIEW
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                        modifier = Modifier.size(68.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = "Locked",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        "Protected Media Explorer",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Enter your PIN or Master Password to inspect app media directory",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = {
                            passwordInput = it
                            authError = null
                        },
                        label = { Text("6-Digit PIN or Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        isError = authError != null,
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .testTag("input_file_manager_password")
                    )

                    if (authError != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            authError!!,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(0.85f),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel")
                        }

                        Button(
                            onClick = { checkAuth() },
                            modifier = Modifier.weight(1f).testTag("btn_unlock_file_manager")
                        ) {
                            Text("Unlock")
                        }
                    }
                }
            } else {
                // FILE MANAGER VIEW
                val files = remember(currentDir) {
                    try {
                        if (!currentDir.exists()) currentDir.mkdirs()
                        currentDir.listFiles()?.sortedWith(
                            compareBy<File> { !it.isDirectory }
                                .thenBy { it.name.lowercase(Locale.ROOT) }
                        ) ?: emptyList()
                    } catch (_: Throwable) {
                        emptyList()
                    }
                }

                val relativePath = remember(currentDir) {
                    val rootPath = rootMediaDir.absolutePath
                    val curPath = currentDir.absolutePath
                    if (curPath.startsWith(rootPath)) {
                        curPath.removePrefix(rootPath).ifEmpty { "/" }
                    } else curPath
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
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
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.FolderSpecial,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    "App Media File Manager",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    "${files.size} items in directory",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Path indicator & navigation
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (currentDir != rootMediaDir && currentDir.parentFile != null) {
                                IconButton(
                                    onClick = { currentDir = currentDir.parentFile ?: rootMediaDir },
                                    modifier = Modifier.size(28.dp).testTag("btn_fm_up_folder")
                                ) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "Up", modifier = Modifier.size(16.dp))
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(
                                text = "media:$relativePath",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Quick Jump Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val subfolders = listOf("Root", "files", "images", "vault", "security")
                        subfolders.forEach { sub ->
                            val target = if (sub == "Root") rootMediaDir else File(rootMediaDir, sub)
                            val isSelected = currentDir.absolutePath == target.absolutePath
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    if (!target.exists()) target.mkdirs()
                                    currentDir = target
                                },
                                label = { Text(sub, fontSize = 10.sp) },
                                modifier = Modifier.testTag("chip_fm_$sub")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // File List
                    if (files.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Folder is empty.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(files, key = { it.absolutePath }) { file ->
                                FileManagerItemRow(
                                    file = file,
                                    onFolderClick = { currentDir = file },
                                    onImageClick = { previewImageFile = file },
                                    onFileClick = { fileDetailTarget = file },
                                    onShare = {
                                        try {
                                            val uri = FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.fileprovider",
                                                file
                                            )
                                            val intent = Intent(Intent.ACTION_SEND).apply {
                                                type = "*/*"
                                                putExtra(Intent.EXTRA_STREAM, uri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(intent, "Share ${file.name}"))
                                        } catch (e: Throwable) {
                                            Toast.makeText(context, "Could not share: ${e.message}", Toast.LENGTH_SHORT).show()
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

    // Modal Image Preview with ZoomableBox (Pinch-to-zoom & gestures)
    if (previewImageFile != null) {
        Dialog(
            onDismissRequest = { previewImageFile = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.95f))
            ) {
                ZoomableBox(
                    modifier = Modifier.fillMaxSize(),
                    maxScale = 6f,
                    minScale = 1f
                ) {
                    AsyncImage(
                        model = previewImageFile,
                        contentDescription = previewImageFile?.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Top overlay bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Black.copy(alpha = 0.6f)
                    ) {
                        Text(
                            text = previewImageFile?.name ?: "",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }

                    IconButton(
                        onClick = { previewImageFile = null },
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            .size(36.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
            }
        }
    }

    // File Details Dialog
    if (fileDetailTarget != null) {
        val target = fileDetailTarget!!
        AlertDialog(
            onDismissRequest = { fileDetailTarget = null },
            icon = {
                Icon(
                    if (target.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = target.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            },
            text = {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val lastMod = dateFormat.format(Date(target.lastModified()))
                val sizeStr = if (target.isDirectory) {
                    "${target.listFiles()?.size ?: 0} items"
                } else {
                    formatFileSize(target.length())
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Type: ${if (target.isDirectory) "Directory" else "File"}", fontSize = 12.sp)
                    Text("Size: $sizeStr", fontSize = 12.sp)
                    Text("Modified: $lastMod", fontSize = 12.sp)
                    Text(
                        "Path: ${target.absolutePath}",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        try {
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                target
                            )
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "*/*"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share ${target.name}"))
                        } catch (e: Throwable) {
                            Toast.makeText(context, "Could not share: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Share")
                }
            },
            dismissButton = {
                TextButton(onClick = { fileDetailTarget = null }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun FileManagerItemRow(
    file: File,
    onFolderClick: () -> Unit,
    onImageClick: () -> Unit,
    onFileClick: () -> Unit,
    onShare: () -> Unit
) {
    val isImage = remember(file.name) {
        val ext = file.extension.lowercase(Locale.ROOT)
        ext in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                when {
                    file.isDirectory -> onFolderClick()
                    isImage -> onImageClick()
                    else -> onFileClick()
                }
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon or thumbnail
            if (isImage) {
                AsyncImage(
                    model = file,
                    contentDescription = file.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(6.dp))
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (file.isDirectory) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    else MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = when {
                                file.isDirectory -> Icons.Default.Folder
                                file.name.endsWith(".dat") -> Icons.Default.Security
                                file.name.endsWith(".json") -> Icons.Default.Code
                                file.name.endsWith(".pdf") -> Icons.Default.PictureAsPdf
                                file.name.endsWith(".xlsx") || file.name.endsWith(".xls") -> Icons.Default.TableChart
                                else -> Icons.Default.InsertDriveFile
                            },
                            contentDescription = null,
                            tint = if (file.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (file.isDirectory) {
                        "${file.listFiles()?.size ?: 0} items"
                    } else {
                        formatFileSize(file.length())
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onShare,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.Share,
                    contentDescription = "Share",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

private fun formatFileSize(size: Long): String {
    return when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", size / 1024.0)
        else -> String.format(Locale.US, "%.1f MB", size / (1024.0 * 1024.0))
    }
}
