# Bundled note fonts

The editor and settings previews use these Google Fonts at their default regular
weight and normal width. System uses the device's default font; Monospace remains
the default for new and legacy sessions. App controls retain system typography.

| Setting | Font | Resource |
| --- | --- | --- |
| Monospace | [Noto Sans Mono](https://fonts.google.com/specimen/Noto+Sans+Mono) | `src/main/res/font/noto_sans_mono.ttf` |
| Serif | [Noto Serif](https://fonts.google.com/specimen/Noto+Serif) | `src/main/res/font/noto_serif.ttf` |
| Sans-Serif | [Noto Sans](https://fonts.google.com/specimen/Noto+Sans) | `src/main/res/font/noto_sans.ttf` |

These readable text families cover Latin, Greek, and Cyrillic, including Vietnamese.
Noto Sans also includes Devanagari. Unsupported characters, including Myanmar text
and emoji, use Android's system fallback fonts and can look alike across choices.

The unmodified variable TTF files were downloaded from the
[Google Fonts repository](https://github.com/google/fonts/tree/e7db6ca603fd4862010383ff17ffd6cc4ffac1c0/ofl)
at commit `e7db6ca603fd4862010383ff17ffd6cc4ffac1c0`:

- `ofl/notosansmono/NotoSansMono[wdth,wght].ttf`
- `ofl/notoserif/NotoSerif[wdth,wght].ttf`
- `ofl/notosans/NotoSans[wdth,wght].ttf`

All three are licensed under the SIL Open Font License 1.1. Their original copyright
notices and full licenses are included in the APK under `assets/licenses/` as
`noto_sans_mono_OFL.txt`, `noto_serif_OFL.txt`, and `noto_sans_OFL.txt`.
