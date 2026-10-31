# K Desktop Suite

Native Android productivity apps for phones, tablets, foldables, and desktop
windows, including Googlebook. Built with Kotlin, Jetpack Compose, and Material 3.
**KD** means **K Desktop**; each app launches independently.

## Apps

- **[KD Notepad](docs/NOTEPAD.md)** — A plain-text editor with document tabs,
  multiple windows, keyboard shortcuts, appearance and font settings, and draft
  recovery.
- **[KD Paint](docs/PAINT.md)** — A drawing and image editor with brushes, shapes,
  selections, text, image transforms, PNG/JPEG files, multiple windows, and draft recovery.

The apps support Android 8.0 / API 26 and newer, touch, keyboard, mouse, and
trackpad. Layouts adapt as windows resize, with system light/dark appearance and
no fixed orientation or aspect ratio.

## Screenshots

> **KD Notepad — screenshot placeholder:** A desktop window with several document
> tabs, File/Edit/View menus, an active text document, and the status bar visible.

> **KD Paint — screenshot placeholder:** A desktop window with both drawing
> toolbars, color controls, and a canvas containing brush strokes, shapes, and text.

## Open and run

1. Open the repository in Android Studio Rabbit 2 RC 1 or newer and let Gradle sync.
2. Install Android 17 / API 37, Build Tools 36.0.0, and Android Emulator through
   SDK Manager. Use the separate API 37.1 desktop system image for desktop testing.
3. Start the local **Small Desktop** emulator, select the `notepad` or `paint`
   run configuration, and run.

See [Development](docs/DEVELOPMENT.md) for command-line builds and SDK setup.

## Files and recovery

Notepad opens UTF-8 plain-text files; Paint opens and saves PNG/JPEG images.
Both use Android's file picker and keep private recovery drafts. Save important
work explicitly: abrupt termination can lose changes since the last checkpoint,
undo history resets after a process restart, and uninstalling removes private
recovery data. Paint flattens opened transparent images onto white.

## Documentation

- [Notepad guide and shortcuts](docs/NOTEPAD.md)
- [Paint guide](docs/PAINT.md)
- [All documentation](docs/README.md), including development, testing, and design specs

## License

Original K Desktop Suite code is licensed under the [MIT License](LICENSE).
Third-party libraries, icons, and fonts retain their respective licenses.
Both apps provide bundled notices under **About > Open-source licenses**.
