# Third-party notices

Original app code is MIT licensed; see the root `LICENSE`. Third-party
components retain their respective licenses. All texts below are available
offline in **About KD Notepad > Open-source licenses**.

- `src/main/assets/licenses/Runtime-NOTICES.txt` contains the release dependency
  inventory, Apache 2.0 text, copyright notices, and additional upstream terms.
- `src/main/assets/licenses/MaterialSymbols-NOTICE.txt` and
  `MaterialSymbols-LICENSE.txt` contain the icon attribution and license.
- `core/ui/src/main/assets/licenses/` supplies the app MIT license and the
  Noto Sans, Noto Serif, and Noto Sans Mono copyright notices and SIL Open Font
  License 1.1 texts to both apps.

Update the runtime inventory and upstream notices when release dependencies
change. Build tools and test-only dependencies are not part of this inventory.

## Material Symbols

The `src/main/res/drawable/ms_*.xml` assets are Material Symbols by Google,
licensed under the Apache License, Version 2.0. The license is bundled in
`src/main/assets/licenses/MaterialSymbols-LICENSE.txt`.

Source: https://github.com/google/material-design-icons/tree/10ecc14790612ca8fd164e3f4ffe9c16fc50d5b7/symbols/android

Variant: Rounded, unfilled, weight 400, grade 0, optical size 24.
The assets retain their original paths. Their XML tint attributes were removed
and their fill color changed to black so Compose supplies the theme tint.

