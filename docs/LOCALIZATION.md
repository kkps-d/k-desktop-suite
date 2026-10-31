# Localization

KD Notepad and KD Paint support English and Burmese. The Burmese translation is
a generated draft for testing; wording should be reviewed by a fluent speaker.

## Choosing a language

Open Settings and scroll to Language. On Android 13 and newer, **Open language
settings** opens Android's language settings for the current app. The links below
show supported languages or open the in-app picker. If the system settings
activity is missing or inaccessible, the picker opens automatically.

On older Android versions, the button opens the picker directly. Choose **Follow
system**, **English**, or **မြန်မာ**. Changes apply immediately to all windows of
that app. Notepad and Paint have independent preferences. Follow system uses
Android's preferred supported language, with English as the fallback.

The in-app picker bypasses the system settings interface, but still relies on
Android's locale APIs. It cannot repair an underlying ROM implementation.

## Resource ownership

- Keep app-specific strings in the owning app's `res/values/strings.xml`.
- Keep shared controls in `core/ui`, with `core_ui_` prefixes for new strings.
- Supply matching keys under `values-my/strings.xml`. Keep the default English
  resource set complete. Group related entries with short XML comments.
- Preserve complete sentences, numbered format arguments, plural quantities,
  and the language-help `action` annotations when translating.
- Keep branding, URLs, extensions, and identifiers non-translatable. Original
  license texts and third-party names retain their original spelling.

The shared `core_ui_supported_locales.xml` is the language list for both app
manifests, the supported-languages dialog, and the picker. Each app restricts
packaged locales to `en` and `my`. Add a language only when the app resources and
shared UI are translated; update the list and both packaging filters together.

Use display mappings for tool, brush, and font labels. Never translate persisted
enum names, font keys, MIME types, appearance values, recovery data, user text, or
existing filenames. Paint messages retain resource identifiers and arguments and
are resolved in the current activity's resources. Pure raster validation uses
`PaintProblem` identities rather than Android resources or translated strings.

## Verification

Run `python scripts/check_localization.py` to check key coverage, duplicate keys,
format arguments, plurals, Unicode replacement characters, and hyperlink
annotations. Build and lint both apps as described in [Testing](TESTING.md).
Inspect Burmese shaping and wrapping at narrow widths and large font scales.
The existing Noto fonts use Android's fallback glyph coverage for Burmese.

Test language changes with multiple windows, pending edits, and unsaved drafts.
Preferences use AppCompat's automatic storage below Android 13 and the platform
store on newer versions; there is no separate application language preference.

An explicit choice matching the device's current language may not recreate the
activity. The picker updates its selected row immediately; changing the actual
interface language uses normal Android configuration recreation.
