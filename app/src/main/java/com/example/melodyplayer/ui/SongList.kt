package com.example.melodyplayer.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import com.example.melodyplayer.LibraryViewModel
import com.example.melodyplayer.data.Song
import kotlinx.coroutines.flow.debounce
import kotlin.time.Duration.Companion.milliseconds

private const val THUMBNAIL_PREFETCH_BUFFER = 5

@OptIn(kotlinx.coroutines.FlowPreview::class)
@Composable
fun SongList(
    songs: LazyPagingItems<Song>,
    currentSong: Song?,
    isPlaying: Boolean,
    favoriteSongIds: Set<String>,
    songThumbnail128Ids: Set<String>,
    onSongSelected: (Song) -> Unit,
    onFavoriteToggle: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    libraryViewModel: LibraryViewModel,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val latestThumbnail128Ids by rememberUpdatedState(songThumbnail128Ids)
    val requestedThumbnailIds = remember { mutableSetOf<String>() }

    LaunchedEffect(listState, songs.itemCount) {
        snapshotFlow {
            val visible = listState.layoutInfo.visibleItemsInfo
            if (visible.isEmpty()) null
            else visible.first().index to visible.last().index
        }
            .debounce(120.milliseconds)
            .collect { range ->
                if (range == null || songs.itemCount == 0) return@collect
                val firstIndex = (range.first - THUMBNAIL_PREFETCH_BUFFER).coerceAtLeast(0)
                val lastIndex = (range.second + THUMBNAIL_PREFETCH_BUFFER).coerceAtMost(songs.itemCount - 1)

                for (i in firstIndex..lastIndex) {
                    val song = songs.peek(i) ?: continue
                    if (song.id in requestedThumbnailIds) continue
                    if (latestThumbnail128Ids.contains(song.id)) continue
                    requestedThumbnailIds.add(song.id)
                    libraryViewModel.requestSongThumbnail(song)
                }
            }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(
            count = songs.itemCount,
            key = songs.itemKey { it.id },
            contentType = songs.itemContentType { "song" }
        ) { index ->
            val song = songs[index]
            if (song != null) {
                val isFavorite = favoriteSongIds.contains(song.id)
                val hasWebp = songThumbnail128Ids.contains(song.id)

                SongListItemWrapper(
                    song = song,
                    currentSongId = currentSong?.id,
                    isPlaying = isPlaying,
                    isFavorite = isFavorite,
                    hasWebp = hasWebp,
                    onSongSelected = onSongSelected,
                    onFavoriteToggle = onFavoriteToggle,
                    onAddToPlaylist = onAddToPlaylist
                )
            }
        }
        item(contentType = "spacer") { Spacer(modifier = Modifier.height(96.dp)) }
    }
}

@Composable
fun SongListItemWrapper(
    song: Song,
    currentSongId: String?,
    isPlaying: Boolean,
    isFavorite: Boolean,
    hasWebp: Boolean,
    onSongSelected: (Song) -> Unit,
    onFavoriteToggle: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit
) {
    val isSelected = song.id == currentSongId
    val activePlayingState = isSelected && isPlaying

    SongListItem(
        song = song,
        isSelected = isSelected,
        isPlaying = activePlayingState,
        isFavorite = isFavorite,
        hasWebp = hasWebp,
        onSongSelected = onSongSelected,
        onFavoriteToggle = onFavoriteToggle,
        onAddToPlaylist = onAddToPlaylist
    )
}

@Composable
fun SongListItem(
    song: Song,
    isSelected: Boolean,
    isPlaying: Boolean,
    isFavorite: Boolean,
    hasWebp: Boolean,
    onSongSelected: (Song) -> Unit,
    onFavoriteToggle: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit
) {
    val bgColor = if (isSelected) Color(0xFF6366F1).copy(alpha = 0.15f) else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable { onSongSelected(song) }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SongArtwork(
            song = song,
            contentDescription = "Album art",
            hasWebp = hasWebp,
            size = 128,
            crossfade = false,
            iconSize = 24.dp,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.06f))
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = if (isSelected) Color(0xFFA5B4FC) else Color.White,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = song.artist,
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(onClick = { onFavoriteToggle(song) }) {
            Icon(
                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = "Favorito",
                tint = if (isFavorite) Color(0xFFEF4444) else Color.White.copy(alpha = 0.35f),
                modifier = Modifier.size(20.dp)
            )
        }

        IconButton(onClick = { onAddToPlaylist(song) }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Mas opciones",
                tint = Color.White.copy(alpha = 0.45f),
                modifier = Modifier.size(20.dp)
            )
        }

        if (isPlaying) {
            Spacer(modifier = Modifier.width(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(3) { i ->
                    Box(
                        modifier = Modifier
                            .size(width = 3.dp, height = if (i == 1) 14.dp else 9.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFF818CF8))
                    )
                }
            }
        }
    }
}
