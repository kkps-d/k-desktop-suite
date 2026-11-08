# Document tabs

All tabbed suite apps use the shared Notepad-derived `SuiteTabHeader` in
`core/ui`. Notepad defines the exact appearance, dimensions, spacing, layout,
and interaction behavior. Changes to tabs belong in the shared component and
apply to every tabbed app. Each app owns its document content and storage.

## Layout and appearance

- Compress tabs equally before scrolling. Keep New and individual Close actions
  reachable, and bring the active tab into view after selection or resizing.
- Below 600dp of usable content width, use the shared document dropdown instead
  of the horizontal strip. Also use it when a tab and New cannot fit. The trigger
  shows the active title/status with New (+) immediately alongside it. Welcome
  uses this same compact header, with a selected Welcome row and the New footer.
  The trigger is a rounded outlined control, with 8dp below it before the toolbar
  and an 8dp gap above its panel. The horizontal strip retains its tab styling.
- The dropdown spans the content region with 8dp side margins. It fits its tab
  list and footer, up to 60% of the usable height after system/fold/IME constraints. The tab list scrolls
  independently above a pinned **New tab** (+) footer. Selecting, closing, or
  creating a tab dismisses it. Long titles/status wrap up to two lines; accessibility
  exposes the full title and status. Selection uses a highlight without a checkmark.
  Individual Close remains independent; row action menus open beside their button.
- Dropdown rows have leading drag handles for local reordering. Dragging starts
  after movement crosses touch slop; other row gestures scroll or select. Release
  inside the list commits; release outside, Escape/Back, or interruption cancels.
  The lifted row follows the finger while neighbors animate into the preview
  order. Holding near either 64dp edge zone scrolls proportionally toward that
  edge, including just beyond the viewport. Speed is independent of frame rate.
  Neither the trigger nor its rows starts a native transfer drag or creates a
  window by dragging out. **Move
  up/down** and **Move to window…** remain explicit menu/accessibility commands.
  In very narrow popups, tapping the handle opens these commands.
- Opening the dropdown reveals and focuses the active row. Up/Down moves focus
  without selecting; Enter/Space activates it. Escape/Back and outside taps dismiss
  it, returning focus to the trigger. Escape first cancels a local drag if active.
  Compact caption integration requires 48dp touch targets; otherwise fall back
  below the caption.
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
- Explicit New commands (buttons, menu, or keyboard) show a localized “New tab
  opened” toast after creation. Restoration and transfers do not replay it.
- Each tab retains its document, file association, selection, scroll position,
  and undo/redo history across switches, resizing, and activity recreation.
- Closing an inactive tab preserves the active document. Closing the active tab
  selects its right neighbor, falling back to its left. Each app defines the
  empty-window state separately.
- Closing unsaved content offers Save / Discard / Cancel. Failed or cancelled
  saving preserves the document. Recovery never overwrites original files.

## Reordering and window transfers

- On the horizontal strip, mouse/trackpad dragging and touch long-press can
  reorder tabs. Preview the destination before committing order, support edge
  scrolling, and preserve
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

- [Shared tabs](../core/ui/src/main/java/com/kkps/kdesktopsuite/ui/DocumentTabs.kt): adaptive header, horizontal strip, sizes, typography, controls, tooltips, and focus.
- [Shared dropdown](../core/ui/src/main/java/com/kkps/kdesktopsuite/ui/DocumentTabSwitcher.kt): compact trigger, scrolling list, pinned New tab footer, and local handle gestures.
- [Shared shapes](../core/ui/src/main/java/com/kkps/kdesktopsuite/ui/DocumentTabShapes.kt): selected feet and hover shape.
- [Shared caption layout](../core/ui/src/main/java/com/kkps/kdesktopsuite/ui/DocumentTabCaption.kt): eligibility, clearances, and fallback.
- [Shared interactions](../core/ui/src/main/java/com/kkps/kdesktopsuite/ui/DocumentTabInteractions.kt): drag shadow, drop handling, accessible commands, and context menu actions.
- [Notepad adapter](../notepad/src/main/java/com/kkps/kdesktopsuite/notepad/NotepadScreen.kt): document status and editor callbacks.
- [Explorer adapter](../explorer/src/main/java/com/kkps/kdesktopsuite/explorer/ExplorerScreen.kt): browsing-tab state and window callbacks.
- [Testing](TESTING.md#tab-and-caption-regressions): reusable regression checks and app coverage.
