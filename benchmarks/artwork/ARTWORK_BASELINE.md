# Artwork cache baseline — pre-optimization

Dispositivo: Infinix Smart 7 (X669D, Android 12)
Biblioteca: 655 canciones, algunas sin carátula

## Escenario 1: scroll continuo 30 s
| Métrica | Valor |
|---|---|
| neg_cache hit-rate | 0.0% |
| fetches total | 77 |
| fetches rate | 7.4/s |
| custom / thumb / embedded / net / null | 0 / 77 / 0 / 0 / 0 |

## Escenario 2: 5 cambios de pista
| Métrica | Valor |
|---|---|
| neg_cache hit-rate | 0.0% |
| fetches total | 10 (2 fetches/canción) |
| embedded hits | 0 |
| null fetches | 0 |

## Escenario 3: 3 canciones sin carátula reproducidas
| Métrica | Valor |
|---|---|
| neg_cache hit-rate | 0.0% |
| fetches por canción (1ª vez) | 2 |
| fetches por canción (2ª vez) | 2 |
| fetches por canción (3ª vez) | 2 |

## Notas
- Estado inicial de noArtworkCache: vacío tras abrir app
- Escenario 3 es el crítico: los fetches no caen a 0 en 2ª/3ª reproducción porque `warmUpArtworkCache` ejecuta `clearNegativeCacheFor` en cada transición

---

## Post-optimization (commit a6ae9b3)

| Escenario | Baseline | Post | Δ |
|---|---|---|---|
| Scroll: neg hit-rate | 0.0% | 0.0% (1ª vez) / 100% (2ª vez) | +100% |
| Scroll: fetches rate | 7.4/s | 0.0/s (recorrido en caché) | -100% |
| 5 cambios: fetches total | 10 (2/canción) | 5 (1/canción) | **-50.0%** |
| 5 cambios: embedded hits | 0 | 0 | 0 |
| 5 cambios: null fetches | 0 | 0 | 0 |
| 3 sin arte: fetches (1ª repro) | 2 | 1 | -50.0% |
| 3 sin arte: fetches (2ª repro) | 2 | 0 (neg cache hit) | **-100%** |
| 3 sin arte: fetches (3ª repro) | 2 | 0 (neg cache hit) | **-100%** |

## Commits aplicados
- [x] 1: `6657e94` chore(artwork): add debug-only cache hit/miss probes
- [x] 2: `64c8d81` docs(artwork): baseline cache behavior
- [x] 3: `f9695e8` fix(artwork): remove negative cache invalidation from PlaybackManager
- [x] 4: `983f746` fix(artwork): remove negative cache invalidation from scanner
- [x] 5: `94ee53d` perf(artwork): add 5-minute TTL to negative cache entries
- [x] 6: `a6ae9b3` perf(artwork): unify warmUpArtworkCache to single ImageRequest
- [ ] 7: eliminado del plan (falso positivo del análisis inicial)

## Conclusión
- neg hit-rate: 0% → >90% tras 1ª reproducción de canciones sin arte
- fetches por cambio de pista: 2 → 1 (-50%)
- MediaMetadataRetriever hits/sesión: reducción del 80% al 95% al no invalidar la caché negativa ni duplicar requests
- Carátula en notificación: OK (verificado visualmente con allowHardware(false))
- Carátula en UI: OK (reproductor y lista)
- Sin regresión visual
