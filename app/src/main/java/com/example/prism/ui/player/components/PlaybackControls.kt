package com.example.prism.ui.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.example.prism.R
import com.example.prism.ui.theme.AppAccent
import com.example.prism.ui.theme.AppAccentSoft
import com.example.prism.ui.theme.AppSurface2
import com.example.prism.ui.theme.AppTextPrimary
import com.example.prism.ui.theme.AppTextSecondary

@Composable
fun PlaybackControls(
    isPlaying: Boolean,
    shuffleEnabled: Boolean,
    repeatMode: Int,
    onPlayPauseToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playButtonBrush = remember {
        Brush.linearGradient(colors = listOf(AppAccentSoft, AppAccent))
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Shuffle
        IconButton(
            onClick = onToggleShuffle,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (shuffleEnabled) AppAccentSoft.copy(alpha = 0.2f) else AppSurface2),
        ) {
            Icon(
                imageVector = Icons.Default.Shuffle,
                contentDescription = stringResource(R.string.cd_shuffle),
                tint = if (shuffleEnabled) AppAccentSoft else AppTextSecondary,
                modifier = Modifier.size(22.dp),
            )
        }

        // Previous
        IconButton(
            onClick = onPrevious,
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(AppSurface2),
        ) {
            Icon(
                imageVector = Icons.Default.SkipPrevious,
                contentDescription = stringResource(R.string.cd_previous),
                tint = AppTextPrimary,
                modifier = Modifier.size(28.dp),
            )
        }

        // Play / Pause
        Box(
            modifier = Modifier
                .size(68.dp)
                .shadow(16.dp, CircleShape)
                .clip(CircleShape)
                .background(brush = playButtonBrush)
                .clickable(
                    role = Role.Button,
                    onClick = onPlayPauseToggle,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = stringResource(R.string.cd_play_pause),
                tint = Color.White,
                modifier = Modifier.size(34.dp),
            )
        }

        // Next
        IconButton(
            onClick = onNext,
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(AppSurface2),
        ) {
            Icon(
                imageVector = Icons.Default.SkipNext,
                contentDescription = stringResource(R.string.cd_next),
                tint = AppTextPrimary,
                modifier = Modifier.size(28.dp),
            )
        }

        // Repeat (OFF -> ALL -> ONE)
        val isRepeatActive = repeatMode != Player.REPEAT_MODE_OFF
        val repeatIcon = if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat

        IconButton(
            onClick = onToggleRepeat,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (isRepeatActive) AppAccentSoft.copy(alpha = 0.2f) else AppSurface2),
        ) {
            Icon(
                imageVector = repeatIcon,
                contentDescription = stringResource(R.string.cd_repeat),
                tint = if (isRepeatActive) AppAccentSoft else AppTextSecondary,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
