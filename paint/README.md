# KD Paint Classic core editor

An independently launchable Android app (`com.kkps.kdesktopsuite.paint`) with one
image open at a time. There are no document or ribbon tabs. File/View are menus;
two single-row drawing toolbars scroll horizontally in narrow windows. The canvas centers
each axis on which the image fits; larger images pan within the viewport.

The core supports Pencil, nine brush styles, Fill, Eraser, Eyedropper, rectangular
selection and clipboard operations, all 23 classic Paint shapes, native multiline text,
undo/redo, PNG/JPEG files, PNG sharing, settings, and durable private recovery.
The Hand tool pans with one finger; pinch zoom and two-finger pan remain available
with editing tools as well, including gestures that start in the gray area outside the image.
Adding a second finger restores any selection or shape move/resize started by the first finger.
Hold Space and drag to pan with a mouse; key repeats never draw. A Space tap activates
once on release. Arrow keys position a visible keyboard cursor; Space starts/finishes
strokes, shapes, and rectangular selections, and Escape cancels an unfinished keyboard edit.
Single touches outside the image do not draw or select. Switching to a different drawing tool clears the selection and commits any
pending move or paste. Returning to the active tool preserves pending edits.
The primary toolbar groups clipboard commands, selection, drawing tools, and primary/secondary
color targets. The secondary toolbar shows options for the active tool or selected color.
Eyedropper shows a live color preview while a pointer is held on the canvas and picks
the release position. It stays active and updates the selected primary or secondary color.
Assign to custom color is off by default; turning it on shows ten custom slots in the
secondary toolbar and also writes each pick to the selected slot. In Edit colors, Save custom color highlights
the custom slots; select a slot to replace it or Cancel to leave the slots unchanged.
Show toolbar / Hide toolbar toggles only the primary row. Text keeps this control in
its secondary row, including while editing and choosing colors.
Tool names hide below 600dp and optional labels below 840dp, adjusted for font scale.
Shape shortcuts adapt from three to ten; all 23 remain in More shapes. Controls never wrap. Color selection
is independent of drawing-tool selection and preserves floating shapes, text, and images.
The Transparent checkbox is visible but disabled until its raster behavior is implemented.
The Brushes dropdown shows actual stroke previews for Solid, Calligraphy left/right,
Airbrush, Oil, Crayon, Marker, Graphite, and Watercolor. Each style remembers its width;
seeded textures match the live preview and remain unchanged through undo/redo.
The Size dropdown previews common stroke widths at the current canvas zoom. Custom
size opens a dialog with the same preview and a 1–100 image-pixel input; changes
apply only after confirmation.
The Shapes icon dropdown offers lines, curves, basic geometry, arrows, stars,
callouts, a heart, and lightning. Drag to create a preview; for curves, place two
bend points; for polygons, add vertices and choose Commit to finish and apply in one action. Previews support
move/resize, stroke width, outline/fill colors, Enter to apply, and Escape to cancel.
Finished shapes also support the selection rotation handle, rotation/flip commands,
and resizing past either origin to mirror the geometry. Shapes remain editable until committed.
Text supports System plus the same bundled Noto Sans, Serif,
and Sans Mono fonts as Notepad, with formatting in the secondary toolbar.
Drag an uncommitted text box by its outside frame to move it; the interior keeps native
caret/character selection. Moving preserves text formatting and selection until Commit.
A visible 48dp move grip provides a larger touch target; activate it to enter a position.
Selection resize handles also accept a 48dp touch area.
Move/resize/skew opens one dialog with Move, Resize, and Skew tabs; text offers only Move and Resize.
Position, percentage/pixel sizing, aspect lock, rotation, and skew apply together on OK.
Selections and pending shapes/text keep their edits floating; Move/Resize keeps text
editable; shape transforms retain native geometry. Cancel preserves
the original preview, and committing remains one undoable edit.
Rotation and flip controls live beside Select in the primary toolbar. Left/right
buttons turn by 90 degrees; a separate button opens custom rotation, and
the Flip dropdown offers vertical/horizontal flips. They target a selection when
present, otherwise the image. Selections retain free-angle rotation handles.
The color dialog shows ten presets or custom slots per row, scrolling horizontally
in narrow windows. Swap colors stays in the color toolbar.
Selection content outside the canvas is clipped when applied. Canvas size
controls change the canvas bounds separately from image scaling.
Modern desktop and the common mobile view remain deferred, as do the extended
tools listed in the [Android specification](../docs/KD_PAINT_ANDROID_SPEC.md).

On systems with custom desktop captions, the menu/title/action row moves into
an unobstructed caption span at least 500dp wide, scaled with system text size.
Long document titles truncate with an ellipsis. System window buttons remain clear,
and the title area allows window dragging. Narrow windows, short
captions, and larger text keep the row below the system caption. Settings and
focus mode retain the system caption clearance.

Settings → On startup lets you reopen the last edited image or draft (the default),
start a new image at the default canvas size, or ask each time a previous session
exists. Android activity and process recreation resume the current session.
Default canvas dimensions save automatically when valid; Reset beside the inputs
restores 800 × 600. These defaults apply to new images only.

## Build and verification

Use Android Studio's JDK and the configured SDK:

```sh
./gradlew :paint:testDebugUnitTest :paint:assembleDebug :paint:lintDebug :paint:assembleDebugAndroidTest
ANDROID_SERIAL=emulator-5554 ./gradlew :paint:connectedDebugAndroidTest
```

The APK is `build/outputs/apk/debug/paint-debug.apk`. The first implementation was
verified on 24 October 2026 with 16 JVM tests and 17 instrumented tests on the API
37 desktop emulator. Lint reported no issues. The
[reference screenshot](../docs/references/kd-paint/classic-implemented.png)
documents the earlier ribbon layout.

Tests cover image-space coverage, fill connectivity, masks and cropping,
region-based history and saved revisions, centered/bounded viewport transforms,
native text selection across recreation, glyph coverage between preview and
raster output, two-finger gesture cancellation, PNG/alpha/JPEG orientation,
provider conflicts, saves while editing, cancelled replacement, durable recovery
fallback, and compact/short Classic command access at large font sizes.

Physical stylus/palm behavior, TalkBack/Switch Access, predictive Back gestures,
freeform caption dragging, external displays, third-party document-provider
failure modes, and performance on release hardware still require manual checks.
Provider replacement is not universally atomic. Process death can lose edits
newer than the last completed checkpoint; Undo history resets after process restart.

## Ownership

- `PaintScreen` owns Classic chrome; `PaintCanvas` owns viewport input/rendering
  and native text overlay integration. Neither introduces a mobile presentation.
- `PaintViewModel` coordinates actions, lifecycle state, revisions, and pending
  commands; the reusable document/viewport logic is independent of Classic chrome.
- `Raster`/`RasterHistory` prepare pixel changes on a worker, then commit through
  the single writer. Published rasters are not mutated by render consumers.
- `ImageRepository` stages and validates content URI I/O; `PaintRecovery` writes
  PNG generations with atomic manifest switching; `PaintSettings` uses DataStore.
