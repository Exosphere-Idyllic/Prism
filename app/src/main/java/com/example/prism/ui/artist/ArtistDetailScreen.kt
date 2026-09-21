package com.example.prism.ui.artist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.prism.R
import com.example.prism.data.entity.Song
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
fun ArtistDetailScreen(
    artistName: String,
    playbackViewModel: PlaybackViewModel,
    libraryViewModel: LibraryViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rawSongs by remember(artistName) { libraryViewModel.getSongsByArtist(artistName) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val songs = remember(rawSongs) { rawSongs.toImmutableList() }
    val favoriteSongIds by libraryViewModel.favoriteSongIds.collectAsStateWithLifecycle()
    val currentSong by playbackViewModel.currentSong.collectAsStateWithLifecycle()
    val isPlaying by playbackViewModel.isPlayingState.collectAsStateWithLifecycle()

    val onPlaySong = remember(songs, playbackViewModel) {
        { song: Song -> playbackViewModel.playSong(song, songs) }
    }
    val onToggleFavorite = remember(libraryViewModel) {
        { song: Song -> libraryViewModel.toggleFavorite(song) }
    }

    ArtistDetailContent(
        artistName = artistName,
        songs = songs,
        currentSongId = currentSong?.id,
        isPlaying = isPlaying,
        favoriteSongIds = favoriteSongIds,
        onBack = onBack,
        onPlaySong = onPlaySong,
        onToggleFavorite = onToggleFavorite,
        modifier = modifier,
    )
}

@Composable
fun ArtistDetailContent(
    artistName: String,
    songs: ImmutableList<Song>,
    currentSongId: String?,
    isPlaying: Boolean,
    favoriteSongIds: ImmutableSet<String>,
    onBack: () -> Unit,
    onPlaySong: (Song) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
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
                title = artistName,
                onBack = onBack
            )

            DetailHeroHeader(
                title = artistName,
                subtitle = songCountText,
                artwork = {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(AppAccentBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = artistName.take(1).uppercase(),
                            color = AppAccentSoft,
                            fontSize = 38.sp,
                            fontWeight = FontWeight.Bold
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
                modifier = Modifier.weight(1f)
            )
        }
    }
}
