package com.iniexe.rollcall;

import org.junit.Test;
import static org.junit.Assert.assertTrue;

/** Guards the functional readability of both Torch Genesis palettes. */
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

    @Test public void lightPaletteKeepsTextReadable() {
        AppPalette p = AppPalette.get(false, 0);
        check("Light primary text", p.ink, p.canvas);
        check("Light secondary text", p.muted, p.canvas);
        check("Light button", p.buttonText, p.button);
        check("Light hero", p.heroText, p.hero);
        check("Light orange label", p.highlight, p.canvas);
        check("Light positive text", p.green, p.surface);
        check("Light error text", p.red, p.surface);
    }

    @Test public void darkPaletteKeepsTextReadable() {
        AppPalette p = AppPalette.get(true, 0);
        check("Dark primary text", p.ink, p.canvas);
        check("Dark secondary text", p.muted, p.canvas);
        check("Dark button", p.buttonText, p.button);
        check("Dark hero", p.heroText, p.hero);
        check("Dark orange label", p.highlight, p.canvas);
        check("Dark positive text", p.green, p.surface);
        check("Dark error text", p.red, p.surface);
    }
}
