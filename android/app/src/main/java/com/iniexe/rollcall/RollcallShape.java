package com.iniexe.rollcall;

final class RollcallShape {
    private RollcallShape() { }

    static float innerRadiusDp(float outerRadiusDp, float insetDp) {
        return Math.max(0f, outerRadiusDp - insetDp);
    }

    static float capsuleRadiusDp(float heightDp) {
        return Math.max(0f, heightDp / 2f);
    }
}
