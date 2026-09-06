package com.example.prism.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import org.koin.androidx.compose.koinViewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.prism.ui.album.AlbumDetailScreen
import com.example.prism.ui.artist.ArtistDetailScreen
import com.example.prism.ui.library.LibraryViewModel
import com.example.prism.ui.library.SongListScreen
import com.example.prism.ui.player.PlaybackViewModel
import com.example.prism.ui.player.PlayerScreen
import com.example.prism.ui.playlist.PlaylistDetailScreen

private const val TRANSITION_DURATION = 320

// Slide-in from right (forward navigation)
private val forwardEnter = slideInHorizontally(
    initialOffsetX = { fullWidth -> fullWidth },
    animationSpec = tween(durationMillis = TRANSITION_DURATION, easing = FastOutSlowInEasing)
) + fadeIn(animationSpec = tween(durationMillis = TRANSITION_DURATION / 2))

private val forwardExit = slideOutHorizontally(
    targetOffsetX = { fullWidth -> -fullWidth / 3 },
    animationSpec = tween(durationMillis = TRANSITION_DURATION, easing = FastOutSlowInEasing)
) + fadeOut(animationSpec = tween(durationMillis = TRANSITION_DURATION / 2))

// Slide-in from left (back navigation)
private val backEnter = slideInHorizontally(
    initialOffsetX = { fullWidth -> -fullWidth / 3 },
    animationSpec = tween(durationMillis = TRANSITION_DURATION, easing = FastOutSlowInEasing)
) + fadeIn(animationSpec = tween(durationMillis = TRANSITION_DURATION / 2))

private val backExit = slideOutHorizontally(
    targetOffsetX = { fullWidth -> fullWidth },
    animationSpec = tween(durationMillis = TRANSITION_DURATION, easing = FastOutSlowInEasing)
) + fadeOut(animationSpec = tween(durationMillis = TRANSITION_DURATION / 2))

@Composable
fun MainNavigation() {
    val backStack = rememberNavBackStack(SongList)
    val playbackViewModel: PlaybackViewModel = koinViewModel()
    val libraryViewModel: LibraryViewModel = koinViewModel()

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        // Forward navigation: new screen slides in from right
        transitionSpec = { forwardEnter togetherWith forwardExit },
        // Back navigation: screen slides back out to the right
        popTransitionSpec = { backEnter togetherWith backExit },
        // Predictive back gesture mirrors the pop animation
        predictivePopTransitionSpec = { backEnter togetherWith backExit },
        entryProvider = entryProvider {
            entry<SongList> {
                SongListScreen(
                    playbackViewModel = playbackViewModel,
                    libraryViewModel = libraryViewModel,
                    onNavigateToPlayer = { backStack.add(Player) },
                    onNavigateToAlbum = { id, name, coverPath, customCoverUri ->
                        backStack.add(AlbumDetail(id, name, coverPath, customCoverUri))
                    },
                    onNavigateToArtist = { name -> backStack.add(ArtistDetail(name)) },
                    onNavigateToPlaylist = { id, name -> backStack.add(PlaylistDetail(id, name)) },
                    modifier = Modifier.fillMaxSize()
                )
            }
            entry<Player> {
                PlayerScreen(
                    viewModel = playbackViewModel,
                    onBack = { backStack.removeLastOrNull() },
                    modifier = Modifier.fillMaxSize()
                )
            }
            entry<AlbumDetail> { key ->
                AlbumDetailScreen(
                    albumId = key.albumId,
                    albumName = key.albumName,
                    coverPath = key.coverPath,
                    customCoverUri = key.customCoverUri,
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
