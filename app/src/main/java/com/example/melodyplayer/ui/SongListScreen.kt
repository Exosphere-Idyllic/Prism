package com.example.melodyplayer.ui

import kotlinx.collections.immutable.*
import android.Manifest
import com.example.melodyplayer.data.ThumbnailManager

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import com.example.melodyplayer.LibraryViewModel
import com.example.melodyplayer.PlaybackViewModel
import com.example.melodyplayer.data.Song
import kotlinx.coroutines.flow.debounce
import kotlin.time.Duration.Companion.milliseconds

enum class LibraryTab(val title: String) {
    Biblioteca("Biblioteca"),
    Albumes("Álbumes"),
    Playlists("Playlists"),
    Artistas("Artistas")
}

@Composable
fun SongListScreen(
    playbackViewModel: PlaybackViewModel,
    libraryViewModel: LibraryViewModel,
    onNavigateToPlayer: () -> Unit,
    onNavigateToAlbum: (Long, String) -> Unit,
    onNavigateToArtist: (String) -> Unit,
    onNavigateToPlaylist: (Long, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentSong by playbackViewModel.currentSong.collectAsStateWithLifecycle()
    val isPlaying by playbackViewModel.isPlayingState.collectAsStateWithLifecycle()
    val searchQuery by libraryViewModel.searchQuery.collectAsStateWithLifecycle()
    val isLoading by libraryViewModel.isLoading.collectAsStateWithLifecycle()
    val totalSongs by libraryViewModel.totalSongsCount.collectAsStateWithLifecycle()
    val songThumbnail128Ids by libraryViewModel.songThumbnail128Ids.collectAsStateWithLifecycle()
    val albumThumbnail256Ids by libraryViewModel.albumThumbnail256Ids.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var selectedTab by remember { mutableStateOf(LibraryTab.Biblioteca) }

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
                    text = "Prism",
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
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "$totalSongs canciones",
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
                        color = Color(0xFF6366F1)
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
                                text = tab.title,
                                fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 14.sp
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
                            val lazySongs = libraryViewModel.songsFlow.collectAsLazyPagingItems()
                            val favoriteSongIds by libraryViewModel.favoriteSongIds.collectAsStateWithLifecycle(emptySet())

                            if (lazySongs.itemCount == 0 && lazySongs.loadState.refresh is androidx.paging.LoadState.Loading) {
                                SongListShimmer(modifier = Modifier.fillMaxSize())
                            } else if (lazySongs.itemCount == 0 && !isLoading) {
                                if (searchQuery.isEmpty()) {
                                    EmptyLibrary(modifier = Modifier.fillMaxSize())
                                } else {
                                    NoSearchResults(query = searchQuery, modifier = Modifier.fillMaxSize())
                                }
                            } else {
                                var songToAddToPlaylist by remember { mutableStateOf<Song?>(null) }
                                
                                val onSongSelected: (Song) -> Unit = remember(playbackViewModel, onNavigateToPlayer) {
                                    { song: Song ->
                                        playbackViewModel.playSong(song)
                                        onNavigateToPlayer()
                                    }
                                }
                                val onFavoriteToggle = remember(libraryViewModel) {
                                    { song: Song -> libraryViewModel.toggleFavorite(song) }
                                }
                                val onAddToPlaylist = remember {
                                    { song: Song -> songToAddToPlaylist = song }
                                }

                                SongList(
                                    songs = lazySongs,
                                    currentSong = currentSong,
                                    isPlaying = isPlaying,
                                    favoriteSongIds = favoriteSongIds,
                                    songThumbnail128Ids = songThumbnail128Ids,
                                    onSongSelected = onSongSelected,
                                    onFavoriteToggle = onFavoriteToggle,
                                    onAddToPlaylist = onAddToPlaylist,
                                    libraryViewModel = libraryViewModel
                                )

                                songToAddToPlaylist?.let { song ->
                                    AddToPlaylistDialog(
                                        song = song,
                                        libraryViewModel = libraryViewModel,
                                        onDismiss = { songToAddToPlaylist = null }
                                    )
                                }
                            }
                        }
                        LibraryTab.Albumes -> {
                            val albums by libraryViewModel.albumsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
                            if (albums.isEmpty() && isLoading) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = Color(0xFF6366F1))
                                }
                            } else if (albums.isEmpty()) {
                                EmptyLibrary(modifier = Modifier.fillMaxSize())
                            } else {
                                LaunchedEffect(albums.size) {
                                    val limit = minOf(20, albums.size)
                                    for (i in 0 until limit) {
                                        val album = albums[i]
                                        libraryViewModel.requestThumbnail(album.id, album.coverPath)
                                    }
                                }

                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(2),
                                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(albums, key = { it.id }) { album ->
                                        AlbumGridItem(
                                            album = album,
                                            hasWebp = albumThumbnail256Ids.contains(album.id),
                                            onClick = { onNavigateToAlbum(album.id, album.albumName) }
                                        )
                                    }
                                    item { Spacer(modifier = Modifier.height(96.dp)) }
                                    item { Spacer(modifier = Modifier.height(96.dp)) }
                                }
                            }
                        }
                        LibraryTab.Playlists -> {
                            val playlists by libraryViewModel.playlistsWithCountsFlow.collectAsStateWithLifecycle(emptyList())
                            var showCreateDialog by remember { mutableStateOf(false) }

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
                                        Text("Nueva Playlist", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                if (playlists.isEmpty()) {
                                    EmptyLibrary(modifier = Modifier.weight(1f).fillMaxWidth())
                                } else {
                                    LazyColumn(
                                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.weight(1f).fillMaxWidth()
                                    ) {
                                        items(playlists, key = { it.id }) { playlist ->
                                            PlaylistListItem(
                                                playlist = playlist,
                                                onClick = { onNavigateToPlaylist(playlist.id, playlist.name) },
                                                onDelete = { libraryViewModel.deletePlaylist(playlist.id) }
                                            )
                                        }
                                        item { Spacer(modifier = Modifier.height(96.dp)) }
                                    }
                                }
                            }

                            if (showCreateDialog) {
                                CreatePlaylistDialog(
                                    onCreate = { libraryViewModel.createPlaylist(it) },
                                    onDismiss = { showCreateDialog = false }
                                )
                            }
                        }
                        LibraryTab.Artistas -> {
                            val artists by libraryViewModel.artistsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
                            if (artists.isEmpty() && isLoading) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = Color(0xFF6366F1))
                                }
                            } else if (artists.isEmpty()) {
                                EmptyLibrary(modifier = Modifier.fillMaxSize())
                            } else {
                                LazyColumn(
                                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(artists, key = { it.id }) { artist ->
                                        ArtistListItem(artist = artist, onClick = { onNavigateToArtist(artist.name) })
                                    }
                                    item { Spacer(modifier = Modifier.height(96.dp)) }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Mini Player (bottom) ─────────────────
        val miniPlayerSong = currentSong
        if (miniPlayerSong != null) {
            val onPlayPauseToggle = remember(playbackViewModel) { { playbackViewModel.togglePlayPause() } }
            val hasWebp = songThumbnail128Ids.contains(miniPlayerSong.id)
            MiniPlayer(
                song = miniPlayerSong,
                isPlaying = isPlaying,
                progressStateFlow = playbackViewModel.progressState,
                hasWebp = hasWebp,
                onPlayPauseToggle = onPlayPauseToggle,
                onOpenPlayer = onNavigateToPlayer,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
    }
}

private const val THUMBNAIL_PREFETCH_BUFFER = 5

@OptIn(kotlinx.coroutines.FlowPreview::class)
@Composable
fun SongList(
    songs: LazyPagingItems<Song>,
    currentSong: Song?,
    isPlaying: Boolean,
    favoriteSongIds: Set<String>,
    songThumbnail128Ids: Set<String>,
    onSongSelected: (Song) -> Unit,
    onFavoriteToggle: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    libraryViewModel: LibraryViewModel,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val latestThumbnail128Ids by rememberUpdatedState(songThumbnail128Ids)
    val requestedThumbnailIds = remember { mutableSetOf<String>() }

    LaunchedEffect(listState, songs.itemCount) {
        snapshotFlow {
            val visible = listState.layoutInfo.visibleItemsInfo
            if (visible.isEmpty()) null
            else visible.first().index to visible.last().index
        }
            .debounce(120.milliseconds)
            .collect { range ->
                if (range == null || songs.itemCount == 0) return@collect
                val firstIndex = (range.first - THUMBNAIL_PREFETCH_BUFFER).coerceAtLeast(0)
                val lastIndex = (range.second + THUMBNAIL_PREFETCH_BUFFER).coerceAtMost(songs.itemCount - 1)

                for (i in firstIndex..lastIndex) {
                    val song = songs.peek(i) ?: continue
                    if (song.id in requestedThumbnailIds) continue
                    if (latestThumbnail128Ids.contains(song.id)) continue
                    requestedThumbnailIds.add(song.id)
                    libraryViewModel.requestSongThumbnail(song)
                }
            }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(
            count = songs.itemCount,
            key = songs.itemKey { it.id },
            contentType = songs.itemContentType { "song" }
        ) { index ->
            val song = songs[index]
            if (song != null) {
                val isFavorite = favoriteSongIds.contains(song.id)
                val hasWebp = songThumbnail128Ids.contains(song.id)

                SongListItemWrapper(
                    song = song,
                    currentSongId = currentSong?.id,
                    isPlaying = isPlaying,
                    isFavorite = isFavorite,
                    hasWebp = hasWebp,
                    onSongSelected = onSongSelected,
                    onFavoriteToggle = onFavoriteToggle,
                    onAddToPlaylist = onAddToPlaylist
                )
            }
        }
        item(contentType = "spacer") { Spacer(modifier = Modifier.height(96.dp)) }
    }
}

@Composable
fun SongListItemWrapper(
    song: Song,
    currentSongId: String?,
    isPlaying: Boolean,
    isFavorite: Boolean,
    hasWebp: Boolean,
    onSongSelected: (Song) -> Unit,
    onFavoriteToggle: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit
) {
    val isSelected = song.id == currentSongId
    val activePlayingState = isSelected && isPlaying

    SongListItem(
        song = song,
        isSelected = isSelected,
        isPlaying = activePlayingState,
        isFavorite = isFavorite,
        hasWebp = hasWebp,
        onSongSelected = onSongSelected,
        onFavoriteToggle = onFavoriteToggle,
        onAddToPlaylist = onAddToPlaylist
    )
}

@Composable
fun SongListItem(
    song: Song,
    isSelected: Boolean,
    isPlaying: Boolean,
    isFavorite: Boolean,
    hasWebp: Boolean,
    onSongSelected: (Song) -> Unit,
    onFavoriteToggle: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit
) {
    val bgColor = if (isSelected) Color(0xFF6366F1).copy(alpha = 0.15f) else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable { onSongSelected(song) }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SongArtwork(
            song = song,
            contentDescription = "Album art",
            hasWebp = hasWebp,
            size = 128,
            crossfade = false,
            iconSize = 24.dp,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.06f))
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = if (isSelected) Color(0xFFA5B4FC) else Color.White,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = song.artist,
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(onClick = { onFavoriteToggle(song) }) {
            Icon(
                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = "Favorito",
                tint = if (isFavorite) Color(0xFFEF4444) else Color.White.copy(alpha = 0.35f),
                modifier = Modifier.size(20.dp)
            )
        }

        IconButton(onClick = { onAddToPlaylist(song) }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Mas opciones",
                tint = Color.White.copy(alpha = 0.45f),
                modifier = Modifier.size(20.dp)
            )
        }

        if (isPlaying) {
            Spacer(modifier = Modifier.width(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(3) { i ->
                    Box(
                        modifier = Modifier
                            .size(width = 3.dp, height = if (i == 1) 14.dp else 9.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFF818CF8))
                    )
                }
            }
        }
    }
}
