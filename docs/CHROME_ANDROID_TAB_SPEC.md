# KD Notepad tab implementation specification

Updated 23 October 2026. Status: implementation requirements.

Apply the relevant Chrome Android tab-strip patterns to KD Notepad using the [design guidelines](../DESIGN_GUIDELINES.md). Values below are KD Notepad design decisions. The linked screenshots illustrate visual relationships, not exact implementation measurements.

## Layout and appearance

Place tabs above the File/Edit menu bar. Integrate them into the desktop caption when system clearances and accessible control sizes permit.

The selected tab forms a continuous surface with the menu bar: rounded top corners, short vertical sides, and outward-curving bottom feet. Its bottom edge has no gap or stroke. Inactive tabs share the strip background. Thin separators divide adjacent inactive tabs and disappear on both sides of selection; a trailing separator may appear before New when the final tab is inactive.

| Property | Requirement |
|---|---|
| Standalone row height | At least 56dp: 8dp top inset plus 48dp interaction height; grow for text scaling |
| Integrated caption | Use actual system caption height and exclusion regions; move below the caption if the top inset and accessible controls cannot fit |
| Selected shape | 8dp top inset, flush bottom, 12dp top corners and 12dp outward bottom feet |
| Tab slot width | 240dp preferred, 120dp minimum |
| Inter-tab gap | 0dp |
| Leading content inset | 20dp from the slot edge, clear of the bottom foot |
| Title | Regular system sans-serif, 14sp with 20sp line height; one line with trailing ellipsis |
| Close | 16dp vector icon in a separate 48 × 48dp target |
| New | 20dp vector plus in a 48 × 48dp target |
| Separator | 1dp × 20dp, vertically centered |
| Focus outline | 2dp, distinct from selection, without changing layout |

Use Material theme tokens:

- Selected tab and File/Edit bar: `surface`.
- Strip: `surfaceContainerHigh`; inactive tab fills: transparent.
- Titles: `onSurface`; secondary icons: `onSurfaceVariant`.
- Separators: subdued `outlineVariant`.

Support light, dark, and dynamic color palettes with the existing green fallback. Keep the editor background independently appropriate to editing.

Draw the selected tab above inactive neighbors. Keep its feet within its slot bounds so scrolling does not clip them. Selection must not change slot width, title baseline, or Close position. Reserve a leading status slot for the dirty indicator so edits do not shift titles. Expose unsaved, saving, and error states accessibly without displacing Close or relying on color alone.

Give inactive tabs subtle rounded hover feedback and Close/New independent state feedback. Use native ripple/state feedback, retain visible focus on selected tabs, and respect reduced motion.

## Width, overflow, and compact layout

Calculate usable space from the current window, caption exclusions, insets, and folding regions. Preserve blank caption space for system dragging.

1. Reserve New's 48dp slot before sizing tabs.
2. For remaining width `A` and document count `N`, use equal slot widths of `min(240dp, max(120dp, A / N))`.
3. Place New directly after the tab viewport. Leave unused space after New when few tabs are open.
4. When minimum-width tabs no longer fit, scroll the tabs horizontally while keeping New outside the scrolling content and reachable. Keep Close available on inactive tabs too.
5. Scroll the selected tab into view after selection, creation, closing, or resizing.

If the usable caption region cannot fit a 120dp tab, New, and the required dragging clearance, move the strip below the caption. Also move it below when caption height cannot accommodate the required controls. If the standalone row cannot fit a tab plus New, show an active-document selector alongside New.

The selector displays the active document's title and opens a scrollable document list with full titles, selection, dirty/save/error states, and individual close actions. Allow long list titles to wrap. Preserve the document session when switching between layout modes.

## Commands, focus, and document safety

- Selecting a tab restores its document's caret, selection, scroll position, and undo history. Closing an inactive tab must not first select it.
- New appends and selects an untitled document and focuses its editor. Use the existing shared command handlers for Ctrl+N, Ctrl+Tab, Ctrl+Shift+Tab, and Ctrl+W.
- Closing the active tab selects its right neighbor, falling back to the left. Closing the final document creates a fresh untitled document.
- Closing or replacing unsaved content offers Save / Discard / Cancel. Failed or canceled saving preserves the document and its tab.
- Support logical Tab/Shift+Tab traversal, arrow navigation within tabs, Enter/Space activation, Escape dismissal, and independently labeled Close/New actions. Keep focus distinct from selection; moving focus must not unexpectedly move the editor caret.
- Provide full-title tooltips, a tab context menu using existing commands, and horizontal wheel/trackpad scrolling.
- Keep tab and button interactions separate from system window dragging. Respect caption controls, cutouts, hinges, and system/IME insets.
- Preserve working state during resizing and activity recreation, and recover drafts and the tab session on relaunch through the existing persistence contract.

## Implementation focus

- [NotepadScreen.kt](../notepad/src/main/java/com/kkps/kdesktopsuite/notepad/NotepadScreen.kt): continuous slots, 120–240dp sizing with equal compression before scrolling, 48dp controls, separators, stable status spacing, vector icons, and the compact document list.
- [NotepadTheme.kt](../notepad/src/main/java/com/kkps/kdesktopsuite/notepad/NotepadTheme.kt): regular 14sp/20sp title typography and the neutral Material surface hierarchy above.
- [NotepadChrome.kt](../notepad/src/main/java/com/kkps/kdesktopsuite/notepad/NotepadChrome.kt): retain the existing 12dp top corners and bottom feet within the tab bounds.
- [DesktopCaption.kt](../notepad/src/main/java/com/kkps/kdesktopsuite/notepad/DesktopCaption.kt): adapt caption eligibility and fallback behavior to the required widths, heights, and system clearances.
- Preserve the existing document command and recovery behavior while changing tab presentation.

## Acceptance checks

1. Compare two tabs, a middle-selected tab among four, and five tabs with the visual references below. Verify continuous selected-tab/menu surfaces, zero gutters, hidden adjacent separators, aligned titles, and reachable New.
2. Exercise 1, 2, 5, and 20 documents. Verify equal compression, scrolling at minimum width, selected-tab visibility, and Close/New availability.
3. Resize through wide, narrow, compact, and short windows; test maximize/restore, split screen, fold changes, and external displays. Verify system controls and dragging remain usable and layout changes preserve documents.
4. Check long, duplicate, and Unicode titles plus dirty/save/error states. Verify stable geometry and full-title access in tooltips and the document list.
5. Verify keyboard-only, touch, mouse, trackpad, TalkBack, RTL, 200% fonts, light/dark/dynamic themes, and reduced motion. Inspect actual 48dp hit bounds, visible focus, and at least 4.5:1 normal-text contrast.
6. Verify active/inactive closing, Save / Discard / Cancel, failed or canceled saving, final-document behavior, and preservation of caret, selection, scroll, and undo history across switches. Check activity recreation and relaunch recovery.

## Visual references

| Reference | Relevant comparison |
|---|---|
| [Two tabs](references/chrome-android-tabs/01-two-tabs.png) | Selected silhouette, title baseline, caption integration |
| [Middle selected](references/chrome-android-tabs/02-middle-selected.png) | Separators and selected-neighbor boundaries |
| [Five wide tabs](references/chrome-android-tabs/03-five-tabs.png) | Equal compression and New position |
