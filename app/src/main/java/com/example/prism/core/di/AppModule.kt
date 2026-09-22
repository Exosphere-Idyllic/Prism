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
import com.example.prism.data.preferences.EqualizerPreferences
import com.example.prism.data.repository.EqualizerRepositoryImpl
import com.example.prism.domain.repository.EqualizerRepository
import com.example.prism.player.CustomCommandDispatcher
import com.example.prism.player.PlaybackManager
import com.example.prism.player.PlaybackManagerImpl
import com.example.prism.player.effects.EqualizerAudioProcessor
import com.example.prism.player.effects.EqualizerController
import com.example.prism.player.effects.EqualizerControllerImpl
import com.example.prism.ui.equalizer.EqualizerViewModel
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

    single {
        PlaybackManagerImpl(
            app = get(),
            repository = get<LibraryRepository>(),
            dispatchers = get(),
            scope = get(),
        )
    }
    single<PlaybackManager> { get<PlaybackManagerImpl>() }
    single<CustomCommandDispatcher> { get<PlaybackManagerImpl>() }

    single<LyricsRepository> {
        LyricsRepositoryImpl(
            context = get(),
            database = get(),
            dispatchers = get(),
        )
    }

    single { EqualizerPreferences(context = get()) }

    single<EqualizerRepository> {
        EqualizerRepositoryImpl(
            preferences = get(),
            scope = get(),
            dispatchers = get(),
        )
    }

    single { EqualizerAudioProcessor() }

    single<EqualizerController> {
        EqualizerControllerImpl(
            equalizerRepository = get(),
            commandDispatcher = get(),
            scope = get(),
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
    viewModel {
        EqualizerViewModel(
            repository = get<EqualizerRepository>(),
            controller = get<EqualizerController>(),
            dispatchers = get<DispatcherProvider>(),
        )
    }
}
