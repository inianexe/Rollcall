package com.iniexe.rollcall;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import java.util.function.IntConsumer;

final class ClassHistoryTraceView extends View {
    private final SignalDrawing draw = new SignalDrawing(this);
    private final Path path = new Path();
    private final HistorySeries data;
    private final int ink, positive, negative, muted;
    private final int slop;
    private final IntConsumer listener;
    private int selected;
    private float startX, startY;
    private boolean scrubbing;

    ClassHistoryTraceView(Context context, HistorySeries data, int ink, int positive, int negative,
                          int muted, IntConsumer listener) {
        super(context);
        this.data = data; this.ink = ink; this.positive = positive; this.negative = negative; this.muted = muted;
        this.listener = listener;
        slop = ViewConfiguration.get(context).getScaledTouchSlop();
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void select(int value, boolean haptic) {
        if (data.events.isEmpty()) return;
        int next = Math.max(0, Math.min(data.events.size() - 1, value));
        if (next != selected && haptic) SignalDrawing.tick(this);
        selected = next;
        listener.accept(selected);
        invalidate();
    }
    int selected() { return selected; }
    private float x(int i) { return draw.dp(12) + (getWidth() - draw.dp(24)) * (data.events.size() == 1 ? .5f : (float) i / Math.max(1, data.events.size() - 1)); }
    private float y(int i) { return data.complete ? getHeight() - draw.dp(12) - (getHeight() - draw.dp(24)) * (float) data.percentages[i] / 100 : getHeight() / 2f; }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        draw.line.setColor(muted);
        if (data.complete) {
            path.rewind();
            for (int i = 0; i < data.events.size(); i++) { if (i == 0) path.moveTo(x(i), y(i)); else path.lineTo(x(i), y(i)); }
            c.drawPath(path, draw.line);
        } else c.drawLine(draw.dp(12), getHeight() / 2f, getWidth() - draw.dp(12), getHeight() / 2f, draw.line);
        for (int i = 0; i < data.events.size(); i++) {
            HistorySeries.Event e = data.events.get(i);
            float x = x(i), y = y(i), r = draw.dp(3);
            draw.marker.setColor("present".equals(e.status) ? positive : "absent".equals(e.status) ? negative : muted);
            if ("present".equals(e.status)) c.drawCircle(x, y, r, draw.marker);
            else if ("absent".equals(e.status)) c.drawRect(x-r, y-r, x+r, y+r, draw.marker);
            else { draw.line.setColor(muted); c.drawCircle(x, y, r, draw.line); }
            if (e.approvedOd) { draw.line.setColor(ink); c.drawLine(x-r, y+draw.dp(7), x+r, y+draw.dp(7), draw.line); }
            if (i == selected) { draw.line.setColor(ink); c.drawCircle(x, y, draw.dp(9), draw.line); }
        }
    }

    private void selectAt(float x) {
        float fraction = (x - draw.dp(12)) / Math.max(1, getWidth() - draw.dp(24));
        select(Math.round(fraction * Math.max(0, data.events.size() - 1)), true);
    }
    @Override public boolean onTouchEvent(MotionEvent e) {
        if (data.events.isEmpty()) return false;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                startX = e.getX(); startY = e.getY(); scrubbing = false;
                getParent().requestDisallowInterceptTouchEvent(true); return true;
            case MotionEvent.ACTION_MOVE:
                if (!scrubbing && Math.abs(e.getY()-startY) > slop && Math.abs(e.getY()-startY) > Math.abs(e.getX()-startX)) {
                    getParent().requestDisallowInterceptTouchEvent(false); return false;
                }
                if (Math.abs(e.getX()-startX) > slop) scrubbing = true;
                if (scrubbing) selectAt(e.getX()); return true;
            case MotionEvent.ACTION_UP:
                selectAt(e.getX()); performClick();
                getParent().requestDisallowInterceptTouchEvent(false); return true;
            case MotionEvent.ACTION_CANCEL:
                getParent().requestDisallowInterceptTouchEvent(false); return true;
            default: return true;
        }
    }
    @Override public boolean performClick() { super.performClick(); return true; }
}
