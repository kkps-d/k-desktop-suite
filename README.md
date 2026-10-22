# K Desktop Suite

A native Android starter for adaptive app development on phones, tablets,
foldables, and desktop windows, including Googlebook. Built with Kotlin,
Jetpack Compose, and Material 3 Adaptive.

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

## References

- [Adaptive app development](https://developer.android.com/develop/adaptive-apps/guides/get-started-with-adaptive-apps)
- [List/detail layouts](https://developer.android.com/develop/ui/compose/layouts/adaptive/list-detail)
- [Adaptive navigation](https://developer.android.com/develop/ui/compose/layouts/adaptive/build-adaptive-navigation)
- [Googlebook development](https://developer.android.com/develop/adaptive-apps/guides/googlebook/overview)
