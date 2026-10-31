# Development

Follow the [contributor instructions](CONTRIBUTING.md) and
[design guidelines](DESIGN_GUIDELINES.md) when making changes.

## Setup

1. Open the repository in Android Studio Rabbit 2 RC 1 or newer and let Gradle sync.
2. In SDK Manager, install Android 17 / API 37, Build Tools 36.0.0, and Android
   Emulator. The desktop API 37.1 system image is a separate emulator package;
   it does not require changing the app's compile SDK.
3. Create or start the **Small Desktop** local virtual device with the desktop
   image. Select the `app`, `notepad`, or `paint` run configuration.

All three apps compile and target API 37 and support Android 8.0 / API 26 onward.
They use standard Android APIs, with no fixed orientation or aspect ratio and no
touchscreen requirement or Googlebook-specific detection.

Use Android Studio's bundled JDK, or a compatible JDK 17+ supported by Gradle.
Android Studio creates `local.properties` when opening the project. On another
machine, set `ANDROID_HOME` or provide your own `sdk.dir` there. Local properties,
IDE settings, SDKs, and build outputs are excluded from Git.

For example, on macOS with this Android Studio installation:

```sh
export JAVA_HOME="/Applications/Android Studio RC.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
```

## Build

Run commands from the repository root. On Windows PowerShell, replace
`./gradlew` with `.\gradlew.bat`.

```sh
./gradlew :app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest
./gradlew :notepad:assembleDebug :notepad:lintDebug :notepad:assembleDebugAndroidTest
./gradlew :paint:assembleDebug :paint:lintDebug :paint:assembleDebugAndroidTest
```

| App | Module | Application ID | Debug APK |
| --- | --- | --- | --- |
| KD Suite starter | `:app` | `com.kkps.kdesktopsuite` | `app/build/outputs/apk/debug/app-debug.apk` |
| KD Notepad | `:notepad` | `com.kkps.kdesktopsuite.notepad` | `notepad/build/outputs/apk/debug/notepad-debug.apk` |
| KD Paint | `:paint` | `com.kkps.kdesktopsuite.paint` | `paint/build/outputs/apk/debug/paint-debug.apk` |

The apps install independently. Paint includes a shared Android Studio
[launch configuration](../.run/paint.run.xml). If a module is missing from the
run configurations, use **File > Sync Project with Gradle Files** first.

See [Testing](TESTING.md) for JVM tests, instrumented tests, and manual checks.

## Toolchain

Versions are maintained in [the version catalog](../gradle/libs.versions.toml)
and [the Gradle wrapper configuration](../gradle/wrapper/gradle-wrapper.properties):
AGP 9.3.3, Gradle 9.5.1, Kotlin/Compose Compiler 2.4.20, Compose BOM 2026.09.00,
and Material 3 Adaptive 1.3.0. AGP's built-in Kotlin support is enabled; there is
no separate Kotlin Android plugin. The wrapper pins its distribution SHA-256.

Espresso is explicitly pinned to 3.7.0 for Android 17 input-injection
compatibility, instead of relying on Compose's older transitive test dependency.

## Project layout

```text
app/                         KD Suite adaptive starter
  src/main/java/com/kkps/kdesktopsuite/
    MainActivity.kt          Activity and ViewModel wiring
    ui/KDesktopSuiteApp.kt   Adaptive navigation, guide, and previews
    ui/theme/Theme.kt        Light and dark Material 3 themes
    workspace/              Sample list/detail UI and transient drafts
notepad/                     Notepad editor, documents, tabs, and recovery
paint/                       Paint editor, raster operations, and recovery
core/ui/                     Shared theme, fonts, menus, settings, and caption support
docs/                        Usage guides, contributor instructions, and specs
gradle/libs.versions.toml    Dependency and plugin versions
```

Keep document and editing behavior in the owning app. Share platform-independent
logic where useful and keep Android adapters in their appropriate modules.
Extract shared UI when both apps demonstrate reuse.

## Shared desktop UI constraints

- Use the actual caption height, system control bounds, cutouts, and content
  origin when integrating controls into a desktop title bar. Geometry is in
  physical window pixels; RTL must not mirror system control positions.
- Integrate only when controls and scaled text fit an unobstructed span. Keep
  window buttons clear and reserve blank space for system dragging. Fall back
  below the caption when geometry is missing, blocked, too short, or too narrow.
- Exclude app controls from caption dragging only within their visible, clipped
  bounds. Excluding the whole strip would prevent system window dragging.
  Restore prior gesture exclusions when the controls leave composition.
- Recompute on resizing, maximize/restore, display/density changes, and font
  scaling. Layout changes must preserve the active document, edits, selection,
  scroll position, and focus.
- Keep caption geometry and input handling in the shared
  [DesktopCaption implementation](../core/ui/src/main/java/com/kkps/kdesktopsuite/ui/DesktopCaption.kt).
  Each app owns the content, minimum space, and fallback rules for its title bar.

Apps that support document tabs should follow the shared
[document tab guidance](TABS.md) for layout, input, state, and window transfers.

## KD Suite starter

The starter provides:

- Material 3 navigation that switches between a bottom bar and a navigation rail
  based on window space and device posture.
- A navigable list/detail scaffold with fold-aware panes and predictive Back.
- An editable sample workspace, with selection and draft state restored across
  activity recreation and destination changes.
- System light/dark themes, edge-to-edge insets, and keyboard/IME handling.
- Focusable controls, mouse text selection, and standard text editing shortcuts.
- Phone, tablet, foldable, and desktop Compose previews in `KDesktopSuiteApp.kt`.

Open a sample document and resize the window. Expanded windows show the list and
editor together; compact windows use Back to return to the list.

**Starter documents are sample data.** Drafts use `SavedStateHandle` for transient
restoration and may not survive closing the task or force-stopping the app. Add
a repository backed by Room or files and domain logic behind the ViewModel before
using the workspace for real documents. Keep large document bodies out of saved
instance state.

## Paint implementation ownership

- `PaintScreen` owns Classic chrome; `PaintCanvas` owns viewport input/rendering
  and native text overlay integration. Neither introduces a mobile presentation.
- `PaintViewModel` coordinates actions, lifecycle state, revisions, and pending
  commands; reusable document/viewport logic is independent of Classic chrome.
- `Raster`/`RasterHistory` prepare pixel changes on a worker, then commit through
  the single writer. Published rasters are not mutated by render consumers.
- `ImageRepository` stages and validates content URI I/O; `PaintRecovery` writes
  PNG generations with atomic manifest switching; `PaintSettings` uses DataStore.

### Paint editing constraints

- Viewport zoom, density, and app appearance do not alter stored image pixels.
  Keep canvas bounds separate from image resampling and preview transforms.
- Treat an edit as one transaction: cancellation restores its original state,
  and applying creates one undoable change. Navigation and color selection
  preserve pending edits; changing drawing tools follows the documented commit
  behavior.
- Serialize raster mutations and publish completed results. Asynchronous work
  must not overwrite a newer edit or a different document. A save marks only the
  revision actually encoded as saved, preserving any newer unsaved work.
- Stage provider I/O and keep a private recovery copy when saving fails. Provider
  replacement is not universally atomic. Recovery uses completed generations,
  preserves preview cancellation, and never overwrites the external file.

See the [Paint guide](PAINT.md) for current behavior and limitations and the
[roadmap](ROADMAP.md) for deferred features. Use the existing implementation and
tests to determine exact dimensions, supported operations, and defaults.

## Licenses and notices

Original code is licensed under the [MIT License](../LICENSE). Third-party
libraries, icons, and fonts retain their respective licenses. Update dependency
notices when changing release dependencies; the per-app
[Notepad notices](../notepad/THIRD_PARTY_NOTICES.md) and
[Paint notices](../paint/THIRD_PARTY_NOTICES.md) describe the bundled assets.
Keep [the bundled suite license](../core/ui/src/main/assets/licenses/KDesktopSuite-MIT.txt)
in sync with the root license. Both apps expose bundled notices under
**About > Open-source licenses**.
