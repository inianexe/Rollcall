package com.iniexe.rollcall;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

abstract class SignalPanel extends LinearLayout {
    final AppPalette palette;
    final Typeface font;
    SignalPanel(Context context, AppPalette palette, Typeface font) {
        super(context); this.palette = palette; this.font = font; setOrientation(VERTICAL);
    }
    int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    TextView copy(String value, int size, int color) {
        TextView text = new TextView(getContext());
        text.setText(value); text.setTextSize(size); text.setTextColor(color); text.setTypeface(font);
        text.setIncludeFontPadding(false); text.setLineSpacing(dp(2), 1);
        return text;
    }
    TextView action(String title, Runnable click) {
        TextView button = copy(title, 14, palette.ink);
        button.setGravity(Gravity.CENTER_VERTICAL);
        button.setPadding(dp(12), dp(10), dp(12), dp(10));
        button.setMinHeight(dp(48)); button.setFocusable(true);
        button.setBackground(surface(palette.surface));
        button.setOnClickListener(v -> { SignalDrawing.tick(v); click.run(); });
        return button;
    }
    GradientDrawable surface(int color) {
        GradientDrawable bg = new GradientDrawable(); bg.setColor(color);
        bg.setCornerRadius(dp(palette.controlRadius)); bg.setStroke(dp(1), palette.border);
        return bg;
    }
    void addSpace(android.view.View view, int space) {
        LayoutParams lp = new LayoutParams(-1, -2); lp.topMargin = dp(space); addView(view, lp);
    }
}
