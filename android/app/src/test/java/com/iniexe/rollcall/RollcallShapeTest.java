package com.iniexe.rollcall;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class RollcallShapeTest {
    @Test public void nestedRadiusTracksActualInsetAndNeverGoesNegative() {
        assertEquals(16f, RollcallShape.innerRadiusDp(24f, 8f), 0f);
        assertEquals(0f, RollcallShape.innerRadiusDp(16f, 20f), 0f);
    }

    @Test public void capsuleRadiusIsHalfItsHeight() {
        assertEquals(32f, RollcallShape.capsuleRadiusDp(64f), 0f);
        assertEquals(26f, RollcallShape.innerRadiusDp(
                RollcallShape.capsuleRadiusDp(64f), 6f), 0f);
    }
}
