#!/usr/bin/env python3
"""Generate native vectors from the approved beveled KD logo geometry.

Coordinates use the mockup's 100 × 100 square. Launcher foregrounds get an
proportional 18dp inset inside Android's 108dp layer; in-app logos keep the square.
No font, rasterizer, network access, or third-party Python packages are needed.
"""

from contextlib import contextmanager
from pathlib import Path
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
ANDROID = "http://schemas.android.com/apk/res/android"
AAPT = "http://schemas.android.com/aapt"
ET.register_namespace("android", ANDROID)
ET.register_namespace("aapt", AAPT)

PALETTES = {
    "light": {
        "background": "#FFFFFF", "outline": "#731A1A1A",
        "cover": "#5B9FCF", "cover_light": "#88BEE3", "cover_dark": "#3C80AF",
        "blue": "#6CB6E8", "blue_light": "#9BD5F6", "blue_dark": "#3F8EBF",
        "ink": "#2B648D", "pen_edge": "#A1ADB8", "paper_shade": "#E7EBEE",
        "ceramic_shade": "#E0E5E9", "cup_inner": "#D4D4D4",
        "gold": "#F2B12F", "gold_light": "#FFDC7D", "gold_dark": "#CE901F",
        "gold_edge": "#C7891F", "pencil_wood": "#EFD5AC",
        "wood": "#A2673B", "wood_light": "#C78F5B", "wood_dark": "#7A4B2D",
        "wood_edge": "#704226", "bristle": "#70513B", "bristle_edge": "#513927",
        "purple": "#9870CE", "purple_light": "#B899E5", "purple_dark": "#7852AD",
        "purple_edge": "#6C479F", "metal": "#C4D5E2",
        "metal_light": "#F8FBFD", "metal_dark": "#94A3AF",
    },
    "dark": {
        "background": "#1A1A1A", "outline": "#80FFFFFF",
        "cover": "#78B8E4", "cover_light": "#A0D2F1", "cover_dark": "#5898C7",
        "blue": "#87C7F2", "blue_light": "#B1DFFA", "blue_dark": "#5B9FC9",
        "ink": "#224F70", "pen_edge": "#8E9DAA", "paper_shade": "#E6EBEF",
        "ceramic_shade": "#E3E9ED", "cup_inner": "#DEDEDE",
        "gold": "#F9C14E", "gold_light": "#FFE293", "gold_dark": "#DDA739",
        "gold_edge": "#D49B2E", "pencil_wood": "#F0D2A6",
        "wood": "#C88B52", "wood_light": "#E0AD79", "wood_dark": "#A36B3F",
        "wood_edge": "#945B31", "bristle": "#9B7656", "bristle_edge": "#79583D",
        "purple": "#B896E8", "purple_light": "#D4BCF2", "purple_dark": "#9670CB",
        "purple_edge": "#8D62C4", "metal": "#DBE4ED",
        "metal_light": "#FFFFFF", "metal_dark": "#ACBAC5",
    },
}

# Original geometric KD lettering, outlined so rendering needs no device font.
WORDMARK = (
    "M0,0H3.6V6.2L8.8,0H13.3L6.9,7.4L13.8,16H9.2L3.6,9V16H0Z"
    "M14.5,0H20.6C26.1,0 29.6,3 29.6,8C29.6,13 26.1,16 20.6,16H14.5Z"
    "M18.1,3.3V12.7H20.4C23.9,12.7 25.8,11.1 25.8,8"
    "C25.8,4.9 23.9,3.3 20.4,3.3Z"
)


def number(value):
    return f"{value:.4f}".rstrip("0").rstrip(".") if value else "0"


def element(parent, tag, **attributes):
    return ET.SubElement(parent, tag, {
        f"{{{ANDROID}}}{name}": str(value) for name, value in attributes.items()
    })


def rectangle(x, y, width, height, radius=0, bottom_radius=None):
    top = radius
    bottom = radius if bottom_radius is None else bottom_radius
    right, low = x + width, y + height
    n = number
    return (
        f"M{n(x + top)},{n(y)}H{n(right - top)}"
        f"Q{n(right)},{n(y)} {n(right)},{n(y + top)}V{n(low - bottom)}"
        f"Q{n(right)},{n(low)} {n(right - bottom)},{n(low)}H{n(x + bottom)}"
        f"Q{n(x)},{n(low)} {n(x)},{n(low - bottom)}V{n(y + top)}"
        f"Q{n(x)},{n(y)} {n(x + top)},{n(y)}Z"
    )


class Vector:
    def __init__(self, size):
        self.root = ET.Element("vector", {
            f"{{{ANDROID}}}width": f"{size}dp", f"{{{ANDROID}}}height": f"{size}dp",
            f"{{{ANDROID}}}viewportWidth": "100", f"{{{ANDROID}}}viewportHeight": "100",
        })
        self.parent = self.root

    @contextmanager
    def group(self, **attributes):
        previous = self.parent
        self.parent = element(previous, "group", **attributes)
        try:
            yield
        finally:
            self.parent = previous

    def path(self, data, fill="#00000000", stroke=None, width=.5):
        path = element(self.parent, "path", pathData=data, fillColor=fill, fillType="evenOdd")
        if stroke:
            for name, value in {
                "strokeColor": stroke, "strokeWidth": number(width),
                "strokeLineJoin": "round", "strokeLineCap": "round",
            }.items():
                path.set(f"{{{ANDROID}}}{name}", value)
        return path

    def linear(self, data, coordinates, stops, stroke=None, width=.5):
        path = self.path(data, stroke=stroke, width=width)
        del path.attrib[f"{{{ANDROID}}}fillColor"]
        attr = ET.SubElement(path, f"{{{AAPT}}}attr", {"name": "android:fillColor"})
        gradient = element(attr, "gradient", type="linear", startX=coordinates[0],
                           startY=coordinates[1], endX=coordinates[2], endY=coordinates[3])
        for offset, color in stops:
            element(gradient, "item", offset=number(offset), color=color)

    def wordmark(self, x, y, scale, color):
        with self.group(translateX=x, translateY=y, scaleX=scale, scaleY=scale):
            self.path(WORDMARK, color)


def notepad(vector, palette):
    p, v = palette, vector
    with v.group(rotation=-6, pivotX=48, pivotY=50.5):
        v.linear(rectangle(21, 18, 54, 65, 4.5), (21, 18, 75, 83),
                 [(0, p["cover_light"]), (.2, p["cover"]), (1, p["cover_dark"])], p["outline"])
        v.path("M23,78.5V23Q23,20 26,20H70", stroke="#73FFFFFF", width=1)
        v.path("M73.5,24V78Q73.5,81.5 70,81.5H26", stroke="#291A1A1A", width=1)
        # A short page-edge shadow and neutral paper retain the restrained blue.
        v.path(rectangle(24.7, 28.3, 49.14, 52.65, 2.5), "#261A1A1A")
        v.linear(rectangle(23.7, 27.1, 49.14, 52.65, 2.5), (23.7, 27.1, 23.7, 79.75),
                 [(0, "#FFFFFF"), (.9, "#FFFFFF"), (1, p["paper_shade"])])
        v.wordmark(34.95, 38.06, .9, p["ink"])
        v.path(rectangle(33.528, 61.3225, 27.027, 2.106, 1.053), "#8B949D")
        v.path(rectangle(33.528, 68.6935, 18.6732, 2.106, 1.053), "#8B949D")
        for x in (30.72, 45.84, 60.96):
            v.linear(rectangle(x, 14.75, 4.32, 11.7, 2.16), (x, 14.75, x + 4.32, 14.75),
                     [(0, p["metal_dark"]), (.2, p["metal_light"]),
                      (.4, p["metal"]), (.8, p["metal"]), (1, p["metal_dark"])],
                     p["metal_dark"], .6)

    with v.group(rotation=30, pivotX=75, pivotY=62):
        v.linear(rectangle(70, 40, 10, 44, 2.5, 0), (70, 40, 80, 40),
                 [(0, p["blue_dark"]), (.18, p["blue_light"]),
                  (.38, p["blue"]), (.8, p["blue"]), (1, p["blue_dark"])])
        v.linear(rectangle(70, 70.8, 10, 13.2), (70, 70.8, 80, 70.8),
                 [(0, p["metal_dark"]), (.2, p["metal_light"]),
                  (.4, p["metal"]), (.8, p["metal"]), (1, p["metal_dark"])])
        v.path("M70,84H80L75,95Z", p["ink"])
        v.path("M72.5,40H77.5Q80,40 80,42.5V84L75,95L70,84V42.5Q70,40 72.5,40Z",
               stroke=p["outline"])
        v.path("M70.7,43V83", stroke=p["pen_edge"], width=1)
        v.path(rectangle(77.3, 42.2, 1.8, 11, .8), p["ink"])


def paint(vector, palette):
    p, v = palette, vector
    with v.group(pivotX=50, pivotY=50, scaleX=.94, scaleY=.94, translateX=-2):
        v.path("M69,55H76Q85,55 85,64V69Q85,78 76,78H69Z"
               "M73.5,59.5V73.5H76Q80.5,73.5 80.5,69V64Q80.5,59.5 76,59.5Z",
               "#FFFFFF", p["outline"])
        v.path("M74,50A24,5 0,1 0,26 50A24,5 0,1 0,74 50Z", p["cup_inner"], p["outline"])

        with v.group(rotation=-10, pivotX=40, pivotY=61):
            v.linear(rectangle(35, 28, 10, 33), (35, 28, 45, 28),
                     [(0, p["gold_light"]), (.24, p["gold_light"]),
                      (.24, p["gold"]), (.75, p["gold"]), (1, p["gold_dark"])])
            v.path(rectangle(35, 28, 2.5, 33), p["gold_edge"])
            v.path("M40,18L45,28H35Z", p["pencil_wood"])
            v.path("M39.95,18L41.4,23H38.5Z", "#3A4652")
            v.path("M40,18L45,28V61H35V28Z", stroke=p["outline"])

        with v.group(rotation=3, pivotX=51.5, pivotY=61):
            v.linear(rectangle(48, 27, 7, 34, 0, 2), (48, 27, 55, 27),
                     [(0, p["wood_light"]), (.25, p["wood_light"]),
                      (.25, p["wood"]), (.75, p["wood"]), (1, p["wood_dark"])], p["outline"])
            v.path("M48.6,28V60", stroke=p["wood_edge"], width=1)
            bristles = "M52.97,9L54.884,12.6L55.928,17L56.45,21.8L56.015,25.8L55.058,29H49.142L48.185,25.8L47.75,21.8L48.272,17L49.664,12.2Z"
            v.path(bristles, p["bristle"], p["outline"])
            with v.group():
                element(v.parent, "clip-path", pathData=bristles)
                for x in (49.2, 51.9, 54.6):
                    v.path(f"M{x},11V29", stroke=p["bristle_edge"], width=.5)
            v.linear(rectangle(48.04, 27, 8.12, 8.5), (48.04, 27, 56.16, 27),
                     [(0, p["metal_light"]), (.24, p["metal"]),
                      (.75, p["metal"]), (1, p["metal_dark"])], p["outline"])
            v.path("M48.04,34.75H56.16", stroke=p["wood_edge"], width=1)

        with v.group(rotation=13, pivotX=63, pivotY=61):
            v.linear(rectangle(58, 28, 10, 33, 2.5), (58, 28, 68, 28),
                     [(0, p["purple_light"]), (.24, p["purple"]),
                      (.75, p["purple"]), (1, p["purple_dark"])], p["outline"])
            v.linear(rectangle(58, 28, 10, 10.23, 2.5, 0), (58, 28, 68, 28),
                     [(0, p["purple"]), (.24, p["purple_edge"]),
                      (.75, p["purple_edge"]), (1, p["purple_dark"])])
            v.path(rectangle(64.5, 29.32, 1.8, 9, 1), p["purple"])

        v.linear(rectangle(26, 50, 48, 35, 0, 7.5), (26, 50, 74, 50),
                 [(0, p["ceramic_shade"]), (.14, "#FFFFFF"),
                  (.84, "#FFFFFF"), (1, p["ceramic_shade"])], p["outline"])
        v.path("M27,79Q28,84 33.5,84H66.5Q72,84 73,79", stroke="#17808080", width=1)
        v.wordmark(35.2, 59.15, 1, "#252525")


@contextmanager
def without_wordmark(vector, x, y, scale=1):
    """Cut KD out of a silhouette for Android's optional themed icons."""
    with vector.group(translateX=x, translateY=y, scaleX=scale, scaleY=scale):
        element(vector.parent, "clip-path", fillType="evenOdd",
                pathData=rectangle(-200, -200, 600, 600) + WORDMARK)
        with vector.group(scaleX=1 / scale, scaleY=1 / scale,
                          translateX=-x / scale, translateY=-y / scale):
            yield


def monochrome(vector, app):
    v = vector
    if app == "notepad":
        with v.group(rotation=-6, pivotX=48, pivotY=50.5):
            with without_wordmark(v, 34.95, 38.06, .9):
                v.path(rectangle(21, 18, 54, 65, 4.5), "#000000")
            for x in (30.72, 45.84, 60.96):
                v.path(rectangle(x, 14.75, 4.32, 11.7, 2.16), "#000000")
        with v.group(rotation=30, pivotX=75, pivotY=62):
            v.path(rectangle(70, 40, 10, 44, 2.5, 0) + "M70,84H80L75,95Z", "#000000")
    else:
        with v.group(pivotX=50, pivotY=50, scaleX=.94, scaleY=.94, translateX=-2):
            v.path("M69,55H76Q85,55 85,64V69Q85,78 76,78H69Z"
                   "M73.5,59.5V73.5H76Q80.5,73.5 80.5,69V64Q80.5,59.5 76,59.5Z", "#000000")
            v.path("M74,50A24,5 0,1 0,26 50A24,5 0,1 0,74 50Z", "#000000")
            with v.group(rotation=-10, pivotX=40, pivotY=61):
                v.path("M40,18L45,28V55H35V28Z", "#000000")
            with v.group(rotation=3, pivotX=51.5, pivotY=61):
                v.path("M52.97,9L54.884,12.6L55.928,17L56.45,21.8L56.015,25.8"
                       "L55.058,29H49.142L48.185,25.8L47.75,21.8L48.272,17L49.664,12.2Z", "#000000")
                v.path(rectangle(48, 27, 8.12, 28, 0, 2), "#000000")
            with v.group(rotation=13, pivotX=63, pivotY=61):
                v.path(rectangle(58, 28, 10, 27, 2.5), "#000000")
            with without_wordmark(v, 35.2, 59.15):
                v.path(rectangle(26, 50, 48, 35, 0, 7.5), "#000000")


def write_xml(path, root):
    path.parent.mkdir(parents=True, exist_ok=True)
    ET.indent(root, space="    ")
    path.write_text('<?xml version="1.0" encoding="utf-8"?>\n'
                    '<!-- Generated by scripts/generate_app_logos.py. -->\n'
                    + ET.tostring(root, encoding="unicode") + '\n')


def generate(app, draw):
    res = ROOT / app / "src/main/res"
    for mode, palette in PALETTES.items():
        # Explicit in-app IDs follow the app's theme even when it differs from Android.
        icon = Vector(48)
        icon.path(rectangle(0, 0, 100, 100), palette["background"])
        draw(icon, palette)
        suffix = "" if mode == "light" else "_dark"
        write_xml(res / f"drawable/ic_{app}{suffix}.xml", icon.root)

        directory = "drawable" if mode == "light" else "drawable-night"
        foreground = Vector(108)
        # Scale in viewport coordinates so the safe area holds at every density/size.
        with foreground.group(scaleX=2 / 3, scaleY=2 / 3,
                              translateX=100 / 6, translateY=100 / 6):
            draw(foreground, palette)
        write_xml(res / directory / f"ic_{app}_foreground.xml", foreground.root)
        colors = ET.Element("resources")
        ET.SubElement(colors, "color", {"name": "logo_background"}).text = palette["background"]
        write_xml(res / ("values" if mode == "light" else "values-night") / "logo_colors.xml", colors)

    themed = Vector(108)
    with themed.group(scaleX=2 / 3, scaleY=2 / 3,
                      translateX=100 / 6, translateY=100 / 6):
        monochrome(themed, app)
    write_xml(res / f"drawable/ic_{app}_monochrome.xml", themed.root)

    adaptive = ET.Element("adaptive-icon")
    element(adaptive, "background", drawable="@color/logo_background")
    element(adaptive, "foreground", drawable=f"@drawable/ic_{app}_foreground")
    element(adaptive, "monochrome", drawable=f"@drawable/ic_{app}_monochrome")
    write_xml(res / "mipmap-anydpi-v26/ic_launcher.xml", adaptive)


if __name__ == "__main__":
    generate("notepad", notepad)
    generate("paint", paint)
