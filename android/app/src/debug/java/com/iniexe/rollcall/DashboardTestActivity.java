package com.iniexe.rollcall;

import android.app.Activity;
import android.os.Bundle;

/** Offline host for Java dashboard instrumentation tests; absent from release builds. */
public final class DashboardTestActivity extends Activity {
    DashboardView dashboard;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        dashboard = new DashboardView(this, new DashboardView.Actions() {
            @Override public void onSync() { }
            @Override public void onPortal() { }
            @Override public void onAcademicsPortal() { }
            @Override public void onTarget() { }
            @Override public void onTheme() { }
            @Override public void onPalette() { }
            @Override public void onAbout() { }
            @Override public void onFilter(boolean belowOnly) { }
            @Override public void onQuery(String value) { }
            @Override public void onCourse(String code) { }
        }, AppPalette.get(true, 0));
        setContentView(dashboard);
    }
}
