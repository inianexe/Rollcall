package com.iniexe.rollcall;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Protects the selected Signal default and safe migration from old theme IDs. */
public class TorchGenesisDesignTokensTest {
    @Test public void signalRemainsTheDefaultBrandPalette() {
        AppPalette palette = AppPalette.get(true, 0);
        assertEquals("Signal", palette.name);
        assertEquals(0xFF000000, palette.canvas);
        assertEquals(0xFFCCFF00, palette.hero);
        assertEquals(0xFFCCFF00, palette.button);
        assertEquals(0xFFCCFF00, palette.highlight);
        assertEquals(0xFF1C1C1E, palette.surface);
        assertEquals(0xFF2C2C2E, palette.raisedSurface);
        assertEquals(AppPalette.SIGNAL_SYNC_SUCCESS, palette.syncSuccess);
        assertEquals(0xFFFF4400, AppPalette.SIGNAL_COURSE_ATTEND_FILL);
    }

    @Test public void fourNativeThemesHaveStableIdsAndNames() {
        assertEquals("Signal", AppPalette.get(true, AppPalette.SIGNAL).name);
        assertEquals("Cupertino", AppPalette.get(true, AppPalette.CUPERTINO).name);
        assertEquals("Graphite", AppPalette.get(true, AppPalette.GRAPHITE).name);
        assertEquals("Atelier", AppPalette.get(true, AppPalette.ATELIER).name);
        assertEquals("Signal", AppPalette.get(true, -1).name);
        assertEquals("Signal", AppPalette.get(true, AppPalette.THEME_COUNT).name);
        assertEquals("Signal", AppPalette.get(true, 999).name);
    }

    @Test public void addedThemesHaveIndependentDarkSurfacesAndAccents() {
        AppPalette signal = AppPalette.get(true, AppPalette.SIGNAL);
        for (int index = AppPalette.CUPERTINO; index < AppPalette.THEME_COUNT; index++) {
            AppPalette theme = AppPalette.get(true, index);
            assertTrue(theme.name + " gets a distinct accent", theme.button != signal.button);
            if (index != AppPalette.CUPERTINO)
                assertTrue(theme.name + " gets a distinct card surface", theme.surface != signal.surface);
        }
    }

    @Test public void themesUseTheirDocumentedCornerHierarchy() {
        AppPalette signal = AppPalette.get(true, AppPalette.SIGNAL);
        AppPalette cupertino = AppPalette.get(true, AppPalette.CUPERTINO);
        AppPalette graphite = AppPalette.get(true, AppPalette.GRAPHITE);
        AppPalette atelier = AppPalette.get(true, AppPalette.ATELIER);
        assertEquals(16, signal.cardRadius);
        assertEquals(20, signal.featureRadius);
        assertEquals(8, signal.controlRadius);
        assertEquals(24, cupertino.cardRadius);
        assertEquals(28, cupertino.featureRadius);
        assertEquals(12, cupertino.controlRadius);
        assertEquals(16, graphite.cardRadius);
        assertEquals(20, graphite.featureRadius);
        assertEquals(8, graphite.controlRadius);
        assertEquals(22, atelier.cardRadius);
        assertEquals(26, atelier.featureRadius);
        assertEquals(10, atelier.controlRadius);
        assertEquals(32, signal.navRadius);
        assertEquals(32, cupertino.navRadius);
        assertEquals(32, graphite.navRadius);
        assertEquals(32, atelier.navRadius);
    }
}
