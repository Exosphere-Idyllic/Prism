package com.example.melodyplayer.ui

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
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
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.example.melodyplayer.LibraryViewModel
import com.example.melodyplayer.PlaybackUiState
import com.example.melodyplayer.PlaybackViewModel
import com.example.melodyplayer.ProgressState
import com.example.melodyplayer.data.Album
import com.example.melodyplayer.data.Artist
import com.example.melodyplayer.data.Playlist
import com.example.melodyplayer.data.Song
import com.example.melodyplayer.data.ThumbnailRegistry
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import java.io.File

private val DarkGrayPainter = ColorPainter(Color(0xFF1E1E2C))

enum class LibraryTab(val title: String) {
    Biblioteca("Biblioteca"),
    Albumes("Álbumes"),
    Playlists("Playlists"),
    Artistas("Artistas")
}

// ─────────────────────────────────────────────
//  SHIMMER SKELETON LOADER
// ─────────────────────────────────────────────

@Composable
fun shimmerBrush(targetValue: Float = 1000f): Brush {
    val shimmerColors = listOf(
        Color.White.copy(alpha = 0.03f),
        Color.White.copy(alpha = 0.12f),
        Color.White.copy(alpha = 0.03f)
    )
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim = transition.animateFloat(
        initialValue = 0f,
        targetValue = targetValue,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslation"
    )
    return Brush.linearGradient(
        colors = shimmerColors,
        start = Offset.Zero,
        end = Offset(x = translateAnim.value, y = translateAnim.value)
    )
}

// ─────────────────────────────────────────────
//  IMAGE ARTWORK RESOLVERS
// ─────────────────────────────────────────────

@Composable
fun SongArtwork(
    song: Song?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Int = 128,
    crossfade: Boolean = false,
    iconSize: androidx.compose.ui.unit.Dp = 24.dp
) {
    val context = LocalContext.current
    val model = if (song == null || song.artworkUri.isEmpty()) {
        null
    } else {
        val sizeSuffix = if (size <= 128) "128" else "256"
        val hasWebp = if (size <= 128) {
            ThumbnailRegistry.thumbnail128Set[song.albumId] == true
        } else {
            ThumbnailRegistry.thumbnail256Set[song.albumId] == true
        }
        if (hasWebp) {
            val cacheFile = File(context.cacheDir, "album_art/album_${song.albumId}_$sizeSuffix.webp")
            Uri.fromFile(cacheFile).toString()
        } else {
            song.artworkUri
        }
    }

    val imageRequest = remember(model, size, crossfade) {
        ImageRequest.Builder(context)
            .data(model)
            .crossfade(crossfade)
            .size(size)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (model != null) {
            AsyncImage(
                model = imageRequest,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                placeholder = DarkGrayPainter,
                error = DarkGrayPainter
            )
        } else {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.25f),
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

@Composable
fun AlbumArtwork(
    albumId: Long,
    coverUri: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Int = 256,
    crossfade: Boolean = false,
    iconSize: androidx.compose.ui.unit.Dp = 32.dp
) {
    val context = LocalContext.current
    val sizeSuffix = if (size <= 128) "128" else "256"
    val hasWebp = if (size <= 128) {
        ThumbnailRegistry.thumbnail128Set[albumId] == true
    } else {
        ThumbnailRegistry.thumbnail256Set[albumId] == true
    }
    val model = if (hasWebp) {
        val cacheFile = File(context.cacheDir, "album_art/album_${albumId}_$sizeSuffix.webp")
        Uri.fromFile(cacheFile).toString()
    } else if (coverUri.isNotEmpty()) {
        coverUri
    } else {
        null
    }

    val imageRequest = remember(model, size, crossfade) {
        ImageRequest.Builder(context)
            .data(model)
            .crossfade(crossfade)
            .size(size)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (model != null) {
            AsyncImage(
                model = imageRequest,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                placeholder = DarkGrayPainter,
                error = DarkGrayPainter
            )
        } else {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.25f),
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

// ─────────────────────────────────────────────
//  SONG LIST SCREEN WITH TABS
// ─────────────────────────────────────────────

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
    val state by playbackViewModel.uiState.collectAsStateWithLifecycle(PlaybackUiState())
    val searchQuery by libraryViewModel.searchQuery.collectAsStateWithLifecycle()
    val isLoading by libraryViewModel.isLoading.collectAsStateWithLifecycle()
    val totalSongs by libraryViewModel.totalSongsCount.collectAsStateWithLifecycle()
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
            ScrollableTabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = Color.Transparent,
                contentColor = Color.White,
                edgePadding = 24.dp,
                divider = {},
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab.ordinal]),
                        color = Color(0xFF6366F1)
                    )
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) {
                LibraryTab.values().forEach { tab ->
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
                                    currentSong = state.currentSong,
                                    isPlaying = state.isPlaying,
                                    favoriteSongIds = favoriteSongIds,
                                    onSongSelected = onSongSelected,
                                    onFavoriteToggle = onFavoriteToggle,
                                    onAddToPlaylist = onAddToPlaylist
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
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(2),
                                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(albums, key = { it.id }) { album ->
                                        AlbumGridItem(album = album, onClick = { onNavigateToAlbum(album.id, album.albumName) })
                                    }
                                    item { Spacer(modifier = Modifier.height(96.dp)) }
                                    item { Spacer(modifier = Modifier.height(96.dp)) }
                                }
                            }
                        }
                        LibraryTab.Playlists -> {
                            val playlists by libraryViewModel.playlistsFlow.collectAsStateWithLifecycle()
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
                                            val songCountFlow = remember(playlist.id) {
                                                libraryViewModel.getSongsForPlaylist(playlist.id).map { it.size }
                                            }
                                            val songCount by songCountFlow.collectAsStateWithLifecycle(initialValue = 0)
                                            PlaylistListItem(
                                                playlist = playlist,
                                                songCount = songCount,
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
        if (state.currentSong != null) {
            val onPlayPauseToggle = remember(playbackViewModel) { { playbackViewModel.togglePlayPause() } }
            MiniPlayer(
                state = state,
                progressStateFlow = playbackViewModel.progressState,
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

// ─────────────────────────────────────────────
//  SONG LIST COMPOSABLES & OPTIMIZATIONS
// ─────────────────────────────────────────────

@Composable
fun SongList(
    songs: LazyPagingItems<Song>,
    currentSong: Song?,
    isPlaying: Boolean,
    favoriteSongIds: Set<String>,
    onSongSelected: (Song) -> Unit,
    onFavoriteToggle: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

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
                val isSelected = song.id == currentSong?.id
                val isFavorite = favoriteSongIds.contains(song.id)
                val activePlayingState = isSelected && isPlaying

                SongListItem(
                    song = song,
                    isSelected = isSelected,
                    isPlaying = activePlayingState,
                    isFavorite = isFavorite,
                    onSongSelected = onSongSelected,
                    onFavoriteToggle = { onFavoriteToggle(song) },
                    onAddToPlaylist = { onAddToPlaylist(song) }
                )
            }
        }
        item(contentType = "spacer") { Spacer(modifier = Modifier.height(96.dp)) }
    }
}

@Composable
fun SongListItem(
    song: Song,
    isSelected: Boolean,
    isPlaying: Boolean,
    isFavorite: Boolean,
    onSongSelected: (Song) -> Unit,
    onFavoriteToggle: () -> Unit,
    onAddToPlaylist: () -> Unit
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

        // Action controls
        IconButton(onClick = onFavoriteToggle) {
            Icon(
                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = "Favorito",
                tint = if (isFavorite) Color(0xFFEF4444) else Color.White.copy(alpha = 0.35f),
                modifier = Modifier.size(20.dp)
            )
        }

        IconButton(onClick = onAddToPlaylist) {
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

// ─────────────────────────────────────────────
//  ALBUMS, PLAYLISTS & ARTISTS COMPONENTS
// ─────────────────────────────────────────────

@Composable
fun AlbumGridItem(
    album: Album,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.03f)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.04f))
            ) {
                AlbumArtwork(
                    albumId = album.id,
                    coverUri = album.coverPath,
                    contentDescription = null,
                    size = 256,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = album.albumName,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = album.artist,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${album.songCount} canciones",
                color = Color(0xFFA5B4FC),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun PlaylistListItem(
    playlist: Playlist,
    songCount: Int,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.03f)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF6366F1).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = Color(0xFFA5B4FC),
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$songCount canciones",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp
                )
            }
            if (playlist.name != "Favoritas") {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Eliminar",
                        tint = Color(0xFFEF4444).copy(alpha = 0.8f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ArtistListItem(
    artist: Artist,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.03f)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF6366F1).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = artist.name.take(1).uppercase(),
                    color = Color(0xFFA5B4FC),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = artist.name,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${artist.albumCount} álbumes • ${artist.songCount} canciones",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp
                )
            }
        }
    }
}

// ─────────────────────────────────────────────
//  DIALOGS
// ─────────────────────────────────────────────

@Composable
fun AddToPlaylistDialog(
    song: Song,
    libraryViewModel: LibraryViewModel,
    onDismiss: () -> Unit
) {
    val playlists by libraryViewModel.playlistsFlow.collectAsStateWithLifecycle(emptyList())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Añadir a Playlist", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 250.dp)) {
                Text("Selecciona una playlist para añadir \"${song.title}\":", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                Spacer(modifier = Modifier.height(12.dp))
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().weight(1f)
                ) {
                    items(playlists) { playlist ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(alpha = 0.05f))
                                .clickable {
                                    libraryViewModel.addSongToPlaylist(playlist.id, song.id)
                                    onDismiss()
                                }
                                .padding(12.dp)
                        ) {
                            Text(playlist.name, color = Color.White, fontSize = 14.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = Color(0xFFA5B4FC)) }
        },
        containerColor = Color(0xFF1E1E2C)
    )
}

@Composable
fun CreatePlaylistDialog(
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nueva Playlist", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Escribe el nombre de la nueva playlist:", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    if (text.isEmpty()) {
                        Text("Nombre de la playlist...", color = Color.White.copy(alpha = 0.3f), fontSize = 14.sp)
                    }
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                        cursorBrush = SolidColor(Color(0xFF818CF8)),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (text.trim().isNotEmpty()) {
                        onCreate(text.trim())
                        onDismiss()
                    }
                }
            ) {
                Text("Crear", color = Color(0xFF818CF8), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = Color.White.copy(alpha = 0.5f)) }
        },
        containerColor = Color(0xFF1E1E2C)
    )
}

// ─────────────────────────────────────────────
//  DETAIL SCREENS
// ─────────────────────────────────────────────

@Composable
fun AlbumDetailScreen(
    albumId: Long,
    albumName: String,
    playbackViewModel: PlaybackViewModel,
    libraryViewModel: LibraryViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val songs by libraryViewModel.getSongsByAlbum(albumId).collectAsStateWithLifecycle(initialValue = emptyList())
    val favoriteSongIds by libraryViewModel.favoriteSongIds.collectAsStateWithLifecycle(emptySet())

    val backgroundBrush = remember {
        Brush.verticalGradient(
            colors = listOf(Color(0xFF1A1A2E), Color(0xFF0F0F1A), Color(0xFF0A0A0F))
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
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = albumName,
                    color = Color.White,
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
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .shadow(16.dp)
                ) {
                    AlbumArtwork(
                        albumId = albumId,
                        coverUri = songs.firstOrNull()?.artworkUri ?: "",
                        contentDescription = albumName,
                        size = 256,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = albumName,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                Text(
                    text = songs.firstOrNull()?.artist ?: "Artista Desconocido",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = { if (songs.isNotEmpty()) playbackViewModel.playSong(songs.first(), songs) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Reproducir Álbum")
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(songs) { song ->
                    SongListItem(
                        song = song,
                        isSelected = false,
                        isPlaying = false,
                        isFavorite = favoriteSongIds.contains(song.id),
                        onSongSelected = { playbackViewModel.playSong(song, songs) },
                        onFavoriteToggle = { libraryViewModel.toggleFavorite(song) },
                        onAddToPlaylist = {}
                    )
                }
            }
        }
    }
}

@Composable
fun ArtistDetailScreen(
    artistName: String,
    playbackViewModel: PlaybackViewModel,
    libraryViewModel: LibraryViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val songs by libraryViewModel.getSongsByArtist(artistName).collectAsStateWithLifecycle(initialValue = emptyList())
    val favoriteSongIds by libraryViewModel.favoriteSongIds.collectAsStateWithLifecycle(emptySet())

    val backgroundBrush = remember {
        Brush.verticalGradient(
            colors = listOf(Color(0xFF1E1E2F), Color(0xFF0F0F1A), Color(0xFF0A0A0F))
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
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = artistName,
                    color = Color.White,
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
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF6366F1).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(artistName.take(1).uppercase(), color = Color(0xFFA5B4FC), fontSize = 38.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = artistName,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${songs.size} canciones",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(songs) { song ->
                    SongListItem(
                        song = song,
                        isSelected = false,
                        isPlaying = false,
                        isFavorite = favoriteSongIds.contains(song.id),
                        onSongSelected = { playbackViewModel.playSong(song, songs) },
                        onFavoriteToggle = { libraryViewModel.toggleFavorite(song) },
                        onAddToPlaylist = {}
                    )
                }
            }
        }
    }
}

@Composable
fun PlaylistDetailScreen(
    playlistId: Long,
    playlistName: String,
    playbackViewModel: PlaybackViewModel,
    libraryViewModel: LibraryViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val songs by libraryViewModel.getSongsForPlaylist(playlistId).collectAsStateWithLifecycle(initialValue = emptyList())
    val favoriteSongIds by libraryViewModel.favoriteSongIds.collectAsStateWithLifecycle(emptySet())

    val backgroundBrush = remember {
        Brush.verticalGradient(
            colors = listOf(Color(0xFF1E1C38), Color(0xFF0F0F1A), Color(0xFF0A0A0F))
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
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = playlistName,
                    color = Color.White,
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
                        .size(100.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF6366F1).copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = Color(0xFFA5B4FC),
                        modifier = Modifier.size(44.dp)
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = playlistName,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${songs.size} canciones",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = { if (songs.isNotEmpty()) playbackViewModel.playSong(songs.first(), songs) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Reproducir Playlist")
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(songs) { song ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                SongListItem(
                                    song = song,
                                    isSelected = false,
                                    isPlaying = false,
                                    isFavorite = favoriteSongIds.contains(song.id),
                                    onSongSelected = { playbackViewModel.playSong(song, songs) },
                                    onFavoriteToggle = { libraryViewModel.toggleFavorite(song) },
                                    onAddToPlaylist = {}
                                )
                            }
                            IconButton(onClick = { libraryViewModel.removeSongFromPlaylist(playlistId, song.id) }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Quitar",
                                    tint = Color.Red.copy(alpha = 0.5f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
//  PERMISSIVE COMPONENTS & HELPERS
// ─────────────────────────────────────────────

@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Buscar",
                tint = Color.White.copy(alpha = 0.35f),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = "Buscar canciones o artistas...",
                        color = Color.White.copy(alpha = 0.3f),
                        fontSize = 14.sp
                    )
                }
                val textStyle = remember {
                    TextStyle(color = Color.White, fontSize = 14.sp)
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = textStyle,
                    cursorBrush = SolidColor(Color(0xFF818CF8)),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun SongListShimmer(modifier: Modifier = Modifier) {
    val brush = shimmerBrush()
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        userScrollEnabled = false
    ) {
        items(10) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(brush)
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(brush)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.35f)
                            .height(10.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(brush)
                    )
                }
            }
        }
    }
}

@Composable
fun PermissionRequest(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(Color(0xFF6366F1).copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = Color(0xFF818CF8),
                modifier = Modifier.size(40.dp)
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Acceso a tu música",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Prism necesita acceso a tu almacenamiento\npara mostrar tus canciones locales.",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 14.sp,
            lineHeight = 20.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(horizontal = 40.dp)
        )
        Spacer(modifier = Modifier.height(28.dp))
        Button(
            onClick = onRequestPermission,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .padding(horizontal = 40.dp)
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text(
                text = "Conceder permiso",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
        }
    }
}

@Composable
fun EmptyLibrary(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.2f),
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No se encontraron canciones",
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 16.sp
        )
    }
}

@Composable
fun NoSearchResults(query: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.2f),
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Sin resultados para \"$query\"",
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 15.sp
        )
    }
}

// ─────────────────────────────────────────────
//  MINI PLAYER
// ─────────────────────────────────────────────

@Composable
fun MiniPlayer(
    state: PlaybackUiState,
    progressStateFlow: StateFlow<ProgressState>,
    onPlayPauseToggle: () -> Unit,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val song = state.currentSong ?: return

    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(24.dp, RoundedCornerShape(20.dp))
            .clickable { onOpenPlayer() },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E30)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column {
            MiniPlayerProgressBar(progressStateFlow)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SongArtwork(
                    song = song,
                    contentDescription = "Mini player art",
                    size = 128,
                    crossfade = false,
                    iconSize = 20.dp,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = song.artist,
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF6366F1))
                        .clickable { onPlayPauseToggle() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun MiniPlayerProgressBar(progressStateFlow: StateFlow<ProgressState>) {
    val progress by progressStateFlow.collectAsStateWithLifecycle(ProgressState())
    val progressFraction = if (progress.duration > 0) {
        (progress.currentPosition.toFloat() / progress.duration.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(Color.White.copy(alpha = 0.08f))
    ) {
        val progressBrush = remember {
            Brush.horizontalGradient(
                colors = listOf(Color(0xFF818CF8), Color(0xFF6366F1))
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth(progressFraction)
                .fillMaxHeight()
                .background(brush = progressBrush)
        )
    }
}

// ─────────────────────────────────────────────
//  PLAYER SCREEN (Detail)
// ─────────────────────────────────────────────

@Composable
fun PlayerScreen(
    viewModel: PlaybackViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle(PlaybackUiState())

    val currentSongColor by viewModel.currentSongColor.collectAsStateWithLifecycle(null)
    val defaultColor = Color(0xFF1E1B4B)
    val dominantColor = currentSongColor?.let { Color(it) } ?: defaultColor

    val animatedColor by animateColorAsState(
        targetValue = dominantColor,
        animationSpec = tween(durationMillis = 800, easing = LinearOutSlowInEasing),
        label = "playerBgColor"
    )

    val playerBrush = remember(animatedColor) {
        Brush.verticalGradient(
            colors = listOf(
                animatedColor.copy(alpha = 0.45f),
                Color(0xFF0F172A),
                Color(0xFF020617)
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(brush = playerBrush)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver",
                        tint = Color.White.copy(alpha = 0.8f)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "REPRODUCIENDO",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.size(48.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))

            val onPlayPauseToggle = remember(viewModel) { { viewModel.togglePlayPause() } }
            val onNext = remember(viewModel) { { viewModel.next() } }
            val onPrevious = remember(viewModel) { { viewModel.previous() } }
            val onSeek = remember(viewModel) { { ms: Long -> viewModel.seekTo(ms) } }

            PlayerCard(
                state = state,
                progressStateFlow = viewModel.progressState,
                onPlayPauseToggle = onPlayPauseToggle,
                onNext = onNext,
                onPrevious = onPrevious,
                onSeek = onSeek
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun PlayerCard(
    state: PlaybackUiState,
    progressStateFlow: StateFlow<ProgressState>,
    onPlayPauseToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 16.dp, shape = RoundedCornerShape(24.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.06f)),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Crossfade(
                targetState = state.currentSong,
                animationSpec = tween(durationMillis = 500, easing = LinearOutSlowInEasing),
                label = "albumArtCrossfade"
            ) { currentSong ->
                SongArtwork(
                    song = currentSong,
                    contentDescription = "Album Art",
                    size = 256,
                    crossfade = true,
                    iconSize = 80.dp,
                    modifier = Modifier
                        .size(240.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .shadow(8.dp, shape = RoundedCornerShape(20.dp))
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = state.currentSong?.title ?: "No Song Playing",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = state.currentSong?.artist ?: "Unknown Artist",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(24.dp))

            PlaybackProgress(
                progressStateFlow = progressStateFlow,
                onSeek = onSeek
            )

            Spacer(modifier = Modifier.height(16.dp))

            PlaybackControls(
                isPlaying = state.isPlaying,
                onPlayPauseToggle = onPlayPauseToggle,
                onNext = onNext,
                onPrevious = onPrevious
            )
        }
    }
}

@Composable
fun PlaybackProgress(
    progressStateFlow: StateFlow<ProgressState>,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val progress by progressStateFlow.collectAsStateWithLifecycle(ProgressState())
    val currentPosition = progress.currentPosition
    val duration = progress.duration

    var isDragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(0f) }
    val totalDuration = remember(duration) { duration.coerceAtLeast(1L) }

    val progressFraction = if (isDragging) dragPosition
    else (currentPosition.toFloat() / totalDuration).coerceIn(0f, 1f)

    val sliderColors = SliderDefaults.colors(
        thumbColor = Color(0xFFA5B4FC),
        activeTrackColor = Color(0xFF6366F1),
        inactiveTrackColor = Color.White.copy(alpha = 0.15f)
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Slider(
            value = progressFraction,
            onValueChange = { fraction ->
                isDragging = true
                dragPosition = fraction
            },
            onValueChangeFinished = {
                isDragging = false
                onSeek((dragPosition * totalDuration).toLong())
            },
            colors = sliderColors,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatTime(if (isDragging) (dragPosition * totalDuration).toLong() else currentPosition),
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp
            )
            Text(
                text = formatTime(duration),
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun PlaybackControls(
    isPlaying: Boolean,
    onPlayPauseToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevious, modifier = Modifier.size(56.dp)) {
            Icon(
                imageVector = Icons.Default.SkipPrevious,
                contentDescription = "Previous",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(modifier = Modifier.width(24.dp))
        val playButtonBrush = remember {
            Brush.radialGradient(
                colors = listOf(Color(0xFF818CF8), Color(0xFF4F46E5))
            )
        }
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(brush = playButtonBrush)
                .clickable { onPlayPauseToggle() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = "Play/Pause",
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
        }
        Spacer(modifier = Modifier.width(24.dp))
        IconButton(onClick = onNext, modifier = Modifier.size(56.dp)) {
            Icon(
                imageVector = Icons.Default.SkipNext,
                contentDescription = "Next",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(java.util.Locale.US, minutes, seconds)
}
