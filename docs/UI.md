# Módulo UI

**Paquetes:** `com.example.prism.ui`, `com.example.prism.navigation`

La capa de presentación: pantallas y componentes Jetpack Compose (Material 3)
más los `ViewModel` que les dan estado. Consume exclusivamente las interfaces
de `domain/repository` y `player/PlaybackManager`/`player/effects/EqualizerController`
— nunca accede a Room, DataStore o `MediaStore` directamente.

## Contenido

```
ui/
├── theme/          # Color, tipografía y Theme de Material 3
├── components/     # Componentes reutilizables entre pantallas
├── library/        # Pantalla principal (tabs: canciones/álbumes/playlists/artistas)
├── album/          # Detalle de álbum
├── artist/         # Detalle de artista
├── playlist/        # Detalle de playlist + diálogos
├── player/         # Reproductor a pantalla completa + letras
└── equalizer/      # Pantalla del ecualizador
navigation/
├── AppNavigation.kt
└── NavKeys.kt
```

## Navegación (`navigation/`)

Usa **Navigation 3** (`androidx.navigation3`), la API de navegación basada
en pila de `NavKey` serializables en lugar de rutas por string.

- **`NavKeys.kt`** define cada destino como un `@Serializable` `data
  object`/`data class` implementando `NavKey`: `SongList`, `Player`,
  `Equalizer`, `AlbumDetail(albumId, albumName, coverPath,
  customCoverUri)`, `ArtistDetail(artistName)`,
  `PlaylistDetail(playlistId, playlistName)`.
- **`AppNavigation.kt`** — `MainNavigation()` es el único punto de entrada
  Compose de la app (llamado desde `MainActivity`). Mantiene el
  `backStack` con `rememberNavBackStack(SongList)`, obtiene
  `PlaybackViewModel` y `LibraryViewModel` vía `koinViewModel()` **una sola
  vez** a este nivel (se pasan hacia abajo a todas las pantallas, en lugar
  de que cada pantalla resuelva su propia instancia — así el estado de
  reproducción y de biblioteca sobrevive a la navegación entre pantallas).
  Define transiciones de deslizamiento con easing personalizado, distintas
  para navegación hacia adelante, hacia atrás y gesto de retroceso
  predictivo (`predictivePopTransitionSpec`).

## Tema (`ui/theme/`)

`PrismTheme` (Material 3) con `Color.kt` y `Type.kt` definiendo la paleta y
tipografía de la app. Sin lógica de negocio.

## Componentes compartidos (`ui/components/`)

| Archivo | Contenido |
|---|---|
| `Artwork.kt` | `SongArtwork`/`AlbumArtwork` — envuelven `CoilImage` (Landscapist) sobre el `ImageLoader` global. Si no hay artwork, eligen de forma determinista uno de 5 drawables por defecto en función del hash del ID (misma canción/álbum siempre muestra el mismo placeholder). |
| `CommonComponents.kt` | `SearchBar`, `SongListShimmer` (skeleton de carga con `Modifier.shimmer()` que anima solo en fase de dibujo, sin recomposición por frame), `PermissionRequest`, `EmptyLibrary`, `NoSearchResults`. |
| `DetailComponents.kt` | `DetailTopBar`, `DetailHeroHeader`, `DetailSongList` — piezas reutilizadas por las tres pantallas de detalle (álbum, artista, playlist). |
| `EditArtworkDialog.kt` | Diálogo para cambiar o restablecer el artwork personalizado de una canción/álbum, con selección de imagen vía Android Photo Picker (`PickVisualMedia`). |
| `MiniPlayer.kt` | Reproductor compacto persistente en la parte inferior de la biblioteca, con su propia barra de progreso (`MiniPlayerProgressBar`). |
| `SongList.kt` | `SongList`/`SongListItem` — el ítem de lista reutilizado en biblioteca, álbum, artista y playlist. |

## Biblioteca (`ui/library/`)

### `LibraryViewModel`

ViewModel de la pantalla principal. Depende únicamente de
`LibraryRepository`, `PlaylistRepository` y `ScannerRepository` (ISP: no hay
un `ViewModel` "Dios" con acceso a todo).

- `songsFlow: Flow<PagingData<Song>>` — pipeline reactivo:
  `_searchQuery → debounce(300ms si hay texto, 0ms si está vacío) →
  flatMapLatest(libraryRepository.getSongsFlow) → cachedIn(viewModelScope)`.
- `albumsFlow`/`artistsFlow` — mismo patrón de debounce, pero materializados
  como `StateFlow` con `SharingStarted.Lazily`: la query permanece activa
  mientras el `ViewModel` viva, incluso sin suscriptores, para que cambiar
  de pestaña (Canciones ↔ Álbumes) no dispare una nueva consulta a Room.
- `playlistsWithCountsFlow`/`favoriteSongIds` — `StateFlow` con
  `SharingStarted.WhileSubscribed(5000)`, convertidos a colecciones
  inmutables de `kotlinx.collections.immutable` (`persistentListOf`,
  `persistentSetOf`) para evitar recomposiciones espurias en Compose por
  igualdad estructural de listas mutables.
- Expone `isLoading`/`totalSongsCount` directamente desde
  `ScannerRepository`, y `loadLocalSongs()` que dispara
  `scannerRepository.triggerScan()`. El `ViewModel` **no** llama a
  `startObserving`/`stopObserving` — ese ciclo de vida lo gestiona
  `PrismApplication` a nivel de proceso, para evitar conflictos si varias
  instancias de la pantalla se crean/destruyen.

### `SongListScreen`

Pantalla contenedora con 4 pestañas (`LibraryTab`: Biblioteca, Álbumes,
Playlists, Artistas), gestión del permiso de audio en tiempo de ejecución
(`READ_MEDIA_AUDIO`/`READ_EXTERNAL_STORAGE` según versión de Android),
barra de búsqueda, y el `MiniPlayer` anclado en la parte inferior. Cada
pestaña usa `LazyColumn`/`LazyVerticalGrid` con estado de scroll retenido
(`rememberLazyListState`/`rememberLazyGridState`).

### `LibraryTabs.kt`

Ítems específicos de cada pestaña: `AlbumGridItem`, `PlaylistListItem`,
`ArtistListItem`.

## Pantallas de detalle (`ui/album`, `ui/artist`, `ui/playlist`)

`AlbumDetailScreen`, `ArtistDetailScreen` y `PlaylistDetailScreen` comparten
la misma forma: reciben el `LibraryViewModel`/`PlaybackViewModel` ya creados
en `AppNavigation`, obtienen su propio `Flow<List<Song>>` filtrado
(`getSongsByAlbum`, `getSongsByArtist`, `getSongsForPlaylist`), y renderizan
`DetailTopBar` + `DetailHeroHeader` + `DetailSongList`.

`playlist/Dialogs.kt` contiene `AddToPlaylistDialog` y
`CreatePlaylistDialog`, usados desde la biblioteca y desde el detalle de
playlist.

## Reproductor (`ui/player/`)

### `PlaybackViewModel`

El `ViewModel` más delgado del proyecto: expone directamente los tres
`StateFlow` de `PlaybackManager` (`currentSong`, `isPlayingState`,
`progressState`) y delega cada acción (`playSong`, `togglePlayPause`,
`next`, `previous`, `seekTo`) sin lógica adicional — es una fachada fina
entre Compose y el módulo `player` (ver [PLAYER.md](./PLAYER.md)).

### `LyricsViewModel`

Deliberadamente separado de `PlaybackViewModel` (SRP): resuelve y cachea las
letras de la canción activa. `loadLyrics(song)` evita recargar si ya se
resolvió la misma canción (`song.id == currentSongId`), cancela una carga en
curso si la canción cambia antes de completarse (`loadJob?.cancel()`), y
permite `selectSource` (alternar entre letra embebida y LRC) y
`setCustomLyricsUri` (asignar un `.lrc` elegido por el usuario, recargando
tras guardar).

### `PlayerScreen`

Pantalla a pantalla completa con artwork, controles de transporte
(`PlaybackControls`), barra de progreso (`PlaybackProgress`), y acceso a
letras (`LyricsDisplay`) y al ecualizador. `components/` contiene estas tres
piezas desglosadas para mantener `PlayerScreen` como orquestador y no como
un archivo monolítico.

## Ecualizador (`ui/equalizer/`)

### `EqualizerUiState`

Estado inmutable de la pantalla: `enabled`, `mode`, `preampDb`,
`selectedPreset`, `availablePresets`, `bands`, `limiterEnabled`,
`selectedBandId` (banda actualmente seleccionada para inspección en modo
avanzado).

### `EqualizerViewModel`

Se suscribe a `EqualizerRepository.equalizerConfig` para mantener
`EqualizerUiState` sincronizado, y **debounce de 100 ms**
(`scheduleDebouncedSync`) antes de reenviar cambios de preamp/bandas al
`EqualizerController` — imprescindible porque arrastrar un slider o un nodo
en el Canvas paramétrico genera decenas de eventos por segundo, y cada
sincronización implica serialización JSON + IPC hacia el servicio de
reproducción. Los cambios de banda (`setBandGain`, `setBandFrequency`,
`setBandQ`, `setBandFilterType`, `updateBandParametric`) actualizan primero
el estado local (respuesta visual inmediata) y solo después de la ventana de
debounce llaman al `EqualizerController`. Los cambios discretos (`toggleEnabled`,
`setMode`, `selectPreset`, `toggleLimiter`, `reset`) se envían de inmediato,
sin debounce, porque no se disparan en ráfaga.

### `EqualizerScreen.kt`

La pantalla más grande de la UI (800+ líneas). Compone:

- `ModeSegmentedControl` — alterna entre modo Simple y Avanzado.
- `IsoBandsRow` + `VerticalBandSlider` — los 10 sliders verticales del modo
  simple (frecuencias ISO fijas).
- `PresetSelectorRow` — selector horizontal de presets predefinidos.
- `PreampCard` — control de ganancia de preamplificación.
- `BandChipsRow` + `BandInspectorCard` — en modo avanzado, selección de
  banda y panel de edición detallada (frecuencia, ganancia, Q, tipo de
  filtro) de la banda seleccionada.

### `equalizer/components/ParametricEqCanvas.kt`

El Canvas interactivo del modo avanzado: dibuja la curva de respuesta en
frecuencia acumulada `|H(f)|` (usando `BiquadCoefficients.magnitudeDb` de
cada banda activa) y expone nodos arrastrables por gesto táctil para editar
frecuencia y ganancia simultáneamente. Incluye las funciones de mapeo entre
espacio de datos y espacio de pantalla:

- `freqToNormalizedX`/`normalizedXToFreq` — escala **logarítmica** en el eje
  de frecuencia (20 Hz–20 kHz), como en cualquier ecualizador profesional.
- `dbToNormalizedY`/`normalizedYToDb` — escala lineal en el eje de ganancia
  (+15 dB a -15 dB).
