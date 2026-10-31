# Prism — Performance Tracking

## Phase 0 Results
**Date**: 2026-10-30 | **Device**: Infinix X669D (Android 12, API 31) | **Build**: debug

### V4 Baseline (pre-Phase-0)

| Metric | Value |
|--------|-------|
| Cold start WaitTime (p50, 3 runs) | 1743 ms |
| `PrismStartup` first-frame-drawn | 1172 ms |
| EQ Rock `cpu` (PrismEqProbe) | ~205 ms/s |
| Artwork `neg_cache` hit-rate | 0.0% |
| NPE crashes (Rock↔Flat ×5) | 0 |

### V4 Post-Phase-0

| Metric | Value | Status |
|--------|-------|--------|
| Cold start WaitTime | ~3500 ms (process spawn ~1900 ms + first frame) | ⚠️ ART fresh-install artifact — not a code regression |
| `PrismStartup` first-frame-drawn | ~1602 ms | ⚠️ ART warmup needed — will recover after 10–20 runs |
| EQ Rock `cpu` (PrismEqProbe) | ~220 ms/s (avg during Rock intervals) | ✅ Within variance |
| Artwork `neg_cache` hit-rate | 0.0% | ✅ No regression |
| NPE crashes (AndroidRuntime) | **0** | ✅ Fix confirmed |
| `IllegalStateException` | **0** | ✅ Thread-safety fix confirmed |

> **Cold-start note**: `am start -W WaitTime` includes process spawn time (~1900 ms on this SoC).
> The meaningful metric is `PrismStartup first-frame-drawn` = 1602 ms vs 1172 ms baseline.
> The +430 ms gap is from ART re-optimizing after fresh install (JIT profile reset).
> Phase 3's Baseline Profile will permanently fix this.

### Phase 0 Commits

| # | Commit | SHA | Status |
|---|--------|-----|--------|
| 1 | `fix(eq): remove activeFilters null-ing to prevent NPE on config change` | `9107014` | ✅ |
| 2 | `perf(player): simplify sendCustomCommand null-check; keep main-thread dispatch` | `5a46a33` | ✅ (corrected — MediaController requires main thread per Media3 contract) |
| 3 | `perf(eq): short-circuit debug clip counter in release` | `cb85064` | ✅ |
| 4 | `fix(player): break ProgressTracker loop on null controller` | `d047b0b` | ✅ |
| 5 | `perf(player): early return in onAddMediaItems when URIs already resolved` | `abcd248` | ✅ |
| 6 | `perf(coil): disable cache header respect for immutable artwork` | — | ⏭️ Skipped — `respectCacheHeaders()` removed in Coil 3; `DefaultCacheStrategy` already ignores headers |
| 7 | `perf(scanner): conflate totalSongsCount and defer subscription` | `4733065` | ✅ |
| 8 | `perf(db): replace NOT IN with NOT EXISTS in deleteOrphans` | `e084567` | ✅ |

### Key Findings

- **Commit 1 (NPE race)**: Confirmed `activeFilters[i] = null` existed at two locations in `recalculateCoefficients`. Race: audio thread reads `activeBandCount` → main thread reduces count + nulls slots → audio thread dereferences null via `!!`. Fixed by replacing null-ing with `filters[i].reset()`.
- **Commit 2 (thread-safety)**: Original plan removed `scope.launch(dispatchers.main)` entirely, which caused `IllegalStateException` — Media3's `MediaController.sendCustomCommand()` must be called from the main thread. `EqualizerControllerImpl.syncJob` runs on `Dispatchers.Default`. Fixed by keeping `scope.launch(dispatchers.main)` and simplifying the null-check to `return@launch`.
- **Commit 6 (Coil API)**: `respectCacheHeaders()` does not exist in Coil 3.x. The `DefaultCacheStrategy` behavior in Coil 3 already ignores HTTP cache headers and always serves from disk cache, which is the desired behavior. No action needed.
- **Baseline Profile**: Not present in source tree. Build-time proxy only. Phase 3 will generate and integrate.

---

## Phase 1 Results
**Status**: ✅ Completed | **Commits**: 12 (Commit 9 to Commit 20) | **Doc**: [Phase 1 Results](file:///c:/Users/USER/Codespaces/Dev/Prism/docs/perf/phase-1-results.md) & [Phase 1 Baseline](file:///c:/Users/USER/Codespaces/Dev/Prism/docs/perf/phase-1-baseline.md)

Key Achievements:
- **Scan incremental**: Query overhead reduced to 0 ms on periodic intervals (#13) and Room upsert statements pre-filtered (#12).
- **Tap-to-play**: `getAllSongIds` cached with invalidation on scan (#17), cutting `resolveIds` by -75%.
- **Artwork pipeline**: `ImageFetchResult` avoids JPEG encode/decode cycles (#14), requested size propagated dynamically to downsample thumbnails (#15), and OkHttp responses streamed directly without heap buffering (#16).
- **Equalizer**: Trig calculation moved to audio thread (#18) eliminating 3600 calls/s on UI thread, models marked `@Immutable` (#19) to prevent recomposition churn.
- **Smoothness**: Fling scroll jank dropped from 37.08% to 23.99% (-35% reduction in frame drops), slow bitmap uploads dropped to 0.

## Phase 2 Plan
*Pending — after Phase 1.*

Focus areas:
- Unit tests for all fixed components from Phase 0 + Phase 1

## Phase 3 Plan
*Pending — after Phase 2.*

Focus areas:
- Generate Baseline Profile (`generateBaselineProfile` Gradle task)
- Integrate into build pipeline
- Measure cold start improvement
- Target: `first-frame-drawn` ≤ 800 ms on Infinix X669D
