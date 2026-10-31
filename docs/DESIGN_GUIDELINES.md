# K Desktop Suite design guidelines

## Direction

Build focused Android productivity apps for phones, tablets, foldables, and desktop windows. Each app launches independently. Use familiar Windows-inspired document workflows with native Jetpack Compose and Material 3 Expressive styling.

Name apps **KD [App]**, where **KD** means **K Desktop**: **KD Notepad** (K Desktop Notepad) and **KD Paint** (K Desktop Paint). Use the short name consistently in launcher labels, window titles, and user-facing text.

## Appearance

- Use Material theme tokens for color, typography, shapes, and spacing. Follow system light/dark appearance and dynamic color where available, with a green fallback palette.
- Keep editors and canvases visually quiet. Apply expressive shapes and restrained motion to controls; use clear separators and minimal elevation.
- Use system typography for controls and a readable monospace face for plain-text editing. Use `dp` for layout and `sp` for text; respect font scaling without clipping.

## Adaptive layout

- Base layout on the current window's width, height, and folding posture using Material 3 Adaptive. Recompute during resizing; avoid device-name checks, orientation locks, and fixed aspect ratios.
- Prioritize the document area. KD Notepad uses tabs above File/Edit menus, a full-size editor, and a quiet status bar. Consolidate commands into overflow menus in narrow or short windows without losing functionality.
- Add supporting panes when space permits; collapse them into navigable destinations or panels when constrained. Use adaptive navigation only when the app has multiple destinations.
- Let editors and canvases use available space; constrain reading panels and dialogs to comfortable widths. Keep tabs scrollable, the selected tab visible, and New reachable.
- Handle system bars, desktop caption controls, cutouts, hinges, and IME insets. Keep controls and the caret visible; preserve Android Back behavior and system window dragging.

## Input and accessibility

- Support touch, keyboard, mouse, and trackpad. Provide logical Tab/Shift+Tab and arrow-key navigation, visible focus, Enter/Space activation, and Escape dismissal without trapping focus.
- Provide standard editing, file, and tab shortcuts; show them in menus and route them through the same commands. Preserve native text selection, clipboard, Unicode, and IME behavior.
- Provide hover feedback, tooltips, right-click context menus, and wheel scrolling. Add drag-and-drop, content zoom, and stylus support where relevant; retain accessible command alternatives.
- Use labeled controls, screen-reader semantics, distinct selected/disabled states, and at least 48dp touch targets. Maintain 4.5:1 normal-text contrast, avoid color-only indicators, support RTL, and respect reduced motion.

## Documents and tabs

- Each tab owns its document, file association, selection, scroll position, and undo history. Preserve working state across tab switches, resizing, and activity recreation; keep focus predictable.
- Show document titles, unsaved indicators, and save/error states. Closing or replacing unsaved content offers Save / Discard / Cancel; failed or cancelled saves preserve the document.
- Recover drafts and the tab session from durable private storage on relaunch. Recovery must not overwrite original files; keep document bodies out of saved instance state and perform file I/O off the UI thread.
- Establish behavior in KD Notepad first; extract shared theme, controls, and tab behavior when KD Paint demonstrates reuse. Keep editor-specific behavior inside each app.

## Verification

- Check compact through extra-large widths, short windows, live resizing, maximize/restore, split screen, external displays, and fold changes. Verify drafts, selection, scroll, and active tabs remain intact.
- Exercise keyboard-only operation, pointer input, software keyboard, large fonts, RTL, TalkBack, light/dark themes, activity recreation, and process recovery. Use previews plus emulator/device checks.

References: [Window size classes](https://developer.android.com/develop/ui/compose/layouts/adaptive/use-window-size-classes), [desktop windowing](https://developer.android.com/develop/adaptive-apps/guides/support-desktop-windowing), [keyboard and pointer input](https://developer.android.com/guide/topics/large-screens/keyboard-mouse-and-trackpad-tier-2), [adaptive app quality](https://developer.android.com/docs/quality-guidelines/adaptive-app-quality).
