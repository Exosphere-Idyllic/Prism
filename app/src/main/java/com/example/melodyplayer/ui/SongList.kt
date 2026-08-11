package com.example.melodyplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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

// ─── Design tokens ────────────────────────────────────────────────────────────
private val SelectedBg       = Color(0x1A6366F1)
private val ArtworkBg        = Color(0xFF151B26)
private val TitleSelected    = Color(0xFF818CF8)
private val TitleNormal      = Color(0xFFF1F5F9)
private val SubtitleColor    = Color(0xFF64748B)
private val FavoriteActive   = Color(0xFFEF4444)
private val IconInactive     = Color(0x59F1F5F9)
private val PlayingBarColor  = Color(0xFF818CF8)

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
        verticalArrangement = Arrangement.spacedBy(2.dp)
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
    onAddToPlaylist: ((Song) -> Unit)? = null
) {
    SongListItem(
        song = song,
        isSelected = song.id == currentSongId,
        isPlaying = (song.id == currentSongId) && isPlaying,
        isFavorite = isFavorite,
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
    onSongSelected: (Song) -> Unit,
    onFavoriteToggle: (Song) -> Unit,
    onAddToPlaylist: ((Song) -> Unit)? = null
) {
    val currentOnSongSelected by rememberUpdatedState(onSongSelected)
    val currentOnFavoriteToggle by rememberUpdatedState(onFavoriteToggle)
    val currentOnAddToPlaylist by rememberUpdatedState(onAddToPlaylist)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) SelectedBg else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { currentOnSongSelected(song) }
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Artwork
        SongArtwork(
            song = song,
            contentDescription = stringResource(R.string.cd_album_art),
            size = 128,
            crossfade = false,
            iconSize = 22.dp,
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(ArtworkBg)
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Title & artist
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = if (isSelected) TitleSelected else TitleNormal,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = song.artist,
                color = SubtitleColor,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Playing bars indicator
        if (isPlaying) {
            Spacer(modifier = Modifier.width(6.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(3) { i ->
                    Box(
                        modifier = Modifier
                            .size(width = 2.5.dp, height = if (i == 1) 14.dp else 8.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(PlayingBarColor)
                    )
                }
            }
        }

        // Favorite
        IconButton(onClick = { currentOnFavoriteToggle(song) }, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = stringResource(R.string.cd_favorite),
                tint = if (isFavorite) FavoriteActive else IconInactive,
                modifier = Modifier.size(18.dp)
            )
        }

        // More options
        if (onAddToPlaylist != null) {
            IconButton(onClick = { currentOnAddToPlaylist?.invoke(song) }, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.cd_more_options),
                    tint = IconInactive,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
