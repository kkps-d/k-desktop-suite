# Canvas transform benchmark

Measured 26 October 2026 on the local **Small Desktop** emulator, API 37, x86_64, 1366 × 768, density 160. All stages use debug APKs and the same expanded instrumentation harness. This comparison starts from the already optimized Kotlin renderer at `fe36333e30d9c12fa5cc4d644faf003a45eb6bc3`.

## Method and selection

- Three independent instrumentation runs per stage: Kotlin baseline, all-Canvas candidate, and the selected production combination. Tables use the median of the three run medians.
- Deterministic opaque and translucent 1024² and 2048² fixtures. Raster cases use two warmups and seven samples; command cases use five samples. Allocation, bitmap conversion, drawing, and readback are included. Command timings include history preparation, but exclude bitmap presentation and recovery/file saves.
- The geometric mean of candidate/baseline case-time ratios must be at most 0.90, and no case may exceed 1.10, to select Canvas for a family. Integer translations belong to the quarter-turn family; other axis-aligned transforms use resize; remaining composed transforms use affine. Selection is static, shared by previews and committed pixels.
- Raw samples, build/harness hashes, revision and environment metadata: [baseline](canvas-before.json), [Canvas candidate](canvas-candidate.json), [production](canvas-after.json). APKs and instrumentation logs are retained locally under ignored `paint/build/canvas-benchmarks/`; candidate/final source snapshots are saved there too.

Canvas wins resize and general affine sampling. Kotlin retains quarter turns, flips and integer moves: vertical flips already use row copies, and a Canvas round trip makes small selection moves substantially slower. Some individual quarter turns/horizontal flips improve in Canvas, but neither family meets the selection rule.

Canvas uses mutable ARGB_8888 bitmap destinations, explicit matrices and no density scaling. Filtered transforms use bilinear sampling; exact turns/flips disable filtering. Selection Apply clears and draws directly into its destination. Readback touches only the cleared/destination rectangles so untouched low-alpha pixels and bounded undo history remain valid. Settled Canvas previews retain the rendered bitmap without a pixel-array round trip.

## Results

| Family | Canvas aggregate speedup | Worst case time change | Selected |
|---|---:|---:|---|
| quarterTurns | 0.72x | +238.7% | Kotlin |
| flips | 0.60x | +231.5% | Kotlin |
| resize | 8.05x | -85.6% | Canvas |
| affine | 6.04x | -75.8% | Canvas |

| Case | Kotlin baseline ms | Canvas candidate ms | Final ms |
|---|---:|---:|---:|
| 1MP opaque rotate90 | 14.25 | 13.74 | 11.36 |
| 1MP opaque rotate-90 | 16.13 | 13.82 | 11.34 |
| 1MP opaque rotate180 | 14.76 | 9.76 | 11.16 |
| 1MP opaque flipH | 10.79 | 9.57 | 10.73 |
| 1MP opaque flipV | 3.23 | 9.50 | 3.82 |
| 1MP opaque resize125 | 166.41 | 17.97 | 16.60 |
| 1MP opaque resize75 | 59.78 | 7.96 | 7.45 |
| 1MP opaque rotate33 | 120.58 | 20.86 | 20.43 |
| 1MP opaque skewH | 110.96 | 15.20 | 14.80 |
| 1MP opaque skewV | 112.56 | 16.14 | 15.95 |
| 1MP opaque skewBoth | 126.85 | 16.94 | 17.43 |
| 1MP opaque resizeSkew | 109.94 | 17.33 | 15.90 |
| 1MP opaque apply33 | 94.99 | 16.25 | 14.34 |
| 1MP opaque clippedApply33 | 67.31 | 15.49 | 13.96 |
| 1MP opaque smallApply | 1.89 | 6.38 | 2.57 |
| 1MP alpha rotate90 | 10.83 | 14.12 | 11.08 |
| 1MP alpha rotate-90 | 10.35 | 15.35 | 10.75 |
| 1MP alpha rotate180 | 10.54 | 9.94 | 10.54 |
| 1MP alpha flipH | 11.77 | 9.67 | 10.51 |
| 1MP alpha flipV | 3.32 | 10.70 | 3.17 |
| 1MP alpha resize125 | 162.70 | 17.93 | 16.28 |
| 1MP alpha resize75 | 58.72 | 8.46 | 7.64 |
| 1MP alpha rotate33 | 122.42 | 22.75 | 20.37 |
| 1MP alpha skewH | 108.07 | 16.73 | 14.31 |
| 1MP alpha skewV | 110.87 | 18.18 | 16.24 |
| 1MP alpha skewBoth | 116.66 | 21.54 | 17.17 |
| 1MP alpha resizeSkew | 106.55 | 20.73 | 16.25 |
| 1MP alpha apply33 | 123.40 | 15.72 | 14.75 |
| 1MP alpha clippedApply33 | 79.16 | 15.65 | 14.05 |
| 1MP alpha smallApply | 2.06 | 6.18 | 2.31 |
| 4MP opaque rotate90 | 46.70 | 56.70 | 44.23 |
| 4MP opaque rotate-90 | 49.34 | 57.95 | 47.02 |
| 4MP opaque rotate180 | 44.12 | 38.46 | 42.74 |
| 4MP opaque flipH | 44.25 | 40.99 | 44.78 |
| 4MP opaque flipV | 12.81 | 39.85 | 13.36 |
| 4MP opaque resize125 | 654.13 | 78.26 | 66.38 |
| 4MP opaque resize75 | 234.96 | 32.00 | 29.75 |
| 4MP opaque rotate33 | 477.92 | 88.72 | 81.83 |
| 4MP opaque skewH | 434.75 | 61.90 | 58.61 |
| 4MP opaque skewV | 455.86 | 75.98 | 69.95 |
| 4MP opaque skewBoth | 464.04 | 81.65 | 74.87 |
| 4MP opaque resizeSkew | 438.09 | 72.45 | 72.95 |
| 4MP opaque apply33 | 393.00 | 62.36 | 59.30 |
| 4MP opaque clippedApply33 | 250.01 | 60.39 | 56.93 |
| 4MP opaque smallApply | 7.72 | 26.15 | 7.57 |
| 4MP alpha rotate90 | 42.81 | 55.79 | 43.33 |
| 4MP alpha rotate-90 | 45.25 | 55.42 | 46.19 |
| 4MP alpha rotate180 | 44.30 | 39.89 | 42.40 |
| 4MP alpha flipH | 43.80 | 39.88 | 42.85 |
| 4MP alpha flipV | 12.89 | 42.73 | 13.01 |
| 4MP alpha resize125 | 666.09 | 71.68 | 66.14 |
| 4MP alpha resize75 | 234.72 | 33.10 | 29.71 |
| 4MP alpha rotate33 | 491.82 | 87.51 | 80.17 |
| 4MP alpha skewH | 444.67 | 64.12 | 57.32 |
| 4MP alpha skewV | 516.55 | 73.22 | 68.74 |
| 4MP alpha skewBoth | 464.10 | 78.04 | 72.85 |
| 4MP alpha resizeSkew | 442.63 | 71.17 | 70.37 |
| 4MP alpha apply33 | 495.41 | 61.58 | 58.89 |
| 4MP alpha clippedApply33 | 318.37 | 59.79 | 54.81 |
| 4MP alpha smallApply | 7.88 | 24.56 | 7.54 |

| Command | Baseline ms | Candidate ms | Final ms |
|---|---:|---:|---:|
| 1MP commandApply | 136.81 | 62.09 | 53.73 |
| 1MP clippedCommandApply | 93.37 | 48.37 | 40.98 |
| 1MP smallApply | 4.57 | 10.38 | 5.23 |
| 4MP commandApply | 598.83 | 219.76 | 192.50 |
| 4MP clippedCommandApply | 388.59 | 186.12 | 153.74 |
| 4MP smallApply | 11.13 | 30.69 | 11.02 |

| Stage | Image | Drag managed MB | P95 frame ms | PSS before/after MB | Native heap before/after MB |
|---|---|---:|---:|---:|---:|
| before | 1MP | 19.36 | 19.44 | 171.0/149.9 | 38.9/35.2 |
| before | 4MP | 17.87 | 19.08 | 229.5/237.3 | 91.3/75.3 |
| candidate | 1MP | 16.58 | 19.01 | 233.1/154.6 | 70.5/35.5 |
| candidate | 4MP | 18.24 | 19.19 | 239.2/211.5 | 108.1/70.5 |
| after | 1MP | 20.12 | 18.99 | 169.8/149.8 | 37.0/35.2 |
| after | 4MP | 17.74 | 18.79 | 234.4/235.9 | 91.3/75.4 |

## Interpretation and reproduction

Drag measurements cover 90 pointer updates through 45°, with 16 ms pauses between updates. Managed allocations are cumulative ART bytes, not peak RAM. PSS and native-heap readings are process-wide snapshots, not peaks; allocator retention and other application activity affect them. Frame times near the display interval do not establish an FPS improvement. These sequential emulator/debug measurements show relative costs and ordinary run-to-run variance; they do not predict release performance on ARM hardware.

The original emulator connection disappeared during the first production attempt. An interim visible instance also failed the benchmark's 45° pointer-angle check, and a concurrent Paint build replaced the shared output APK. Those attempts were discarded and their evidence archived under ignored `after-visible-interrupted/` and `after-concurrent-build/`. Final measurements restart all three passes using the preserved transform-only APK in the original headless configuration on `emulator-5556`, with the same Small Desktop AVD, Android image, user 10, display size and density. The runner verifies the installed APK hash before accepting each pass. The emulator restart remains an additional limitation of the comparison.

Canvas normalizes transparent colors and can round filtered pixels differently. Tests compare controlled transparent-edge fixtures over black and white, retain exact opaque quarter-turn/flip assertions, and verify source immutability, clipping, cancellation, cache reuse and document workflows.

Validation passed for the transform build: 35 JVM tests, Android lint, app/instrumentation builds, 61 relevant Android regression tests, and all 18 timing tests across the nine accepted benchmark runs. Regression coverage includes toolbar commands, selection Apply/cancel, undo/redo, copy/crop, recovery and storage. Android tests ran only on local Small Desktop; the regression display override was restored afterward. Concurrent menu edits were preserved and excluded from the measured/regression APK.

Build each stage with `:paint:assembleDebug :paint:assembleDebugAndroidTest`, then run `./paint/performance/run-canvas-benchmarks.ps1 -Stage before` (or `candidate` / `after`). Start the local Small Desktop emulator first if needed and keep its display at 1366 × 768, density 160. Use the baseline renderer plus the expanded harness for `before`, force `usesCanvas()` to true for `candidate`, and use the production selection policy for `after`. The runner verifies the AVD, saves APKs, and resumes completed runs. Existing stage directories reuse their saved APKs; archive those directories before measuring a different build under the same stage name.

`python paint/performance/summarize-canvas-benchmarks.py` regenerates comparison tables from the raw JSON, requiring all three runs and matching harness hashes. Keep the earlier `transform-before.json` / `transform-after.json` comparison unchanged; it measured the preceding Kotlin optimizations.
