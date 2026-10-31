# KD Paint — Android implementation specification

Updated 24 October 2026. Status: Classic core editor implemented in `:paint`; extended editing remains planned.

The initial implementation uses a permanent Classic ribbon with **no tabs of any
kind**. File and View are menus; one image is open at a time. The canvas centers
horizontally and vertically within its viewport whenever it fits. Modern desktop
and mobile presentations are deferred. The future Modern mobile view will serve
both desktop layouts; the Classic mockup's phone presentation is not a requirement.

KD Paint is an offline raster drawing and image editing app built with Kotlin, Jetpack Compose, and Material 3 Expressive. It opens directly into an editable canvas and supports drawing, image annotation, selection, text, transforms, and flat-image export. This document is self-contained; its behavior and numerical defaults are KD Paint product requirements.

Follow the [suite design guidelines](DESIGN_GUIDELINES.md) for branding, theme, accessibility, and adaptive behavior. Where presentation differs, the Android component and interaction requirements here govern KD Paint. Keep the editor quiet and prioritize the image over controls.

## 1. Scope and delivery

Create a separate `:paint` Android application module with application ID `com.kkps.kdesktopsuite.paint` and launcher label **KD Paint**. Use the suite's API 26 minimum, compile/target SDK configuration, version catalog, and compatible Compose/AndroidX versions. Do not upgrade unrelated modules to implement this feature. Use stable APIs when available; isolate and document opt-ins required by the suite's expressive Material components.

Use a single `ComponentActivity` with Compose content and one editor document per task. An Android task is not a storage boundary: give every document/session a stable ID. Additional documents may open in separate tasks when multi-instance support is implemented; ordinary Open replaces the current document after unsaved-change handling. Do not introduce document tabs or a navigation bar for a single editor destination.

| Milestone | Deliverables |
|---|---|
| Core editor | Canvas; pencil/solid brush/fill/eraser/eyedropper/zoom/pan; rectangular selection; copy/cut/paste; line/rectangle/ellipse; text; PNG/JPEG; undo/redo; durable recovery; accessible adaptive UI |
| Extended editing | Nine brush styles; 23 shapes; free-form/inverse/color-key selections; outline/fill styles; resize/skew/rotate/flip; monochrome mode; rulers/grid/overview; printing; optional codecs after validation |
| Later enhancements | Pressure/tilt brushes, additional validated formats, multiple document tasks, reusable custom tool presets |
| Outside scope | Layers, persistent vector objects, a transparent document background, generative tools, automatic background removal, cloud accounts, and a mandatory gallery/start screen |

Hide commands whose feature milestone is not delivered. Disable implemented commands only when their current preconditions are unmet, with a useful explanation where needed.

## 2. Compose screen structure

Place the document name, unsaved state, File and View menu buttons, Undo, Redo, Save, and overflow in a Material top app bar. Keep it below system caption controls. On narrow layouts move lower-priority actions into overflow; keep Undo and tool access easy to reach. An untitled document displays **Untitled**. Synchronize the task label with the active document title where supported. Do not implement document tabs, Home/View ribbon tabs, or tab switching.

Place a permanent top ribbon directly below the app bar, grouping Clipboard,
Image, Tools, Brushes, Shapes, Size, and Colors in that order. Contextual preview
and text actions remain above the canvas. The rest of the screen is the centered
canvas viewport and a quiet status row. There is no side tool strip, trailing
properties pane, mobile bottom toolbar, or layout switch in the first delivery.
Keep the reusable canvas, document state, and commands independent of Classic
chrome so later layouts use the same editing implementation.

| Surface | Commands and content |
|---|---|
| File menu (`DropdownMenu`) | New image, Open image, Save, Save as, Recent images, Insert image, Image properties, Share, Close image |
| App bar overflow | Settings, About KD Paint, lower-priority quick actions on narrow layouts |
| Permanent drawing ribbon | Clipboard, Image, Tools, Brushes, Shapes, Size, Colors; menus expose collapsed groups |
| Contextual tool controls | Brush/style gallery, width, foreground/background swatches, outline/fill, selection options, or text formatting |
| Image actions | Crop, Resize image, Skew, Rotate, Flip, Resize canvas, Monochrome conversion |
| View options | Zoom in/out, Actual size, Fit, Rulers, Pixel grid, Image information, Focus mode, Overview |
| Active-edit actions | Apply/Finish and Cancel, plus selection Cut/Copy/Delete and numeric Move/Resize where relevant |

Use `IconButton`/`IconToggleButton` for actions and selected tools, labeled buttons or chips for explicit choices, sliders and numeric fields, and scrollable pickers. Do not depend on hover or a secondary pointer button to discover a command. Classic uses dropdown menus and constrained scrollable dialogs at all sizes; mobile sheets are deferred. A dialog must not hide the only Apply or Cancel control. Only implemented tools are advertised: Solid brush and Line/Rectangle/Ellipse in the core release.

Settings contains Appearance (System, Light, Dark), custom color management, and JPEG quality. Use DataStore for these small preferences. About shows the app icon, version, developer, and source repository link. Settings is an ordinary Compose destination if it needs a full page; add a navigation library only when actual destinations justify it.

## 3. Adaptive layout and system integration

Derive layout from `currentWindowAdaptiveInfo()`/window size classes, available content constraints, and folding features. Recompute during live resizing; do not infer layout from device names, orientation alone, or the physical display size. Android's [window size class guidance](https://developer.android.com/develop/ui/compose/layouts/adaptive/use-window-size-classes) defines the breakpoints; the editor arrangements below are product decisions.

| Available width | KD Paint layout |
|---|---|
| Compact: <600dp | Retain Classic; consolidated Tools/Image/Size/Colors/Clipboard menus above the canvas. No dedicated mobile UI |
| Medium: 600–839dp | Same Classic editor with collapsed groups; retain access through labeled menus |
| Expanded: 840–1199dp | Show tool grid; collapse color palette, shapes, and Clipboard/Image progressively according to measured space |
| Large: 1200–1599dp; extra-large: ≥1600dp | Permanent grouped ribbon with more visible color and shape choices, above a centered canvas |
| Compact height: <480dp, at any width | Offer ribbon collapse; keep command access through menus and preserve the usable viewport |

Begin with a 56dp app bar and approximately 128dp ribbon. Grow groups for text
scaling and keep captions aligned. Collapse groups before sacrificing interaction
targets; any residual extreme-width overflow scrolls the ribbon only. Component
heights follow Material defaults. Target at least 48dp per interactive control.

Enable edge-to-edge content and apply/consume `Scaffold` padding and `WindowInsets` once. Keep interactive controls clear of system bars, cutouts, caption controls, IME, and gesture-navigation regions. Do not draw custom system caption buttons. Preserve system drag regions in freeform tasks. On separating folds, place the canvas in one uninterrupted pane and properties in the other; do not map editable pixels beneath a hinge.

Use Material theme color/typography/shape tokens, system dark mode, dynamic color where available, and the suite's green fallback. Controls use `dp` and `sp` with system font scaling. Image pixels and artwork colors remain independent of theme, density, and accessibility font scale. A white canvas remains white in dark appearance. Mirror surrounding layout for RTL while preserving image-space coordinates.

Keep tool choice, zoom, active preview, selection, document contents, and focus stable when ribbon groups collapse or expand. Layout changes must not create duplicate editors or commit pending edits. Implement the shared Modern mobile presentation in a later delivery without adding a separate Classic mobile layout.

## 4. State ownership and architecture

Use unidirectional data flow: the editor route collects `StateFlow<PaintUiState>` with `collectAsStateWithLifecycle()`, passes state to composables, and handles callbacks through `PaintViewModel` methods. Keep reusable composables independent of the ViewModel. Expose immutable state; do not expose mutable collections or mutable bitmap access through UI state. This follows Android's [architecture recommendations](https://developer.android.com/topic/architecture/recommendations).

Keep the implementation small and feature-owned:

| Owner | Responsibility |
|---|---|
| `PaintActivity` / editor route | Activity result launchers, incoming intents, lifecycle integration, screen-level state collection, task integration |
| `PaintViewModel` | User actions, operation coordination, enabled states, document/session ID, pending action after confirmation |
| Document session / raster engine | Committed image tiles, masks, active edit transaction, revision identity, undo/redo; single-writer ownership |
| Compose state holders | Focus, sheet/dialog presentation, transient pointer/viewport interaction; hoist shared state only as far as needed |
| Document repository | Content URI I/O, decoding/encoding, staged saves, provider metadata, recovery snapshots |
| Settings repository | DataStore-backed preferences and custom colors |

Separate image-space geometry, masks, validation, and transaction rules from Android adapters. Keep `Bitmap`/`ImageBitmap`, `ContentResolver`, clipboard, printing, sharing, and input APIs in the Android feature. Use constructor injection and testable dispatchers; do not require a new dependency-injection framework or a use-case class for each button.

ViewModels must not retain an Activity, View, lifecycle owner, or composition. Register launchers in the UI with `rememberLauncherForActivityResult`; invoke them from user interactions or controlled effects, never during composition. Represent pending save/open requests and their IDs as state, consume results once, and handle recreation while a picker is open.

Use `remember` for ephemeral UI objects; `rememberSaveable`/`SavedStateHandle` only for small reconstructible state such as session IDs, selected tool, and panel identifiers. Store bitmaps, large text previews, undo data, and masks in document storage rather than a `Bundle`. A ViewModel survives configuration changes, not process death. Follow Android's [state-saving guidance](https://developer.android.com/develop/ui/compose/state-saving).

## 5. Raster model, coordinates, and rendering

A document is an opaque sRGB raster with dimensions, color mode, stable document ID, current revision, saved revision, file association, viewport settings, and an optional active transaction. New images are **800 × 600 image pixels**, white, with Brush selected, 3px width, black foreground, white background, solid outline, and no shape fill. Start at Actual size if it fits; otherwise Fit.

**Foreground** is the stroke/text color. **Background** is the shape-fill, eraser, source-clear, and canvas-extension color; it is not the Compose surface color. Name both explicitly in the UI and accessibility semantics.

Image coordinates start at top-left `(0, 0)`. Image pixels, physical display pixels, and `dp` are different units. At Actual size/100%, one image pixel maps to one physical display pixel. Compose drawing coordinates are physical pixels; convert only layout dimensions through `Density`. Use one invertible image-to-viewport transform for rendering, hit testing, overlays, and pointer positions. Never apply density twice or mutate raster pixels to implement zoom.

Draw the raster and temporary overlays in a Compose `Canvas` or an appropriate custom drawing surface. Cache stable paths/resources, avoid allocations in each draw callback, and update only affected tiles. Keep active stroke samples and render invalidation in the drawing state holder; do not rebuild the whole screen or emit a new image through `StateFlow` for every pointer sample. High-frequency rendering state is separate from low-frequency toolbar state. Follow the [Compose rendering-phase guidance](https://developer.android.com/develop/ui/compose/performance/phases) when choosing where state is read.

The document engine owns mutations. Never draw a mutable bitmap while a worker writes into the same buffer. Publish completed frame/tile snapshots or safely exchange owned buffers; revision notifications alone do not make shared pixels thread-safe. Retain an uncommitted overlay during input, then merge once. A predicted point may improve temporary feedback but must not be committed as measured input.

Use nearest-neighbor display sampling at 100% and above; filtered downscaling is allowed below 100%. Keep stored pixel data unchanged. Center each fitting image axis within the viewport below the ribbon and above the status row, with at least 8dp clearance and a subtle nonexported border. Overflowing axes support bounded panning. Fit recomputes during resize; explicit zoom preserves the viewed image center where possible. The surrounding workspace is not editable image content.

Allow width/height from 1–8192px, with total area ≤16,777,216 pixels. Preflight decoded raster, temporary buffers, history, and device memory; dimensions alone are insufficient. A 4096 × 4096 ARGB_8888 buffer is about 64MiB. Reject unsafe work before mutation and preserve the document. Do not silently downsample. The working raster is mutable software ARGB_8888; normalize supported imports to sRGB and opaque coverage.

## 6. Drawing, brushes, and colors

| Tool | Behavior |
|---|---|
| Pencil | Hard-edged raster stroke at the chosen image-pixel width; deterministic pixel coverage |
| Brush | Continuous sampled stroke with the selected style/width; interpolate measured samples to avoid gaps |
| Fill | Exact seed-color, four-connected flood fill, clipped to any active selection mask; no tolerance control in the initial scope |
| Eraser | Replace covered pixels with Background; never create transparent document pixels |
| Eyedropper | Sample an image pixel into the selected color slot; return to the previous drawing tool |
| Zoom | Tap/primary action zooms in around that point; explicit Zoom out is available; does not edit pixels |
| Pan | One-pointer viewport navigation; also available temporarily through multitouch or keyboard/pointer input |
| Text | Begin a live text overlay as specified in §9 |

A press without movement creates one dab where applicable. Track the initiating pointer through the input sequence; clip drawing to the image and mask. One completed stroke is one history entry. Release outside finishes the clipped stroke; a cancelled input sequence restores its pre-stroke state. Losing focus, lifecycle interruption, or a newly recognized navigation gesture must not leave a partial mark or a held modifier.

Brush styles: **Solid, Calligraphy left, Calligraphy right, Airbrush, Oil, Crayon, Marker, Graphite, Watercolor**. Use labeled preview strokes. Calligraphy styles have distinct fixed nib angles; Airbrush has stippled coverage; Crayon/Graphite have grain; Marker/Watercolor accumulate translucent pigment onto the opaque raster; Oil has dense texture. Procedural textures use an operation seed so previews are stable; history stores completed pixel changes. Brush depletion is not required.

Width controls offer a preview, four presets, and a numeric 1–100px value. Initial presets: 1/3/5/8px for Pencil, Solid, and shapes; 4/8/16/24px for Eraser and textured brushes. Retain the last width per style. Width is image-space data, unaffected by zoom. Do not show irrelevant width controls for Fill, Eyedropper, Zoom, Pan, or Text.

Provide Foreground/Background swatches, a Swap colors action, 20 preset colors, and ten custom slots. Publish an explicit RGB palette fixture during implementation so tests do not depend on device themes. Selecting a slot determines where palette activation applies. The color dialog includes a visual picker, RGB 0–255 fields, six-digit hex input, sample, and Apply/Cancel. It has no alpha control. Persist custom colors in DataStore. Tool/color/width changes alone do not dirty the document or create history.

Touch users explicitly choose a slot. Pointer secondary-click selects Background for swatches; on the canvas, secondary-click opens the applicable context menu rather than painting an undiscoverable alternate-color stroke. Swap colors provides the alternate painting color for every input type. Keyboard shortcuts and tooltips expose that action.

## 7. Shapes and previews

The shape picker contains **23 shapes**:

1. Line, Curve, Ellipse, Rectangle, Rounded rectangle.
2. Polygon, Triangle, Right triangle, Diamond, Pentagon, Hexagon.
3. Right arrow, Left arrow, Up arrow, Down arrow.
4. Four-point star, Five-point star, Six-point star.
5. Rounded rectangle callout, Ellipse callout, Cloud callout, Heart, Lightning bolt.

Drag to define bounds. The shape remains a movable/resizable preview; changing style, width, or colors updates that preview. Foreground supplies the outline; Background supplies the fill. Outline styles are None, Solid, Crayon, Marker, Oil, Graphite, Watercolor; Fill has the same choices. Line/Curve have no fill. Reject a closed shape with both styles None and explain the invalid combination. Anti-alias shape edges; keep Pencil coverage hard-edged.

Provide Apply/Cancel and a Constrain proportions toggle. Shift temporarily constrains closed-shape width/height equally and line angles to 45° increments. A numeric geometry panel exposes position, dimensions, and aspect lock without requiring handle dragging. Apply rasterizes the shape in one history operation. Cancel discards it. There are no editable shape objects after commit.

Curve starts as a dragged line with two successive bend-control placements, then remains adjustable until Apply. Polygon starts with one edge, accepts further vertices, and closes with Finish; Enter may finish construction. Neither requires double-tap/double-click. Keep Finish/Apply and Cancel accessible to touch, keyboard, and accessibility services.

While a preview is active, navigation and properties controls preserve it. Explicit Apply, selecting another editing tool, or Save commits a valid nonempty preview; Save then writes that revision. Invalid previews keep their controls open with an actionable error. Merely closing a sheet, moving focus, or tapping outside does not rasterize or discard work. New/Open/Close image follows the unsaved-change flow in §12, including pending content. Document-wide transforms finish the current preview first; transforms targeting the active selection stay within its transaction.

## 8. Selection, insertion, and clipboard

Support rectangular and free-form masks, Select all, Invert selection, Clear selection, Delete selected pixels, Crop to selection, and numeric Move/Resize. Close a free-form path on release. Its bounding rectangle does not replace the mask. Distinguish **Clear selection** (no pixel change) from **Delete selected pixels** (fill masked pixels with Background).

Selecting does not modify pixels. On the first move/transform, lift the mask's pixels into an uncommitted overlay, retaining its original raster/dimensions for cancellation. Clear moved source coverage to Background in the preview; leave pixels outside the mask intact. Apply commits source clearing and destination compositing in one operation. Cancel restores the original. Pan/zoom preserves the floating selection. Arrow keys nudge 1 image pixel, Shift+arrow 10; numeric X/Y may be negative, with final output clipped to image bounds.

| Action | Contract |
|---|---|
| Copy | Encode selected coverage to PNG, using alpha outside the mask; publish an image content URI on Android's clipboard; do not change the document |
| Cut | Complete Copy successfully, then fill masked source pixels with Background; one history entry |
| Paste | On explicit request, read a supported image/URI clip and create a floating selection at the visible image origin, clamped to the canvas |
| Insert image | Use the system picker to create a floating image selection; do not replace the current document |
| Delete selected pixels | Fill the mask with Background; one history entry |
| Crop to selection | Replace image bounds with the selection bounds; areas outside a free-form mask inside that rectangle use Background; clear the selection |
| Ignore background color | Optional color-key compositing: skip exact matches to the captured Background RGB inside the source mask; preserve other pixels |

Capture the color key when lifting/inserting; changing Background later does not reinterpret it. Toggling Ignore background color updates the overlay. Partial-alpha inserted/clipboard pixels composite against destination pixels; document pixels remain opaque. Color-key compositing is separate from a transparent document background.

For oversized content offer **Expand canvas / Keep canvas / Cancel**. Expand previews right/bottom growth subject to memory limits; Keep clips the eventual result without changing source data during preview. Cancel restores both raster and dimensions. Clipboard and decode failures leave the document unchanged.

Only inspect clipboard content in response to foreground Paste actions; metadata can guide enablement without background content reads. Validate actual MIME/content/size at Paste time. Use `ClipData` with a readable content URI and bounded retention for copied image data; do not delete its backing file immediately while another app may still paste it. Give local copy feedback only when the system does not already provide it, following Android's [clipboard guidance](https://developer.android.com/develop/ui/views/touch-and-input/copy-paste).

## 9. Text overlay and IME

Tap to create a default text box or drag to size one. Use a state-based Compose text field with native selection, editing actions, Unicode, font fallback, multiline entry, and IME composition. Use available font families (Sans-serif, Serif, Monospace), image-pixel font size, Bold, Italic, Underline, and Strikethrough. Formatting applies to the whole live box in the initial scope. Foreground colors glyphs; optional opaque text background uses Background. Transparent text background means preserving existing pixels between glyphs.

Keep one native text-editing state for the overlay; do not replace text/selection from asynchronous document state on each keystroke or interrupt composition. Persist a bounded preview representation through the document checkpoint, not a potentially large saved-state text buffer. While editing, Undo/Redo targets text changes; after Apply, document Undo removes the complete text operation.

Artwork text size is document data, not `sp`. Scale the editable overlay and raster output from the same metrics so system font scaling does not change exported artwork. Formatting labels, buttons, dialogs, and accessibility affordances still honor font scaling. Verify preview and committed text align, including fallback glyphs and line wrapping.

Dragging the box border moves it; handles/numeric controls adjust wrap bounds. Reveal overflow without silently truncating input. Show any canvas clipping before Apply. Pan the viewport and respect IME insets to keep the insertion point visible; do not resize image dimensions when the keyboard opens.

Apply rasterizes once; Cancel discards uncommitted text. Enter inserts a newline; Ctrl+Enter activates Apply where supported. A canvas tap outside the box changes focus without silently applying or discarding it. Navigation preserves the overlay. Back behavior follows §11.

## 10. Image operations and viewport

Name the target **Selection** or **Image** in every transform panel. Validate numbers/output dimensions and memory before enabling Apply. One Apply produces one transaction; cancellation, failure, and no-op transforms leave pixels, dimensions, history, and dirty state unchanged.

| Operation | Contract |
|---|---|
| Resize image/selection | Pixels or percentage; linked width/height with aspect lock initially enabled; nearest-integer dimensions, minimum 1px. Bilinear resampling by default |
| Skew | Horizontal/vertical −89° to +89°; apply horizontal then vertical shear. Expand result bounds and fill exposed coverage with Background; selection output stays floating |
| Rotate | Clockwise 90°, Counterclockwise 90°, 180°; exact pixel remapping; whole-image quarter turns swap dimensions |
| Flip | Horizontal or vertical; exact pixel remapping |
| Resize canvas | Change bounds anchored top-left without resampling; extend with Background or crop right/bottom. Numeric fields are the primary accessible control; pointer handles are optional shortcuts |
| Image properties | Filename/type, dimensions, color mode, last successful save time, encoded size and resolution when known. Missing metadata is unavailable, not zero |
| Physical dimensions | Pixels/Inches/Centimeters with image DPI; use 96 DPI when absent and label the assumption. Never derive export dimensions from display density |
| Monochrome | Preview conversion with sRGB luma `0.2126R + 0.7152G + 0.0722B`, threshold 128. Apply is undoable. Later marks are thresholded while the mode is active; switching back to Color does not restore removed color |

Keep Resize image and Resize canvas distinct. Reset size changes form fields to 800 × 600; it does not clear pixels until Apply. Show proposed bounds/clipping before any operation that removes image coverage. Anti-aliased fringe colors remain distinct under exact-color Fill; do not add an undocumented tolerance.

View controls provide **Actual size**, **Fit**, zoom 12.5–800%, pixel grid, rulers, image information, Focus mode, and an overview navigator. Zoom buttons step through 12.5/25/50/100/200/400/800%; pinch/slider may use intermediate scales. Anchor zoom at the gesture centroid/pointer, or viewport center for keyboard activation. Fit recomputes on viewport changes; explicit zoom preserves scale and the nearest feasible image-space center.

Gridlines are a nonexported overlay visible from 400%; retain the preference and explain its visibility threshold. Rulers follow the current viewport and selected units. Image information shows image dimensions and zoom, adding pointer coordinates/selection bounds when space permits. Noninteractive labels may occupy a short row; interactive controls retain 48dp targets. View changes never dirty image data or add history.

Focus mode hides app controls with a clearly reachable Exit action. System bars may hide only through supported immersive APIs; transient reveal and Back still work. The optional overview shows the complete image and current viewport; tapping/dragging navigates without editing pixels. Disable/hide the overview when it provides no useful context, retaining its preference.

## 11. Input, Back, and accessibility

| Input | Required behavior |
|---|---|
| Touch | One finger draws/selects; two-finger pan/pinch navigates. If a second pointer changes gesture ownership, roll back the unfinished mark before navigation. Pan tool supports one-finger navigation |
| Stylus | Tip draws; hover shows footprint where supported; eraser end temporarily selects Eraser. Respect input cancellation/palm rejection; intentional touch navigation remains available |
| Mouse/trackpad | Primary draws/selects; secondary opens context actions. Middle drag or Space+primary drag pans; wheel scrolls, Shift+wheel scrolls horizontally, Ctrl+wheel zooms |
| Keyboard | Tab/Shift+Tab focus traversal, arrows within pickers, Enter/Space activation, visible focus, Escape dismiss/cancel, application shortcuts when editor focus permits |
| Accessibility services | Named tools/colors, explicit selected state, readable document summary, custom actions for canvas editing, numeric geometry, Apply/Cancel, and semantic alternatives to gestures |

Start with fixed-width strokes. Evaluate Jetpack Ink's Compose authoring/rendering support for low-latency strokes when its brush/output contract fits; retain custom raster processing for exact pixels, selections, fill, and unsupported textures. Ink's transient authoring surface must hand off to the same raster transaction model. Avoid introducing raw event interop unless required input data is unavailable through Compose; isolate any adapter and test gesture consumption. See [Ink stroke authoring](https://developer.android.com/develop/ui/compose/touch-input/stylus-input/ink-api-draw-stroke).

Handle system Back in this order: IME dismisses first; a modal sheet/dialog closes without committing; Focus mode exits; a pending edit cancels/restores; a selection clears; otherwise delegate Back to the system so normal task navigation/Back-to-home remains available. Change document/preview state only when a Back gesture completes; a cancelled gesture preserves it. Durable draft recovery protects unsaved work when leaving the task; do not put an unsaved-change dialog on every Back-to-home gesture. Explicit New/Open/Close image still uses Save/Discard/Cancel.

Use Material's built-in Back handling and `BackHandler` only while an editor-owned transient state needs it. Use `PredictiveBackHandler` only for a custom progress animation that requires it, and roll back cleanly if the gesture cancels. Do not intercept platform key events to emulate Back. Follow [Compose predictive Back guidance](https://developer.android.com/develop/ui/compose/system/predictive-back).

Use standard Material component semantics before adding custom semantics. Every icon action has a localized label; selected tools/colors expose state; decorative icons have no duplicate description. Group tool pickers logically; return focus to the invoking control after dismissal. Custom-drawn canvas handles have semantic/numeric alternatives. Support TalkBack and Switch Access; do not describe artwork as if its content were understood.

Minimum touch targets are 48dp with no overlapping expanded hit regions. Maintain 4.5:1 normal-text contrast and non-color-only focus/selection. Palette entries announce color names/RGB. Honor font scaling, RTL UI, system animation settings, and contrast needs. Tooltips may appear after 500ms for pointer/focus but never carry the only label. Android documents [default Compose accessibility behavior](https://developer.android.com/develop/ui/compose/accessibility/api-defaults); verify custom drawing controls separately.

Provide an optional keyboard canvas cursor: arrows move 1 image pixel, Shift+arrow 10; Space toggles a freehand stroke or performs a dab/fill/pick. With a floating selection, arrows move that selection instead. Only announce coordinates on request to avoid continuous speech. Space+pointer panning and cursor drawing must be distinguished by input sequence.

### Hardware keyboard commands

Route menu/buttons/shortcuts through the same action methods. Do not consume IME composition, native text selection, numeric input, or platform-reserved shortcuts. Document non-Latin keyboard and numpad behavior in tests. Avoid hard-coding bare tool-letter shortcuts while text is focused.

| Action | Shortcut |
|---|---|
| New / Open / Save | Ctrl+N / Ctrl+O / Ctrl+S |
| Save as | Ctrl+Shift+S |
| Close image | Ctrl+W, using unsaved-change handling |
| Undo / Redo | Ctrl+Z / Ctrl+Shift+Z; Ctrl+Y alias |
| Cut / Copy / Paste / Select all | Ctrl+X / Ctrl+C / Ctrl+V / Ctrl+A |
| Delete selected pixels | Delete with canvas/selection focus |
| Zoom in / out / Actual size | Ctrl+Plus / Ctrl+Minus / Ctrl+0 |
| Brush width down / up | Left/right bracket, only when canvas has focus |
| Swap colors | X, only when canvas has focus and no text field is active |
| Bold / Italic / Underline | Ctrl+B / Ctrl+I / Ctrl+U in live text |
| Apply live text / preview | Ctrl+Enter; Enter for non-text Apply/Finish |
| Print / Context actions / Cancel | Ctrl+P / Shift+F10 / Escape |

Resize, skew, properties, grid, and rulers remain accessible through named controls; no special shortcut is required for every feature.

## 12. Storage, formats, and platform services

Use the Storage Access Framework through Activity Result contracts: `OpenDocument` for opening an editable document, `CreateDocument` for Save as. Pass accurate MIME types. Read/write through `ContentResolver`; never turn a content URI into an assumed filesystem path. Retain persistable read/write grants only when offered and needed. No broad storage or media-read permission is required for this picker-based workflow. Follow Android's [document access guidance](https://developer.android.com/training/data-storage/shared/documents-files).

Insert image may use the Android Photo Picker through a compatible Activity Result contract; fall back to a supported system document picker. Treat photo/shared/drop grants as potentially temporary and copy imported pixels into private recovery. External Open/Share/drag intake accepts granted `content://` image streams, validates metadata/actual content/decoded bounds, and uses the same unsaved-change flow. Do not fetch arbitrary remote URLs from pasted text. Handle cancelled pickers and unavailable intent handlers without losing the current draft.

| Format | Support policy |
|---|---|
| PNG | Core open/save; default export; lossless opaque output |
| JPEG | Core open/save; quality 90 by default; warn that compression may alter pixels |
| BMP | Optional extended codec: test supported variants; 24-bit uncompressed export if delivered |
| GIF | Optional extended codec: first composited animation frame on import; one-frame palette-reduced output with warning |
| TIFF | Optional extended codec: first-page import/one-page export with a page-loss warning |
| WebP and other formats | Add only with an explicit MIME/API-level/codec capability matrix and round-trip fixtures; do not imply encoding support from decoding support |

Do not advertise optional format support until codecs are selected and tested across API levels. Apply EXIF orientation once using AndroidX ExifInterface where necessary. Decode with supported platform APIs (`ImageDecoder` on API 28+, `BitmapFactory` fallback) or the chosen codec, normalize color/alpha consistently, and enforce limits before full allocation where possible. Composite opened-image transparency onto white; inserted image transparency remains in its temporary selection until composited.

New/Open/Close image offers **Save / Discard / Cancel** if committed or pending content is unsaved. Preserve the old document until saving/discarding succeeds and an incoming image decodes successfully. A failed/cancelled Save blocks the pending replacement/close. Recent images holds at most ten successful associations; missing access offers reselect/Save as while recovery retains the image.

Save snapshots a committed revision, stages encoded bytes privately, and writes off the main thread. Save as creates a new document; `CreateDocument` does not overwrite an existing filename. Save writes the associated writable URI when possible. Change the association only after successful output. Mark only the encoded revision saved; edits made during encoding remain dirty. If applying a preview succeeds but subsequent save fails, retain its committed raster and Undo entry.

Before overwriting an existing URI, compare available metadata/fingerprint with the last successful read/write. Offer Overwrite/Save as/Cancel when external changes are detected. Providers may omit metadata or lack atomic replace/concurrency guarantees; do not promise race-free conflict detection. Stage encoding before opening a potentially truncating output stream, preserve recovery and staged output if writing fails, and explain that a failed provider write may leave an incomplete external file. Retrying must not discard the private draft.

Share exports a private temporary PNG through a narrowly configured, nonexported `FileProvider` with temporary read permission, `ACTION_SEND`, MIME type, `EXTRA_STREAM`, `ClipData`, and Android Sharesheet. Do not expose the full private files directory or use a `file://` URI. Retain the output long enough for the recipient; clean up later. Follow [FileProvider guidance](https://developer.android.com/reference/androidx/core/content/FileProvider).

Printing uses the Android print framework, fit-to-page default, explicit orientation/scaling, and a flat-image preview. Sharing/printing does not mark a document saved or change its association. Request no network, camera, notification, or storage permission solely for editing. No background service is needed for ordinary drawing/recovery.

## 13. Undo, concurrency, recovery, and performance

Treat one stroke, applied text/shape/selection move, Cut/Delete, Crop, Resize/Skew, Rotate/Flip, canvas resize, or monochrome conversion as one operation. Store changed tiles plus dimension/mode changes. Undo/Redo restores exact committed pixels; a new committed edit clears Redo. Tool changes, masks alone, view navigation, saved files, cancelled previews, and true no-ops add no history.

Track revision identity rather than an irreversible dirty flag: Undo back to the saved revision is clean; subsequent edits are dirty. Pending content also participates in unsaved-change prompts. Start with a 64MiB per-document history budget, reduce under memory pressure, and evict oldest complete entries. Target 50 ordinary strokes on an 800 × 600 image without promising 50 maximum-size transforms.

Use structured coroutines: CPU work on an injected computation dispatcher, blocking provider/file work on an injected I/O dispatcher. Repository functions are main-safe, following Android's [coroutine best practices](https://developer.android.com/kotlin/coroutines/coroutines-best-practices). Do not use `GlobalScope`, blocking calls during composition, or WorkManager for subsecond checkpointing. Serialize mutations; tag asynchronous results with document ID/source revision and ignore stale results. Off-screen transforms build a candidate output and publish once; cancellation/failure keeps the original.

Persist raster, dimensions/mode, file association, saved-revision marker, tool settings, viewport, active mask/preview/text, and the pre-preview state needed to cancel. Keep large payloads in versioned private files, with a small session manifest and atomic generation switch. Recovery files must not live in evictable cache. Exclude draft pixel payloads from automatic backup/device transfer by default; preference backup does not imply restored external URI access.

Checkpoint 500ms after completed edits or preview changes and request a best-effort flush when the UI stops, through lifecycle-aware integration. Continuous editing must also checkpoint at a bounded interval (initially 5 seconds), so debounce cannot indefinitely delay recovery. Serialize writers across tasks. Do not depend solely on `onStop`, and never block the main thread waiting for a flush. Process termination can lose changes after the last completed checkpoint; show save/recovery errors without claiming success.

Restore the last valid generation after relaunch, preserve pending-preview cancellation semantics, and never overwrite external documents during recovery. Retain completed undo history through configuration/activity recreation; reset it after process restart and disclose this limit. Close/Discard removes that session from the recoverable manifest without deleting externally saved files. If multi-task support is delivered, recover every document, offering a temporary recovery picker when the system cannot recreate separate tasks.

Performance targets are product goals: smooth continuous drawing on a declared 2048 × 2048 test document, typical 16ms frame budget at 60Hz, no visible whole-screen recomposition per sample, and progress/cancel for work above 500ms. Measure end-to-end input latency, frame timing, allocations, and decode/transform/save duration on stated hardware. Respond to memory pressure by dropping recreatable resources/old history before active content. Add Baseline Profiles and Macrobenchmark coverage once the main editing flow exists and measured traces identify startup/interaction work worth optimizing.

## 14. Verification and acceptance

Use JVM tests for deterministic geometry/masks/validation/revision rules; instrumented tests for bitmap/text/codecs/providers/lifecycle; Compose UI tests for semantics/actions/adaptation; physical-device checks for stylus, touch, trackpad, and system behavior. Use exact pixel fixtures for hard-edged operations and documented tolerances for antialiasing/fonts/textures.

| ID | Acceptance scenario |
|---|---|
| A01 | Fresh launch opens a clean 800 × 600 white image with the specified brush/colors; no onboarding blocks editing |
| A02 | A 1px Pencil path has identical image coverage at different viewport zooms/display densities; Undo removes one stroke |
| A03 | Fast input has no gaps; cancellation, lost focus, and two-finger navigation leave no partial or accidental marks |
| A04 | Fill reaches four-connected pixels only, clips to the mask, and does not add history for a no-op |
| A05 | Eraser produces opaque Background pixels; theme changes leave exported colors unchanged |
| A06 | All delivered brushes/shapes are labeled and reachable in compact/expanded layouts; textures remain stable during preview and redo |
| A07 | Shape Apply commits once; Cancel restores exactly; curve/polygon/proportion controls work without multitap gestures |
| A08 | Free-form/inverse selection moves clear only masked source pixels; cancelling restores original pixels and dimensions |
| A09 | Copy/Cut failure changes nothing; PNG masks and inserted partial alpha composite correctly; oversize insertion handles Expand/Keep/Cancel |
| A10 | Color-key insertion skips exact key matches, preserves near-matches, and retains the captured key when Background changes |
| A11 | Unicode/IME text preserves composition/selection; keyboard visibility does not alter image size; preview and committed text metrics agree |
| A12 | Aspect-locked resize, exact quarter-turn/flip fixtures, canvas resize, skew bounds, and monochrome threshold produce expected results; Undo restores dimensions/mode |
| A13 | Invalid/oversized/allocation-failing operations preserve the original; asynchronous results cannot mutate a different or newer document |
| A14 | Zoom/pan/grid/ruler/overview/Focus mode change no stored pixels, history, or dirty state; image-anchored zoom remains stable |
| A15 | Open transparent PNG and EXIF-oriented JPEG; verify white flattening, one orientation adjustment, sRGB handling, and PNG round-trip pixels |
| A16 | Cancel/recreate during Open/Save as handles the result once; temporary/revoked URI access preserves recovery and offers Save as |
| A17 | Editing during Save marks only the encoded revision clean; external conflict and failed/truncated-provider writes retain private draft/output |
| A18 | New/Open/Close image Save/Discard/Cancel includes pending content and preserves work on cancellation/failure; Save/tool changes apply valid previews, while focus changes do not; Back-to-home does not repeatedly prompt |
| A19 | Activity recreation/live resizing retains document/history/preview/viewport; process restart restores checkpointed content and resets history |
| A20 | Interrupted/corrupt recovery writes fall back to a valid generation; continuous drawing checkpoints within the stated interval; all recovered task documents remain reachable |
| A21 | Test 360 × 800, 600 × 900, 840 × 900, 1200 × 800, 1600 × 1000, and 1200 × 400dp; split screen, folds, external display, edge-to-edge/caption/IME insets, and live resizing |
| A22 | Test 200% font scale, RTL, light/dark/dynamic themes, TalkBack, Switch Access, keyboard focus, labels, and touch targets. No controls clip or overlap |
| A23 | Predictive Back commit/cancel, IME-first Back, sheet dismissal, preview cancellation, and Focus mode exit preserve state and system task navigation |
| A24 | Physical-device touch/stylus/mouse/trackpad tests cover palm rejection, hover, eraser end, context actions, pan/pinch, scrolling, shortcuts, and modifier release |
| A25 | Sharesheet/clipboard recipients can read only the intended URI; no broad permissions are requested; optional codecs/printing have API-level fixtures before advertisement |
| A26 | Profile declared hardware for frame/input latency, allocation/memory limits, large transforms, cancellation, recovery I/O, and main-thread stalls |

For implementation milestones run `:paint:testDebugUnitTest`, `:paint:assembleDebug`, `:paint:lintDebug`, and `:paint:assembleDebugAndroidTest`, followed by `:paint:connectedDebugAndroidTest` on the local Small Desktop emulator with its `ANDROID_SERIAL` explicitly selected. Never run tests on connected ADB remote devices. See [Testing](TESTING.md) for commands and manual checks for system gestures, screen readers, physical input, provider failure, and device memory behavior.

The core implementation adds unit, Compose, storage, and lifecycle tests in
`:paint`. Extended-milestone acceptance rows are not claims of implemented
functionality. Hardware-dependent manual checks remain part of release validation.
