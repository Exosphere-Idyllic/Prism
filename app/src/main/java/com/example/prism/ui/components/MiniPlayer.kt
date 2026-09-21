package com.example.prism.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.prism.R
import com.example.prism.data.entity.Song
import com.example.prism.player.ProgressState
import com.example.prism.ui.theme.*
import kotlinx.coroutines.flow.StateFlow

@Composable
fun MiniPlayer(
    song: Song,
    isPlaying: Boolean,
    progressStateFlow: StateFlow<ProgressState>,
    onPlayPauseToggle: () -> Unit,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progressState = progressStateFlow.collectAsStateWithLifecycle()

    MiniPlayer(
        song = song,
        isPlaying = isPlaying,
        progressFraction = {
            val current = progressState.value
            if (current.duration > 0L) {
                (current.currentPosition.toFloat() / current.duration.toFloat()).coerceIn(0f, 1f)
            } else 0f
        },
        onPlayPauseToggle = onPlayPauseToggle,
        onOpenPlayer = onOpenPlayer,
        modifier = modifier,
    )
}

@Composable
fun MiniPlayer(
    song: Song,
    isPlaying: Boolean,
    progressFraction: () -> Float,
    onPlayPauseToggle: () -> Unit,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnOpenPlayer by rememberUpdatedState(onOpenPlayer)
    val currentOnPlayPauseToggle by rememberUpdatedState(onPlayPauseToggle)

    Card(
        onClick = currentOnOpenPlayer,
        modifier = modifier
            .fillMaxWidth()
            .shadow(20.dp, RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(containerColor = AppSurface),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column {
            // Thin progress indicator at the very top of the card (draw phase only)
            MiniPlayerProgressBar(progressFraction = progressFraction)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Artwork
                SongArtwork(
                    song = song,
                    contentDescription = stringResource(R.string.cd_mini_player_art),
                    size = 128,
                    crossfade = false,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(AppTrackOverlay)
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Title & artist
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        color = AppTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = song.artist,
                        color = AppTextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Play / Pause button with tactile ripple
                val playBrush = remember {
                    Brush.linearGradient(listOf(AppAccentSoft, AppAccent))
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(playBrush)
                        .clickable(onClick = currentOnPlayPauseToggle),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = stringResource(R.string.cd_play_pause),
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun MiniPlayerProgressBar(
    progressFraction: () -> Float,
    modifier: Modifier = Modifier,
) {
    val gradientColors = remember { listOf(AppAccentSoft, AppAccent) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(2.dp)
            .drawBehind {
                // Track
                drawRect(color = AppTrackOverlay)
                val fraction = progressFraction().coerceIn(0f, 1f)
                // Filled portion
                drawRect(
                    brush = Brush.horizontalGradient(gradientColors),
                    size = androidx.compose.ui.geometry.Size(size.width * fraction, size.height)
                )
            }
    )
}
