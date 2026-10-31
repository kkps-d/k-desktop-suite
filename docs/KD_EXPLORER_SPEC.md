# KD Explorer — basic file browsing specification

Updated 24 October 2026. Status: proposed requirements; the `:explorer` application module is not implemented.

KD Explorer is a native Android file browser for phones, tablets, foldables, and desktop windows. Preserve Microsoft File Explorer's recognizable arrangement and navigation model, styled with Jetpack Compose and Material 3 Expressive. Follow the [suite design guidelines](DESIGN_GUIDELINES.md), including KD branding, system appearance, dynamic color, and a green fallback palette.

This first version lets people reach a folder, browse its contents, find an item, inspect basic information, and open a file in another app. It does not manage or edit files.

## 1. Scope

| Included in the first version | Deferred |
|---|---|
| Add and revisit user-authorized folder locations | Unrestricted device storage, Windows drive letters, network shares, account setup |
| Folder navigation, breadcrumbs, Back, Forward, Up, Refresh | Tabs, multiple browser windows, split browsing panes |
| A leading locations pane and an expandable folder tree | Recent-file feeds, recommendations, favorites beyond saved locations |
| Details and icon views; sorting; current-folder name filtering | Recursive/indexed search, content search, grouping, advanced filters |
| Single and multiple selection; basic item information | Copy, cut, paste, move, rename, delete, create, file drag-and-drop |
| Open one file through Android's installed-app handling | Built-in editors, previews, thumbnails, archive browsing/extraction |
| Keyboard, pointer, touch, accessibility, and session restoration | Shell extensions, terminal integration, sharing, disk management |

Launch independently as **KD Explorer**, using a separate `:explorer` module and application ID `com.kkps.kdesktopsuite.explorer`. Follow the suite's API 26 minimum and existing build configuration. Do not change other apps to deliver this feature.

Keep unimplemented commands absent. In particular, do not reproduce Explorer's New, Cut, Copy, Paste, Rename, Share, or Delete controls as placeholders.

## 2. Explorer familiarity and Material expression

Microsoft documents File Explorer around navigation controls, an address bar, a leading navigation pane, a central content pane, and a status bar. Those relationships form this app's layout reference ([Microsoft's File Explorer navigation guide](https://support.microsoft.com/en-us/accessibility/windows/use-a-screen-reader-to-explore-and-navigate-file-explorer-in-windows)). No exact Windows version or pixel-level reproduction is required.

Material 3 Expressive supplies the visual language: tonal surfaces, expressive control shapes, clear typography, and responsive motion ([Google's introduction](https://blog.google/products-and-platforms/platforms/android/material-3-expressive-android-wearos-launch/)). The dimensions and component mappings below are KD Explorer product decisions.

| Familiar Explorer element | KD Explorer treatment |
|---|---|
| Back / Forward / Up beside the address | Standard Material icon buttons in the same order, with labels for accessibility and hover tooltips |
| Breadcrumb address bar | Rounded, neutral container with individually actionable ancestors and a distinct current folder |
| Search at the trailing edge | Material search field labeled **Search this folder**, filtering immediate children only |
| Left navigation pane | Tonal locations pane with expandable folder rows, visible labels, and a rounded active indicator |
| Command bar | Quiet toolbar with Sort, View, and overflow; connected expressive buttons for the two view choices |
| Main file listing | Dense enough to scan, with aligned metadata, restrained row highlighting, and clear focus |
| Bottom status bar | Low-emphasis item and selection counts, without an oversized footer |

The folder tree is hierarchical content, not a set of top-level app destinations. Build it as an accessible tree/list; do not force folders into a bottom navigation bar or a fixed destination rail. Rounded indicators and tonal surfaces can follow Material styling without changing the tree's behavior.

## 3. Window structure

Expanded-window arrangement, shown in left-to-right layout:

```text
┌────────────────────────────────────────────────────────────────────┐
│ KD Explorer                          Native system window controls │
├────────────────────────────────────────────────────────────────────┤
│ Back Forward Up Refresh  [ Locations > Work > Reports ] [ Search ] │
│ Sort ▾   View ▾                                           More ⋮  │
├───────────────────┬────────────────────────────────────────────────┤
│ Locations         │ Name                 Date modified  Type  Size │
│ All locations     │ ▸ Annual reports     …              Folder  — │
│ ▾ Work            │   Budget.xlsx        …              …       … │
│   ▸ Reports       │   Notes.txt          …              …       … │
│ ▸ Pictures        │                                                │
│                   │                                                │
│ Add location      │                                                │
├───────────────────┴────────────────────────────────────────────────┤
│ 3 items · 1 selected                                               │
└────────────────────────────────────────────────────────────────────┘
```

- Preserve the order: system caption/title, navigation/address/search row, command row, browsing area, status bar. Use the native Android caption and window controls; do not draw Windows minimize/maximize/close buttons.
- The leading pane and file area scroll independently. Keep the address and commands visible while browsing.
- Start the leading pane at 256dp, within a 224–320dp range when space allows. Keep at least 360dp for the content pane; collapse the leading pane when that cannot be met.
- Use 8dp spacing increments, 16dp outer content padding where appropriate, and compact 8dp gaps between controls. Do not pad every file as a separate card.
- Navigation and command rows begin at 56dp and grow for font scaling. All interactive targets are at least 48dp. The status text can occupy a shorter, noninteractive row.
- The status bar reports the displayed item count and selected count. With filtering, show **4 of 28 items**; with incomplete loading, label the count as provisional. Do not recursively calculate folder sizes.

## 4. Locations and Android storage access

Use Android's Storage Access Framework and `ACTION_OPEN_DOCUMENT_TREE` to add a folder location. Save the read grant when the provider offers persistent access. The app's own browsing behavior must not write to the chosen tree, even if the returned grant permits writing.

Android limits the directories selectable through this API, including storage roots and the Download directory on Android 11 and later. It also restricts access to Android/data and Android/obb. Do not promise unrestricted **This PC**, **C:**, **Downloads**, or app-private storage browsing ([Android's document access and restrictions guide](https://developer.android.com/training/data-storage/shared/documents-files)).

- On first launch, show **Locations**, a short explanation, and a primary **Add location** button. There is no mandatory onboarding carousel or account step.
- After authorization, save the provider's folder label and stable URI identity, then open that folder. Cancelling the picker leaves the current view unchanged.
- Treat each granted tree as a location boundary. Up is disabled at its root; breadcrumbs do not imply access to inaccessible parents. File and folder identities come from provider identifiers, never reconstructed display paths.
- The **Locations** overview lists saved folders with provider labels where needed to distinguish identical names. Choosing a saved location opens its root. Re-adding the same tree selects the existing location.
- The pane includes **Locations**, saved location roots, and **Add location**. Expand roots and descendants lazily; show folders only in the tree. Clicking a disclosure arrow expands/collapses without navigation; activating the folder label navigates.
- Keep the current folder visible in the tree when practical. Collapse/expand operations must not change the open folder or file-list selection. Do not enumerate the entire tree on startup.
- **Remove location** is available from a saved root's context/overflow menu. It removes the saved shortcut and releases its grant when no longer used; it never deletes files. If currently browsing that location, return to Locations and remove its history entries.
- Check access on restore and before reading. A revoked grant offers **Choose folder again** and **Remove location**; unavailable removable storage offers **Retry**. Do not repeatedly launch the picker automatically.
- Providers can include remote folders. Show provider loading/offline errors accurately; basic local browsing requires no account or network. The app does not implement its own cloud integration.

## 5. Navigation

- Opening a folder replaces the content pane and updates the breadcrumb. New navigation clears selection and the name filter. Each successful folder visit stores its scroll anchor and focused item for history restoration.
- **Back** and **Forward** traverse this window's successful location history, including Locations. A new navigation after Back clears Forward history. **Up** visits the immediate parent inside the granted tree and records an ordinary navigation entry. Refresh does not add history.
- Breadcrumb ancestors navigate directly. The current segment is not a redundant navigation action. If the path overflows, keep the current folder visible and put earlier ancestors in a labeled overflow menu.
- The address is a breadcrumb navigator in this version. `Ctrl+L` focuses it; keyboard users can activate ancestors or open its location menu. Raw filesystem-path entry, drive-letter syntax, and pasted content URIs are deferred.
- Refresh reloads the current folder. Keep still-existing selections and the scroll anchor by stable identity; remove vanished items from selection and report a disappeared focused item without opening a different file.
- During navigation, show the target location and a loading state. Never present the previous folder's rows under a new address. If navigation fails, show Retry and a route back to the last accessible view; do not add a failed visit to successful history.
- Android Back first dismisses a menu/dialog/panel or the IME, then exits selection mode, then clears an active filter, then navigates Back. At the beginning of history it follows normal system task behavior. Support predictive Back where applicable.

## 6. Content views, sorting, and filtering

### Details and icons

**Details** is the initial view in expanded windows. Show Name, Date modified, Type, and Size with a fixed header and aligned columns. Name takes remaining width; truncate visually and expose the full name through accessibility, a pointer tooltip, and Item information. Right-align sizes. Folder size is **—**; missing provider metadata is **Unknown**, never zero. Format known dates and sizes for the user's locale.

At narrower widths, retain the same Details preference but present a list with a filename and secondary metadata line. Move metadata that does not fit to Item information. Do not force the entire browser to scroll horizontally.

**Icons** uses a responsive grid with consistent folder/file-type symbols and filenames beneath them. Use generic file symbols for unknown types. Thumbnail generation is deferred; do not decode document contents while listing. Full names remain available through Item information.

Use 48dp minimum list rows, enlarging for wrapped text or font scaling; icon tiles also respect 48dp targets. A folder's disclosure affordance belongs in the tree, not the main listing. Main-view folders use an ordinary folder symbol and the same activation behavior in both views.

### Sorting

Default to Name ascending, folders first. Support Name, Date modified, Type, and Size, with ascending/descending and a checked **Folders first** option. Clicking a Details column chooses that key; clicking the active column reverses its direction. The Sort menu exposes the same choices in every layout.

Sort names naturally so File 2 precedes File 10, using locale-aware comparison. Unknown metadata sorts last in either direction. Keep deterministic ordering for ties using name and stable item identity. Preserve selection and focus when order changes. Save sort and view preferences globally for the first version.

### Search this folder

Filter the already listed immediate children by a case-insensitive substring of their display names, including extensions. The field must disclose **Current folder only** as supporting text or an accessible description; it never searches descendants, file contents, or other locations.

Apply after a short typing debounce, with immediate submission on Enter. The clear action restores the unfiltered listing. Selecting filtered-out items is cleared so no hidden selections remain. An empty result reads **No matching items** with **Clear search**, distinct from **This folder is empty**. Clearing search restores the prior unfiltered scroll anchor where possible.

## 7. Input, selection, and opening

| Input | Behavior |
|---|---|
| Pointer single click | Select one item; clicking empty space clears selection |
| Pointer double click | Open a folder or launch one file |
| Ctrl+click / Shift+click | Toggle an item / select an inclusive contiguous range in the displayed order |
| Touch tap | Open a folder or launch a file when outside selection mode |
| Touch long press | Enter selection mode and select the item; subsequent taps toggle items |
| Keyboard arrows | Move focus through rows or grid cells; tree Left/Right collapses/expands, with parent/child navigation |
| Space / Shift+arrows | Toggle the focused item / extend the selection range |
| Enter | Open the focused item when zero or one item is selected; with multiple selected, do not launch a batch |
| Right click / Menu key / Shift+F10 | Open the item's context menu; preserve a multiple selection when invoked within it |

Support Ctrl+A for all displayed items and a visible **Select items** overflow command for touch and assistive technology. Selection mode shows a count and **Clear selection**. Escape closes transient UI first, then clears selection, then clears filtering; it does not close the app.

Item context commands are **Open** and **Item information**. Open requires exactly one selected item; Item information can summarize a multiple selection with count and total known file sizes, explicitly indicating unknown sizes and excluding directory contents. Empty-space commands are Refresh, Sort, View, and Select items. Saved location roots additionally offer Remove location. Every essential action has a visible menu or button alternative.

Open files with Android `ACTION_VIEW`, the provider's MIME type, a content URI, and temporary read access for the receiving app. Resolve handlers normally and offer the system chooser when required. If the provider requires downloading first, report its loading state without blocking input. Unsupported virtual documents or missing handlers show an actionable explanation and leave the folder intact. Do not treat file activation as an executable/shell command or convert content URIs to guessed filesystem paths.

Item information opens a constrained Material dialog containing full name, folder/file type, known size and modified date, location breadcrumb, and provider. Unknown properties are labeled. The dialog is scrollable at large font sizes, with a reachable Close action; a persistent preview/details pane is deferred.

| Action | Shortcut |
|---|---|
| Back / Forward / Up | Alt+Left / Alt+Right / Alt+Up |
| Refresh | F5 or Ctrl+R |
| Focus address / search | Ctrl+L / Ctrl+F |
| Select all displayed items | Ctrl+A, only when the listing owns focus |
| Item information | Alt+Enter |
| Move between major regions | F6 / Shift+F6 |

Show supported shortcuts in command menus where applicable. Text fields retain native text editing and selection shortcuts; browser commands must not intercept them. Keep touch scrolling, mouse wheel, trackpad scrolling, and keyboard scrolling available.

## 8. Material 3 Expressive styling

Use the suite's Material theme and supported expressive components. Keep any required experimental API opt-ins local; adopting this spec does not require upgrading unrelated dependencies. Reference [Material 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3) for theme and component integration.

- **Color:** use `surface` for the listing, tonal surface-container roles for navigation/chrome, `onSurface` for filenames, and `onSurfaceVariant` for metadata. Use a primary-container role for selected files and a secondary-container role for the active tree location, with their matching foreground colors. Never encode selection or errors using color alone.
- **Shape:** use theme shapes consistently: a large rounded address/search container, fully rounded view-choice indicators, and smaller rounded selection rows. Keep list geometry stable while states change. No scalloped file tiles or decorative shapes that compete with filenames.
- **Type:** use Material title styles for Locations and dialogs, body styles for filenames, and label/body-small styles for metadata and commands. Use system typography; reserve emphasized weight for hierarchy and selection counts. Do not add a large folder headline above a breadcrumb that already names it.
- **Controls:** use Material menus, dialogs, tooltips, state layers, and connected toggle buttons for Details/Icons where supported. Add location can be a filled button in the initial empty state and a lower-emphasis action in the pane. File browsing has no floating create button in this scope.
- **Motion:** use Material motion tokens for pane changes and control feedback. Selection updates immediately; folder transitions must not delay navigation or bounce the file grid. Honor reduced motion and system animation settings, including removing spatial transitions when disabled.
- **Accessibility:** maintain 4.5:1 contrast for normal text and visible focus/control boundaries. Announce filename, type, selected state, and tree expansion state. Expose column sort direction and result/error updates without announcing every row as it loads. Support RTL and 200% font scaling without losing commands.

## 9. Adaptive layout

Use current window constraints, Material 3 Adaptive, height, and folding features; recompute during live resizing. Width bands follow [Android's window size classes](https://developer.android.com/develop/ui/compose/layouts/adaptive/use-window-size-classes). These arrangements are product decisions, subject to measured space and font scaling.

| Available width | Arrangement |
|---|---|
| Compact: <600dp | One browsing pane. Locations opens from a labeled leading button into a full-screen panel. Breadcrumb stays visible; search expands into its own row when invoked. Sort/View/Refresh remain reachable through overflow |
| Medium: 600–839dp | Content-first layout. Locations uses an overlay panel; show more toolbar labels when they fit. Search stays inline only when the breadcrumb remains usable |
| Expanded: 840–1199dp | Persistent leading locations/tree pane, central listing, full navigation/address row; search inline when measured space permits |
| Large/extra-large: ≥1200dp | Same two-pane structure; use additional width for names and metadata rather than stretching controls or adding dashboards |
| Compact height: <480dp, any width | Consolidate command rows into overflow, use scrollable dialogs/panels, and preserve browsing height |

On a narrow window, opening Locations does not replace navigation history. Choosing a folder closes the panel and returns focus to the listing; cancelling restores focus to the opener. Search and all commands remain available when panes collapse.

Respect system captions, bars, cutouts, IME, and dragging regions. Do not integrate controls into the caption in this first version. Never place a control or filename across a separating hinge; use separate panes when space permits, otherwise place browsing in one uninterrupted region. Preserve current location, selection, search, and scroll when resizing or changing posture.

## 10. State, loading, and errors

Keep filesystem/provider work off the UI thread. Use lazy lists/grids and stable identities; cancel superseded requests and prevent late results from replacing the current folder. Query only needed metadata, close cursors, and load tree children on demand. Large listings must remain scrollable and cancellable; do not recursively scan to populate counts or sizes.

| State | Required presentation |
|---|---|
| No locations | Locations empty state with Add location |
| Loading folder | Labeled progress in the content pane; navigation remains usable |
| Empty folder | This folder is empty; retain navigation and view controls |
| No filter matches | No matching items with Clear search |
| Access revoked | Folder access is no longer available; Choose folder again / Remove location |
| Provider offline or removed storage | Location unavailable with Retry and Locations |
| Folder/file removed externally | Explain the missing item and offer Refresh or an accessible parent/location |
| Metadata unavailable | Unknown fields; keep other items usable |

After returning from an external viewer or the background, revalidate the visible folder and refresh without unnecessarily resetting scroll or selection. Provider notifications may improve freshness but cannot be the only refresh mechanism.

Preserve location, successful navigation history, filter, selection identities, focused item, tree expansion, and scroll anchors through activity recreation. Persist saved locations, offered read grants, view/sort preferences, and the last visited folder in private storage for process restart. Revalidate the last folder before restoring it; fall back to its accessible root or Locations with an explanation. Transient selection, filters, and history may reset after process death. Never persist whole directory listings in saved instance state or claim cached metadata is current.

Keep provider/URI handling in the Android storage layer, browsing state in a ViewModel, and rendering in Compose. Reuse existing suite theme code where appropriate; do not extract a new shared framework for this single feature.

## 11. Acceptance criteria

The basic browsing milestone is complete when all of the following are verified:

1. A clean launch offers Add location; picker cancellation changes nothing; an authorized folder opens and remains reachable after restart when persistent access is available.
2. The expanded layout reads immediately as an Explorer-style browser: leading tree, address/navigation above the listing, Sort/View commands, and bottom counts. Compact windows expose every command without obscured or clipped controls.
3. Folder activation, tree expansion, breadcrumbs, Back/Forward/Up, Refresh, and branching history behave as specified. Up cannot cross a granted root; a failed visit does not corrupt successful history.
4. Details and Icons show the same items; all sort keys/directions, folder ordering, natural name order, unknown metadata, and current-folder-only filtering work consistently.
5. Pointer, touch, and keyboard selection agree on item identity; sorting preserves selection, filtering leaves no hidden selections, and external removals never activate a replacement row accidentally.
6. A supported file opens in another app with readable URI access. Missing handlers, revoked grants, offline providers, and deleted items leave a usable browser with the specified recovery actions.
7. Empty, loading, error, and no-match states are distinguishable. Slow or large directories do not block input, and results from an abandoned folder cannot overwrite the current view.
8. Activity recreation and live resizing preserve browsing state. Relaunch restores saved locations/preferences and safely validates the last folder. Removing a location does not modify its files.
9. Verify compact, medium, expanded, extra-large, and short windows; fold changes; desktop maximize/restore; 200% fonts; RTL; TalkBack; keyboard-only operation; light/dark and dynamic/fallback colors; reduced motion; caption and IME insets.
10. No write operations, placeholder management commands, tabs, thumbnails, recursive search, or unrestricted-storage claims ship in this milestone.

Use unit tests for meaningful browsing state, sort/filter rules, and stale-result handling; provider-backed tests for access/revocation and file launch grants; Compose/device checks for adaptive layout and input. Run the future module's relevant unit tests, assemble, lint, and instrumented checks, and report device/provider combinations that could not be verified. This specification itself does not establish implementation or runtime validation.
