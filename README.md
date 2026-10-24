# K Desktop Suite

A native Android starter for adaptive app development on phones, tablets,
foldables, and desktop windows, including Googlebook. Built with Kotlin,
Jetpack Compose, and Material 3 Adaptive.

Apps use **KD [App]** branding, where **KD** means **K Desktop**: **KD Notepad**
and **KD Paint**. The included starter launches as **KD Suite**.

## Open and run

1. Open this directory in Android Studio Rabbit 2 RC 1 or newer and let Gradle sync.
2. In SDK Manager, install **Android 17 / API 37**, **Build Tools 36.0.0**, and
   **Android Emulator**. The desktop **API 37.1 system image** is a separate
   emulator package; it does not require changing the app's compile SDK.
3. Select your API 37.1 desktop virtual device and run the `app` configuration.
4. Open a starter document and resize the app window. At expanded sizes, the list
   and editor appear together; at compact sizes, use Back to return to the list.

The app compiles and targets API 37 and supports Android 8.0 / API 26 onward.
It has no fixed orientation or aspect ratio and does not require a touchscreen.
It uses standard Android APIs rather than detecting Googlebook specifically.

## What's included

- Material 3 navigation that switches between a bottom bar and a navigation rail
  based on window space and device posture.
- A navigable list/detail scaffold with fold-aware panes and predictive Back.
- An editable sample workspace, with selection and draft state restored across
  activity recreation and destination changes.
- System light/dark themes, edge-to-edge insets, and keyboard/IME handling.
- Focusable controls, mouse text selection, and standard text editing shortcuts.
- Phone, tablet, foldable, and desktop Compose previews in `KDesktopSuiteApp.kt`.
- Instrumented tests for compact and expanded windows, live resizing, state
  restoration, activity recreation, and destination switching.
- A pinned Gradle wrapper with a distribution SHA-256 and a dependency version catalog.

**The documents are sample data. Drafts use `SavedStateHandle` for transient
restoration, not durable storage.** They are not guaranteed to survive closing the
task or force-stopping the app. Add a repository backed by Room or files before
using the editor for real documents; keep large document contents out of saved
instance state.

## Command-line build

Use Android Studio's bundled JDK (or a compatible JDK 17+ supported by Gradle).
On macOS with this installation:

```sh
export JAVA_HOME="/Applications/Android Studio RC.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew :app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest
```

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`.
`local.properties`, IDE settings, SDKs, and build outputs are excluded from Git.
Android Studio creates `local.properties` when opening the project; on another
machine, set `ANDROID_HOME` or provide your own `sdk.dir` there.

The toolchain uses AGP 9.3.3, Gradle 9.5.1, Kotlin/Compose Compiler 2.4.20,
Compose BOM 2026.09.00, and Material 3 Adaptive 1.3.0. AGP's built-in Kotlin
support is enabled; there is no separate Kotlin Android plugin.

Espresso is explicitly pinned to 3.7.0 for Android 17 input-injection compatibility,
instead of relying on Compose's older transitive test dependency.

## Test on a device

Start an emulator or connect a device, then run:

```sh
./gradlew :app:connectedDebugAndroidTest
```

With multiple devices connected, select one explicitly:

```sh
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest
```

The adaptive UI tests force 400 × 800 dp and 1200 × 900 dp configurations, so
both layout modes are exercised on the selected emulator. Also manually check:

- Free-form resizing, maximization, and narrow/short desktop windows.
- Tab/Shift+Tab focus, Enter activation, pointer selection, and copy/paste.
- System Back in a compact editor, then with list and detail side by side.
- Light/dark appearance, large font sizes, RTL, and the software keyboard.
- Rotation and activity recreation while a document contains edits.

## Project layout

```text
app/src/main/java/com/kkps/kdesktopsuite/
  MainActivity.kt             Activity and ViewModel wiring
  ui/KDesktopSuiteApp.kt      Adaptive navigation, guide, and previews
  ui/theme/Theme.kt           Light and dark Material 3 themes
  workspace/WorkspaceScreen.kt     Adaptive list/detail UI
  workspace/WorkspaceViewModel.kt  Sample data and transient draft state
app/src/androidTest/          Adaptive layout and lifecycle tests
gradle/libs.versions.toml     Dependency and plugin versions
```

Use the workspace as the first feature module's starting point. Add durable
storage and domain logic behind the ViewModel as real tools are introduced.

## KD Notepad

KD Notepad is a separate Android application in the `:notepad` module. Select the
`notepad` run configuration in Android Studio to install it alongside KD Suite.
Its application ID is `com.kkps.kdesktopsuite.notepad`; the existing starter and
its application ID are unchanged. Theme and tab code stay inside Notepad until
KD Paint establishes what should be shared.

Notepad uses a Windows-inspired document layout with Material 3 Expressive:
scrollable document tabs above File/Edit/View menus, a monospace editor, and a quiet
status bar. Narrow or short windows consolidate commands into an overflow menu.
It follows system appearance and dynamic color, with a green fallback palette.

On Android 15 / API 35 and newer desktop windows, document tabs share the system
title bar using the actual system caption height and control exclusion regions.
The decorative top inset shrinks when needed, and tab controls fit the caption
instead of forcing an extra row below it. Large text that cannot fit still uses
the standalone row.
The integrated strip reserves 8dp for window dragging and requires at least 176dp
of unobstructed width for a tab, New, and that clearance. Otherwise tabs move below
the caption. Caption-free windows and older Android versions use a standalone row.
If that row is narrower than 168dp, an active-document selector opens a scrollable
list with full titles, document status, selection, and individual close actions.
New remains alongside the selector.

Tab slots overlap by 16dp and compress equally from 240dp to 120dp before scrolling.
New follows the last tab until the strip overflows, then stays at the viewport edge.
The selected tab stays visible after selection or resizing. Titles use regular
14sp text with 20sp line height and a 22dp leading inset. Close and New
use vector icons with smaller circular feedback; standalone targets are 48dp,
while caption controls use its available height. Dirty/save/error
indicators add leading space only while shown. The selected tab's outward feet join the neutral
File/Edit surface; separators appear only between inactive tabs and after a final
inactive tab. Inactive hover matches the selected tab's body width with a 4dp bottom
gap; keyboard focus is distinct from selection. Arrow keys move tab focus, and
Enter/Space activates a tab. Tabs have no tooltips; icon tooltips wait 500ms.
Outward feet retain their 12dp radius; overlap keeps the visible gap between tab bodies at 8dp.
The 48dp toolbar has smaller inset controls and a trailing Settings cogwheel that
opens a settings page with a Back button. Settings includes Light / Dark / Auto
appearance and Monospace / Serif / Sans-serif note fonts, with a live preview.
Changes apply immediately and are restored on relaunch; Auto follows system
appearance. System Back and Escape also return to the active draft. Settings uses
Material 3 Expressive connected theme buttons and stacks choices for narrow
windows or large fonts. A subtle divider separates the toolbar from the content area.
Help > About KD Notepad opens a dialog with the app icon, version, developer, and
source repository link. Compact windows place About at the bottom of Commands.
Dismiss, system Back, and Escape return to the active draft.
The shorter status bar matches the tab strip and has no top border.
Dropdown items are at least 44dp high, with no top or bottom popup padding.
View contains all zoom commands and checked toggles for word wrap and the status
bar. Wrapping off preserves line breaks and enables per-document horizontal
scrolling. These view preferences are restored on relaunch.

See the [tab implementation specification](docs/CHROME_ANDROID_TAB_SPEC.md) for
layout rules and acceptance checks.

### Documents and recovery

- New, Open, Save, Save As, Close, independent undo/redo, and standard clipboard
  operations are available through menus and keyboard shortcuts.
- Each tab retains its text, selection, scroll position, file association, and
  undo history across switches, resizing, and activity recreation.
- Drag a tab with a mouse or trackpad to reorder it; touch uses a long press.
  Neighboring tabs slide aside to preview the position. Releasing commits the
  order; Escape or dropping over the editor cancels it. Overflowing strips
  scroll when the pointer approaches an edge. Motion follows system animation settings.
- Move a tab between Notepad windows by dropping it onto the destination tab bar.
  Drop it onto the compact document selector to append it. The live document,
  including undo/redo history, moves with it. A window emptied by a transfer closes;
  ordinary Close on the final tab still opens a fresh untitled document.
- On Android 15 / API 35 and newer, dropping a tab onto empty desktop space can
  create a separate window when the system permits it. A window's only tab stays
  in its current window when dropped outside. Right-click a tab for Move left,
  Move right, or Move to window; these commands also appear as accessibility
  actions and in the compact document list. New window is available in desktop
  or split-screen mode, including on older supported Android versions.
- Closing unsaved tabs offers Save / Discard / Cancel. Opening an already-open
  file selects its existing tab. A cancelled or failed save keeps the draft open.
- Open and Save As use Android's document picker without broad storage access.
  Files must be UTF-8 plain text, optionally with a BOM. LF/CRLF conventions are
  retained; mixed line endings normalize to CRLF if any CRLF is present. New files
  use UTF-8 without a BOM and LF. Invalid UTF-8 and NUL-containing files are rejected.
- Private recovery snapshots restore documents, tab order, the active tab,
  selection, vertical/horizontal scroll positions, zoom, and view preferences on
  relaunch. Empty tabs are discarded at startup; whitespace counts as content.
  If the active tab was empty, the first retained document is selected; if none
  remain, one fresh untitled document opens. Recovery never overwrites
  original files. Snapshots follow 500 ms of inactivity and flush on backgrounding;
  an abrupt termination can lose edits newer than the last successful checkpoint.
  Undo history resets after process restart. Uninstalling removes private recovery.
- All windows share one recovery writer and appearance/view preferences. After
  process restart, documents merge into one window in window-creation order,
  preserving each window's tab order and the last-focused active document.
  Opening an already-open file brings its existing window forward.
- Missing file access preserves recovered contents and offers Save As. External
  file changes require Overwrite / Save As / Cancel before saving over them.
- Mouse/trackpad input supports context menus, scrolling, hover, editor zoom, and
  opening one dropped text file. Native text drops insert into the editor. When a
  provider grants only temporary access, a later Save may require Save As.

| Action | Shortcut |
| --- | --- |
| New / Open | Ctrl+N / Ctrl+O |
| Save / Save As | Ctrl+S / Ctrl+Shift+S |
| Close tab | Ctrl+W |
| Next / previous tab | Ctrl+Tab / Ctrl+Shift+Tab |
| Undo / redo | Ctrl+Z / Ctrl+Y or Ctrl+Shift+Z |
| Cut / copy / paste / select all | Ctrl+X / C / V / A |
| Zoom in / out / reset | Ctrl+Plus / Ctrl+Minus / Ctrl+0 |

Editor zoom also supports Ctrl+wheel and pinch input. It changes document text
size, retaining the system font scale. Accessible zoom commands remain available
in the Edit/overflow menu.

### Build and test Notepad

With the JDK and Android SDK configured as above:

```sh
./gradlew :notepad:testDebugUnitTest :notepad:assembleDebug :notepad:lintDebug :notepad:assembleDebugAndroidTest
ANDROID_SERIAL=emulator-5554 ./gradlew :notepad:connectedDebugAndroidTest
```

The APK is `notepad/build/outputs/apk/debug/notepad-debug.apk`. Codec unit tests,
real content-provider tests, document/tab state tests, recovery tests, adaptive UI
tests, and an activity recreation test live in the Notepad module. Compose previews
cover phone, tablet, desktop, short windows, and an extra-large dark window.

Manually verify TalkBack, keyboard-only operation, large fonts, RTL, right-click
menus, trackpad pinch, drops from another app, caption/window dragging,
maximize/restore, fold changes, split screen, and external displays on suitable
devices. Also check animated tab reordering, edge scrolling, cancelled drops,
dragging between two windows, drag-out creation, final-tab transfers, and recovery
after editing both windows. Find/Replace, rich text, legacy
encodings, and large-file optimizations are deferred.

## References

- [Adaptive app development](https://developer.android.com/develop/adaptive-apps/guides/get-started-with-adaptive-apps)
- [List/detail layouts](https://developer.android.com/develop/ui/compose/layouts/adaptive/list-detail)
- [Adaptive navigation](https://developer.android.com/develop/ui/compose/layouts/adaptive/build-adaptive-navigation)
- [Googlebook development](https://developer.android.com/develop/adaptive-apps/guides/googlebook/overview)
