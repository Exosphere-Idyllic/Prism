# Profiling Guide: SongListItem Performance — Prism App

## Device & Environment
- **Device**: Infinix X669D (API 31, Android 12) — **USE THIS** (mid-range = real UX)
- **Package**: `com.example.melodyplayer`
- **Activity**: `MainActivity`
- **Target Composable**: `SongListItem()` in `PlayerUi.kt`
- **Target List**: `SongList()` 
- **Goal**: Confirm each SongListItem < 2–3 ms to compose

---

## Part 1: Setup JankStats + Add Dependencies

### 1.1 Add JankStats to `app/build.gradle.kts`

In the `dependencies {}` block, add:

```kotlin
// Performance monitoring
implementation("androidx.metrics:metrics-performance:1.0.0-alpha01")
```

Then **Sync Gradle** in Android Studio.

### 1.2 Create a Performance Logger in your project

Create a new file: `app/src/main/java/com/example/melodyplayer/PerformanceMonitor.kt`

```kotlin
package com.example.melodyplayer

import android.app.Activity
import androidx.metrics.performance.JankStats
import android.util.Log

object PerformanceMonitor {
    private var jankStats: JankStats? = null
    private const val TAG = "JankStats"

    fun start(activity: Activity) {
        jankStats = JankStats.createAndTrack(activity) { frameData ->
            // Log every frame drop > 16.67ms (60 fps threshold)
            if (frameData.isJank) {
                Log.w(TAG, "JANK DETECTED: ${frameData.frameDurationUiThread}ms (UI) / ${frameData.frameDurationRenderThread}ms (Render)")
            }
        }
    }

    fun stop() {
        jankStats?.stop()
    }
}
```

### 1.3 Hook into MainActivity

Open `app/src/main/java/com/example/melodyplayer/MainActivity.kt` and add:

```kotlin
override fun onResume() {
    super.onResume()
    PerformanceMonitor.start(this)
}

override fun onPause() {
    PerformanceMonitor.stop()
    super.onPause()
}
```

---

## Part 2: CPU Profiler Setup (No Code Changes Needed)

### 2.1 Enable CPU Profiler Tracing in Android Studio

1. **Build > Select Build Variant** → Choose `debuggable` (or `debug`)
2. **Build > Build APK(s)** or **Run** (if running on device)
3. Install on **Infinix X669D**:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

### 2.2 Launch the App with CPU Profiler

In Android Studio:

1. **Profiler** tab (bottom, or **View > Tool Windows > Profiler**)
2. Select **Infinix X669D** from dropdown
3. Select **com.example.melodyplayer** process
4. Click the **CPU** section
5. Change profiler method to **Sample Java Methods** (faster, less overhead than Trace):
   - Or use **Trace System Calls** if you want syscall detail

### 2.3 Reproduce the Scenario

Once profiler is recording:

1. **Open the app** → navigate to **"Biblioteca"** (Songs list tab)
2. **Scroll slowly** through the list ~20–30 items over 10 seconds
3. **Stop recording** (click stop button in profiler)

---

## Part 3: Analyze the Trace

### 3.1 Understand the Timeline View

The profiler will show:

```
Timeline:
  ├─ UI Thread (main) — where Compose recomposition happens
  ├─ Render Thread — where actual drawing happens
  └─ GC Events — garbage collection pauses
```

**What to look for**:

- **Frame Duration**: Ideally < 16.67ms (60 fps) per frame
- **Recomposition Time**: Each `SongListItem` + its internal `derivedStateOf` evaluation should be **< 2–3 ms**
- **Render Time**: How long it takes to send pixels to screen (usually fast if recomp is fast)

### 3.2 Find SongListItem Composition in the Trace

In the **CPU Profiler timeline**, look for:

1. **Expand the "main" thread** (UI Thread)
2. Search (Ctrl+F) for: **`SongListItem`** or **`SongArtwork`**
3. You'll see stack traces like:
   ```
   SongListItem (compose)
     ├─ SongArtwork (derivedStateOf evaluation + recomposition)
     ├─ remember(model, size, hasWebp)
     ├─ AsyncImage (Coil image load)
     └─ [rest of the item layout]
   ```

### 3.3 Check Frame Duration Breakdown

**Look at each frame**:

- **<5 ms** on UI Thread (recomposition) — ✅ **Good**
- **5–10 ms** — acceptable, but could be faster
- **>10 ms** — **Potential issue**, profile deeper

For **render thread**, it should be similar or faster (hardware accelerated).

---

## Part 4: ADB Commands for the Infinix

### 4.1 Check Device Connection

```bash
adb devices
```

You should see:
```
List of attached devices:
<SERIAL_NUMBER>          device
```

If not listed, enable **USB Debugging** on Infinix:
- Settings > About phone > tap "Build number" 7 times
- Settings > Developer options > USB Debugging → ON
- Reconnect device

### 4.2 Real-time Logcat (JankStats Output)

While the app is running and you're scrolling:

```bash
adb logcat | grep "JankStats"
```

This will print every frame drop in real-time. Watch for patterns:

```
W/JankStats: JANK DETECTED: 22.5ms (UI) / 18.3ms (Render)
W/JankStats: JANK DETECTED: 25.1ms (UI) / 20.1ms (Render)
```

High frequency = scroll is janky.

### 4.3 Profile Method Traces (Via Systrace)

For a deeper trace capture:

```bash
adb shell perfetto --config - <<EOF
buffers {
  size_kb: 100000
}
data_sources {
  config {
    name: "linux.ftrace"
    ftrace_config {
      ftrace_events: "sched_switch"
      ftrace_events: "sched_wakeup"
    }
  }
}
data_sources {
  config {
    name: "android.systrace"
  }
}
duration_ms: 15000
EOF
```

**Then**:
1. Scroll the list for 15 seconds
2. The trace is saved to device, pull it:
   ```bash
   adb pull /data/misc/perfetto-traces/perfetto-trace /tmp/trace.perfetto-trace
   ```
3. Open in **Android Studio > Profiler > Load from File** or use `perfetto.dev` online viewer

---

## Part 5: Interpretation Checklist

### 5.1 Before the Playlist Item Fix

**Expected symptoms**:
- ✗ Many `collectAsStateWithLifecycle(initialValue = 0)` subscriptions in stack trace (one per row)
- ✗ Multiple `remember(playlist.id)` allocations
- ✗ High recomposition count when you scroll (count goes 10, 20, 30+)

### 5.2 After the Playlist Item Fix

**Expected improvements**:
- ✓ **Single** `collectAsStateWithLifecycle` at screen level (not per row)
- ✓ Zero new Flow allocations during scroll
- ✓ Recomposition count stable (only recompose rows that change, not the whole list)

---

## Part 6: Running the Analysis (Step-by-Step)

### Workflow:

1. **Build & Deploy**:
   ```bash
   cd /path/to/Prism
   ./gradlew build
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

2. **Start Profiler** (Android Studio):
   - Profiler > Select Infinix > Select Process > CPU > Sample Java Methods

3. **Reproduce**:
   - Launch app (adb shell am start -n com.example.melodyplayer/com.example.melodyplayer.MainActivity)
   - Navigate to Playlists tab
   - Slow scroll 10 seconds

4. **Stop Recording** → Analyze

5. **Check Logcat**:
   ```bash
   adb logcat | grep -E "JankStats|SongListItem"
   ```

---

## Part 7: What to Measure (Specific Metrics)

### 7.1 Per-Item Compose Time

**In the CPU Profiler trace, for EACH SongListItem**:

| Metric | Target | Why |
|--------|--------|-----|
| `SongListItem` compose | < 3 ms | Main work |
| `SongArtwork` + `derivedStateOf` | < 1 ms | Conditional render |
| `AsyncImage` layout | < 0.5 ms | Coil setup (not actual image load) |
| **Total per item** | **< 3 ms** | 60 fps = 16.67ms/frame, so 5–6 items max per frame |

### 7.2 Frame-Level Metrics

| Metric | Target |
|--------|--------|
| UI Thread frame time | < 16.67 ms |
| Render Thread frame time | < 16.67 ms |
| Frames dropped | 0–2 per 10 sec scroll |

### 7.3 Recomposition Count

In the profiler, check the **Recomposition count** at top:

- **Before fix**: Might show 50+ recompositions for 30-item list (cascading)
- **After fix**: Should show 5–10 recompositions (only changed rows)

---

## Part 8: Common Issues & Solutions

### Issue: "JankStats not found" after Gradle sync

**Fix**: 
```bash
adb shell pm install -r app/build/outputs/apk/debug/app-debug.apk
# Then:
./gradlew clean
./gradlew build
```

### Issue: Profiler shows "No process found" for com.example.melodyplayer

**Fix**:
```bash
adb shell am start -n com.example.melodyplayer/.MainActivity
# Wait 2 seconds, then try profiler again
```

### Issue: Logcat flooded with unrelated logs

**Filter**:
```bash
adb logcat --pid=$(adb shell pidof com.example.melodyplayer) | grep -E "JankStats|Compose|SongList"
```

---

## Part 9: Report Template (Once You Collect Data)

After profiling, fill this out:

```
PROFILING RESULTS: SongListItem Performance
============================================

Device: Infinix X669D (Android 12, API 31)
Date: [date]
Test Duration: 15 seconds (slow scroll)

BEFORE Playlist Item Fix:
  - Average SongListItem compose time: __ ms
  - Max frame time: __ ms
  - Dropped frames: __
  - Recomposition count: __
  - JankStats warnings: __ per 10 sec

AFTER Playlist Item Fix:
  - Average SongListItem compose time: __ ms
  - Max frame time: __ ms
  - Dropped frames: __
  - Recomposition count: __
  - JankStats warnings: __ per 10 sec

Key observations:
  - [What improved]
  - [Any bottlenecks remaining]
  - [Next optimization to tackle]
```

---

## Quick Command Cheat Sheet

```bash
# Check device
adb devices

# Install app
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Start app
adb shell am start -n com.example.melodyplayer/.MainActivity

# Watch JankStats in real-time
adb logcat | grep JankStats

# Filter CPU profiler noise
adb logcat --pid=$(adb shell pidof com.example.melodyplayer)

# Clear logcat
adb logcat -c

# Get frame stats (one-shot)
adb shell dumpsys gfxinfo com.example.melodyplayer
```

---

## Next Steps After Profiling

Once you have baseline numbers, share:

1. The **CPU Profiler trace file** (if large, just describe it)
2. The **JankStats output** from logcat
3. The **recomposition counts** before/after the playlist fix
4. Any frame drops or spikes

Then we'll know:
- If the playlist fix helped (it should)
- What to target next (SongItem? Thumbnail loading? Shimmer animation?)
- Whether the 2–3 ms target is realistic for your device
