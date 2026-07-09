package com.example.melodyplayer.ui

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.melodyplayer.LibraryViewModel
import com.example.melodyplayer.data.Song

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
                    items(playlists, key = { it.id }) { playlist ->
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
