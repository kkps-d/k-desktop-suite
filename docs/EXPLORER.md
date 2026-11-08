# KD Explorer

KD Explorer currently provides a mockup-based browser layout for UI validation,
with shared Notepad tabs and independent Android windows. File browsing and storage
access are not implemented. The listing contains illustrative sample content;
Explorer requests no storage permissions and does not read or create those files.

## Layout preview

The [desktop mockup](references/kd-explorer/desktop-mockup.png) defines the layout:
navigation/address/search across the top, a tonal Locations tree, a quiet command
strip, an aligned file listing, and a subdued item/selection status bar. The shared
tab header replaces the mockup's older plain title bar.

- The sample location is **Locations > Work > Reports**. Its eight folders/files,
  metadata, and initial **Budget.xlsx** selection match the mockup.
- The locations pane stays alongside the listing in expanded windows. Narrow,
  large-font, and short windows use a Locations drawer. Work expands/collapses as
  a presentation control; location rows do not navigate or request folder access.
- Details uses Name, Date modified, Type, and right-aligned Size columns. Narrow
  panes put type/size beneath names and omit columns that cannot fit. The listing
  and tree scroll independently. Metadata remains available in Item information.
- Details/Icons switches the sample presentation. Clicking a sample item changes
  its visual selection and count. More > Item information shows fixture metadata.
  These controls do not open or modify files.
- Search accepts text and can be cleared, but does not filter or query anything.
  Back, Forward, Up, Refresh, Sort, and Add location have no browsing behavior.
  Menus, tooltips, focus, hover, view choices, and drawer controls are available
  for UI review. The Sample content status label identifies the fixture listing.
- Compact layouts stack navigation and breadcrumbs, reveal search on demand, and
  keep view/sort choices in More. Short windows reduce chrome and collapse the tree.
  Colors follow system appearance and dynamic color, with the suite green fallback.

The presentation fields survive activity recreation within their window. They
are not a browsing session or a durable file index.

## Tabs and windows

Explorer uses the same `SuiteTabHeader` as Notepad. Tab dimensions, typography,
outward-curving selected feet, inset hover shape, overlap/separators, button
feedback, tooltips, overflow, and caption layout come from that one component.
The plus sits immediately after the visible tabs, including RTL. New and Close
have Notepad's independent hit targets; hovering and focusing do not select tabs.

- Launch with one blank tab. Use **New document** (+) or **File > New document**
  to add one. The shared command uses Notepad's label; Explorer creates a blank
  preview tab with a stable numbered title.
- Tabs compress equally before scrolling. The active tab stays visible;
  windows below 600dp use the shared dropdown showing the active tab, with New
  (+) alongside it. The dropdown is 60% of usable window height with a scrolling
  list and pinned **New tab** (+) footer. Rows retain independent Close actions.
  Leading handles reorder locally; release outside cancels without opening a
  window. Move up/down and Move to window remain menu/accessibility commands.
  In very narrow popups, tap the handle to open these commands.
- Closing an inactive tab keeps the active tab. Closing the active tab selects
  its right neighbor, falling back to the left. Closing the final tab shows the
  shared Welcome tab and keeps the window open.
- In the horizontal strip, drag with a mouse/trackpad, or long-press with touch,
  to preview and commit a reorder. Edge scrolling is supported. Escape and rejected drops preserve the
  tab and order. Context menus and accessibility actions offer Move left/right.
- **Move to window…** offers other open Explorer windows and a new window.
  Dragging between tab strips transfers the same tab. Dropping a tab into the
  listing workspace or outside the source can open a new window where Android
  supports it. Source removal waits for destination acknowledgement; failed,
  stale, and cancelled transfers preserve the source. A sole tab can move to an
  existing window, closing its empty source, but cannot detach to a new window.
- Use **File > New window** to open an independent Android task. **File > Close
  window** removes only that task. Native system window commands remain available.
- Tabs share the system caption when a tab and New fit, using Notepad's 8dp drag
  clearance and actual system-control geometry. Unused header space stays
  draggable. Narrow, obstructed, or short captions fall back below it; the same
  font scaling, RTL, and folding behavior applies as in Notepad.

Tab IDs, order, active selection, and numbering survive resizing, activity
recreation, and Android saved-task restoration. There is no private on-disk
session recovery after task removal or force stop. Each new window starts with
one preview tab. Actual browsing commands and file/provider behavior remain deferred.

## Keyboard

| Action | Shortcut |
| --- | --- |
| New tab | Ctrl+N |
| Close current tab | Ctrl+W |
| New window | Ctrl+Shift+N |
| Next / previous tab | Ctrl+Tab / Ctrl+Shift+Tab |
| Move tab focus without selecting | Left/Right, wrapping at the ends |
| Activate focused tab/control | Enter/Space |
| Tab context menu | Menu key or Shift+F10 |
| Cancel tab drag / dismiss menu or tooltip | Escape |

Use Tab/Shift+Tab to reach tab, Close, New, and menu controls. Menus show shortcuts;
New/Close tooltips appear after Notepad's half-second delay.

## Development

Run the shared `explorer` Android Studio configuration after Gradle sync, or
build `:explorer:assembleDebug`. The app ID is `com.kkps.kdesktopsuite.explorer`;
minimum Android is API 26. See [Testing](TESTING.md#explorer-shell) and
[Document tabs](TABS.md). The [browsing specification](KD_EXPLORER_SPEC.md)
describes future functionality; this guide describes the current shell.
