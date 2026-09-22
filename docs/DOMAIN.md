# Módulo Domain

**Paquete:** `com.example.prism.domain`

La capa de dominio contiene los modelos de negocio y los contratos
(`interface`) que el resto de capas deben respetar. Es la capa más interna de
la arquitectura: **no depende de Android, Room, Media3 ni de ningún detalle
de infraestructura** (más allá de `androidx.compose.runtime.Immutable`, usada
únicamente como anotación de optimización de recomposición). Esto permite que
`domain` se pueda testear en JVM puro y que `data`/`player`/`ui` dependan de
él sin acoplarse entre sí.

## Contenido

```
domain/
├── model/
│   ├── Lyrics.kt              # LyricsLine, LyricsSource, LyricsContent, SongLyrics
│   ├── PlaylistWithCount.kt
│   └── equalizer/
│       ├── EqBand.kt
│       ├── EqFilterType.kt
│       ├── EqualizerConfig.kt
│       ├── EqualizerMode.kt
│       └── EqualizerPresets.kt
└── repository/
    ├── EqualizerRepository.kt
    ├── LibraryRepository.kt
    ├── LyricsRepository.kt
    ├── PlaylistRepository.kt
    └── ScannerRepository.kt
```

## Modelos (`domain/model/`)

### Letras — `Lyrics.kt`

- **`LyricsLine(timestampMs, text)`** — una línea de letra. `timestampMs =
  -1L` indica una línea sin sincronizar (`isSynced` calculado).
- **`LyricsSource`** — enum `EMBEDDED` (ID3) o `LRC_FILE` (archivo externo).
- **`LyricsContent(source, isSynced, lines)`** — el resultado parseado de
  *una* fuente concreta.
- **`SongLyrics(songId, embeddedLyrics, lrcLyrics, selectedSource)`** —
  agrega ambas fuentes posibles para una canción y resuelve cuál mostrar:
  - `activeLyrics`: si el usuario seleccionó una fuente explícitamente, la
    prioriza (con fallback a la otra si es null); si no hay selección,
    prioriza LRC sobre embebida.
  - `hasLyrics` / `hasBothSources`: flags de conveniencia para la UI (p. ej.
    mostrar un selector de fuente solo si hay ambas).

### `PlaylistWithCount`

Proyección de solo lectura usada por `PlaylistDao.getAllPlaylistsWithCounts`:
una playlist con su `songCount` precalculado por SQL (`LEFT JOIN` +
`GROUP BY`), para no lanzar una suscripción `Flow<Int>` por cada fila de la
lista de playlists.

### Ecualizador — `equalizer/`

Este subpaquete modela por completo el estado del ecualizador de forma
serializable (`@Serializable`, kotlinx.serialization), ya que
`EqualizerConfig` viaja tanto a DataStore (como JSON) como por IPC hacia el
`PlaybackService` (ver [PLAYER.md](./PLAYER.md)).

- **`EqFilterType`** — topologías de filtro biquad soportadas: `BELL`,
  `LOW_SHELF`, `HIGH_SHELF`, `LOW_PASS`, `HIGH_PASS`, `NOTCH`.
- **`EqBand(id, enabled, type, frequencyHz, gainDb, q)`** — una banda
  individual de la cascada de filtros.
- **`EqualizerMode`** — `SIMPLE` (10 bandas ISO fijas, con presets) o
  `ADVANCED` (8 bandas paramétricas libres, editables en Canvas).
- **`EqualizerConfig(enabled, mode, preampDb, selectedPreset, bands,
  limiterEnabled)`** — el estado completo e inmutable de la configuración.
  Expone en su `companion object`:
  - `SIMPLE_FREQUENCIES`: las 10 frecuencias ISO estándar (31 Hz–16 kHz).
  - `defaultBands()` / `defaultAdvancedBands()`: generadores de las bandas
    por defecto para cada modo (primera banda `LOW_SHELF`, última
    `HIGH_SHELF`, resto `BELL`).
- **`EqualizerPresets`** — catálogo estático de 8 perfiles predefinidos
  (`Flat`, `Rock`, `Pop`, `Bass Boost`, `Classical`, `Vocal`, `Electronic`,
  `Acoustic`), cada uno como una lista de 10 ganancias en dB alineadas con
  `SIMPLE_FREQUENCIES`.

Nota: aunque `EqBand`/`Song` conceptualmente pertenecen a capas distintas,
algunas entidades de `data/entity` (`Song`, `Album`, `Artist`) se referencian
directamente desde interfaces de `domain/repository` en lugar de mapearse a
modelos de dominio separados — una simplificación pragmática del proyecto:
las entidades Room ya son inmutables y se tratan como el modelo de lectura
compartido entre capas.

## Interfaces de repositorio (`domain/repository/`)

Cada interfaz sigue el **Principio de Segregación de Interfaces (ISP)**:
en lugar de un único repositorio "Dios", hay una interfaz por área
funcional, de forma que cada `ViewModel`/consumidor declara solo las
dependencias que realmente usa.

| Interfaz | Responsabilidad |
|---|---|
| `LibraryRepository` | Consulta de canciones/álbumes/artistas (paginado y no paginado), búsqueda, y actualización de artwork personalizado. |
| `PlaylistRepository` | CRUD de playlists, gestión de favoritos, asociación canción↔playlist. |
| `LyricsRepository` | Resolución de letras (embebidas + `.lrc`) y asignación de un `.lrc` personalizado. |
| `EqualizerRepository` | Persistencia y lectura reactiva (`StateFlow`) de `EqualizerConfig`, con mutaciones atómicas vía `updateConfig`. Sigue un patrón **CQRS**: un único `StateFlow` para lecturas, métodos `suspend` dedicados para cada mutación. |
| `ScannerRepository` | Control del escaneo de `MediaStore` (`startObserving`, `triggerScan`, `stopObserving`) y estado del escaneo (`isLoading`, `totalSongsCount`). |

Estas interfaces se implementan en `data/repository/` (ver
[REPOSITORY.md](./REPOSITORY.md)) o, en el caso de `ScannerRepository`, en
`data/media/MusicScannerManager`. Se inyectan vía Koin (ver
[CORE.md](./CORE.md)) y son la única vía por la que `ui` accede a datos —
nunca acceden directamente a Room, DataStore o `MediaStore`.
