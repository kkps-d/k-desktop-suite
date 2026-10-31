# Roadmap

These are deferred features and ideas retained from earlier proposals, not
delivered functionality or a committed schedule. Scope a feature and check the
current code before implementing it. The [Notepad guide](NOTEPAD.md) and
[Paint guide](PAINT.md) describe current behavior.

## KD Notepad

- Find/Replace.
- Rich text and legacy encodings.
- Large-file optimizations.

## KD Paint

- Selection: free-form masks, inverse selection, and background-color-key
  compositing. Define mask, clipboard alpha, and cancellation behavior before
  enabling the currently disabled Transparent option.
- Shape styles: textured outline/fill choices beyond the current controls.
- Image modes and metadata: monochrome conversion, physical units/DPI, and
  expanded image properties.
- View aids: rulers, a nonexported pixel grid, and an overview navigator.
- Printing through Android's print framework, with scaling/orientation choices;
  printing must preserve file association and unsaved state.
- Additional codecs, potentially BMP, GIF, TIFF, and WebP. Validate import/export
  separately, supported API levels, round trips, and warnings for discarded
  animation frames/pages or reduced color before advertising support.
- Modern desktop and a shared mobile presentation. Keep document editing and
  viewport behavior independent of the surrounding controls.
- Pressure/tilt brushes and reusable custom tool presets.
- Measured performance work, including large-document handling, Baseline Profiles,
  and Macrobenchmark coverage when traces justify it.

Nine brush styles, 23 shapes, image/selection transforms, and PNG/JPEG sharing
already exist; the old core/extended milestone split is retired.

Layers, persistent vector objects after committing, transparent document
backgrounds, generative editing, automatic background removal, cloud accounts,
and a mandatory gallery/start screen were outside the original Paint scope.
They require a separate product decision before inclusion.
