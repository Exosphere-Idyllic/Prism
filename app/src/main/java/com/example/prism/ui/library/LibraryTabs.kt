package com.example.prism.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.prism.R
import com.example.prism.data.entity.Album
import com.example.prism.data.entity.Artist
import com.example.prism.data.repository.isFavoritesPlaylist
import com.example.prism.domain.model.PlaylistWithCount
import com.example.prism.ui.components.AlbumArtwork

import com.example.prism.ui.theme.AppAccentBg
import com.example.prism.ui.theme.AppAccentSoft
import com.example.prism.ui.theme.AppDeleteTint
import com.example.prism.ui.theme.AppSurface
import com.example.prism.ui.theme.AppTextPrimary
import com.example.prism.ui.theme.AppTextSecondary
import com.example.prism.ui.theme.AppTrackBg

@Composable
fun AlbumGridItem(
    album: Album,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AppSurface)
            .clickable(onClick = onClick)
            .padding(10.dp)
    ) {
        Box(
            modifier = Modifier
                .aspectRatio(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(AppTrackBg)
        ) {
            AlbumArtwork(
                albumId = album.id,
                coverUri = album.coverPath,
                customCoverUri = album.customCoverUri,
                contentDescription = null,
                size = 256,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = album.albumName,
            color = AppTextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = album.artist,
            color = AppTextSecondary,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = pluralStringResource(R.plurals.songs_count, album.songCount, album.songCount),
            color = AppAccentSoft,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun PlaylistListItem(
    playlist: PlaylistWithCount,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AppSurface)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(AppAccentBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = AppAccentSoft,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        val displayName = if (isFavoritesPlaylist(playlist.name)) {
            stringResource(R.string.playlist_favorites)
        } else {
            playlist.name
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = displayName,
                color = AppTextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = pluralStringResource(R.plurals.songs_count, playlist.songCount, playlist.songCount),
                color = AppTextSecondary,
                fontSize = 12.sp
            )
        }
        if (!isFavoritesPlaylist(playlist.name)) {
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.cd_delete),
                    tint = AppDeleteTint.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun ArtistListItem(
    artist: Artist,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AppSurface)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(AppAccentBg),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = artist.name.take(1).uppercase(),
                color = AppAccentSoft,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = artist.name,
                color = AppTextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = stringResource(R.string.artist_albums_songs_count, artist.albumCount, artist.songCount),
                color = AppTextSecondary,
                fontSize = 12.sp
            )
        }
    }
}
