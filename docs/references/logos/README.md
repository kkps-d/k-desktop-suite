# Logo design studies

Design checkpoint: 5 November 2026. These are HTML mockups; the Android app
icons have not been replaced.

Open the previews directly in a browser. They include their styling and scripts
and do not require a server or a network connection.

## Current user-preferred version

**Beveled edges with silver bindings** is the user's latest selection.
Open the [preferred preview](kd-logo-beveled.html) or edit its
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

## Earlier explorations

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

The matching files under [sources/](sources/) are editable HTML fragments used
for the inline previews. The HTML files beside this README are standalone
exports with a bundled preview runtime. Edit the fragments and regenerate the
corresponding exports with the visualize skill's `scripts/render.py` helper.
The fragments also work directly in the inline preview host.

Browser checks covered the current square originals, all six crop previews,
light/dark icon palettes, unchanged preview appearance when switching icon
modes, centered and evenly spaced silver bindings, foreground gradients with
flat icon backgrounds, and layout at 320px and 768px viewport widths. Earlier
explorations were checked for variant navigation, light/dark rendering, and
narrow layouts.
Android builds and emulator tests are not applicable to these HTML-only
mockups. Production launcher resources and platform integration remain future
work.
