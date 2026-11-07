# Logo design studies

Design checkpoint: 7 November 2026. Android uses beveled Notepad with silver
bindings and the refined ceramic Paint cup. KD Preview and KD Explorer have selected logo studies.
The HTML previews preserve the design studies. The approved
[alternate glass style](GLASS_STYLE.md) is maintained separately from the
current default artwork.

Open the previews directly in a browser. They include their styling and scripts
and do not require a server or a network connection.

## Current user-preferred versions

### KD Notepad and KD Paint

**Beveled edges with silver bindings** remains the shipped Notepad design.
Paint now uses the refined ceramic design below. The earlier pair is preserved
in the [beveled preview](kd-logo-beveled.html); edit its
[source](sources/kd-logo-beveled.html). This replaces the earlier flat finish
and black Notepad bindings; the studies below preserve those explorations.

- **KD Notepad:** neutral paper with blue KD print and a slim blue cover,
  three filled silver bindings centered across the top edge, and a blue pen
  with a silver lower section. The bindings, cover, page edges, and pen use
  the selected beveled treatment. Blue remains in the cover and pen rather
  than filling the page.
- **KD Paint:** a compact white cup with a small handle and dark KD lettering,
  holding a golden pencil, a brown wooden paintbrush with a silver collar and
  pointed bristles, and a purple capped marker. The pencil and marker use
  slightly richer colors than the earlier explorations, with beveled shading
  on the foreground materials.
- Square originals with squircle, rounded-square, and circle crop previews.
- White icon backgrounds in light mode and neutral very dark gray (`#1a1a1a`)
  backgrounds in dark mode. Colored foregrounds adjust by mode; the cup stays
  white ceramic with subtle edge shading and dark KD lettering.
- Gradients apply only to foreground objects. Icon backgrounds stay flat.
- Subtle dark outlines on light icons and light outlines on dark icons.
- The icon appearance selector affects the originals and crops independently
  of the surrounding preview's theme.

The crop previews mask the square artwork without repositioning or rescaling
its foreground items.

### Refined ceramic KD Paint

**Refined ceramic** is the latest approved original-style Paint design reference
(7 November 2026). Open the [preview](kd-paint-ceramic-refined.html) or edit its
[source](sources/kd-paint-ceramic-refined.html). It carries the selected glass
study's rolled rim and softened handle joins into the opaque white ceramic cup,
with solid dark KD lettering and the original colored tools.

- The front and rear rim curves have the same shallow bow. Default rim depth is
  1.7 units in the 100-unit artwork; bevel strength is 100%.
- The cup retains its 48 × 35 body, bottom corners, and handle footprint. Flared
  handle roots join behind the face. Light mode adds a clearer gray contour and
  stronger outer-edge shading to keep the white handle visible on white. Dark
  mode retains its previous shading.
- Graphite fills the upper half of the sharpened pencil tip and meets both
  sloping edges. KD keeps its geometric path and original size and position.
- Square 1024 × 1024 renders: [light](kd-paint-ceramic-light.png) and
  [dark](kd-paint-ceramic-dark.png). The preview includes both appearances and
  squircle, rounded-square, and circle crops, plus rim-depth and bevel controls.

The eight canvases and small crops were visually reviewed with an offline canvas
renderer. Runtime checks covered both controls and saved-state restoration;
before/after pixels confirmed stronger light-mode handle contrast and unchanged
dark-mode artwork. The source, bundled standalone export, render dimensions,
and local links were checked. Browser layout verification remains pending because
the browser URL policy blocked local file previews. The export uses the same
bundled runtime dependencies and [notices](kd-preview-NOTICES.txt) as the other
logo studies.

This design is now used by Paint's light/dark in-app icons, adaptive launcher
foregrounds, and themed silhouette through `scripts/generate_app_logos.py`.
Notepad artwork is unchanged. The Android build, all 51 Paint JVM tests, lint
(no errors, three warnings), and both existing About/logo checks passed on local
Small Desktop. Regeneration was deterministic; generated vectors and adaptive
crops were visually reviewed offline. See [Testing](../../TESTING.md) for limits.


### KD Preview (planned)

**Crystal frame** is the latest approved magnifier design (7 November 2026).
Open its [preview](kd-preview-crystal-glass.html) or edit its
[source](sources/kd-preview-crystal-glass.html). Square 1024px renders:
[light](kd-preview-glass-light.png) and [dark](kd-preview-glass-dark.png).

- Broad beveled aquamarine rim and continuous glass grip.
- The original 23-tile lens-only mosaic with seven file-type symbols, 110%
  refraction, and 10% lens tint. Glass tint is `#29b99d`, density 34%, reflections 80%.
- KD keeps its original size and position, with unfilled etched outlines and
  brighter glints tied to edge reflections. The underlying mosaic remains visible.
- Light/dark originals and all six crop previews were visually reviewed offline.
  Checks cover the public Lucide API, delayed symbol initialization, four design
  controls, state restoration, and no letter fill. The standalone export bundles
  its dependencies with the existing [notices](kd-preview-NOTICES.txt).

The earlier silver-rim design is preserved below for comparison.

**Lens-only mosaic with silver rim** was the earlier selection. Open the
[standalone preview](kd-preview-liquid-mosaic.html) or edit its
[source](sources/kd-preview-liquid-mosaic.html). It opens on the selected variant;
the earlier full-background mosaic remains available for comparison.

| Setting | Selected value |
| --- | --- |
| Mosaic items | 23 |
| Glass refraction | 110% |
| Aqua tint | 10% |

- A centered looking glass with a thick silver rim and handle exiting diagonally
  at the bottom right. Light mode uses darker silver for contrast.
- A diagonal masonry mosaic with irregular widths and proportional file-type
  symbols for images, text, audio, archives, PDFs, spreadsheets, and video.
  The mosaic is clipped to the lens; the exterior stays white or `#1a1a1a`.
- Centered KD uses the same outlined lettering and dimensions as the shipped
  Paint logo. Refraction affects only the mosaic; an aqua tint covers both
  mosaic and KD, with a darker tint in dark mode.
- Light-mode mosaic colors are more saturated. Crop previews include squircle,
  rounded square, and circle masks.

Square 1024 × 1024 PNG renders: [light](kd-preview-light.png) and
[dark](kd-preview-dark.png). These preserve the selected browser rendering,
including the glass distortion. KD Preview Android integration is still planned.

The optical map is an independent implementation informed by the
[JavaScript liquid-glass displacement approach](https://github.com/jbbixler/liquid-glass).
The file symbols use the export runtime's bundled Lucide artwork. No network
connection is needed to open the standalone preview.

### KD Explorer

**Soft geometry with a seamless, shortened back** is the user's latest selection.
Open the [preferred preview](kd-explorer-soft-geometry.html) or edit its
[source](sources/kd-explorer-soft-geometry.html).

- An upright amber open folder with softly rounded corners and subtle foreground
  shading. The front and back have the same width, with a taller front pocket.
- The ear and back form a continuous surface without a dividing highlight;
  both were shortened in the final refinement.
- Two lightly fanned paper sheets sit farther inside the pocket. The geometric
  KD print retains the same dimensions as the other suite logos and is centered
  on the front.
- Flat white and `#1a1a1a` icon backgrounds, adjusted foreground palettes, and
  subtle opposite-color edge outlines. Square originals include squircle,
  rounded-square, and circle crop previews.

Square 1024 × 1024 PNG renders: [light](kd-explorer-light.png) and
[dark](kd-explorer-dark.png). These are design references; the study has not yet
been applied to Explorer's Android resources.

## Alternate glass style

**Aquamarine glass** is the approved alternate style, with Explorer as its first
completed reference. Open the [preview](kd-explorer-aquamarine-glass.html) or edit
its [source](sources/kd-explorer-aquamarine-glass.html). See the
[glass style guide](GLASS_STYLE.md) for material rules, the exact approved values,
verification, and scope.

The upright folder has transparent blue/green layers, long visible sheets,
reflective rims, and unfilled etched KD lettering. The approved controls are
`#29b99d` tint, 34% density, 80% reflections, and 35% refraction. Square 1024px
renders: [light](kd-explorer-glass-light.png) and [dark](kd-explorer-glass-dark.png).
The amber Explorer selection, beveled Notepad, and refined ceramic Paint
remain the default designs.

**KD Notepad glass** extends the same material to the notebook backing, upper
pen barrel, and pointed pen cone, ending in a small silver writing tip. The
paper and blue KD print, three silver bindings, and silver pen grip retain their
existing materials and geometry. Open its [preview](kd-notepad-glass.html) or
edit the [source](sources/kd-notepad-glass.html). Square 1024px renders:
[light](kd-notepad-glass-light.png) and [dark](kd-notepad-glass-dark.png).
It uses the same tint, density, reflection, and refraction defaults as Explorer.
Android settings now offer this Glass style alongside Classic.

**KD Paint glass (Rolled rim)** is the selected Paint alternate style. Only the
cup and handle use glass; the original colored tools remain intact. The shallow
rear rim matches the front curve, the rounded lip and handle joins retain the
original cup proportions, and KD uses Explorer's unfilled etched outlines. The
pencil's graphite now follows the full width of the sharpened tip's upper half.
Open its [preview](kd-paint-glass.html) or edit the
[source](sources/kd-paint-glass.html). Rolled rim opens first; Soft joins remains
available as a second accepted alternative. Square 1024px renders:
[light](kd-paint-glass-light.png) and [dark](kd-paint-glass-dark.png).
The [glass style guide](GLASS_STYLE.md) records the selected values and scope;
Android settings offer Glass alongside the default refined ceramic Classic cup.

## Earlier explorations

- [Explorer directions](kd-explorer-logo-directions.html): Classic folder,
  Open folder, Folder compass, and File drawer. Open folder was selected.
- [Upright open folder](kd-explorer-open-folder.html): level folder layers,
  fanned papers, and the earlier beveled finish.
- [Modern Explorer treatments](kd-explorer-modern-folder.html): Soft geometry
  and Flat cut. Soft geometry was selected and refined into the version above.

- [Initial styles](kd-logo-studies.html): Material, folded paper, KD monogram,
  and desktop classic. Material was selected.
- [Material branding variants](kd-material-branded.html): corner seal, printed
  KD, brand band, and KD centerpiece. Printed KD was selected and refined into
  the current design above.
- [First Printed KD pair](kd-printed-blue-cup.html): the blue notebook and a
  terracotta utensil cup, with square originals and launcher crops.
- [Paint refinements](kd-paint-refinements.html): balanced pot, compact cup,
  and two-tool pot. Compact cup was selected, then refined with white ceramic,
  richer utensil colors, and a wooden brush with a pointed tip.
- [White compact cup pair](kd-compact-white-cup.html): the selected Paint logo
  beside the earlier all-blue Notepad design.
- [Notepad blue accent variants](kd-notepad-refinements.html): blue spine,
  blue header, blue binding, and blue ink. Blue binding was selected and
  refined with centered solid black bindings, quieter page rules, and a
  mostly blue pen.
- [Flat pair with black bindings](kd-notepad-binding.html): the previous
  preferred pair before the gradient exploration and silver-binding change.
- [Gradient treatments](kd-logo-gradients.html): Soft light, Satin materials,
  Beveled edges, and a Flat reference. The user selected Beveled edges, then
  requested silver bindings using that same finish.

## Sources and verification

### Android assets

Both apps use Classic gradient vectors and the approved Glass PNG renders, packaged by
[`scripts/generate_app_logos.py`](../../../scripts/generate_app_logos.py), using
the approved geometry and outlined KD lettering without a device-font dependency.
Run `python3 scripts/generate_app_logos.py` after changing the native artwork.
The generator also copies the approved 1024px glass renders into `drawable-nodpi`
and creates six launcher choices through stable activity aliases. Settings offer
Classic/Glass and System/Light/Dark independently for each app; preferences update
all its open windows. Explicit icon palettes apply to Welcome/About too, while
System retains app-appearance artwork and Android-appearance launcher resources.

- `drawable/ic_notepad.xml` and `drawable/ic_paint.xml` are the light square
  originals; their `_dark` counterparts use the adjusted dark palette.
- `mipmap-anydpi-v26/ic_launcher.xml` supplies adaptive launcher layers. The
  foreground vectors reserve an 18dp margin in the 108dp layer, proportionally
  at every output size. Android supplies the launcher mask.
- Default and night foreground/background resources follow Android appearance.
  Backgrounds remain flat white or `#1a1a1a`. Launchers may cache the previous
  palette until their icons refresh; Small Desktop's Launcher3 refreshed after
  restarting its process.
- Optional Android themed icons use a separate monochrome silhouette with KD
  cut out of the notebook/cup. Their colors are controlled by the launcher.
- Welcome and About use the square originals through the shared `AppLogo`
  component. They follow the app's selected appearance, including an explicit
  Light/Dark override that differs from Android. Dynamic color does not recolor
  the brand artwork.

### HTML studies

The matching files under [sources/](sources/) are editable HTML fragments used
for the inline previews. The HTML files beside this README are standalone
exports with a bundled preview runtime. Edit the fragments and regenerate the
corresponding exports with the visualize skill's `scripts/render.py` helper.
The fragments also work directly in the inline preview host.

The KD Preview standalone export additionally inlines its pinned Lucide 1.17.0
and Floating UI core 1.7.3 / DOM 1.7.4 dependencies, with licenses preserved in
[notices](kd-preview-NOTICES.txt). Re-inline these scripts after regenerating that
export to retain offline operation. Its PNGs capture each selected square original
at 1024px in Chrome; the KD canvas redraws at the output resolution.

The Explorer exports also inline those same pinned runtime dependencies;
their licenses are in the existing [runtime notices](kd-preview-NOTICES.txt).
After regenerating them, re-inline the scripts to retain offline operation.
The Explorer PNGs capture the selected square originals at 1024px, with the KD
canvas drawn at the same resolution.

Browser checks covered the current square originals, all six crop previews,
light/dark icon palettes, unchanged preview appearance when switching icon
modes, centered and evenly spaced silver bindings, foreground gradients with
flat icon backgrounds, and layout at 320px and 768px viewport widths. Earlier
explorations were checked for variant navigation, light/dark rendering, and
narrow layouts.
The KD Preview export was checked offline in Chrome at 320px and 768px widths,
with the selected 23/110%/10% defaults and Lens-only mosaic first. Both variants,
all crop masks, centered frame/glass geometry, sharp KD under refraction changes,
and the light/dark 1024px PNG renders were checked. The Preview study changes no
Android resources, so Android builds/tests were not rerun for this checkpoint.
The HTML previews are design references; Android uses the native resources above.
The Explorer studies were checked offline in Chrome at 320px and 768px widths,
including variant navigation, all crop masks, both icon palettes, aligned folder
layers, an upright outer folder, and accent changes. The selected light/dark PNGs
were visually reviewed. No Android resources changed in this checkpoint, so
Android builds and tests were not rerun.
See [Testing](../../TESTING.md) for production icon verification.
