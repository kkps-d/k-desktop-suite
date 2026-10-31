# Document tabs

Shared guidance for suite apps that support multiple documents in tabs. Each app
owns its document content, commands, and behavior when the last tab closes.

## Layout and appearance

- Compress tabs equally before scrolling. Keep New and individual Close actions
  reachable, and bring the active tab into view after selection or resizing.
- When a tab and New cannot fit, use a document selector with full titles,
  selection/status information, and individual close actions.
- Join the selected tab to the adjoining command surface where present. Place
  separators between inactive tabs; hover, focus, and document status must not
  shift control positions. Keep keyboard focus distinct from selection.
- Use theme tokens, respect font scaling and reduced motion, and expose full
  titles and unsaved/saving/error states accessibly.
- Integrate tabs into the desktop caption only when controls and scaled text
  fit safely. Preserve system buttons and blank window-dragging space; otherwise
  place tabs below the caption. Follow the
  [shared desktop UI constraints](DEVELOPMENT.md#shared-desktop-ui-constraints).

Keep exact dimensions and caption thresholds with the implementation and tests
that own them. Reuse established constraints when introducing another tabbed app;
document intentional differences and their reasons.

## Input and document state

- Support logical Tab/Shift+Tab traversal, arrow-key focus movement, Enter/Space
  activation, and Escape dismissal or cancellation. Close and New remain
  independent actions. Moving focus alone must not select another document.
- Each tab retains its document, file association, selection, scroll position,
  and undo/redo history across switches, resizing, and activity recreation.
- Closing an inactive tab preserves the active document. Closing the active tab
  selects its right neighbor, falling back to its left. Each app defines the
  empty-window state separately.
- Closing unsaved content offers Save / Discard / Cancel. Failed or cancelled
  saving preserves the document. Recovery never overwrites original files.

## Reordering and window transfers

- Mouse/trackpad dragging and touch long-press can reorder tabs. Preview the
  destination before committing order, support edge scrolling, and preserve
  documents when dragging is cancelled.
- Where supported, window transfers retain the live document and undo history.
  Confirm destination ownership before removing the source; invalid, stale,
  cancelled, or failed transfers must preserve the source document.
- Provide keyboard and accessibility alternatives to dragging. Keep transfer
  data limited to application-owned identifiers or tokens, not document bodies.
- Define recovery ordering and active-document selection across windows so
  process restart does not duplicate or lose documents.

## Current implementation references

Notepad is the current implementation reference. Tab UI and session ownership
remain in its module; the shared caption geometry/input support lives in
`core/ui`. This guide does not imply that a shared tab component already exists.

- [NotepadScreen](../notepad/src/main/java/com/kkps/kdesktopsuite/notepad/NotepadScreen.kt): tab dimensions, sizing, overflow, focus, and controls.
- [NotepadChrome](../notepad/src/main/java/com/kkps/kdesktopsuite/notepad/NotepadChrome.kt): tab shape and surface treatment.
- [Caption layout](../notepad/src/main/java/com/kkps/kdesktopsuite/notepad/DesktopCaption.kt): current caption eligibility and fallback thresholds.
- [Testing](TESTING.md#tab-and-caption-regressions): reusable regression checks and existing Notepad test coverage.
- [Notepad guide](NOTEPAD.md): current shortcuts, Welcome behavior, recovery, and window-specific commands.
