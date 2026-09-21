package com.example.prism.ui.album

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.prism.R
import com.example.prism.core.util.resolveArtistName
import com.example.prism.data.entity.Song
import com.example.prism.ui.components.AlbumArtwork
import com.example.prism.ui.components.DetailHeroHeader
import com.example.prism.ui.components.DetailSongList
import com.example.prism.ui.components.DetailTopBar
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
    val albums by libraryViewModel.albumsFlow.collectAsStateWithLifecycle(emptyList())
    val currentAlbum = remember(albums, albumId) { albums.firstOrNull { it.id == albumId } }
    val effectiveCustomCoverUri = currentAlbum?.customCoverUri ?: customCoverUri

    val favoriteSongIds by libraryViewModel.favoriteSongIds.collectAsStateWithLifecycle()
    val currentSong by playbackViewModel.currentSong.collectAsStateWithLifecycle()
    val isPlaying by playbackViewModel.isPlayingState.collectAsStateWithLifecycle()

    var showEditCoverDialog by remember { mutableStateOf(false) }

    val onPlayAll = remember(songs, playbackViewModel) {
        { if (songs.isNotEmpty()) playbackViewModel.playSong(songs.first(), songs) }
    }
    val onPlaySong = remember(songs, playbackViewModel) {
        { song: Song -> playbackViewModel.playSong(song, songs) }
    }
    val onToggleFavorite = remember(libraryViewModel) {
        { song: Song -> libraryViewModel.toggleFavorite(song) }
    }

    AlbumDetailContent(
        albumId = albumId,
        albumName = albumName,
        coverPath = coverPath,
        customCoverUri = effectiveCustomCoverUri,
        songs = songs,
        currentSongId = currentSong?.id,
        isPlaying = isPlaying,
        favoriteSongIds = favoriteSongIds,
        onBack = onBack,
        onPlayAll = onPlayAll,
        onPlaySong = onPlaySong,
        onToggleFavorite = onToggleFavorite,
        onEditCover = { showEditCoverDialog = true },
        modifier = modifier,
    )

    if (showEditCoverDialog) {
        com.example.prism.ui.components.EditArtworkDialog(
            title = albumName,
            currentCustomUri = effectiveCustomCoverUri,
            onSave = { uri -> libraryViewModel.updateAlbumCover(albumId, uri) },
            onDismiss = { showEditCoverDialog = false }
        )
    }
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
    onEditCover: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val unknownArtistLabel = stringResource(R.string.unknown_artist)
    val artistName = remember(songs, unknownArtistLabel) {
        resolveArtistName(songs.firstOrNull()?.artist, unknownArtistLabel)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(brush = AppBackgroundBrush)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            DetailTopBar(
                title = albumName,
                onBack = onBack
            )

            DetailHeroHeader(
                title = albumName,
                subtitle = artistName,
                actionButtonText = stringResource(R.string.play_album),
                onActionClick = onPlayAll,
                artwork = {
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(AppSurface)
                            .clickable { onEditCover() }
                    ) {
                        AlbumArtwork(
                            albumId = albumId,
                            coverUri = coverPath.ifEmpty { songs.firstOrNull()?.artworkUri ?: "" },
                            customCoverUri = customCoverUri.ifEmpty { songs.firstOrNull()?.customArtworkUri ?: "" },
                            contentDescription = stringResource(R.string.cd_album_art),
                            size = 256,
                            modifier = Modifier.fillMaxSize()
                        )
                        // Edit Badge
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(AppSurface.copy(alpha = 0.85f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = stringResource(R.string.cd_edit_artwork),
                                tint = AppTextPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            )

            DetailSongList(
                songs = songs,
                currentSongId = currentSongId,
                isPlaying = isPlaying,
                favoriteSongIds = favoriteSongIds,
                onSongSelected = onPlaySong,
                onFavoriteToggle = onToggleFavorite,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
