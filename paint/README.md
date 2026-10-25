# KD Paint Classic core editor

An independently launchable Android app (`com.kkps.kdesktopsuite.paint`) with one
image open at a time. There are no document or ribbon tabs. File/View are menus;
the permanent top ribbon collapses commands in narrow windows. The canvas centers
each axis on which the image fits; larger images pan within the viewport.

The core supports Pencil, nine brush styles, Fill, Eraser, Eyedropper, rectangular
selection and clipboard operations, Line/Rectangle/Ellipse, native multiline text,
undo/redo, PNG/JPEG files, PNG sharing, settings, and durable private recovery.
The Hand tool pans with one finger; pinch zoom and two-finger pan remain available
with editing tools as well. Switching tools clears the selection and commits any
pending move or paste.
The Brushes dropdown shows actual stroke previews for Solid, Calligraphy left/right,
Airbrush, Oil, Crayon, Marker, Graphite, and Watercolor. Each style remembers its width;
seeded textures match the live preview and remain unchanged through undo/redo.
Modern desktop and the common mobile view remain deferred, as do the extended
tools listed in the [Android specification](../docs/KD_PAINT_ANDROID_SPEC.md).

Settings → On startup lets you reopen the last edited image or draft (the default),
start a new image at the default canvas size, or ask each time a previous session
exists. Android activity and process recreation resume the current session.
Default canvas dimensions save automatically when valid; Reset beside the inputs
restores 800 × 600. These defaults apply to new images only.

## Build and verification

Use Android Studio's JDK and the configured SDK:

```sh
./gradlew :paint:testDebugUnitTest :paint:assembleDebug :paint:lintDebug :paint:assembleDebugAndroidTest
ANDROID_SERIAL=emulator-5554 ./gradlew :paint:connectedDebugAndroidTest
```

The APK is `build/outputs/apk/debug/paint-debug.apk`. The first implementation was
verified on 24 October 2026 with 16 JVM tests and 17 instrumented tests on the API
37 desktop emulator. Lint reported no issues. The
[implementation screenshot](../docs/references/kd-paint/classic-implemented.png)
shows the actual expanded editor.

Tests cover image-space coverage, fill connectivity, masks and cropping,
region-based history and saved revisions, centered/bounded viewport transforms,
native text selection across recreation, glyph coverage between preview and
raster output, two-finger gesture cancellation, PNG/alpha/JPEG orientation,
provider conflicts, saves while editing, cancelled replacement, durable recovery
fallback, and compact/short Classic command access at large font sizes.

Physical stylus/palm behavior, TalkBack/Switch Access, predictive Back gestures,
freeform caption dragging, external displays, third-party document-provider
failure modes, and performance on release hardware still require manual checks.
Provider replacement is not universally atomic. Process death can lose edits
newer than the last completed checkpoint; Undo history resets after process restart.

## Ownership

- `PaintScreen` owns Classic chrome; `PaintCanvas` owns viewport input/rendering
  and native text overlay integration. Neither introduces a mobile presentation.
- `PaintViewModel` coordinates actions, lifecycle state, revisions, and pending
  commands; the reusable document/viewport logic is independent of Classic chrome.
- `Raster`/`RasterHistory` prepare pixel changes on a worker, then commit through
  the single writer. Published rasters are not mutated by render consumers.
- `ImageRepository` stages and validates content URI I/O; `PaintRecovery` writes
  PNG generations with atomic manifest switching; `PaintSettings` uses DataStore.
