# Módulo Repository

**Paquete:** `com.example.prism.data.repository`

Este documento cubre las **implementaciones concretas** de las interfaces
definidas en `domain/repository` (ver [DOMAIN.md](./DOMAIN.md)). Todas viven
en `data/repository/` salvo `ScannerRepository`, cuya implementación
(`MusicScannerManager`) está en `data/media/` y se documenta en
[DATA.md](./DATA.md#escáner-de-biblioteca-datamedia).

Cada repositorio es responsable de traducir entre el modelo de dominio y su
fuente de datos concreta (Room, DataStore), y de decidir en qué
`CoroutineDispatcher` se ejecuta cada operación mediante el
`DispatcherProvider` inyectado (ver [UTIL.md](./UTIL.md)) — ninguno llama a
`Dispatchers.IO` directamente.

## `LibraryRepositoryImpl`

Implementa `LibraryRepository` delegando en `SongDao`, `AlbumDao` y
`ArtistDao`.

- `getSongsFlow(query)` construye un `Pager` de Paging 3 (`pageSize = 30`,
  `prefetchDistance = 40`, sin placeholders) sobre
  `songDao.getAllSongsPaging()` o `searchSongsPaging()` según haya query o
  no — la paginación real ocurre en SQL, no en memoria.
- `getAlbumsFlow`/`getArtistsFlow` devuelven listas completas (no paginadas:
  el número de álbumes/artistas es mucho menor que el de canciones).
- `updateSongArtwork`/`updateAlbumCover` escriben directamente las columnas
  `customArtworkUri`/`customCoverUri`.

## `PlaylistRepositoryImpl`

Implementa `PlaylistRepository` sobre `PlaylistDao`. Las operaciones que
combinan varias sentencias SQL se ejecutan dentro de
`database.withTransaction { }` para evitar estados intermedios inconsistentes
bajo acceso concurrente:

- **`toggleFavorite(song)`**: dentro de una transacción, obtiene o crea la
  playlist "Favorites" (`getOrCreateFavorites`, atómica a nivel de DAO),
  comprueba si la canción ya está en ella, y la añade o la quita.
- **`addSongToPlaylist`** usa `insertPlaylistSongAtEnd`, que calcula la
  siguiente posición (`MAX(position)+1`) dentro de la propia sentencia SQL
  para no incurrir en una carrera lectura-luego-escritura.

También expone la función de extensión de nivel de archivo
`isFavoritesPlaylist(name)`, usada por la UI para distinguir la playlist
especial "Favorites" de las creadas por el usuario.

## `LyricsRepositoryImpl`

Implementa `LyricsRepository` orquestando `EmbeddedLyricsExtractor` y
`LrcParser` (ambos en `data/lyrics/`, ver [DATA.md](./DATA.md#letras-datalyrics)).

`getLyrics(song)` resuelve **ambas** fuentes posibles en paralelo lógico
(embebida y LRC) y construye un `SongLyrics` con la fuente seleccionada por
defecto:

1. **Letra LRC**, en orden de prioridad:
   - `customLyricsUri` del usuario, si existe.
   - Un archivo `.lrc` "sidecar" junto al archivo de audio: mismo nombre con
     extensión `.lrc`, o un archivo `<título de la canción>.lrc` en el mismo
     directorio. La resolución de ruta usa `MediaStore.Audio.Media.DATA`
     (deprecada desde API 29, pero sigue siendo el mecanismo práctico para
     localizar archivos hermanos en almacenamiento local).
2. **Letra embebida**, vía `EmbeddedLyricsExtractor.extract`.

## `EqualizerRepositoryImpl`

Implementa `EqualizerRepository` como fuente de verdad reactiva sobre
`EqualizerPreferences` (DataStore), siguiendo un patrón **CQRS**:

- Un único `MutableStateFlow<EqualizerConfig>` interno, inicializado desde
  DataStore en `init` y mantenido sincronizado suscribiéndose a
  `preferences.equalizerConfigFlow`.
- Todas las mutaciones (`setEnabled`, `setMode`, `setPreamp`, `setBand`,
  `applyPreset`, `reset`, etc.) se canalizan a través de un único método
  privado `updateConfig(transform)`, que aplica la transformación al valor
  actual, actualiza el `StateFlow` de forma síncrona (para que la UI
  reaccione al instante) y persiste el resultado en DataStore de forma
  asíncrona.
- `setMode` regenera el conjunto de bandas por defecto correspondiente al
  nuevo modo (`EqualizerConfig.defaultBands()` o
  `defaultAdvancedBands()`) cuando el modo cambia efectivamente.
- `applyPreset` reescribe la ganancia de cada banda existente con los
  valores del preset, indexando por posición contra
  `EqualizerPresets.getGains(presetName)`.

Esta implementación **no** se comunica directamente con el motor de audio:
esa responsabilidad es de `EqualizerController` /
`EqualizerControllerImpl` en el módulo `player`, que observa este mismo
`StateFlow` y reenvía los cambios al servicio por IPC (ver
[PLAYER.md](./PLAYER.md)).

## Convenciones comunes

- **Todas** las implementaciones reciben `DispatcherProvider` por
  constructor (nunca acceden a `Dispatchers` global), lo que las hace
  testeables sustituyendo el dispatcher por uno de test.
- Las operaciones de escritura son `suspend fun`; las de lectura reactiva
  devuelven `Flow`/`StateFlow` para que la UI se recomponga automáticamente
  ante cambios, sin necesidad de refrescos manuales.
- Ninguna implementación expone tipos de Room (`Cursor`, `PagingSource`) por
  fuera de sí misma: la interfaz de dominio solo ve `Flow`, `PagingData` y
  las entidades (`Song`, `Album`, `Artist`).
