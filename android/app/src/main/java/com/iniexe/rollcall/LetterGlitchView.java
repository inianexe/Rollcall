package com.iniexe.rollcall;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.View;

import java.util.Random;

/** A small, bounded character field based on React Bits Letter Glitch. */
final class LetterGlitchView extends View {
    private static final char[] CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789+-[]{}".toCharArray();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();
    private final int base, accent;
    private char[] cells = new char[0];
    private int columns, rows;
    private long nextFrame;

    LetterGlitchView(Context context, int base, int accent) {
        super(context);
        this.base = base; this.accent = accent;
        paint.setTypeface(Typeface.MONOSPACE);
        paint.setTextSize(getResources().getDisplayMetrics().scaledDensity * 11f);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float cellW = Math.max(1f, paint.measureText("W") * 1.6f);
        columns = Math.max(1, (int) (w / cellW));
        rows = Math.max(1, (int) (h / (paint.getTextSize() * 1.45f)));
        cells = new char[columns * rows];
        for (int i = 0; i < cells.length; i++) cells[i] = CHARS[random.nextInt(CHARS.length)];
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (columns == 0 || rows == 0) return;
        float cw = getWidth() / (float) columns, ch = getHeight() / (float) rows;
        for (int i = 0; i < cells.length; i++) {
            paint.setColor(i % 17 == 0 ? accent : base);
            paint.setAlpha(i % 17 == 0 ? 205 : 100);
            canvas.drawText(cells, i, 1, (i % columns) * cw,
                    (i / columns + 1) * ch - (ch - paint.getTextSize()) / 2f, paint);
        }
        if (NativeMotion.enabled(getContext()) && isShown() && getWindowVisibility() == VISIBLE) {
            long now = android.os.SystemClock.uptimeMillis();
            if (now >= nextFrame) {
                for (int n = 0; n < Math.max(1, cells.length / 8); n++) {
                    int i = random.nextInt(cells.length);
                    cells[i] = CHARS[random.nextInt(CHARS.length)];
                }
                nextFrame = now + 120;
            }
            postInvalidateDelayed(Math.max(1, nextFrame - now));
        }
    }

    @Override protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == VISIBLE) invalidate();
    }
}
