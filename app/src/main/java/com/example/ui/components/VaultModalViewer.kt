package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.ui.theme.AccentGreen
import com.example.ui.theme.PrimaryBlue
import com.example.util.SettingsManager
import com.example.util.VaultImage

@Composable
fun VaultModalViewer(
    images: List<VaultImage>,
    currentIndex: Int,
    isSlideshowPlaying: Boolean,
    isModalPaused: Boolean,
    slideshowProgress: Float,
    onClose: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleSlideshow: () -> Unit,
    onTogglePause: () -> Unit
) {
    if (images.isEmpty() || currentIndex !in images.indices) return

    val currentItem = images[currentIndex]
    val context = LocalContext.current
    val settings by SettingsManager.settings.collectAsState()
    var totalDrag by remember { mutableFloatStateOf(0f) }

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
                .testTag("vault_modal_viewer")
        ) {
            // Main Display Image inside ZoomableBox (pinch-to-zoom, pan, double-tap zoom)
            ZoomableBox(
                modifier = Modifier.fillMaxSize(),
                onSwipeNext = onNext,
                onSwipePrevious = onPrevious,
                onSingleTap = {
                    if (isSlideshowPlaying) {
                        onTogglePause()
                    }
                }
            ) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(currentItem.file)
                        .crossfade(true)
                        .build(),
                    contentDescription = currentItem.name,
                    contentScale = if (settings.imageFitMode == "crop") ContentScale.Crop else ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("vault_modal_full_image"),
                    loading = {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = Color(0xFF6C5CE7),
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    },
                    error = {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Could not preview ${currentItem.name}",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 14.sp
                            )
                        }
                    }
                )
            }

            // Slideshow Progress Bar at top
            if (isSlideshowPlaying) {
                LinearProgressIndicator(
                    progress = { slideshowProgress },
                    color = Color(0xFFFDCB6E),
                    trackColor = Color.White.copy(alpha = 0.15f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                )
            }

            // Top Bar Overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.85f),
                                Color.Transparent
                            )
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentItem.name,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val statusDesc = buildString {
                            append("${currentIndex + 1} / ${images.size}")
                            append(" • ${currentItem.sizeBytes / 1024} KB")
                            if (isSlideshowPlaying) {
                                append(if (isModalPaused) " (Paused ⏸)" else " (Slideshow ▶)")
                            }
                        }
                        Text(
                            text = statusDesc,
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Slideshow Play / Pause button
                    FilledIconButton(
                        onClick = onToggleSlideshow,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (isSlideshowPlaying) Color(0xFFFDCB6E) else Color.White.copy(alpha = 0.2f),
                            contentColor = if (isSlideshowPlaying) Color.Black else Color.White
                        ),
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("vault_slideshow_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (isSlideshowPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isSlideshowPlaying) "Stop Slideshow" else "Start Slideshow",
                            modifier = Modifier.size(22.dp)
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
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("vault_close_modal_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Viewer",
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // Left Navigation Button
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
                    .testTag("vault_prev_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Previous Image",
                    modifier = Modifier.size(28.dp)
                )
            }

            // Right Navigation Button
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
                    .testTag("vault_next_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Next Image",
                    modifier = Modifier.size(28.dp)
                )
            }

            // Bottom Navigation Hint & Counter Pill
            Surface(
                color = Color.Black.copy(alpha = 0.65f),
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    if (isSlideshowPlaying) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(Color(0xFFFDCB6E), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = "← Swipe or Arrows → (${currentIndex + 1} of ${images.size})",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
