package com.iniexe.rollcall;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.LinearInterpolator;

/** A live path redraws only while its owner reports activity and it is on screen. */
final class SignalPulseView extends View {
    private final SignalDrawing draw = new SignalDrawing(this);
    private final Rect visible = new Rect();
    private final int muted, accent;
    private final ViewTreeObserver.OnScrollChangedListener scroll = this::updateMotion;
    private ValueAnimator animator;
    private boolean active;
    private float fraction;
    SignalPulseView(Context context, int muted, int accent) {
        super(context); this.muted=muted; this.accent=accent;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    void setActive(boolean active) { this.active=active; updateMotion(); invalidate(); }
    private void updateMotion() {
        boolean run=active && isAttachedToWindow() && isShown() && hasWindowFocus()
                && getWindowVisibility()==VISIBLE && getLocalVisibleRect(visible) && NativeMotion.enabled(getContext());
        if (run && animator==null) {
            animator=ValueAnimator.ofFloat(0,1); animator.setDuration(1800);
            animator.setInterpolator(new LinearInterpolator()); animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.addUpdateListener(a -> { fraction=(float)a.getAnimatedValue(); invalidate(); }); animator.start();
        } else if(!run && animator!=null) { animator.cancel(); animator=null; fraction=0; invalidate(); }
    }
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        float left=draw.dp(8),right=getWidth()-draw.dp(8),y=getHeight()/2f;
        draw.line.setColor(muted); c.drawLine(left,y,right,y,draw.line);
        draw.marker.setColor(muted); c.drawCircle(left,y,draw.dp(3),draw.marker); c.drawCircle(right,y,draw.dp(3),draw.marker);
        if(active) { draw.marker.setColor(accent); c.drawCircle(left+(right-left)*fraction,y,draw.dp(4),draw.marker); }
    }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); getViewTreeObserver().addOnScrollChangedListener(scroll); updateMotion(); }
    @Override protected void onDetachedFromWindow() {
        getViewTreeObserver().removeOnScrollChangedListener(scroll);
        if(animator!=null) { animator.cancel(); animator=null; }
        super.onDetachedFromWindow();
    }
    @Override public void onWindowFocusChanged(boolean focus) { super.onWindowFocusChanged(focus); updateMotion(); }
    @Override protected void onWindowVisibilityChanged(int visibility) { super.onWindowVisibilityChanged(visibility); updateMotion(); }
    @Override protected void onVisibilityChanged(View view,int visibility) { super.onVisibilityChanged(view,visibility); updateMotion(); }
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh) { super.onSizeChanged(w,h,oldw,oldh); updateMotion(); }
}
