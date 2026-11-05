# KD Explorer

KD Explorer currently provides a blank tabbed workspace for validating the UI
and independent Android windows. File browsing and storage access are not
implemented. It requests no storage permissions and displays no sample files.

## Tabs and windows

- Launch with one blank tab. Use **New tab** (+) or the window menu (⋮) to add
  tabs. Each tab has a stable, numbered title and an individual Close button.
- Tabs compress equally before scrolling. The selected tab scrolls into view;
  very narrow windows or large text switch to a tab selector with selection and
  individual Close actions.
- Closing an inactive tab keeps the active tab. Closing the active tab selects
  the right neighbor, falling back to the left. Closing the final tab closes
  that window.
- Use **Window menu > New window** to open an independent Android task with its
  own tabs. The system New window command is also supported where available.
  Close window removes only the current task.
- Tabs share the native system caption when a readable tab, New, and menu fit.
  They use the actual system control bounds with only an 8dp trailing drag gap;
  unused header space also remains draggable. Narrow windows, large text, or
  unavailable caption geometry put tabs below it. System dragging and window
  controls remain available. The workspace follows system appearance and
  dynamic color, with the suite's green fallback on older Android versions.

Tab order, active tab, and numbering survive resizing and activity recreation,
including Android's saved-task restoration. There is no private on-disk session
recovery after task removal or force stop. Each new window starts with one tab.
Drag reordering, moving tabs between windows, and browsing controls are future
work. The blank workspace intentionally has no editor, location pane, or toolbar
for file operations.

## Keyboard

| Action | Shortcut |
| --- | --- |
| New tab | Ctrl+T |
| Close current tab | Ctrl+W |
| New window | Ctrl+N or Ctrl+Shift+N |
| Close window | Ctrl+Shift+W |
| Next / previous tab | Ctrl+Tab / Ctrl+Shift+Tab |
| Select tab 1–8 / last tab | Ctrl+1–8 / Ctrl+9 |
| Move focus among tabs without selecting | Left/Right, Home/End |
| Activate focused control | Enter/Space |

Use Tab/Shift+Tab to reach tab, Close, New, and menu controls. Escape dismisses
menus. Menu commands display their primary shortcuts; hover shows button labels.

## Development

Run the `explorer` Android Studio configuration after Gradle sync, or build
`:explorer:assembleDebug`. The app ID is `com.kkps.kdesktopsuite.explorer` and
the minimum Android version is API 26, matching the suite.

See [Testing](TESTING.md#explorer-shell) for checks. The older
[browsing specification](KD_EXPLORER_SPEC.md) describes planned functionality;
this guide describes the current shell. The shell-first milestone introduces
tabs and multiple windows ahead of the original proposal.
