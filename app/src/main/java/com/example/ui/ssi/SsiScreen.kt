package com.example.ui.ssi

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ui.theme.AccentGreen
import com.example.ui.theme.PrimaryBlue
import com.example.util.AppStorageHelper
import com.example.util.QrCodeGenerator
import com.example.util.SettingsManager
import com.example.util.SsiHostInfo
import com.example.util.SsiImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SsiScreen(
    viewModel: SsiViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val hostState by viewModel.hostState.collectAsStateWithLifecycle()
    val settings by SettingsManager.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearUserMessage()
        }
    }

    var itemToDelete by remember { mutableStateOf<SsiImage?>(null) }

    if (uiState.isRearrangeMode) {
        BackHandler {
            viewModel.exitRearrangeMode()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("ssi_screen")
    ) {
        // Top Header Banner & Stats
        Surface(
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "📸 SSI (Imported Slideshow)",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        val dir = remember(context) { AppStorageHelper.getMediaImportedDir(context) }
                        Text(
                            text = "Path: ${dir.name} • ${uiState.images.size} images (${formatFileSize(uiState.images.sumOf { it.sizeBytes })})",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                if (uiState.isRearrangeMode) {
                                    viewModel.exitRearrangeMode()
                                } else {
                                    viewModel.enterRearrangeMode()
                                }
                            },
                            enabled = uiState.images.isNotEmpty(),
                            modifier = Modifier.size(36.dp).testTag("ssi_rearrange_toggle_button")
                        ) {
                            Icon(
                                if (uiState.isRearrangeMode) Icons.Default.Check else Icons.Default.DragHandle,
                                contentDescription = "Rearrange Images",
                                tint = if (uiState.isRearrangeMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = { viewModel.refresh() },
                            modifier = Modifier.size(36.dp).testTag("ssi_refresh_button")
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Refresh Images",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // The 2 Main Action Buttons: Slideshow and Clear
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Slideshow Button
                    Button(
                        onClick = { viewModel.startSlideshow(0) },
                        enabled = uiState.images.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentGreen,
                            contentColor = Color.Black,
                            disabledContainerColor = AccentGreen.copy(alpha = 0.35f),
                            disabledContentColor = Color.Black.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .testTag("ssi_slideshow_button")
                    ) {
                        Icon(Icons.Default.Slideshow, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Slideshow", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }

                    // Clear Button
                    Button(
                        onClick = { viewModel.requestClearAll() },
                        enabled = uiState.images.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                            disabledContainerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.35f),
                            disabledContentColor = MaterialTheme.colorScheme.onError.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .testTag("ssi_clear_button")
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Clear All", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Slideshow Time Interval & Loop Setting Bar (1s to 5s)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Timer,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Slideshow Interval: ",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = String.format(Locale.US, "%.1fs", settings.ssiSlideshowIntervalSeconds),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Loop",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Switch(
                                    checked = settings.ssiAutoLoop,
                                    onCheckedChange = { SettingsManager.setSsiAutoLoop(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier.size(width = 38.dp, height = 24.dp).testTag("ssi_loop_switch")
                                )
                            }
                        }

                        Slider(
                            value = settings.ssiSlideshowIntervalSeconds.coerceIn(1.0f, 5.0f),
                            onValueChange = { SettingsManager.setSsiSlideshowIntervalSeconds(it) },
                            valueRange = 1.0f..5.0f,
                            steps = 7, // 1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0, 4.5, 5.0
                            modifier = Modifier.fillMaxWidth().testTag("ssi_interval_slider")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Host Slideshow Control Card (Zero-Trace Local Web Host)
                Surface(
                    color = if (hostState.isRunning) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(
                        1.dp,
                        if (hostState.isRunning) AccentGreen else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ssi_host_card")
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
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
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(
                                            if (hostState.isRunning) AccentGreen else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                                            CircleShape
                                        )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = if (hostState.isRunning) "🌐 Host Active (Zero-Trace)" else "🌐 Host Slideshow",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (hostState.isRunning) AccentGreen else MaterialTheme.colorScheme.onSurface
                                        )
                                        if (hostState.isRunning) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                color = AccentGreen.copy(alpha = 0.2f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "PIN: ${hostState.pin}",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = AccentGreen,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = if (hostState.isRunning) {
                                            hostState.fullUrl
                                        } else {
                                            "Stream slideshow to browser on same Wi-Fi with 4-digit PIN"
                                        },
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (hostState.isRunning) {
                                    Button(
                                        onClick = { viewModel.openHostInfoDialog() },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary
                                        ),
                                        modifier = Modifier
                                            .height(30.dp)
                                            .testTag("ssi_host_qr_btn")
                                    ) {
                                        Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("QR / Link", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                }

                                Switch(
                                    checked = hostState.isRunning,
                                    onCheckedChange = { enabled ->
                                        if (enabled) {
                                            viewModel.openHostPinDialog()
                                        } else {
                                            viewModel.stopHost()
                                        }
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = AccentGreen,
                                        checkedTrackColor = AccentGreen.copy(alpha = 0.35f)
                                    ),
                                    modifier = Modifier.size(width = 38.dp, height = 24.dp).testTag("ssi_host_switch")
                                )
                            }
                        }
                    }
                }
            }
        }

        // Dedicated Rearrange Control Toolbar
        if (uiState.isRearrangeMode && uiState.images.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ssi_rearrange_bar")
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f, fill = false)) {
                            Icon(
                                Icons.Default.DragHandle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            val sel = uiState.selectedRearrangeIndex
                            Text(
                                text = if (sel != null && sel in uiState.images.indices) {
                                    "Move #${sel + 1}: ${uiState.images[sel].name}"
                                } else {
                                    "Rearrange Mode (${uiState.images.size} items)"
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        Button(
                            onClick = { viewModel.exitRearrangeMode() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("ssi_rearrange_done_button")
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Done", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val sel = uiState.selectedRearrangeIndex ?: -1
                        val hasSel = sel in uiState.images.indices
                        val canLeft = hasSel && sel > 0
                        val canRight = hasSel && sel < uiState.images.size - 1

                        Button(
                            onClick = { viewModel.moveSelectedToStart() },
                            enabled = canLeft,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            contentPadding = PaddingValues(horizontal = 6.dp),
                            modifier = Modifier.weight(1f).height(34.dp)
                        ) {
                            Text("⏮ First", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }

                        Button(
                            onClick = { viewModel.moveSelectedLeft() },
                            enabled = canLeft,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp),
                            modifier = Modifier.weight(1f).height(34.dp).testTag("ssi_move_left_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("Left", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }

                        Button(
                            onClick = { viewModel.moveSelectedRight() },
                            enabled = canRight,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp),
                            modifier = Modifier.weight(1f).height(34.dp).testTag("ssi_move_right_button")
                        ) {
                            Text("Right", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(14.dp))
                        }

                        Button(
                            onClick = { viewModel.moveSelectedToEnd() },
                            enabled = canRight,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            contentPadding = PaddingValues(horizontal = 6.dp),
                            modifier = Modifier.weight(1f).height(34.dp)
                        ) {
                            Text("Last ⏭", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Press & hold any image or tap any position to place it. Order is saved automatically.",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }

        // Main Body: Images Grid or Empty State
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = PrimaryBlue)
            }
        } else if (uiState.images.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            shape = CircleShape,
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No Imported Images in SSI",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Go to the Excel tab, open any image in full screen, and tap the \"Import to SSI\" button at the bottom.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Imported images are stored in media_imported directory and can be played in full screen slideshow with custom intervals and swipe gestures.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("ssi_images_grid")
            ) {
                itemsIndexed(uiState.images, key = { _, item -> item.file.absolutePath }) { index, item ->
                    val isSelected = uiState.isRearrangeMode && uiState.selectedRearrangeIndex == index
                    SsiImageThumbnailCard(
                        item = item,
                        index = index,
                        isRearrangeMode = uiState.isRearrangeMode,
                        isSelectedForRearrange = isSelected,
                        onClick = {
                            if (uiState.isRearrangeMode) {
                                viewModel.selectRearrangeIndex(index)
                            } else {
                                viewModel.startSlideshow(index)
                            }
                        },
                        onLongPress = {
                            viewModel.enterRearrangeMode(index)
                        },
                        onMoveLeft = if (uiState.isRearrangeMode && index > 0) {
                            { viewModel.moveImage(index, index - 1) }
                        } else null,
                        onMoveRight = if (uiState.isRearrangeMode && index < uiState.images.size - 1) {
                            { viewModel.moveImage(index, index + 1) }
                        } else null,
                        onDeleteClick = {
                            itemToDelete = item
                        }
                    )
                }
            }
        }
    }

    // Confirmation Dialog for Clear All
    if (uiState.showClearDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissClearDialog() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Clear All Imported Images?")
                }
            },
            text = {
                Text(
                    "This will permanently delete all ${uiState.images.size} imported image(s) from the media_imported folder. This action cannot be undone."
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmClearAll() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("ssi_confirm_clear_button")
                ) {
                    Text("Delete All")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissClearDialog() }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Single Item Delete Dialog
    itemToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("Delete Image?") },
            text = { Text("Remove \"${item.name}\" from media_imported?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteImage(item)
                        itemToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Set Host PIN Dialog
    if (uiState.showHostPinDialog) {
        SsiSetHostPinDialog(
            onDismiss = { viewModel.dismissHostPinDialog() },
            onConfirm = { pin -> viewModel.confirmStartHost(pin) }
        )
    }

    // Host Info & QR Code Dialog
    if (uiState.showHostInfoDialog && hostState.isRunning) {
        SsiHostInfoDialog(
            hostInfo = hostState,
            onDismiss = { viewModel.dismissHostInfoDialog() },
            onStopHost = { viewModel.stopHost() }
        )
    }

    // Fullscreen Slideshow with Swipe Navigation
    if (uiState.isSlideshowActive && uiState.images.isNotEmpty()) {
        val currentImg = uiState.images.getOrNull(uiState.currentSlideshowIndex)
        if (currentImg != null) {
            SsiFullscreenSlideshowDialog(
                currentImg = currentImg,
                currentIndex = uiState.currentSlideshowIndex,
                totalImages = uiState.images.size,
                isPaused = uiState.isSlideshowPaused,
                intervalSeconds = settings.ssiSlideshowIntervalSeconds,
                isLoop = settings.ssiAutoLoop,
                onClose = { viewModel.stopSlideshow() },
                onTogglePlayPause = { viewModel.togglePlayPause() },
                onNext = { viewModel.nextImage() },
                onPrevious = { viewModel.prevImage() },
                onIntervalChange = { SettingsManager.setSsiSlideshowIntervalSeconds(it) }
            )
        }
    }
}

@Composable
private fun SsiImageThumbnailCard(
    item: SsiImage,
    index: Int,
    isRearrangeMode: Boolean = false,
    isSelectedForRearrange: Boolean = false,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onMoveLeft: (() -> Unit)? = null,
    onMoveRight: (() -> Unit)? = null,
    onDeleteClick: () -> Unit
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isSelectedForRearrange) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = if (isSelectedForRearrange) {
            BorderStroke(2.5.dp, MaterialTheme.colorScheme.primary)
        } else if (isRearrangeMode) {
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        },
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .pointerInput(item.file.absolutePath, isRearrangeMode, isSelectedForRearrange) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = {
                        try {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        } catch (_: Throwable) {}
                        onLongPress()
                    }
                )
            }
            .testTag("ssi_thumbnail_card_$index")
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(Color.Black.copy(alpha = 0.2f))
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(item.uri)
                        .crossfade(true)
                        .build(),
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Delete quick icon on thumbnail top-right (only in normal mode)
                if (!isRearrangeMode) {
                    FilledIconButton(
                        onClick = onDeleteClick,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.6f),
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .size(24.dp)
                            .align(Alignment.TopEnd)
                            .padding(2.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Delete",
                            modifier = Modifier.size(14.dp)
                        )
                    }
                } else {
                    // In rearrange mode, show drag indicator handle at top right
                    Surface(
                        color = if (isSelectedForRearrange) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(bottomStart = 6.dp),
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Icon(
                            Icons.Default.DragHandle,
                            contentDescription = "Rearrange Handle",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp).padding(2.dp)
                        )
                    }
                }

                // Index badge on bottom-left
                Surface(
                    color = if (isSelectedForRearrange) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(topEnd = 6.dp),
                    modifier = Modifier.align(Alignment.BottomStart)
                ) {
                    Text(
                        text = if (isSelectedForRearrange) "★ #${index + 1}" else "#${index + 1}",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }

            Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
                Text(
                    text = item.name,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelectedForRearrange) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatFileSize(item.sizeBytes),
                    fontSize = 9.sp,
                    color = if (isSelectedForRearrange) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isRearrangeMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp, vertical = 1.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { onMoveLeft?.invoke() },
                        enabled = onMoveLeft != null,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Move Left",
                            tint = if (onMoveLeft != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                            modifier = Modifier.size(13.dp)
                        )
                    }

                    Text(
                        text = if (isSelectedForRearrange) "ACTIVE" else "MOVE",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelectedForRearrange) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    IconButton(
                        onClick = { onMoveRight?.invoke() },
                        enabled = onMoveRight != null,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Move Right",
                            tint = if (onMoveRight != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SsiFullscreenSlideshowDialog(
    currentImg: SsiImage,
    currentIndex: Int,
    totalImages: Int,
    isPaused: Boolean,
    intervalSeconds: Float,
    isLoop: Boolean,
    onClose: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onIntervalChange: (Float) -> Unit
) {
    val context = LocalContext.current
    var totalDrag by remember { mutableFloatStateOf(0f) }

    BackHandler {
        onClose()
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .testTag("ssi_fullscreen_slideshow_dialog")
                // Swipe Navigation Gesture Handler
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            totalDrag += dragAmount
                        },
                        onDragEnd = {
                            if (totalDrag > 45) {
                                onPrevious() // Swiped right -> go to previous
                            } else if (totalDrag < -45) {
                                onNext() // Swiped left -> go to next
                            }
                            totalDrag = 0f
                        },
                        onDragCancel = {
                            totalDrag = 0f
                        }
                    )
                }
        ) {
            // Main Image with tap-to-pause
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                onTogglePlayPause()
                            },
                            onDoubleTap = {
                                onClose()
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(currentImg.uri)
                        .crossfade(true)
                        .build(),
                    contentDescription = currentImg.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Top Header Bar Overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Play / Pause Icon
                    FilledIconButton(
                        onClick = onTogglePlayPause,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (!isPaused) AccentGreen else Color.White.copy(alpha = 0.25f),
                            contentColor = if (!isPaused) Color.Black else Color.White
                        ),
                        modifier = Modifier.size(40.dp).testTag("ssi_slideshow_play_pause")
                    ) {
                        Icon(
                            if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (isPaused) "Play" else "Pause",
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentImg.name,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${formatFileSize(currentImg.sizeBytes)} • Swipe left/right to navigate",
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 11.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Close Button
                    FilledIconButton(
                        onClick = onClose,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Color.White.copy(alpha = 0.2f),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.size(40.dp).testTag("ssi_slideshow_close")
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // Navigation Arrows: Left
            FilledIconButton(
                onClick = onPrevious,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color.White.copy(alpha = 0.2f),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 12.dp)
                    .size(width = 44.dp, height = 64.dp)
                    .testTag("ssi_prev_image")
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Previous Image",
                    modifier = Modifier.size(26.dp)
                )
            }

            // Navigation Arrows: Right
            FilledIconButton(
                onClick = onNext,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color.White.copy(alpha = 0.2f),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp)
                    .size(width = 44.dp, height = 64.dp)
                    .testTag("ssi_next_image")
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Next Image",
                    modifier = Modifier.size(26.dp)
                )
            }

            // Bottom Footer Overlay (Counter + Interval Adjuster)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                        )
                    )
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Counter badge
                    Surface(
                        color = Color.Black.copy(alpha = 0.65f),
                        shape = CircleShape,
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            if (!isPaused) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(AccentGreen, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(
                                text = "${currentIndex + 1} / $totalImages",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Interval badge with quick adjust
                    Surface(
                        color = Color.Black.copy(alpha = 0.65f),
                        shape = CircleShape,
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            // - 0.5s button
                            IconButton(
                                onClick = {
                                    val newInterval = (intervalSeconds - 0.5f).coerceIn(1.0f, 5.0f)
                                    onIntervalChange(newInterval)
                                },
                                modifier = Modifier.size(26.dp)
                            ) {
                                Text("-", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }

                            Text(
                                text = String.format(Locale.US, "%.1fs", intervalSeconds),
                                color = AccentGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )

                            // + 0.5s button
                            IconButton(
                                onClick = {
                                    val newInterval = (intervalSeconds + 0.5f).coerceIn(1.0f, 5.0f)
                                    onIntervalChange(newInterval)
                                },
                                modifier = Modifier.size(26.dp)
                            ) {
                                Text("+", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1.0) {
        String.format(Locale.US, "%.2f MB", mb)
    } else {
        String.format(Locale.US, "%.1f KB", kb)
    }
}

@Composable
fun SsiSetHostPinDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var isPinVisible by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Set 4-Digit Host PIN", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Anyone opening the slideshow link in their browser must enter this 4-digit PIN to view images.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = pin,
                    onValueChange = { input ->
                        val filtered = input.filter { it.isDigit() }
                        if (filtered.length <= 4) {
                            pin = filtered
                            errorMessage = null
                        }
                    },
                    label = { Text("4-Digit PIN") },
                    placeholder = { Text("e.g. 1234") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done
                    ),
                    visualTransformation = if (isPinVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isPinVisible = !isPinVisible }) {
                            Icon(
                                if (isPinVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (isPinVisible) "Hide PIN" else "Show PIN"
                            )
                        }
                    },
                    isError = errorMessage != null,
                    supportingText = {
                        if (errorMessage != null) {
                            Text(errorMessage!!, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                        } else {
                            Text("${pin.length} / 4 digits", fontSize = 11.sp)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ssi_host_pin_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🛡️ Zero-Trace Guarantee: Images stream in RAM only. Turning off the host or closing the browser immediately clears all data on the other device.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (pin.length == 4 && pin.all { it.isDigit() }) {
                        onConfirm(pin)
                    } else {
                        errorMessage = "Please enter exactly 4 numbers"
                    }
                },
                enabled = pin.length == 4,
                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen, contentColor = Color.Black),
                modifier = Modifier.testTag("ssi_confirm_host_pin_btn")
            ) {
                Text("Start Host", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun SsiHostInfoDialog(
    hostInfo: SsiHostInfo,
    onDismiss: () -> Unit,
    onStopHost: () -> Unit
) {
    val context = LocalContext.current
    val qrBitmap = remember(hostInfo.fullUrl) {
        QrCodeGenerator.generateQrBitmap(hostInfo.fullUrl, sizePx = 400)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 16.dp)
                .testTag("ssi_host_info_dialog")
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(AccentGreen, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Slideshow Host Active",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(20.dp))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Scan QR code or open link in another device's browser (same Wi-Fi or hotspot):",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(14.dp))

                // QR Code Display
                if (qrBitmap != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White,
                        border = BorderStroke(2.dp, AccentGreen.copy(alpha = 0.5f)),
                        modifier = Modifier.padding(4.dp)
                    ) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "Host QR Code",
                            modifier = Modifier
                                .size(190.dp)
                                .padding(10.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Host Link Card with Copy & Open
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "BROWSER LINK",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = hostInfo.fullUrl,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row {
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    val clip = ClipData.newPlainText("SSI Host Link", hostInfo.fullUrl)
                                    clipboard?.setPrimaryClip(clip)
                                    Toast.makeText(context, "Link copied to clipboard!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(34.dp).testTag("ssi_copy_host_link_btn")
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy Link", modifier = Modifier.size(18.dp))
                            }

                            IconButton(
                                onClick = {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(hostInfo.fullUrl))
                                        context.startActivity(intent)
                                    } catch (e: Throwable) {
                                        Toast.makeText(context, "Could not open browser", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.size(34.dp).testTag("ssi_open_host_browser_btn")
                            ) {
                                Icon(Icons.Default.OpenInBrowser, contentDescription = "Open in Browser", modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Prominent 4-digit PIN Box
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "REQUIRED 4-DIGIT PIN",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val pinChars = hostInfo.pin.padEnd(4, '•').toList()
                            pinChars.forEach { ch ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.5.dp, AccentGreen),
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = ch.toString(),
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = AccentGreen
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Zero-trace explanation
                Text(
                    text = "🔒 Zero-Trace: Streamed purely in RAM. Turning off host or closing the tab instantly cleans visitor local storage.",
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 14.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Action Buttons: Stop Host & Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onStopHost,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .testTag("ssi_stop_host_btn")
                    ) {
                        Text("Stop Host", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                    ) {
                        Text("Done", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
