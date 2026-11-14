package com.iniexe.rollcall;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class AppPaletteTest {
    @Test public void allPaletteActionsAndHeroTextMeetAaContrastInBothModes() {
        for (int i = 0; i < 5; i++) for (boolean dark : new boolean[]{false, true}) {
            AppPalette palette = AppPalette.get(dark, i);
            assertTrue(palette.name + " action contrast, dark=" + dark + ": " + contrast(palette.buttonText, palette.button), contrast(palette.buttonText, palette.button) >= 4.5);
            assertTrue(palette.name + " hero contrast, dark=" + dark + ": " + contrast(palette.heroText, palette.hero), contrast(palette.heroText, palette.hero) >= 4.5);
            assertTrue(palette.name + " body contrast, dark=" + dark + ": " + contrast(palette.ink, palette.canvas), contrast(palette.ink, palette.canvas) >= 4.5);
        }
    }

    private static double contrast(int foreground, int background) {
        double a = luminance(foreground), b = luminance(background);
        return (Math.max(a, b) + .05) / (Math.min(a, b) + .05);
    }

    private static double luminance(int color) {
        double r = channel((color >> 16) & 255), g = channel((color >> 8) & 255), b = channel(color & 255);
        return .2126 * r + .7152 * g + .0722 * b;
    }

    private static double channel(int value) {
        double c = value / 255.0;
        return c <= .04045 ? c / 12.92 : Math.pow((c + .055) / 1.055, 2.4);
    }
}
