package com.iniexe.rollcall;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.widget.FrameLayout;

/** Offline host for Java dashboard instrumentation tests; absent from release builds. */
public final class DashboardTestActivity extends Activity {
    DashboardView dashboard;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        boolean light = getIntent().getBooleanExtra("light", false);
        int canvas = AppPalette.get(!light, 0).canvas;
        getWindow().setStatusBarColor(Build.VERSION.SDK_INT >= 30 ? Color.TRANSPARENT : canvas);
        getWindow().setNavigationBarColor(Build.VERSION.SDK_INT >= 30 ? Color.TRANSPARENT : canvas);
        getWindow().getDecorView().setBackgroundColor(canvas);
        if (Build.VERSION.SDK_INT >= 29) {
            getWindow().setStatusBarContrastEnforced(false);
            getWindow().setNavigationBarContrastEnforced(false);
        }
        if (Build.VERSION.SDK_INT >= 30 && getWindow().getInsetsController() != null) {
            int flags = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            getWindow().getInsetsController().setSystemBarsAppearance(light ? flags : 0, flags);
        }
        dashboard = new DashboardView(this, new DashboardView.Actions() {
            @Override public void onSync() { }
            @Override public void onPortal() { }
            @Override public void onAcademicsPortal() { }
            @Override public void onTarget() { }
            @Override public void onTheme() { }
            @Override public void onPalette() { }
            @Override public void onAbout() { }
            @Override public void onRemindersToggle() { }
            @Override public void onFilter(boolean belowOnly) { }
            @Override public void onQuery(String value) { }
            @Override public void onCourse(String code) { }
        }, AppPalette.get(!light, 0));
        FrameLayout root = new FrameLayout(this);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            root.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        root.addView(dashboard);
        setContentView(root);
    }
}
