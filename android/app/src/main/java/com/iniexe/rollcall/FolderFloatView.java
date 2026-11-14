package com.iniexe.rollcall;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Press-open folder with floating, independently accessible actions. */
final class FolderFloatView extends LinearLayout {
    private final FolderArt art;
    private final LinearLayout notes;
    private ValueAnimator flapAnimator;
    private boolean open;

    FolderFloatView(Context context, int folderColor, int ink, int noteColor, Typeface typeface,
                    Runnable currentSemester, Runnable allRecords) {
        super(context);
        setOrientation(VERTICAL);
        art = new FolderArt(context, folderColor, ink, typeface);
        art.setContentDescription("Leave and OD folder, closed. Double tap to open actions.");
        art.setFocusable(true);
        art.setClickable(true);
        art.setOnClickListener(v -> toggle());
        addView(art, new LayoutParams(-1, dp(56)));
        notes = new LinearLayout(context);
        notes.setOrientation(VERTICAL);
        notes.setVisibility(GONE);
        notes.addView(note("This semester", ink, noteColor, typeface, currentSemester));
        notes.addView(note("All Leave & OD", ink, noteColor, typeface, allRecords));
        addView(notes, new LayoutParams(-1, -2));
    }

    private View note(String label, int ink, int fill, Typeface typeface, Runnable action) {
        TextView text = new TextView(getContext());
        text.setText(label);
        text.setTextSize(13);
        text.setTypeface(typeface);
        text.setTextColor(ink);
        text.setGravity(Gravity.CENTER_VERTICAL);
        text.setPadding(dp(18), 0, dp(12), 0);
        text.setMinHeight(dp(48));
        android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable();
        background.setColor(fill);
        background.setCornerRadius(dp(9));
        text.setBackground(background);
        text.setFocusable(true);
        text.setOnClickListener(v -> action.run());
        LayoutParams lp = new LayoutParams(-1, dp(48));
        lp.bottomMargin = dp(5);
        text.setLayoutParams(lp);
        return text;
    }

    void toggle() {
        open = !open;
        if (flapAnimator != null) flapAnimator.cancel();
        if (!NativeMotion.enabled(getContext())) {
            art.progress = open ? 1f : 0f;
            art.invalidate();
            notes.setVisibility(open ? VISIBLE : GONE);
        } else {
            if (open) {
                notes.setVisibility(VISIBLE);
                for (int i = 0; i < notes.getChildCount(); i++) {
                    View note = notes.getChildAt(i);
                    note.animate().cancel();
                    note.setAlpha(0f);
                    note.setTranslationY(dp(24));
                    note.setRotation(i == 0 ? -3f : 3f);
                    note.animate().alpha(1f).translationY(0f).rotation(0f)
                            .setStartDelay(i * 55L).setDuration(420)
                            .setInterpolator(new OvershootInterpolator(.6f)).start();
                }
            } else notes.setVisibility(GONE);
            flapAnimator = ValueAnimator.ofFloat(art.progress, open ? 1f : 0f);
            flapAnimator.setDuration(330);
            flapAnimator.addUpdateListener(a -> { art.progress = (float) a.getAnimatedValue(); art.invalidate(); });
            flapAnimator.start();
        }
        art.setContentDescription("Leave and OD folder, " + (open ? "open" : "closed") + ". Double tap to "
                + (open ? "close" : "open") + " actions.");
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override protected void onDetachedFromWindow() {
        if (flapAnimator != null) flapAnimator.cancel();
        for (int i = 0; i < notes.getChildCount(); i++) notes.getChildAt(i).animate().cancel();
        super.onDetachedFromWindow();
    }

    private static final class FolderArt extends View {
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Path flap = new Path();
        final RectF body = new RectF();
        final int folder, ink;
        final Typeface typeface;
        float progress;

        FolderArt(Context context, int folder, int ink, Typeface typeface) {
            super(context); this.folder = folder; this.ink = ink; this.typeface = typeface;
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float d = getResources().getDisplayMetrics().density;
            float left = 2 * d, top = 6 * d, width = Math.min(getWidth() - 4 * d, 210 * d);
            paint.setColor(folder);
            body.set(left, top + 12 * d, left + width, top + 48 * d);
            canvas.drawRoundRect(body, 8 * d, 8 * d, paint);
            paint.setColor(ink);
            paint.setAlpha(44);
            canvas.drawRoundRect(left + 9 * d, top + 5 * d - progress * 6 * d,
                    left + 70 * d, top + 27 * d, 5 * d, 5 * d, paint);
            paint.setAlpha(255);
            paint.setColor(folder);
            flap.reset();
            flap.moveTo(left, top + 20 * d);
            flap.lineTo(left + width, top + 20 * d);
            flap.lineTo(left + width - 8 * d, top + (48 + progress * 9) * d);
            flap.lineTo(left + 8 * d, top + (48 + progress * 9) * d);
            flap.close();
            canvas.drawPath(flap, paint);
            paint.setColor(ink);
            paint.setTextSize(12 * getResources().getDisplayMetrics().scaledDensity);
            paint.setTypeface(typeface);
            canvas.drawText("LEAVE & OD", left + 16 * d, top + 41 * d, paint);
        }
    }
}
