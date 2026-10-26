# Paint transform performance

## Canvas renderer follow-up

The current implementation uses Android Canvas for resize, skew and arbitrary rotation, retaining Kotlin for quarter turns, flips and integer moves. All operations were benchmarked with Canvas; only families meeting the 10% aggregate improvement / maximum 10% case regression rule switched. Three independent runs were captured for the current Kotlin baseline, all-Canvas candidate and final production policy.

New 4MP opaque-image medians (milliseconds), including bitmap conversion and pixel readback:

| Case | Kotlin baseline | Final | Speedup |
|---|---:|---:|---:|
| Resize to 125% | 654.1 | 66.4 | 9.9x |
| Combined skew | 464.0 | 74.9 | 6.2x |
| Arbitrary rotation, 33° | 477.9 | 81.8 | 5.8x |
| Selection render, 33° | 393.0 | 59.3 | 6.6x |
| Apply command, 33° | 598.8 | 192.5 | 3.1x |

The final run retains the fast 4MP small-selection Apply path (11.1 → 11.0 ms). Some smaller retained Kotlin cases regressed in these measurements: 1MP small-selection render was 1.89 → 2.57 ms and command completion 4.57 → 5.23 ms. Frame times remain near the display interval. No peak-memory or FPS improvement is claimed.

See the [complete Canvas comparison](performance/CANVAS.md) for all operations, alpha fixtures, managed allocations, process-memory snapshots, APK/harness hashes, reproduction commands and limitations. Interrupted attempts were discarded; the emulator was restarted before the final three accepted passes. Concurrent menu changes in the shared workspace were excluded by using the preserved transform-only APK.

Verification: app/test builds and Android lint pass; all 35 JVM tests and 61 relevant instrumentation tests pass. The nine accepted benchmark runs each pass both timing tests. Instrumentation uses local Small Desktop only; its temporary regression-test display override was restored. ARM release performance was not measured.

## Earlier Kotlin optimization measurements

Measured on 26 October 2026 using the local **Small Desktop** emulator, Android API 37, x86_64, 1366 x 768 at 160 dpi. Both versions used debug APKs and the same timing harness. The baseline is commit `ebec86a`; the after measurement covers the Kotlin optimizations committed in `a95e65a`. APK hashes and raw samples are in [before](performance/transform-before.json) and [after](performance/transform-after.json).

## Timings

| Case | Before median | After median | Speedup |
|---|---:|---:|---:|
| 1MP 90-degree rotation | 49.6 ms | 10.6 ms | 4.7x |
| 1MP Horizontal flip | 52.1 ms | 10.7 ms | 4.9x |
| 1MP Resize to 125% | 342.9 ms | 162.7 ms | 2.1x |
| 1MP Selection render at 33 degrees | 595.3 ms | 93.6 ms | 6.4x |
| 1MP Apply command at 33 degrees | 480.4 ms | 133.0 ms | 3.6x |
| 1MP Apply a 64 x 64 selection | 20.1 ms | 4.4 ms | 4.6x |
| 4MP 90-degree rotation | 205.9 ms | 45.5 ms | 4.5x |
| 4MP Horizontal flip | 167.7 ms | 42.1 ms | 4.0x |
| 4MP Resize to 125% | 1074.6 ms | 653.4 ms | 1.6x |
| 4MP Selection render at 33 degrees | 1742.9 ms | 380.3 ms | 4.6x |
| 4MP Apply command at 33 degrees | 1876.8 ms | 526.7 ms | 3.6x |
| 4MP Apply a 64 x 64 selection | 67.0 ms | 10.3 ms | 6.5x |

## Dragging

| Case | Before | After |
|---|---:|---:|
| 1MP, managed allocations over 90 updates | 636.1 MB | 22.2 MB |
| 1MP, P95 frame duration | 18.74 ms | 18.93 ms |
| 4MP, managed allocations over 90 updates | 2454.3 MB | 18.1 MB |
| 4MP, P95 frame duration | 18.71 ms | 18.63 ms |

These are cumulative managed allocations reported by `art.gc.bytes-allocated`, including Compose and input processing. They are not peak RAM usage and do not include all native/graphics allocations. Frame durations stayed near the display interval; this run does not establish an FPS improvement. Cached texture transforms remove repeated selection resampling while the pointer is moving.

## Method

- Deterministic opaque 1024 x 1024 and 2048 x 2048 gradient payloads (1.05 and 4.19 megapixels).
- Raster timings: two warmups, seven measured samples. Setup is excluded. Resize produces 125% dimensions; selection render includes clearing the lifted region and compositing the rotated payload into the original canvas bounds.
- Real pointer input rotates the selection handle through 45 degrees in 90 updates, with a 16 ms pause between updates. FrameMetrics records the resulting window frames. Pointer-dispatch call durations are retained in JSON for diagnostics; they include test synchronization and are not touch-latency estimates.
- Apply timings: five samples from command dispatch until the operation's busy flag clears. Undo and settling happen outside the timed interval. Bitmap presentation, PNG recovery, and file saves are not part of this command-completion metric. The small-selection case moves 64 x 64 pixels by 12 pixels on each canvas size.
- The regression suite uses a temporary 1920 x 1200 display to fit its existing 1200 x 800 surfaces. Both performance runs use the original 1366 x 768 display. The original display size was restored after verification.
- These development/emulator measurements are useful for relative comparison, not release-hardware guarantees.

## Implementation

- Cache payload bitmaps and transform their texture during pointer interaction. Resample exact pixels after release; integer moves reuse sampled pixels.
- Create and prepare display bitmaps on a worker. Retain the previous complete frame and released stroke until the replacement bitmap is ready.
- Use integer pixel mapping for exact quarter turns/flips and row copies for translations; avoid resampling identity transforms.
- Fuse selection source clearing, visible destination sampling, and compositing into one canvas copy. Whole-image background compositing also avoids a separate full-image pass.
- Restrict selection history comparisons to the union of its original and destination bounds. Use a common local sampling origin so cached and committed alpha rounding agrees.

## Verification

Build and Android lint pass. All 44 JVM tests pass. Of 48 focused emulator regression tests, 47 pass; two stroke tests also pass on the final build. The remaining existing shape test expects exact red (`#FFFF0000`) at an antialiased outline sample, but returns `#FFFD0002`. The untouched baseline APK produces the identical failure on this emulator. Its assertion was left unchanged. Both performance-harness tests pass.

## Reproduce

Build the app and instrumentation APK with `:paint:assembleDebug :paint:assembleDebugAndroidTest`. Install them on a verified local Small Desktop emulator only, then run the opt-in harness (replace the serial and active Android user if needed):

```powershell
adb -s emulator-5556 shell am instrument --user 10 -w -e class com.kkps.kdesktopsuite.paint.TransformPerformanceTest -e performanceStage after com.kkps.kdesktopsuite.paint.test/androidx.test.runner.AndroidJUnitRunner
```

Reports are written to the app's private cache as `transform-performance-after-rasterTimings.json` and `transform-performance-after-livePreviewAndApplyTimings.json`. Omit `performanceStage` to skip timing tests during ordinary test runs. For a fresh comparison, preserve the baseline app APK and run the same harness against each version with identical emulator and display settings.
