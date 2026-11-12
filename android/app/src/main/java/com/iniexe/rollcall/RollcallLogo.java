package com.iniexe.rollcall;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;

final class RollcallLogo extends View {
    static final class Mark {
        final Drawable mark;
        Mark(Drawable mark) { this.mark = mark; }
    }

    private final Mark mark;

    RollcallLogo(Context context, AppPalette palette) {
        super(context);
        mark = themedMark(context, palette);
        setContentDescription("Rollcall logo");
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float size = Math.min(getWidth(), getHeight());
        draw(mark, canvas, (getWidth() - size) / 2f, (getHeight() - size) / 2f,
                size, 1f);
    }

    static Mark themedMark(Context context, AppPalette palette) {
        Drawable mark = context.getDrawable(R.drawable.rollcall_brush).mutate();
        mark.setTint(palette.highlight);
        return new Mark(mark);
    }

    static void draw(Mark mark, Canvas canvas, float left, float top, float size, float alpha) {
        if (size <= 0 || alpha <= 0) return;
        float artworkSize = size * 1.12f;
        float x = left + (size - artworkSize) / 2f;
        float y = top + (size - artworkSize) / 2f;
        Rect bounds = new Rect(Math.round(x), Math.round(y), Math.round(x + artworkSize),
                Math.round(y + artworkSize));
        drawLayer(mark.mark, canvas, bounds, alpha);
    }

    private static void drawLayer(Drawable drawable, Canvas canvas, Rect bounds, float alpha) {
        int previousAlpha = drawable.getAlpha();
        Rect previousBounds = new Rect(drawable.getBounds());
        drawable.setBounds(bounds);
        drawable.setAlpha(Math.round(255 * alpha));
        drawable.draw(canvas);
        drawable.setAlpha(previousAlpha);
        drawable.setBounds(previousBounds);
    }
}
