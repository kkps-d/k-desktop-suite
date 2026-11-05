# Logo design studies

Design checkpoint: 5 November 2026. These are HTML mockups; the Android app
icons have not been replaced.

Open the previews directly in a browser. They include their styling and scripts
and do not require a server or a network connection.

## Selected direction

[Printed KD — square originals and launcher crops](kd-printed-blue-cup.html)
contains the current design for both apps:

- **KD Notepad:** a light-blue notebook and matching pen, with KD printed on
  the notebook.
- **KD Paint:** a terracotta cup printed with KD, holding a golden pencil,
  teal paintbrush, and purple marker.
- Square originals with squircle, rounded-square, and circle crop previews.
- White icon backgrounds in light mode and neutral very dark gray (`#1a1a1a`)
  backgrounds in dark mode. Foreground colors and KD lettering adjust by mode.
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

## Sources and verification

The matching files under [sources/](sources/) are editable HTML fragments used
for the inline previews. The HTML files beside this README are standalone
exports with a bundled preview runtime. Edit the fragments and regenerate the
corresponding exports with the visualize skill's `scripts/render.py` helper.
The fragments also work directly in the inline preview host.

Browser checks covered the current square originals, all six crop previews,
light/dark icon palettes, unchanged preview appearance when switching icon
modes, and layout at 320px and 768px viewport widths. Earlier explorations were
checked for variant navigation, light/dark rendering, and narrow layouts.
Android builds and emulator tests are not applicable to these HTML-only
mockups. Production launcher resources and platform integration remain future
work.
