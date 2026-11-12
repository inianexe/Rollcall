package com.iniexe.rollcall;

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
        if (dark) return new AppPalette("Rollcall Signal", 0xFF121212, 0xFFF4F5EF, 0xFFC2C6B7,
                0xFF292929, 0xFF666765, 0xFF343628, 0xFF90E1B4, 0xFFFF9784, 0xFFFFC675,
                0xFF525745, 0xFFCCFF00, 0xFF121212, 0xFFFF663D, 0xFFCCFF00, 0xFF121212,
                0xFF3F4B16, 0xFF292929, 0xFFFF663D);
        return new AppPalette("Rollcall Signal", 0xFFF5F4EC, 0xFF182019, 0xFF586057,
                0xFFFFFEFA, 0xFFC6C8BC, 0xFFEEEEE6, 0xFF176146, 0xFFAA2921, 0xFF744500,
                0xFF91AA31, 0xFF121212, 0xFFCCFF00, 0xFFAF3518, 0xFFCCFF00, 0xFF111827,
                0xFF3E4651, 0xFFDEDCFF, 0xFFAF3518);
    }
}
