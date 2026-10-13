package com.example.prism.ui.player

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.prism.R
import com.example.prism.core.util.resolveArtistName
import com.example.prism.data.entity.Song
import com.example.prism.domain.model.LyricsSource
import com.example.prism.domain.model.SongLyrics
import com.example.prism.player.ProgressState
import com.example.prism.ui.components.EditArtworkDialog
import com.example.prism.ui.components.PlayerSongArtwork
import com.example.prism.ui.library.LibraryViewModel
import com.example.prism.ui.player.components.LyricsDisplay
import com.example.prism.ui.player.components.PlaybackControls
import com.example.prism.ui.player.components.PlaybackProgress
import com.example.prism.ui.theme.AppAccentSoft
import com.example.prism.ui.theme.AppSurface2
import com.example.prism.ui.theme.AppTextPrimary
import com.example.prism.ui.theme.AppTextSecondary
import com.example.prism.ui.theme.AppBgTop
import com.example.prism.ui.theme.AppBgBottom
import com.example.prism.ui.theme.PlayerBackgroundBrush
import androidx.compose.animation.Animatable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@Composable
fun PlayerScreen(
    viewModel: PlaybackViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToEqualizer: () -> Unit = {},
    lyricsViewModel: LyricsViewModel = koinViewModel(),
    libraryViewModel: LibraryViewModel = koinViewModel(),
) {
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlayingState.collectAsStateWithLifecycle()
    val shuffleEnabled by viewModel.shuffleModeState.collectAsStateWithLifecycle()
    val repeatMode by viewModel.repeatModeState.collectAsStateWithLifecycle()
    val songLyrics by lyricsViewModel.songLyrics.collectAsStateWithLifecycle()
    var showEditArtworkDialog by remember { mutableStateOf(false) }

    LaunchedEffect(currentSong?.id) {
        lyricsViewModel.loadLyrics(currentSong)
    }

    PlayerContent(
        currentSong = currentSong,
        isPlaying = isPlaying,
        shuffleEnabled = shuffleEnabled,
        repeatMode = repeatMode,
        progressState = viewModel.progressState,
        songLyrics = songLyrics,
        onSelectLyricsSource = { lyricsViewModel.selectSource(it) },
        onBack = onBack,
        onNavigateToEqualizer = onNavigateToEqualizer,
        onPlayPauseToggle = { viewModel.togglePlayPause() },
        onNext = { viewModel.next() },
        onPrevious = { viewModel.previous() },
        onToggleShuffle = { viewModel.toggleShuffle() },
        onToggleRepeat = { viewModel.toggleRepeatMode() },
        onSeek = { ms -> viewModel.seekTo(ms) },
        onEditArtwork = { showEditArtworkDialog = true },
        modifier = modifier,
    )

    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    if (showEditArtworkDialog && currentSong != null) {
        val song = currentSong!!
        EditArtworkDialog(
            title = song.title,
            currentCustomUri = song.customArtworkUri,
            onSave = { uri ->
                val oldUri = song.customArtworkUri
                if (oldUri.isNotEmpty() && oldUri != uri) {
                    coroutineScope.launch {
                        com.example.prism.data.artwork.ArtworkStorage.deleteIfInternalFile(context, oldUri)
                    }
                }
                libraryViewModel.updateSongArtwork(song.id, uri)
            },
            onDismiss = { showEditArtworkDialog = false }
        )
    }
}

@Composable
fun PlayerContent(
    currentSong: Song?,
    isPlaying: Boolean,
    shuffleEnabled: Boolean,
    repeatMode: Int,
    progressState: StateFlow<ProgressState>,
    songLyrics: SongLyrics?,
    onSelectLyricsSource: (LyricsSource) -> Unit,
    onBack: () -> Unit,
    onPlayPauseToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onSeek: (Long) -> Unit,
    onEditArtwork: () -> Unit = {},
    onNavigateToEqualizer: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val currentOnBack by rememberUpdatedState(onBack)
    val unknownArtistLabel = stringResource(R.string.unknown_artist)
    val artistText = remember(currentSong?.artist, unknownArtistLabel) {
        resolveArtistName(currentSong?.artist, unknownArtistLabel)
    }

    var showLyrics by remember { mutableStateOf(false) }

    // Use Animatable so the color transition runs purely in the draw phase.
    // Reading animatedTopColor.value inside drawBehind skips composition and layout
    // entirely during the 600ms background animation — no recomposition occurs.
    val animatedTopColor = remember { Animatable(AppBgTop) }

    // targetBgColor is the only state written from the palette callback.
    // A LaunchedEffect observes it and drives the Animatable via animateTo (suspend).
    var targetBgColor by remember { mutableStateOf(AppBgTop) }
    val onPrimaryColorExtracted: (Color) -> Unit = remember {
        { color ->
            targetBgColor = color.copy(alpha = 0.35f)
        }
    }
    LaunchedEffect(currentSong?.id) {
        targetBgColor = AppBgTop
        animatedTopColor.snapTo(AppBgTop)
    }
    LaunchedEffect(targetBgColor) {
        animatedTopColor.animateTo(
            targetValue = targetBgColor,
            animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                // Read animatedTopColor.value here in the draw phase — no recomposition triggered.
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(animatedTopColor.value, AppBgBottom)
                    )
                )
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ── Top bar ────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(
                    onClick = { currentOnBack() },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(AppSurface2),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back),
                        tint = AppTextPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }

                Text(
                    text = stringResource(R.string.now_playing_uppercase),
                    color = AppTextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.5.sp,
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Equalizer Navigation Button
                    IconButton(
                        onClick = onNavigateToEqualizer,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(AppSurface2),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = stringResource(R.string.cd_equalizer),
                            tint = AppTextPrimary,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    // Toggle between Artwork and Lyrics
                    IconButton(
                        onClick = { showLyrics = !showLyrics },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (showLyrics) AppAccentSoft.copy(alpha = 0.2f) else AppSurface2),
                    ) {
                        Icon(
                            imageVector = if (showLyrics) Icons.Default.Album else Icons.Default.Lyrics,
                            contentDescription = stringResource(
                                if (showLyrics) R.string.cd_artwork else R.string.cd_lyrics,
                            ),
                            tint = if (showLyrics) AppAccentSoft else AppTextPrimary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Center Stage: Artwork or Lyrics ────────────────────────────
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Crossfade(
                    targetState = showLyrics,
                    animationSpec = tween(350, easing = FastOutSlowInEasing),
                    label = "playerCenterCrossfade",
                ) { isLyricsVisible ->
                    if (isLyricsVisible) {
                        LyricsDisplay(
                            songLyrics = songLyrics,
                            currentPositionProvider = { progressState.value.currentPosition },
                            onSelectSource = onSelectLyricsSource,
                            onSeekTo = onSeek,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            Crossfade(
                                targetState = currentSong,
                                animationSpec = tween(400, easing = FastOutSlowInEasing),
                                label = "albumArtCrossfade",
                            ) { crossfadeSong ->
                                Box(
                                    modifier = Modifier
                                        .size(300.dp)
                                        .shadow(32.dp, RoundedCornerShape(24.dp))
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(AppSurface2)
                                        .clickable { onEditArtwork() },
                                ) {
                                    PlayerSongArtwork(
                                        song = crossfadeSong,
                                        contentDescription = stringResource(R.string.cd_album_art),
                                        onPrimaryColorExtracted = onPrimaryColorExtracted,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                    // Edit badge in bottom-end corner
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(12.dp)
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(AppSurface2.copy(alpha = 0.85f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = stringResource(R.string.cd_edit_artwork),
                                            tint = AppTextPrimary,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(28.dp))

                            Text(
                                text = currentSong?.title ?: stringResource(R.string.no_song_playing),
                                color = AppTextPrimary,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = artistText,
                                color = AppTextSecondary,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Progress ───────────────────────────────────────────────────
            PlaybackProgress(
                progressStateFlow = progressState,
                onSeek = onSeek,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ── Controls ───────────────────────────────────────────────────
            PlaybackControls(
                isPlaying = isPlaying,
                shuffleEnabled = shuffleEnabled,
                repeatMode = repeatMode,
                onPlayPauseToggle = onPlayPauseToggle,
                onNext = onNext,
                onPrevious = onPrevious,
                onToggleShuffle = onToggleShuffle,
                onToggleRepeat = onToggleRepeat,
            )

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
