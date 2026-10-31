# Phase 1 — Baseline (pre-optimization)

- **Dispositivo**: Infinix Smart 7 (X669D, Android 12)
- **Fecha**: 2026-10-30
- **Commit**: `9cc08b6` (main + ScanProbe & PlayTapProbe)

## Scan incremental (0 cambios, 655 canciones)
| Fase | Tiempo |
|---|---|
| getSongsSyncInfo | 0 ms |
| queryMediaStoreIds | 39 ms |
| diffAndQueryChanged | 76 ms |
| upsert | 11 ms |
| delete | 1 ms |
| metadataSync | 1 ms |
| setLastScanTimestamp | 63 ms |
| **Total** | **194 ms** |

## Scan full (655 canciones)
| Fase | Tiempo |
|---|---|
| getSongsSyncInfo | 0 ms |
| queryMediaStoreIds | 84 ms |
| diffAndQueryChanged | 166 ms |
| upsert | 1347 ms |
| delete | 0 ms |
| metadataSync | 42 ms |
| setLastScanTimestamp | 160 ms |
| **Total** | **1801 ms** |

## Tap-to-play (sin playlist, 5 taps)
| Fase | Avg |
|---|---|
| resolveIds | 114 ms |
| resolveSongs | 163 ms |
| toMediaItems | 36 ms |
| setMediaItems | 11 ms |
| play | 1 ms |
| **Total** | **327 ms** |

## Scroll jank (fling 10 s)
| Métrica | Valor |
|---|---|
| Total frames | 81 |
| Janky frames (%) | 77.78% |
| p50 | 57 ms |
| p90 | 125 ms |
| p99 | 250 ms |
