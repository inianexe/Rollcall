package com.iniexe.rollcall;

final class AppPalette {
    static final int SIGNAL = 0;
    static final int CUPERTINO = 1;
    static final int GRAPHITE = 2;
    static final int ATELIER = 3;
    static final int THEME_COUNT = 4;
    static final int SIGNAL_COURSE_ATTEND_FILL = 0xFFFF4400;
    static final int SIGNAL_SYNC_SUCCESS = 0xFF9BE4A1;

    final String name;
    final int themeIndex;
    final boolean dark;
    final int canvas, ink, muted, surface, raisedSurface, border, soft, green, red, amber, track;
    final int button, buttonText, highlight, hero, heroText, heroMuted, focusSurface, focusBorder;
    final int syncSuccess;
    final int cardRadius, featureRadius, controlRadius, navRadius;
    final int customAccent;

    private AppPalette(boolean dark, int requestedTheme, int customAccent) {
        themeIndex = normalizeIndex(requestedTheme);
        this.dark = dark;
        name = nameFor(themeIndex);

        int darkCanvas, darkSurface, darkRaised, darkInk, darkMuted, darkAccent, darkAccentText;
        int lightCanvas, lightSurface, lightRaised, lightInk, lightMuted, lightAccent, lightAccentText;
        switch (themeIndex) {
            case CUPERTINO:
                darkCanvas = 0xFF000000; darkSurface = 0xFF1C1C1E; darkRaised = 0xFF2C2C2E;
                darkInk = 0xFFF5F5F7; darkMuted = 0xFFB8B8BE; darkAccent = 0xFF8DB6FF; darkAccentText = 0xFF111827;
                lightCanvas = 0xFFF2F2F7; lightSurface = 0xFFFFFFFF; lightRaised = 0xFFE5E5EA;
                lightInk = 0xFF1C1C1E; lightMuted = 0xFF52525B; lightAccent = 0xFF2458D3; lightAccentText = 0xFFFFFFFF;
                cardRadius = 24; featureRadius = 28; controlRadius = 12; navRadius = 32;
                break;
            case GRAPHITE:
                darkCanvas = 0xFF0B0F14; darkSurface = 0xFF171C22; darkRaised = 0xFF232A33;
                darkInk = 0xFFF0F3F6; darkMuted = 0xFFB3BBC4; darkAccent = 0xFF9EBCCE; darkAccentText = 0xFF11171D;
                lightCanvas = 0xFFF4F5F6; lightSurface = 0xFFFFFFFF; lightRaised = 0xFFE9EBED;
                lightInk = 0xFF1E2226; lightMuted = 0xFF5C646C; lightAccent = 0xFF405E72; lightAccentText = 0xFFFFFFFF;
                cardRadius = 16; featureRadius = 20; controlRadius = 8; navRadius = 32;
                break;
            case ATELIER:
                darkCanvas = 0xFF161310; darkSurface = 0xFF25211D; darkRaised = 0xFF302B25;
                darkInk = 0xFFF2ECE4; darkMuted = 0xFFC8B9A7; darkAccent = 0xFFE2BC92; darkAccentText = 0xFF29241E;
                lightCanvas = 0xFFF8F5EF; lightSurface = 0xFFFFFEFA; lightRaised = 0xFFEFE9DF;
                lightInk = 0xFF29241E; lightMuted = 0xFF675E52; lightAccent = 0xFF805C3C; lightAccentText = 0xFFFFFFFF;
                cardRadius = 22; featureRadius = 26; controlRadius = 10; navRadius = 32;
                break;
            case SIGNAL:
            default:
                darkCanvas = 0xFF0B0C0A; darkSurface = 0xFF151714; darkRaised = 0xFF1D201B;
                darkInk = 0xFFF2F3EC; darkMuted = 0xFFA8AD9E; darkAccent = 0xFFD4FF35; darkAccentText = 0xFF15180F;
                lightCanvas = 0xFFF2F2F7; lightSurface = 0xFFFFFFFF; lightRaised = 0xFFF0F0F2;
                lightInk = 0xFF1C1C1E; lightMuted = 0xFF636366; lightAccent = 0xFF2458E5; lightAccentText = 0xFFFFFFFF;
                cardRadius = 18; featureRadius = 22; controlRadius = 11; navRadius = 28;
                break;
        }

        this.customAccent = customAccent == 0 ? 0 : 0xFF000000 | (customAccent & 0x00FFFFFF);
        int baseCanvas = dark ? darkCanvas : lightCanvas;
        int baseSurface = dark ? darkSurface : lightSurface;
        int baseRaised = dark ? darkRaised : lightRaised;
        int accent = this.customAccent == 0 ? (dark ? darkAccent : lightAccent) : this.customAccent;
        boolean neutralSignal = dark && themeIndex == SIGNAL;
        canvas = this.customAccent == 0 || neutralSignal ? baseCanvas : blend(baseCanvas, accent, dark ? .035f : .025f);
        surface = this.customAccent == 0 || neutralSignal ? baseSurface : blend(baseSurface, accent, dark ? .045f : .025f);
        raisedSurface = this.customAccent == 0 || neutralSignal ? baseRaised : blend(baseRaised, accent, dark ? .07f : .045f);
        ink = dark ? darkInk : lightInk;
        muted = dark ? darkMuted : lightMuted;
        button = this.customAccent == 0 ? accent : accessibleAccent(accent);
        buttonText = this.customAccent == 0 ? (dark ? darkAccentText : lightAccentText) : bestText(button);
        int baseBorder = dark ? (themeIndex == GRAPHITE ? 0xFF333B45
                : themeIndex == SIGNAL ? 0xFF30352B : 0xFF38383A) : 0xFFD1D1D6;
        border = this.customAccent == 0 || neutralSignal ? baseBorder : blend(baseBorder, button, .24f);
        soft = raisedSurface;
        green = dark ? 0xFF54D68B : 0xFF167C4A;
        syncSuccess = dark && themeIndex == SIGNAL ? SIGNAL_SYNC_SUCCESS : green;
        amber = dark ? 0xFFFDBA4D : 0xFF975400;
        red = dark ? 0xFFFF777B : 0xFFB42318;
        track = dark ? 0xFF636366 : 0xFF8E8E93;
        highlight = this.customAccent == 0 ? button : accessibleTextAccent(accent, canvas, dark);
        hero = button;
        heroText = buttonText;
        heroMuted = muted;
        focusSurface = raisedSurface;
        int baseFocusBorder = dark ? (themeIndex == SIGNAL ? 0xFF687455 : 0xFF77777D) : 0xFF303630;
        focusBorder = this.customAccent == 0 ? baseFocusBorder : blend(baseFocusBorder, button, .32f);
    }

    static String nameFor(int index) {
        switch (normalizeIndex(index)) {
            case CUPERTINO: return "Cupertino";
            case GRAPHITE: return "Graphite";
            case ATELIER: return "Atelier";
            case SIGNAL:
            default: return "Signal";
        }
    }

    static String fontNameFor(int index) {
        switch (normalizeIndex(index)) {
            case CUPERTINO: return "Manrope";
            case GRAPHITE: return "IBM Plex Sans";
            case ATELIER: return "Instrument Sans · Newsreader";
            case SIGNAL:
            default: return "Inter";
        }
    }

    static int normalizeIndex(int index) {
        return index >= SIGNAL && index < THEME_COUNT ? index : SIGNAL;
    }

    static AppPalette get(boolean dark, int index) { return new AppPalette(dark, index, 0); }

    static AppPalette get(boolean dark, int index, int customAccent) {
        return new AppPalette(dark, index, customAccent);
    }

    static AppPalette get(int index) { return new AppPalette(true, index, 0); }

    private static int accessibleAccent(int color) {
        int text = bestText(color);
        if (contrastRatio(text, color) >= 4.5) return color;
        int target = text == 0xFFFFFFFF ? 0xFF000000 : 0xFFFFFFFF;
        for (int step = 1; step <= 100; step++) {
            int adjusted = blend(color, target, step / 100f);
            if (contrastRatio(bestText(adjusted), adjusted) >= 4.5) return adjusted;
        }
        return color;
    }

    private static int accessibleTextAccent(int color, int background, boolean dark) {
        if (contrastRatio(color, background) >= 4.5) return color;
        int target = dark ? 0xFFFFFFFF : 0xFF000000;
        for (int step = 1; step <= 100; step++) {
            int adjusted = blend(color, target, step / 100f);
            if (contrastRatio(adjusted, background) >= 4.5) return adjusted;
        }
        return target;
    }

    private static int bestText(int color) {
        return contrastRatio(0xFFFFFFFF, color) >= contrastRatio(0xFF111214, color)
                ? 0xFFFFFFFF : 0xFF111214;
    }

    private static double contrastRatio(int foreground, int background) {
        double a = luminance(foreground), b = luminance(background);
        return (Math.max(a, b) + .05) / (Math.min(a, b) + .05);
    }

    private static double luminance(int color) {
        return .2126 * linear((color >>> 16 & 255) / 255.0)
                + .7152 * linear((color >>> 8 & 255) / 255.0)
                + .0722 * linear((color & 255) / 255.0);
    }

    private static double linear(double channel) {
        return channel <= .04045 ? channel / 12.92 : Math.pow((channel + .055) / 1.055, 2.4);
    }

    private static int blend(int from, int to, float amount) {
        int r = Math.round(((from >>> 16) & 255) * (1 - amount) + ((to >>> 16) & 255) * amount);
        int g = Math.round(((from >>> 8) & 255) * (1 - amount) + ((to >>> 8) & 255) * amount);
        int b = Math.round((from & 255) * (1 - amount) + (to & 255) * amount);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }
}
