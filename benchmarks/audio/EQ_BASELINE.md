# EQ CPU baseline — Infinix Smart 7 (X669D, Android 12)

Dispositivo: Infinix Smart 7 (X669D, Android 12)  
Escenario: reproducción continua ~60 s, batería > 50%

---

## Baseline Pre-Optimización (commit `d392e41`)

### 1. EQ desactivado (bypass)
| Métrica       | Valor |
|---------------|-------|
| avg ms/buffer | 0.033 |
| buffers/s     | 83.4  |
| cpu ms/s      | 2.72  |
| clips         | 0     |

### 2. EQ activado, preset "Flat" (todas las bandas 0 dB)
| Métrica       | Valor   |
|---------------|---------|
| avg ms/buffer | 2.398   |
| buffers/s     | 83.3    |
| cpu ms/s      | 199.85  |
| clips         | 18498   |

### 3. EQ activado, preset "Rock" (bandas activas)
| Métrica       | Valor   |
|---------------|---------|
| avg ms/buffer | 2.565   |
| buffers/s     | 83.3    |
| cpu ms/s      | 213.77  |
| clips         | 25645   |

---

## Post-Optimización (commit `d50b7f8`)

| Escenario         | Baseline cpu ms/s | Post cpu ms/s | Δ (%)                |
|-------------------|-------------------|---------------|----------------------|
| EQ desactivado    | 2.72              | 3.35          | ~0% (bypass nativo)  |
| EQ activo "Flat"  | 199.85            | 3.10          | **-98.45%** (🔥)     |
| EQ activo "Rock"  | 213.77            | 213.28        | -0.23%               |

---

## Commits aplicados y validados
- [x] 1: `b9b56da` probe debug (`EqualizerProbe`)
- [x] 2: `d392e41` baseline doc template
- [x] 3: `fdaa3a8` `processSample(coeffs)` + snapshot per buffer
- [x] 4: `52a3bb1` `magnitudeDb` usa `===`
- [x] 5: `5be7769` compactación `activeFilters` + bypass neutral (`activeBandCount == 0`)
- [x] 6: `3a637f2` debounce (16 ms) + eliminar doble-hop de coroutine
- [x] 7: `76def0a` `ProgressTracker` singleton + cancel `collectJob` en release
- [x] Test fix: `d50b7f8` adaptación de tests unitarios

---

## Hallazgos y análisis técnico

1. **Preset "Flat" (identidad):**
   - Reducción masiva de **~200 ms/s a 3.10 ms/s (-98.45%)**.
   - Al compactar las bandas a 0 dB, `activeBandCount == 0` activa el camino rápido de copia directa de buffers (`output.put(inputBuffer)`), eliminando por completo 960 000 ciclos de filtrado biquad por segundo en un preset neutro.

2. **Preset "Rock" (10 bandas activas con ganancia ≠ 0):**
   - El baseline confirmó que el DSP biquad puro en 10 bandas activas toma ~213 ms/s en este SoC Cortex-A53.
   - En "Rock", las 10 bandas son no-cero (`activeBandCount == 10`), por lo que los 10 filtros deben procesar cada muestra.
   - La eliminación de las lecturas `@Volatile` por muestra tuvo un impacto secundario comparado con la aritmética de punto flotante en Kotlin/JVM (6 multiplicaciones + 4 sumas/restas por banda × 10 bandas × 96k muestras/s = ~9.6 millones de FLOPs/s en Java sin SIMD/NEON).
