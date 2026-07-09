package com.example.melodyplayer.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.melodyplayer.LibraryViewModel
import com.example.melodyplayer.PlaybackViewModel
import com.example.melodyplayer.ProgressState
import com.example.melodyplayer.data.Song
import kotlinx.coroutines.flow.StateFlow

@Composable
fun PlayerScreen(
    viewModel: PlaybackViewModel,
    libraryViewModel: LibraryViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlayingState.collectAsStateWithLifecycle()
    val currentSongColor by viewModel.currentSongColor.collectAsStateWithLifecycle(null)
    val songThumbnail256Ids by libraryViewModel.songThumbnail256Ids.collectAsStateWithLifecycle()
    val defaultColor = Color(0xFF1E1B4B)
    val dominantColor = currentSongColor?.let { Color(it) } ?: defaultColor

    val animatedColor by animateColorAsState(
        targetValue = dominantColor,
        animationSpec = tween(durationMillis = 800, easing = LinearOutSlowInEasing),
        label = "playerBgColor"
    )

    val playerBrush = remember(animatedColor) {
        Brush.verticalGradient(
            colors = listOf(
                animatedColor.copy(alpha = 0.45f),
                Color(0xFF0F172A),
                Color(0xFF020617)
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(brush = playerBrush)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver",
                        tint = Color.White.copy(alpha = 0.8f)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "REPRODUCIENDO",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.size(48.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))

            val onPlayPauseToggle = remember(viewModel) { { viewModel.togglePlayPause() } }
            val onNext = remember(viewModel) { { viewModel.next() } }
            val onPrevious = remember(viewModel) { { viewModel.previous() } }
            val onSeek = remember(viewModel) { { ms: Long -> viewModel.seekTo(ms) } }

            val hasWebp256 = currentSong?.let { songThumbnail256Ids.contains(it.id) } ?: false
            PlayerCard(
                currentSong = currentSong,
                isPlaying = isPlaying,
                progressStateFlow = viewModel.progressState,
                hasWebp256 = hasWebp256,
                onPlayPauseToggle = onPlayPauseToggle,
                onNext = onNext,
                onPrevious = onPrevious,
                onSeek = onSeek
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun PlayerCard(
    currentSong: Song?,
    isPlaying: Boolean,
    progressStateFlow: StateFlow<ProgressState>,
    hasWebp256: Boolean,
    onPlayPauseToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 16.dp, shape = RoundedCornerShape(24.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.06f)),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Crossfade(
                targetState = currentSong,
                animationSpec = tween(durationMillis = 500, easing = LinearOutSlowInEasing),
                label = "albumArtCrossfade"
            ) { crossfadeSong ->
                SongArtwork(
                    song = crossfadeSong,
                    contentDescription = "Album Art",
                    hasWebp = hasWebp256,
                    size = 256,
                    crossfade = true,
                    iconSize = 80.dp,
                    modifier = Modifier
                        .size(240.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .shadow(8.dp, shape = RoundedCornerShape(20.dp))
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = currentSong?.title ?: "No Song Playing",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = currentSong?.artist ?: "Unknown Artist",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(24.dp))

            PlaybackProgress(
                progressStateFlow = progressStateFlow,
                onSeek = onSeek
            )

            Spacer(modifier = Modifier.height(16.dp))

            PlaybackControls(
                isPlaying = isPlaying,
                onPlayPauseToggle = onPlayPauseToggle,
                onNext = onNext,
                onPrevious = onPrevious
            )
        }
    }
}

@Composable
fun PlaybackProgress(
    progressStateFlow: StateFlow<ProgressState>,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val progress by progressStateFlow.collectAsStateWithLifecycle(ProgressState())
    val currentPosition = progress.currentPosition
    val duration = progress.duration

    var isDragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(0f) }
    val totalDuration = remember(duration) { duration.coerceAtLeast(1L) }

    val progressFraction = if (isDragging) dragPosition
    else (currentPosition.toFloat() / totalDuration).coerceIn(0f, 1f)

    val sliderColors = SliderDefaults.colors(
        thumbColor = Color(0xFFA5B4FC),
        activeTrackColor = Color(0xFF6366F1),
        inactiveTrackColor = Color.White.copy(alpha = 0.15f)
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Slider(
            value = progressFraction,
            onValueChange = { fraction ->
                isDragging = true
                dragPosition = fraction
            },
            onValueChangeFinished = {
                isDragging = false
                onSeek((dragPosition * totalDuration).toLong())
            },
            colors = sliderColors,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatTime(if (isDragging) (dragPosition * totalDuration).toLong() else currentPosition),
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp
            )
            Text(
                text = formatTime(duration),
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun PlaybackControls(
    isPlaying: Boolean,
    onPlayPauseToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevious, modifier = Modifier.size(56.dp)) {
            Icon(
                imageVector = Icons.Default.SkipPrevious,
                contentDescription = "Previous",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(modifier = Modifier.width(24.dp))
        val playButtonBrush = remember {
            Brush.radialGradient(
                colors = listOf(Color(0xFF818CF8), Color(0xFF4F46E5))
            )
        }
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(brush = playButtonBrush)
                .clickable { onPlayPauseToggle() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = "Play/Pause",
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
        }
        Spacer(modifier = Modifier.width(24.dp))
        IconButton(onClick = onNext, modifier = Modifier.size(56.dp)) {
            Icon(
                imageVector = Icons.Default.SkipNext,
                contentDescription = "Next",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}
