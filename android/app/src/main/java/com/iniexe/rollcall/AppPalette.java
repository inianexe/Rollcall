package com.iniexe.rollcall;

final class AppPalette {
    final String name;
    // Exact source-prototype signature colour, consistent across every screen.
    final int lilac = 0xFFC8C8FF;
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
        // Source of truth: user-supplied rollcall_torch_prototype(1).html.
        // Preserve the neutral majority; lime is an action colour, not a page background.
        if (dark) return new AppPalette("Torch Genesis", 0xFF121212, 0xFFF5F5EE, 0xFFA8AAA1,
                0xFF222222, 0xFF595959, 0xFF2C2C2C,
                0xFF9BE4A1, 0xFFFF8C73, 0xFFFFC675, 0xFF555555,
                0xFFCCFF00, 0xFF111111, 0xFFFF4400, 0xFFCCFF00, 0xFF111111,
                0xFF343A26, 0xFF292929, 0xFF707070);
        // Calm high-contrast companion palette for accessibility and outdoor legibility.
        return new AppPalette("Torch Genesis", 0xFFF5F5EE, 0xFF182019, 0xFF555D55,
                0xFFFFFEFA, 0xFFC6C8BC, 0xFFECECE6,
                0xFF176146, 0xFFAA2921, 0xFF744500, 0xFF91AA31,
                0xFF121212, 0xFFCCFF00, 0xFFAF3518, 0xFFCCFF00, 0xFF111111,
                0xFF343A26, 0xFFEAE9FF, 0xFFCB421B);
    }
}
