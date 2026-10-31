# Phase 1 — Baseline & Post-Optimization Measurements

- **Dispositivo**: Infinix Smart 7 (X669D, Android 12)
- **Fecha**: 2026-10-30
- **Baseline Commit**: `9cc08b6` (main + ScanProbe & PlayTapProbe)
- **Post-Optimization Commit**: `74b7cbd`

## Scan incremental (0 cambios, 655 canciones)
| Fase | Baseline | Post-Optimization (Reconcile pass) | Post-Optimization (Deferred) |
|---|---|---|---|
| getSongsSyncInfo | 0 ms | 0 ms | 0 ms |
| queryMediaStoreIds | 39 ms | 46 ms | 0 ms |
| diffAndQueryChanged | 76 ms | 75 ms | ~75 ms |
| upsert | 11 ms | 7 ms | ~7 ms |
| delete | 1 ms | 0 ms | 0 ms |
| metadataSync | 1 ms | 0 ms | 0 ms |
| setLastScanTimestamp | 63 ms | 53 ms | ~53 ms |
| **Total** | **194 ms** | **186 ms** | **~135 ms** |

## Scan full (655 canciones)
| Fase | Baseline |
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
| Fase | Baseline (Avg) | Post-Optimization (Steady-state) |
|---|---|---|
| resolveIds | 114 ms (spikes 288 ms) | 22–29 ms |
| resolveSongs | 163 ms | 65–71 ms |
| toMediaItems | 36 ms | 36–42 ms |
| setMediaItems | 11 ms | 8–10 ms |
| play | 1 ms | 1 ms |
| **Total** | **327 ms** | **143–332 ms** |

## Scroll jank (fling 10 s)
| Métrica | Baseline | Post-Optimization | Δ |
|---|---|---|---|
| Total frames | 1068 / 81 | 721 / 78 | — |
| Janky frames (%) | 37.08% | 23.99% | **-35.3%** |
| Slow bitmap uploads | 3 | 0 | **-100%** |
| p50 frame time | 20 ms | 18 ms | **-10%** |
| p90 frame time | 42 ms | 77 ms | — |
| p99 frame time | 500 ms | 150 ms | **-70%** |
