# Módulo Core

**Paquete:** `com.example.prism.core`

El módulo Core agrupa las piezas transversales que el resto de la app
necesita pero que no pertenecen a ninguna capa concreta: la configuración de
inyección de dependencias y utilidades genéricas (documentadas en
[UTIL.md](./UTIL.md)).

## Contenido

```
core/
├── di/
│   └── AppModule.kt       # Único módulo Koin de la aplicación
└── util/
    ├── DispatcherProvider.kt
    └── Formatting.kt
```

## Inyección de dependencias — `AppModule.kt`

Prism usa [Koin](https://insert-koin.io/) como framework de inyección de
dependencias. Todo el grafo de objetos de la app se declara en un único
`Module`, `appModule`, cargado en `PrismApplication.onCreate()` mediante
`startKoin { modules(appModule) }`.

### Qué declara

`appModule` registra, en este orden lógico:

1. **Infraestructura de concurrencia**
   - `DispatcherProvider` → `DefaultDispatcherProvider` (singleton).
   - Un `CoroutineScope` de aplicación (`SupervisorJob() + dispatcher.default`),
     compartido por todos los componentes que necesitan sobrevivir a la UI
     (escáner de música, `PlaybackManager`, repositorios reactivos).

2. **Persistencia**
   - `AppDatabase` (Room), construida vía `AppDatabase.build(context)`.
   - `EqualizerPreferences` (DataStore) para la configuración del ecualizador.

3. **Repositorios de dominio** (interfaz → implementación):
   - `LibraryRepository` → `LibraryRepositoryImpl`
   - `PlaylistRepository` → `PlaylistRepositoryImpl`
   - `LyricsRepository` → `LyricsRepositoryImpl`
   - `EqualizerRepository` → `EqualizerRepositoryImpl`
   - `ScannerRepository` → `MusicScannerManager`

4. **Reproducción**
   - `PlaybackManagerImpl` como singleton concreto, expuesto bajo tres tipos
     distintos (`PlaybackManager`, `CustomCommandDispatcher` y la clase
     concreta) porque distintos consumidores necesitan distintas porciones de
     su API — ver [Principio de Segregación de Interfaces](#por-qué-tres-bindings-para-playbackmanagerimpl)
     más abajo.
   - `EqualizerAudioProcessor` (singleton, compartido entre el `ViewModel` del
     ecualizador — a través del `EqualizerController` — y el `PlaybackService`).
   - `EqualizerController` → `EqualizerControllerImpl`.

5. **ViewModels** (usando `viewModel { }` de `koin-androidx-compose`):
   - `LibraryViewModel`, `PlaybackViewModel`, `LyricsViewModel`,
     `EqualizerViewModel`.

### Por qué tres bindings para `PlaybackManagerImpl`

```kotlin
single { PlaybackManagerImpl(...) }
single<PlaybackManager> { get<PlaybackManagerImpl>() }
single<CustomCommandDispatcher> { get<PlaybackManagerImpl>() }
```

`PlaybackManagerImpl` implementa dos interfaces con responsabilidades
distintas: `PlaybackManager` (controles de reproducción para la UI) y
`CustomCommandDispatcher` (envío de comandos IPC de ecualización hacia el
servicio, usado por `EqualizerControllerImpl`). Declarar un único `single`
concreto y exponerlo bajo dos interfaces evita crear dos instancias
independientes mientras se mantiene el Principio de Segregación de
Interfaces (ISP): cada consumidor solo ve el subconjunto de métodos que le
corresponde.

### Cómo se usa

Cualquier clase que Koin instancie (`ViewModel`, `Activity`, u otro objeto
del propio grafo) puede pedir sus dependencias por constructor y Koin las
resuelve automáticamente. Fuera del grafo de Koin — por ejemplo en
`PlaybackService`, que es instanciado por el sistema Android, no por Koin —
se usa `KoinComponent` + `by inject()` para acceder a dependencias
puntuales (ver [PLAYER.md](./PLAYER.md)).

### Extender el módulo

Al añadir un nuevo repositorio o caso de uso:

1. Definir la interfaz en `domain/repository/`.
2. Implementarla en `data/repository/` (o el paquete que corresponda).
3. Registrar el binding en `AppModule.kt` siguiendo el patrón
   `single<Interfaz> { Implementacion(dependencias = get()) }`.
4. Si un `ViewModel` la necesita, añadirla a su constructor: Koin la resuelve
   sola gracias al binding anterior.
