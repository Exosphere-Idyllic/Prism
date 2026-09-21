package com.example.prism.ui.components

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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.prism.R
import com.example.prism.data.entity.Song
import kotlinx.collections.immutable.ImmutableSet

import com.example.prism.ui.theme.AppAccentBg
import com.example.prism.ui.theme.AppAccentSoft
import com.example.prism.ui.theme.AppFavoriteColor
import com.example.prism.ui.theme.AppIconInactive
import com.example.prism.ui.theme.AppSurface2
import com.example.prism.ui.theme.AppSurface3
import com.example.prism.ui.theme.AppTextPrimary
import com.example.prism.ui.theme.AppTextSecondary

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
    onEditArtwork: ((Song) -> Unit)? = null,
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
                val isSelected = song.id == currentSong?.id
                val isCurrentPlaying = isSelected && isPlaying
                val isFavorite = favoriteSongIds.contains(song.id)
                SongListItem(
                    song = song,
                    isSelected = isSelected,
                    isCurrentPlaying = isCurrentPlaying,
                    isFavorite = isFavorite,
                    onSongSelected = onSongSelected,
                    onFavoriteToggle = onFavoriteToggle,
                    onAddToPlaylist = onAddToPlaylist,
                    onEditArtwork = onEditArtwork,
                )
            }
        }
    }
}

@Composable
fun SongListItem(
    song: Song,
    isSelected: Boolean,
    isCurrentPlaying: Boolean,
    isFavorite: Boolean,
    onSongSelected: (Song) -> Unit,
    onFavoriteToggle: (Song) -> Unit,
    onAddToPlaylist: ((Song) -> Unit)? = null,
    onEditArtwork: ((Song) -> Unit)? = null,
    customTrailingAction: (@Composable (Song) -> Unit)? = null,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) AppAccentBg else Color.Transparent)
            .clickable(onClick = { onSongSelected(song) })
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Artwork
        SongArtwork(
            song = song,
            contentDescription = stringResource(R.string.cd_album_art),
            size = 128,
            crossfade = false,
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(AppSurface3)
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Title & artist
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = if (isSelected) AppAccentSoft else AppTextPrimary,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = song.artist,
                color = AppTextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Playing bars indicator
        if (isCurrentPlaying) {
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
                            .background(AppAccentSoft)
                    )
                }
            }
        }

        // Favorite
        IconButton(onClick = { onFavoriteToggle(song) }, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = stringResource(R.string.cd_favorite),
                tint = if (isFavorite) AppFavoriteColor else AppIconInactive,
                modifier = Modifier.size(18.dp)
            )
        }

        // Custom trailing action or More options
        if (customTrailingAction != null) {
            customTrailingAction(song)
        } else if (onAddToPlaylist != null || onEditArtwork != null) {
            Box {
                IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.cd_more_options),
                        tint = AppIconInactive,
                        modifier = Modifier.size(18.dp)
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier = Modifier.background(AppSurface2)
                ) {
                    if (onAddToPlaylist != null) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(R.string.add_to_playlist_title),
                                    color = AppTextPrimary,
                                    fontSize = 14.sp
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onAddToPlaylist(song)
                            }
                        )
                    }
                    if (onEditArtwork != null) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(R.string.change_artwork),
                                    color = AppTextPrimary,
                                    fontSize = 14.sp
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onEditArtwork(song)
                            }
                        )
                    }
                }
            }
        }
    }
}
