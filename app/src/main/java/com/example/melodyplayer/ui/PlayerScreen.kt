package com.example.melodyplayer.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.melodyplayer.R
import com.example.melodyplayer.PlaybackViewModel
import com.example.melodyplayer.ProgressState
import com.example.melodyplayer.data.Song
import kotlinx.coroutines.flow.StateFlow

// ─── Color tokens ────────────────────────────────────────────────────────────
private val BgTop     = Color(0xFF0D1117)
private val BgBottom  = Color(0xFF070B10)
private val Accent    = Color(0xFF6366F1)
private val AccentSoft = Color(0xFF818CF8)
private val Surface1  = Color(0xFF141B2D)
private val TextPrimary   = Color(0xFFF1F5F9)
private val TextSecondary = Color(0xFF64748B)
private val TrackBg   = Color(0xFF1E293B)

@Composable
fun PlayerScreen(
    viewModel: PlaybackViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlayingState.collectAsStateWithLifecycle()

    val backgroundBrush = remember {
        Brush.verticalGradient(colors = listOf(BgTop, BgBottom))
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
            // ── Top bar ────────────────────────────────────────────────────
            val currentOnBack by rememberUpdatedState(onBack)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = { currentOnBack() },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Surface1)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back),
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = stringResource(R.string.now_playing_uppercase),
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.5.sp
                )
                // Balance spacer
                Spacer(modifier = Modifier.size(40.dp))
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Stable callbacks ───────────────────────────────────────────
            val onPlayPauseToggleState by rememberUpdatedState { viewModel.togglePlayPause() }
            val onNextState by rememberUpdatedState { viewModel.next() }
            val onPreviousState by rememberUpdatedState { viewModel.previous() }
            val onSeekState by rememberUpdatedState { ms: Long -> viewModel.seekTo(ms) }

            val onPlayPauseToggle = remember { { onPlayPauseToggleState() } }
            val onNext = remember { { onNextState() } }
            val onPrevious = remember { { onPreviousState() } }
            val onSeek = remember { { ms: Long -> onSeekState(ms) } }

            // ── Artwork ────────────────────────────────────────────────────
            Crossfade(
                targetState = currentSong,
                animationSpec = tween(400, easing = FastOutSlowInEasing),
                label = "albumArtCrossfade"
            ) { crossfadeSong ->
                Box(
                    modifier = Modifier
                        .size(300.dp)
                        .shadow(32.dp, RoundedCornerShape(24.dp))
                        .clip(RoundedCornerShape(24.dp))
                        .background(Surface1)
                ) {
                    SongArtwork(
                        song = crossfadeSong,
                        contentDescription = stringResource(R.string.cd_album_art),
                        size = 512,
                        crossfade = true,
                        iconSize = 80.dp,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ── Song info ──────────────────────────────────────────────────
            Text(
                text = currentSong?.title ?: stringResource(R.string.no_song_playing),
                color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = currentSong?.artist ?: stringResource(R.string.unknown_artist),
                color = TextSecondary,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(28.dp))

            // ── Progress ───────────────────────────────────────────────────
            PlaybackProgress(
                progressStateFlow = viewModel.progressState,
                onSeek = onSeek
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ── Controls ───────────────────────────────────────────────────
            PlaybackControls(
                isPlaying = isPlaying,
                onPlayPauseToggle = onPlayPauseToggle,
                onNext = onNext,
                onPrevious = onPrevious
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun PlaybackProgress(
    progressStateFlow: StateFlow<ProgressState>,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(0f) }

    val sliderColors = SliderDefaults.colors(
        thumbColor = AccentSoft,
        activeTrackColor = Accent,
        inactiveTrackColor = TrackBg
    )

    Column(modifier = modifier.fillMaxWidth()) {
        ProgressSlider(
            progressStateFlow = progressStateFlow,
            isDragging = isDragging,
            dragPosition = dragPosition,
            sliderColors = sliderColors,
            onDragStart = { fraction ->
                isDragging = true
                dragPosition = fraction
            },
            onDragEnd = { isDragging = false },
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
            color = TextSecondary,
            fontSize = 12.sp
        )
        Text(
            text = formatTime(progress.duration),
            color = TextSecondary,
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
    val playButtonBrush = remember {
        Brush.linearGradient(colors = listOf(AccentSoft, Accent))
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Previous
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(Surface1)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onPrevious
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.SkipPrevious,
                contentDescription = stringResource(R.string.cd_previous),
                tint = TextPrimary,
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(modifier = Modifier.width(20.dp))

        // Play / Pause
        Box(
            modifier = Modifier
                .size(68.dp)
                .shadow(16.dp, CircleShape)
                .clip(CircleShape)
                .background(brush = playButtonBrush)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onPlayPauseToggle
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = stringResource(R.string.cd_play_pause),
                tint = Color.White,
                modifier = Modifier.size(34.dp)
            )
        }

        Spacer(modifier = Modifier.width(20.dp))

        // Next
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(Surface1)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onNext
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.SkipNext,
                contentDescription = stringResource(R.string.cd_next),
                tint = TextPrimary,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}
