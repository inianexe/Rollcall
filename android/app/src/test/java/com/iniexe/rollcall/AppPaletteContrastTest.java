package com.iniexe.rollcall;

import org.junit.Test;
import static org.junit.Assert.assertTrue;

/** Guards Signal's semantic color contrast in both supported token sets. */
public class AppPaletteContrastTest {
    private static double luminance(int color) {
        double r = linear((color >>> 16 & 255) / 255.0);
        double g = linear((color >>> 8 & 255) / 255.0);
        double b = linear((color & 255) / 255.0);
        return .2126 * r + .7152 * g + .0722 * b;
    }

    private static double linear(double value) {
        return value <= .04045 ? value / 12.92 : Math.pow((value + .055) / 1.055, 2.4);
    }

    private static double ratio(int a, int b) {
        double high = Math.max(luminance(a), luminance(b));
        double low = Math.min(luminance(a), luminance(b));
        return (high + .05) / (low + .05);
    }

    private static void check(String name, int text, int background) {
        assertTrue(name + " contrast is " + ratio(text, background), ratio(text, background) >= 4.5);
    }

    @Test public void allThemesKeepTextReadableInBothPalettes() {
        for (int i = 0; i < AppPalette.THEME_COUNT; i++) {
            for (boolean dark : new boolean[]{false, true}) {
                AppPalette p = AppPalette.get(dark, i);
                String prefix = p.name + (dark ? " dark" : " light");
                check(prefix + " primary", p.ink, p.canvas);
                check(prefix + " secondary", p.muted, p.surface);
                check(prefix + " button", p.buttonText, p.button);
                check(prefix + " hero", p.heroText, p.hero);
                check(prefix + " accent", p.highlight, p.canvas);
                check(prefix + " positive", p.green, p.surface);
                check(prefix + " error", p.red, p.surface);
            }
        }
    }

    @Test public void arbitraryCustomAccentsKeepButtonsAndTextReadable() {
        int[] colors = {0xFF000000, 0xFFFFFFFF, 0xFFFF0000, 0xFF00FF00, 0xFF0000FF,
                0xFFFFFF00, 0xFF00FFFF, 0xFFFF00FF, 0xFF777777, 0xFF123456, 0xFFEF6A2F};
        for (int color : colors) for (int theme = 0; theme < AppPalette.THEME_COUNT; theme++)
            for (boolean dark : new boolean[]{false, true}) {
                AppPalette p = AppPalette.get(dark, theme, color);
                assertTrue("button text " + Integer.toHexString(color), ratio(p.buttonText, p.button) >= 4.5);
                assertTrue("highlight " + Integer.toHexString(color), ratio(p.highlight, p.canvas) >= 4.5);
                assertTrue("body text " + Integer.toHexString(color), ratio(p.ink, p.canvas) >= 4.5);
                assertTrue("secondary text " + Integer.toHexString(color), ratio(p.muted, p.surface) >= 4.5);
                assertTrue("chosen accent preserved", p.customAccent == color);
            }
    }
}
