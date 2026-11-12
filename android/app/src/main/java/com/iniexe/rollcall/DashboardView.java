package com.iniexe.rollcall;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.webkit.WebViewAssetLoader;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Exact Torch Genesis HTML presentation, connected to the original Java data/service layer.
 * The reference HTML/CSS is shipped as assets; user data comes only from MainActivity.
 * This WebView NEVER navigates to MyCamu (which runs in MainActivity's separate WebView).
 */
final class DashboardView extends FrameLayout {
    interface Actions {
        void onSync();
        void onPortal();
        void onAcademicsPortal();
        void onTarget();
        void onTheme();
        void onPalette();
        void onAbout();
        void onFilter(boolean belowOnly);
        void onQuery(String value);
        void onCourse(String code);
        void onReminderToggle();
        void onNotifications();
        void onCloudAccount();
        void onUpdates();
        void onClearSavedData();
        void onPrivacy();
        void onTerms();
        void onProject();
        void onTargetChanged(int target);
    }

    static final class State {
        boolean dark;
        String name = "MyCamu student", initials = "R";
        String status = "", heroMessage = "— / —";
        String overallCaption = "classes attended", targetHint = "";
        int target = 75, overallPercent = -1, courses, below, onTrack;
        boolean belowOnly, syncing, syncError, sessionExpired, syncSucceeded, attendanceLoaded, partialSync;
        String partialSyncStatus = "";
        boolean timetableLoaded, leavesLoaded;
        long timetableUpdatedAt, classMarksUpdatedAt;
        String query = "";
        String latestClassName = "", latestClassStatus = "";
        List<Course> rows = new ArrayList<>();
        List<ScheduleItem> timetable = new ArrayList<>();
        List<LeaveItem> leaves = new ArrayList<>();
        List<ClassMarkItem> classMarks = new ArrayList<>();
        List<AcademicCourse> academicCourses = new ArrayList<>();
        long academicUpdatedAt;
        boolean academicConnected;
        String academicStatus = "";
        String upcomingExamTitle = "";
        String upcomingExamType = "";
        String upcomingExamSession = "";
        String upcomingExamCode = "";
        String upcomingExamDetails = "";
        long upcomingExamAt;
        boolean upcomingExamHasTime;
        boolean remindersEnabled;
        String version = "";
        Set<String> approvedOdClasses = new HashSet<>();
        Set<String> currentSemesterClassKeys = new HashSet<>();
        Set<String> currentSemesterOdClasses = new HashSet<>();
    }

    static final class Course {
        String code = "", name = "", percentLabel = "", status = "", counts = "", guidance = "", targetCount = "";
        float percent;
        int attended, total, color, odCredit, odCount;
        boolean odReconciled;
        boolean countsKnown, below, expanded;
    }
    static final class ScheduleItem {
        String date = "", time = "", name = "", code = "", details = "";
        long startsAt, endsAt;
        boolean allDay, cancelled;
    }
    static final class LeaveItem {
        String id = "", date = "", type = "Leave", status = "Unknown", details = "",
                periodNumbers = "", periodDetails = "[]";
        long startsAt, endsAt;
        int periods, color;
        boolean currentSemester, semesterKnown;
    }
    static final class ClassMarkItem {
        String day = "", time = "", code = "", name = "", status = "",
                leaveType = "", leaveStatus = "";
        long startsAt;
        int color;
    }
    static final class AcademicCourse {
        String semester = "", code = "", name = "", grade = "", result = "";
        double credits;
    }

    private final Actions actions;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final WebView web;
    private State state = new State();
    private boolean ready;
    private volatile String currentPage = "home";
    private static final String ORIGIN = "appassets.androidplatform.net";
    private static final String HOME_ASSET =
            "https://appassets.androidplatform.net/assets/rollcall_torch_native.html";

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    DashboardView(Context context, Actions actions, AppPalette palette) {
        super(context);
        this.actions = actions;
        setBackgroundColor(palette.canvas);
        web = new WebView(context);
        web.setBackgroundColor(palette.canvas);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.setVerticalScrollBarEnabled(false);
        web.setHorizontalScrollBarEnabled(false);
        web.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setDefaultTextEncodingName("UTF-8");
        WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .setDomain(ORIGIN)
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(context))
                .build();
        web.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(
                    WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if ("https".equals(uri.getScheme()) && ORIGIN.equals(uri.getHost()))
                    return loader.shouldInterceptRequest(uri);
                // Prevent untrusted network resources and remote frame/script inclusion.
                return new WebResourceResponse("text/plain", "UTF-8",
                        new java.io.ByteArrayInputStream(new byte[0]));
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if ("https".equals(uri.getScheme()) && ORIGIN.equals(uri.getHost())) return false;
                String host = uri.getHost();
                if ("https".equals(uri.getScheme()) && host != null
                        && ("github.com".equals(host) || "www.github.com".equals(host))) {
                    try { getContext().startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
                    catch (Exception ignored) { /* No browser available; remain in app. */ }
                }
                return true;
            }
        });
        // The JS interface exists ONLY on this packaged, navigation-restricted local page.
        web.addJavascriptInterface(new Bridge(), "RollcallBridge");
        addView(web, new FrameLayout.LayoutParams(-1, -1));
        web.loadUrl(HOME_ASSET);
    }

    private final class Bridge {
        private void ui(Runnable task) { mainHandler.post(task); }
        @JavascriptInterface public void ready(String unused) {
            ui(() -> { ready = true; publish(); });
        }
        @JavascriptInterface public void page(String destination) {
            currentPage = destination;
        }
        @JavascriptInterface public void sync(String unused) { ui(actions::onSync); }
        @JavascriptInterface public void toggleTheme(String unused) { ui(actions::onTheme); }
        @JavascriptInterface public void reminders(String unused) { ui(actions::onReminderToggle); }
        @JavascriptInterface public void toggleReminders(String unused) { ui(actions::onReminderToggle); }
        @JavascriptInterface public void notifications(String unused) { ui(actions::onNotifications); }
        @JavascriptInterface public void cloud(String unused) { ui(actions::onCloudAccount); }
        @JavascriptInterface public void kct(String unused) { ui(actions::onAcademicsPortal); }
        @JavascriptInterface public void updates(String unused) { ui(actions::onUpdates); }
        @JavascriptInterface public void clear(String unused) { ui(actions::onClearSavedData); }
        @JavascriptInterface public void privacy(String unused) { ui(actions::onPrivacy); }
        @JavascriptInterface public void terms(String unused) { ui(actions::onTerms); }
        @JavascriptInterface public void github(String unused) { ui(actions::onProject); }
        @JavascriptInterface public void setTarget(String selected) {
            ui(() -> {
                try { actions.onTargetChanged(Math.max(50, Math.min(100, Integer.parseInt(selected)))); }
                catch (NumberFormatException ignored) { }
            });
        }
    }

    void update(State next) {
        if (next == null) return;
        state = next;
        publish();
    }

    private void publish() {
        if (!ready) return;
        try {
            JSONObject data = toJson(state);
            String safeJson = data.toString();
            web.evaluateJavascript("window.__rollcallRender(" + safeJson + ");window.__rollcallNavigate(" + JSONObject.quote(currentPage) + ");", null);
        } catch (JSONException ignored) {
            // A bad record must not crash Rollcall's native attendance service.
        }
    }

    private static JSONObject toJson(State s) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("dark", s.dark);
        json.put("name", s.name);
        json.put("initials", s.initials);
        json.put("status", s.status);
        json.put("target", s.target);
        json.put("syncing", s.syncing);
        json.put("syncError", s.syncError);
        json.put("remindersEnabled", s.remindersEnabled);
        json.put("version", s.version);
        json.put("timetableLoaded", s.timetableLoaded);
        json.put("leavesLoaded", s.leavesLoaded);
        json.put("academicConnected", s.academicConnected);
        json.put("upcomingExamAt", s.upcomingExamAt);
        json.put("upcomingExamTitle", s.upcomingExamTitle);
        json.put("upcomingExamType", s.upcomingExamType);
        json.put("upcomingExamSession", s.upcomingExamSession);
        json.put("upcomingExamCode", s.upcomingExamCode);
        json.put("upcomingExamDetails", s.upcomingExamDetails);
        JSONArray rows = new JSONArray();
        for (Course c : s.rows) {
            JSONObject item = new JSONObject();
            item.put("code", c.code); item.put("name", c.name);
            item.put("percent", c.percent); item.put("percentLabel", c.percentLabel);
            item.put("status", c.status); item.put("counts", c.counts);
            item.put("guidance", c.guidance); item.put("targetCount", c.targetCount);
            item.put("attended", c.attended); item.put("total", c.total);
            item.put("countsKnown", c.countsKnown);
            item.put("odCount", c.odCount); item.put("odCredit", c.odCredit);
            item.put("odReconciled", c.odReconciled);
            rows.put(item);
        }
        json.put("rows", rows);
        JSONArray timetable = new JSONArray();
        for (ScheduleItem t : s.timetable) {
            JSONObject item = new JSONObject();
            item.put("date", t.date); item.put("time", t.time);
            item.put("name", t.name); item.put("code", t.code);
            item.put("details", t.details); item.put("startsAt", t.startsAt);
            item.put("endsAt", t.endsAt); item.put("allDay", t.allDay);
            item.put("cancelled", t.cancelled);
            timetable.put(item);
        }
        json.put("timetable", timetable);
        JSONArray marks = new JSONArray();
        for (ClassMarkItem m : s.classMarks) {
            JSONObject item = new JSONObject();
            item.put("name", m.name); item.put("code", m.code);
            item.put("day", m.day); item.put("time", m.time);
            item.put("status", m.status); item.put("startsAt", m.startsAt);
            marks.put(item);
        }
        json.put("classMarks", marks);
        JSONArray leaves = new JSONArray();
        for (LeaveItem m : s.leaves) {
            JSONObject item = new JSONObject();
            item.put("id", m.id); item.put("type", m.type);
            item.put("date", m.date); item.put("status", m.status);
            item.put("details", m.details); item.put("periods", m.periods);
            item.put("currentSemester", m.currentSemester);
            item.put("startsAt", m.startsAt); item.put("endsAt", m.endsAt);
            leaves.put(item);
        }
        json.put("leaves", leaves);
        JSONArray grades = new JSONArray();
        for (AcademicCourse m : s.academicCourses) {
            JSONObject item = new JSONObject();
            item.put("semester", m.semester); item.put("name", m.name);
            item.put("code", m.code); item.put("grade", m.grade);
            item.put("result", m.result); item.put("credits", m.credits);
            grades.put(item);
        }
        json.put("academicCourses", grades);
        return json;
    }

    static String shortCourseName(String name) {
        if (name == null || name.trim().isEmpty()) return "Class";
        name = name.trim();
        if (name.indexOf(' ') < 0 && name.length() <= 20) return name;
        StringBuilder shortName = new StringBuilder();
        for (String part : name.toUpperCase(java.util.Locale.ROOT).split("[^A-Z0-9]+")) {
            if (part.isEmpty() || "AND".equals(part) || "OF".equals(part) || "THE".equals(part)) continue;
            shortName.append(part.charAt(0));
        }
        return shortName.length() == 0 ? name : shortName.toString();
    }

    private void navigate(String page) {
        currentPage = page;
        if (ready) web.evaluateJavascript("window.__rollcallNavigate(" + JSONObject.quote(page) + ");", null);
    }
    String getCurrentPage() { return currentPage; }
    void destroy() {
        ready = false;
        web.removeJavascriptInterface("RollcallBridge");
        web.stopLoading();
        web.loadUrl("about:blank");
        removeView(web);
        web.destroy();
    }
    void openSettings() { navigate("settings"); }
    void openWidgetPage(int index) {
        String[] pages = {"home", "courses", "week", "what", "marks"};
        navigate(index >= 0 && index < pages.length ? pages[index] : "home");
    }
    void openNotificationDestination(String destination) {
        switch (destination == null ? "" : destination) {
            case "leave_od": navigate("leave"); break;
            case "week": navigate("week"); break;
            case "courses": navigate("courses"); break;
            case "settings": navigate("settings"); break;
            case "marks": navigate("marks"); break;
            default: navigate("home"); break;
        }
    }
    boolean closeOdDetailsForBack() {
        if ("home".equals(currentPage)) return false;
        navigate("home");
        return true;
    }
    void restoreSelectedPage() { navigate(currentPage); }
    void animateCurrentPageEntry() { web.animate().alpha(1f).setDuration(120).start(); }
}
