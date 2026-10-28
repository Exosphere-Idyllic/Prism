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
