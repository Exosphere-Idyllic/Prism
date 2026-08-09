package com.example.melodyplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.melodyplayer.R
import com.example.melodyplayer.LibraryViewModel
import com.example.melodyplayer.PlaybackViewModel
import com.example.melodyplayer.data.Song
import kotlinx.collections.immutable.persistentSetOf
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
    val currentOnBack by rememberUpdatedState(onBack)

    // remember() stabilises the Flow reference so collectAsStateWithLifecycle doesn't see
    // a new object on every recomposition (which would cancel + reopen the Room query each time).
    val rawSongs by remember(playlistId) { libraryViewModel.getSongsForPlaylist(playlistId) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val songs = remember(rawSongs) { rawSongs.toImmutableList() }
    val favoriteSongIds by libraryViewModel.favoriteSongIds.collectAsStateWithLifecycle(persistentSetOf())
    val currentSong by playbackViewModel.currentSong.collectAsStateWithLifecycle()
    val isPlaying by playbackViewModel.isPlayingState.collectAsStateWithLifecycle()

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
                IconButton(onClick = { currentOnBack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back), tint = Color.White)
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
                    text = stringResource(R.string.song_count_format, songs.size),
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
                    Text(stringResource(R.string.play_playlist))
                }
            }

            val onPlaySong: (Song) -> Unit = remember(songs, playbackViewModel) {
                { song: Song -> playbackViewModel.playSong(song, songs) }
            }
            val onToggleFav: (Song) -> Unit = remember(libraryViewModel) {
                { song: Song -> libraryViewModel.toggleFavorite(song) }
            }
            val onRemove: (Song) -> Unit = remember(playlistId, libraryViewModel) {
                { song: Song -> libraryViewModel.removeSongFromPlaylist(playlistId, song.id) }
            }

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(songs, key = { it.id }) { song ->
                    val isFavorite = favoriteSongIds.contains(song.id)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            SongListItemWrapper(
                                song = song,
                                currentSongId = currentSong?.id,
                                isPlaying = isPlaying,
                                isFavorite = isFavorite,
                                onSongSelected = onPlaySong,
                                onFavoriteToggle = onToggleFav,
                                onAddToPlaylist = null
                            )
                        }
                        IconButton(onClick = { onRemove(song) }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.cd_remove),
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
