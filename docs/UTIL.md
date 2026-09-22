# Módulo Util

**Paquete:** `com.example.prism.core.util`

Utilidades pequeñas, sin estado y sin dependencias de Android más allá de lo
estrictamente necesario, usadas de forma transversal por el resto de la app.

## Contenido

```
core/util/
├── DispatcherProvider.kt
└── Formatting.kt
```

## `DispatcherProvider`

```kotlin
interface DispatcherProvider {
    val main: CoroutineDispatcher
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
}

class DefaultDispatcherProvider : DispatcherProvider {
    override val main = Dispatchers.Main
    override val io = Dispatchers.IO
    override val default = Dispatchers.Default
}
```

**Propósito:** ningún componente de Prism llama a `Dispatchers.IO` o
`Dispatchers.Main` directamente. En su lugar, reciben un `DispatcherProvider`
inyectado (por Koin, con `DefaultDispatcherProvider` como implementación de
producción). Esto permite sustituirlo por un `StandardTestDispatcher` en
tests unitarios sin tocar el código de producción, controlando el
determinismo de las corrutinas bajo test.

Se usa de forma consistente en repositorios (`LibraryRepositoryImpl`,
`PlaylistRepositoryImpl`, etc.), en `PlaybackManagerImpl`, en
`ProgressTracker` y en los `ViewModel` que lanzan trabajo en `viewModelScope`.

## `Formatting.kt`

Dos funciones de formateo puro, sin efectos secundarios ni dependencias de
Android (por eso pueden ejecutarse en tests unitarios de JVM sin necesidad de
Robolectric):

### `formatTime(ms: Long): String`

Convierte una duración en milisegundos a `mm:ss` o, si supera la hora, a
`hh:mm:ss`. Se usa en la barra de progreso de reproducción y en las listas de
canciones para mostrar la duración.

```kotlin
formatTime(65_000L)     // "01:05"
formatTime(3_725_000L)  // "1:02:05"
```

### `resolveArtistName(artist: String?, fallback: String): String`

Normaliza el nombre de artista proveniente de `MediaStore`: si es `null`,
está en blanco, o es literalmente `"unknown"`/`"<unknown>"` (valores típicos
que devuelve el proveedor de contenido para archivos sin metadatos), devuelve
`fallback` en su lugar (normalmente una cadena localizada como "Artista
desconocido").
