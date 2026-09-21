package com.example.prism.core.di

import com.example.prism.core.util.DefaultDispatcherProvider
import com.example.prism.core.util.DispatcherProvider
import com.example.prism.data.db.AppDatabase
import com.example.prism.data.media.MusicScannerManager
import com.example.prism.data.repository.LibraryRepositoryImpl
import com.example.prism.data.repository.LyricsRepositoryImpl
import com.example.prism.data.repository.PlaylistRepositoryImpl
import com.example.prism.domain.repository.LibraryRepository
import com.example.prism.domain.repository.LyricsRepository
import com.example.prism.domain.repository.PlaylistRepository
import com.example.prism.domain.repository.ScannerRepository
import com.example.prism.player.PlaybackManager
import com.example.prism.player.PlaybackManagerImpl
import com.example.prism.ui.library.LibraryViewModel
import com.example.prism.ui.player.LyricsViewModel
import com.example.prism.ui.player.PlaybackViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single<DispatcherProvider> { DefaultDispatcherProvider() }
    single { CoroutineScope(SupervisorJob() + get<DispatcherProvider>().default) }
    single { AppDatabase.build(get()) }
    single<LibraryRepository> { LibraryRepositoryImpl(database = get(), dispatchers = get()) }
    single<PlaylistRepository> { PlaylistRepositoryImpl(database = get(), dispatchers = get()) }

    single<ScannerRepository> {
        MusicScannerManager(
            app = get(),
            scope = get(),
            database = get(),
            dispatchers = get(),
        )
    }

    single<PlaybackManager> {
        PlaybackManagerImpl(
            app = get(),
            repository = get<LibraryRepository>(),
            dispatchers = get(),
            scope = get(),
        )
    }

    single<LyricsRepository> {
        LyricsRepositoryImpl(
            context = get(),
            database = get(),
            dispatchers = get(),
        )
    }

    viewModel {
        LibraryViewModel(
            libraryRepository = get<LibraryRepository>(),
            playlistRepository = get<PlaylistRepository>(),
            scannerRepository = get<ScannerRepository>(),
            dispatchers = get(),
        )
    }
    viewModel { PlaybackViewModel(playbackManager = get()) }
    viewModel { LyricsViewModel(lyricsRepository = get()) }
}
