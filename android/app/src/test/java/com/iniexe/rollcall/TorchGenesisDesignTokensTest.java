package com.iniexe.rollcall;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

/** A regression guard for the exact dark brand tokens in the uploaded HTML. */
public class TorchGenesisDesignTokensTest {
    @Test public void matchesCanonicalPrototype() {
        AppPalette palette = AppPalette.get(true, 0);
        assertEquals("Torch Genesis", palette.name);
        assertEquals(0xFF121212, palette.canvas);
        assertEquals(0xFFF5F5EE, palette.ink);
        assertEquals(0xFFCCFF00, palette.hero);
        assertEquals(0xFFCCFF00, palette.button);
        assertEquals(0xFF111111, palette.heroText);
        assertEquals(0xFFFF4400, palette.highlight);
        assertEquals(0xFFC8C8FF, palette.focusSurface);
        assertEquals(0xFF222222, palette.surface);
        assertEquals(0xFF2C2C2C, palette.soft);
        assertEquals(0xFF525252, palette.border);
        assertEquals(0xFFA8AAA1, palette.muted);
    }

    @Test public void allHistoricalThemeNumbersNowReturnSameBrand() {
        AppPalette expected = AppPalette.get(true, 0);
        for (int index : new int[]{5, 6, 999}) {
            AppPalette palette = AppPalette.get(true, index);
            assertEquals(expected.canvas, palette.canvas);
            assertEquals(expected.hero, palette.hero);
            assertEquals(expected.highlight, palette.highlight);
        }
    }
}
