# EQ CPU baseline — pre-optimization

Dispositivo: Infinix Smart 7 (X669D, Android 12)  
Escenario: reproducción continua 60 s, pantalla apagada, batería > 50%

> [!NOTE]
> Rellenar ejecutando:
> ```bash
> adb logcat -c && adb logcat -s PrismEqProbe
> ```
> Esperar al menos 2 líneas de reporte (cada 5 s). Tomar el valor estable.

---

## EQ desactivado (bypass)

| Métrica       | Valor |
|---------------|-------|
| avg ms/buffer | ___   |
| buffers/s     | ___   |
| cpu ms/s      | ___   |
| clips         | ___   |

> [!IMPORTANT]
> Con EQ desactivado se espera `cpu < 1 ms/s`. Si es mayor, el bypass tiene un bug.

---

## EQ activado, preset "Flat" (todas las bandas 0 dB)

| Métrica       | Valor |
|---------------|-------|
| avg ms/buffer | ___   |
| buffers/s     | ___   |
| cpu ms/s      | ___   |
| clips         | ___   |

---

## EQ activado, preset "Rock" (bandas activas)

| Métrica       | Valor |
|---------------|-------|
| avg ms/buffer | ___   |
| buffers/s     | ___   |
| cpu ms/s      | ___   |
| clips         | ___   |

> [!IMPORTANT]
> Con "Rock" activo se espera `cpu > 20 ms/s`. Si es menor, el probe no está midiendo
> correctamente — investigar antes de continuar.

---

## Notas de contexto

| Campo          | Valor |
|----------------|-------|
| Audio Hz       | ___   |
| Canales        | ___   |
| Estado térmico | ___   |
| Commit hash    | ___   |

---

## Post-optimization (rellenar después de Fase 1+2)

| Escenario         | Baseline cpu ms/s | Post cpu ms/s | Δ    |
|-------------------|-------------------|---------------|------|
| EQ desactivado    | ___               | ___           | 0%   |
| EQ activo "Flat"  | ___               | ___           | ___% |
| EQ activo "Rock"  | ___               | ___           | ___% |

### Commits aplicados

- [ ] 1: probe debug (EqualizerProbe)
- [ ] 2: baseline doc
- [ ] 3: processSample(coeffs) + snapshot per buffer
- [ ] 4: magnitudeDb usa ===
- [ ] 5: compactación activeFilters + bypass neutral
- [ ] 6: debounce + eliminar doble-hop de coroutine
- [ ] 7: ProgressTracker singleton + cancel collectJob en release

### Conclusión

- CPU "Rock": -___%
- CPU "Flat": -___% (bit-perfect)
- Jank del slider: ___
- Audio: sin regresiones acústicas
