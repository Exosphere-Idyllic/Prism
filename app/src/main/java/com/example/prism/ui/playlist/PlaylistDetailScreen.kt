package com.example.prism.ui.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.prism.R
import com.example.prism.data.entity.Song
import com.example.prism.data.repository.isFavoritesPlaylist
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
fun PlaylistDetailScreen(
    playlistId: Long,
    playlistName: String,
    playbackViewModel: PlaybackViewModel,
    libraryViewModel: LibraryViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rawSongs by remember(playlistId) { libraryViewModel.getSongsForPlaylist(playlistId) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val songs = remember(rawSongs) { rawSongs.toImmutableList() }
    val favoriteSongIds by libraryViewModel.favoriteSongIds.collectAsStateWithLifecycle()
    val currentSong by playbackViewModel.currentSong.collectAsStateWithLifecycle()
    val isPlaying by playbackViewModel.isPlayingState.collectAsStateWithLifecycle()

    val onPlayAll = remember(songs, playbackViewModel) {
        { if (songs.isNotEmpty()) playbackViewModel.playSong(songs.first(), songs) }
    }
    val onPlaySong = remember(songs, playbackViewModel) {
        { song: Song -> playbackViewModel.playSong(song, songs) }
    }
    val onToggleFavorite = remember(libraryViewModel) {
        { song: Song -> libraryViewModel.toggleFavorite(song) }
    }
    val onRemoveSong = remember(playlistId, libraryViewModel) {
        { song: Song -> libraryViewModel.removeSongFromPlaylist(playlistId, song.id) }
    }

    PlaylistDetailContent(
        playlistName = playlistName,
        songs = songs,
        currentSongId = currentSong?.id,
        isPlaying = isPlaying,
        favoriteSongIds = favoriteSongIds,
        onBack = onBack,
        onPlayAll = onPlayAll,
        onPlaySong = onPlaySong,
        onToggleFavorite = onToggleFavorite,
        onRemoveSong = onRemoveSong,
        modifier = modifier,
    )
}

@Composable
fun PlaylistDetailContent(
    playlistName: String,
    songs: ImmutableList<Song>,
    currentSongId: String?,
    isPlaying: Boolean,
    favoriteSongIds: ImmutableSet<String>,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onPlaySong: (Song) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onRemoveSong: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    val displayName = if (isFavoritesPlaylist(playlistName)) {
        stringResource(R.string.playlist_favorites)
    } else {
        playlistName
    }
    val songCountText = pluralStringResource(R.plurals.songs_count, songs.size, songs.size)

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
                title = displayName,
                onBack = onBack
            )

            DetailHeroHeader(
                title = displayName,
                subtitle = songCountText,
                actionButtonText = stringResource(R.string.play_playlist),
                onActionClick = onPlayAll,
                artwork = {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(AppAccentBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = AppAccentSoft,
                            modifier = Modifier.size(44.dp)
                        )
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
                trailingAction = { song ->
                    IconButton(onClick = { onRemoveSong(song) }, modifier = Modifier.size(40.dp)) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.cd_remove),
                            tint = AppDeleteTint.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}
