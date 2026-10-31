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

For localization, also run `python scripts/check_localization.py` and
`./gradlew :core:ui:testDebugUnitTest`. The shared JVM tests exercise API 32 locale
application across activities and unavailable/restricted Android settings intents.
Each editor's `LanguageSettingsTest` covers language changes with retained drafts
or multiple windows. Check system settings and in-app selection in both directions,
Follow system, supported-language links, Burmese wrapping at large text sizes,
and independence between the two app preferences. Restore locale and display
settings after testing. Small Desktop's API 37.1 image does not verify actual
pre-13 emulator or manufacturer-specific ROM behavior.

Localization verification on 31 October 2026: both debug builds and instrumented
test APKs compiled; lint reported no errors (five existing Notepad warnings and
two existing Paint warnings). All 60 JVM tests and 35 focused instrumented tests
passed. The instrumented checks ran on local Small Desktop's regular desktop
user. The resource check covered 415 translated entries. Manual checks covered
the Android app-language settings shortcut, independent per-app preferences,
relaunch persistence, Burmese glyph rendering, and a 400dp window at 200% font
size. Burmese wording still needs fluent-speaker review.

**Run tests only on the local Small Desktop emulator. Never run tests on
connected ADB remote devices.** Start Small Desktop through Android Studio's
Device Manager if it is not already running.

Run `adb devices -l` and identify Small Desktop's local `emulator-…` serial.
Always set `ANDROID_SERIAL` explicitly to that serial before running connected
tests, especially when other devices are connected. The serial below is an
example; replace it with the one belonging to Small Desktop.

After a cold boot, wait for the regular desktop user to finish signing in before
starting tests. `adb -s <serial> shell am get-current-user` should identify that
user, rather than the internal System User. Locale changes do not behave like a
normal app session under the internal user.

Some Paint Compose fixtures request a 1200 × 800 dp surface. Give their host
window enough space; a smaller freeform window clips the fixture and can cause
false input, screenshot, or status-bar failures. A temporary larger display size
on Small Desktop can provide this space; restore the original display settings
afterward. Check real narrow and short windows separately using the adaptive
and window lifecycle tests and the manual checks below.

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
  Open multiple windows through File → New window, Ctrl+Shift+N, and system New
  window. Check independent undo, pending text/shapes, picker results, settings,
  and titles; opening an already-owned file must focus its owner without replacing
  the source draft. Check Ctrl+W Save / Discard / Cancel, failed saves, closing
  during edits, and system-close recovery. Recreate and restart multiple windows;
  restore each draft once, with the last focused window in front.

TalkBack/Switch Access, physical stylus/palm handling, trackpad pinch, predictive
Back gestures, cross-app drops, freeform window dragging, folds, external displays,
third-party document-provider failures, and release-hardware performance need
manual verification where the environment supports them. Record anything that
could not be verified; do not substitute automated tests on remote ADB devices.

### Tab and caption regressions

Apply these checks to each tabbed app using the [document tab guidance](TABS.md).

- Exercise 1, 2, 5, and 20 tabs, with long/duplicate/Unicode titles and unsaved,
  saving, or error states. Check equal compression before scrolling, reachable
  New/Close actions, selected-tab visibility, and the compact document selector.
- Check caption integration and fallback with obstructed, missing, narrow, or
  short geometry; include large fonts, RTL, density changes, and live resizing.
  System window controls and blank dragging space must remain usable.
- Check keyboard focus separately from selection, inactive-tab closing, active
  neighbor selection, and the app's empty-window behavior after the final tab
  closes. Notepad returns to Welcome.
- Verify cancelled reorder/transfer operations leave documents intact. Successful
  transfers preserve text, selection, scroll, and undo/redo; process recovery
  restores retained documents without duplication or overwriting their files.

Current Notepad coverage lives in
[DesktopCaptionTest](../notepad/src/test/java/com/kkps/kdesktopsuite/notepad/DesktopCaptionTest.kt),
[TabReorderingTest](../notepad/src/test/java/com/kkps/kdesktopsuite/notepad/TabReorderingTest.kt),
[CaptionInputTest](../notepad/src/androidTest/java/com/kkps/kdesktopsuite/notepad/CaptionInputTest.kt),
[TabWorkspaceTest](../notepad/src/androidTest/java/com/kkps/kdesktopsuite/notepad/TabWorkspaceTest.kt),
and [TabWindowTest](../notepad/src/androidTest/java/com/kkps/kdesktopsuite/notepad/TabWindowTest.kt).
These identify existing checks; they are not claims of complete manual coverage.

### Paint document safety regressions

- Check one undoable change per committed stroke, shape, text, or transform;
  cancellation and no-op operations preserve pixels, dimensions, and dirty state.
- Verify image-space coverage at different zooms/densities, stable brush textures,
  selection alpha compositing, and matching text preview/export metrics.
- Exercise editing during a save, failed/truncated provider writes, external
  conflicts, cancelled replacement, and interrupted/corrupt recovery generations.
  Preserve private drafts and keep newer edits unsaved when an older revision saves.
- Verify picker cancellation/recreation, PNG round trips, EXIF-oriented JPEG,
  readable share/clipboard URIs, and layout changes with pending edits.
- `PaintWindowSessionTest` covers per-window recovery and pending text, close/save
  decisions, delayed checkpoints after close, URI reservations, failed loads,
  Save as conflicts, legacy migration, startup choices, corrupt-entry isolation,
  simultaneous settings updates, and opening another window while a PNG
  checkpoint is blocked. `PaintWindowTest` exercises actual separate
  tasks, File/New window and keyboard commands, recreation with undo, duplicate
  launches, system-style new instances, and focusing an already-owned image.

See the [development constraints](DEVELOPMENT.md) for the behavior these checks
protect. Add acceptance checks for a deferred feature when its implementation is
scoped; the [roadmap](ROADMAP.md) is not a claim of delivered functionality.
