# Document tabs

All tabbed suite apps use the shared Notepad-derived `SuiteTabHeader` in
`core/ui`. Notepad defines the exact appearance, dimensions, spacing, layout,
and interaction behavior. Changes to tabs belong in the shared component and
apply to every tabbed app. Each app owns its document content and storage.

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
- Integrate tabs into the desktop caption whenever controls and scaled text
  fit safely. Keep clearance beside system elements compact (8dp in the current
  tabbed apps), and let unused custom header space remain draggable. Exclude only
  interactive controls from dragging; avoid a wide dedicated drag gap. Preserve
  system buttons; otherwise place tabs below the caption. Follow the
  [shared desktop UI constraints](DEVELOPMENT.md#shared-desktop-ui-constraints).

Keep exact dimensions and caption thresholds in the shared implementation and
its regression tests. Use the same selected-tab feet, hover inset, 16dp overlap,
120–240dp tab widths, 22dp/8dp content padding, 8dp top inset, 48dp New/Close
targets, 14sp/20sp title typography, and caption-height adaptation as Notepad.
New follows the visible tabs immediately, including RTL; it is never pushed
to the far edge of a mostly empty header.

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

Notepad is the implementation reference; both Notepad and Explorer now render
through the same shared component. Document ownership and recovery remain in
each application. Explorer's blank tabs support reordering, drag cancellation,
window transfer acknowledgement, and a Welcome tab after closing the final tab.

- [Shared tabs](../core/ui/src/main/java/com/kkps/kdesktopsuite/ui/DocumentTabs.kt): complete header, tab/switcher layout, sizes, typography, controls, tooltips, and focus.
- [Shared shapes](../core/ui/src/main/java/com/kkps/kdesktopsuite/ui/DocumentTabShapes.kt): selected feet and hover shape.
- [Shared caption layout](../core/ui/src/main/java/com/kkps/kdesktopsuite/ui/DocumentTabCaption.kt): eligibility, clearances, and fallback.
- [Shared interactions](../core/ui/src/main/java/com/kkps/kdesktopsuite/ui/DocumentTabInteractions.kt): drag shadow, drop handling, accessible commands, and context menu actions.
- [Notepad adapter](../notepad/src/main/java/com/kkps/kdesktopsuite/notepad/NotepadScreen.kt): document status and editor callbacks.
- [Explorer adapter](../explorer/src/main/java/com/kkps/kdesktopsuite/explorer/ExplorerScreen.kt): browsing-tab state and window callbacks.
- [Testing](TESTING.md#tab-and-caption-regressions): reusable regression checks and app coverage.
