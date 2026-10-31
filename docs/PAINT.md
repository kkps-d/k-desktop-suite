# KD Paint

An independently launchable Android app (`com.kkps.kdesktopsuite.paint`) with one
image per window and support for multiple independent windows. There are no document or ribbon tabs. File/View are menus;
two single-row drawing toolbars scroll horizontally in narrow windows. The canvas centers
each axis on which the image fits; larger images pan within the viewport.

Run the `paint` configuration in Android Studio; see
[Development](DEVELOPMENT.md) for setup and builds. Controls follow system
appearance and dynamic color, with the suite's green fallback palette.

## Drawing and editing

The core supports Pencil, nine brush styles, Fill, Eraser, Eyedropper, rectangular
selection and clipboard operations, all 23 classic Paint shapes, native multiline text,
undo/redo, PNG/JPEG files and sharing, settings, and durable private recovery.

### Navigation and input

The Hand tool pans with one finger; pinch zoom and two-finger pan remain available
with editing tools as well, including gestures that start in the gray area outside the image.
Adding a second finger restores any selection or shape move/resize started by the first finger.
Hold Space and drag to pan with a mouse; key repeats never draw. A Space tap activates
once on release. Arrow keys position a visible keyboard cursor; Space starts/finishes
strokes, shapes, and rectangular selections, and Escape cancels an unfinished keyboard edit.
Single touches outside the image do not draw or select. Switching to a different drawing tool clears the selection and commits any
pending move or paste. Returning to the active tool preserves pending edits.

### Toolbars and colors

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

### Brushes and shapes

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

### Text and transforms

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
Modern desktop and the common mobile view remain deferred. See the
[roadmap](ROADMAP.md) for features beyond those available in the current editor.

## Desktop windows and settings

**File → New window** or **Ctrl+Shift+N** opens a blank image in a separate Android
task, using the default canvas size. Supported systems also offer New window in
their window controls. Each window keeps its own edits, undo history, pending
text/shapes, viewport, and file picker. Settings and recent files are shared.
Android controls window placement; systems without desktop windowing can show
the tasks full screen or in split screen.

**New image / Ctrl+N** and **Open / Ctrl+O** still replace the image in the current
window. Opening a file already open in another Paint window brings that window
forward and preserves the current image. Save as cannot target a file owned by
another Paint window. File identity follows the content URI supplied by the
provider; different provider URIs for the same underlying file may still require
the existing save-conflict decision.

**Close image / Ctrl+W** closes only the current window, offering Save / Discard /
Cancel for unsaved pixels or pending edits. Cancelled or failed saves keep it open;
newer edits made during a save also keep it open. A confirmed close removes that
window from recovery. Android's system Close or task removal can bypass app
confirmation; Paint retains its latest checkpoint for recovery in that case.

On systems with custom desktop captions, the menu/title/action row moves into
an unobstructed caption span at least 500dp wide, scaled with system text size.
Long document titles truncate with an ellipsis. System window buttons remain clear,
and the title area allows window dragging. Narrow windows, short
captions, and larger text keep the row below the system caption. Settings and
focus mode retain the system caption clearance.
The integrated header has no extra side padding; below the title bar, it keeps
8dp of padding on both sides.

Settings → On startup lets you reopen previous windows and drafts (the default),
start a new image at the default canvas size, or ask each time a previous session
exists. Reopening restores each retained image in its own window and returns to
the last focused window. Ask presents one decision for the previous session;
Start new image starts a fresh session. Android activity and process recreation
resume the matching window regardless of the startup preference. Undo survives
activity recreation but resets after process restart.
Default canvas dimensions save automatically when valid; Reset beside the inputs
restores 800 × 600. These defaults apply to new images only.

## Files and recovery

Open and Save as use Android's document picker without broad storage permissions.
Save as and File → Share this image… first offer matching PNG/JPEG format dialogs.
Sharing exports a copy through Android's share sheet; JPEG uses the quality set in
Settings. Sharing leaves the current file association and unsaved state intact.
Opening transparent images flattens them on white; inserted image selections
retain their alpha until composited. Image colors remain independent of the app
appearance.

Unsaved replacement offers Save / Discard / Cancel. Provider conflicts require
an explicit overwrite or Save as; provider writes are not universally atomic.

Private recovery uses PNG generations, a 500 ms debounce, a five-second maximum
checkpoint interval during editing, and best-effort background flushing. Process
termination can lose changes after the last successful checkpoint. Undo survives
activity recreation but resets after process restart. Recovery never overwrites
original files. Each window has a separate recovery directory, with an atomic
session index tracking membership. Existing single-image recovery migrates once;
its original files remain intact. Corrupt window drafts remain preserved while
healthy windows can reopen. A window that fails to launch retains its recovery
entry for the next launch.

## Further documentation

- [Development](DEVELOPMENT.md): setup, builds, and implementation ownership.
- [Testing](TESTING.md): automated coverage and manual verification.
- [Roadmap](ROADMAP.md): deferred editing tools, layouts, printing, and codecs.
- [Layout references](references/kd-paint/README.md): design mockups and an earlier
  implementation reference.
