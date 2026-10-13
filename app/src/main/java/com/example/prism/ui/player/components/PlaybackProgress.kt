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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.prism.core.util.formatTime
import com.example.prism.player.ProgressState
import com.example.prism.ui.theme.AppAccent
import com.example.prism.ui.theme.AppAccentSoft
import com.example.prism.ui.theme.AppTextSecondary
import com.example.prism.ui.theme.AppTrackBg
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy

@Composable
fun PlaybackProgress(
    progressStateFlow: StateFlow<ProgressState>,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    // ── Drag state ─────────────────────────────────────────────────────────
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var optimisticSeekMs by remember { mutableStateOf<Long?>(null) }

    // ── Progress state, split to minimize recompositions ───────────────────
    // duration changes infrequently (only on track change) — read in composition is fine.
    var durationMs by remember { mutableLongStateOf(progressStateFlow.value.duration) }
    // currentPosition changes every 250ms — collected separately so we can
    // gate recompositions with derivedStateOf.
    var rawPositionMs by remember { mutableLongStateOf(progressStateFlow.value.currentPosition) }

    // Collect duration changes only when duration actually changes.
    LaunchedEffect(progressStateFlow) {
        progressStateFlow
            .distinctUntilChangedBy { it.duration }
            .collectLatest { state ->
                if (state.duration > 0L) durationMs = state.duration
            }
    }

    // Collect position updates — but skip them while dragging so the thumb
    // doesn't fight the user's touch input.
    LaunchedEffect(progressStateFlow) {
        progressStateFlow
            .distinctUntilChangedBy { it.currentPosition }
            .collectLatest { state ->
                if (!isDragging) rawPositionMs = state.currentPosition
            }
    }

    // Resolve optimistic seek: once the real position converges on the target, clear it.
    LaunchedEffect(rawPositionMs) {
        optimisticSeekMs?.let { target ->
            if (kotlin.math.abs(rawPositionMs - target) < 1000L) {
                optimisticSeekMs = null
            }
        }
    }

    // derivedStateOf ensures the Slider only recomposes when the *fraction* changes,
    // not on every raw Long tick (avoids redundant float comparisons triggering layout).
    val totalDuration by remember { derivedStateOf { durationMs.coerceAtLeast(1L) } }

    val progressFraction by remember {
        derivedStateOf {
            when {
                isDragging -> dragFraction
                optimisticSeekMs != null -> (optimisticSeekMs!!.toFloat() / totalDuration).coerceIn(0f, 1f)
                else -> (rawPositionMs.toFloat() / totalDuration).coerceIn(0f, 1f)
            }
        }
    }

    val displayPosition by remember {
        derivedStateOf {
            when {
                isDragging -> (dragFraction * totalDuration).toLong()
                optimisticSeekMs != null -> optimisticSeekMs!!
                else -> rawPositionMs
            }
        }
    }

    val sliderColors = SliderDefaults.colors(
        thumbColor = AppAccentSoft,
        activeTrackColor = AppAccent,
        inactiveTrackColor = AppTrackBg,
    )

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
                text = formatTime(durationMs),
                color = AppTextSecondary,
                fontSize = 12.sp,
            )
        }
    }
}
