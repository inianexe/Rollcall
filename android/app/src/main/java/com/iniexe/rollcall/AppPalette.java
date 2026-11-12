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

    /**
     * Torch Genesis is the single visual system throughout Rollcall.
     * Dark values are ported from rollcall_torch_prototype(1).html.
     * Light values preserve its black/lime/lilac/orange vocabulary on paper.
     */
    static AppPalette get(boolean dark, int ignoredLegacyTheme) {
        if (dark) return new AppPalette("Torch Genesis",
                0xFF121212, // canvas / --ink
                0xFFF5F5EE, // text / --paper
                0xFFA8AAA1, // muted
                0xFF222222, // cards
                0xFF525252, // borders
                0xFF2C2C2C, // raised controls
                0xFF9BE4A1, // positive
                0xFFFF8C73, // warning / destructive contrast
                0xFFFFC675, // pending
                0xFF555555, // progress track
                0xFFCCFF00, // high-priority action
                0xFF121212, // button label
                0xFFFF4400, // signature orange
                0xFFCCFF00, // hero
                0xFF111111, // hero title
                0xFF343A26, // hero secondary text
                0xFFC8C8FF, // lilac accent panel
                0xFF121212  // focus panel stroke
        );
        return new AppPalette("Torch Genesis",
                0xFFF5F5EE, // warm paper
                0xFF121212, // primary text
                0xFF51574E, // subdued text
                0xFFFFFFFF, // clean surface
                0xFFB9BDB2, // outline
                0xFFE8EAE0, // raised controls
                0xFF176146, // readable green
                0xFFAD321D, // readable red
                0xFF83510D, // readable amber
                0xFFABB09E, // progress track
                0xFF121212, // dark primary action
                0xFFCCFF00, // action label
                0xFFAD3000, // readable orange on warm paper
                0xFFCCFF00, // same hero in both modes
                0xFF111111, // hero text remains ink
                0xFF343A26, // secondary hero text
                0xFFC8C8FF, // lilac panel
                0xFF121212
        );
    }
}
