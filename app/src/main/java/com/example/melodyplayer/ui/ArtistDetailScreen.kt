package com.example.melodyplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.melodyplayer.LibraryViewModel
import com.example.melodyplayer.PlaybackViewModel
import com.example.melodyplayer.data.Song
import kotlinx.collections.immutable.toImmutableList

private val NoOpSongAction: (Song) -> Unit = { _ -> }

@Composable
fun ArtistDetailScreen(
    artistName: String,
    playbackViewModel: PlaybackViewModel,
    libraryViewModel: LibraryViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // remember() stabilises the Flow reference so collectAsStateWithLifecycle doesn't see
    // a new object on every recomposition (which would cancel + reopen the Room query each time).
    val rawSongs by remember(artistName) { libraryViewModel.getSongsByArtist(artistName) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val songs = remember(rawSongs) { rawSongs.toImmutableList() }
    val favoriteSongIds by libraryViewModel.favoriteSongIds.collectAsStateWithLifecycle(emptySet())
    val songThumbnail128Ids by libraryViewModel.songThumbnail128Ids.collectAsStateWithLifecycle()
    val currentSong by playbackViewModel.currentSong.collectAsStateWithLifecycle()
    val isPlaying by playbackViewModel.isPlayingState.collectAsStateWithLifecycle()

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

            val onPlaySong: (Song) -> Unit = remember(songs, playbackViewModel) {
                { song: Song -> playbackViewModel.playSong(song, songs) }
            }
            val onToggleFav: (Song) -> Unit = remember(libraryViewModel) {
                { song: Song -> libraryViewModel.toggleFavorite(song) }
            }

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(songs, key = { it.id }) { song ->
                    val isFavorite = favoriteSongIds.contains(song.id)
                    val hasWebp = songThumbnail128Ids.contains(song.id)

                    SongListItemWrapper(
                        song = song,
                        currentSongId = currentSong?.id,
                        isPlaying = isPlaying,
                        isFavorite = isFavorite,
                        hasWebp = hasWebp,
                        onSongSelected = onPlaySong,
                        onFavoriteToggle = onToggleFav,
                        onAddToPlaylist = NoOpSongAction
                    )
                }
            }
        }
    }
}
