# Complete rotation and flip commands

Follow-up to the [Canvas raster comparison](CANVAS.md), measured on 26 October 2026 using local **Small Desktop**, API 37, x86_64, 1366 × 768 at 160 dpi, debug APKs.

## Method

- Compare three renderer builds: all Kotlin (`before`), all Canvas (`candidate`), and the committed mixed policy (`after`). Each uses the same command harness, two warmups and five samples per case, with three independent instrumentation runs. Tables show the median of the three run medians.
- Measure from dispatch to `PaintViewModel.rotate` / `flip` until pixels and undo history are committed and `busy` clears. Selection cases include Apply in the same command sequence; raw selections include lifting/extracting their pixels. These measure complete model commands, excluding bitmap presentation, Compose rendering, PNG recovery and file saves. They are not toolbar-click-to-screen latency measurements, and are not directly comparable to the earlier Compose-hosted command metric.
- No UI composition is hosted, so bitmap uploads cannot compete with the next sample. The test supplies a cancelled application recovery scope to suppress deferred PNG writes. Setup, undo and fixture validation happen outside timed intervals. Every command must commit a new raster, clear the preview, retain canvas dimensions and undo to the exact original pixels.
- Deterministic 1024² and 2048² gradient canvases, with six targets: `canvas` (no selection), `selection` (select all, including pixel lifting), `floatingAlpha` (pasted translucent payload), `clipped` (half outside the canvas), `composed33` (already rotated 33°), and `small64` (64 × 64 pixels). Each runs right/left 90°, 180°, and horizontal/vertical flips.
- Avoid holding redundant full-size fixtures under the emulator's 192 MiB Java heap. Request GC between target groups, before warmups. An earlier fixture-heavy attempt failed commit validation and is retained under ignored `before-fixture-memory/`; its samples are excluded.
- Raw samples, memory snapshots, renderer-policy checks, APK/harness hashes and environment metadata: [Kotlin](commands-before.json), [Canvas](commands-candidate.json), [production](commands-after.json). APKs, logs and source snapshots are saved locally under ignored `paint/build/commands-benchmarks/`. The runner rejects a mismatched compiled policy or changed installed APK.

## Results

Complete commands change the conclusion for some targets: Canvas is about 1.20× faster for whole-canvas operations and 1.69× for large translucent floating selections, while raw full selections improve only about 1.09×. Kotlin remains faster for clipped and small selections. These aggregates are geometric means across both sizes and all five commands; the table also shows the worst individual case.

The current four-family production policy is unchanged. These results support a future target-aware policy for whole-canvas and large translucent edits; they do not support switching every rotation/flip to Canvas. Already-rotated selections use the affine Canvas path in production and benefit substantially. Selection timing includes preparation/Apply, so it cannot be inferred from the standalone sampler timings.

| Target | Canvas aggregate speedup over Kotlin | Worst case time change |
|---|---:|---:|
| canvas | 1.20x | -12.7% |
| selection | 1.09x | -2.2% |
| floatingAlpha | 1.69x | -34.6% |
| clipped | 0.91x | +36.4% |
| composed33 | 2.43x | -50.8% |
| small64 | 0.46x | +193.2% |

| Command case | Kotlin ms | Canvas ms | Production ms |
|---|---:|---:|---:|
| 1MP canvas rotate90 | 72.51 | 60.07 | 71.78 |
| 1MP canvas rotate-90 | 73.06 | 61.92 | 75.35 |
| 1MP canvas rotate180 | 73.69 | 58.18 | 74.36 |
| 1MP canvas flipH | 73.86 | 58.86 | 74.51 |
| 1MP canvas flipV | 70.94 | 60.06 | 75.67 |
| 1MP selection rotate90 | 81.82 | 72.97 | 82.05 |
| 1MP selection rotate-90 | 81.01 | 79.25 | 83.29 |
| 1MP selection rotate180 | 81.65 | 70.62 | 81.06 |
| 1MP selection flipH | 81.58 | 76.22 | 78.40 |
| 1MP selection flipV | 77.83 | 69.72 | 78.19 |
| 1MP floatingAlpha rotate90 | 110.44 | 65.37 | 101.04 |
| 1MP floatingAlpha rotate-90 | 104.60 | 68.41 | 103.07 |
| 1MP floatingAlpha rotate180 | 103.29 | 62.94 | 104.60 |
| 1MP floatingAlpha flipH | 105.31 | 63.19 | 104.68 |
| 1MP floatingAlpha flipV | 110.44 | 61.77 | 105.30 |
| 1MP clipped rotate90 | 60.87 | 61.94 | 60.59 |
| 1MP clipped rotate-90 | 63.61 | 62.55 | 59.74 |
| 1MP clipped rotate180 | 59.57 | 65.04 | 58.68 |
| 1MP clipped flipH | 59.37 | 66.58 | 60.76 |
| 1MP clipped flipV | 58.00 | 79.08 | 61.03 |
| 1MP composed33 rotate90 | 184.02 | 70.27 | 68.82 |
| 1MP composed33 rotate-90 | 153.97 | 75.70 | 67.88 |
| 1MP composed33 rotate180 | 153.02 | 71.87 | 68.96 |
| 1MP composed33 flipH | 151.74 | 65.43 | 67.62 |
| 1MP composed33 flipV | 153.03 | 67.17 | 67.56 |
| 1MP small64 rotate90 | 5.24 | 9.88 | 4.95 |
| 1MP small64 rotate-90 | 5.41 | 8.63 | 4.43 |
| 1MP small64 rotate180 | 5.72 | 9.33 | 5.28 |
| 1MP small64 flipH | 5.43 | 9.92 | 5.47 |
| 1MP small64 flipV | 4.93 | 10.44 | 5.18 |
| 4MP canvas rotate90 | 238.86 | 204.01 | 241.83 |
| 4MP canvas rotate-90 | 233.70 | 203.95 | 238.06 |
| 4MP canvas rotate180 | 235.75 | 193.56 | 232.10 |
| 4MP canvas flipH | 236.01 | 197.12 | 236.09 |
| 4MP canvas flipV | 232.79 | 190.24 | 236.20 |
| 4MP selection rotate90 | 281.07 | 269.82 | 283.05 |
| 4MP selection rotate-90 | 283.19 | 266.63 | 281.77 |
| 4MP selection rotate180 | 280.42 | 252.43 | 278.13 |
| 4MP selection flipH | 280.75 | 257.22 | 279.51 |
| 4MP selection flipV | 278.83 | 251.36 | 281.03 |
| 4MP floatingAlpha rotate90 | 374.28 | 226.10 | 379.34 |
| 4MP floatingAlpha rotate-90 | 382.46 | 229.96 | 381.54 |
| 4MP floatingAlpha rotate180 | 378.15 | 215.00 | 380.90 |
| 4MP floatingAlpha flipH | 375.84 | 213.96 | 377.27 |
| 4MP floatingAlpha flipV | 383.44 | 214.53 | 374.34 |
| 4MP clipped rotate90 | 184.59 | 208.17 | 189.57 |
| 4MP clipped rotate-90 | 187.77 | 207.33 | 188.77 |
| 4MP clipped rotate180 | 186.48 | 204.13 | 187.73 |
| 4MP clipped flipH | 185.88 | 201.08 | 188.82 |
| 4MP clipped flipV | 185.18 | 201.71 | 185.59 |
| 4MP composed33 rotate90 | 576.52 | 223.09 | 233.09 |
| 4MP composed33 rotate-90 | 567.35 | 221.64 | 223.26 |
| 4MP composed33 rotate180 | 572.62 | 218.09 | 220.31 |
| 4MP composed33 flipH | 564.76 | 220.51 | 219.25 |
| 4MP composed33 flipV | 585.79 | 220.51 | 226.92 |
| 4MP small64 rotate90 | 10.27 | 25.83 | 9.64 |
| 4MP small64 rotate-90 | 10.85 | 26.79 | 9.43 |
| 4MP small64 rotate180 | 9.33 | 27.36 | 9.87 |
| 4MP small64 flipH | 10.09 | 26.65 | 9.68 |
| 4MP small64 flipV | 9.36 | 26.53 | 9.96 |

| Stage | 4MP case | Cumulative managed MB | PSS before/after MB | Native heap before/after MB |
|---|---|---:|---:|---:|
| before | canvas rotate90 | 338.9 | 223.7/223.7 | 6.5/6.5 |
| before | selection rotate90 | 424.1 | 223.7/223.7 | 6.5/6.5 |
| before | floatingAlpha flipH | 339.4 | 237.3/237.3 | 6.5/6.5 |
| before | small64 flipV | 169.1 | 189.5/189.5 | 6.5/6.5 |
| before | composed33 rotate90 | 339.3 | 221.5/221.5 | 6.5/6.5 |
| candidate | canvas rotate90 | 338.9 | 221.7/221.7 | 6.4/6.4 |
| candidate | selection rotate90 | 423.6 | 221.7/221.7 | 6.4/6.4 |
| candidate | floatingAlpha flipH | 339.0 | 237.6/237.6 | 6.4/6.4 |
| candidate | small64 flipV | 169.1 | 189.9/189.9 | 6.5/6.5 |
| candidate | composed33 rotate90 | 339.1 | 221.8/221.8 | 6.4/6.4 |
| after | canvas rotate90 | 338.9 | 221.8/221.8 | 6.5/6.5 |
| after | selection rotate90 | 424.1 | 221.8/221.8 | 6.5/6.5 |
| after | floatingAlpha flipH | 339.1 | 237.8/237.8 | 6.5/6.5 |
| after | small64 flipV | 169.1 | 190.0/190.0 | 6.5/6.5 |
| after | composed33 rotate90 | 339.1 | 221.9/221.9 | 6.5/6.5 |

## Interpretation and reproduction

All nine accepted instrumentation runs pass: 60 cases × seven executions × nine runs = **3,780 committed commands**, each validated by exact undo restoration. The final production build, instrumentation build, Android lint and all 35 JVM tests also pass. No runtime backend policy change was made in this follow-up.

Managed allocation totals cover five measured commands plus their untimed setup, undo and validation. PSS/native heap figures are process-wide snapshots, not peaks. Timing variation includes GC, heap pressure and scheduler effects; these emulator/debug results do not predict ARM release performance. Opaque canvas fixtures and translucent floating selections represent different document workflows and should not be treated as interchangeable inputs.

Use `:paint:assembleDebug :paint:assembleDebugAndroidTest` for each variant, then `./paint/performance/run-canvas-benchmarks.ps1 -Suite commands -Stage before` (or `candidate` / `after`). Temporarily force `usesCanvas()` to false for the all-Kotlin build, true for the all-Canvas build, then restore the committed policy for production. The runner uses only verified local Small Desktop and preserves each stage's APKs. Archive an existing stage directory before measuring a different build under the same stage name. Regenerate tables with `python paint/performance/summarize-command-benchmarks.py`.
