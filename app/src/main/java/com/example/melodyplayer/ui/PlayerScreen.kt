package com.example.melodyplayer.ui

import androidx.compose.animation.Crossfade
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
import androidx.compose.material3.SliderColors
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.melodyplayer.R
import com.example.melodyplayer.PlaybackViewModel
import com.example.melodyplayer.ProgressState
import com.example.melodyplayer.data.Song
import kotlinx.coroutines.flow.StateFlow

@Composable
fun PlayerScreen(
    viewModel: PlaybackViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlayingState.collectAsStateWithLifecycle()

    // Static dark-to-darker gradient — no colour extraction needed.
    // The AnimatedColor approach was removed because _currentSongColor was never populated.
    val backgroundBrush = remember {
        val bgColor1 = Color(0xFF0F172A)
        val bgColor2 = Color(0xFF020617)
        val topColor = Color(0xFF1E1B4B).copy(alpha = 0.45f)
        Brush.verticalGradient(
            colors = listOf(topColor, bgColor1, bgColor2),
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(brush = backgroundBrush)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val currentOnBack by rememberUpdatedState(onBack)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { currentOnBack() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back),
                        tint = Color.White.copy(alpha = 0.8f)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.now_playing_uppercase),
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.size(48.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))

            val onPlayPauseToggleState by rememberUpdatedState { viewModel.togglePlayPause() }
            val onNextState by rememberUpdatedState { viewModel.next() }
            val onPreviousState by rememberUpdatedState { viewModel.previous() }
            val onSeekState by rememberUpdatedState { ms: Long -> viewModel.seekTo(ms) }

            val onPlayPauseToggle = remember { { onPlayPauseToggleState() } }
            val onNext = remember { { onNextState() } }
            val onPrevious = remember { { onPreviousState() } }
            val onSeek = remember { { ms: Long -> onSeekState(ms) } }

            PlayerCard(
                currentSong = currentSong,
                isPlaying = isPlaying,
                progressStateFlow = viewModel.progressState,
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
    onPlayPauseToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
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
                    contentDescription = stringResource(R.string.cd_album_art),
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
                text = currentSong?.title ?: stringResource(R.string.no_song_playing),
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = currentSong?.artist ?: stringResource(R.string.unknown_artist),
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
    var isDragging by remember { mutableStateOf(value = false) }
    var dragPosition by remember { mutableFloatStateOf(0f) }

    val sliderColors = SliderDefaults.colors(
        thumbColor = Color(0xFFA5B4FC),
        activeTrackColor = Color(0xFF6366F1),
        inactiveTrackColor = Color.White.copy(alpha = 0.15f)
    )

    Column(modifier = modifier.fillMaxWidth()) {
        // Isolate the slider's state reading to avoid recomposing the outer Column
        ProgressSlider(
            progressStateFlow = progressStateFlow,
            isDragging = isDragging,
            dragPosition = dragPosition,
            sliderColors = sliderColors,
            onDragStart = { fraction ->
                isDragging = true
                dragPosition = fraction
            },
            onDragEnd = {
                isDragging = false
            },
            onSeek = onSeek
        )
        ProgressTimestamps(
            progressStateFlow = progressStateFlow,
            isDragging = isDragging,
            dragPosition = dragPosition
        )
    }
}

@Composable
private fun ProgressSlider(
    progressStateFlow: StateFlow<ProgressState>,
    isDragging: Boolean,
    dragPosition: Float,
    sliderColors: SliderColors,
    onDragStart: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onSeek: (Long) -> Unit,
) {
    val progress by progressStateFlow.collectAsStateWithLifecycle(ProgressState())
    val totalDuration = progress.duration.coerceAtLeast(1L)

    val progressFraction = if (isDragging) dragPosition
    else (progress.currentPosition.toFloat() / totalDuration).coerceIn(0f, 1f)

    Slider(
        value = progressFraction,
        onValueChange = { fraction -> onDragStart(fraction) },
        onValueChangeFinished = {
            onDragEnd()
            onSeek((dragPosition * totalDuration).toLong())
        },
        colors = sliderColors,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ProgressTimestamps(
    progressStateFlow: StateFlow<ProgressState>,
    isDragging: Boolean,
    dragPosition: Float,
) {
    val progress by progressStateFlow.collectAsStateWithLifecycle(ProgressState())
    val totalDuration = progress.duration.coerceAtLeast(1L)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = formatTime(if (isDragging) (dragPosition * totalDuration).toLong() else progress.currentPosition),
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 12.sp
        )
        Text(
            text = formatTime(progress.duration),
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 12.sp
        )
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
                contentDescription = stringResource(R.string.cd_previous),
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
                contentDescription = stringResource(R.string.cd_play_pause),
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
        }
        Spacer(modifier = Modifier.width(24.dp))
        IconButton(onClick = onNext, modifier = Modifier.size(56.dp)) {
            Icon(
                imageVector = Icons.Default.SkipNext,
                contentDescription = stringResource(R.string.cd_next),
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}
