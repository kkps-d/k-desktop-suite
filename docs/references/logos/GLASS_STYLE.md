# Alternate glass logo style

Approved references: **KD Explorer aquamarine glass** and **KD Notepad glass**
(5 November 2026), and **KD Paint glass, Rolled rim** (7 November 2026).
This is a second visual style for the suite. It does not replace Explorer's
amber Soft geometry selection, beveled Notepad, or refined ceramic Paint
Android artwork.

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

## Notepad checkpoint

Open the [Notepad preview](kd-notepad-glass.html), edit its
[source](sources/kd-notepad-glass.html), or inspect the square 1024 × 1024 renders:
[light](kd-notepad-glass-light.png) and [dark](kd-notepad-glass-dark.png).

The notebook backing, upper pen barrel, and pointed pen cone use the same
blue-to-aquamarine glass material. The barrel and cone transmit the actual
artwork underneath, with slight refraction and fine reflective edges. A small
silver writing tip terminates the glass cone. The silver grip and three centered
silver bindings keep their metal finish. Neutral paper, blue KD ink, page rules,
notebook tilt, and pen placement retain the established Notepad geometry.

The approved defaults match Explorer: `#29b99d` tint, 34% density, 80% edge
reflections, and 35% pen refraction. The pen's original full pointed silhouette
is preserved; only its final 2.6 coordinate units (in the 100-unit square) are
silver. Square originals and all six crop previews use these same defaults.
The standalone export uses the same bundled runtime and regeneration process
described above.

## Paint checkpoint

**Rolled rim** is the selected Paint glass design. Open the
[preview](kd-paint-glass.html), edit its [source](sources/kd-paint-glass.html),
or inspect the square 1024 × 1024 renders: [light](kd-paint-glass-light.png) and
[dark](kd-paint-glass-dark.png). The preview opens on Rolled rim and retains
**Soft joins** as a second accepted alternative.

Only the cup and handle use glass. The golden pencil, wooden brush with silver
ferrule and brown bristles, and purple marker retain their original materials.
The cup retains its original 48 × 35 body, bottom corners, and handle footprint.
The rolled lip is slightly rounded; its shallow rear curve mirrors the front
curve. Softly flared handle roots join behind the cup wall without a visible
attachment bar across the face. The tools extend inside the transparent cup,
and KD uses Explorer's unfilled etched outline with reflection-linked highlights.

Defaults remain `#29b99d` tint, 34% density, 80% edge reflections, and 35% cup
refraction. The pencil's gray graphite fills the upper half of its sharpened
tip, sharing the wood triangle's sloping edges so there is no pale gap beside
the graphite. The correction applies to both retained alternatives.
The standalone export uses the same bundled runtime and regeneration process
described above. The refined ceramic white cup is the native Paint artwork.

## Verification and scope

The light/dark artwork and small crop contact sheet were visually reviewed using
an offline canvas renderer. Runtime checks exercised eight canvases, tint and
density changes, reflection changes in the KD area, and saved-state restoration.
Notepad's light/dark artwork, glass pen cone, silver writing tip, and small crops
were also visually reviewed. Runtime checks covered its tint, density,
reflections, pen refraction, and saved-state restoration.
Paint's selected light/dark renders and crop previews were visually reviewed,
including the pencil's corrected graphite edges. Checks covered both retained
variants, 16 canvases, eight independent controls, refraction, reflection changes,
and saved-state restoration. The rear and front rim curves are mirrored, and
the graphite triangle's two sides align with the sharpened wood tip.
The PNGs were rendered directly from the approved canvas code at 1024 × 1024.
The exported document was checked for bundled scripts and local documentation
links. Browser layout verification remains pending because local file previews
were blocked by the browser URL policy.

This checkpoint changes design references and documentation only. No native
resources, generator, app appearance setting, or device deployment changed;
Android builds and emulator tests were not rerun. These are approved alternate
design references, with native integration and an in-app style selector outside
this checkpoint.
