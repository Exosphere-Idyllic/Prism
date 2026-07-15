package com.example.melodyplayer

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
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
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.melodyplayer.ui.PlayerScreen
import com.example.melodyplayer.ui.SongListScreen
import com.example.melodyplayer.ui.AlbumDetailScreen
import com.example.melodyplayer.ui.ArtistDetailScreen
import com.example.melodyplayer.ui.PlaylistDetailScreen

private val TRANSITION_DURATION = 320

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
    val playbackViewModel: PlaybackViewModel = viewModel()
    val libraryViewModel: LibraryViewModel = viewModel()

    // SharedTransitionLayout enables smooth shared-element transitions between
    // scenes so that NavDisplay doesn't produce jumpy cut-transitions when the
    // scene key changes (e.g. navigating between SongList → Player → Detail).
    SharedTransitionLayout {
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
}
