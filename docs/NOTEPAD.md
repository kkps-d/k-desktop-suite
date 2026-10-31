# KD Notepad

KD Notepad is a plain-text editor with document tabs, File/Edit/View menus,
a quiet status bar, and support for touch, keyboard, mouse, and trackpad.
It uses a Windows-inspired layout with native Material 3 Expressive controls.
Run the `notepad` configuration in Android Studio; see
[Development](DEVELOPMENT.md) for setup and builds.

## Layout and appearance

In supported Android 15 / API 35 and newer desktop windows, tabs share the
system title bar while leaving window controls and dragging space available.
When there is insufficient room, tabs move below the title bar. Very narrow
windows use a document selector with full titles and individual close actions,
with New alongside it. Overflowing tab strips scroll, and the selected tab stays
visible after selection or resizing.

Narrow or short windows consolidate commands into an overflow menu. Arrow keys
move focus between tabs; Enter or Space selects the focused tab.

Settings opens a page with Light / Dark / Auto appearance and Monospace / Serif /
Sans-serif note fonts, with a live preview. Changes apply immediately and persist
across relaunch; Auto follows system appearance. Dynamic color is used where
available, with a green fallback palette. Back, system Back, and Escape return to
the active draft.

Help > About KD Notepad shows the app icon, version, developer, source repository,
and open-source licenses. Compact windows put About at the bottom of Commands.
Dismiss, system Back, and Escape return to the active draft.

View contains zoom commands and checked toggles for word wrap and the status
bar. Turning wrapping off preserves line breaks and enables independent horizontal
scrolling in each document. These preferences persist across relaunch.

## Documents and recovery

- New, Open, Save, Save As, Close, independent undo/redo, and standard clipboard
  operations are available through menus and keyboard shortcuts.
- Each tab retains its text, selection, scroll position, file association, and
  undo history across switches, resizing, and activity recreation.
- With no documents open, a Welcome tab shows the app logo, New and Open file
  actions, and up to five recent saved text files. Start and Recent appear side
  by side in wide windows and stack below 600dp. New or Open replaces Welcome;
  closing the last document brings it back. Recent files persist across launches,
  ordered by the latest successful open or save, and exclude unsaved drafts.
- Drag a tab with a mouse or trackpad to reorder it; touch uses a long press.
  Neighboring tabs slide aside to preview the position. Releasing commits the
  order; Escape or dropping over the toolbar cancels it. Overflowing strips
  scroll when the pointer approaches an edge. Motion follows system animation settings.
- Move a tab between Notepad windows by dropping it onto the destination tab bar.
  The strip expands during dragging to make room, including in a single-tab window.
  Drop it onto the compact document selector to append it. The live document,
  including undo/redo history, moves with it. A window emptied by a transfer closes;
  ordinary Close on the final tab returns to Welcome.
- On Android 15 / API 35 and newer, dropping a tab onto empty desktop space can
  create a separate window when the system permits it. In desktop or split-screen
  mode, dropping below the source window's tabs and toolbar also opens a new window;
  dropping on another window's editor cancels. A window's only tab stays
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
  remain, the Welcome tab opens. Recovery never overwrites
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
size, retaining the system font scale. Zoom commands remain available in View or
the compact Commands menu.

## Current limitations

Find/Replace, rich text, legacy encodings, and large-file optimizations are
deferred.

For implementation details, see the [tab specification](CHROME_ANDROID_TAB_SPEC.md).
Build and verification instructions are in [Development](DEVELOPMENT.md) and
[Testing](TESTING.md).
