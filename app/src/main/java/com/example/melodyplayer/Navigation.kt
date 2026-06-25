package com.example.melodyplayer

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.melodyplayer.ui.PlayerScreen
import com.example.melodyplayer.ui.SongListScreen
import com.example.melodyplayer.ui.AlbumDetailScreen
import com.example.melodyplayer.ui.ArtistDetailScreen
import com.example.melodyplayer.ui.PlaylistDetailScreen

@Composable
fun MainNavigation() {
    val backStack = rememberNavBackStack(SongList)
    val playbackViewModel: PlaybackViewModel = viewModel()
    val libraryViewModel: LibraryViewModel = viewModel()

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<SongList> {
                SongListScreen(
                    playbackViewModel = playbackViewModel,
                    libraryViewModel = libraryViewModel,
                    onNavigateToPlayer = { backStack.add(Player) },
                    onNavigateToAlbum = { id, name -> backStack.add(AlbumDetail(id, name)) },
                    onNavigateToArtist = { name -> backStack.add(ArtistDetail(name)) },
                    onNavigateToPlaylist = { id, name -> backStack.add(PlaylistDetail(id, name)) },
                    modifier = Modifier.fillMaxSize()
                )
            }
            entry<Player> {
                PlayerScreen(
                    viewModel = playbackViewModel,
                    libraryViewModel = libraryViewModel,
                    onBack = { backStack.removeLastOrNull() },
                    modifier = Modifier.fillMaxSize()
                )
            }
            entry<AlbumDetail> { key ->
                AlbumDetailScreen(
                    albumId = key.albumId,
                    albumName = key.albumName,
                    playbackViewModel = playbackViewModel,
                    libraryViewModel = libraryViewModel,
                    onBack = { backStack.removeLastOrNull() },
                    modifier = Modifier.fillMaxSize()
                )
            }
            entry<ArtistDetail> { key ->
                ArtistDetailScreen(
                    artistName = key.artistName,
                    playbackViewModel = playbackViewModel,
                    libraryViewModel = libraryViewModel,
                    onBack = { backStack.removeLastOrNull() },
                    modifier = Modifier.fillMaxSize()
                )
            }
            entry<PlaylistDetail> { key ->
                PlaylistDetailScreen(
                    playlistId = key.playlistId,
                    playlistName = key.playlistName,
                    playbackViewModel = playbackViewModel,
                    libraryViewModel = libraryViewModel,
                    onBack = { backStack.removeLastOrNull() },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    )
}
