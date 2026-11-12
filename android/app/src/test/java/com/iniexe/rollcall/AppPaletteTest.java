package com.iniexe.rollcall;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AppPaletteTest {
    @Test public void composeTokensMapTheExistingLightPalette() {
        AppPalette palette = AppPalette.get(false, 0);
        com.iniexe.rollcall.ui.theme.RollcallThemeColors colors = palette.toComposeColors();

        assertEquals(palette.canvas, colors.getBackground());
        assertEquals(palette.ink, colors.getContentPrimary());
        assertEquals(palette.muted, colors.getContentSecondary());
        assertEquals(palette.surface, colors.getSurface());
        assertEquals(palette.soft, colors.getSurfaceSubtle());
        assertEquals(palette.green, colors.getAttendanceSafe());
        assertEquals(palette.amber, colors.getAttendanceWarning());
        assertEquals(palette.red, colors.getAttendanceCritical());
        assertEquals(0xFFF1EEDA, colors.getTicketPaper());
        assertEquals(0xFFC8C8FF, colors.getTicketBacking());
    }

    @Test public void composeTokensMapTheExistingDarkPalette() {
        AppPalette palette = AppPalette.get(true, 0);
        com.iniexe.rollcall.ui.theme.RollcallThemeColors colors = palette.toComposeColors();

        assertEquals(palette.canvas, colors.getBackground());
        assertEquals(palette.hero, colors.getBrand());
        assertEquals(palette.heroText, colors.getOnBrand());
        assertEquals(palette.highlight, colors.getHighlight());
        assertEquals(palette.focusSurface, colors.getFocusSurface());
        assertEquals(palette.focusBorder, colors.getFocusBorder());
    }

    @Test public void primaryTextAndSemanticLabelsRemainReadableOnTheirSurfaces() {
        for (boolean dark : new boolean[] {false, true}) {
            AppPalette palette = AppPalette.get(dark, 0);
            com.iniexe.rollcall.ui.theme.RollcallThemeColors colors = palette.toComposeColors();
            assertTextContrast(colors.getContentPrimary(), colors.getBackground());
            assertTextContrast(colors.getContentSecondary(), colors.getBackground());
            assertTextContrast(colors.getOnPrimaryAction(), colors.getPrimaryAction());
            assertTextContrast(colors.getSurface(), colors.getHighlight()); // AN exam-session badge
            assertTextContrast(colors.getAttendanceSafe(), colors.getSurfaceSubtle());
            assertTextContrast(colors.getAttendanceWarning(), colors.getSurfaceSubtle());
            assertTextContrast(colors.getAttendanceCritical(), colors.getSurfaceSubtle());
            assertNonTextContrast(colors.getFocusBorder(), colors.getFocusSurface());
        }
    }

    private static void assertTextContrast(int foreground, int background) {
        double a = luminance(foreground), b = luminance(background);
        double ratio = (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
        assertTrue("Text contrast " + ratio + " is below WCAG AA", ratio >= 4.5);
    }

    private static void assertNonTextContrast(int foreground, int background) {
        double a = luminance(foreground), b = luminance(background);
        double ratio = (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
        assertTrue("Focus indicator contrast " + ratio + " is below 3:1", ratio >= 3.0);
    }

    private static double luminance(int color) {
        double red = linear((color >>> 16) & 0xFF);
        double green = linear((color >>> 8) & 0xFF);
        double blue = linear(color & 0xFF);
        return 0.2126 * red + 0.7152 * green + 0.0722 * blue;
    }

    private static double linear(int channel) {
        double value = channel / 255.0;
        return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }
}
