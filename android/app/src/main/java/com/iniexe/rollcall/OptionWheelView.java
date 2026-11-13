package com.iniexe.rollcall;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/** Curved, snapping picker with frame-independent smoothing from React Bits Option Wheel. */
final class OptionWheelView extends View {
    interface OnSelection { void selected(int index); }
    private final TextPaint paint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
    private final RectF focus = new RectF();
    private final List<String> items = new ArrayList<>();
    private final int ink, muted, highlight;
    private OnSelection listener;
    private float position, target, downY, downTarget;
    private long lastFrame;
    private boolean dragging;
    private int selected;

    OptionWheelView(Context context, int ink, int muted, int highlight) {
        super(context);
        this.ink = ink; this.muted = muted; this.highlight = highlight;
        paint.setTypeface(android.graphics.Typeface.create("sans-serif-medium", 1));
        paint.setTextSize(getResources().getDisplayMetrics().scaledDensity * 19f);
        setFocusable(true);
        setClickable(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    void setItems(List<String> labels, int initial, OnSelection callback) {
        items.clear(); items.addAll(labels);
        listener = callback;
        selected = Math.max(0, Math.min(initial, items.size() - 1));
        position = target = selected;
        describe();
        invalidate();
    }

    int selection() { return selected; }

    void select(int index) {
        if (items.isEmpty()) return;
        int next = Math.max(0, Math.min(index, items.size() - 1));
        if (next == selected) { target = selected; invalidate(); return; }
        selected = next;
        target = next;
        if (!NativeMotion.enabled(getContext())) position = target;
        describe();
        invalidate();
        if (listener != null) listener.selected(next);
    }

    private void describe() {
        setContentDescription(items.isEmpty() ? "No semesters available"
                : "Semester picker, " + items.get(selected) + ". Swipe up or down, or use the arrow buttons.");
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(resolveSize(dp(280), widthSpec), resolveSize(dp(150), heightSpec));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (items.isEmpty()) return;
        long now = android.os.SystemClock.uptimeMillis();
        if (NativeMotion.enabled(getContext()) && Math.abs(position - target) > .001f) {
            float dt = Math.min(50f, lastFrame == 0 ? 16f : now - lastFrame);
            position += (target - position) * (1f - (float) Math.exp(-dt / 200f));
            if (Math.abs(position - target) < .001f) position = target;
            postInvalidateOnAnimation();
        } else position = target;
        lastFrame = now;
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float rowH = dp(40), tilt = (float) Math.toRadians(6), radius = rowH / tilt;
        focus.set(dp(4), cy - dp(23), getWidth() - dp(4), cy + dp(23));
        paint.setColor(highlight);
        paint.setAlpha(32);
        canvas.drawRoundRect(focus, dp(10), dp(10), paint);
        for (int i = 0; i < items.size(); i++) {
            float distance = i - position;
            if (Math.abs(distance) > 2.5f) continue;
            float angle = Math.max(-(float) Math.PI / 2, Math.min((float) Math.PI / 2, distance * tilt));
            float x = -radius * (1f - (float) Math.cos(angle));
            float y = radius * (float) Math.sin(angle);
            float opacity = Math.max(.1f, 1f - Math.abs(distance) * .34f);
            paint.setColor(i == selected ? ink : muted);
            paint.setAlpha(Math.round(255 * opacity));
            float available = getWidth() - dp(32) + x;
            CharSequence label = TextUtils.ellipsize(items.get(i), paint, available, TextUtils.TruncateAt.END);
            float tx = cx - paint.measureText(label.toString()) / 2f + x;
            float baseline = cy + y - (paint.ascent() + paint.descent()) / 2f;
            canvas.save();
            canvas.rotate((float) Math.toDegrees(angle), cx + x, cy + y);
            canvas.drawText(label.toString(), tx, baseline, paint);
            canvas.restore();
        }
        paint.setAlpha(255);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (items.isEmpty()) return super.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downY = event.getY(); downTarget = target; dragging = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                float dy = event.getY() - downY;
                if (Math.abs(dy) > dp(4)) {
                    dragging = true;
                    getParent().requestDisallowInterceptTouchEvent(true);
                    target = Math.max(0, Math.min(items.size() - 1, downTarget - dy / dp(40)));
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (dragging) select(Math.round(target));
                else {
                    int step = event.getY() < getHeight() * .35f ? -1 : event.getY() > getHeight() * .65f ? 1 : 0;
                    if (step != 0) select(selected + step);
                    else performClick();
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                target = selected; invalidate(); return true;
            default: return super.onTouchEvent(event);
        }
    }

    @Override public boolean performClick() {
        super.performClick();
        select(selected + 1 < items.size() ? selected + 1 : 0);
        return true;
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) { select(selected - 1); return true; }
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) { select(selected + 1); return true; }
        return super.onKeyDown(keyCode, event);
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
