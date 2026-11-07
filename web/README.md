# KD showcase assets

Screenshot preparation for **KD Notepad** and **KD Paint** (KD means K Desktop).
Both Android apps are free. This folder is ready for a later showcase website;
no website or privacy-policy URL has been published in this phase.

## Start here

- [Screenshot inventory](INVENTORY.md): 12 Notepad and 13 Paint captures, captions,
  suggested roles, window bounds, and links to originals and web copies.
- [Notepad contact sheet](capture/notepad-contact-sheet.jpg)
- [Paint contact sheet](capture/paint-contact-sheet.jpg)
- [Machine-readable inventory](capture/inventory.json): paths, dimensions,
  crop rectangles, file sizes, SHA-256 hashes, and review notes.
- [Capture provenance](capture/conditions.json): build, device, and APK hashes.

## Files

| Location | Contents |
| --- | --- |
| `assets/artwork/interpretative-shrimp.png` | The user's original 1920 × 1080 artwork, copied without re-encoding. |
| `assets/screenshots/originals/{notepad,paint}/` | Untouched 1366 × 768 PNG captures from Android `screencap`. |
| `assets/screenshots/optimized/{notepad,paint}/` | JPEG window crops and two uncropped full-desktop copies, quality 90%. |
| `samples/` | Original UTF-8 showcase text files used in Notepad. |
| `content/privacy-policy.md` | Exact copy of the publication draft in `docs/PRIVACY_POLICY.md`. |
| `capture/` | Inventory, capture conditions, contact sheets, and local capture/preparation helpers. |

The artwork was supplied and authorized by the user. The Downloads original is
untouched. Both copies have SHA-256
`a000610436e947532e5411d45051470b761e78fbc518463e0ca118d07f80c51a`.
Paint's geometric/text sample was made using the real app's drawing tools.

## Capture conditions and review

Captured on **7 November 2026**, using only local **Small Desktop**
(`Small_Desktop`, `emulator-5554`, regular Android user 10). The display remained
1366 × 768 at 160dpi, with Android's light appearance unchanged. App appearance
was changed through each app's settings. Native freeform windows were resized:
desktop 1254dp wide, Paint tablet-size 900dp wide, and compact 420dp wide.
Compact shots are **resized Small Desktop captures, not physical-phone captures**.
Multi-window images retain both real windows and the intervening desktop.

Both current debug APK builds passed with `:notepad:assembleDebug
:paint:assembleDebug`; versions remain **0.1.0**, version code 1. The APKs came
from the existing dirty checkout at HEAD `3f87d79`, including pre-existing shared
UI changes. Historical signed release artifacts were not used. This is manual
workflow/visual capture, not a new regression-test result. No instrumented tests
or actions on the connected remote ADB device were performed.

All selected images and both contact sheets were visually reviewed. Initial
transition captures were replaced; inspection scratch images were removed.
The Notepad hero was recaptured taller to keep its final line visible. Compact
search was recaptured without touch-selection handles. Scrollable documents,
truncated compact tab/title labels, and horizontally scrolling toolbars are real
adaptive behavior. The 100% Paint scene intentionally shows only part of the
artwork. Settings, menus, the shape gallery's native Ellipse label, and native
canvas/text editing handles are intentional. No UI was generated or composited;
JPEGs only crop the recorded window bounds and encode the resulting image.
Rounded window corners can retain a few desktop pixels from the original capture.

The original private app data was backed up outside this public asset folder.
After capture, both apps' original drafts and preferences were restored and
verified by file-content hashes. Showcase windows were closed and the imported
showcase media removed. System size, density, and appearance remained at their
original values. Unrelated checkout changes were preserved; nothing was staged
or committed.

## Additional uncropped floating-window captures

`notepad/12-floating-windows-desktop` and `paint/13-floating-windows-desktop`
show overlapping native freeform windows with the entire desktop, wallpaper,
dock, system bar, shadows, window controls, and native pointer retained. Their
PNG originals and full-frame JPEG copies are 1366 × 768; **nothing is cropped**.
Notepad shows sample field notes and Welcome. Paint shows the original shrimp
artwork and a separate simple brush sketch. Both frames were visually reviewed.
App data was restored from the fresh pre-capture backups and hash-verified.
Emulator access was serialized with the other active local task before the final
captures; no instrumented tests or application source changes were made here.

## Suggested first selections

- Notepad hero: `01-desktop-field-notes-light`; dark alternate: `06-desktop-field-notes-dark`.
- Notepad details: `03-desktop-find-replace`, `08-compact-find-replace`, `11-independent-windows`.
- Paint hero: `07-desktop-shrimp-dark`; light alternate: `01-desktop-shrimp-light`.
- Paint details: `03-brush-style-gallery`, `09-compact-color-toolbar`, `11-tablet-editable-text`, `12-independent-windows`.

Use the inventory captions as factual starting points. Do not imply a physical
phone capture, a production release, or professionally reviewed Burmese text.
The privacy copy remains a publication draft covering the Android apps. Review
it against `docs/PRIVACY_POLICY.md` before publishing; future website hosting or
analytics behavior needs its own disclosure. Keep future website source, assets,
and build configuration in this folder and use the Sites workflow when that phase
is requested.

## Rebuilding web copies

On macOS, from the repository root:

```sh
swift -module-cache-path /tmp/kd-showcase-swift-cache web/capture/prepare-images.swift
```

This reads `capture/inventory.json` and regenerates the JPEG crops and contact
sheets. `capture/device.py` targets `emulator-5554` explicitly; identify the actual
local Small Desktop serial and coordinate ownership before any future session.
Its `shot APP NAME` command saves a raw capture; `ui` uses Android's UI hierarchy
dump (unavailable for this desktop user through the shell during this session).
