# Cold start baseline — pre-optimization

> Instrucciones para rellenar este documento:
> 1. Instala la APK debug con `./gradlew installDebug`
> 2. **No midas el primer run** tras instalar — espera al menos 3 runs para que ART compile el profile
> 3. Condiciones obligatorias: batería >50%, modo avión ON, sin apps en background, pantalla al 100%

## TotalTime (n=10, `adb shell am start -W`)

```
adb shell am force-stop com.example.prism; sleep 1; adb shell am start -W -n com.example.prism/.MainActivity | grep TotalTime
```

| Run | TotalTime (ms) |
|-----|---------------|
| 1   | ___ |
| 2   | ___ |
| 3   | ___ |
| 4   | ___ |
| 5   | ___ |
| 6   | ___ |
| 7   | ___ |
| 8   | ___ |
| 9   | ___ |
| 10  | ___ |

- **p50 (run 5):** ___ ms
- **p90 (run 9):** ___ ms
- **max (run 10):** ___ ms

## Phase breakdown (logcat, 1 run representativo)

```
adb logcat -s PrismStartup -d | grep -E "PHASE|BREAKDOWN"
```

| Phase | Delta (ms) | Cumulative (ms) |
|-------|-----------|-----------------|
| begin → super.onCreate done | ___ | ___ |
| super.onCreate → startKoin done | ___ | ___ |
| startKoin → scopes resolved | ___ | ___ |
| scopes resolved → MainActivity.onCreate done | ___ | ___ |
| MainActivity.onCreate → setContent entry | ___ | ___ |
| setContent entry → first composition | ___ | ___ |
| first composition → first frame drawn | ___ | ___ |

> Nota: `coil pre-warm done` y `scanner started` corren en IO y no están en el critical path.
> Lo que importa para el usuario es `first frame drawn`.

## Perfetto analysis (ui.perfetto.dev)

```bash
adb shell am force-stop com.example.prism
adb shell perfetto \
  -o /data/misc/perfetto-traces/prism_startup_before.perfetto-trace \
  -t 8s \
  sched freq idle am wm gfx view binder_driver hal dalvik input res &
sleep 1
adb shell am start -W -n com.example.prism/.MainActivity
adb pull /data/misc/perfetto-traces/prism_startup_before.perfetto-trace \
  benchmarks/startup/prism_startup_before.perfetto-trace
```

- **Time to First Frame:** ___ ms
- **Main thread work before first frame:**
  - Koin `startKoin {}` block: ___ ms
  - First Compose pass (`Choreographer#doFrame`): ___ ms
  - Any blocking I/O on main thread: ___ ms
- **Binder calls count (main thread):** ___
- **Storage I/O during startup:** ___

## Condiciones de medición

- Sistema térmico: ___ (frío / tibio / caliente)
- Batería: ___ %
- Modo avión: ✓ / ✗
- Background apps: ___ (pocas / muchas)
- Apps cerradas antes de medir: ✓ / ✗

## Notas

<!-- Pega aquí el output completo del dump de StartupMetrics -->

---

<!-- La siguiente sección se rellena DESPUÉS de aplicar los commits de optimización -->

## Post-optimization

| Métrica | Pre (ms) | Post (ms) | Δ (ms) |
|---------|---------|---------|--------|
| TotalTime p50 | ___ | ___ | -___ |
| TotalTime max | ___ | ___ | -___ |
| Time to First Frame | ___ | ___ | -___ |
| startKoin phase | ___ | ___ | -___ |
| First composition | ___ | ___ | -___ |

## Commits aplicados

- [ ] Commit 3: Instrumentación (Phase 0)
- [ ] Commit 4: Baseline capturado (Phase 0)
- [ ] Commit 5: Defer scan + reduce delay 800→200ms (Phase 1)
- [ ] Commit 7: Paging maxSize=400 (Phase 2)
- [ ] Commit 8: Koin lazy — condicional a Perfetto
- [ ] Commit 9: UI composition — condicional a Perfetto
- [ ] Commit 10: Coil defer — condicional a Perfetto

## Conclusión

- Antes: ___ ms
- Después: ___ ms
- Mejora: ___% (___ ms absolutos)
- Objetivo ≤ 1200 ms: ✓ / ✗
