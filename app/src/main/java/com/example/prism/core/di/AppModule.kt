package com.example.prism.core.di

import com.example.prism.core.util.DefaultDispatcherProvider
import com.example.prism.core.util.DispatcherProvider
import com.example.prism.data.db.AppDatabase
import com.example.prism.data.preferences.ScanPreferences
import com.example.prism.data.repository.MusicRepositoryImpl
import com.example.prism.data.repository.PlaylistRepositoryImpl
import com.example.prism.domain.repository.LibraryRepository
import com.example.prism.domain.repository.MusicRepository
import com.example.prism.domain.repository.PlaylistRepository
import com.example.prism.domain.repository.ScannerRepository
import com.example.prism.player.PlaybackManager
import com.example.prism.player.PlaybackManagerImpl
import com.example.prism.ui.library.LibraryViewModel
import com.example.prism.ui.player.PlaybackViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single<DispatcherProvider> { DefaultDispatcherProvider() }
    single { CoroutineScope(SupervisorJob() + get<DispatcherProvider>().default) }
    single { AppDatabase.getDatabase(get()) }
    single { get<AppDatabase>().songDao() }
    single { get<AppDatabase>().albumDao() }
    single { get<AppDatabase>().artistDao() }
    single { get<AppDatabase>().playlistDao() }
    single<PlaylistRepository> { PlaylistRepositoryImpl(get(), get()) }
    single { ScanPreferences(get()) }

    single<MusicRepository> {
        MusicRepositoryImpl(
            app = get(),
            scope = get(),
            database = get(),
            playlistRepository = get(),
            dispatchers = get()
        )
    }
    single<LibraryRepository> { get<MusicRepository>() }
    single<ScannerRepository> { get<MusicRepository>() }

    single<PlaybackManager> {
        PlaybackManagerImpl(
            app = get(),
            repository = get<LibraryRepository>(),
            dispatchers = get(),
            scope = get(),
        )
    }

    viewModel {
        LibraryViewModel(
            repository = get(),
            dispatchers = get(),
        )
    }
    viewModel { PlaybackViewModel(playbackManager = get()) }
}

