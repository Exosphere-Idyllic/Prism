package com.example.prism.ui.album

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.prism.R
import com.example.prism.data.entity.Song
import com.example.prism.ui.components.AlbumArtwork
import com.example.prism.ui.components.SongListItem
import com.example.prism.ui.library.LibraryViewModel
import com.example.prism.ui.player.PlaybackViewModel
import com.example.prism.ui.theme.*
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.toImmutableList

@Composable
fun AlbumDetailScreen(
    albumId: Long,
    albumName: String,
    playbackViewModel: PlaybackViewModel,
    libraryViewModel: LibraryViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    coverPath: String = "",
    customCoverUri: String = "",
) {
    val rawSongs by remember(albumId) { libraryViewModel.getSongsByAlbum(albumId) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val songs = remember(rawSongs) { rawSongs.toImmutableList() }
    val favoriteSongIds by libraryViewModel.favoriteSongIds.collectAsStateWithLifecycle()
    val currentSong by playbackViewModel.currentSong.collectAsStateWithLifecycle()
    val isPlaying by playbackViewModel.isPlayingState.collectAsStateWithLifecycle()

    AlbumDetailContent(
        albumId = albumId,
        albumName = albumName,
        coverPath = coverPath,
        customCoverUri = customCoverUri,
        songs = songs,
        currentSongId = currentSong?.id,
        isPlaying = isPlaying,
        favoriteSongIds = favoriteSongIds,
        onBack = onBack,
        onPlayAll = { if (songs.isNotEmpty()) playbackViewModel.playSong(songs.first(), songs) },
        onPlaySong = { song -> playbackViewModel.playSong(song, songs) },
        onToggleFavorite = { song -> libraryViewModel.toggleFavorite(song) },
        modifier = modifier,
    )
}

@Composable
fun AlbumDetailContent(
    albumId: Long,
    albumName: String,
    coverPath: String,
    customCoverUri: String,
    songs: ImmutableList<Song>,
    currentSongId: String?,
    isPlaying: Boolean,
    favoriteSongIds: ImmutableSet<String>,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onPlaySong: (Song) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnBack by rememberUpdatedState(onBack)

    val backgroundBrush = remember {
        Brush.verticalGradient(
            colors = listOf(AppBgDeep, AppBgDeeper, AppBgDarkest)
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(brush = backgroundBrush)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { currentOnBack() }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back),
                        tint = AppTextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = albumName,
                    color = AppTextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(AppSurface)
                ) {
                    AlbumArtwork(
                        albumId = albumId,
                        coverUri = coverPath.ifEmpty { songs.firstOrNull()?.artworkUri ?: "" },
                        customCoverUri = customCoverUri.ifEmpty { songs.firstOrNull()?.customArtworkUri ?: "" },
                        contentDescription = stringResource(R.string.cd_album_art),
                        size = 256,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = albumName,
                    color = AppTextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                val albumArtist = songs.firstOrNull()?.artist
                Text(
                    text = if (albumArtist.isNullOrBlank() || albumArtist.equals("Unknown", ignoreCase = true)) {
                        stringResource(R.string.unknown_artist)
                    } else {
                        albumArtist
                    },
                    color = AppTextSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onPlayAll,
                    colors = ButtonDefaults.buttonColors(containerColor = AppAccent),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.play_album))
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
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
                        onSongSelected = onPlaySong,
                        onFavoriteToggle = onToggleFavorite,
                        onAddToPlaylist = null
                    )
                }
            }
        }
    }
}
