package com.example.prism.ui.player.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.prism.R
import com.example.prism.domain.model.LyricsLine
import com.example.prism.domain.model.LyricsSource
import com.example.prism.domain.model.SongLyrics
import com.example.prism.ui.theme.AppAccent
import com.example.prism.ui.theme.AppAccentSoft
import com.example.prism.ui.theme.AppSurface2
import com.example.prism.ui.theme.AppTextPrimary
import com.example.prism.ui.theme.AppTextSecondary

@Composable
fun LyricsDisplay(
    songLyrics: SongLyrics?,
    currentPositionMs: Long,
    onSelectSource: (LyricsSource) -> Unit,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeContent = songLyrics?.activeLyrics

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(24.dp))
            .background(AppSurface2.copy(alpha = 0.5f)),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Source selector / header bar
            LyricsHeader(
                songLyrics = songLyrics,
                onSelectSource = onSelectSource,
            )

            if (activeContent == null || activeContent.lines.isEmpty()) {
                EmptyLyricsView(modifier = Modifier.weight(1f))
            } else if (activeContent.isSynced) {
                SyncedLyricsView(
                    lines = activeContent.lines,
                    currentPositionMs = currentPositionMs,
                    onSeekTo = onSeekTo,
                    modifier = Modifier.weight(1f),
                )
            } else {
                UnsyncedLyricsView(
                    lines = activeContent.lines,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun LyricsHeader(
    songLyrics: SongLyrics?,
    onSelectSource: (LyricsSource) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val active = songLyrics?.activeLyrics
        val syncLabel = if (active?.isSynced == true) {
            stringResource(R.string.lyrics_synced)
        } else if (active != null) {
            stringResource(R.string.lyrics_unsynced)
        } else {
            ""
        }

        Text(
            text = syncLabel,
            color = AppAccentSoft,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
        )

        // Source switcher if both sources exist, or tag indicator
        if (songLyrics?.hasBothSources == true) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = songLyrics.selectedSource == LyricsSource.LRC_FILE,
                    onClick = { onSelectSource(LyricsSource.LRC_FILE) },
                    label = { Text(stringResource(R.string.lyrics_source_lrc), fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AppAccent,
                        selectedLabelColor = Color.White,
                        containerColor = AppSurface2,
                        labelColor = AppTextSecondary,
                    ),
                )
                FilterChip(
                    selected = songLyrics.selectedSource == LyricsSource.EMBEDDED,
                    onClick = { onSelectSource(LyricsSource.EMBEDDED) },
                    label = { Text(stringResource(R.string.lyrics_source_embedded), fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AppAccent,
                        selectedLabelColor = Color.White,
                        containerColor = AppSurface2,
                        labelColor = AppTextSecondary,
                    ),
                )
            }
        } else if (songLyrics?.activeLyrics != null) {
            val sourceText = when (songLyrics.activeLyrics?.source) {
                LyricsSource.EMBEDDED -> stringResource(R.string.lyrics_source_embedded)
                LyricsSource.LRC_FILE -> stringResource(R.string.lyrics_source_lrc)
                null -> ""
            }
            Text(
                text = sourceText,
                color = AppTextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun SyncedLyricsView(
    lines: List<LyricsLine>,
    currentPositionMs: Long,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    // Find active line index
    val activeIndex = remember(lines, currentPositionMs) {
        lines.indexOfLast { it.timestampMs <= currentPositionMs }
    }

    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0) {
            val targetIndex = (activeIndex - 2).coerceAtLeast(0)
            listState.animateScrollToItem(targetIndex)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        itemsIndexed(lines) { index, line ->
            val isActive = index == activeIndex
            val textColor by animateColorAsState(
                targetValue = if (isActive) Color.White else AppTextSecondary.copy(alpha = 0.5f),
                animationSpec = tween(durationMillis = 250),
                label = "lyricsColor",
            )

            Text(
                text = line.text,
                color = textColor,
                fontSize = if (isActive) 20.sp else 16.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSeekTo(line.timestampMs) }
                    .padding(vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun UnsyncedLyricsView(
    lines: List<LyricsLine>,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(lines) { _, line ->
            Text(
                text = line.text,
                color = AppTextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun EmptyLyricsView(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(AppSurface2),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Lyrics,
                contentDescription = null,
                tint = AppTextSecondary,
                modifier = Modifier.size(32.dp),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.no_lyrics_available),
            color = AppTextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "LRC sidecar or embedded ID3 tags will appear here automatically.",
            color = AppTextSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}
