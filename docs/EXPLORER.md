# KD Explorer

KD Explorer currently provides a blank tabbed workspace for UI validation and
independent Android windows. File browsing and storage access are not implemented.
It requests no storage permissions and displays no sample files.

## Tabs and windows

Explorer uses the same `SuiteTabHeader` as Notepad. Tab dimensions, typography,
outward-curving selected feet, inset hover shape, overlap/separators, button
feedback, tooltips, overflow, and caption layout come from that one component.
The plus sits immediately after the visible tabs, including RTL. New and Close
have Notepad's independent hit targets; hovering and focusing do not select tabs.

- Launch with one blank tab. Use **New document** (+) or **File > New document**
  to add one. The shared command uses Notepad's label; Explorer creates a blank
  browsing tab with a stable numbered title.
- Tabs compress equally before scrolling. The active tab stays visible;
  very narrow windows use Notepad's document selector with independent Close,
  move, and selection actions.
- Closing an inactive tab keeps the active tab. Closing the active tab selects
  its right neighbor, falling back to the left. Closing the final tab shows the
  shared Welcome tab and keeps the window open.
- Drag with a mouse/trackpad, or long-press with touch, to preview and commit a
  reorder. Edge scrolling is supported. Escape and rejected drops preserve the
  tab and order. Context menus and accessibility actions offer Move left/right.
- **Move to window…** offers other open Explorer windows and a new window.
  Dragging between tab strips transfers the same tab. Dropping a tab into the
  blank workspace or outside the source can open a new window where Android
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
one blank tab. The workspace intentionally has no editor or browsing commands.

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
