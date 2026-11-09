"""Contrast of the actual native dialog/theme tokens, not a design screenshot.
Device rendering, font metrics and translucency remain separate manual gates.
"""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET


def luminance(color):
    channels = [int(color[i:i + 2], 16) / 255 for i in (1, 3, 5)]
    linear = [v / 12.92 if v <= .04045 else ((v + .055) / 1.055) ** 2.4 for v in channels]
    return sum(a * b for a, b in zip(linear, (.2126, .7152, .0722)))


def contrast(a, b):
    x, y = sorted((luminance(a), luminance(b)))
    return (y + .05) / (x + .05)


class NativeContrastTest(unittest.TestCase):
    def setUp(self):
        root = ET.parse(Path(__file__).resolve().parents[1] / 'res/values/styles.xml').getroot()
        self.themes = {s.get('name'): {i.get('name'): i.text for i in s} for s in root}

    def test_small_text_against_native_opaque_surfaces(self):
        for name, theme in self.themes.items():
            for fg in ('android:colorAccent', 'android:textColorPrimary', 'android:textColorSecondary'):
                for bg in ('android:colorBackground', 'android:colorButtonNormal'):
                    with self.subTest(theme=name, foreground=fg, background=bg):
                        self.assertGreaterEqual(contrast(theme[fg], theme[bg]), 4.5)

    def test_primary_button_labels(self):
        self.assertGreaterEqual(contrast('#ffffff', self.themes['MyTasks.Light']['android:colorAccent']), 4.5)
        self.assertGreaterEqual(contrast('#0b0d12', self.themes['MyTasks.Dark']['android:colorAccent']), 4.5)


if __name__ == '__main__':
    unittest.main()
