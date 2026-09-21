package com.example.prism.ui.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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
import com.example.prism.R
import com.example.prism.ui.theme.AppAccent
import com.example.prism.ui.theme.AppAccentSoft
import com.example.prism.ui.theme.AppSurface2
import com.example.prism.ui.theme.AppTextPrimary

@Composable
fun PlaybackControls(
    isPlaying: Boolean,
    onPlayPauseToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playButtonBrush = remember {
        Brush.linearGradient(colors = listOf(AppAccentSoft, AppAccent))
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
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

        Spacer(modifier = Modifier.width(20.dp))

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

        Spacer(modifier = Modifier.width(20.dp))

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
    }
}
