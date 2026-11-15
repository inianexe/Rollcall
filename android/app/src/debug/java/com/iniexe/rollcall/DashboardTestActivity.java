package com.iniexe.rollcall;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;

/** Offline host for Java dashboard instrumentation tests; absent from release builds. */
public final class DashboardTestActivity extends Activity {
    DashboardView dashboard;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        int canvas = AppPalette.get(0).canvas;
        getWindow().setStatusBarColor(Build.VERSION.SDK_INT >= 30 ? Color.TRANSPARENT : canvas);
        getWindow().setNavigationBarColor(Build.VERSION.SDK_INT >= 30 ? Color.TRANSPARENT : canvas);
        getWindow().getDecorView().setBackgroundColor(canvas);
        if (Build.VERSION.SDK_INT >= 29) {
            getWindow().setStatusBarContrastEnforced(false);
            getWindow().setNavigationBarContrastEnforced(false);
        }
        int systemUi = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        getWindow().getDecorView().setSystemUiVisibility(systemUi);
        dashboard = new DashboardView(this, new DashboardView.Actions() {
            @Override public void onSync() { }
            @Override public void onPortal() { }
            @Override public void onAcademicsPortal() { }
            @Override public void onTarget() { }
            @Override public void onAbout() { }
            @Override public void onProfile() { }
            @Override public void onRemindersToggle() { }
            @Override public void onFilter(boolean belowOnly) { }
            @Override public void onQuery(String value) { }
            @Override public void onCourse(String code) { }
            @Override public void onAnnouncementReceipt(String campaignId, String receiptState) { }
            @Override public void onHomePageShown() { }
        }, AppPalette.get(0));
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(canvas);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            root.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        root.addView(dashboard);
        setContentView(root);
    }
}
