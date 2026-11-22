package com.example.ui.components

import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.os.Build
import android.view.Surface
import android.view.TextureView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.util.EncryptedVideoDataSource
import com.example.util.SettingsManager
import com.example.util.VaultVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

private val PLAYBACK_SPEEDS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

@Composable
fun VidVaultPlayerModal(
    videos: List<VaultVideo>,
    currentIndex: Int,
    createDirectDataSource: (String) -> EncryptedVideoDataSource?,
    prepareFallbackStorageFile: suspend (String) -> File?,
    onShare: (VaultVideo) -> Unit,
    onClose: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit
) {
    val video = videos.getOrNull(currentIndex) ?: return
    val settings by SettingsManager.settings.collectAsState()
    val scope = rememberCoroutineScope()

    BackHandler(onBack = onClose)

    var showControls by remember { mutableStateOf(true) }
    var isPreparing by remember(video.id) { mutableStateOf(true) }
    var isPlaying by remember(video.id) { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    var isLooping by remember { mutableStateOf(settings.vidVaultLoopPlayback) }
    var playbackSpeed by remember { mutableFloatStateOf(settings.vidVaultDefaultPlaybackSpeed) }
    var durationMs by remember(video.id) { mutableLongStateOf(video.durationMs.coerceAtLeast(0L)) }
    var positionMs by remember(video.id) { mutableLongStateOf(0L) }
    var isUserSeeking by remember { mutableStateOf(false) }
    var seekSliderMs by remember(video.id) { mutableFloatStateOf(0f) }
    var videoWidth by remember(video.id) { mutableIntStateOf(16) }
    var videoHeight by remember(video.id) { mutableIntStateOf(9) }
    var errorMessage by remember(video.id) { mutableStateOf<String?>(null) }
    var usingDirectStream by remember(video.id) { mutableStateOf(settings.vidVaultDirectDiskStream) }
    var reloadNonce by remember(video.id) { mutableIntStateOf(0) }

    var mediaPlayerRef by remember { mutableStateOf<MediaPlayer?>(null) }
    var activeDataSourceRef by remember { mutableStateOf<EncryptedVideoDataSource?>(null) }
    var activeSurfaceRef by remember { mutableStateOf<Surface?>(null) }
    var surfaceReadyNonce by remember { mutableIntStateOf(0) }

    fun releasePlayer() {
        try {
            mediaPlayerRef?.stop()
        } catch (_: Throwable) {}
        try {
            mediaPlayerRef?.release()
        } catch (_: Throwable) {}
        mediaPlayerRef = null
        try {
            activeDataSourceRef?.close()
        } catch (_: Throwable) {}
        activeDataSourceRef = null
    }

    fun applySpeed(mp: MediaPlayer, speed: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val params = mp.playbackParams
                params.speed = speed
                mp.playbackParams = params
            } catch (_: Throwable) {}
        }
    }

    // Prepare MediaPlayer whenever video changes, surface becomes ready, or fallback/retry is triggered
    LaunchedEffect(video.id, surfaceReadyNonce, usingDirectStream, reloadNonce) {
        val surface = activeSurfaceRef ?: return@LaunchedEffect
        releasePlayer()
        isPreparing = true
        isPlaying = false
        errorMessage = null
        positionMs = 0L
        seekSliderMs = 0f

        val mp = MediaPlayer()
        mediaPlayerRef = mp

        try {
            mp.setSurface(surface)
            mp.isLooping = isLooping
            val vol = if (isMuted) 0f else 1f
            mp.setVolume(vol, vol)

            mp.setOnVideoSizeChangedListener { _, w, h ->
                if (w > 0 && h > 0) {
                    videoWidth = w
                    videoHeight = h
                }
            }

            mp.setOnPreparedListener { preparedPlayer ->
                isPreparing = false
                durationMs = preparedPlayer.duration.toLong().coerceAtLeast(video.durationMs)
                if (settings.vidVaultAutoPlay) {
                    preparedPlayer.start()
                    isPlaying = true
                    if (playbackSpeed != 1.0f) {
                        applySpeed(preparedPlayer, playbackSpeed)
                    }
                } else {
                    preparedPlayer.seekTo(1)
                    isPlaying = false
                }
            }

            mp.setOnCompletionListener {
                if (!isLooping) {
                    isPlaying = false
                    positionMs = durationMs
                    seekSliderMs = durationMs.toFloat()
                    showControls = true
                }
            }

            mp.setOnErrorListener { _, what, extra ->
                // If direct encrypted MediaDataSource fails on a non-standard container/codec,
                // automatically fall back to storage-streamed temp file playback!
                if (usingDirectStream) {
                    usingDirectStream = false
                } else {
                    isPreparing = false
                    isPlaying = false
                    errorMessage = "Playback error (code $what/$extra). Format may not be supported by device decoder."
                    showControls = true
                }
                true
            }

            if (usingDirectStream) {
                val ds = withContext(Dispatchers.IO) { createDirectDataSource(video.id) }
                if (ds != null) {
                    activeDataSourceRef = ds
                    mp.setDataSource(ds)
                    mp.prepareAsync()
                } else {
                    usingDirectStream = false
                }
            } else {
                val tempFile = prepareFallbackStorageFile(video.id)
                if (tempFile != null && tempFile.exists()) {
                    mp.setDataSource(tempFile.absolutePath)
                    mp.prepareAsync()
                } else {
                    isPreparing = false
                    errorMessage = "Could not decrypt video stream from storage."
                }
            }
        } catch (e: Throwable) {
            if (usingDirectStream) {
                usingDirectStream = false
            } else {
                isPreparing = false
                errorMessage = "Failed to play video: ${e.message}"
            }
        }
    }

    // Live playback progress ticker
    LaunchedEffect(isPlaying, video.id) {
        while (isPlaying) {
            val mp = mediaPlayerRef
            if (mp != null && !isUserSeeking) {
                try {
                    if (mp.isPlaying) {
                        val pos = mp.currentPosition.toLong().coerceAtLeast(0L)
                        positionMs = pos
                        seekSliderMs = pos.toFloat()
                        if (durationMs <= 0L) {
                            durationMs = mp.duration.toLong().coerceAtLeast(0L)
                        }
                    }
                } catch (_: Throwable) {}
            }
            delay(250L)
        }
    }

    // Cleanup on modal dismissal
    DisposableEffect(Unit) {
        onDispose {
            releasePlayer()
            try {
                activeSurfaceRef?.release()
            } catch (_: Throwable) {}
            activeSurfaceRef = null
        }
    }

    val extLabel = video.name.substringAfterLast('.', "VIDEO").uppercase(Locale.getDefault()).take(5)
    val sizeMb = video.sizeBytes / (1024.0 * 1024.0)
    val sizeText = if (sizeMb >= 1024.0) {
        String.format(Locale.getDefault(), "%.2f GB", sizeMb / 1024.0)
    } else {
        String.format(Locale.getDefault(), "%.1f MB", sizeMb)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { showControls = !showControls }
            .testTag("vid_player_modal_root")
    ) {
        // Aspect-ratio preserving TextureView for smooth Compose overlay rendering
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            val maxW = maxWidth
            val maxH = maxHeight
            val videoAspect = (videoWidth.toFloat() / videoHeight.coerceAtLeast(1).toFloat()).coerceIn(0.25f, 4.0f)
            val containerAspect = (maxW.value / maxH.value.coerceAtLeast(1f)).coerceAtLeast(0.1f)

            val (targetW, targetH) = if (videoAspect > containerAspect) {
                maxW to (maxW / videoAspect)
            } else {
                (maxH * videoAspect) to maxH
            }

            AndroidView(
                factory = { ctx ->
                    TextureView(ctx).apply {
                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                                activeSurfaceRef?.release()
                                activeSurfaceRef = Surface(surface)
                                surfaceReadyNonce++
                            }

                            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}

                            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                                releasePlayer()
                                activeSurfaceRef?.release()
                                activeSurfaceRef = null
                                return true
                            }

                            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
                        }
                    }
                },
                modifier = Modifier
                    .size(width = targetW, height = targetH)
                    .testTag("vid_player_surface")
            )
        }

        // Loading Indicator
        if (isPreparing && errorMessage == null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.align(Alignment.Center)
            ) {
                CircularProgressIndicator(
                    color = Color(0xFF00CEC9),
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(44.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = if (usingDirectStream) "Streaming directly from encrypted disk…" else "Preparing storage buffer…",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp
                )
            }
        }

        // Error State
        errorMessage?.let { err ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xEE1A1A2E),
                border = BorderStroke(1.dp, Color(0xFFFF7675)),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(18.dp)
                ) {
                    Text(
                        text = "Unable to Play Video",
                        color = Color(0xFFFF7675),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = err,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                errorMessage = null
                                reloadNonce++
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00CEC9))
                        ) {
                            Text("Retry", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { onShare(video) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C5CE7))
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open / Share", color = Color.White)
                        }
                    }
                }
            }
        }

        // Interactive Overlay Controls
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Top Gradient Bar (Close, File Info, Share)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)
                            )
                        )
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.14f))
                            .testTag("vid_player_close")
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close Video Player",
                            tint = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = video.name,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF6C5CE7).copy(alpha = 0.7f)
                            ) {
                                Text(
                                    text = extLabel,
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                            Text(
                                text = "$sizeText • ${currentIndex + 1}/${videos.size}",
                                color = Color.White.copy(alpha = 0.75f),
                                fontSize = 11.sp
                            )
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF00B894).copy(alpha = 0.25f),
                                border = BorderStroke(0.5.dp, Color(0xFF00B894))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Storage,
                                        contentDescription = null,
                                        tint = Color(0xFF00CEC9),
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = if (usingDirectStream) "Zero-RAM Disk" else "Disk Buffer",
                                        color = Color(0xFF00CEC9),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = { onShare(video) },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF6C5CE7).copy(alpha = 0.8f))
                            .testTag("vid_player_share")
                    ) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = "Share Video",
                            tint = Color.White
                        )
                    }
                }

                // Center Transport Controls (Prev, -10s, Play/Pause, +10s, Next)
                if (!isPreparing && errorMessage == null) {
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (videos.size > 1) {
                            IconButton(
                                onClick = onPrevious,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.55f))
                                    .testTag("vid_player_prev")
                            ) {
                                Icon(
                                    Icons.Default.SkipPrevious,
                                    contentDescription = "Previous Video",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                mediaPlayerRef?.let { mp ->
                                    try {
                                        val target = (mp.currentPosition - 10_000).coerceAtLeast(0)
                                        mp.seekTo(target)
                                        positionMs = target.toLong()
                                        seekSliderMs = target.toFloat()
                                    } catch (_: Throwable) {}
                                }
                            },
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.55f))
                                .testTag("vid_player_rewind")
                        ) {
                            Icon(
                                Icons.Default.Replay10,
                                contentDescription = "Rewind 10 seconds",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                mediaPlayerRef?.let { mp ->
                                    try {
                                        if (mp.isPlaying) {
                                            mp.pause()
                                            isPlaying = false
                                        } else {
                                            mp.start()
                                            isPlaying = true
                                            if (playbackSpeed != 1.0f) {
                                                applySpeed(mp, playbackSpeed)
                                            }
                                        }
                                    } catch (_: Throwable) {}
                                }
                            },
                            modifier = Modifier
                                .size(66.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00CEC9))
                                .testTag("vid_player_play_pause")
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.Black,
                                modifier = Modifier.size(38.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                mediaPlayerRef?.let { mp ->
                                    try {
                                        val maxDur = mp.duration.coerceAtLeast(0)
                                        val target = (mp.currentPosition + 10_000).coerceAtMost(maxDur)
                                        mp.seekTo(target)
                                        positionMs = target.toLong()
                                        seekSliderMs = target.toFloat()
                                    } catch (_: Throwable) {}
                                }
                            },
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.55f))
                                .testTag("vid_player_forward")
                        ) {
                            Icon(
                                Icons.Default.Forward10,
                                contentDescription = "Forward 10 seconds",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        if (videos.size > 1) {
                            IconButton(
                                onClick = onNext,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.55f))
                                    .testTag("vid_player_next")
                            ) {
                                Icon(
                                    Icons.Default.SkipNext,
                                    contentDescription = "Next Video",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                }

                // Bottom Seek Bar & Playback Tools
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    val safeDuration = durationMs.coerceAtLeast(1L).toFloat()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatDurationMs(if (isUserSeeking) seekSliderMs.toLong() else positionMs),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Slider(
                            value = seekSliderMs.coerceIn(0f, safeDuration),
                            onValueChange = { newVal ->
                                isUserSeeking = true
                                seekSliderMs = newVal
                            },
                            onValueChangeFinished = {
                                val target = seekSliderMs.toInt()
                                scope.launch {
                                    try {
                                        mediaPlayerRef?.seekTo(target)
                                        positionMs = target.toLong()
                                    } catch (_: Throwable) {}
                                    isUserSeeking = false
                                }
                            },
                            valueRange = 0f..safeDuration,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF00CEC9),
                                activeTrackColor = Color(0xFF00CEC9),
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                                .testTag("vid_player_seekbar")
                        )
                        Text(
                            text = formatDurationMs(durationMs),
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Mute Toggle
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color.White.copy(alpha = 0.14f),
                            modifier = Modifier
                                .clickable {
                                    isMuted = !isMuted
                                    val vol = if (isMuted) 0f else 1f
                                    try {
                                        mediaPlayerRef?.setVolume(vol, vol)
                                    } catch (_: Throwable) {}
                                }
                                .testTag("vid_player_mute")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                    contentDescription = if (isMuted) "Unmute" else "Mute",
                                    tint = if (isMuted) Color(0xFFFF7675) else Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isMuted) "Muted" else "Audio",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        // Loop Toggle
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isLooping) Color(0xFF00CEC9).copy(alpha = 0.3f) else Color.White.copy(alpha = 0.14f),
                            border = if (isLooping) BorderStroke(1.dp, Color(0xFF00CEC9)) else null,
                            modifier = Modifier
                                .clickable {
                                    isLooping = !isLooping
                                    try {
                                        mediaPlayerRef?.isLooping = isLooping
                                    } catch (_: Throwable) {}
                                }
                                .testTag("vid_player_loop")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = if (isLooping) Icons.Default.RepeatOne else Icons.Default.Repeat,
                                    contentDescription = "Toggle Loop",
                                    tint = if (isLooping) Color(0xFF00CEC9) else Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isLooping) "Loop ON" else "Loop",
                                    color = if (isLooping) Color(0xFF00CEC9) else Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        // Playback Speed Cycle Chip
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (playbackSpeed != 1.0f) Color(0xFF6C5CE7).copy(alpha = 0.5f) else Color.White.copy(alpha = 0.14f),
                            modifier = Modifier
                                .clickable {
                                    val idx = PLAYBACK_SPEEDS.indexOfFirst { it == playbackSpeed }
                                    val nextSpeed = PLAYBACK_SPEEDS[(idx + 1) % PLAYBACK_SPEEDS.size]
                                    playbackSpeed = nextSpeed
                                    mediaPlayerRef?.let { mp ->
                                        if (mp.isPlaying) {
                                            applySpeed(mp, nextSpeed)
                                        }
                                    }
                                }
                                .testTag("vid_player_speed")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Speed,
                                    contentDescription = "Playback Speed",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${playbackSpeed}x",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

fun formatDurationMs(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}
