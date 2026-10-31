# Phase 1 — Performance Optimization Results

- **Dispositivo**: Infinix Smart 7 (X669D, Android 12, API 31)
- **Fecha**: 2026-10-30
- **Rama**: `perf/phase-1`

## Resumen Before vs After

| Métrica | Baseline | Post-Phase-1 | Δ | Commit(s) responsables |
|---|---|---|---|---|
| **Scan incremental queryIds (reconcile deferred)** | 39 ms | 0 ms | **-100%** | #13 (`4368a79`) |
| **Scan incremental upsert (0 cambios)** | 11 ms | 7 ms | **-36%** | #12 (`66e636c`) |
| **Scan incremental total (deferred)** | 194 ms | ~135 ms | **-30%** | #12, #13 |
| **Tap-to-play resolveIds (steady-state)** | 114 ms (spikes 288 ms) | ~22-29 ms | **-75%** | #17 (`29e3043`) |
| **Artwork thumbnail pipeline** | JPEG re-encode + decode | Direct `ImageFetchResult` | **-100% encode churn** | #14 (`f4d892f`) |
| **Artwork thumbnail resolution** | Always 512×512 (fixed) | Dynamic `options.size` (128-512) | **-60% RAM en mini-player** | #15 (`1b13fe8`) |
| **HTTP artwork fetch** | Full buffer in heap | Streamed `body.source()` | **Zero heap byte buffering** | #16 (`8156c98`) |
| **EQ drag UI thread overhead** | ~12-30 ms/s trig on main | Lazy on audio thread | **Eliminated UI jank** | #18 (`50bfdee`) |
| **EQ model recompositions** | Unstable inferred | `@Immutable` skipped equals | **Recompositions skipped** | #19 (`74b7cbd`) |
| **Scroll fling janky frames** | 37.08% | 23.99% | **-35% janky frames** | #14, #15, #18, #19 |
| **Scroll slow bitmap uploads** | 3 | 0 | **-100% slow uploads** | #14, #15 |

---

## Detalle de Commits Phase 1

| # | Commit | SHA | Descripción |
|---|---|---|---|
| 9 | `feat(probe): add ScanProbe for MediaStore scan phase tracing` | `6744840` | Trazador de fases para `MediaStoreScanner.doScan()` en debug |
| 10 | `feat(probe): add PlayTapProbe for playSong latency measurement` | `9cc08b6` | Trazador de latencia de tap-to-play y resolución de IDs |
| 11 | `docs(perf): baseline measurements for scan + tap-to-play + scroll` | `b657f60` | Mediciones empíricas baseline pre-optimización en Infinix X669D |
| 12 | `perf(scan): pre-filter upsertPreservingUserFields via getSongsSyncInfo` | `66e636c` | Pre-filtrado en Room para evitar 1310 statements innecesarios |
| 13 | `perf(scan): restrict queryMediaStoreIds to periodic reconciliation` | `4368a79` | Reconciliación de MediaStore IDs periódica (5 min) o forzada |
| 14 | `perf(artwork): use ImageFetchResult for loadThumbnail path` | `f4d892f` | `ImageFetchResult` de Coil 3 que elimina ciclo JPEG encode/decode |
| 15 | `perf(artwork): propagate requested size to loadThumbnail` | `1b13fe8` | Uso de `options.size` para cargar dimensiones exactas en mini-player |
| 16 | `perf(artwork): stream HTTP responses instead of buffering` | `8156c98` | Streaming directo de OkHttp response body a `SourceFetchResult` |
| 17 | `perf(player): cache getAllSongIds with invalidation on scan` | `29e3043` | Cache `@Volatile` de IDs de biblioteca con invalidación en fin de scan |
| 18 | `perf(eq): move recalculateCoefficients to audio thread` | `50bfdee` | Recálculo diferido en `queueInput` antes de procesar audio |
| 19 | `refactor(domain): mark EqualizerConfig and EqBand @Immutable` | `74b7cbd` | Contrato `@Immutable` para Compose skipping durante drags de EQ |
| 20 | `docs(perf): phase 1 results (before/after per commit)` | *(este commit)* | Documentación completa de resultados numéricos y cierre |
