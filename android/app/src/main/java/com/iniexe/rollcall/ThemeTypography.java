package com.iniexe.rollcall;

import android.content.Context;
import android.graphics.Typeface;

final class ThemeTypography {
    final Typeface body;
    final Typeface bodyBold;
    final Typeface display;

    private ThemeTypography(Typeface body, Typeface bodyBold, Typeface display) {
        this.body = body;
        this.bodyBold = bodyBold;
        this.display = display;
    }

    static ThemeTypography load(Context context, int requestedTheme) {
        int theme = AppPalette.normalizeIndex(requestedTheme);
        switch (theme) {
            case AppPalette.CUPERTINO: {
                Typeface body = asset(context, "fonts/Manrope.ttf", Typeface.create("sans-serif", Typeface.NORMAL));
                Typeface semibold = weight(body, 600);
                return new ThemeTypography(body, semibold, semibold);
            }
            case AppPalette.GRAPHITE: {
                Typeface body = asset(context, "fonts/IBM-Plex-Sans.ttf", Typeface.create("sans-serif", Typeface.NORMAL));
                Typeface semibold = weight(body, 600);
                return new ThemeTypography(body, semibold, semibold);
            }
            case AppPalette.ATELIER: {
                Typeface body = asset(context, "fonts/Instrument-Sans.ttf", Typeface.create("sans-serif", Typeface.NORMAL));
                Typeface display = asset(context, "fonts/Newsreader.ttf", body);
                return new ThemeTypography(body, weight(body, 600), weight(display, 600));
            }
            case AppPalette.SIGNAL:
            default: {
                Typeface body = asset(context, "fonts/InterVariable.ttf", Typeface.create("sans-serif", Typeface.NORMAL));
                Typeface bold = weight(body, 700);
                return new ThemeTypography(body, bold, weight(body, 650));
            }
        }
    }

    private static Typeface asset(Context context, String path, Typeface fallback) {
        try { return Typeface.createFromAsset(context.getAssets(), path); }
        catch (RuntimeException ignored) { return fallback; }
    }

    private static Typeface weight(Typeface typeface, int weight) {
        if (android.os.Build.VERSION.SDK_INT >= 28) return Typeface.create(typeface, weight, false);
        return Typeface.create(typeface, weight >= 600 ? Typeface.BOLD : Typeface.NORMAL);
    }
}
