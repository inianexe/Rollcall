package com.iniexe.rollcall;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

/** Protects the selected Signal default and safe migration from old theme IDs. */
public class TorchGenesisDesignTokensTest {
    @Test public void signalRemainsTheDefaultBrandPalette() {
        AppPalette palette = AppPalette.get(true, 0);
        assertEquals("Signal", palette.name);
        assertEquals(0xFF151719, palette.canvas);
        assertEquals(0xFFCCFF00, palette.hero);
        assertEquals(0xFFCCFF00, palette.button);
        assertEquals(0xFFFF663D, palette.highlight);
        assertEquals(0xFF222628, palette.surface);
    }

    @Test public void historicalThemeNumbersFallBackToSignal() {
        AppPalette expected = AppPalette.get(true, 0);
        for (int index : new int[]{5, 6, 999}) {
            AppPalette palette = AppPalette.get(true, index);
            assertEquals(expected.canvas, palette.canvas);
            assertEquals(expected.hero, palette.hero);
            assertEquals(expected.highlight, palette.highlight);
        }
    }
}
