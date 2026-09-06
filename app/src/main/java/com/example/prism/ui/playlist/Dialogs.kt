package com.example.prism.ui.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.prism.R
import com.example.prism.data.entity.Song
import com.example.prism.data.repository.FAVORITES_PLAYLIST_NAME
import com.example.prism.ui.library.LibraryViewModel
import com.example.prism.ui.theme.*

@Composable
fun AddToPlaylistDialog(
    song: Song,
    libraryViewModel: LibraryViewModel,
    onDismiss: () -> Unit,
) {
    val playlists by libraryViewModel.playlistsWithCountsFlow.collectAsStateWithLifecycle()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_to_playlist_title), color = AppTextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 250.dp)) {
                Text(stringResource(R.string.select_playlist_for_song, song.title), color = AppTextSecondary, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(12.dp))
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().weight(1f)
                ) {
                    items(playlists, key = { it.id }) { playlist ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(AppSurface2)
                                .clickable {
                                    libraryViewModel.addSongToPlaylist(playlist.id, song.id)
                                    onDismiss()
                                }
                                .padding(12.dp)
                        ) {
                            val displayName = if (playlist.name == FAVORITES_PLAYLIST_NAME) {
                                stringResource(R.string.playlist_favorites)
                            } else {
                                playlist.name
                            }
                            Text(displayName, color = AppTextPrimary, fontSize = 14.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = AppAccentSoft) }
        },
        containerColor = AppSurface
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
        title = { Text(stringResource(R.string.new_playlist_title), color = AppTextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.enter_playlist_name), color = AppTextSecondary, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(AppSurface2)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    if (text.isEmpty()) {
                        Text(stringResource(R.string.playlist_name_hint), color = AppTextSecondary.copy(alpha = 0.6f), fontSize = 14.sp)
                    }
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        singleLine = true,
                        textStyle = TextStyle(color = AppTextPrimary, fontSize = 14.sp),
                        cursorBrush = SolidColor(AppAccentSoft),
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
                Text(stringResource(R.string.create), color = AppAccentSoft, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = AppTextSecondary) }
        },
        containerColor = AppSurface
    )
}
