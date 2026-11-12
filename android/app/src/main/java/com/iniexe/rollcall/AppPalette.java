package com.iniexe.rollcall;

/**
 * TORCH GENESIS / Design tokens transcribed from rollcall_torch_prototype(1).html.
 * Brand colours are visual accents, never a replacement for semantic status colours.
 * The HTML's dark mode is canonical; the light values preserve its relative hierarchy.
 */
final class AppPalette {
    final String name;
    final int canvas, ink, muted, surface, border, soft, green, red, amber, track;
    final int button, buttonText, highlight, hero, heroText, heroMuted, focusSurface, focusBorder;

    private AppPalette(String name, int canvas, int ink, int muted, int surface, int border, int soft,
                       int green, int red, int amber, int track, int button, int buttonText,
                       int highlight, int hero, int heroText, int heroMuted, int focusSurface, int focusBorder) {
        this.name = name;
        this.canvas = canvas; this.ink = ink; this.muted = muted; this.surface = surface;
        this.border = border; this.soft = soft; this.green = green; this.red = red; this.amber = amber;
        this.track = track; this.button = button; this.buttonText = buttonText;
        this.highlight = highlight; this.hero = hero; this.heroText = heroText;
        this.heroMuted = heroMuted; this.focusSurface = focusSurface; this.focusBorder = focusBorder;
    }

    static AppPalette get(boolean dark, int ignoredLegacyTheme) {
        if (dark) {
            return new AppPalette("Torch Genesis",
                    0xFF121212, // canvas
                    0xFFF5F5EE, // paper text
                    0xFFA8AAA1, // subdued text
                    0xFF222222, // cards
                    0xFF525252, // 1dp outlines
                    0xFF2C2C2C, // panels
                    0xFF9BE4A1, // present/success
                    0xFFFF8C73, // warnings/absent readable on dark surfaces
                    0xFFFFC675,
                    0xFF555555,
                    0xFFCCFF00, // prototype vivid lime
                    0xFF111111,
                    0xFFFF4400, // prototype orange
                    0xFFCCFF00,
                    0xFF111111,
                    0xFF343A26,
                    0xFFC8C8FF, // lilac secondary callout
                    0xFF111111);
        }
        return new AppPalette("Torch Genesis / Day",
                0xFFF5F5EE, 0xFF121212, 0xFF53594F,
                0xFFFFFEFA, 0xFFB8B9B0, 0xFFE9EAE1,
                0xFF176146, 0xFFAA2921, 0xFF744500,
                0xFFA1A49A, 0xFF121212, 0xFFCCFF00,
                0xFFB42F08, 0xFFCCFF00, 0xFF111111,
                0xFF343A26, 0xFFC8C8FF, 0xFF121212);
    }
}
