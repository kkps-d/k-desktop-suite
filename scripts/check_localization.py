"""Check the shipped Burmese resources against their English sources."""

from pathlib import Path
import re
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
PLACEHOLDER = re.compile(r"%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z]")


def resources(path):
    entries = {}
    for entry in ET.parse(path).getroot():
        name = entry.attrib["name"]
        assert name not in entries, f"{path}: duplicate {name}"
        entries[name] = entry
    return entries


def check():
    total = 0
    for module in ("core/ui", "notepad", "paint"):
        folder = ROOT / module / "src/main/res"
        source = resources(folder / "values/strings.xml")
        translated = resources(folder / "values-my/strings.xml")
        expected = {key for key, value in source.items() if value.get("translatable") != "false"}
        assert translated.keys() == expected, f"{module}: missing {expected - translated.keys()}, extra {translated.keys() - expected}"
        for key in expected:
            english, burmese = source[key], translated[key]
            assert english.tag == burmese.tag, f"{module}/{key}: resource type changed"
            pairs = [(english, burmese)]
            if english.tag == "plurals":
                originals = {item.get("quantity"): item for item in english}
                quantities = {item.get("quantity"): item for item in burmese}
                assert "other" in quantities, f"{module}/{key}: missing other quantity"
                # Burmese uses only "other"; translations need not copy English categories.
                assert quantities.keys() == {"other"}, f"{module}/{key}: unsupported Burmese quantity"
                pairs = [(originals[quantity], item) for quantity, item in quantities.items()]
            for original, translation in pairs:
                original_text = "".join(original.itertext())
                translated_text = "".join(translation.itertext())
                assert translated_text.strip(), f"{module}/{key}: empty translation"
                assert sorted(PLACEHOLDER.findall(original_text.replace("%%", ""))) == sorted(PLACEHOLDER.findall(translated_text.replace("%%", ""))), f"{module}/{key}: placeholders changed"
                assert "\ufffd" not in translated_text, f"{module}/{key}: invalid Unicode"
                annotations = lambda value: sorted(tuple(sorted(span.attrib.items())) for span in value.findall("annotation"))
                assert annotations(original) == annotations(translation), f"{module}/{key}: link annotations changed"
        total += len(expected)
        print(f"{module}: {len(expected)} translated resources checked")
    config = ET.parse(ROOT / "core/ui/src/main/res/xml/core_ui_supported_locales.xml")
    assert [item.get("{http://schemas.android.com/apk/res/android}name") for item in config.getroot()] == ["en", "my"]
    print(f"All {total} resources passed.")


if __name__ == "__main__":
    check()
