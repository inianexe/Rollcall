package com.iniexe.rollcall;

import android.content.Context;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

/** One-time, layout-stable reveal based on React Bits Animated Content. */
final class AnimatedContentView extends FrameLayout {
    private boolean revealed;
    private final Runnable onReveal;
    private final ViewTreeObserver.OnScrollChangedListener scrollListener = this::reveal;

    AnimatedContentView(Context context, View content, boolean alreadyRevealed, Runnable onReveal) {
        super(context);
        revealed = alreadyRevealed;
        this.onReveal = onReveal;
        addView(content, new LayoutParams(-1, -2));
    }

    void reveal() {
        if (revealed || !isShown()) return;
        Rect visible = new Rect();
        if (!getGlobalVisibleRect(visible) || visible.height() < Math.min(getHeight(),
                getResources().getDisplayMetrics().density * 12f)) return;
        revealed = true;
        if (onReveal != null) onReveal.run();
        if (!NativeMotion.enabled(getContext())) return;
        View content = getChildAt(0);
        content.animate().cancel();
        content.setTranslationY(getResources().getDisplayMetrics().density * 18f);
        content.setAlpha(.35f);
        content.animate().translationY(0f).alpha(1f).setDuration(560)
                .setInterpolator(new DecelerateInterpolator(2.5f)).start();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getViewTreeObserver().addOnScrollChangedListener(scrollListener);
        post(this::reveal);
    }

    @Override protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == VISIBLE) post(this::reveal);
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (changed) post(this::reveal);
    }

    @Override protected void onDetachedFromWindow() {
        if (getViewTreeObserver().isAlive()) getViewTreeObserver().removeOnScrollChangedListener(scrollListener);
        if (getChildCount() > 0) {
            View content = getChildAt(0);
            content.animate().cancel();
            content.setTranslationY(0f);
            content.setAlpha(1f);
        }
        super.onDetachedFromWindow();
    }
}
