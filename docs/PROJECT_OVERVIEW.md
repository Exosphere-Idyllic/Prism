# Prism — Descripción del proyecto

## Qué es

Prism es un reproductor de música local para Android, escrito 100% en Kotlin con
Jetpack Compose. Escanea la librería musical del dispositivo a través de
`MediaStore`, la indexa en una base de datos Room, y ofrece reproducción en segundo
plano con Media3/ExoPlayer, letras sincronizadas, portadas personalizables,
playlists (incluyendo favoritos) y un ecualizador paramétrico propio con
procesamiento DSP en tiempo real.

No depende de streaming ni de servicios externos: todo el contenido reproducido
proviene de archivos de audio ya presentes en el dispositivo del usuario.

## Por qué existe

El proyecto nace como ejercicio de ingeniería Android "hecho bien" — aplicando
Clean Architecture, principios SOLID (especialmente SRP e ISP) y patrones CQRS
donde aporta valor — y como banco de pruebas para resolver problemas reales de
rendimiento en Compose (recomposición, scroll en listas grandes, carga de
miniaturas) y de audio en tiempo real (filtros IIR sin asignaciones de memoria
por muestra).

## Funcionalidades principales

- **Biblioteca musical**: listados paginados de canciones, álbumes y artistas,
  con búsqueda con debounce y escaneo incremental de `MediaStore` (observa
  cambios del proveedor de contenido y reindexa solo lo que cambió).
- **Reproducción en segundo plano**: servicio `MediaSessionService` con
  controles en la pantalla de bloqueo, notificación multimedia y gestión
  automática de foco de audio.
- **Ecualizador paramétrico**: 10 bandas ISO en modo simple o 8 bandas
  totalmente paramétricas (frecuencia, ganancia, Q, tipo de filtro) en modo
  avanzado, con una curva de respuesta `|H(f)|` dibujada en Canvas y un motor
  DSP propio de filtros biquad (fórmulas de Robert Bristow-Johnson).
- **Letras**: extracción de letras embebidas en el propio archivo de audio
  (ID3 USLT/SYLT) y carga de archivos `.lrc` externos, con sincronización por
  timestamp.
- **Portadas personalizadas**: el usuario puede sustituir la carátula de una
  canción o álbum por una imagen propia (local o remota vía HTTP).
- **Playlists y favoritos**: creación de playlists, orden estable de
  canciones y una playlist "Favorites" gestionada de forma atómica.

## Stack tecnológico

| Categoría | Tecnología |
|---|---|
| Lenguaje | Kotlin 2.4, JVM 17 |
| UI | Jetpack Compose (Material 3), Navigation 3 |
| Inyección de dependencias | Koin 4 |
| Base de datos | Room 2.8 + Paging 3 |
| Preferencias | Jetpack DataStore |
| Reproducción de audio | Media3 / ExoPlayer 1.9 |
| Carga de imágenes | Coil 3 |
| Concurrencia | Kotlin Coroutines / Flow |
| Serialización | kotlinx.serialization (JSON) |
| Logging | Timber |
| Tests | JUnit4, MockK, Turbine, Robolectric |
| Build | Gradle (Kotlin DSL), KSP |

`compileSdk`/`targetSdk` 37, `minSdk` 24.

## Arquitectura en una frase

Clean Architecture por capas (`ui` → `domain` → `data`/`player`), con
inyección de dependencias vía Koin, estado unidireccional con `StateFlow`, y
un módulo `player` que corre en un servicio independiente del ciclo de vida de
la UI, comunicado por `MediaSession`/`MediaController` (IPC) en lugar de
comunicación directa en proceso.

Ver [`README.md`](../README.md) para la vista general de módulos y
[`docs/modules/`](./modules) para el detalle de cada uno. La arquitectura del
ecualizador en particular está documentada en detalle en
[`EQUALIZER_ARCHITECTURE.md`](./EQUALIZER_ARCHITECTURE.md).

## Organización del repositorio

```
app/src/main/java/com/example/prism/
├── core/         # Utilidades transversales e inyección de dependencias (Koin)
├── data/         # Room, DataStore, escáner de MediaStore, artwork, letras, repositorios (impl.)
├── domain/       # Modelos e interfaces de repositorio (sin dependencias de Android)
├── player/       # Servicio de reproducción (Media3), control de sesión, DSP del ecualizador
├── navigation/   # Grafo de navegación (Navigation 3)
└── ui/           # Pantallas y componentes Compose, ViewModels
```
