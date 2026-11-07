#!/usr/bin/env python3
"""Small Desktop capture helper. Always targets the local emulator explicitly."""
import json
import pathlib
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1]
ADB = pathlib.Path.home() / "Library/Android/sdk/platform-tools/adb"
SERIAL = "emulator-5554"


def adb(*args):
    return subprocess.check_output([str(ADB), "-s", SERIAL, *args])


command, *args = sys.argv[1:]
if command == "shot":
    app, name = args
    time.sleep(1)
    path = ROOT / "assets/screenshots/originals" / app / (name + ".png")
    path.write_bytes(adb("exec-out", "screencap", "-p"))
    print(path)
elif command == "ui":
    adb("shell", "uiautomator", "dump", "/data/local/tmp/kd-showcase-ui.xml")
    tree = ET.fromstring(adb("shell", "cat", "/data/local/tmp/kd-showcase-ui.xml"))
    for node in tree.iter("node"):
        a = node.attrib
        if a.get("text") or a.get("content-desc") or a.get("class", "").endswith("EditText"):
            print(json.dumps({k: a.get(k) for k in ("text", "content-desc", "class", "bounds", "focused")}, ensure_ascii=False))
elif command == "adb":
    output = adb(*args)
    sys.stdout.buffer.write(output)
    time.sleep(0.8)
else:
    raise SystemExit("Use shot APP NAME, ui, or adb ARGS...")
