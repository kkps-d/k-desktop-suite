# KD Paint specification — Windows 7 Paint baseline

Updated 24 October 2026. Status: proposed implementation specification; no Paint application code is included in this change.

Build KD Paint as a small raster drawing and image editing app whose tools, command grouping, and editing model follow **Microsoft Paint shipped with Windows 7**. Implement the Android presentation with Jetpack Compose and Material 3 Expressive under the [project design guidelines](DESIGN_GUIDELINES.md).

## 1. Reference and fidelity

The reference is the ribbon-era Paint with Home, View, a contextual Text tab, Color 1/Color 2, artistic brushes, and a single flattened picture. Preserve this workflow. Exclude layers, AI tools, automatic background removal, a transparent document background, vector object persistence, and Paint 3D features.

Microsoft's [Windows Ribbon introduction](https://learn.microsoft.com/en-us/windows/win32/windowsribbon/windowsribbon-introduction) explicitly illustrates Paint for Windows 7. Its [reference screenshot](https://learn.microsoft.com/en-us/windows/win32/windowsribbon/images/overviews/screenshot-paint-win7transparency-mirror.png) establishes the original command hierarchy. The original application-menu entry uses an icon; Microsoft documents the switch to a **File** label in Windows 8 in its [Application Menu reference](https://learn.microsoft.com/en-us/windows/win32/windowsribbon/windowsribbon-controls-applicationmenu). KD Paint deliberately uses the clearer File label.

### Evidence and limits

Computer-use inspection on 24 October 2026 examined an existing classic ribbon Paint window. The executable reported version **10.0.26100.8457**, so these observations are evidence from a later classic build, **not a verified Windows 7 installation**. Existing picture content was left intact; no drawing, saving, conversion, or clipboard operations were tested.

| Evidence | What it establishes | Limit |
|---|---|---|
| Microsoft Windows 7 ribbon screenshot and documentation | Home/View hierarchy, command groups, application-menu placement | Does not establish drawing algorithms or pixel measurements |
| Classic app Home ribbon and accessibility tree | Six tools, 23 named shapes, image commands, two active colors, status fields | Later executable; no rendered-tool comparison |
| Classic app Brushes gallery | Nine brush choices arranged as a gallery | Names and texture algorithms were not exposed by that popup's accessibility tree |
| Classic app Select menu | Rectangular/free-form selection, Select all, Invert selection, Delete, Transparent selection | Compositing behavior was not exercised |
| Classic app Resize and Skew dialog | Percentage/pixel modes, horizontal/vertical dimensions, aspect lock, two skew angles | Validation ranges and interpolation were not tested |
| Classic app View tab | Zoom in/out, 100%, rulers, gridlines, status bar, full screen, thumbnail | Thumbnail was disabled at the observed 25% zoom |
| Classic app File menu and Save as flyout | File lifecycle, recent pictures, PNG/JPEG/BMP/GIF/other formats, legacy integrations | No export or system integration was invoked |
| Classic app Image Properties dialog | Dimensions, pixels/inches/centimeters, color/black-and-white, file metadata, Default | Conversion and Default behavior were not exercised |

Requirements below define KD Paint behavior. Numerical layout values, limits, defaults, gesture rules, transaction boundaries, and adaptation choices are **product decisions**, not claimed Windows 7 measurements. Exact legacy rendering parity remains a separate verification task (§15).

## 2. Product scope

The primary workflows are drawing on a blank picture, annotating an opened image, copying or moving a region, resizing/cropping/rotating, adding text, and saving a flat image. The app opens directly into a canvas without a dashboard, account, or network dependency.

Launch independently as **KD Paint** in a separate `:paint` Android application module, supporting the suite's API 26+ baseline. Use one document per window for the initial release, matching the reference's document model. Do not inherit Notepad's tabs merely to reuse its UI. Multiple windows may hold separate documents where Android permits them.

| Scope | Required capability |
|---|---|
| Core editing milestone | Canvas, pencil, solid brush, fill, eraser, picker, magnifier; rectangle selection; clipboard; line/rectangle/oval; text; PNG/JPEG; undo/redo; durable recovery; adaptive input/UI |
| Classic feature milestone | All nine brush styles and 23 shapes; free-form/inverse/transparent selections; outline/fill styles; resize/skew; rotate/flip; monochrome conversion; BMP/GIF/TIFF; print; rulers/gridlines/thumbnail |
| Deliberate Android adaptations | Native file picker, Share, touch/stylus navigation, Material styling, draft recovery, external-change protection |
| Deferred | Scanner acquisition, wallpaper setting, custom Quick Access Toolbar arrangement, exact legacy brush depletion, legacy selection-stamping tricks |

The core milestone is a usable intermediate delivery. Claim the full classic feature milestone only after its corresponding acceptance checks pass. Deferred commands must be absent rather than displayed as inert controls.

## 3. Window and command layout

### Expanded workspace

The vertical order is system caption, document/quick-action row, File/Home/View tab row, the selected ribbon, canvas viewport, then status bar. During text entry, expose Text under a contextual Text tools label. Home and View remain reachable. Use native Android caption controls; reserve their exclusion regions and a clear window-drag area.

Place Save, Undo, and Redo beside the document title. Show `Untitled — KD Paint` or the provider's filename, plus an accessible unsaved indicator. File opens a menu; Home/View/Text switch command panels. A collapse-ribbon action preserves the tab row and opens the selected panel temporarily when requested. Selecting a drawing tool dismisses a temporary panel. Escape/Back dismiss it and restore canvas focus.

| Home group, leading to trailing | Contents |
|---|---|
| Clipboard | Paste split action; Paste from; Cut; Copy |
| Image | Select split action; Crop; Resize; Rotate/flip |
| Tools | Pencil, Fill with color, Text, Eraser, Color picker, Magnifier |
| Brushes | Current brush button and nine-item gallery |
| Shapes | Shape gallery; Outline; Fill |
| Size | Current document-pixel width and preset gallery |
| Colors | Color 1, Color 2, palette, custom colors, Edit colors |

Keep these groups recognizable. Menus, keyboard commands, and compact controls must invoke the same command behavior and enablement rules.

### Adaptive presentation

Use current window constraints and fold posture. The following are starting layout rules; switch earlier if text scaling or measured content requires it.

| Available space | Presentation |
|---|---|
| Width ≥1200dp and usable height ≥600dp | Full labeled ribbon with visible shape and color galleries |
| Width 840–1199dp and usable height ≥600dp | Preserve group order; collapse shapes, brushes, and colors into labeled gallery buttons as needed |
| Width <840dp or usable height <600dp | File/title/Undo/Redo header; compact current-tool/current-color/options row; Tools, Image, and View panels opened on demand |
| Very narrow or large text | Scrollable command panels; title truncates; File, tool selection, color selection, and Undo remain reachable |

The canvas occupies the remaining area. Do not squeeze a desktop ribbon into a horizontally scrolling whole-window layout. Compact panels use a bottom sheet on tall windows and a constrained scrollable dialog on short windows. Popups stay inside the window. A separating hinge must not contain controls or usable canvas pixels; place the viewport in one uninterrupted pane and optional tool panels in the other.

Initial dimensions: 48dp minimum control targets, 48dp tab/quick-action rows, approximately 112–144dp full ribbon, 8dp group spacing, and a 28dp noninteractive status row. Grow for font scaling. Interactive status controls use 48dp targets or move into View. Use theme typography; 14sp control labels and 12sp secondary labels are starting values. Do not reproduce tiny Windows ribbon targets.

Use theme surfaces for chrome, `onSurface` for labels, restrained dividers, and distinct hover/focus/selected states. The picture remains white in dark mode; dark theme changes the workspace and controls, never the image colors. Keep content coordinates left-to-right even in an RTL UI.

## 4. Picture, viewport, and defaults

An editable picture is a width × height raster with an opaque white background. Start new pictures at **800 × 600 image pixels**, 100% zoom where it fits; otherwise use Fit. Initial tool: Brush, solid style, 3px width; Color 1: black; Color 2: white. Outline defaults to Solid color; closed-shape fill defaults to No fill. These are KD defaults.

The canvas origin is its top-left image pixel `(0, 0)`. Image pixels, screen pixels, and `dp` are separate units. At 100%, map one image pixel to one physical display pixel, accounting for Android density. Pointer coordinates and brush widths are computed in image space. Apply scroll/zoom only when presenting the raster. Display coordinates as integer image pixels; never draw into workspace outside the image.

Keep the image top-left aligned with an 8dp viewport margin when smaller than the viewport, with a subtle border and exterior workspace. Expose right, bottom, and bottom-right canvas-size handles on pointer devices. Touch users have Image properties. Extending the canvas adds Color 2 pixels; reducing it crops the right/bottom without scaling existing content. Preview changes and commit once on release; Cancel restores the original. Image > Resize scales pixels and is a separate operation.

Initial limits: each dimension 1–8192px and total area ≤16,777,216 pixels. Check dimensions, decoded size, temporary buffers, history, and device memory before allocation. A device may reject an operation below these limits if safe allocation is unavailable. Explain the limit and keep the original document intact; never silently downsample an opened file. A 4096 × 4096 32-bit buffer alone is approximately 64MiB, before previews/history.

## 5. Drawing tools and colors

### Tool behavior

| Tool | Required behavior |
|---|---|
| Pencil | Hard-edged raster stroke at the selected width, with no smoothing that changes requested pixel coverage |
| Brush | Continuous stroke with the chosen brush style and size; interpolate samples to avoid gaps during fast movement |
| Fill with color | Four-connected flood fill of the exact seed color within the editable selection mask, if any; no tolerance slider in the classic baseline |
| Eraser | Paint Color 2 across the stroke; it does not create alpha transparency |
| Color picker | Sample a composited image pixel into the active color slot; then return to the previously active drawing tool |
| Magnifier | Primary activation zooms in about the indicated image point; secondary activation zooms out; never edits pixels |
| Text | Begin an editable text box and show Text tools; commit raster text according to §8 |

Capture the pointer for a stroke. Clip coverage to the document and active mask. A press without movement creates one dab. One completed stroke is one undo operation. Pointer cancellation restores the pre-stroke raster. A pointer release outside the viewport ends the stroke without drawing outside the image. A system interruption cancels unfinished work and checkpoints completed operations.

Primary mouse input uses Color 1; secondary input uses Color 2 for supported drawing/fill tools. With shapes, secondary drawing swaps stroke/fill color roles. Eraser secondary drag replaces exact Color 1 matches under its footprint with Color 2. Right-click context menus appear in Select/navigation mode; other tools retain secondary drawing behavior. Shift+F10 always provides a context menu.

### Brush and size catalog

Provide **Brush, Calligraphy brush 1, Calligraphy brush 2, Airbrush, Oil brush, Crayon, Marker, Natural pencil, and Watercolor brush**. The gallery uses labeled stroke samples with accessible names. Give the two calligraphy brushes different nib angles; airbrush uses stippled coverage; crayon/natural pencil use grain; marker/watercolor build translucent pigment onto the opaque picture; oil has a textured dense stroke. The solid brush remains deterministic and fully opaque.

Texture resemblance is required for the classic milestone; exact Windows pigment simulation and paint depletion are deferred. Seed procedural texture per operation so redo and recovery do not change the stroke. Store completed pixels rather than regenerate history from random samples.

Show four labeled width presets with a stroke preview. Initial KD presets are 1, 3, 5, and 8 image pixels for pencil/solid brush/shapes, and 4, 8, 16, and 24 for eraser/artistic brushes. Permit a numeric width from 1–100px through Size. Preserve the last size per tool/style. Width is unaffected by zoom. Hide or disable irrelevant Size controls for Text, Fill, picker, and magnifier.

### Color behavior

Show two distinctly labeled active swatches. Selecting Color 1 or Color 2 chooses which slot subsequent palette/custom-color activation edits. Left-click a palette swatch assigns the active slot; right-click assigns Color 2. Touch uses explicit slot selection. Provide 20 preset colors and 10 custom slots; preserve custom slots across relaunch. Exact historical RGB values are a reference-verification item, not a release blocker for the core milestone.

Edit colors offers a visual picker, RGB fields (0–255), and a six-digit hex field, with a live sample and current slot label. Accept only valid values; Cancel changes nothing. Do not expose alpha. Color/tool/width/palette changes affect future work and eligible active previews; alone they neither dirty the picture nor add history entries.

Transparent selection is a color-key compositing option under Select, not document transparency. The Windows 7-era naming is corroborated by the [Microsoft-hosted support answer on Draw Opaque](https://learn.microsoft.com/en-us/answers/questions/2525706/i-am-looking-for-the-draw-opaque-option-on-word-pa). KD's explicit matching rules are in §7.

## 6. Shapes

The gallery contains **23 entries**, in this order:

1. Line, Curve, Oval, Rectangle, Rounded rectangle.
2. Polygon, Triangle, Right triangle, Diamond, Pentagon, Hexagon.
3. Right arrow, Left arrow, Up arrow, Down arrow.
4. Four-point star, Five-point star, Six-point star.
5. Rounded rectangular callout, Oval callout, Cloud callout, Heart, Lightning.

Drag to define a shape's bounds. Shift constrains closed shapes to equal width/height and lines to the nearest 45-degree increment. A new shape remains a movable/resizable preview until committed. While active, colors, width, outline, and fill changes update that preview. Enter, a click outside, or switching drawing tools commits it; Escape/Back cancels it. There are no editable shape objects after rasterization. Undo removes the complete shape in one step.

Curve: drag the initial line, then place two successive bend controls; the second bend completes its construction and leaves the resulting shape preview adjustable. Enter may finish early; Escape cancels the whole preview. Polygon: drag the first edge, click additional vertices, then double-click the final vertex or activate Finish; Enter closes it. Touch and accessibility users have visible Finish/Cancel actions. Do not require a double-click gesture to finish.

Outline choices: No outline, Solid color, Crayon, Marker, Oil, Natural pencil, Watercolor. Fill choices: No fill plus the same six rendering styles. Color 1 supplies the outline and Color 2 the fill under primary input. Line/Curve have no fill. Prevent both No outline and No fill for closed shapes by retaining the last visible outline. Anti-alias shape edges; pencil coverage remains hard-edged. Smooth shape rendering can leave fringe colors that an exact-color bucket fill does not replace; do not hide this distinction behind an undocumented fill tolerance.

## 7. Selection and clipboard

Rectangular and free-form selections create an explicit mask with a dashed outline and resize handles. Close a free-form path on release. A mask can be nonrectangular; its bounding box does not redefine its contents. Select all covers the whole canvas. Invert selection flips the mask within canvas bounds. Cut/Copy/Delete/Crop are disabled without a nonempty selection; Paste is disabled without supported image clipboard content.

Creating or deselecting a mask does not edit pixels. On the first move/transform, lift the selected pixels into a floating selection and retain an original snapshot for cancellation. Fill a moved/cut/deleted source region with Color 2. Pixels outside the mask remain unchanged. Moving, scaling, or rotating a floating selection previews the result until Enter, an outside click, tool switch, Save, or a document-wide operation commits it. Escape/Back restores the pre-lift picture; for a paste, it removes only that paste preview. Switching to viewport navigation does not commit it.

| Command | Required behavior |
|---|---|
| Copy | Copy selected raster pixels with the mask represented as alpha where possible; leave the picture unchanged |
| Cut | Copy successfully first, then replace masked source pixels with Color 2; one undo operation |
| Paste | Read image/PNG clipboard content or a granted image URI; create a floating selection at visible top-left, clamped to the canvas |
| Paste from | Open an image picker and insert the chosen image as a floating selection rather than replacing the document |
| Delete | Replace selected pixels with Color 2; one undo operation |
| Crop | Replace the document with selection bounds; fill outside a free-form mask within those bounds with Color 2; clear selection |
| Transparent selection | While compositing a floating selection, skip pixels whose RGB exactly equals the captured Color 2 key; partial-alpha source pixels composite against the destination |

Capture the transparency key when lifting/pasting; toggling Transparent selection updates the active preview. Changing Color 2 after lifting does not reinterpret an existing key. This rule is a KD decision. The original mask and source clearing still apply even where color-key pixels are skipped at the destination.

Keep pasted content fully available even when it exceeds the picture. Offer **Expand canvas / Keep canvas / Cancel** before presenting the preview. Keep canvas clips the committed result; Expand canvas previews sufficient right/bottom growth, subject to limits. Escape restores both the original raster and dimensions. Never silently stretch the picture or shrink the pasted content.

Arrow keys nudge a floating selection by 1 image pixel; Shift+arrow by 10. An accessible Move/Resize panel provides numeric X/Y/width/height fields and aspect lock. Negative X/Y are allowed for a preview; commit clips to canvas bounds. Clipboard errors preserve document contents and the existing selection.

## 8. Text

Dragging with Text creates a live text box. Text tools contains font family, size, Bold, Italic, Underline, Strikethrough, transparent/opaque background, and Color 1/Color 2. Default background is transparent: existing pixels remain visible between glyphs. Opaque background fills the box with Color 2. Text glyphs use Color 1. Support Unicode, selection, clipboard, multiline input, native IME composition, and system font fallback. Fonts come from those available to Android; identical Windows font availability is not promised.

While the box is live, its contents and formatting remain editable; dragging the border moves it and handles change wrapping bounds. Grow the live box vertically to reveal overflow rather than silently dropping typed text. Clip final raster coverage to the canvas; show an out-of-bounds indication before committing. IME insets must keep the active insertion point visible by panning the viewport, without changing image dimensions or text size.

Explicit Apply, clicking outside, switching drawing tools, or Save commits the box to raster in one picture-history operation. Escape/Cancel discards the uncommitted box. Android Back first dismisses the IME; a subsequent Back cancels the live box. Enter inserts a newline; it does not commit text. Undo/Redo while text editing first targets native text editing history; after commit, picture Undo removes the complete text operation. Committed text cannot be reopened for editing.

## 9. Image operations and dialogs

All dialogs show the affected target: **Selection** when a floating/nonempty selection is eligible, otherwise **Picture**. Validate before allocating or editing. Preview-affecting dialogs have Apply/Cancel; a cancel or failed operation leaves pixels, dimensions, history, and dirty state unchanged.

| Operation | Specification |
|---|---|
| Resize | Percentage or pixels; horizontal and vertical values; maintain aspect ratio enabled initially. Link the other dimension with nearest-integer rounding, minimum 1px. Use bilinear resampling as the KD default. Act on selection when present, otherwise the whole picture |
| Skew | Horizontal and vertical angles, each −89° to +89°, default 0°. Apply horizontal then vertical shear after resize; expand target bounds to contain output, filling exposed pixels with Color 2. Selection output remains floating |
| Rotate | Right 90°, Left 90°, 180°. Swap dimensions for whole-picture quarter turns. Use exact pixel remapping, without interpolation |
| Flip | Horizontal or vertical; act on selection when present, otherwise the whole picture; exact pixel remapping |
| Image properties | Show file name/type, last successful save time, encoded size when known, pixel dimensions, and resolution metadata. Offer Pixels/Inches/Centimeters and Color/Black and white. Never label missing metadata as zero |
| Properties dimensions | Change canvas bounds, anchored top-left, without resampling. Default restores KD's 800 × 600 dimensions in the form; it does not clear pixels until Apply |
| Physical units | Convert with image DPI, using 96 DPI when unavailable; retain floating-point form values until applying rounded pixel dimensions. This does not use display density |
| Black and white | Preview a two-color conversion using a documented fixed luminance threshold of 128; Apply is undoable. Later colored marks are thresholded too while this mode is active. Switching back to Color does not restore lost colors; Undo does |

Resize and skew together apply as one transaction. Show calculated output dimensions and reject out-of-range or excessive results before Apply. No-op transformations do not dirty the document. When changing canvas bounds may discard pixels, show the proposed bounds clearly before applying; Undo restores cropped pixels while history retains that entry.

## 10. View and navigation

View contains Zoom in/out, 100%, Fit (KD addition), Rulers, Gridlines, Status bar, Full screen, and Thumbnail. Zoom ranges from **12.5% to 800%**. Buttons use 12.5, 25, 50, 100, 200, 400, and 800%; pinch/slider can use intermediate values. Zoom around the pointer/gesture centroid, or viewport center for keyboard commands. Show the actual percentage. Fit recomputes on resizing; explicit zoom preserves scale and the nearest feasible image-space center.

Use nearest-neighbor display sampling from 100% upward so pixels remain inspectable. Downscaling may use filtered display sampling; it must not change stored pixels. Rulers track image coordinates, selected units, and viewport offsets. Gridlines are a nonexported overlay visible at 400% or greater; keep the toggle available and explain the zoom threshold.

Status fields, leading to trailing: pointer image coordinates, selection/active-preview dimensions, picture dimensions, last saved encoded size if known, and zoom. Blank unavailable fields rather than showing stale values. On compact layouts show dimensions and zoom; full details remain available in Image properties/View. View changes never dirty the picture or create history.

Full screen hides application chrome and system bars where supported. Provide an accessible Exit full screen control; Escape/Back exits before any document-close action. Thumbnail opens a small overview with a viewport rectangle when zoomed/scrollable; dragging or activating a position navigates. Hide it when there is no useful overview, without changing its saved preference.

## 11. Input, focus, and accessibility

| Input | Behavior |
|---|---|
| Mouse/trackpad | Primary draws/selects; secondary color behavior as in §5; middle drag or Space+primary drag pans; wheel scrolls vertically; Shift+wheel horizontally; Ctrl+wheel zooms |
| Touch | One finger draws/selects with the active tool; two-finger pan/pinch navigates. On recognition of a second finger, cancel the uncommitted one-finger mark before navigation. Touch mode offers a Hand tool for one-finger navigation |
| Stylus | Tip draws; hover previews footprint; eraser end temporarily uses Eraser where reported. While stylus input is active, suppress incidental palm drawing and allow deliberate touch navigation |
| Keyboard | Logical Tab/Shift+Tab order, arrow navigation inside galleries, Enter/Space activation, visible focus, Escape cancellation. Do not dispatch picture shortcuts while a native text/numeric field consumes them |
| Accessibility services | Labeled tools/colors, selected and disabled states, numeric move/resize/dimension alternatives, explicit shape/text Apply/Cancel, and current picture/zoom/selection summary |

Keep initial strokes fixed-width; pressure/tilt painting is outside this baseline. Gesture recognition must never leave an accidental dot or stroke after a pan/pinch. Losing focus must not leave pointer capture, drawing, or a modifier key logically held.

Support a keyboard canvas cursor: arrows move one image pixel, Shift+arrows ten; Space toggles stroke start/end for supported freehand tools, or activates a dab/fill/pick. Announce coordinates on request, without flooding screen readers during continuous movement. When a floating selection exists, arrows move that selection instead. Hold-to-pan applies only to pointer gestures, so it does not conflict with the canvas cursor.

Use at least 4.5:1 normal-text contrast and visible non-color-only selection/focus indicators. Every color has a name or RGB label. Tooltip delay is 500ms and includes shortcut/description where useful. System font scaling must not clip controls; support RTL chrome, high contrast, reduced motion, keyboard-only use, and TalkBack. Do not announce canvas artwork as if it were semantically understood.

### Shortcut contract

These are KD mappings based on the classic command set. They do not assert identical handling in every Windows release. Preserve the classic **Ctrl+W = Resize and Skew** mapping; this single-document release has no Close tab shortcut.

| Action | Shortcut |
|---|---|
| New / Open / Save | Ctrl+N / Ctrl+O / Ctrl+S |
| Save as | F12; Ctrl+Shift+S alias |
| Undo / Redo | Ctrl+Z / Ctrl+Y; Ctrl+Shift+Z alias |
| Cut / Copy / Paste / Select all | Ctrl+X / Ctrl+C / Ctrl+V / Ctrl+A |
| Delete selected pixels | Delete, when canvas/selection has focus |
| Resize and Skew / Image properties | Ctrl+W / Ctrl+E |
| Gridlines / Rulers | Ctrl+G / Ctrl+R |
| Zoom in / out | Ctrl+Page Up / Ctrl+Page Down |
| Increase / decrease drawing width | Ctrl+Plus / Ctrl+Minus, including numpad forms |
| Bold / Italic / Underline | Ctrl+B / Ctrl+I / Ctrl+U within live text |
| Print / Full screen | Ctrl+P / F11 |
| Context menu / Help | Shift+F10 / F1 |
| Commit preview / Cancel | Enter / Escape, subject to text rules in §8 |

## 12. Files, lifecycle, and recovery

File commands: New, Open, Save, Save as, Recent pictures, Print, Share, Image properties, About KD Paint, Close picture. Recent pictures displays up to ten provider-backed files and routes through Open. A menu action that closes the final picture opens a fresh untitled picture; the system window close retains platform behavior and preserves recovery.

Open uses Android's document picker and granted content URIs; never require broad filesystem access. Open replaces the current picture only after unsaved-content handling succeeds and the incoming image decodes successfully. Opening the same URI in another app-owned window focuses that window. Paste from inserts into the current document. A single dropped image opens through the same checks; multiple drops are rejected with an explanation. File/clipboard ingestion is size-checked independently of the extension.

| Format | Core milestone | Classic feature milestone |
|---|---|---|
| PNG | Open/save; default new-file format; lossless opaque RGB result | Required |
| JPEG | Open/save; flatten on white; quality default 90 with an accessible setting | Required |
| BMP | Not advertised | Open/save 24-bit uncompressed; unsupported variants report a useful error |
| GIF | Not advertised | Open first composited animation frame; save one frame with palette reduction warning |
| TIFF | Not advertised | Open first page; save one flattened page; disclose multi-page loss |
| ICO, WebP, HEIF and other formats | Outside the promised format set | Optional only after explicit codec/behavior tests; no promise based solely on platform decoding |

Import transparent images by compositing onto white; the editor stores opaque pixels. Apply EXIF orientation once during decode. Warn before lossy/palette/multiframe conversion and allow Cancel. Save as changes the document's associated URI/format only after a successful write. Saving to a lossy format leaves the higher-quality current raster intact; indicate that reopening the file may differ.

Save commits pending text/shape/selection first, snapshots a document revision, and performs encoding/I/O off the UI thread. Keep edits responsive. On success mark only that saved revision clean; newer edits remain dirty. On cancellation/failure keep the draft, association, and recovery available. Android providers may not support atomic replacement: stage encoded data privately first, use provider capabilities where available, and do not claim every provider can guarantee an atomic overwrite.

Before overwriting, compare available provider metadata/content fingerprint with the last opened/saved version. If externally changed, offer Overwrite / Save as / Cancel. If access is revoked, retain the draft and offer Save as. New, replacing Open, and Close picture ask **Save / Discard / Cancel** for dirty content. Failed/cancelled Save stops the pending destructive action. Discard removes that draft from the recovered session only after the choice succeeds.

Print uses Android's print framework with a generated flat-picture preview, orientation, fit-to-page default, and explicit scaling. Share creates an app-private temporary PNG and grants read access through the Android share sheet. Neither marks the picture saved or changes its file association. These replace the reference's Windows-specific print/email plumbing. Scanner acquisition and desktop-background commands remain deferred.

Recovery stores the raster, dimensions, color mode, document association, saved-revision marker, viewport, tool settings, and pending text/shape/floating-selection state in private durable storage. Pending work includes the pre-preview state needed to cancel after restoration. Checkpoint 500ms after completed edits or preview-state changes and flush when backgrounding. Use versioned, atomic private snapshots; keep a previous valid generation for interrupted writes. Do not put raster bodies in `SavedStateHandle`/saved instance state. An abrupt termination can lose changes after the last successful checkpoint.

Restore unsaved/recovered work on launch without overwriting external files. Preserve completed-operation history across ordinary resizing/activity recreation; reset undo history after process restart and disclose this limitation. Recover multiple windows in creation order into separate documents/windows where possible; otherwise provide a recovered-picture picker, never silently discard all but one. Do not create a permanent gallery destination for normal use.

## 13. Transactions and implementation boundaries

Each document owns its raster, optional preview/mask, history, file association, dirty revision, and viewport. View preferences/custom colors persist separately from document pixels. Use explicit interaction states: Idle, Drawing stroke, Selecting, Floating selection, Shape preview, Text editing, and Modal operation. Navigation temporarily suspends the active preview. A second edit cannot mutate the raster while another transaction owns it.

History records committed pixel/dimension/color-mode changes. Drawing a stroke, applying text/shape, moving a selection, deleting/cutting, crop, resize/skew, rotate/flip, and monochrome conversion each create one operation. Capture affected tiles/regions rather than copying the entire picture for every small stroke. Undo/Redo restores dimensions and mode as well as pixels. A new committed edit clears Redo. Color changes, viewport movement, creating a mask, saving, cancelled previews, and true no-ops do not add history entries.

Use a saved revision identity, not an irreversible dirty boolean: Undo back to the saved revision restores clean status. Bound history by memory; start with a 64MiB per-document history budget and evict oldest complete operations. Target at least 50 ordinary pencil strokes on an 800 × 600 picture, without promising 50 whole-image transformations at maximum dimensions. If the saved revision leaves history, maintain its identity for accurate dirty checks where possible.

Keep image-space geometry, mask/transform calculations, validation, and transaction rules independent of Android UI APIs. Keep Compose input/rendering, `Bitmap`/Canvas adapters, content resolver permissions, codecs, clipboard, print, share, stylus events, and caption handling in Android-specific code inside `:paint`. Reuse suite theme/document patterns only where ownership and behavior match; do not move Paint raster logic into Notepad or introduce a generalized editor framework.

Preflight expensive work and perform decode/encode/fill/large transforms off the main thread with cancellation. Install a result only if its source revision is still valid. On memory pressure, discard recreatable previews/history before risking the active document. A failed operation must not leave half-applied pixels. Local performance targets: continuous freehand input at display refresh on the test device for a 2048 × 2048 image; 16ms typical frame budget on a 60Hz display; progress/cancel affordance for work lasting more than 500ms. Record hardware and image dimensions with measurements.

## 14. Acceptance checks

Use deterministic unit tests for raster semantics and transforms, instrumented tests for UI/lifecycle/provider behavior, and manual device checks for input/display integration. Use small pixel fixtures to compare exact results for hard-edged operations; allow documented tolerances for antialiasing, text, and textured brushes.

| ID | Scenario and expected result |
|---|---|
| P01 | Fresh launch shows an 800 × 600 white picture, correct tool/colors/width, and a clean untitled state. No onboarding blocks drawing |
| P02 | Draw the same 1px pencil path at 100%, 400%, and a noninteger display density; resulting image-space coverage is identical. Undo removes one complete stroke |
| P03 | Fast strokes have no sample gaps; releasing outside ends safely. Interrupted drawing and two-finger navigation leave no partial mark |
| P04 | Fill two regions joined only diagonally: only the seed's four-connected region changes. A selection mask clips fill; replacing a color with itself creates no history |
| P05 | Erase over red pixels with blue Color 2: result is opaque blue. Secondary erasing changes only exact Color 1 matches |
| P06 | All nine brushes have distinct visible samples; all 23 shape commands are reachable in expanded and compact layouts. Preview changes commit once; cancellation leaves pixels/history unchanged |
| P07 | Draw constrained circle/square/line, a two-bend curve, and a polygon with Finish. Committed shapes flatten; Undo restores original pixels |
| P08 | Move an irregular selection over patterned pixels. Only masked source pixels clear to Color 2; Escape restores the exact original. Repeat with inverse selection |
| P09 | Color-key paste skips exact Color 2 matches while preserving other colors, including near-matches. Oversized Paste offers Expand/Keep/Cancel; cancelling restores dimensions |
| P10 | Copy/Cut failure changes nothing. Paste from inserts rather than replaces. Transparent clipboard mask edges composite correctly onto the opaque picture |
| P11 | Live Unicode/multiline/IME text supports selection and formatting. Back dismisses IME first. Apply rasterizes once; committed text Undo restores underlying pixels |
| P12 | Resize 100 × 50 to width 200 with aspect lock yields 200 × 100. Quarter-turn rotates a labeled 2 × 3 fixture to 3 × 2 with exact pixel positions. Flip twice restores the fixture |
| P13 | Canvas enlargement preserves original pixel coordinates and fills only added area. Shrink/crop and monochrome conversion Undo restore pixels and dimensions/mode |
| P14 | Zero, malformed, excessive, and allocation-failing dimensions report errors and preserve the original. Resize/skew Cancel changes nothing |
| P15 | Zoom, pan, grid/ruler/full-screen/thumbnail operations never change exported pixel data or dirty state. Pointer-centered zoom preserves the image point under the pointer |
| P16 | Open a transparent PNG and an EXIF-rotated JPEG: alpha flattens on white; orientation applies once. PNG round-trip preserves dimensions and pixels |
| P17 | Cancelled/failed/revoked-access Save keeps the draft and blocks pending New/Open/Close. Save during newer edits marks only the encoded revision saved |
| P18 | Undo to the saved revision clears dirty state; Redo restores it. External file changes offer Overwrite/Save as/Cancel. Lossy conversion warns before writing |
| P19 | Resize/rotate the app and recreate its activity during a selection/text preview: viewport, preview, and completed history survive. Process restart restores checkpointed pixels/preview but resets undo history |
| P20 | Corrupt or interrupted recovery writes fall back to a valid generation. Multiple recovered pictures remain individually reachable |
| P21 | Exercise 360 × 800, 600 × 900, 840 × 900, 1200 × 800, and 1200 × 400dp windows; 200% font scale; light/dark; RTL; fold changes. Commands remain reachable and image pixels do not change |
| P22 | Keyboard-only and TalkBack can choose tools/colors, set dimensions, finish/cancel text/shapes, move selections, save, and dismiss panels. Focus returns predictably; no shortcut steals text-field input |
| P23 | On touch, stylus, mouse, and trackpad hardware, validate gesture arbitration, hover/secondary color, palm handling, scrolling, window dragging, and full-screen exit |
| P24 | At the classic feature milestone, round-trip BMP; test palette-reduced GIF and first-page TIFF behavior; inspect Android print preview, exported dimensions, and shared URI grants |
| P25 | Measure continuous drawing and large operations on declared test hardware. Forced cancellation/memory failure preserves the last committed document |

## 15. Remaining reference verification

These items refine fidelity; the explicit KD requirements above remain implementable without guessing:

- Inspect a genuine Windows 7 SP1 Paint installation before claiming exact parity. Record build, theme, DPI, and window size with any captured reference.
- Compare per-tool legacy width presets, brush texture/depletion, calligraphy angles, and stroke antialiasing. KD's initial presets and no-depletion rule are intentional until revised.
- Record original palette RGB values, custom-color persistence, shape Outline/Fill labels, contextual Text defaults, and thumbnail enablement behavior.
- Verify legacy selection lifting, right-button drawing, color-key changes, free-form crop, oversized paste, curve/polygon completion, skew ordering, and monochrome threshold against small fixtures. These were specified as KD rules rather than inferred from menu labels.
- Test actual supported format variants, transparency handling, metadata retention, print scaling, keyboard layout differences, and undo limits. Do not infer algorithmic compatibility from matching UI alone.

Specification validation for this change: checked repository/design guidance; inspected classic UI menus/dialogs using computer use; cross-checked the Windows 7 ribbon and application-menu distinction against Microsoft documentation. No Android builds or runtime tests were run because this change contains documentation only.
