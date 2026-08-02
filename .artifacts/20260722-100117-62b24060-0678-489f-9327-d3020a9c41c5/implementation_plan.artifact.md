# Performance Optimization Plan

This plan aims to resolve several performance bottlenecks identified in the Prism music player app, including UI jank, high memory usage, and inefficient background processing.

## Proposed Changes

### UI & Compose Optimizations

#### [Artwork.kt](file:///C:/Users/USER/Codespaces/Dev/Prism/app/src/main/java/com/example/melodyplayer/ui/Artwork.kt)

- **Problem:** `SongArtwork` and `AlbumArtwork` observe a `StateFlow<Set<Long>>`. Whenever any thumbnail is generated (any change to the set), every visible artwork item recomposes, causing significant jank in lists.
- **Solution:** Use `map { it.contains(albumId) }.distinctUntilChanged()` to observe only the specific thumbnail status for each item.

```kotlin
    val hasWebp by remember(albumId, size) {
        val repo = MainApplication.repository
        val flow = if (size <= 128) repo.albumThumbnail128Ids else repo.albumThumbnail256Ids
        flow.map { it.contains(albumId) }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(false)
```

#### [MiniPlayer.kt](file:///C:/Users/USER/Codespaces/Dev/Prism/app/src/main/java/com/example/melodyplayer/ui/MiniPlayer.kt)

- **Problem:** `MiniPlayerProgressBar` reads `progressStateFlow.value` directly inside `drawBehind`. Since it's not a `State`, it doesn't trigger redraws.
- **Solution:** Use `collectAsStateWithLifecycle()` to convert the flow to a `State`, and read its value inside `drawBehind` to trigger redraws without causing parent recompositions.

---

### Data & Resource Efficiency

#### [ThumbnailHelper.kt](file:///C:/Users/USER/Codespaces/Dev/Prism/app/src/main/java/com/example/melodyplayer/data/ThumbnailHelper.kt)

- **Problem:** `loadBitmapViaStream` reads the entire image into a `ByteArray` before decoding, which is highly inefficient and risks OutOfMemory errors for large files.
- **Solution:** Decode directly from the `InputStream` using `BitmapFactory.Options` with `inJustDecodeBounds` to calculate `inSampleSize`, then decode the sampled bitmap.

#### [MusicRepository.kt](file:///C:/Users/USER/Codespaces/Dev/Prism/app/src/main/java/com/example/melodyplayer/data/MusicRepository.kt)

- **Problem:** Processing large lists of thumbnail info in `init` can be slow.
- **Solution:** Use `asSequence()` for the filtering and mapping operations to improve performance on large datasets.
- Clean up unused imports and redundant qualifiers as suggested by static analysis.

#### [AppDatabase.kt](file:///C:/Users/USER/Codespaces/Dev/Prism/app/src/main/java/com/example/melodyplayer/data/AppDatabase.kt)

- **Problem:** Raw `Thread` used for file cleanup in `getDatabase`.
- **Solution:** Use `MainApplication.applicationScope` with `Dispatchers.IO` to run the cleanup task.

---

## Verification Plan

### Automated Tests
- I will run the existing project build to ensure no regressions:
  `./gradlew assembleDebug`

### Manual Verification
- I will use `analyze_file` to verify that the reported warnings are resolved and no new errors are introduced.
- I will review the logic of the changes to ensure they correctly address the identified performance issues.
- Specifically, I will verify that `MiniPlayerProgressBar` now correctly observes the state by checking the code structure against Compose best practices.
