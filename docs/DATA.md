# Módulo Data

**Paquete:** `com.example.prism.data`

La capa Data contiene todo lo que habla con fuentes de datos concretas:
`MediaStore`, Room, DataStore y el sistema de archivos. Implementa las
interfaces definidas en `domain/repository` (documentadas por separado en
[REPOSITORY.md](./REPOSITORY.md)) y expone además utilidades de soporte que
no forman parte del contrato de dominio: entidades de base de datos, el
escáner de biblioteca, la resolución de artwork y el parseo de letras.

## Contenido

```
data/
├── entity/         # Entidades Room (tablas)
├── db/             # AppDatabase + DAOs
├── media/          # Escáner de MediaStore
├── artwork/        # Carga de portadas (Coil Fetcher/Keyer)
├── lyrics/          # Parseo de letras (LRC, ID3 embebido)
├── preferences/    # DataStore (ecualizador, estado de escaneo)
└── repository/     # Implementaciones de los repositorios de dominio
```

## Entidades (`data/entity/`)

Todas están anotadas `@Immutable` (optimización de recomposición de Compose)
y son `data class` de Room.

| Entidad | Tabla | Notas |
|---|---|---|
| `Song` | `songs` | Clave primaria `id` (String, el `_ID` de MediaStore). Incluye `customArtworkUri` y `customLyricsUri` para overrides del usuario. Índices en `title`, `artist`, `album`, `albumId`, `dateModified`. |
| `Album` | `albums` | Tabla materializada, agregada desde `songs` (ver [escáner](#escáner-de-biblioteca-datamedia)). Incluye `customCoverUri`. |
| `Artist` | `artists` | Tabla materializada, agregada desde `songs`. Clave primaria = nombre del artista. |
| `Playlist` / `PlaylistSong` | `playlists` / `playlist_songs` | Relación muchos-a-muchos con clave compuesta `(playlistId, songId)` y `ForeignKey` en cascada. `PlaylistSong.position` mantiene el orden. |

`Album` y `Artist` **no** se escriben desde MediaStore directamente: se
derivan por agregación SQL de la tabla `songs` (`GROUP BY albumId` /
`GROUP BY artist`), preservando campos personalizados por el usuario como
`customCoverUri` mediante `ON CONFLICT DO UPDATE` que no toca esa columna.

## Base de datos (`data/db/`)

### `AppDatabase`

`RoomDatabase` con 5 entidades, versión `2`, con `AutoMigration(1, 2)` y
`exportSchema = true` (los esquemas se versionan en `app/schemas/`).
`AppDatabase.build(context)` construye la instancia con
`PRAGMA foreign_keys = ON` activado en `onOpen`, imprescindible para que las
`ForeignKey` con `onDelete = CASCADE` de `PlaylistSong` funcionen.

### DAOs

- **`SongDao`** — el más grande. Expone tanto `List`/`Flow` simples como
  `PagingSource` (`getAllSongsPaging`, `searchSongsPaging`) para listas
  grandes. Incluye `upsertPreservingUserFields`, una transacción Room que
  inserta canciones nuevas e ignora conflictos (`OnConflictStrategy.IGNORE`),
  y para las que ya existen actualiza solo columnas provenientes del escáner
  sin pisar `customArtworkUri`/`customLyricsUri`. También expone consultas de
  agregación (`getAggregatedAlbums`, `getAggregatedArtists` y sus variantes
  incrementales `...ForIds`/`...ForNames`) usadas por el escáner.
- **`AlbumDao`** / **`ArtistDao`** — CRUD de las tablas materializadas, con
  `syncAllFromSongs()` (resincronización completa vía SQL `INSERT ... SELECT
  ... ON CONFLICT DO UPDATE`) y `syncFromSongsForIds`/`syncFromSongsForNames`
  para resincronización incremental acotada a un lote de IDs/nombres.
  `deleteOrphans()` elimina álbumes/artistas que ya no tienen canciones.
- **`PlaylistDao`** — incluye dos primitivas atómicas notables:
  - `insertPlaylistSongAtEnd`: inserta una canción al final de la playlist
    calculando `MAX(position) + 1` **dentro** de la misma sentencia SQL, para
    no incurrir en una condición de carrera lectura-luego-escritura que
    produciría posiciones duplicadas bajo acceso concurrente.
  - `getOrCreateFavorites`: `@Transaction` que hace `INSERT OR IGNORE` +
    `SELECT` para garantizar que la playlist "Favorites" existe exactamente
    una vez, sin duplicados por nombre bajo carreras.

## Escáner de biblioteca (`data/media/`)

### `MediaStoreScanner` / `MediaStoreScannerImpl`

Escanea `MediaStore.Audio.Media` y sincroniza el resultado con Room.

- Se suscribe a cambios del proveedor de contenido con un `ContentObserver`
  registrado sobre `MediaStore.Audio.Media.EXTERNAL_CONTENT_URI`.
- Los eventos del observer se **debounce** 1 segundo (`contentObserverEvents
  .debounce(1000ms)`) antes de disparar un escaneo, para agrupar ráfagas de
  cambios (p. ej. una sincronización de muchos archivos a la vez).
- Las solicitudes de escaneo pasan por un `Channel(CONFLATED)`: si llegan
  varios triggers mientras un escaneo está en curso, se colapsan en uno solo
  que se ejecuta al terminar el actual — sin colas de escaneos redundantes.
- **Escaneo completo vs. incremental**: si la tabla `songs` está vacía se
  hace un escaneo completo. En caso contrario, se compara el conjunto de IDs
  de MediaStore contra los IDs ya presentes en Room para detectar altas y
  bajas, y se usa `dateModified > lastScanTimestamp` para detectar
  modificaciones — evitando releer toda la librería en cada escaneo.
- Todas las escrituras (upsert, delete, resincronización de álbumes/artistas)
  ocurren dentro de una única `database.withTransaction { }` envuelta en
  `NonCancellable`, para que un escaneo no quede a medias si la corrutina se
  cancela.
- Los lotes se trocean de a 200 elementos (`chunked(200)`) para respetar el
  límite de variables de SQLite en las cláusulas `IN (...)`.

### `IncrementalMetadataUpdater`

Aísla la resincronización de las tablas materializadas `albums`/`artists`
tras un escaneo. En escaneo completo, borra huérfanos y resincroniza todo. En
escaneo incremental, calcula el conjunto de `albumId`/`artist` afectados por
las canciones añadidas, modificadas o eliminadas, y resincroniza solo esos
——evitando un `GROUP BY` de tabla completa en librerías grandes.

### `MusicScannerManager`

Implementa `ScannerRepository` (la interfaz de dominio) delegando en
`MediaStoreScannerImpl`, y añade un efecto adicional: al completar un
escaneo, limpia la caché negativa de artwork
(`AlbumArtFetcher.clearNegativeCache()`) para que las portadas que antes no
se encontraron se reintenten.

## Artwork (`data/artwork/`)

### `AlbumArtFetcher`

`Fetcher` de Coil 3 con una cadena de estrategias en cascada, distinta para
canciones y para álbumes:

**Para canciones:**
1. URI de portada personalizada del usuario (`customArtworkUri`) — soporta
   `content://`, `file://`, `http(s)://` (esta última vía OkHttp).
2. `ContentResolver.loadThumbnail` (API 29+) por la URI de la canción.
3. `ContentResolver.loadThumbnail` (API 29+) por `albumId`.
4. Carátula embebida en el propio archivo (`MediaMetadataRetriever`).
5. URI genérica de álbum de MediaStore.
6. `null` → la UI muestra un drawable por defecto.

**Para álbumes:** el mismo orden sin el paso 4 (no aplica a un álbum) ni el 2
(no hay una canción concreta).

Incluye una **caché negativa en memoria** (`LinkedHashMap` LRU, tope 2000
entradas) que recuerda qué combinaciones ya fallaron para no repetir el
trabajo de resolución (incluyendo posibles llamadas JNI vía
`MediaMetadataRetriever`) en cada scroll.

### `ArtworkKeyers.kt`

`Keyer<T>` de Coil para `SongArtworkParams` y `AlbumArtworkParams`. Sin un
`Keyer`, Coil no puede mapear estos tipos de datos personalizados a una clave
de caché en disco, por lo que **cada** solicitud de artwork golpearía
`AlbumArtFetcher` desde cero en cada scroll. Las claves se construyen a
partir de IDs y `hashCode()` de las URIs para mantenerlas compactas.

### `ArtworkParams.kt`

`SongArtworkParams(song, size)` y `AlbumArtworkParams(albumId, coverUri,
customCoverUri, size)` — los tipos de datos de entrada para Coil que
`SongFactory`/`AlbumFactory` (dentro de `AlbumArtFetcher`) consumen.

## Letras (`data/lyrics/`)

### `LrcParser`

Parser de formato LRC sin dependencias externas. Soporta timestamps
`[mm:ss.xx]`/`[mm:ss.xxx]`/`[mm:ss]`, múltiples timestamps por línea (letra
repetida), la etiqueta `[offset:±ms]` aplicada globalmente, y texto plano sin
sincronizar como fallback si no se detecta ningún timestamp.

### `EmbeddedLyricsExtractor`

Extrae letras embebidas directamente de los bytes del archivo de audio, leyendo
manualmente el tag ID3v2 (sin librerías de terceros):
- **USLT** (letra sin sincronizar): se decodifica el texto y, si contiene
  timestamps en formato LRC, se reparsa con `LrcParser`.
- **SYLT** (letra sincronizada, `timeFormat` en milisegundos): se decodifican
  los pares texto+timestamp directamente a `LyricsLine`.

Limita la lectura del header a 512 KB (`MAX_TAG_HEADER_SEARCH_BYTES`) para no
cargar archivos completos en memoria solo para leer metadatos.

## Preferencias (`data/preferences/`)

### `EqualizerPreferences`

Persiste `EqualizerConfig` completo serializado como JSON en una única clave
de Preferences DataStore (`equalizer_config_json`). Expone
`equalizerConfigFlow: Flow<EqualizerConfig>` con manejo de errores de lectura
(`IOException` → `emptyPreferences()`) y de deserialización (JSON corrupto →
`EqualizerConfig()` por defecto), evitando que un fallo de lectura tumbe la
app.

### `ScanPreferences`

Persiste un único `Long` (`last_scan_timestamp`) usado por el escáner
incremental de `MediaStoreScanner` para saber desde qué instante buscar
canciones modificadas.

## Repositorios (`data/repository/`)

Las implementaciones concretas de las interfaces de `domain/repository` viven
aquí. Están documentadas en detalle en [REPOSITORY.md](./REPOSITORY.md).
