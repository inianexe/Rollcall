"""Check text contrast in all shipped theme pairs without Android dependencies."""
import re
from pathlib import Path

def luminance(color):
    rgb = [int(color[i:i + 2], 16) / 255 for i in (2, 4, 6)]
    linear = [v / 12.92 if v <= .04045 else ((v + .055) / 1.055) ** 2.4 for v in rgb]
    return sum(v * weight for v, weight in zip(linear, [.2126, .7152, .0722]))

def contrast(a, b):
    low, high = sorted((luminance(a), luminance(b)))
    return (high + .05) / (low + .05)

PAIRS = [(1, 0), (1, 3), (2, 0), (2, 3), (2, 5), (6, 3), (6, 5),
         (7, 3), (7, 5), (7, 16), (8, 3), (8, 5), (11, 10), (14, 13), (15, 13)]
if __name__ == '__main__':
    source = (Path(__file__).parents[1] / 'app/src/main/java/com/iniexe/rollcall/AppPalette.java').read_text()
    palettes = re.findall(r'new AppPalette\("([^"]+)", ([^)]+)\)', source)
    assert len(palettes) == 6
    for name, body in palettes:
        colors = re.findall(r'0x([A-Fa-f0-9]{8})', body)
        for fg, bg in PAIRS:
            assert contrast(colors[fg], colors[bg]) >= 4.5, (name, fg, bg, contrast(colors[fg], colors[bg]))
    print('6 theme variants: all text role pairs meet 4.5:1')
