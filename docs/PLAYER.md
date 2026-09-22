# Módulo Player

**Paquete:** `com.example.prism.player`

El módulo Player contiene todo lo relacionado con la reproducción real del
audio: el servicio en segundo plano (Media3), el puente entre la UI y ese
servicio, y el motor DSP propio del ecualizador. Es, junto con `data`, una de
las dos capas de infraestructura, y la única que corre parcialmente **fuera
del proceso de UI** (un `MediaSessionService` puede sobrevivir a que la
Activity se destruya).

## Contenido

```
player/
├── PlaybackService.kt          # MediaSessionService — el servicio real de reproducción
├── PlaybackManager.kt          # Interfaz + impl. consumida por la UI (habla con el servicio vía MediaController)
├── ProgressTracker.kt          # Polling de progreso de reproducción, consciente de suscriptores
├── ProgressState.kt            # Modelo (posición, duración)
├── MediaItemBuilder.kt         # Song -> androidx.media3.common.MediaItem
├── CustomCommandDispatcher.kt  # Interfaz mínima para enviar comandos IPC al servicio
├── PrismRenderersFactory.kt    # Inyecta el AudioProcessor del ecualizador en el pipeline de ExoPlayer
└── effects/                    # Motor DSP del ecualizador
    ├── BiquadCoefficients.kt
    ├── BiquadFilter.kt
    ├── EqualizerAudioProcessor.kt
    ├── EqualizerCommands.kt
    ├── EqualizerController.kt
    └── EqualizerControllerImpl.kt
```

## Arquitectura general del módulo

```
UI (ViewModels)
   │  PlaybackManager (interfaz)
   ▼
PlaybackManagerImpl  ── MediaController ──IPC──▶  PlaybackService (MediaSessionService)
   │                                                   │
   │ ProgressTracker (polling 250ms, solo si hay        ├─ ExoPlayer
   │ suscriptores activos y está reproduciendo)          ├─ PrismRenderersFactory
   ▼                                                    │    └─ EqualizerAudioProcessor (AudioProcessor)
StateFlow<Song?>, StateFlow<Boolean>, StateFlow<ProgressState>       │
                                                          └─ MediaSession.Callback (comandos custom de EQ)
```

La UI **nunca** llama directamente a `ExoPlayer` ni al `PlaybackService`.
Toda la comunicación pasa por `MediaController`/`MediaSession`, la API de
Media3 diseñada para este tipo de arquitectura cliente-servicio, lo que
permite que la reproducción continúe aunque la Activity se destruya (p. ej.
con la pantalla apagada) y que el sistema gestione la notificación y los
controles de la pantalla de bloqueo automáticamente.

## `PlaybackService`

`MediaSessionService` (API estable de Media3) responsable de:

- Construir el `ExoPlayer` con `AudioAttributes` de tipo música,
  `setHandleAudioBecomingNoisy(true)` (pausa automática al desconectar
  auriculares) y `WAKE_MODE_LOCAL` (mantiene la CPU activa durante la
  reproducción con pantalla apagada).
- Instalar `PrismRenderersFactory` para inyectar el `EqualizerAudioProcessor`
  en el pipeline de audio de ExoPlayer.
- Configurar un `DefaultLoadControl` con buffers ajustados (15–30 s de
  buffer, 1.5 s antes de empezar a reproducir, 5 s tras un rebuffering).
- Crear la `MediaSession` con un `MediaSession.Callback` que:
  - Añade comandos de sesión personalizados (`SessionCommand`) para el
    ecualizador — ver [`EqualizerCommands`](#equalizercommandskt) — durante
    `onConnect`.
  - Procesa esos comandos en `onCustomCommand`, aplicándolos directamente
    sobre el `EqualizerAudioProcessor` en memoria (sin pasar por Room ni
    DataStore: la persistencia ya ocurrió en el lado de la UI, vía
    `EqualizerRepository`).
  - Resuelve `onAddMediaItems` reconstruyendo la URI de reproducción si un
    `MediaItem` llega sin `localConfiguration` (caso de reanudación desde
    controles externos, p. ej. Android Auto).
- Alternar el offload de audio (`AudioOffloadPreferences`) según el
  ecualizador esté activo o no: el offload de hardware es incompatible con
  el procesamiento DSP en software, así que se desactiva mientras el EQ está
  encendido.
- Liberar `ExoPlayer` y `MediaSession` en `onDestroy`, y detenerse a sí mismo
  en `onTaskRemoved` si no hay reproducción en curso.

`PlaybackService` es `KoinComponent` (no una clase gestionada por Koin
directamente, porque el sistema Android la instancia) y obtiene
`EqualizerAudioProcessor` y `EqualizerRepository` vía `by inject()`.

## `PlaybackManager` / `PlaybackManagerImpl`

Interfaz consumida por `PlaybackViewModel` (ver [UI.md](./UI.md)):

```kotlin
interface PlaybackManager {
    val currentSong: StateFlow<Song?>
    val isPlayingState: StateFlow<Boolean>
    val progressState: StateFlow<ProgressState>
    fun playSong(song: Song, playlistSongs: List<Song> = emptyList())
    fun togglePlayPause()
    fun next()
    fun previous()
    fun seekTo(positionMs: Long)
    fun release()
}
```

`PlaybackManagerImpl` mantiene un `MediaController` (construido de forma
asíncrona con `MediaController.Builder(...).buildAsync()`) y traduce sus
eventos (`Player.Listener`) a los `StateFlow` de arriba.

Puntos destacables de la implementación:

- **Conexión diferida**: si se llama a `playSong` antes de que el
  `MediaController` esté listo, la petición se guarda en un
  `AtomicReference<Pair<Song, List<Song>>?>` (`pendingPlay`) y se ejecuta en
  cuanto la conexión se completa.
- **Ventaneo de la cola (`MAX_QUEUE_SIZE = 1000`)**: al llamar a `playSong`
  con una librería muy grande, en lugar de enviar todos los `MediaItem` al
  servicio (que viaja por Binder IPC, con un límite práctico de ~1 MB por
  transacción — `TransactionTooLargeException`), se recorta una ventana de
  como máximo 1000 canciones centrada en la canción seleccionada.
- **Resolución de metadatos ausentes**: si el `MediaController` reporta una
  transición a una canción que no está en `activePlaylist` en memoria (poco
  habitual, pero posible tras reconexión), se resuelve consultando
  `LibraryRepository.getSongById` de forma asíncrona.
- `sendCustomCommand` implementa `CustomCommandDispatcher`, usado
  exclusivamente por `EqualizerControllerImpl` para reenviar cambios de
  configuración del ecualizador al servicio.

## `ProgressTracker`

Aísla el *polling* de posición de reproducción (SRP), con una optimización
de batería relevante: el polling (cada 250 ms) solo está activo cuando **a
la vez** (a) hay al menos un suscriptor activo al `StateFlow` de progreso
(`_progressState.subscriptionCount`) y (b) la reproducción está en curso.
Si la pantalla de reproducción no está visible, no hay nadie observando el
`Flow` y el polling se detiene automáticamente.

También suprime el "jitter" visual tras un `seek`: durante el segundo
siguiente a un `seekTo`, ignora las actualizaciones de posición provenientes
del polling para no mostrar brevemente la posición antigua antes de que el
controller confirme el salto.

## `MediaItemBuilder.kt`

Extensión `Song.toMediaItem()` que construye un `androidx.media3.common.MediaItem`
con metadatos completos (título, artista, álbum, número de pista, artwork)
para que el sistema los muestre en notificación y pantalla de bloqueo. La
resolución de la URI de artwork sigue el mismo orden de prioridad que
`AlbumArtFetcher` (custom → artwork ya resuelto → MediaStore por `albumId` →
URI de la propia canción → `null`).

## `PrismRenderersFactory`

`DefaultRenderersFactory` de ExoPlayer con `buildAudioSink` sobreescrito para
insertar `equalizerAudioProcessor` en la cadena de `AudioProcessor` del
`DefaultAudioSink`. Es el punto exacto donde el motor DSP del ecualizador se
conecta al pipeline de audio de ExoPlayer.

---

## Motor DSP del ecualizador (`player/effects/`)

> Para el diseño completo (diagrama de capas, matemática de los filtros y
> flujo de comunicación), ver también
> [`docs/EQUALIZER_ARCHITECTURE.md`](../EQUALIZER_ARCHITECTURE.md).

### `BiquadCoefficients`

Calcula los coeficientes normalizados (`a0 = 1`) de un filtro biquad de
segundo orden (IIR) según las fórmulas del *Audio EQ Cookbook* de Robert
Bristow-Johnson, para cada `EqFilterType` (`BELL`, `LOW_SHELF`, `HIGH_SHELF`,
`LOW_PASS`, `HIGH_PASS`, `NOTCH`). Incluye protecciones numéricas: recorta la
frecuencia por debajo de Nyquist, recorta `Q`, y devuelve `BYPASS`
(coeficientes identidad) cuando la ganancia es esencialmente 0 dB (evita
procesamiento innecesario en bandas neutras).

También expone `magnitudeDb(freqHz, sampleRate)`, que evalúa
matemáticamente la respuesta en frecuencia `|H(f)|` del filtro sin necesidad
de un analizador FFT — es lo que dibuja la curva del ecualizador paramétrico
en la UI (`ParametricEqCanvas`).

### `BiquadFilter`

Filtro biquad en **Direct Form II Transposed**, con registros de estado
(`d1`, `d2`) independientes por canal. `processSample(sample, channel)` no
hace ninguna asignación de memoria — diseño imprescindible porque se llama
una vez por muestra de audio en tiempo real.

### `EqualizerAudioProcessor`

`androidx.media3.common.audio.AudioProcessor` — el punto donde el audio
crudo se transforma. Características clave:

- Mantiene hasta `MAX_BANDS = 16` `BiquadFilter` en cascada (uno por banda
  configurada) y soporta hasta `MAX_CHANNELS = 8`.
- La configuración (`EqualizerConfig`) se guarda en un `AtomicReference`
  para lectura/escritura *lock-free* y *thread-safe* entre el hilo de UI
  (que la actualiza) y el hilo de audio (que la lee).
- Los coeficientes se **recalculan solo cuando cambia la configuración**
  (`recalculateCoefficients`), nunca por muestra — el trabajo por muestra en
  `queueInput` es exclusivamente aplicar los coeficientes ya calculados.
- Si el ecualizador está desactivado, `queueInput` hace una copia directa de
  bytes sin ningún procesamiento (bypass de coste cero).
- Soporta PCM de 16 bits y PCM float.
- Aplica preamplificación, la cascada de filtros biquad, y opcionalmente un
  **limitador *soft-knee*** (`softClip`) para evitar *clipping* digital
  duro: es transparente por debajo de ~-3.5 dBFS y comprime suavemente por
  encima mediante una función polinómica (sin funciones trascendentales,
  más barata en CPU de audio en tiempo real).

### `EqualizerCommands.kt`

Constantes de nombres de comando y claves de `Bundle` usadas en la IPC de
`SessionCommand` entre la UI (a través de `EqualizerControllerImpl`) y
`PlaybackService.onCustomCommand`: configurar todo el `EqualizerConfig`,
activar/desactivar, ajustar preamp, actualizar una banda, aplicar un preset,
o resetear.

### `EqualizerController` / `EqualizerControllerImpl`

Interfaz de cara a la UI (`ui/equalizer/EqualizerViewModel`), separada
deliberadamente de `PlaybackManager` (ISP): la gestión del ecualizador no
tiene nada que ver con transporte de reproducción (play/pause/seek).

`EqualizerControllerImpl`:
- Delega toda mutación de estado (`setEnabled`, `setMode`, `setBand`, etc.)
  en `EqualizerRepository` (fuente de verdad persistida — ver
  [REPOSITORY.md](./REPOSITORY.md)).
- Se suscribe (`collectLatest`) al `StateFlow` de `EqualizerRepository` y,
  ante cada cambio, serializa la configuración a JSON y la reenvía al
  servicio con `CustomCommandDispatcher.sendCustomCommand` — así el
  `EqualizerAudioProcessor` que vive dentro de `PlaybackService` se mantiene
  sincronizado con lo que el usuario configura en la UI, sin acoplar
  directamente `ui` con `player`.
