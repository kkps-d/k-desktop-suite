# Testing

Configure the JDK and Android SDK as described in [Development](DEVELOPMENT.md).
Run commands from the repository root. On Windows PowerShell, replace
`./gradlew` with `.\gradlew.bat`.

## Local checks

Build and lint the affected module, compile its instrumented tests, and run its
JVM tests where available:

```sh
./gradlew :app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest
./gradlew :notepad:testDebugUnitTest :notepad:assembleDebug :notepad:lintDebug :notepad:assembleDebugAndroidTest
./gradlew :paint:testDebugUnitTest :paint:assembleDebug :paint:lintDebug :paint:assembleDebugAndroidTest
```

## Instrumented tests

**Run tests only on the local Small Desktop emulator. Never run tests on
connected ADB remote devices.** Start Small Desktop through Android Studio's
Device Manager if it is not already running.

Run `adb devices -l` and identify Small Desktop's local `emulator-…` serial.
Always set `ANDROID_SERIAL` explicitly to that serial before running connected
tests, especially when other devices are connected. The serial below is an
example; replace it with the one belonging to Small Desktop.

PowerShell:

```powershell
$env:ANDROID_SERIAL = "emulator-5554"
.\gradlew.bat :app:connectedDebugAndroidTest
.\gradlew.bat :notepad:connectedDebugAndroidTest
.\gradlew.bat :paint:connectedDebugAndroidTest
```

macOS/Linux:

```sh
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest
ANDROID_SERIAL=emulator-5554 ./gradlew :notepad:connectedDebugAndroidTest
ANDROID_SERIAL=emulator-5554 ./gradlew :paint:connectedDebugAndroidTest
```

## Coverage

### Starter

Instrumented tests cover compact and expanded windows, live resizing, state
restoration, activity recreation, and destination switching. Adaptive UI tests
force 400 × 800 dp and 1200 × 900 dp configurations on the selected emulator.
Compose previews cover phone, tablet, foldable, and desktop layouts.

### Notepad

Tests cover codecs, real content providers, document/tab state, recovery, adaptive
UI, and activity recreation. Compose previews cover phone, tablet, desktop, short
windows, and an extra-large dark window.

### Paint

Tests cover image-space coverage, fill connectivity, masks and cropping,
region-based history and saved revisions, centered/bounded viewport transforms,
native text selection across recreation, glyph coverage between preview and
raster output, two-finger gesture cancellation, PNG/alpha/JPEG orientation,
provider conflicts, saves while editing, cancelled replacement, durable recovery
fallback, and compact/short Classic command access at large font sizes.

The initial Paint implementation was verified on 24 October 2026 with 16 JVM
tests and 17 instrumented tests on the API 37 desktop emulator, with no lint
issues. This is historical evidence, not verification of subsequent changes.
The [earlier implementation reference](references/kd-paint/classic-implemented.png)
shows the former ribbon layout.

## Manual verification

Check the affected flows on the local Small Desktop emulator:

- Free-form resizing, maximize/restore, narrow and short windows, and split screen.
- Tab/Shift+Tab focus, arrow navigation, Enter activation, Escape dismissal,
  pointer selection, copy/paste, and right-click menus.
- System Back, light/dark appearance, large fonts, RTL, and software keyboard/IME.
- Activity recreation and recovery after editing; preserve active documents,
  selections, and scroll positions as appropriate.
- Notepad: tab reordering, edge scrolling, cancelled drops, transfers between
  windows, drag-out creation, final-tab transfers, caption dragging, zoom, file
  drops, and recovery after editing multiple windows.
- Paint: active tool access, preview/commit/cancel behavior, pan/zoom, caption
  clearance, focus mode, and unsaved replacement or provider-conflict decisions.

TalkBack/Switch Access, physical stylus/palm handling, trackpad pinch, predictive
Back gestures, cross-app drops, freeform window dragging, folds, external displays,
third-party document-provider failures, and release-hardware performance need
manual verification where the environment supports them. Record anything that
could not be verified; do not substitute automated tests on remote ADB devices.

The [Notepad tab specification](CHROME_ANDROID_TAB_SPEC.md) and
[Paint specification](KD_PAINT_ANDROID_SPEC.md) contain detailed acceptance checks.
