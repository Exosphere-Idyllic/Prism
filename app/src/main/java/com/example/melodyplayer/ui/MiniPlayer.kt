package com.example.melodyplayer.ui

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
import com.example.melodyplayer.ProgressState
import com.example.melodyplayer.data.Song
import kotlinx.coroutines.flow.StateFlow

@Composable
fun MiniPlayer(
    song: Song,
    isPlaying: Boolean,
    progressStateFlow: StateFlow<ProgressState>,
    hasWebp: Boolean,
    onPlayPauseToggle: () -> Unit,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(24.dp, RoundedCornerShape(20.dp))
            .clickable { onOpenPlayer() },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E30)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column {
            MiniPlayerProgressBar(progressStateFlow)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SongArtwork(
                    song = song,
                    contentDescription = "Mini player art",
                    hasWebp = hasWebp,
                    size = 128,
                    crossfade = false,
                    iconSize = 20.dp,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = song.artist,
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF6366F1))
                        .clickable { onPlayPauseToggle() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun MiniPlayerProgressBar(progressStateFlow: StateFlow<ProgressState>) {
    val progress by progressStateFlow.collectAsStateWithLifecycle(ProgressState())
    val progressFraction = if (progress.duration > 0) {
        (progress.currentPosition.toFloat() / progress.duration.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(Color.White.copy(alpha = 0.08f))
    ) {
        val progressBrush = remember {
            Brush.horizontalGradient(
                colors = listOf(Color(0xFF818CF8), Color(0xFF6366F1))
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth(progressFraction)
                .fillMaxHeight()
                .background(brush = progressBrush)
        )
    }
}
