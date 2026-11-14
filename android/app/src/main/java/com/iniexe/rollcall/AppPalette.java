package com.iniexe.rollcall;

final class AppPalette {
    final String name;
    final boolean dark;
    final int canvas, ink, muted, surface, border, soft, green, red, amber, track;
    final int button, buttonText, highlight, hero, heroText, heroMuted, focusSurface, focusBorder;

    private AppPalette(String name, boolean dark, int canvas, int ink, int muted, int surface, int border, int soft,
                       int green, int red, int amber, int track, int button, int buttonText,
                       int highlight, int hero, int heroText, int heroMuted, int focusSurface, int focusBorder) {
        this.name = name;
        this.dark = dark;
        this.canvas = canvas; this.ink = ink; this.muted = muted; this.surface = surface;
        this.border = border; this.soft = soft; this.green = green; this.red = red; this.amber = amber;
        this.track = track; this.button = button; this.buttonText = buttonText;
        this.highlight = highlight; this.hero = hero; this.heroText = heroText;
        this.heroMuted = heroMuted; this.focusSurface = focusSurface; this.focusBorder = focusBorder;
    }

    static int normalizeIndex(int index) { return index >= 0 && index < 5 ? index : 0; }

    static AppPalette get(boolean dark, int index) {
        switch (normalizeIndex(index)) {
            case 1: return create("Cobalt Studio", dark, 0xFF3157D5, 0xFF90A9FF, 0xFFFFFFFF, 0xFF101827, 0xFF8A4B00, 0xFFFFC27A, 0xFF0D1430, 0xFFEAF0FF, 0xFFFFFFFF);
            case 2: return create("Copper Night", dark, 0xFFB7653D, 0xFFE6A07A, 0xFF000000, 0xFF000000, 0xFF873E21, 0xFFE6A07A, 0xFF281812, 0xFFFFE6D9, 0xFF000000);
            case 3: return create("Forest Circuit", dark, 0xFF287A57, 0xFF78D4A4, 0xFFFFFFFF, 0xFF10251C, 0xFF176146, 0xFF78D4A4, 0xFF10251C, 0xFFE4F4E9, 0xFFFFFFFF);
            case 4: return create("Paper Circuit", dark, 0xFF2563EB, 0xFF2563EB, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFB94A1A, 0xFFFFA77F, 0xFF27251F, 0xFFF5F0E4, 0xFFFFFFFF);
            default: return create("Signal", dark, 0xFFCCFF00, 0xFFCCFF00, 0xFF111827, 0xFF111827, 0xFFB42F08, 0xFFFF663D, 0xFF121212, 0xFFC8C8FF, 0xFF111827);
        }
    }

    private static AppPalette create(String name, boolean dark, int lightAccent, int darkAccent,
                                     int lightActionText, int darkActionText, int lightHighlight, int darkHighlight,
                                     int hero, int focus, int lightHeroText) {
        int canvas = dark ? 0xFF151719 : 0xFFF5F6F3;
        int ink = dark ? 0xFFF2F4F0 : 0xFF171A18;
        int muted = dark ? 0xFFB0B8B2 : 0xFF606963;
        int surface = dark ? 0xFF222628 : 0xFFFFFFFF;
        int border = dark ? 0xFF4A5350 : 0xFFD6DDD8;
        int soft = dark ? 0xFF2C3431 : 0xFFEDF1EE;
        int green = dark ? 0xFF8FE0B0 : 0xFF176146;
        int red = dark ? 0xFFFF9686 : 0xFFAA2921;
        int amber = dark ? 0xFFFFC675 : 0xFF744500;
        int track = dark ? 0xFF58605B : 0xFFA1AAA4;
        int heroText = dark ? (name.equals("Signal") ? 0xFF111827 : 0xFFF4F5EF) : lightHeroText;
        int heroMuted = dark ? 0xFFD1D7C8 : 0xFF343A26;
        int focusBorder = dark ? 0xFF111111 : 0xFF303630;
        int highlight = dark ? darkHighlight : lightHighlight;
        int accent = dark ? darkAccent : lightAccent;
        int buttonText = dark ? darkActionText : lightActionText;
        return new AppPalette(name, dark, canvas, ink, muted, surface, border, soft, green, red, amber, track,
                accent, buttonText, highlight,
                dark && !name.equals("Signal") ? hero : accent, heroText, heroMuted,
                dark ? blend(focus, surface, .12f) : focus, focusBorder);
    }

    private static int blend(int color, int base, float amount) {
        int r = Math.round(ColorPart.r(color) * (1f - amount) + ColorPart.r(base) * amount);
        int g = Math.round(ColorPart.g(color) * (1f - amount) + ColorPart.g(base) * amount);
        int b = Math.round(ColorPart.b(color) * (1f - amount) + ColorPart.b(base) * amount);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static final class ColorPart {
        static int r(int color) { return (color >> 16) & 255; }
        static int g(int color) { return (color >> 8) & 255; }
        static int b(int color) { return color & 255; }
    }
}
