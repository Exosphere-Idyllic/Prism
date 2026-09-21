package com.example.prism.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.prism.R
import com.example.prism.data.entity.Song
import com.example.prism.ui.theme.*
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet

@Composable
fun DetailTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.cd_back),
                tint = AppTextPrimary
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            color = AppTextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun DetailHeroHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    actionButtonText: String? = null,
    onActionClick: (() -> Unit)? = null,
    artwork: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        artwork()
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = title,
            color = AppTextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (subtitle.isNotEmpty()) {
            Text(
                text = subtitle,
                color = AppTextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, start = 24.dp, end = 24.dp)
            )
        }
        if (actionButtonText != null && onActionClick != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onActionClick,
                colors = ButtonDefaults.buttonColors(containerColor = AppAccent),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(actionButtonText)
            }
        }
    }
}

@Composable
fun DetailSongList(
    songs: ImmutableList<Song>,
    currentSongId: String?,
    isPlaying: Boolean,
    favoriteSongIds: ImmutableSet<String>,
    onSongSelected: (Song) -> Unit,
    onFavoriteToggle: (Song) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    trailingAction: (@Composable (Song) -> Unit)? = null,
) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(songs, key = { it.id }) { song ->
            val isSelected = song.id == currentSongId
            val isCurrentPlaying = isSelected && isPlaying
            val isFavorite = favoriteSongIds.contains(song.id)

            SongListItem(
                song = song,
                isSelected = isSelected,
                isCurrentPlaying = isCurrentPlaying,
                isFavorite = isFavorite,
                onSongSelected = onSongSelected,
                onFavoriteToggle = onFavoriteToggle,
                customTrailingAction = trailingAction
            )
        }
    }
}
