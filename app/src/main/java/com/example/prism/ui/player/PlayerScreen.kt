package com.example.prism.ui.player

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.prism.R
import com.example.prism.ui.player.PlaybackViewModel
import com.example.prism.ui.player.ProgressState
import com.example.prism.core.util.formatTime
import com.example.prism.ui.components.SongArtwork
import kotlinx.coroutines.flow.StateFlow

import com.example.prism.ui.theme.AppAccent
import com.example.prism.ui.theme.AppAccentSoft
import com.example.prism.ui.theme.AppBgBottom
import com.example.prism.ui.theme.AppBgTop
import com.example.prism.ui.theme.AppSurface2
import com.example.prism.ui.theme.AppTextPrimary
import com.example.prism.ui.theme.AppTextSecondary
import com.example.prism.ui.theme.AppTrackBg

// ─── Color tokens mapped from central theme ──────────────────────────────────
private val BgTop         = AppBgTop
private val BgBottom      = AppBgBottom
private val Accent        = AppAccent
private val AccentSoft    = AppAccentSoft
private val Surface1      = AppSurface2
private val TextPrimary   = AppTextPrimary
private val TextSecondary = AppTextSecondary
private val TrackBg       = AppTrackBg

@Composable
fun PlayerScreen(
    viewModel: PlaybackViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlayingState.collectAsStateWithLifecycle()

    PlayerContent(
        currentSong = currentSong,
        isPlaying = isPlaying,
        progressState = viewModel.progressState,
        onBack = onBack,
        onPlayPauseToggle = { viewModel.togglePlayPause() },
        onNext = { viewModel.next() },
        onPrevious = { viewModel.previous() },
        onSeek = { ms -> viewModel.seekTo(ms) },
        modifier = modifier,
    )
}

@Composable
fun PlayerContent(
    currentSong: com.example.prism.data.entity.Song?,
    isPlaying: Boolean,
    progressState: StateFlow<ProgressState>,
    onBack: () -> Unit,
    onPlayPauseToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
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
            val artistText = currentSong?.artist
            Text(
                text = if (artistText.isNullOrBlank() || artistText.equals("Unknown", ignoreCase = true)) {
                    stringResource(R.string.unknown_artist)
                } else {
                    artistText
                },
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
                progressStateFlow = progressState,
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

    // Collect once here and pass the value down — avoids two independent
    // subscriptions (one in ProgressSlider, one in ProgressTimestamps) that
    // each trigger their own recomposition on every 250 ms poller tick.
    val progress by progressStateFlow.collectAsStateWithLifecycle(ProgressState())

    val sliderColors = SliderDefaults.colors(
        thumbColor = AccentSoft,
        activeTrackColor = Accent,
        inactiveTrackColor = TrackBg
    )

    Column(modifier = modifier.fillMaxWidth()) {
        ProgressSlider(
            progress = progress,
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
            progress = progress,
            isDragging = isDragging,
            dragPosition = dragPosition
        )
    }
}

@Composable
private fun ProgressSlider(
    progress: ProgressState,
    isDragging: Boolean,
    dragPosition: Float,
    sliderColors: SliderColors,
    onDragStart: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onSeek: (Long) -> Unit,
) {
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
    progress: ProgressState,
    isDragging: Boolean,
    dragPosition: Float,
) {
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
                    interactionSource = null,
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
                    interactionSource = null,
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
                    interactionSource = null,
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
