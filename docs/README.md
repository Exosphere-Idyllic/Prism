# Prism 🎧

Reproductor de música local para Android, escrito en Kotlin + Jetpack Compose,
con reproducción en segundo plano vía Media3/ExoPlayer y un ecualizador
paramétrico con motor DSP propio.

Para una descripción funcional completa del proyecto, ver
[`docs/PROJECT_OVERVIEW.md`](docs/PROJECT_OVERVIEW.md).

## Características

- 📚 Biblioteca musical con canciones, álbumes, artistas y playlists,
  indexada desde `MediaStore` con escaneo incremental en segundo plano.
- ▶️ Reproducción persistente en un `MediaSessionService`, con controles en
  notificación y pantalla de bloqueo.
- 🎚️ Ecualizador paramétrico de 8/10 bandas con curva de respuesta en tiempo
  real y limitador anti-clipping.
- 📝 Letras sincronizadas (embebidas o `.lrc`).
- 🖼️ Portadas personalizables por canción o álbum.
- ⭐ Favoritos y playlists.

## Empezando

### Requisitos

- Android Studio (Ladybug o superior recomendado)
- JDK 17
- SDK de Android con `compileSdk`/`targetSdk` 37 instalado
- Un dispositivo o emulador con `minSdk` 24 (Android 7.0) o superior

### Compilar y ejecutar

```bash
git clone https://github.com/Exosphere-Idyllic/Prism.git
cd Prism
./gradlew assembleDebug
```

Para instalar directamente en un dispositivo/emulador conectado:

```bash
./gradlew installDebug
```

Al abrir la app por primera vez, Prism solicitará el permiso de acceso a
audio (`READ_MEDIA_AUDIO` en Android 13+, `READ_EXTERNAL_STORAGE` en
versiones anteriores) y realizará un escaneo completo de la librería musical
del dispositivo.

### Ejecutar tests

```bash
./gradlew testDebugUnitTest
```

## Arquitectura y módulos

El proyecto sigue Clean Architecture por capas, con inyección de dependencias
vía Koin. Cada capa vive en su propio paquete bajo
`app/src/main/java/com/example/prism/`:

| Módulo | Paquete | Descripción | Documentación |
|---|---|---|---|
| Core | `core/` | DI (Koin) y utilidades transversales | [docs/modules/CORE.md](docs/modules/CORE.md) |
| Util | `core/util/` | Dispatchers de corrutinas, formateo | [docs/modules/UTIL.md](docs/modules/UTIL.md) |
| Data | `data/` | Room, DataStore, escáner de MediaStore, artwork, letras | [docs/modules/DATA.md](docs/modules/DATA.md) |
| Domain | `domain/` | Modelos e interfaces de repositorio | [docs/modules/DOMAIN.md](docs/modules/DOMAIN.md) |
| Repository | `data/repository/` | Implementaciones concretas de los repositorios de dominio | [docs/modules/REPOSITORY.md](docs/modules/REPOSITORY.md) |
| Player | `player/` | Servicio de reproducción Media3 y motor DSP del ecualizador | [docs/modules/PLAYER.md](docs/modules/PLAYER.md) |
| UI | `ui/`, `navigation/` | Pantallas Compose, ViewModels y navegación | [docs/modules/UI.md](docs/modules/UI.md) |

Documentación adicional:

- [`docs/EQUALIZER_ARCHITECTURE.md`](docs/EQUALIZER_ARCHITECTURE.md) — diseño
  detallado del motor DSP del ecualizador (filtros biquad, cascada,
  limitador).

### Flujo de dependencias

```
ui  ─depende de─▶  domain  ◀─depende de─  data
 │                                          │
 └────────── player (servicio) ─────────────┘
```

`domain` no depende de Android ni de ninguna otra capa: define modelos e
interfaces puras. `data` y `player` implementan esas interfaces o las
consumen. `ui` consume `domain` (vía interfaces) y se comunica con `player`
únicamente a través de `PlaybackManager`, que internamente habla con el
servicio por `MediaController` (IPC), nunca en llamada directa.

## Configuración

Prism no requiere claves de API ni configuración adicional: toda la música
proviene del propio dispositivo. La única configuración persistente es local
(Room + DataStore) y se crea automáticamente en el primer arranque.

## Contribuir

1. Crea una rama a partir de `main`.
2. Sigue las convenciones ya presentes en el código: Clean Architecture,
   `DispatcherProvider` inyectado en lugar de `Dispatchers.IO` directo,
   interfaces segregadas por responsabilidad (ISP) en `domain/repository`.
3. Añade tests unitarios para lógica nueva en `data`, `domain` o `player`
   (usa `Turbine` para `Flow`/`StateFlow` y `MockK` para dobles de prueba).
4. Verifica que `./gradlew testDebugUnitTest` pase antes de abrir un PR.
