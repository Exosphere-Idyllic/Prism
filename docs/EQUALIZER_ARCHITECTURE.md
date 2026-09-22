# Arquitectura del Ecualizador Gráfico y Paramétrico (Prism)

Este documento detalla la arquitectura, el diseño matemático DSP y el flujo de comunicación por capas implementado para el sistema de ecualización en Prism.

---

## 1. Visión General de la Arquitectura

El diseño sigue estrictamente los principios de **Responsabilidad Única (SRP)**, **Segregación de Interfaces (ISP)** y **CQRS (Command Query Responsibility Segregation)**, desacoplando por completo el procesamiento DSP de audio de la gestión de reproducción y de la interfaz de usuario:

```
┌─────────────────────────────────────────────────────────────┐
│                       UI / PRESENTACIÓN                     │
│  EqualizerScreen (Simple: 10 bandas ISO / Avanzado: Canvas) │
│  ParametricEqCanvas (Curva matemática de respuesta |H(f)|)   │
│  EqualizerViewModel (StateFlow reactivo + Debounce 100ms)   │
└──────────────────────────────┬──────────────────────────────┘
                               │ Inyección Koin / StateFlow
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                    CONTROLADOR / DISPATCHER                 │
│  EqualizerController (ISP - Desacoplado de PlaybackManager) │
│  CustomCommandDispatcher (MediaSession IPC)                 │
└──────────────┬───────────────────────────────┬──────────────┘
               │ Escritura atómica             │ Comandos de Sesión
               ▼                               ▼
┌──────────────────────────────┐ ┌────────────────────────────┐
│      PERSISTENCIA / DATOS    │ │    SERVICIO DE AUDIO       │
│  EqualizerRepository (CQRS)  │ │  PlaybackService           │
│  EqualizerPreferences        │ │  (MediaSession.Callback)   │
│  (DataStore JSON + Mutex)    │ └─────────────┬──────────────┘
└──────────────────────────────┘               │
                                               ▼
                                 ┌────────────────────────────┐
                                 │      EXOPLAYER PIPELINE    │
                                 │  PrismRenderersFactory     │
                                 │  DefaultAudioSink          │
                                 │  EqualizerAudioProcessor   │
                                 │  (AtomicReference config)  │
                                 └─────────────┬──────────────┘
                                               │
                                               ▼
                                 ┌────────────────────────────┐
                                 │         MOTOR DSP          │
                                 │  BiquadFilter              │
                                 │  (Direct Form II Transp.)  │
                                 │  BiquadCoefficients (RBJ)  │
                                 │  Soft-Knee Limiter         │
                                 └────────────────────────────┘
```

---

## 2. Motor DSP y Procesamiento de Audio

### 2.1 BiquadCoefficients
- Implementa las fórmulas del **Audio EQ Cookbook de Robert Bristow-Johnson (RBJ)** para filtros IIR de segundo orden:
  - `BELL` (Peaking EQ)
  - `LOW_SHELF` y `HIGH_SHELF`
  - `LOW_PASS` y `HIGH_PASS` (-12 dB/octava)
  - `NOTCH` (rechazabanda)
- Optimización de Bypass: ganancia absoluta $< 0.005\text{ dB}$ conmuta inmediatamente al filtro identidad ($b_0 = 1, b_1=b_2=a_1=a_2 = 0$).
- Evaluación analítica de respuesta en frecuencia:
  $$\left|H(e^{j\omega})\right| = \sqrt{\frac{(b_0 + b_1\cos\omega + b_2\cos 2\omega)^2 + (-b_1\sin\omega - b_2\sin 2\omega)^2}{(1 + a_1\cos\omega + a_2\cos 2\omega)^2 + (-a_1\sin\omega - a_2\sin 2\omega)^2}}$$
  Esto permite graficar en tiempo real en la UI la curva de respuesta exacta en decibelios sin necesidad de un analizador FFT en tiempo de ejecución.

### 2.2 BiquadFilter
- Estructura **Direct Form II Transposed**, minimizando errores de redondeo de punto flotante y eliminando el retardo en el sumador de salida:
  $$y[n] = b_0 x[n] + s_1[n-1]$$
  $$s_1[n] = b_1 x[n] - a_1 y[n] + s_2[n-1]$$
  $$s_2[n] = b_2 x[n] - a_2 y[n]$$
- Estados independientes por canal estéreo/multicanal ($s_1, s_2$).

### 2.3 EqualizerAudioProcessor
- Extiende `BaseAudioProcessor` de AndroidX Media3.
- Soporta PCM 16-bit nativo y PCM Float de 32-bit.
- **Zero Allocations:** No crea objetos en heap durante `queueInput()`, reutilizando buffers internos directos para evitar recolectores de basura durante la reproducción de audio.
- **Thread-Safety Lock-Free:** Actualizaciones de configuración vía `AtomicReference<EqualizerConfig>`.
- **Soft-Knee Limiter:** Limitador polinomial de saturación suave para prevenir distorsión armónica digital dura (clipping) al aplicar ganancias positivas elevadas o preamp agresivo.

---

## 3. Modos de Operación

| Característica | Modo Simple | Modo Avanzado |
|---|---|---|
| **Bandas** | 10 bandas ISO estándar (31 Hz – 16 kHz) | 8 bandas paramétricas (60 Hz – 16 kHz) |
| **Topología** | Shelves en extremos, Bell en intermedias | Configurable por banda (Bell, Shelves, Pass, Notch) |
| **Parámetros** | Ganancia lineal (-12 dB a +12 dB) | Frecuencia (20 Hz - 20 kHz), Ganancia, Q (0.1 - 10) |
| **Visualización** | Sliders verticales ISO + Chips de presets | Canvas interactivo con curva analítica y nodos arrastrables |
| **Presets** | 8 presets (Rock, Pop, Bass Boost, Flat, etc.) | Configuración de precisión personalizada |

---

## 4. Comunicación IPC y Gestión de Offload

1. **Gestión de Audio Offload:** En dispositivos Android modernos, la decodificación por hardware (DSP Offload) se desactiva automáticamente al encender el ecualizador (`AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED`), permitiendo que el pipeline de ExoPlayer procese el flujo PCM a través de `EqualizerAudioProcessor`.
2. **MediaSession IPC:** La UI interactúa con el servicio de fondo a través de comandos de sesión tipados (`COMMAND_SET_EQ_CONFIG`, `COMMAND_SET_EQ_BAND`, etc.) para sincronizar el estado sin acoplar directamente el ciclo de vida de la actividad con el servicio.
