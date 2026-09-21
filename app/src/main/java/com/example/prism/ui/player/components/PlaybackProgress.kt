package com.example.prism.ui.player.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.prism.core.util.formatTime
import com.example.prism.player.ProgressState
import com.example.prism.ui.theme.AppAccent
import com.example.prism.ui.theme.AppAccentSoft
import com.example.prism.ui.theme.AppTextSecondary
import com.example.prism.ui.theme.AppTrackBg
import kotlinx.coroutines.flow.StateFlow

@Composable
fun PlaybackProgress(
    progressStateFlow: StateFlow<ProgressState>,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var optimisticSeekMs by remember { mutableStateOf<Long?>(null) }

    val progress by progressStateFlow.collectAsStateWithLifecycle(ProgressState())

    LaunchedEffect(progress.currentPosition) {
        optimisticSeekMs?.let { target ->
            if (kotlin.math.abs(progress.currentPosition - target) < 1000L) {
                optimisticSeekMs = null
            }
        }
    }

    val sliderColors = SliderDefaults.colors(
        thumbColor = AppAccentSoft,
        activeTrackColor = AppAccent,
        inactiveTrackColor = AppTrackBg,
    )

    val totalDuration = progress.duration.coerceAtLeast(1L)
    val progressFraction = when {
        isDragging -> dragFraction
        optimisticSeekMs != null -> (optimisticSeekMs!!.toFloat() / totalDuration).coerceIn(0f, 1f)
        else -> (progress.currentPosition.toFloat() / totalDuration).coerceIn(0f, 1f)
    }

    val displayPosition = when {
        isDragging -> (dragFraction * totalDuration).toLong()
        optimisticSeekMs != null -> optimisticSeekMs!!
        else -> progress.currentPosition
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Slider(
            value = progressFraction,
            onValueChange = { fraction ->
                isDragging = true
                dragFraction = fraction
            },
            onValueChangeFinished = {
                val targetMs = (dragFraction * totalDuration).toLong()
                isDragging = false
                optimisticSeekMs = targetMs
                onSeek(targetMs)
            },
            colors = sliderColors,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatTime(displayPosition),
                color = AppTextSecondary,
                fontSize = 12.sp,
            )
            Text(
                text = formatTime(progress.duration),
                color = AppTextSecondary,
                fontSize = 12.sp,
            )
        }
    }
}
