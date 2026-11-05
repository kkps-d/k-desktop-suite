# Alternate glass logo style

Approved reference: **KD Explorer aquamarine glass**, 5 November 2026.
This is a second visual style for the suite. It does not replace Explorer's
amber Soft geometry selection or the beveled Notepad/Paint Android artwork.

Open the [Explorer preview](kd-explorer-aquamarine-glass.html), edit its
[source](sources/kd-explorer-aquamarine-glass.html), or inspect the square
1024 × 1024 renders: [light](kd-explorer-glass-light.png) and
[dark](kd-explorer-glass-dark.png).

## Material and geometry

- Preserve each logo's established silhouette, proportions, tool placement,
  and geometric KD outlines. Change the material rather than the composition.
- Use translucent blue, green, and aquamarine foreground layers. Composite them
  over the real contents, so paper contours and rules remain visible through
  glass. Do not simulate transparency with an opaque pale fill.
- Suggest thickness with denser tinted edges, fine reflective rims, and restrained
  directional highlights. Keep the cool-to-green lighting consistent across
  objects. Tie reflected highlights to the same reflection-strength control.
- Keep any refraction slight enough to preserve the contents' readable shapes.
  Apply it only through glass; paper above a glass lip remains undistorted.
- Keep square icon backgrounds flat white or `#1a1a1a`. All gradients belong to
  foreground materials. Show both appearances and squircle, rounded-square,
  and circle masks without repositioning the artwork.
- When KD is engraved in glass, use a fine shaded outline with a reflected glint.
  Leave its interior unfilled, so the underlying glass and contents continue
  through the letters. Keep the KD path and placement unchanged.
- Apply glass selectively. Paper stays paper, and silver bindings and metal pen
  sections keep their existing materials. KD printed on paper keeps its ink.

## Explorer checkpoint

| Setting | Approved value |
| --- | --- |
| Glass tint | `#29b99d` |
| Glass density | 34% |
| Edge reflections | 80% |
| Paper refraction | 35% |

The folder remains upright. Front and back share the same width, and a single
back outline joins the compact ear without a seam. Both sheets extend close to
the bottom with a small glass margin. The front is transparent enough to reveal
the full sheet contours. KD is an unfilled engraving whose highlights follow
the edge reflections. The latest outline is slightly more distinct than the
earlier faint version; it has no dark interior fill.

The source is a self-contained CSS/canvas fragment with local design controls
and saved-state restoration. The standalone export bundles its preview runtime
and the same pinned dependencies used by the other Explorer exports; licenses
are preserved in [runtime notices](kd-preview-NOTICES.txt). Regenerate exports
with the visualize skill's `scripts/render.py`, then re-inline those dependencies
to retain offline operation.

## Verification and scope

The light/dark artwork and small crop contact sheet were visually reviewed using
an offline canvas renderer. Runtime checks exercised eight canvases, tint and
density changes, reflection changes in the KD area, and saved-state restoration.
The PNGs were rendered directly from the approved canvas code at 1024 × 1024.
The exported document was checked for bundled scripts and local documentation
links. Browser layout verification remains pending because local file previews
were blocked by the browser URL policy.

This checkpoint changes design references and documentation only. No native
resources, generator, app appearance setting, or device deployment changed;
Android builds and emulator tests were not rerun. Notepad's backing and blue pen
barrel are the next preview exploration, not an approved replacement yet.
