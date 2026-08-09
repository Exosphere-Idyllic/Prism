package com.example.melodyplayer.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import com.example.melodyplayer.R
import com.example.melodyplayer.LibraryViewModel
import com.example.melodyplayer.PlaybackViewModel
import com.example.melodyplayer.data.Song
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf


enum class LibraryTab(@StringRes val titleRes: Int) {
    Biblioteca(R.string.tab_library),
    Albumes(R.string.tab_albums),
    Playlists(R.string.tab_playlists),
    Artistas(R.string.tab_artists)
}

@Composable
fun SongListScreen(
    playbackViewModel: PlaybackViewModel,
    libraryViewModel: LibraryViewModel,
    onNavigateToPlayer: () -> Unit,
    onNavigateToAlbum: (Long, String) -> Unit,
    onNavigateToArtist: (String) -> Unit,
    onNavigateToPlaylist: (Long, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentSong by playbackViewModel.currentSong.collectAsStateWithLifecycle()
    val isPlaying by playbackViewModel.isPlayingState.collectAsStateWithLifecycle()
    val searchQuery by libraryViewModel.searchQuery.collectAsStateWithLifecycle()
    val isLoading by libraryViewModel.isLoading.collectAsStateWithLifecycle()
    val totalSongs by libraryViewModel.totalSongsCount.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // ── Hoist all tab data above the `when` block so state is preserved ────────
    // Collecting flows *inside* `when(selectedTab)` was the main cause of tab-switch
    // jank: every switch cancelled and re-subscribed the Room/Paging queries, causing
    // a loading flash and a fresh full recomposition.  Collecting here keeps all data
    // in memory regardless of which tab is visible.
    val lazySongs = libraryViewModel.songsFlow.collectAsLazyPagingItems()
    val favoriteSongIds by libraryViewModel.favoriteSongIds.collectAsStateWithLifecycle(persistentSetOf())
    val albums by libraryViewModel.albumsFlow.collectAsStateWithLifecycle(persistentListOf())
    val artists by libraryViewModel.artistsFlow.collectAsStateWithLifecycle(persistentListOf())
    val playlists by libraryViewModel.playlistsWithCountsFlow.collectAsStateWithLifecycle(persistentListOf())

    // ── Hoist list states above `when` to preserve scroll position on tab switches ─
    val songsListState = rememberLazyListState()
    val albumsGridState = rememberLazyGridState()
    val playlistsListState = rememberLazyListState()
    val artistsListState = rememberLazyListState()

    var selectedTab by remember { mutableStateOf(LibraryTab.Biblioteca) }
    var showCreateDialog by remember { mutableStateOf(value = false) }
    var songToAddToPlaylist by remember { mutableStateOf<Song?>(null) }

    // FIX #8: Clear the search query when the user switches tabs so the active
    // filter is not silently applied to all tabs without any visual indicator.
    LaunchedEffect(selectedTab) {
        if (searchQuery.isNotEmpty()) libraryViewModel.setSearchQuery("")
    }

    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, permission) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) libraryViewModel.loadLocalSongs()
    }

    val backgroundBrush = remember {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF0F0F1A),
                Color(0xFF0B0B12),
                Color(0xFF08080C)
            )
        )
    }

    val currentOnNavigateToPlayer by rememberUpdatedState(onNavigateToPlayer)
    val onSongSelected: (Song) -> Unit = remember(playbackViewModel) {
        { song: Song ->
            playbackViewModel.playSong(song)
            currentOnNavigateToPlayer()
        }
    }
    val onFavoriteToggle = remember(libraryViewModel) {
        { song: Song -> libraryViewModel.toggleFavorite(song) }
    }
    val onAddToPlaylist = remember {
        { song: Song -> songToAddToPlaylist = song }
    }
    val onPlayPauseToggle = remember(playbackViewModel) { { playbackViewModel.togglePlayPause() } }

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
            // ── Header ──────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.weight(1f)
                )
                if (totalSongs > 0) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFF6366F1).copy(alpha = 0.15f))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.song_count_format, totalSongs),
                            color = Color(0xFFA5B4FC),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // ── Search Bar ──────────────────────────
            SearchBar(
                query = searchQuery,
                onQueryChange = { libraryViewModel.setSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 12.dp)
            )

            // ── TabRow ──────────────────────────────
            SecondaryScrollableTabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = Color.Transparent,
                contentColor = Color.White,
                edgePadding = 24.dp,
                divider = {},
                        indicator = {
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(selectedTab.ordinal),
                                color = Color(0xFF6366F1),
                            )
                        },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) {
                LibraryTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = {
                            Text(
                                text = stringResource(tab.titleRes),
                                fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 14.sp,
                            )
                        },
                        selectedContentColor = Color.White,
                        unselectedContentColor = Color.White.copy(alpha = 0.5f)
                    )
                }
            }

            // ── Content ─────────────────────────────
            if (!hasPermission) {
                PermissionRequest(
                    onRequestPermission = { permissionLauncher.launch(permission) },
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
            } else {
                Box(modifier = Modifier.weight(1f)) {
                    when (selectedTab) {
                        LibraryTab.Biblioteca -> {
                            val isSongsLoading = (lazySongs.loadState.refresh is androidx.paging.LoadState.Loading) || (isLoading && (lazySongs.itemCount == 0))
                            if (isSongsLoading) {
                                SongListShimmer(modifier = Modifier.fillMaxSize())
                            } else if (lazySongs.itemCount == 0) {
                                if (searchQuery.isEmpty()) {
                                    EmptyLibrary(modifier = Modifier.fillMaxSize())
                                } else {
                                    NoSearchResults(query = searchQuery, modifier = Modifier.fillMaxSize())
                                }
                            } else {
                                SongList(
                                    songs = lazySongs,
                                    currentSong = currentSong,
                                    isPlaying = isPlaying,
                                    favoriteSongIds = favoriteSongIds,
                                    onSongSelected = onSongSelected,
                                    onFavoriteToggle = onFavoriteToggle,
                                    onAddToPlaylist = onAddToPlaylist,
                                    listState = songsListState,
                                )
                            }
                        }
                        LibraryTab.Albumes -> {
                            val isAlbumsLoading = isLoading && albums.isEmpty()
                            if (isAlbumsLoading) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = Color(0xFF6366F1))
                                }
                            } else if (albums.isEmpty()) {
                                EmptyLibrary(modifier = Modifier.fillMaxSize())
                            } else {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(2),
                                    state = albumsGridState,
                                    contentPadding = PaddingValues(start = 24.dp, top = 8.dp, end = 24.dp, bottom = 100.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                    modifier = Modifier.fillMaxSize(),
                                ) {
                                    items(albums, key = { it.id }) { album ->
                                        AlbumGridItem(album = album) {
                                            onNavigateToAlbum(album.id, album.albumName)
                                        }
                                    }
                                }
                            }
                        }
                        LibraryTab.Playlists -> {
                            Column(modifier = Modifier.fillMaxSize()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Button(
                                        onClick = { showCreateDialog = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(stringResource(R.string.new_playlist_title), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                if (playlists.isEmpty()) {
                                    EmptyLibrary(modifier = Modifier.weight(1f).fillMaxWidth())
                                } else {
                                    LazyColumn(
                                        state = playlistsListState,
                                        contentPadding = PaddingValues(start = 24.dp, top = 8.dp, end = 24.dp, bottom = 100.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.weight(1f).fillMaxWidth()
                                    ) {
                                        items(playlists, key = { it.id }) { playlist ->
                                            PlaylistListItem(
                                                playlist = playlist,
                                                onClick = { onNavigateToPlaylist(playlist.id, playlist.name) },
                                            ) { libraryViewModel.deletePlaylist(playlist.id) }
                                        }
                                    }
                                }
                            }
                        }
                        LibraryTab.Artistas -> {
                            val isArtistsLoading = isLoading && artists.isEmpty()
                            if (isArtistsLoading) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = Color(0xFF6366F1))
                                }
                            } else if (artists.isEmpty()) {
                                EmptyLibrary(modifier = Modifier.fillMaxSize())
                            } else {
                                LazyColumn(
                                    state = artistsListState,
                                    contentPadding = PaddingValues(start = 24.dp, top = 8.dp, end = 24.dp, bottom = 100.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(artists, key = { it.name }) { artist ->
                                        ArtistListItem(artist = artist) { onNavigateToArtist(artist.name) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Mini Player (bottom) ─────────────────
        currentSong?.let { miniPlayerSong ->
            MiniPlayer(
                song = miniPlayerSong,
                isPlaying = isPlaying,
                progressStateFlow = playbackViewModel.progressState,
                onPlayPauseToggle = onPlayPauseToggle,
                onOpenPlayer = onNavigateToPlayer,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }

        // ── Dialogs ───────────────────────────────
        if (showCreateDialog) {
            CreatePlaylistDialog(
                onCreate = { libraryViewModel.createPlaylist(it) },
                onDismiss = { showCreateDialog = false }
            )
        }
        songToAddToPlaylist?.let { song ->
            AddToPlaylistDialog(
                song = song,
                libraryViewModel = libraryViewModel,
                onDismiss = { songToAddToPlaylist = null },
            )
        }
    }
}

// SongList, SongListItemWrapper y SongListItem han sido extraídos a SongList.kt
