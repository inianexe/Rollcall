package com.iniexe.rollcall;

import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.os.Build;
import android.view.HapticFeedbackConstants;
import android.view.View;

/** Shared meaning: solid = recorded, dashed = hypothetical, outline = selected. */
final class SignalDrawing {
    static final long SELECT = 180, CHART = 260, FOLD = 340;
    final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint marker = new Paint(Paint.ANTI_ALIAS_FLAG);
    final DashPathEffect projection;
    final float density;

    SignalDrawing(View view) {
        density = view.getResources().getDisplayMetrics().density;
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(dp(2));
        line.setStrokeCap(Paint.Cap.ROUND);
        projection = new DashPathEffect(new float[]{dp(6), dp(5)}, 0);
    }

    float dp(float n) { return n * density; }

    static void tick(View view) {
        view.performHapticFeedback(Build.VERSION.SDK_INT >= 34
                ? HapticFeedbackConstants.SEGMENT_TICK : HapticFeedbackConstants.CLOCK_TICK);
    }
}
