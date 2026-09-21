package com.example.prism.ui.library

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.annotation.StringRes
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.example.prism.R
import com.example.prism.ui.player.PlaybackViewModel
import com.example.prism.data.entity.Album
import com.example.prism.data.entity.Artist
import com.example.prism.data.entity.Song
import com.example.prism.domain.model.PlaylistWithCount
import com.example.prism.ui.components.EditArtworkDialog
import com.example.prism.ui.components.EmptyLibrary
import com.example.prism.ui.components.MiniPlayer
import com.example.prism.ui.components.NoSearchResults
import com.example.prism.ui.components.PermissionRequest
import com.example.prism.ui.components.SearchBar
import com.example.prism.ui.components.SongList
import com.example.prism.ui.components.SongListShimmer
import com.example.prism.ui.playlist.AddToPlaylistDialog
import com.example.prism.ui.playlist.CreatePlaylistDialog
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import com.example.prism.ui.theme.*


enum class LibraryTab(@param:StringRes val titleRes: Int) {
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
    onNavigateToAlbum: (Long, String, String, String) -> Unit,
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
    var songToEditArtwork by remember { mutableStateOf<Song?>(null) }

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

    LifecycleResumeEffect(permission) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            permission
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted != hasPermission) {
            hasPermission = granted
            if (granted) libraryViewModel.loadLocalSongs()
        }
        onPauseOrDispose { }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) libraryViewModel.loadLocalSongs()
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
    val onEditArtwork = remember {
        { song: Song -> songToEditArtwork = song }
    }
    val onPlayPauseToggle = remember(playbackViewModel) { { playbackViewModel.togglePlayPause() } }

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
            // ── Header ──────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(top = 20.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.app_name),
                        color = AppTextPrimary,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    if (totalSongs > 0) {
                        Text(
                            text = pluralStringResource(R.plurals.songs_count, totalSongs, totalSongs),
                            color = AppTextSecondary,
                            fontSize = 12.sp,
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
                contentColor = AppTextPrimary,
                edgePadding = 24.dp,
                divider = {},
                indicator = {
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(selectedTab.ordinal),
                        color = AppAccent,
                        height = 2.dp
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
                                fontWeight = if (selectedTab == tab) FontWeight.SemiBold else FontWeight.Normal,
                                fontSize = 14.sp,
                            )
                        },
                        selectedContentColor = AppTextPrimary,
                        unselectedContentColor = AppTextSecondary
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
                        LibraryTab.Biblioteca -> SongsTabContent(
                            lazySongs = lazySongs,
                            isLoading = isLoading,
                            searchQuery = searchQuery,
                            currentSong = currentSong,
                            isPlaying = isPlaying,
                            favoriteSongIds = favoriteSongIds,
                            onSongSelected = onSongSelected,
                            onFavoriteToggle = onFavoriteToggle,
                            onAddToPlaylist = onAddToPlaylist,
                            onEditArtwork = onEditArtwork,
                            listState = songsListState,
                        )
                        LibraryTab.Albumes -> AlbumsTabContent(
                            albums = albums,
                            isLoading = isLoading,
                            gridState = albumsGridState,
                            onNavigateToAlbum = onNavigateToAlbum,
                        )
                        LibraryTab.Playlists -> PlaylistsTabContent(
                            playlists = playlists,
                            listState = playlistsListState,
                            onCreatePlaylistClick = { showCreateDialog = true },
                            onNavigateToPlaylist = onNavigateToPlaylist,
                            onDeletePlaylist = { libraryViewModel.deletePlaylist(it) },
                        )
                        LibraryTab.Artistas -> ArtistsTabContent(
                            artists = artists,
                            isLoading = isLoading,
                            listState = artistsListState,
                            onNavigateToArtist = onNavigateToArtist,
                        )
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
                playlists = playlists,
                onSelectPlaylist = { playlistId -> libraryViewModel.addSongToPlaylist(playlistId, song.id) },
                onDismiss = { songToAddToPlaylist = null },
            )
        }
        songToEditArtwork?.let { song ->
            EditArtworkDialog(
                title = song.title,
                currentCustomUri = song.customArtworkUri,
                onSave = { uri -> libraryViewModel.updateSongArtwork(song.id, uri) },
                onDismiss = { songToEditArtwork = null }
            )
        }
    }
}

@Composable
private fun SongsTabContent(
    lazySongs: LazyPagingItems<Song>,
    isLoading: Boolean,
    searchQuery: String,
    currentSong: Song?,
    isPlaying: Boolean,
    favoriteSongIds: ImmutableSet<String>,
    onSongSelected: (Song) -> Unit,
    onFavoriteToggle: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    onEditArtwork: (Song) -> Unit,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val isSongsLoading = (lazySongs.loadState.refresh is androidx.paging.LoadState.Loading) || (isLoading && (lazySongs.itemCount == 0))
    if (isSongsLoading) {
        SongListShimmer(modifier = modifier.fillMaxSize())
    } else if (lazySongs.itemCount == 0) {
        if (searchQuery.isEmpty()) {
            EmptyLibrary(modifier = modifier.fillMaxSize())
        } else {
            NoSearchResults(query = searchQuery, modifier = modifier.fillMaxSize())
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
            onEditArtwork = onEditArtwork,
            listState = listState,
            modifier = modifier,
        )
    }
}

@Composable
private fun AlbumsTabContent(
    albums: List<Album>,
    isLoading: Boolean,
    gridState: LazyGridState,
    onNavigateToAlbum: (Long, String, String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isAlbumsLoading = isLoading && albums.isEmpty()
    if (isAlbumsLoading) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = AppAccent)
        }
    } else if (albums.isEmpty()) {
        EmptyLibrary(modifier = modifier.fillMaxSize())
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            contentPadding = PaddingValues(start = 24.dp, top = 8.dp, end = 24.dp, bottom = 100.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = modifier.fillMaxSize(),
        ) {
            items(albums, key = { it.id }) { album ->
                AlbumGridItem(album = album) {
                    onNavigateToAlbum(album.id, album.albumName, album.coverPath, album.customCoverUri)
                }
            }
        }
    }
}

@Composable
private fun PlaylistsTabContent(
    playlists: List<PlaylistWithCount>,
    listState: LazyListState,
    onCreatePlaylistClick: () -> Unit,
    onNavigateToPlaylist: (Long, String) -> Unit,
    onDeletePlaylist: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Button(
                onClick = onCreatePlaylistClick,
                colors = ButtonDefaults.buttonColors(containerColor = AppAccent),
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
                state = listState,
                contentPadding = PaddingValues(start = 24.dp, top = 8.dp, end = 24.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                items(playlists, key = { it.id }) { playlist ->
                    PlaylistListItem(
                        playlist = playlist,
                        onClick = { onNavigateToPlaylist(playlist.id, playlist.name) },
                        onDelete = { onDeletePlaylist(playlist.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtistsTabContent(
    artists: List<Artist>,
    isLoading: Boolean,
    listState: LazyListState,
    onNavigateToArtist: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isArtistsLoading = isLoading && artists.isEmpty()
    if (isArtistsLoading) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = AppAccent)
        }
    } else if (artists.isEmpty()) {
        EmptyLibrary(modifier = modifier.fillMaxSize())
    } else {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 24.dp, top = 8.dp, end = 24.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = modifier.fillMaxSize()
        ) {
            items(artists, key = { it.name }) { artist ->
                ArtistListItem(artist = artist) { onNavigateToArtist(artist.name) }
            }
        }
    }
}

