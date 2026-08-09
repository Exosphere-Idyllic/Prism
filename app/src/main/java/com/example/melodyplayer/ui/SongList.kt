package com.example.melodyplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import com.example.melodyplayer.R
import com.example.melodyplayer.data.Song
import kotlinx.collections.immutable.ImmutableSet


@Composable
fun SongList(
    songs: LazyPagingItems<Song>,
    currentSong: Song?,
    isPlaying: Boolean,
    favoriteSongIds: ImmutableSet<String>,
    onSongSelected: (Song) -> Unit,
    onFavoriteToggle: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 100.dp),
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

                SongListItemWrapper(
                    song = song,
                    currentSongId = currentSong?.id,
                    isPlaying = isPlaying,
                    isFavorite = isFavorite,
                    onSongSelected = onSongSelected,
                    onFavoriteToggle = onFavoriteToggle,
                    onAddToPlaylist = onAddToPlaylist
                )
            }
        }
    }
}

@Composable
fun SongListItemWrapper(
    song: Song,
    currentSongId: String?,
    isPlaying: Boolean,
    isFavorite: Boolean,
    onSongSelected: (Song) -> Unit,
    onFavoriteToggle: (Song) -> Unit,
    onAddToPlaylist: ((Song) -> Unit)? = null  // null = ocultar el botón
) {
    val isSelected = song.id == currentSongId
    val activePlayingState = isSelected && isPlaying

    SongListItem(
        song = song,
        isSelected = isSelected,
        isPlaying = activePlayingState,
        isFavorite = isFavorite,
        onSongSelected = onSongSelected,
        onFavoriteToggle = onFavoriteToggle,
        onAddToPlaylist = onAddToPlaylist
    )
}

private val SelectedItemBgColor = Color(0x266366F1)
private val PlaceholderArtworkBgColor = Color(0x0FFFFFFF)
private val ArtistTextColor = Color(0x73FFFFFF)
private val InactiveIconTint = Color(0x59FFFFFF)

@Composable
fun SongListItem(
    song: Song,
    isSelected: Boolean,
    isPlaying: Boolean,
    isFavorite: Boolean,
    onSongSelected: (Song) -> Unit,
    onFavoriteToggle: (Song) -> Unit,
    onAddToPlaylist: ((Song) -> Unit)? = null  // null = ocultar el botón
) {
    val currentOnSongSelected by rememberUpdatedState(onSongSelected)
    val currentOnFavoriteToggle by rememberUpdatedState(onFavoriteToggle)
    val currentOnAddToPlaylist by rememberUpdatedState(onAddToPlaylist)

    val bgColor = if (isSelected) SelectedItemBgColor else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable { currentOnSongSelected(song) }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SongArtwork(
            song = song,
            contentDescription = stringResource(R.string.cd_album_art),
            size = 128,
            crossfade = false,
            iconSize = 24.dp,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(PlaceholderArtworkBgColor)
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
                color = ArtistTextColor,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(onClick = { currentOnFavoriteToggle(song) }) {
            Icon(
                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = stringResource(R.string.cd_favorite),
                tint = if (isFavorite) Color(0xFFEF4444) else InactiveIconTint,
                modifier = Modifier.size(20.dp)
            )
        }

        // FIX #11: solo mostrar si hay una acción real asignada
        if (onAddToPlaylist != null) {
            IconButton(onClick = { currentOnAddToPlaylist?.invoke(song) }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.cd_more_options),
                    tint = Color.White.copy(alpha = 0.45f),
                    modifier = Modifier.size(20.dp)
                )
            }
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
