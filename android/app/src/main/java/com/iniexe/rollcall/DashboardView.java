package com.iniexe.rollcall;

import com.iniexe.rollcall.ui.bridge.RollcallHeroIdentityView;
import com.iniexe.rollcall.ui.bridge.LatestClassAttendanceCardView;
import com.iniexe.rollcall.ui.bridge.CourseCardView;
import com.iniexe.rollcall.ui.bridge.UpcomingExamCardView;
import com.iniexe.rollcall.ui.screens.home.HomeHeroIdentityState;
import com.iniexe.rollcall.ui.screens.home.LatestClassAttendanceState;
import com.iniexe.rollcall.ui.screens.courses.CourseCardUiState;
import com.iniexe.rollcall.ui.screens.courses.CourseMetricUiState;
import com.iniexe.rollcall.ui.screens.home.UpcomingExamCardState;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ReplacementSpan;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.os.Handler;
import android.os.Looper;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Date;
import java.text.SimpleDateFormat;

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
        Set<String> approvedOdClasses = new HashSet<>();
        Set<String> currentSemesterClassKeys = new HashSet<>();
        Set<String> currentSemesterOdClasses = new HashSet<>();
    }

    static final class Course {
        String code = "", name = "", percentLabel = "", status = "", counts = "", guidance = "", targetCount = "";
        String canMissText = "—", mustAttendText = "—";
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
        String id = "", date = "", type = "Leave", status = "Unknown", details = "", periodNumbers = "", periodDetails = "[]";
        long startsAt, endsAt;
        int periods;
        int color;
        boolean currentSemester, semesterKnown;
    }

    static final class ClassMarkItem {
        String day = "", time = "", code = "", name = "", status = "", leaveType = "", leaveStatus = "";
        long startsAt;
        int color;
    }

    static final class AcademicCourse {
        String semester = "", code = "", name = "", grade = "", result = "";
        double credits;
    }

    private static final class GpaPoint {
        final String semester;
        final float value;
        GpaPoint(String semester, float value) { this.semester = semester; this.value = value; }
    }

    private static final class CourseGroup {
        final Course lecture;
        final ArrayList<Course> labs = new ArrayList<>();
        CourseGroup(Course lecture) { this.lecture = lecture; }
        float priority() {
            float value = lecture.percent;
            for (Course lab : labs) value = Math.min(value, lab.percent);
            return value;
        }
    }

    private final Actions actions;
    private final AppPalette palette;
    private final int canvas, ink, muted, surface, border, soft, green, red, amber, hero, heroText, heroMuted, lime;
    private final int track, focusSurface;
    private final int labBadgeBackground, labBadgeForeground;
    private final boolean cyberpunk;
    private final Typeface cyberpunkTypeface;
    private final FrameLayout pages = new FrameLayout(getContext());
    private final ScrollView[] pageScrolls = new ScrollView[5];
    private final LinearLayout[] pageBodies = new LinearLayout[5];
    private final LinearLayout[] courseLists = new LinearLayout[2];
    private final ArrayList<CourseRow> courseViews = new ArrayList<>();
    private final ArrayList<CourseGroupRow> courseGroupViews = new ArrayList<>();
    private final ArrayList<TextView> avatars = new ArrayList<>();
    private final HashSet<String> expandedScheduleDays = new HashSet<>();
    private boolean scheduleExpansionInitialized;
    private String pendingScheduleRevealDate = "";
    private SyncGlyphView dashboardSyncGlyph;
    private final Typeface uiTypeface;
    private final Typeface displayTypeface;
    private final LinearLayout nav = new LinearLayout(getContext());
    private NavBackgroundDrawable navBackground;
    private ValueAnimator navIndicatorAnimator;
    private final LinearLayout[] navTabs = new LinearLayout[5];
    private final ImageView[] navIcons = new ImageView[5];
    private AttendanceRail attendanceRail;
    private TextView overviewGoal, courseSummary, decisionCourse, decisionLabel, decisionAction, plannerBaseline;
    private String decisionCode = "";
    private ModeSwitchButtonView homeThemeButton;
    private final TextView[] navItems = new TextView[5];

    private TextView statusView, heroMessageView, captionView, targetHintView, rulerTargetLabel;
    private TextView allCoursesFilter, focusCoursesFilter;
    private TextView coursesEmptyView;
    private TextView attendancePercentView, onTrackView, overviewEmptyView, targetView;
    private TextView attendForecastView, skipForecastView, forecastStatusView;
    private LinearLayout attendForecastTile, skipForecastTile;
    private LinearLayout decisionFigures, decisionRulerLegend;
    private boolean attendForecastSelected = true, hasForecast, nextClassHasApprovedOd;
    private long decisionStartsAt = -1;
    private float attendForecast, skipForecast, currentCoursePercent;
    private final Handler classStartHandler = new Handler(Looper.getMainLooper());
    private final Runnable classStartRefresh = () -> { updateDecision(); updateOverviewSchedule(); };
    private TextView overviewLeaveSummary;
    private View overviewCourseHeader;
    private LinearLayout overviewScheduleBlock, overviewWeekBlock, overviewLeaveBlock, overviewExamSection;
    private UpcomingExamCardView overviewExamBlock;
    private LatestClassAttendanceCardView overviewAttendanceBlock;
    private LinearLayout overviewScheduleRows;
    private TextView overviewWeekMeta;
    private WeekPulseView overviewWeekPulse;
    private TextView projectionView, projectionStatusView, plannerCountView;
    private final java.util.Map<String, Float> previousCourseProgress = new java.util.HashMap<>();
    private RollcallHeroIdentityView homeHeroIdentity;
    private boolean wasSyncing;
    private PlannerMeter plannerMeter;
    private TextView plannerFeedback;
    private TextView scheduleUpdatedView, scheduleEmptyView, historyEmptyView;
    private LinearLayout scheduleEmptyPanel;
    private TextView scheduleEmptyTitle, scheduleEmptyDetail;
    private WeekPulseView weekPulse;
    private TextView weekPulseMeta;
    private LinearLayout scheduleRows, historyRows, scheduleContent, historyContent;
    private LinearLayout scheduleExtras, odOptions;
    private ScrollView odDetailsPage;
    private LinearLayout odDetailsRows;
    private TextView currentSemesterOdFilter, allOdFilter;
    private boolean odDetailsOpen;
    private int odDetailsReturnPage;
    private boolean showAllOd;
    private final Set<String> expandedOdRecords = new HashSet<>();
    private TextView odNotice;
    private final TextView[] odChips = new TextView[3];
    private int odMode;
    private TextView scheduleTab, historyTab;
    private boolean showingHistory;
    private int historyLimit = 30;
    private boolean scheduleDirty = true;
    private boolean historyDirty = true;
    private FrameLayout coursePicker;
    private TextView coursePickerText;
    private PopupWindow coursePopup;
    private EditText coursesSearch;
    private FrameLayout dashboardSyncButton;
    private State state = new State();
    private String query = "";
    private boolean belowOnly;
    private boolean plannerAttend;
    private TextView plannerMissView, plannerAttendView;
    private int plannerCount = 1, plannerCourse;
    private int selectedPage;
    private LinearLayout academicsContent;
    private boolean initialized;
    private int swipeTouchSlop;
    private float swipeDownX, swipeDownY;
    private boolean swipeAllowed, swipingPages;

    DashboardView(Context context, Actions actions, AppPalette palette) {
        super(context);
        swipeTouchSlop = android.view.ViewConfiguration.get(context).getScaledTouchSlop();
        Typeface loadedTypeface;
        try { loadedTypeface = Typeface.createFromAsset(context.getAssets(), "fonts/JetBrainsMono-Regular.ttf"); }
        catch (RuntimeException ignored) { loadedTypeface = Typeface.create("monospace", Typeface.NORMAL); }
        uiTypeface = loadedTypeface;
        cyberpunk = "Night City 2077".equals(palette.name);
        Typeface themeDisplayTypeface;
        if (cyberpunk) {
            themeDisplayTypeface = Typeface.create("monospace", Typeface.BOLD);
            try {
                themeDisplayTypeface = Typeface.createFromAsset(context.getAssets(), "fonts/Neuropol.otf");
            } catch (RuntimeException ignored) { /* Keep the readable system fallback. */ }
        } else {
            themeDisplayTypeface = Typeface.create("sans-serif", Typeface.BOLD);
            try {
                themeDisplayTypeface = Typeface.createFromAsset(context.getAssets(), "fonts/NovaRound-Bold.ttf");
            } catch (RuntimeException ignored) { /* Keep the readable system fallback. */ }
        }
        displayTypeface = cyberpunk ? themeDisplayTypeface : Typeface.create(uiTypeface, Typeface.BOLD);
        Typeface themeTypeface = Typeface.create("sans-serif-condensed", Typeface.BOLD);
        if (cyberpunk) {
            try { themeTypeface = Typeface.createFromAsset(context.getAssets(), "fonts/Cyberpunk-Regular.ttf"); }
            catch (RuntimeException ignored) { /* Keep the readable system fallback. */ }
        }
        cyberpunkTypeface = themeTypeface;
        this.actions = actions;
        this.palette = palette;
        canvas = palette.canvas; ink = palette.ink; muted = palette.muted;
        surface = palette.surface; border = palette.border; soft = palette.soft;
        green = palette.green; red = palette.red; amber = palette.amber;
        hero = palette.hero; heroText = palette.heroText; heroMuted = palette.heroMuted;
        lime = palette.highlight; track = palette.track;
        focusSurface = palette.focusSurface;
        labBadgeBackground = palette.button; labBadgeForeground = palette.buttonText;
        setBackgroundColor(canvas);
        build();
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            swipeDownX = event.getX();
            swipeDownY = event.getY();
            ScrollView page = selectedPage >= 0 && selectedPage < pageScrolls.length
                    ? pageScrolls[selectedPage] : null;
            swipeAllowed = !odDetailsOpen && page != null
                    && !startsOnHorizontalControl(page, event.getX() - pages.getLeft() - page.getLeft(),
                    event.getY() - pages.getTop() - page.getTop());
            swipingPages = false;
            return false;
        }
        if (swipingPages) return true;
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE && swipeAllowed) {
            float dx = event.getX() - swipeDownX, dy = event.getY() - swipeDownY;
            int nextPage = selectedPage + (dx < 0 ? 1 : -1);
            if (Math.abs(dx) > swipeTouchSlop && Math.abs(dx) > Math.abs(dy) * 1.25f
                    && Math.abs(dx) > dp(56) && nextPage >= 0 && nextPage < pageScrolls.length) {
                swipingPages = true;
                return true;
            }
        }
        if (event.getActionMasked() == MotionEvent.ACTION_CANCEL
                || event.getActionMasked() == MotionEvent.ACTION_UP) swipeAllowed = false;
        return false;
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (swipingPages && event.getActionMasked() == MotionEvent.ACTION_UP) {
            float dx = event.getX() - swipeDownX;
            if (Math.abs(dx) > dp(56)) showPage(selectedPage + (dx < 0 ? 1 : -1));
            swipingPages = false;
            swipeAllowed = false;
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            swipingPages = false;
            swipeAllowed = false;
        }
        return swipingPages || super.onTouchEvent(event);
    }

    void update(State next) {
        if (next == null) return;
        state = next;
        scheduleDirty = true;
        historyDirty = true;
        query = next.query == null ? query : next.query;
        belowOnly = next.belowOnly;
        if (homeHeroIdentity == null) return;
        String displayName = next.name == null ? "MyCamu student"
                : next.name.trim().replaceAll("\\s+", " ").replaceAll("\\s+([,.;:!?])", "$1")
                        .replaceAll("\\.$", "");
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String greeting = hour < 12 ? "Good morning" : hour < 17 ? "Good afternoon" : "Good evening";
        SimpleDateFormat broadcastDate = new SimpleDateFormat("EEE / dd MMM", Locale.ENGLISH);
        Calendar currentWeek = Calendar.getInstance();
        String encouragement = next.rows.isEmpty() ? "YOUR ACADEMIC WEEK, DECODED."
                : next.below == 0 ? "YOU’RE KEEPING UP. ONE CLASS AT A TIME."
                : "ONE CLASS AT A TIME. YOUR WEEK, DECODED.";
        homeHeroIdentity.setIdentity(new HomeHeroIdentityState(
                greeting + ",", displayName.toUpperCase(Locale.ROOT) + ".",
                broadcastDate.format(new Date()).toUpperCase(Locale.ROOT),
                String.format(Locale.ROOT, "%02d", currentWeek.get(Calendar.WEEK_OF_YEAR)),
                new SimpleDateFormat("MMMM", Locale.ENGLISH).format(new Date()).toUpperCase(Locale.ROOT),
                encouragement));
        String avatarLabel = next.initials == null || next.initials.isEmpty()
                ? "R." : next.initials.substring(0, 1) + ".";
        for (TextView avatar : avatars) avatar.setText(avatarLabel);
        if (homeThemeButton != null) homeThemeButton.setDark(next.dark);
        if (attendanceRail != null) attendanceRail.invalidate();
        if (overviewGoal != null) overviewGoal.setText(next.target + "% target  ↗");
        if (rulerTargetLabel != null) rulerTargetLabel.setText(next.target + "% target");
        if (courseSummary != null) {
            courseSummary.setVisibility(next.rows.isEmpty() ? GONE : VISIBLE);
            courseSummary.setText(next.courses + " courses  ·  " + next.below + " below target");
        }
        boolean completedSync = wasSyncing && !next.syncing && next.syncSucceeded && !next.syncError && !next.sessionExpired;
        wasSyncing = next.syncing;
        statusView.setText(next.syncing ? "Checking your latest attendance…" : next.status);
        if (completedSync && statusView.isShown()) {
            statusView.announceForAccessibility("Attendance updated. You’re ready to plan.");
            settleFeedback(statusView);
        }
        statusView.setTextColor(next.syncError ? 0xFF762B1E : next.syncing ? 0xFF704400 : heroMuted);
        statusView.setContentDescription(next.syncError ? "Attendance refresh failed. " + next.status : next.status);
        statusView.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        statusView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        heroMessageView.setText(next.heroMessage);
        captionView.setText("classes attended");
        targetHintView.setText(next.targetHint);
        updateDecision();
        onTrackView.setText(next.onTrack + " of " + next.courses + " courses on target");
        overviewCourseHeader.setVisibility(next.below > 0 ? VISIBLE : GONE);
        courseLists[0].setVisibility(next.below > 0 ? VISIBLE : GONE);
        overviewEmptyView.setVisibility(next.below > 0 ? GONE : VISIBLE);
        overviewEmptyView.setTextColor(next.rows.isEmpty() ? muted : green);
        overviewEmptyView.setText(next.rows.isEmpty() ? "Course attendance appears after your first sync."
                : "You’re on track in all " + next.courses + " courses. Keep your " + next.target + "% target in sight.");
        int pendingLeaves = 0, approvedLeaves = 0;
        for (LeaveItem leave : next.leaves) {
            String leaveStatus = leave.status.toLowerCase(Locale.ROOT);
            if (leaveStatus.contains("pending") || leaveStatus.contains("submit")) pendingLeaves++;
            if (leaveStatus.contains("approv")) approvedLeaves++;
        }
        overviewLeaveBlock.setVisibility(VISIBLE);
        overviewLeaveSummary.setText(next.leavesLoaded
                ? approvedLeaves + " approved  ·  " + pendingLeaves + " awaiting review"
                : "View your leave requests and on-duty records");
        boolean hasClassStatus = !next.latestClassName.isEmpty() && !next.latestClassStatus.isEmpty();
        overviewAttendanceBlock.setVisibility(hasClassStatus ? VISIBLE : GONE);
        if (hasClassStatus) {
            String statusLabel = "present".equals(next.latestClassStatus) ? "Present"
                    : "absent".equals(next.latestClassStatus) ? "Absent"
                    : "not_taken".equals(next.latestClassStatus) ? "Not marked yet" : "Unable to confirm";
            overviewAttendanceBlock.setAttendanceState(new LatestClassAttendanceState(
                    next.latestClassName, statusLabel, next.latestClassStatus,
                    "Latest class attendance. " + next.latestClassName + ": " + statusLabel + ". Open attendance history"));
        }
        updateSyncIndicator(next.syncing, next.syncError || next.sessionExpired || next.partialSync, next.syncSucceeded);
        updateSearchFields();
        rebuildCourseRows();
        updatePlannerCourses();
        refreshVisibleRows();
        if (selectedPage == 2) updateTimetable();
        updateOverviewSchedule();
        updateOverviewExam(next);
        updateOdDetails();
        updateAcademics(next);
    }

    private void updateSyncIndicator(boolean syncing, boolean failed, boolean succeeded) {
        if (dashboardSyncButton == null) return;
        dashboardSyncButton.setContentDescription(state.sessionExpired ? "MyCamu sign-in expired. Tap to reconnect"
                : failed ? "Sync failed. Tap to refresh attendance"
                : syncing ? "Refreshing attendance from MyCamu"
                : succeeded ? "Sync complete"
                : "Refresh attendance from MyCamu");
        dashboardSyncButton.setOnClickListener(v -> {
            if (state.sessionExpired) actions.onPortal();
            else actions.onSync();
        });
        dashboardSyncGlyph.show(syncing, failed, succeeded);
    }

    private void build() {
        LinearLayout shell = new LinearLayout(getContext());
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(canvas);
        addView(shell, new LayoutParams(-1, -1));

        LinearLayout header = column();
        header.setPadding(dp(20), dp(12), dp(20), 0);
        header.addView(topBar(true), lp(-1, dp(48)));
        shell.addView(header, new LinearLayout.LayoutParams(-1, -2));
        pages.setBackgroundColor(canvas);
        shell.addView(pages, new LinearLayout.LayoutParams(-1, 0, 1));
        buildOverview();
        buildCoursesPage();
        buildTimetablePage();
        buildLabPage();
        buildAcademicsPage();
        for (int i = 0; i < pageScrolls.length; i++) {
            pageScrolls[i].setVisibility(i == 0 ? VISIBLE : GONE);
            pages.addView(pageScrolls[i], new FrameLayout.LayoutParams(-1, -1));
        }
        buildOdDetailsPage();
        buildNavigation(shell);
    }

    private void buildOverview() {
        LinearLayout body = newPage(0);
        LinearLayout heroCard = column();
        heroCard.setPadding(dp(16), dp(14), dp(16), dp(14));
        heroCard.setBackground(shape(hero, 18, 0));
        homeHeroIdentity = new RollcallHeroIdentityView(getContext(), palette.toComposeColors());
        heroCard.addView(homeHeroIdentity, lp(-1, -2));

        LinearLayout syncLine = row();
        syncLine.setGravity(Gravity.CENTER_VERTICAL);
        statusView = text("", 10, heroMuted, Typeface.NORMAL);
        syncLine.addView(statusView, new LinearLayout.LayoutParams(0, -2, 1));
        dashboardSyncButton = new FrameLayout(getContext());
        dashboardSyncButton.setBackground(shape(0x223F4B16, 100, 0x663F4B16));
        dashboardSyncButton.setClickable(true);
        dashboardSyncButton.setFocusable(true);
        dashboardSyncGlyph = new SyncGlyphView(getContext());
        dashboardSyncButton.addView(dashboardSyncGlyph,
                new FrameLayout.LayoutParams(-1, -1));
        dashboardSyncButton.setContentDescription("Refresh attendance from MyCamu");
        dashboardSyncButton.setOnClickListener(v -> actions.onSync());
        syncLine.addView(dashboardSyncButton, lp(dp(48), dp(48)));
        heroCard.addView(syncLine, topLp(-1, -2, 8));
        addTop(body, heroCard, 10);
        courseLists[0] = column();
        LinearLayout summary = column();
        summary.setPadding(dp(16), dp(16), dp(16), dp(16));
        summary.setBackground(industrialSurface(surface, 20, border));
        LinearLayout summaryHeader = row();
        summaryHeader.addView(label("NEXT CLASS", 11, lime, Typeface.BOLD),
                new LinearLayout.LayoutParams(0, -2, 1));
        overviewGoal = text("75% target  ↗", 11, lime, Typeface.BOLD);
        overviewGoal.setMinHeight(dp(48));
        overviewGoal.setGravity(Gravity.CENTER_VERTICAL);
        overviewGoal.setOnClickListener(v -> actions.onTarget());
        summaryHeader.addView(overviewGoal);
        summary.addView(summaryHeader);
        decisionCourse = text("Choose a course to begin", 16, ink, Typeface.BOLD);
        decisionCourse.setMaxLines(2);
        decisionCourse.setEllipsize(android.text.TextUtils.TruncateAt.END);
        summary.addView(decisionCourse, topLp(-1, -2, 4));
        decisionLabel = text("", 12, muted, Typeface.NORMAL);
        decisionLabel.setMaxLines(2);
        summary.addView(decisionLabel, topLp(-1, -2, 8));
        LinearLayout figures = row();
        figures.setGravity(Gravity.CENTER_VERTICAL);
        decisionFigures = figures;
        LinearLayout current = column();
        current.setGravity(Gravity.CENTER_VERTICAL);
        current.addView(label("CURRENT", 10, muted, Typeface.BOLD));
        attendancePercentView = text("—", 34, ink, Typeface.BOLD);
        attendancePercentView.setLetterSpacing(-.055f);
        attendancePercentView.setIncludeFontPadding(false);
        current.addView(attendancePercentView, topLp(-2, -2, 1));
        heroMessageView = text("— / — attended", 11, muted, Typeface.NORMAL);
        captionView = text("this course", 9, muted, Typeface.NORMAL);
        current.addView(heroMessageView, topLp(-2, -2, 1));
        captionView.setVisibility(GONE);
        figures.addView(current, new LinearLayout.LayoutParams(0, dp(76), 1));
        attendForecastTile = forecastTile(figures, "ATTEND");
        attendForecastView = (TextView) attendForecastTile.getChildAt(1);
        skipForecastTile = forecastTile(figures, "SKIP");
        skipForecastView = (TextView) skipForecastTile.getChildAt(1);
        attendForecastTile.setOnClickListener(v -> selectForecast(true));
        skipForecastTile.setOnClickListener(v -> selectForecast(false));
        summary.addView(figures, topLp(-1, -2, 8));
        forecastStatusView = text("", 11, muted, Typeface.BOLD);
        summary.addView(forecastStatusView, topLp(-1, -2, 8));
        attendanceRail = new AttendanceRail(getContext());
        summary.addView(attendanceRail, topLp(-1, dp(20), 7));
        LinearLayout rulerLegend = row();
        decisionRulerLegend = rulerLegend;
        TextView zero = text("0", 10, muted, Typeface.NORMAL);
        TextView half = text("50%", 10, muted, Typeface.NORMAL);
        rulerTargetLabel = text("75% target", 10, lime, Typeface.NORMAL);
        zero.setGravity(Gravity.LEFT);
        half.setGravity(Gravity.CENTER);
        rulerTargetLabel.setGravity(Gravity.RIGHT);
        rulerLegend.addView(zero, new LinearLayout.LayoutParams(0, -2, 1));
        rulerLegend.addView(half, new LinearLayout.LayoutParams(0, -2, 1));
        rulerLegend.addView(rulerTargetLabel, new LinearLayout.LayoutParams(0, -2, 1));
        summary.addView(rulerLegend, topLp(-1, -2, 1));
        targetHintView = text("", 12, muted, Typeface.NORMAL);
        summary.addView(targetHintView, topLp(-1, -2, 8));
        onTrackView = text("", 11, muted, Typeface.NORMAL);
        onTrackView.setVisibility(GONE);
        summary.addView(onTrackView, topLp(-1, -2, 6));
        decisionAction = text("Plan this course  →", 13, labBadgeForeground, Typeface.BOLD);
        decisionAction.setGravity(Gravity.CENTER);
        decisionAction.setMinHeight(dp(56));
        decisionAction.setBackground(shape(labBadgeBackground, 12, 0));
        decisionAction.setOnClickListener(v -> {
            if (state.sessionExpired) actions.onPortal();
            else actions.onSync();
        });
        setTactileFeedback(decisionAction);
        summary.addView(decisionAction, topLp(-1, -2, 14));
        addTop(body, summary, 12);

        overviewExamSection = column();
        LinearLayout radarHeading = row();
        radarHeading.setGravity(Gravity.CENTER_VERTICAL);
        TextView radarTitle = text("On your radar", 17, ink, Typeface.BOLD);
        radarTitle.setTypeface(displayTypeface, Typeface.BOLD);
        radarHeading.addView(radarTitle, new LinearLayout.LayoutParams(0, -2, 1));
        radarHeading.addView(label("01 / EXAM", 9, palette.highlight, Typeface.BOLD));
        overviewExamSection.addView(radarHeading);
        overviewExamBlock = new UpcomingExamCardView(getContext(), palette.toComposeColors());
        overviewExamSection.addView(overviewExamBlock, topLp(-1, -2, 8));
        addTop(body, overviewExamSection, 14);

        overviewScheduleBlock = column();
        overviewScheduleBlock.addView(overviewSectionHeader("Next up", "Week", () -> showPage(2)));
        overviewScheduleRows = column();
        overviewScheduleBlock.addView(overviewScheduleRows, lp(-1, -2));
        overviewScheduleBlock.setVisibility(GONE);
        addTop(body, overviewScheduleBlock, 18);

        overviewWeekBlock = column();
        overviewWeekBlock.addView(overviewSectionHeader("This week", "Full week", () -> showPage(2)));
        overviewWeekMeta = text("Sync MyCamu to map your timetable.", 12, muted, Typeface.NORMAL);
        overviewWeekBlock.addView(overviewWeekMeta, topLp(-1, -2, 2));
        overviewWeekPulse = new WeekPulseView(getContext());
        overviewWeekPulse.setContentDescription("Weekly timetable preview. Open the full week schedule");
        overviewWeekPulse.setOnClickListener(v -> showPage(2));
        overviewWeekBlock.addView(overviewWeekPulse, topLp(-1, dp(64), 2));
        overviewWeekBlock.setVisibility(GONE);
        addTop(body, overviewWeekBlock, 18);

        overviewAttendanceBlock = new LatestClassAttendanceCardView(getContext(), palette.toComposeColors(),
                () -> { showingHistory = true; showPage(2); pageScrolls[2].scrollTo(0, 0); });
        overviewAttendanceBlock.setVisibility(GONE);
        addTop(body, overviewAttendanceBlock, 10);

        overviewCourseHeader = overviewSectionHeader("Below target", "All courses", () -> showPage(1));
        addTop(body, overviewCourseHeader, 18);
        body.addView(courseLists[0], lp(-1, -2));
        overviewEmptyView = text("", 12, green, Typeface.BOLD);
        addTop(body, overviewEmptyView, 6);

        overviewLeaveBlock = column();
        overviewLeaveBlock.setPadding(dp(14), dp(12), dp(14), dp(12));
        overviewLeaveBlock.setBackground(shape(surface, 14, border));
        overviewLeaveBlock.addView(overviewSectionHeader("Leave & OD", "Open", this::openOdDetails));
        overviewLeaveSummary = text("", 12, muted, Typeface.NORMAL);
        overviewLeaveSummary.setPadding(0, dp(5), 0, 0);
        overviewLeaveBlock.addView(overviewLeaveSummary);
        overviewLeaveBlock.setOnClickListener(v -> openOdDetails());
        overviewLeaveBlock.setVisibility(VISIBLE);
        addTop(body, overviewLeaveBlock, 14);
        addBottom(body, 24);
    }

    private void buildCoursesPage() {
        LinearLayout body = newPage(1);
        addTop(body, heading("02 / YOUR PROGRESS", "Courses", "Attendance, stripped down to what matters. Can miss. Must attend. No guessing."), 18);
        courseSummary = text("", 12, muted, Typeface.NORMAL);
        addTop(body, courseSummary, 0);
        coursesSearch = searchField("Search by subject or code");
        addTop(body, coursesSearch, 8);
        addTop(body, filters(), 6);
        courseLists[1] = column();
        body.addView(courseLists[1], lp(-1, -2));
        coursesEmptyView = text("", 13, muted, Typeface.NORMAL);
        coursesEmptyView.setPadding(dp(14), dp(14), dp(14), dp(14));
        coursesEmptyView.setMinHeight(dp(56));
        coursesEmptyView.setBackground(shape(soft, 12, 0));
        coursesEmptyView.setVisibility(GONE);
        coursesEmptyView.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        addTop(body, coursesEmptyView, 10);
        addBottom(body, 24);
    }

    private void buildLabPage() {
        LinearLayout body = newPage(3);
        addTop(body, heading("04 / ATTENDANCE LAB", "What if?", "See how attending or missing classes changes your target."), 8);
        addTop(body, plannerCard(), 2);
        addTop(body, targetCard(), 12);
        addTop(body, text("This is an estimate based on your latest MyCamu counts. It does not change your attendance record.",
                11, muted, Typeface.NORMAL), 12);
        addBottom(body, 24);
    }

    private void buildAcademicsPage() {
        LinearLayout body = newPage(4);
        addTop(body, heading("05 / ACADEMICS", "The numbers", "Your marks and academic story, at a glance."), 18);
        academicsContent = column();
        body.addView(academicsContent, lp(-1, -2));
        addBottom(body, 24);
    }

    private void updateAcademics(State next) {
        if (academicsContent == null) return;
        academicsContent.removeAllViews();
        if (next.academicCourses == null || next.academicCourses.isEmpty()) {
            LinearLayout empty = column();
            empty.setPadding(dp(16), dp(16), dp(16), dp(16));
            empty.setBackground(industrialSurface(surface, 18, border));
            empty.addView(text("Your results will appear here", 18, ink, Typeface.BOLD));
            String guidance = next.academicConnected
                    ? "KCT is connected. No completed results have been returned yet; Rollcall will keep the information on this device."
                    : "Sign in to KCT once. Rollcall will collect your semester results and next exam automatically on this device.";
            TextView detail = text(guidance,
                    13, muted, Typeface.NORMAL);
            detail.setLineSpacing(dp(2), 1f);
            empty.addView(detail, topLp(-1, -2, 8));
            TextView open = text("Sign in to KCT  →", 14, palette.buttonText, Typeface.BOLD);
            open.setGravity(Gravity.CENTER);
            open.setMinHeight(dp(50));
            open.setBackground(shape(palette.button, 12, 0));
            open.setOnClickListener(v -> actions.onAcademicsPortal());
            setTactileFeedback(open);
            empty.addView(open, topLp(-1, -2, 14));
            academicsContent.addView(empty, lp(-1, -2));
            return;
        }

        ArrayList<AcademicCourse> eligible = new ArrayList<>();
        java.util.LinkedHashMap<String, ArrayList<AcademicCourse>> grouped = new java.util.LinkedHashMap<>();
        ArrayList<String> semesters = new ArrayList<>();
        for (AcademicCourse course : next.academicCourses) {
            if (course == null || course.semester.isEmpty()) continue;
            ArrayList<AcademicCourse> courses = grouped.get(course.semester);
            if (courses == null) {
                courses = new ArrayList<>();
                grouped.put(course.semester, courses);
                semesters.add(course.semester);
            }
            courses.add(course);
            if (gradePoint(course.grade) >= 0 && course.credits > 0) eligible.add(course);
        }
        Collections.sort(semesters, (a, b) -> Integer.compare(semesterNumber(b), semesterNumber(a)));
        double weighted = 0, creditTotal = 0;
        for (AcademicCourse course : eligible) {
            weighted += course.credits * gradePoint(course.grade);
            creditTotal += course.credits;
        }
        double cgpa = creditTotal == 0 ? Double.NaN : weighted / creditTotal;

        LinearLayout summary = column();
        summary.setPadding(dp(18), dp(16), dp(18), dp(16));
        summary.setBackground(shape(palette.hero, 18, 0));
        summary.addView(label("CGPA", 10, palette.heroMuted, Typeface.BOLD));
        TextView value = text(Double.isNaN(cgpa) ? "—" : String.format(Locale.getDefault(), "%.2f", cgpa) + " / 10",
                42, palette.heroText, Typeface.BOLD);
        value.setLetterSpacing(-.04f);
        summary.addView(value, topLp(-1, -2, 2));
        double earnedCredits = 0;
        for (AcademicCourse course : eligible)
            if (gradePoint(course.grade) >= 5) earnedCredits += course.credits;
        summary.addView(text(String.format(Locale.getDefault(), "%.1f credits counted · %.1f earned · %d semesters",
                creditTotal, earnedCredits, semesters.size()),
                13, palette.heroMuted, Typeface.NORMAL), topLp(-1, -2, 1));
        academicsContent.addView(summary);
        TextView method = text("Credit-weighted from graded courses shown in KCT. Audit and non-credit results are excluded.",
                12, muted, Typeface.NORMAL);
        method.setLineSpacing(dp(2), 1f);
        addTop(academicsContent, method, 10);
        TextView sync = text("Refresh from KCT portal  →", 13, palette.highlight, Typeface.BOLD);
        sync.setGravity(Gravity.CENTER_VERTICAL);
        sync.setMinHeight(dp(48));
        sync.setOnClickListener(v -> actions.onAcademicsPortal());
        setTactileFeedback(sync);
        addTop(academicsContent, sync, 2);

        ArrayList<GpaPoint> trend = new ArrayList<>();
        for (String semester : semesters) {
            double termWeighted = 0, termCredits = 0;
            for (AcademicCourse course : grouped.get(semester)) {
                int point = gradePoint(course.grade);
                if (point < 0 || course.credits <= 0) continue;
                termWeighted += course.credits * point;
                termCredits += course.credits;
            }
            if (termCredits > 0) trend.add(new GpaPoint(semester, (float) (termWeighted / termCredits)));
        }
        Collections.sort(trend, (a, b) -> Integer.compare(semesterNumber(a.semester), semesterNumber(b.semester)));
        if (!trend.isEmpty()) {
            addTop(academicsContent, text("Semester GPA trend", 18, ink, Typeface.BOLD), 20);
            SemesterGpaChart chart = new SemesterGpaChart(getContext(), palette.highlight, muted, uiTypeface);
            chart.setPoints(trend);
            academicsContent.addView(chart, topLp(-1, dp(168), 4));
        }

        addTop(academicsContent, text("Semester results", 20, ink, Typeface.BOLD), 20);
        for (String semester : semesters) {
            ArrayList<AcademicCourse> courses = grouped.get(semester);
            if (courses == null || courses.isEmpty()) continue;
            double termWeighted = 0, termCredits = 0;
            for (AcademicCourse course : courses) {
                int point = gradePoint(course.grade);
                if (point < 0 || course.credits <= 0) continue;
                termWeighted += course.credits * point;
                termCredits += course.credits;
            }
            double sgpa = termCredits == 0 ? Double.NaN : termWeighted / termCredits;
            LinearLayout section = column();
            section.setPadding(0, dp(12), 0, dp(6));
            LinearLayout title = row();
            title.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout titleText = column();
            titleText.addView(text(semesterTitle(semester) + " · SGPA", 16, ink, Typeface.BOLD));
            titleText.addView(text(String.format(Locale.getDefault(), "%d course records · %.1f GPA credits",
                    courses.size(), termCredits), 12, muted, Typeface.NORMAL), topLp(-1, -2, 2));
            title.addView(titleText, new LinearLayout.LayoutParams(0, -2, 1));
            TextView gpa = text(Double.isNaN(sgpa) ? "—" : String.format(Locale.getDefault(), "%.2f", sgpa),
                    22, palette.highlight, Typeface.BOLD);
            gpa.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            title.addView(gpa, lp(-2, -2));
            section.addView(title);
            LinearLayout courseRows = column();
            courseRows.setVisibility(semesters.indexOf(semester) == 0 ? VISIBLE : GONE);
            for (AcademicCourse course : courses) {
                LinearLayout line = row();
                line.setGravity(Gravity.CENTER_VERTICAL);
                line.setPadding(0, dp(10), 0, dp(10));
                LinearLayout name = column();
                TextView courseTitle = text(course.name.isEmpty() ? course.code : course.name, 16, ink, Typeface.BOLD);
                courseTitle.setMaxLines(2);
                courseTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
                name.addView(courseTitle);
                String details = course.code.isEmpty() || course.name.equals(course.code) ? "" : course.code;
                if (course.credits > 0) details += (details.isEmpty() ? "" : " · ")
                        + String.format(Locale.getDefault(), "%.1f cr", course.credits);
                if (!course.result.isEmpty()) details += (details.isEmpty() ? "" : " · ") + course.result;
                if (!details.isEmpty()) name.addView(text(details, 11, muted, Typeface.NORMAL), topLp(-1, -2, 2));
                line.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
                TextView grade = text(course.grade, 13, ink, Typeface.BOLD);
                grade.setGravity(Gravity.CENTER);
                grade.setIncludeFontPadding(false);
                grade.setBackground(shape(soft, 8, 0));
                line.addView(grade, lp(dp(44), dp(32)));
                courseRows.addView(line);
                View divider = new View(getContext());
                divider.setBackgroundColor(border);
                courseRows.addView(divider, lp(-1, dp(1)));
            }
            section.addView(courseRows, topLp(-1, -2, 4));
            section.setFocusable(true);
            section.setContentDescription(semesterTitle(semester) + ", SGPA "
                    + (Double.isNaN(sgpa) ? "not available" : String.format(Locale.getDefault(), "%.2f", sgpa))
                    + ". Double tap to show or hide course grades.");
            section.setOnClickListener(v -> courseRows.setVisibility(courseRows.getVisibility() == VISIBLE ? GONE : VISIBLE));
            View divider = new View(getContext());
            divider.setBackgroundColor(border);
            section.addView(divider, topLp(-1, dp(1), 3));
            academicsContent.addView(section, topLp(-1, -2, 4));
        }
        if (next.academicUpdatedAt > 0) {
            TextView updated = text("Last read from KCT " + relativeAge(next.academicUpdatedAt), 11, muted, Typeface.NORMAL);
            addTop(academicsContent, updated, 10);
        }
        if (!next.academicStatus.isEmpty()) addTop(academicsContent,
                text(next.academicStatus, 12, muted, Typeface.NORMAL), 4);
    }

    private static int gradePoint(String grade) {
        if (grade == null) return -1;
        switch (grade.trim().toUpperCase(Locale.ROOT)) {
            case "O": case "S": return 10;
            case "A+": return 9;
            case "A": return 8;
            case "B+": return 7;
            case "B": return 6;
            case "C": return 5;
            case "U": case "SA": case "WD": return 0;
            default: return -1;
        }
    }

    private static int semesterNumber(String value) {
        java.util.regex.Matcher match = java.util.regex.Pattern.compile("(?i)semester\\s*[-:]?\\s*(\\d+)").matcher(value);
        if (!match.find()) return 0;
        int term = Integer.parseInt(match.group(1));
        java.util.regex.Matcher year = java.util.regex.Pattern.compile("20\\d{2}").matcher(value);
        return year.find() ? Integer.parseInt(year.group()) * 10 + term : term;
    }

    private static String semesterTitle(String value) {
        java.util.regex.Matcher match = java.util.regex.Pattern.compile("(?i)semester\\s*[-:]?\\s*\\d+").matcher(value);
        return match.find() ? match.group().replaceAll("\\s+", " ") : value;
    }

    private static String semesterShortLabel(String value) {
        java.util.regex.Matcher match = java.util.regex.Pattern.compile("(?i)semester\\s*[-:]?\\s*(\\d+)").matcher(value);
        return match.find() ? "S" + match.group(1) : "Sem";
    }

    private void buildTimetablePage() {
        LinearLayout body = newPage(2);
        addTop(body, heading("03 / TIME & ATTENDANCE", "Your week", "Every class. Every record. In one place."), 18);
        scheduleExtras = column();
        LinearLayout pulseCard = column();
        pulseCard.setPadding(0, dp(16), 0, dp(8));
        LinearLayout pulseHeading = row();
        pulseHeading.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout pulseCopy = column();
        pulseCopy.addView(label(cyberpunk ? "CITY PULSE · CLASSES" : "WEEK RHYTHM", 10, green, Typeface.BOLD));
        weekPulseMeta = text("Sync MyCamu to map your timetable.", 10, muted, Typeface.NORMAL);
        pulseCopy.addView(weekPulseMeta, topLp(-1, -2, 3));
        pulseHeading.addView(pulseCopy, new LinearLayout.LayoutParams(0, -2, 1));
        
        pulseCard.addView(pulseHeading);
        weekPulse = new WeekPulseView(getContext());
        pulseCard.addView(weekPulse, topLp(-1, dp(64), 5));
        addTop(scheduleExtras, pulseCard, 2);
        scheduleUpdatedView = text("Waiting for MyCamu timetable", 11, muted, Typeface.NORMAL);

        LinearLayout selector = row();
        selector.setPadding(dp(3), dp(3), dp(3), dp(3));
        selector.setBackground(shape(soft, 13, border));
        scheduleTab = text("Schedule", 12, ink, Typeface.BOLD);
        historyTab = text("Attendance", 12, muted, Typeface.BOLD);
        scheduleTab.setGravity(Gravity.CENTER);
        historyTab.setGravity(Gravity.CENTER);
        scheduleTab.setMinHeight(dp(48));
        historyTab.setMinHeight(dp(48));
        scheduleTab.setContentDescription("Show weekly class schedule");
        historyTab.setContentDescription("Show attendance history");
        scheduleTab.setOnClickListener(v -> { showingHistory = false; updateTimetable(); });
        historyTab.setOnClickListener(v -> { showingHistory = true; updateTimetable(); });
        selector.addView(scheduleTab, new LinearLayout.LayoutParams(0, -2, 1));
        selector.addView(historyTab, new LinearLayout.LayoutParams(0, -2, 1));
        addTop(body, selector, 10);
        addTop(body, scheduleExtras, 10);
        addTop(body, scheduleUpdatedView, 8);

        scheduleContent = column();
        body.addView(scheduleContent, topLp(-1, -2, 10));
        LinearLayout weekHeading = row();
        weekHeading.setGravity(Gravity.CENTER_VERTICAL);
        weekHeading.addView(text("This week", 16, ink, Typeface.BOLD), new LinearLayout.LayoutParams(0, -2, 1));
        scheduleContent.addView(weekHeading);
        scheduleRows = column();
        scheduleContent.addView(scheduleRows, topLp(-1, -2, 24));
        scheduleEmptyPanel = column();
        scheduleEmptyPanel.setPadding(dp(18), dp(17), dp(18), dp(14));
        scheduleEmptyPanel.setMinimumHeight(dp(205));
        scheduleEmptyPanel.setBackground(shape(0xFFC8C8FF, 16, 0xFF121212));
        scheduleEmptyPanel.addView(label("THE WEEK / LIVE SCHEDULE", 9, 0xFF343A26, Typeface.BOLD));
        scheduleEmptyTitle = text("NO CLASSES\nON YOUR\nSCHEDULE.", 22, 0xFF121212, Typeface.BOLD);
        scheduleEmptyTitle.setLineSpacing(0f, .92f);
        scheduleEmptyPanel.addView(scheduleEmptyTitle, topLp(-1, -2, 12));
        scheduleEmptyDetail = text("Your connected timetable will appear here when classes are scheduled.", 11, 0xFF343A26, Typeface.NORMAL);
        scheduleEmptyDetail.setMaxLines(2);
        scheduleEmptyDetail.setLineSpacing(dp(2), 1f);
        scheduleEmptyPanel.addView(scheduleEmptyDetail, topLp(-1, -2, 8));
        scheduleEmptyView = text("Refresh timetable  →", 11, 0xFF121212, Typeface.BOLD);
        scheduleEmptyView.setGravity(Gravity.CENTER);
        scheduleEmptyView.setMinHeight(dp(44));
        scheduleEmptyView.setBackground(shape(0xFFCCFF00, 9, 0));
        scheduleEmptyPanel.addView(scheduleEmptyView, topLp(-1, -2, 10));
        scheduleEmptyView.setFocusable(true);
        scheduleEmptyView.setClickable(true);
        scheduleEmptyView.setOnClickListener(v -> {
            if (state.sessionExpired) actions.onPortal(); else actions.onSync();
        });
        scheduleEmptyView.setContentDescription("Sync timetable from MyCamu");
        scheduleEmptyPanel.setFocusable(true);
        scheduleEmptyPanel.setOnClickListener(v -> {
            if (state.sessionExpired) actions.onPortal(); else actions.onSync();
        });
        addTop(scheduleContent, scheduleEmptyPanel, 14);

        historyContent = column();
        historyContent.setVisibility(GONE);
        body.addView(historyContent, topLp(-1, -2, 10));
        LinearLayout historyHeading = row();
        historyHeading.setGravity(Gravity.CENTER_VERTICAL);
        historyHeading.addView(text("Attendance history", 16, ink, Typeface.BOLD),
                new LinearLayout.LayoutParams(0, -2, 1));
        TextView leaveDetails = text("Leave & OD  →", 11, green, Typeface.BOLD);
        leaveDetails.setGravity(Gravity.CENTER);
        leaveDetails.setMinHeight(dp(44));
        leaveDetails.setPadding(dp(8), 0, 0, 0);
        leaveDetails.setOnClickListener(v -> openOdDetails());
        historyHeading.addView(leaveDetails, lp(-2, dp(44)));
        historyContent.addView(historyHeading);
        historyRows = column();
        historyContent.addView(historyRows, topLp(-1, -2, 6));
        historyEmptyView = text("Go to Home and tap refresh to load attendance history.", 12, muted, Typeface.NORMAL);
        historyEmptyView.setPadding(dp(12), dp(12), dp(12), dp(12));
        historyEmptyView.setMinHeight(dp(48));
        historyEmptyView.setTextColor(lime);
        historyEmptyView.setTypeface(uiTypeface, Typeface.BOLD);
        historyEmptyView.setBackground(shape(soft, 14, 0));
        historyEmptyView.setFocusable(true);
        historyEmptyView.setClickable(true);
        historyEmptyView.setOnClickListener(v -> {
            if (state.sessionExpired) actions.onPortal(); else actions.onSync();
        });
        historyEmptyView.setContentDescription("Sync attendance history from MyCamu");
        addTop(historyContent, historyEmptyView, 4);
        addBottom(body, 24);
    }

    private View legendItem(String title, int color) {
        LinearLayout item = row();
        item.setGravity(Gravity.CENTER_VERTICAL);
        View dot = new View(getContext());
        dot.setBackground(shape(color, 100, 0));
        item.addView(dot, lp(dp(7), dp(7)));
        TextView caption = text(title, 9, muted, Typeface.NORMAL);
        caption.setPadding(dp(4), 0, 0, 0);
        item.addView(caption);
        return item;
    }

    private View overviewSectionHeader(String title, String action, Runnable onAction) {
        LinearLayout header = row();
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading = text(cyberpunk ? title.toUpperCase(Locale.ROOT) : title, 17, ink, Typeface.BOLD);
        heading.setTypeface(displayTypeface);
        heading.setLetterSpacing(cyberpunk ? .04f : -.02f);
        header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        TextView button = text(action + "  →", 11, green, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setMinWidth(dp(48));
        button.setMinHeight(dp(48));
        button.setPadding(dp(8), 0, 0, 0);
        button.setContentDescription(action);
        button.setOnClickListener(v -> onAction.run());
        header.addView(button, lp(-2, dp(48)));
        return header;
    }

    private void updateOverviewSchedule() {
        if (overviewScheduleRows == null || overviewScheduleBlock == null) return;
        overviewScheduleRows.removeAllViews();
        ScheduleItem nextItem = nextScheduledClass(System.currentTimeMillis());
        overviewScheduleBlock.setVisibility(nextItem == null ? GONE : VISIBLE);
        overviewWeekBlock.setVisibility(VISIBLE);
        int total = overviewWeekPulse.setSchedule(state.timetable);
        int campusDays = 0;
        for (int count : overviewWeekPulse.counts) if (count > 0) campusDays++;
        String todayLabel = new SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(new Date());
        overviewWeekMeta.setText(!state.timetableLoaded ? "Today · " + todayLabel + " · timetable updates after sync."
                : state.timetable.isEmpty() ? "Today · " + todayLabel + " · no classes scheduled."
                : total == 0 ? "Today · " + todayLabel + " · no class periods mapped this week."
                : "Today · " + todayLabel + " · " + total + (total == 1 ? " class" : " classes")
                + " across " + campusDays + (campusDays == 1 ? " day" : " days"));
        if (nextItem == null) {
            return;
        }

        Calendar today = Calendar.getInstance();
        Calendar classDay = Calendar.getInstance();
        classDay.setTimeInMillis(nextItem.startsAt);
        boolean sameDay = today.get(Calendar.YEAR) == classDay.get(Calendar.YEAR)
                && today.get(Calendar.DAY_OF_YEAR) == classDay.get(Calendar.DAY_OF_YEAR);
        String when = sameDay ? "TODAY" : nextItem.date;
        String time = nextItem.time;
        int separator = time.indexOf(" – ");
        if (separator >= 0) time = time.substring(0, separator);

        LinearLayout line = row();
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setPadding(dp(16), dp(16), dp(16), dp(16));
        line.setBackground(industrialSurface(surface, 20, border));
        LinearLayout timeColumn = column();
        TextView whenView = text(when, 11, muted, Typeface.BOLD);
        whenView.setLetterSpacing(.04f);
        timeColumn.addView(whenView);
        timeColumn.addView(text(time, 11, green, Typeface.BOLD), topLp(-1, -2, 2));
        line.addView(timeColumn, lp(dp(84), -2));
        LinearLayout copy = column();
        TextView title = text(nextItem.name, 13, ink, Typeface.BOLD);
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        copy.addView(title);
        String subtitle = nextItem.code;
        if (!nextItem.details.isEmpty()) subtitle += (subtitle.isEmpty() ? "" : "  ·  ") + nextItem.details;
        if (!subtitle.isEmpty()) {
            TextView detail = text(subtitle, 10, muted, Typeface.NORMAL);
            detail.setMaxLines(1);
            detail.setEllipsize(android.text.TextUtils.TruncateAt.END);
            copy.addView(detail, topLp(-1, -2, 2));
        }
        line.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        line.setContentDescription("Next class: " + nextItem.name + ", " + when + " at " + time);
        line.setOnClickListener(v -> showPage(2));
        overviewScheduleRows.addView(line, lp(-1, -2));
    }

    private ScheduleItem nextScheduledClass(long now) {
        ScheduleItem next = null;
        for (ScheduleItem item : state.timetable) {
            if (item.allDay || item.cancelled || item.startsAt <= now) continue;
            if (next == null || item.startsAt < next.startsAt) next = item;
        }
        return next;
    }

    private void updateOverviewExam(State next) {
        if (overviewExamBlock == null) return;
        SimpleDateFormat dateKey = new SimpleDateFormat("yyyyMMdd", Locale.ROOT);
        boolean upcoming = next.upcomingExamAt > 0
                && (next.upcomingExamHasTime ? next.upcomingExamAt > System.currentTimeMillis()
                : dateKey.format(new Date(next.upcomingExamAt)).compareTo(dateKey.format(new Date())) >= 0)
                && !next.upcomingExamTitle.isEmpty();
        boolean connected = next.academicConnected || !next.academicCourses.isEmpty();
        overviewExamSection.setVisibility(connected ? VISIBLE : GONE);
        if (!connected) return;
        if (!upcoming) {
            overviewExamBlock.setExamState(new UpcomingExamCardState(
                    false, "No upcoming exam found", "", "", "", "",
                    "Refresh KCT from Marks & academics to check the latest schedule.",
                    "No upcoming exam found. Refresh KCT from Marks and academics to check the latest schedule."));
            return;
        }
        SimpleDateFormat format = new SimpleDateFormat(next.upcomingExamHasTime
                ? "EEE, d MMM · h:mm a" : "EEE, d MMM", Locale.getDefault());
        java.util.Calendar day = java.util.Calendar.getInstance();
        day.set(java.util.Calendar.HOUR_OF_DAY, 0);
        day.set(java.util.Calendar.MINUTE, 0);
        day.set(java.util.Calendar.SECOND, 0);
        day.set(java.util.Calendar.MILLISECOND, 0);
        java.util.Calendar examDay = (java.util.Calendar) day.clone();
        examDay.setTimeInMillis(next.upcomingExamAt);
        examDay.set(java.util.Calendar.HOUR_OF_DAY, 0);
        examDay.set(java.util.Calendar.MINUTE, 0);
        examDay.set(java.util.Calendar.SECOND, 0);
        examDay.set(java.util.Calendar.MILLISECOND, 0);
        int daysUntil = 0;
        while (day.before(examDay) && daysUntil < 400) {
            day.add(java.util.Calendar.DAY_OF_YEAR, 1);
            daysUntil++;
        }
        String countdown = daysUntil == 0 ? "Today" : daysUntil == 1 ? "Tomorrow" : "In " + daysUntil + " days";
        String examDate = format.format(new Date(next.upcomingExamAt));
        String details = next.upcomingExamCode;
        if (!next.upcomingExamDetails.isEmpty())
            details += (details.isEmpty() ? "" : " · ") + next.upcomingExamDetails;
        String accessibleDescription = "Upcoming exam: " + next.upcomingExamTitle
                + (next.upcomingExamType.isEmpty() ? "" : ", " + next.upcomingExamType)
                + (next.upcomingExamSession.isEmpty() ? "" : ", session " + next.upcomingExamSession)
                + ", " + countdown + ", " + examDate + (details.isEmpty() ? "" : ", " + details);
        overviewExamBlock.setExamState(new UpcomingExamCardState(
                true, next.upcomingExamTitle, next.upcomingExamType, next.upcomingExamSession,
                countdown, examDate, details, accessibleDescription));
    }

    private LinearLayout forecastTile(LinearLayout parent, String title) {
        LinearLayout tile = column();
        tile.setGravity(Gravity.CENTER_VERTICAL);
        tile.setPadding(dp(9), dp(7), dp(9), dp(7));
        tile.setBackground(shape(soft, 11, border));
        TextView label = text(title, 9, muted, Typeface.BOLD);
        label.setLetterSpacing(.05f);
        TextView value = text("—", 20, ink, Typeface.BOLD);
        value.setIncludeFontPadding(false);
        tile.addView(label);
        tile.addView(value, topLp(-1, -2, 2));
        tile.addView(text("", 9, muted, Typeface.NORMAL), topLp(-1, -2, 1));
        tile.setFocusable(true);
        tile.setClickable(true);
        tile.setContentDescription(title + " projected attendance");
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(76), 1);
        params.setMargins(dp(3), 0, dp(3), 0);
        parent.addView(tile, params);
        return tile;
    }

    private void selectForecast(boolean attend) {
        if (!hasForecast) return;
        attendanceRail.animateSelection(attend);
        attendForecastSelected = attend;
        updateForecastSelection(true);
        float selected = attend ? attendForecast : skipForecast;
        forecastStatusView.setText((attend ? "Attend → " : "Skip → ") + targetStatus(selected));
        forecastStatusView.setTextColor(ink);
        attendanceRail.invalidate();
        forecastStatusView.announceForAccessibility(attend
                ? "Attend next class: " + Math.round(attendForecast) + " percent projected"
                : "Skip next class: " + Math.round(skipForecast) + " percent projected");
    }

    private void updateForecastSelection(boolean animate) {
        if (attendForecastTile == null || skipForecastTile == null) return;
        LinearLayout selected = attendForecastSelected ? attendForecastTile : skipForecastTile;
        LinearLayout other = attendForecastSelected ? skipForecastTile : attendForecastTile;
        selected.setBackground(shape(hero, 11, 0));
        other.setBackground(shape(soft, 11, border));
        ((TextView) selected.getChildAt(0)).setTextColor(heroText);
        ((TextView) selected.getChildAt(1)).setTextColor(heroText);
        ((TextView) other.getChildAt(0)).setTextColor(muted);
        ((TextView) other.getChildAt(1)).setTextColor(ink);
        attendForecastTile.setSelected(attendForecastSelected);
        skipForecastTile.setSelected(!attendForecastSelected);
        attendForecastTile.setContentDescription("Attend next class, projected "
                + Math.round(attendForecast) + " percent" + (attendForecastSelected ? ", selected" : ""));
        skipForecastTile.setContentDescription("Skip next class, projected "
                + Math.round(skipForecast) + " percent" + (!attendForecastSelected ? ", selected" : ""));
        if (animate && selected.isShown()
                && (android.os.Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled())) {
            selected.animate().cancel();
            selected.setScaleX(.97f); selected.setScaleY(.97f);
            selected.animate().scaleX(1f).scaleY(1f).setDuration(170)
                    .setInterpolator(new DecelerateInterpolator()).start();
        }
    }

    private void updateTimetable() {
        if (scheduleRows == null || state == null) return;
        scheduleContent.setVisibility(showingHistory ? GONE : VISIBLE);
        historyContent.setVisibility(showingHistory ? VISIBLE : GONE);
        scheduleExtras.setVisibility(showingHistory ? GONE : VISIBLE);
        scheduleTab.setTextColor(showingHistory ? muted : ink);
        historyTab.setTextColor(showingHistory ? ink : muted);
        scheduleTab.setSelected(!showingHistory);
        historyTab.setSelected(showingHistory);
        scheduleTab.setBackground(showingHistory ? null : shape(surface, 10, border));
        historyTab.setBackground(showingHistory ? shape(surface, 10, border) : null);
        if (weekPulse != null) {
            int total = weekPulse.setSchedule(state.timetable);
            int campusDays = 0;
            for (int count : weekPulse.counts) if (count > 0) campusDays++;
            weekPulse.setVisibility(VISIBLE);
            weekPulseMeta.setText(!state.timetableLoaded ? "Sync MyCamu to map your timetable."
                    : state.timetable.isEmpty() ? "No classes scheduled!"
                    : total == 0 ? "No class periods mapped this week."
                    : total + (total == 1 ? " class" : " classes") + " across " + campusDays
                    + (campusDays == 1 ? " campus day" : " campus days"));
        }
        scheduleUpdatedView.setText(showingHistory
                ? state.classMarksUpdatedAt > 0
                    ? (state.syncError ? "Sync issue · attendance log last updated " : "Attendance log · Updated ")
                        + relativeAge(state.classMarksUpdatedAt)
                    : state.syncError ? "Sync issue · open Home and refresh attendance" : "Waiting for MyCamu attendance log"
                : state.timetableLoaded
                    ? (state.syncError ? "Sync issue · timetable last updated " : "MyCamu timetable · Updated ")
                        + relativeAge(state.timetableUpdatedAt)
                    : state.syncError ? "Sync issue · open Home and refresh your timetable" : "Waiting for MyCamu timetable");
        if (showingHistory) {
            if (historyDirty) updateClassHistory();
            return;
        }
        if (!scheduleDirty) return;
        scheduleDirty = false;
        scheduleRows.removeAllViews();
        boolean hasSchedule = state.timetableLoaded && !state.timetable.isEmpty();
        scheduleEmptyPanel.setVisibility(hasSchedule ? GONE : VISIBLE);
        scheduleEmptyTitle.setText(state.sessionExpired ? "RECONNECT TO\nSEE YOUR WEEK."
                : state.timetableLoaded && state.timetable.isEmpty() ? "NO CLASSES\nON YOUR\nSCHEDULE."
                : state.syncError ? "YOUR WEEK\nNEEDS A\nREFRESH."
                : "YOUR WEEK\nIS WAITING.");
        scheduleEmptyDetail.setText(state.sessionExpired
                ? "Sign in to MyCamu again to refresh your timetable."
                : state.timetableLoaded && state.timetable.isEmpty()
                    ? "MyCamu has no classes scheduled this week. Your attendance history is still available."
                    : state.syncError ? "Rollcall could not refresh the timetable. Try again when the connection is ready."
                    : "Sync MyCamu to display your connected classes here.");
        scheduleEmptyView.setText(state.sessionExpired ? "Reconnect MyCamu  →"
                : state.timetableLoaded && state.timetable.isEmpty() ? "Refresh timetable  →"
                : state.syncError ? "Try again  →" : "Sync timetable  →");
        scheduleEmptyView.setContentDescription(state.sessionExpired ? "Reconnect MyCamu to load the timetable"
                : "Refresh timetable from MyCamu");
        HashSet<String> dates = new HashSet<>();
        for (ScheduleItem item : state.timetable) dates.add(item.date);
        boolean stillHasExpandedDay = false;
        for (String date : expandedScheduleDays) if (dates.contains(date)) { stillHasExpandedDay = true; break; }
        if (scheduleExpansionInitialized && !expandedScheduleDays.isEmpty() && !stillHasExpandedDay) {
            expandedScheduleDays.clear();
            scheduleExpansionInitialized = false;
        }
        if (!scheduleExpansionInitialized && !state.timetable.isEmpty()) {
            Calendar today = Calendar.getInstance();
            today.set(Calendar.HOUR_OF_DAY, 0); today.set(Calendar.MINUTE, 0);
            today.set(Calendar.SECOND, 0); today.set(Calendar.MILLISECOND, 0);
            long todayStart = today.getTimeInMillis();
            String defaultDate = state.timetable.get(0).date;
            for (ScheduleItem item : state.timetable) {
                if (item.startsAt >= todayStart) { defaultDate = item.date; break; }
            }
            expandedScheduleDays.add(defaultDate);
            scheduleExpansionInitialized = true;
        }
        int index = 0;
        while (index < state.timetable.size()) {
            int end = index + 1;
            String date = state.timetable.get(index).date;
            while (end < state.timetable.size() && date.equals(state.timetable.get(end).date)) end++;

            LinearLayout dayCard = column();
            dayCard.setPadding(dp(16), dp(8), dp(16), dp(8));
            dayCard.setBackground(industrialSurface(surface, 20, border));
            LinearLayout dayHeader = row();
            dayHeader.setGravity(Gravity.CENTER_VERTICAL);
            TextView day = text(date, 15, ink, Typeface.BOLD);
            day.setIncludeFontPadding(false);
            day.setMinHeight(dp(40));
            day.setGravity(Gravity.CENTER_VERTICAL);
            dayHeader.addView(day, new LinearLayout.LayoutParams(0, -2, 1));
            int count = end - index;
            dayHeader.addView(text(count + (count == 1 ? " class" : " classes"), 12, muted, Typeface.NORMAL));
            boolean expanded = expandedScheduleDays.contains(date);
            TextView expansionIcon = text(expanded ? "−" : "+", 18, green, Typeface.BOLD);
            expansionIcon.setGravity(Gravity.CENTER);
            expansionIcon.setPadding(dp(8), 0, 0, 0);
            dayHeader.addView(expansionIcon, lp(dp(40), dp(48)));
            dayCard.addView(dayHeader);

            TextView daySummary = text(daySummary(index, end), 11, muted, Typeface.NORMAL);
            daySummary.setPadding(0, dp(4), 0, dp(6));
            daySummary.setVisibility(expanded ? GONE : VISIBLE);
            dayCard.addView(daySummary);
            LinearLayout dayItems = column();
            dayItems.setVisibility(expanded ? VISIBLE : GONE);
            dayCard.addView(dayItems);
            dayHeader.setContentDescription(date + ", " + count + (count == 1 ? " class" : " classes")
                    + (expanded ? ", collapse" : ", expand"));
            dayHeader.setOnClickListener(v -> {
                if (expandedScheduleDays.contains(date)) expandedScheduleDays.remove(date);
                else expandedScheduleDays.add(date);
                pendingScheduleRevealDate = date;
                scheduleDirty = true;
                updateTimetable();
            });

            for (int i = expanded ? index : end; i < end; i++) {
                ScheduleItem item = state.timetable.get(i);
                LinearLayout line = row();
                line.setGravity(Gravity.CENTER_VERTICAL);
                line.setPadding(0, dp(7), 0, dp(7));
                String timeLabel = item.time;
                if (!item.allDay) {
                    String[] range = item.time.split("\\s*[–—-]\\s*", 2);
                    if (range.length == 2) timeLabel = range[0] + "\n" + range[1];
                } else timeLabel = "All day";
                TextView time = text(timeLabel, 11, green, Typeface.BOLD);
                time.setGravity(Gravity.CENTER_VERTICAL);
                time.setMaxLines(2);
                time.setIncludeFontPadding(false);
                line.addView(time, lp(dp(72), -2));
                LinearLayout copy = column();
                String title = item.name + (item.code.isEmpty() ? "" : "  ·  " + item.code);
                TextView course = text(title, 13, ink, Typeface.BOLD);
                course.setMaxLines(2);
                copy.addView(course);
                if (!item.details.isEmpty()) copy.addView(text(item.details, 11, muted, Typeface.NORMAL), topLp(-1, -2, 2));
                line.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
                if (item.cancelled) {
                    TextView canceled = text("Cancelled", 8, muted, Typeface.BOLD);
                    canceled.setPadding(dp(5), dp(4), dp(5), dp(4));
                    canceled.setBackground(shape(soft, 8, 0));
                    line.addView(canceled, lp(-2, -2));
                }
                dayItems.addView(line);
                if (i < end - 1) {
                    View divider = new View(getContext());
                    divider.setBackgroundColor(border);
                    LinearLayout.LayoutParams dividerParams = lp(-1, dp(1));
                    dividerParams.leftMargin = dp(72);
                    dayItems.addView(divider, dividerParams);
                }
            }
            LinearLayout.LayoutParams dayParams = lp(-1, -2);
            dayParams.bottomMargin = dp(8);
            scheduleRows.addView(dayCard, dayParams);
            if (date.equals(pendingScheduleRevealDate)) {
                pendingScheduleRevealDate = "";
                View revealed = expanded ? dayItems : daySummary;
                if (android.os.Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled()) {
                    revealed.setAlpha(.9f);
                    revealed.setTranslationY(dp(4));
                    revealed.animate().alpha(1f).translationY(0f).setDuration(180)
                            .setInterpolator(new android.view.animation.PathInterpolator(.2f, 0f, 0f, 1f)).start();
                }
            }
            index = end;
        }
    }

    private void buildOdDetailsPage() {
        odDetailsPage = new ScrollView(getContext());
        odDetailsPage.setFillViewport(true);
        odDetailsPage.setBackgroundColor(canvas);
        LinearLayout body = column();
        body.setPadding(dp(18), dp(12), dp(18), dp(24));
        odDetailsPage.addView(body, new ScrollView.LayoutParams(-1, -2));
        TextView back = text("←  Back", 13, green, Typeface.BOLD);
        back.setGravity(Gravity.CENTER_VERTICAL);
        back.setMinHeight(dp(48));
        back.setOnClickListener(v -> closeOdDetails());
        body.addView(back);
        TextView title = text("Leave & OD", 25, ink, Typeface.BOLD);
        body.addView(title, topLp(-1, -2, 8));
        body.addView(text("Open an OD to see the scheduled classes it covers. Use All OD for older semester records.",
                12, muted, Typeface.NORMAL), topLp(-1, -2, 4));
        LinearLayout odFilters = row();
        currentSemesterOdFilter = filterChip("This semester");
        allOdFilter = filterChip("All OD");
        currentSemesterOdFilter.setOnClickListener(v -> { showAllOd = false; updateOdDetails(); });
        allOdFilter.setOnClickListener(v -> { showAllOd = true; updateOdDetails(); });
        LinearLayout.LayoutParams currentFilterParams = new LinearLayout.LayoutParams(0, dp(40), 1);
        currentFilterParams.rightMargin = dp(5);
        LinearLayout.LayoutParams allFilterParams = new LinearLayout.LayoutParams(0, dp(40), 1);
        allFilterParams.leftMargin = dp(5);
        odFilters.addView(currentSemesterOdFilter, currentFilterParams);
        odFilters.addView(allOdFilter, allFilterParams);
        body.addView(odFilters, topLp(-1, -2, 12));
        odDetailsRows = column();
        body.addView(odDetailsRows, topLp(-1, -2, 16));
        pages.addView(odDetailsPage, new FrameLayout.LayoutParams(-1, -1));
        odDetailsPage.setVisibility(GONE);
    }

    private void openOdDetails() {
        if (odDetailsPage == null) return;
        odDetailsReturnPage = selectedPage;
        updateOdDetails();
        odDetailsOpen = true;
        for (ScrollView page : pageScrolls) if (page != null) {
            page.animate().cancel();
            page.setVisibility(GONE);
        }
        nav.setVisibility(GONE);
        odDetailsPage.setVisibility(VISIBLE);
        odDetailsPage.scrollTo(0, 0);
        odDetailsPage.bringToFront();
        animatePageArrival(odDetailsPage, 1);
    }

    private void closeOdDetails() {
        odDetailsOpen = false;
        if (odDetailsPage != null) {
            odDetailsPage.animate().cancel();
            odDetailsPage.setVisibility(GONE);
        }
        ScrollView returnPage = pageScrolls[odDetailsReturnPage];
        returnPage.setVisibility(VISIBLE);
        returnPage.bringToFront();
        nav.setVisibility(VISIBLE);
        selectedPage = odDetailsReturnPage;
        animatePageArrival(returnPage, -1);
        updateNavigation();
    }

    boolean closeOdDetailsForBack() {
        if (!odDetailsOpen) return false;
        closeOdDetails();
        return true;
    }

    private void updateOdDetails() {
        if (odDetailsRows == null) return;
        odDetailsRows.removeAllViews();
        setChipSelected(currentSemesterOdFilter, !showAllOd);
        setChipSelected(allOdFilter, showAllOd);
        int approved = 0, pending = 0, rejected = 0, currentOdCount = 0, allOdCount = 0;
        for (LeaveItem leave : state.leaves) {
            if (Attendance.isApprovedOd(leave.type, leave.status)) {
                allOdCount++;
                if (isCurrentSemesterOd(leave)) currentOdCount++;
                if (!showAllOd && !isCurrentSemesterOd(leave)) continue;
            }
            String status = leave.status.toLowerCase(Locale.ROOT);
            if (Attendance.isApprovedOd(leave.type, leave.status)) approved++;
            else if (status.contains("reject")) rejected++;
            else if (status.contains("pending") || status.contains("submit") || status.contains("request")) pending++;
        }
        currentSemesterOdFilter.setText("Current sem · " + currentOdCount);
        allOdFilter.setText("All OD · " + allOdCount);
        LinearLayout summary = row();
        summary.setGravity(Gravity.CENTER_VERTICAL);
        summary.addView(text("✓ " + approved + " approved", 11, green, Typeface.BOLD),
                new LinearLayout.LayoutParams(0, dp(44), 1));
        summary.addView(text("◷ " + pending + " pending", 11, amber, Typeface.BOLD),
                new LinearLayout.LayoutParams(0, dp(44), 1));
        summary.addView(text("× " + rejected + " rejected", 11, red, Typeface.BOLD),
                new LinearLayout.LayoutParams(0, dp(44), 1));
        odDetailsRows.addView(summary);

        boolean hasApprovedMark = false;
        for (ClassMarkItem mark : state.classMarks)
            hasApprovedMark |= Attendance.isApprovedOd(mark.leaveType, mark.leaveStatus);
        if (state.leaves.isEmpty() && !hasApprovedMark) {
            TextView empty = text("Your leave and OD requests will appear here. Sync to check their latest status.", 13, muted, Typeface.NORMAL);
            empty.setPadding(dp(14), dp(16), dp(14), dp(16));
            empty.setBackground(shape(surface, 14, border));
            odDetailsRows.addView(empty, topLp(-1, -2, 10));
            return;
        }

        int visibleRecords = 0;
        for (LeaveItem leave : state.leaves) {
            boolean isOd = Attendance.isOdType(leave.type);
            if (isOd && !showAllOd && !isCurrentSemesterOd(leave)) continue;
            visibleRecords++;
            LinearLayout item = column();
            item.setPadding(dp(14), dp(12), dp(14), dp(12));
            item.setBackground(shape(surface, 14, border));
            String status = leave.status == null || leave.status.trim().isEmpty() ? "Unknown" : leave.status;
            String recordKey = leave.id.isEmpty() ? leave.type + leave.startsAt : leave.id;
            boolean expanded = expandedOdRecords.contains(recordKey);
            LinearLayout titleLine = row();
            titleLine.setGravity(Gravity.CENTER_VERTICAL);
            titleLine.setMinimumHeight(dp(48));
            TextView heading = text(leave.type, 14, ink, Typeface.BOLD);
            heading.setMaxLines(1);
            heading.setEllipsize(android.text.TextUtils.TruncateAt.END);
            titleLine.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
            TextView statusBadge = text(status, 11, leave.color, Typeface.BOLD);
            statusBadge.setPadding(dp(8), dp(5), dp(8), dp(5));
            statusBadge.setBackground(shape(soft, 10, 0));
            titleLine.addView(statusBadge);
            if (isOd) {
                TextView disclosure = text(expanded ? "−" : "+", 16, green, Typeface.BOLD);
                disclosure.setGravity(Gravity.CENTER);
                disclosure.setMinWidth(dp(40));
                titleLine.addView(disclosure);
                titleLine.setContentDescription(leave.type + ", " + status + (expanded ? ", collapse details" : ", show details"));
                titleLine.setOnClickListener(v -> {
                    if (expandedOdRecords.contains(recordKey)) expandedOdRecords.remove(recordKey);
                    else expandedOdRecords.add(recordKey);
                    updateOdDetails();
                });
            }
            item.addView(titleLine);
            item.addView(text(leave.date, 12, muted, Typeface.NORMAL), topLp(-1, -2, 3));
            JSONArray previewSlots = periodDetails(leave.periodDetails);
            int odHours = previewSlots.length() > 0 ? previewSlots.length() : leave.periods;
            String cardSummary = isOd
                    ? odHours > 0 ? odHours + (odHours == 1 ? " HOUR" : " HOURS")
                    : state.timetableLoaded ? "FULL DAY" : "FULL DAY · SYNC TO MAP HOURS"
                    : leave.periods > 0 ? leave.periods + (leave.periods == 1 ? " period requested" : " periods requested")
                    : "Full-day leave request";
            TextView duration = text(cardSummary, 13, palette.button, Typeface.BOLD);
            if (isOd) {
                duration.setPadding(dp(9), dp(6), dp(9), dp(6));
                duration.setBackground(shape(soft, 8, 0));
            }
            item.addView(duration, topLp(-2, -2, 8));
            if (isOd && expanded) {
                JSONArray slots = periodDetails(leave.periodDetails);
                Map<String, List<JSONObject>> byDay = new java.util.TreeMap<>();
                Calendar coveredDay = Calendar.getInstance();
                coveredDay.setTimeInMillis(leave.startsAt);
                coveredDay.set(Calendar.HOUR_OF_DAY, 0); coveredDay.set(Calendar.MINUTE, 0);
                coveredDay.set(Calendar.SECOND, 0); coveredDay.set(Calendar.MILLISECOND, 0);
                Calendar lastCoveredDay = Calendar.getInstance();
                lastCoveredDay.setTimeInMillis(Math.max(leave.startsAt, leave.endsAt));
                lastCoveredDay.set(Calendar.HOUR_OF_DAY, 0); lastCoveredDay.set(Calendar.MINUTE, 0);
                lastCoveredDay.set(Calendar.SECOND, 0); lastCoveredDay.set(Calendar.MILLISECOND, 0);
                SimpleDateFormat dayKeyFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                for (int day = 0; coveredDay.before(lastCoveredDay) || coveredDay.equals(lastCoveredDay); day++) {
                    if (day >= 31) break;
                    byDay.put(dayKeyFormat.format(coveredDay.getTime()), new ArrayList<>());
                    coveredDay.add(Calendar.DAY_OF_YEAR, 1);
                }
                for (int i = 0; i < slots.length(); i++) {
                    JSONObject slot = slots.optJSONObject(i);
                    if (slot == null) continue;
                    String dateKey = slot.optString("date", new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(leave.startsAt)));
                    if (!byDay.containsKey(dateKey)) byDay.put(dateKey, new ArrayList<>());
                    byDay.get(dateKey).add(slot);
                }
                if (byDay.isEmpty()) item.addView(text("Class periods have not been mapped yet. Sync the timetable to see the covered classes.",
                        12, muted, Typeface.NORMAL), topLp(-1, -2, 8));
                SimpleDateFormat dateTitle = new SimpleDateFormat("EEEE, d MMM yyyy", Locale.getDefault());
                for (Map.Entry<String, List<JSONObject>> day : byDay.entrySet()) {
                    long dayTime = leave.startsAt;
                    try { dayTime = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(day.getKey()).getTime(); }
                    catch (Exception ignored) { }
                    item.addView(text(dateTitle.format(new Date(dayTime)), 12, green, Typeface.BOLD), topLp(-1, -2, 10));
                    for (JSONObject slot : day.getValue()) {
                        String time = slot.optString("time", "");
                        String endTime = slot.optString("endTime", "");
                        String interval = time.isEmpty() ? "" : time + (endTime.isEmpty() ? "" : "–" + endTime) + " · ";
                        String code = slot.optString("code", "");
                        String course = shortCourseName(slot.optString("name", "Class"));
                        item.addView(text("Period " + slot.optInt("period", 0) + " · " + interval + course
                                + (code.isEmpty() ? "" : " (" + code + ")"), 12, muted, Typeface.NORMAL), topLp(-1, -2, 4));
                    }
                    if (day.getValue().isEmpty())
                        item.addView(text(state.timetableLoaded ? "No scheduled classes map to this OD date."
                                : "Sync your timetable to see the classes covered on this day.",
                                11, muted, Typeface.NORMAL), topLp(-1, -2, 4));
                }
            }
            odDetailsRows.addView(item, topLp(-1, -2, 10));
        }
        if (visibleRecords == 0) {
            TextView empty = text("No OD periods map to courses in the current semester. Older OD records are available under All OD.",
                    12, muted, Typeface.NORMAL);
            empty.setPadding(dp(14), dp(14), dp(14), dp(14));
            empty.setBackground(shape(surface, 14, border));
            odDetailsRows.addView(empty, topLp(-1, -2, 10));
        }
    }

    private boolean isCurrentSemesterOd(LeaveItem leave) {
        if (leave.semesterKnown) return leave.currentSemester;
        if (leave.currentSemester) return true;
        JSONArray slots = periodDetails(leave.periodDetails);
        for (int i = 0; i < slots.length(); i++) {
            JSONObject slot = slots.optJSONObject(i);
            if (slot != null) {
                String code = currentCourseCode(slot.optString("code", ""), slot.optString("name", ""));
                if (state.currentSemesterClassKeys.contains(odClassKey(code, slot.optLong("startsAt", 0)))) return true;
            }
        }
        long startDay = dayTimestamp(leave.startsAt), endDay = dayTimestamp(Math.max(leave.startsAt, leave.endsAt));
        long semesterStart = Long.MAX_VALUE;
        for (String key : state.currentSemesterClassKeys) {
            int separator = key.lastIndexOf(':');
            if (separator < 0) continue;
            try {
                long classDay = dayTimestamp(Long.parseLong(key.substring(separator + 1)) * 60_000L);
                semesterStart = Math.min(semesterStart, classDay);
                if (classDay >= startDay && classDay <= endDay) return true;
            } catch (NumberFormatException ignored) { }
        }
        if (semesterStart != Long.MAX_VALUE && endDay >= semesterStart) return true;
        // shortcut: include untagged records when no active course dates exist; use MyCamu semester IDs when available.
        if (semesterStart == Long.MAX_VALUE) return true;
        return false;
    }

    private ArrayList<ClassMarkItem> classMarksForLeave(LeaveItem leave) {
        ArrayList<ClassMarkItem> result = new ArrayList<>();
        for (ClassMarkItem mark : state.classMarks) {
            if (!isWithinLeaveDates(mark.startsAt, leave.startsAt, leave.endsAt)) continue;
            if (!showAllOd && !state.currentSemesterClassKeys.contains(odClassKey(
                    currentCourseCode(mark.code, mark.name), mark.startsAt))) continue;
            result.add(mark);
        }
        Collections.sort(result, Comparator.comparingLong(mark -> mark.startsAt));
        return result;
    }

    private boolean isCurrentSemesterSlot(JSONObject slot) {
        String code = currentCourseCode(slot.optString("code", ""), slot.optString("name", ""));
        long startsAt = slot.optLong("startsAt", 0);
        return startsAt > 0 && state.currentSemesterClassKeys.contains(odClassKey(code, startsAt));
    }

    private boolean isWithinLeaveDates(long time, long start, long end) {
        long classDay = dayTimestamp(time);
        return classDay >= dayTimestamp(start) && classDay <= dayTimestamp(Math.max(start, end));
    }

    private long dayTimestamp(long timestamp) {
        Calendar day = Calendar.getInstance();
        day.setTimeInMillis(timestamp);
        day.set(Calendar.HOUR_OF_DAY, 0); day.set(Calendar.MINUTE, 0);
        day.set(Calendar.SECOND, 0); day.set(Calendar.MILLISECOND, 0);
        return day.getTimeInMillis();
    }

    private String currentCourseCode(String code, String name) {
        String normalizedCode = normalizeCourseCode(code);
        String normalizedName = normalizeCourseCode(name);
        for (Course course : state.rows) {
            if (!normalizedCode.isEmpty() && normalizedCode.equals(normalizeCourseCode(course.code))) return course.code;
            if (!normalizedName.isEmpty() && (normalizedName.equals(normalizeCourseCode(course.name))
                    || normalizedName.equals(normalizeCourseCode(course.code)))) return course.code;
        }
        return "";
    }

    private JSONArray periodDetails(String value) {
        try { return new JSONArray(value == null ? "[]" : value); }
        catch (org.json.JSONException ignored) { return new JSONArray(); }
    }

    static String shortCourseName(String name) {
        if (name == null || name.trim().isEmpty()) return "Class";
        name = name.trim();
        if (name.indexOf(' ') < 0 && name.length() <= 20) return name;
        StringBuilder shortName = new StringBuilder();
        for (String part : name.toUpperCase(Locale.ROOT).split("[^A-Z0-9]+")) {
            if (part.isEmpty() || "AND".equals(part) || "OF".equals(part) || "THE".equals(part)) continue;
            shortName.append(part.charAt(0));
        }
        return shortName.length() == 0 ? name : shortName.toString();
    }

    private void updateClassHistory() {
        historyDirty = false;
        historyRows.removeAllViews();
        historyEmptyView.setVisibility(state.classMarks.isEmpty() ? VISIBLE : GONE);
        historyEmptyView.setText(state.sessionExpired ? "Reconnect MyCamu to load attendance history  →"
                : state.syncError ? "Sync failed · tap to retry attendance history  →"
                : state.classMarksUpdatedAt > 0 ? "No attendance records returned · tap to refresh  →"
                : "Tap to sync MyCamu and load attendance history  →");
        historyEmptyView.setContentDescription(state.sessionExpired ? "Reconnect MyCamu to load attendance history"
                : "Sync attendance history from MyCamu");
        int shown = Math.min(historyLimit, state.classMarks.size());
        String activeDay = "";
        for (int i = 0; i < shown; i++) {
            ClassMarkItem mark = state.classMarks.get(i);
            if (!mark.day.equals(activeDay)) {
                activeDay = mark.day;
                TextView day = label(activeDay.toUpperCase(Locale.ROOT), 9, muted, Typeface.BOLD);
                day.setPadding(dp(2), dp(9), dp(2), dp(3));
                historyRows.addView(day);
            }
            LinearLayout line = row();
            line.setGravity(Gravity.CENTER_VERTICAL);
            line.setPadding(dp(10), dp(8), dp(10), dp(8));
            line.setBackground(shape(surface, 11, border));
            LinearLayout copy = column();
            TextView title = text(mark.name, 12, ink, Typeface.BOLD);
            title.setMaxLines(2);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            copy.addView(title);
            String detail = mark.code.isEmpty() ? mark.time : mark.code + "  ·  " + mark.time;
            if (!mark.leaveType.isEmpty()) detail += "\n" + mark.leaveType + " · " + mark.leaveStatus;
            TextView sub = text(detail, 10, muted, Typeface.NORMAL);
            sub.setMaxLines(1);
            copy.addView(sub, topLp(-1, -2, 2));
            line.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
            String status = "present".equals(mark.status) ? "Present"
                    : "absent".equals(mark.status) ? "Absent"
                    : "not_taken".equals(mark.status) ? "Not marked"
                    : "Unable to confirm";
            TextView badge = text(status, 9, mark.color, Typeface.BOLD);
            badge.setGravity(Gravity.CENTER);
            badge.setPadding(dp(8), dp(5), dp(8), dp(5));
            badge.setBackground(shape(soft, 9, 0));
            badge.setContentDescription(mark.name + ", " + status);
            line.addView(badge, lp(-2, -2));
            LinearLayout.LayoutParams params = lp(-1, -2);
            params.bottomMargin = dp(5);
            historyRows.addView(line, params);
        }
        if (shown < state.classMarks.size()) {
            TextView more = text("Show older · " + (state.classMarks.size() - shown) + " more", 11, green, Typeface.BOLD);
            more.setGravity(Gravity.CENTER);
            more.setMinHeight(dp(44));
            more.setOnClickListener(v -> { historyLimit += 30; updateClassHistory(); });
            historyRows.addView(more, lp(-1, dp(44)));
        }
    }

    private String daySummary(int start, int end) {
        String first = "", last = "";
        for (int i = start; i < end; i++) {
            ScheduleItem item = state.timetable.get(i);
            if (item.allDay || item.time.isEmpty()) continue;
            if (first.isEmpty()) first = item.time;
            last = item.time;
        }
        if (first.isEmpty()) return "All-day items";
        int separator = last.indexOf(" – ");
        String endTime = separator >= 0 ? last.substring(separator + 3) : last;
        int firstSeparator = first.indexOf(" – ");
        String startTime = firstSeparator >= 0 ? first.substring(0, firstSeparator) : first;
        return startTime + " – " + endTime;
    }

    private String relativeAge(long timestamp) {
        long minutes = Math.max(0, (System.currentTimeMillis() - timestamp) / 60_000L);
        if (minutes == 0) return "just now";
        if (minutes < 60) return minutes + "m ago";
        if (minutes < 1440) return (minutes / 60) + "h ago";
        return (minutes / 1440) + "d ago";
    }

    private LinearLayout newPage(int index) {
        ScrollView scroll = new ScrollView(getContext());
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        if (cyberpunk) scroll.setBackground(new ScanlineDrawable());
        else scroll.setBackgroundColor(canvas);
        LinearLayout body = column();
        body.setPadding(dp(20), 0, dp(20), dp(24));
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
        pageScrolls[index] = scroll;
        pageBodies[index] = body;
        return body;
    }

    private boolean startsOnHorizontalControl(ViewGroup parent, float x, float y) {
        for (int i = parent.getChildCount() - 1; i >= 0; i--) {
            View child = parent.getChildAt(i);
            float childX = x + parent.getScrollX() - child.getLeft();
            float childY = y + parent.getScrollY() - child.getTop();
            if (childX < 0 || childY < 0 || childX >= child.getWidth() || childY >= child.getHeight()) continue;
            if (child instanceof android.widget.SeekBar || child instanceof EditText
                    || child instanceof android.widget.HorizontalScrollView) return true;
            if (child instanceof ViewGroup && startsOnHorizontalControl((ViewGroup) child, childX, childY)) return true;
            return false;
        }
        return false;
    }

    private final class ModeSwitchButtonView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean dark;

        ModeSwitchButtonView(Context context) {
            super(context);
            setBackground(shape(surface, 10, border));
            setFocusable(true);
            setClickable(true);
            setElevation(dp(1));
            setOnClickListener(v -> actions.onTheme());
            setTactileFeedback(this);
            setDark(false);
        }

        void setDark(boolean value) {
            dark = value;
            setSelected(value);
            setContentDescription(value ? "Switch to light mode" : "Switch to dark mode");
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cy = getHeight() / 2f;
            float cx = getWidth() / 2f;
            paint.setStyle(Paint.Style.FILL);
            paint.setStrokeWidth(dp(1.6f));
            paint.setStrokeCap(Paint.Cap.ROUND);
            if (dark) {
                paint.setColor(ink);
                canvas.drawCircle(cx, cy, dp(6.2f), paint);
                paint.setColor(surface);
                canvas.drawCircle(cx + dp(3.8f), cy - dp(3.4f), dp(5.8f), paint);
            } else {
                paint.setColor(ink);
                canvas.drawCircle(cx, cy, dp(3.8f), paint);
                paint.setStyle(Paint.Style.STROKE);
                for (int i = 0; i < 8; i++) {
                    double angle = i * Math.PI / 4d;
                    float x1 = cx + (float) Math.cos(angle) * dp(6.2f);
                    float y1 = cy + (float) Math.sin(angle) * dp(6.2f);
                    float x2 = cx + (float) Math.cos(angle) * dp(8.3f);
                    float y2 = cy + (float) Math.sin(angle) * dp(8.3f);
                    canvas.drawLine(x1, y1, x2, y2, paint);
                }
            }
            paint.setStyle(Paint.Style.FILL);
        }

        @Override public CharSequence getAccessibilityClassName() { return Button.class.getName(); }
    }

    private View topBar(boolean showTheme) {
        LinearLayout bar = row();
        RollcallLogo logo = new RollcallLogo(getContext(), palette);
        logo.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        bar.addView(logo, lp(dp(34), dp(34)));
        TextView brand = text("rollcall", 19, ink, Typeface.BOLD);
        brand.setPadding(dp(8), 0, 0, 0);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        if (cyberpunk) {
            brand.setText("ROLLCALL");
            brand.setTypeface(cyberpunkTypeface);
            brand.setLetterSpacing(.035f);
        } else {
            brand.setTypeface(displayTypeface);
        }
        bar.addView(brand, new LinearLayout.LayoutParams(0, -1, 1));
        if (showTheme) {
            homeThemeButton = new ModeSwitchButtonView(getContext());
            homeThemeButton.setDark(state.dark);
            bar.addView(homeThemeButton, lp(dp(48), dp(48)));
        }
        TextView avatar = text("R", 12, ink, Typeface.BOLD);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(new android.graphics.drawable.InsetDrawable(shape(soft, 100, 0), dp(4)));
        avatar.setContentDescription("About Rollcall and student profile");
        avatar.setFocusable(true);
        avatar.setOnClickListener(v -> actions.onAbout());
        setTactileFeedback(avatar);
        avatars.add(avatar);
        bar.addView(avatar, lp(dp(48), dp(48)));
        return bar;
    }

    private View heading(String index, String title, String subtitle) {
        LinearLayout box = column();
        box.addView(label(index, 9, lime, Typeface.BOLD));
        SpannableString titleText = new SpannableString(title + ".");
        titleText.setSpan(new android.text.style.ForegroundColorSpan(palette.highlight), title.length(), titleText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        TextView heading = text(titleText.toString(), 32, ink, Typeface.BOLD);
        heading.setText(titleText);
        heading.setTypeface(displayTypeface, Typeface.BOLD);
        heading.setLetterSpacing(-.055f);
        box.addView(heading, topLp(-1, -2, 6));
        TextView sub = text(subtitle, 12, muted, Typeface.NORMAL);
        sub.setLineSpacing(dp(2), 1f);
        sub.setPadding(0, dp(2), 0, dp(10));
        box.addView(sub);
        return box;
    }

    private View targetCard() {
        LinearLayout card = new LinearLayout(getContext());
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(shape(soft, 18, 0));
        LinearLayout copy = column();
        copy.addView(text("Your attendance target", 11, muted, Typeface.NORMAL));
        targetView = text("75%", 24, ink, Typeface.NORMAL);
        copy.addView(targetView, topLp(-1, -2, 2));
        copy.addView(text("A goal that keeps you on track.", 10, muted, Typeface.NORMAL), topLp(-1, -2, 1));
        card.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        TextView adjust = text("Adjust target  ↗", 10, hero, Typeface.BOLD);
        adjust.setOnClickListener(v -> actions.onTarget());
        card.addView(adjust);
        card.setOnClickListener(v -> actions.onTarget());
        return card;
    }


    private View filters() {
        LinearLayout filters = row();
        filters.setGravity(Gravity.CENTER_VERTICAL);
        filters.setPadding(dp(3), dp(3), dp(3), dp(3));
        filters.setBackground(shape(soft, 12, 0));
        TextView all = filterChip("All courses · " + state.courses);
        TextView focus = filterChip("Below target · " + state.below);
        allCoursesFilter = all;
        focusCoursesFilter = focus;
        all.setTag("allFilter");
        focus.setTag("focusFilter");
        all.setOnClickListener(v -> setFilter(false));
        focus.setOnClickListener(v -> setFilter(true));
        filters.addView(all, new LinearLayout.LayoutParams(0, dp(48), 1));
        filters.addView(focus, new LinearLayout.LayoutParams(0, dp(48), 1));
        return filters;
    }

    private TextView filterChip(String label) {
        TextView chip = text(label, 10, muted, Typeface.BOLD);
        chip.setPadding(dp(11), dp(8), dp(11), dp(8));
        chip.setGravity(Gravity.CENTER);
        chip.setMinHeight(dp(34));
        chip.setBackground(shape(soft, 6, 0));
        return chip;
    }

    private EditText searchField(String hint) {
        EditText field = new EditText(getContext());
        field.setSingleLine(true);
        field.setTextSize(14);
        field.setMinHeight(dp(48));
        field.setTextColor(ink);
        field.setHintTextColor(muted);
        field.setHint(hint);
        field.setTypeface(uiTypeface);
        field.setContentDescription(hint);
        field.setPadding(dp(12), 0, dp(12), 0);
        field.setBackground(shape(soft, 5, 0));
        field.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                query = s.toString();
                refreshVisibleRows();
                actions.onQuery(query);
            }
            @Override public void afterTextChanged(Editable e) { }
        });
        return field;
    }

    private View plannerCard() {
        LinearLayout card = card();
        card.addView(label("Choose a course", 13, muted, Typeface.BOLD));
        coursePicker = new FrameLayout(getContext());
        coursePicker.setBackground(shape(surface, 12, border));
        coursePickerText = text("Choose a course", 14, ink, Typeface.NORMAL);
        coursePickerText.setGravity(Gravity.CENTER_VERTICAL);
        coursePickerText.setMaxLines(2);
        coursePickerText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        coursePickerText.setPadding(dp(12), 0, dp(40), 0);
        coursePicker.addView(coursePickerText, new FrameLayout.LayoutParams(-1, -1));
        TextView spinnerArrow = text("⌄", 18, muted, Typeface.NORMAL);
        spinnerArrow.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams arrowParams = new FrameLayout.LayoutParams(dp(40), -1, Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        coursePicker.addView(spinnerArrow, arrowParams);
        coursePicker.setOnClickListener(v -> showCoursePopup());
        card.addView(coursePicker, topLp(-1, dp(72), 12));
        plannerBaseline = text("", 12, muted, Typeface.NORMAL);
        card.addView(plannerBaseline, topLp(-1, -2, 10));
        odNotice = text("", 12, muted, Typeface.NORMAL);
        card.addView(odNotice, topLp(-1, -2, 12));
        odOptions = column();
        String[] policies = {"Recorded", "OD present", "OD excluded"};
        for (int i = 0; i < policies.length; i++) {
            final int policy = i;
            TextView chip = filterChip(policies[i]);
            chip.setTextSize(12);
            chip.setPadding(dp(14), 0, dp(14), 0);
            chip.setMinHeight(dp(48));
            chip.setOnClickListener(v -> { odMode = policy; plannerFeedback(v); });
            LinearLayout.LayoutParams chipParams = lp(-1, dp(48));
            chipParams.bottomMargin = dp(6);
            odOptions.addView(chip, chipParams);
            odChips[i] = chip;
        }
        card.addView(odOptions, topLp(-1, -2, 8));

        LinearLayout mode = row();
        TextView miss = filterChip("If I miss");
        TextView attend = filterChip("If I attend");
        plannerMissView = miss;
        plannerAttendView = attend;
        miss.setContentDescription("Project attendance if I miss the next classes");
        attend.setContentDescription("Project attendance if I attend the next classes");
        miss.setGravity(Gravity.CENTER);
        attend.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams missParams = new LinearLayout.LayoutParams(0, dp(48), 1);
        missParams.rightMargin = dp(5);
        LinearLayout.LayoutParams attendParams = new LinearLayout.LayoutParams(0, dp(48), 1);
        attendParams.leftMargin = dp(5);
        mode.addView(miss, missParams);
        mode.addView(attend, attendParams);
        card.addView(label("Plan a scenario", 13, muted, Typeface.BOLD), topLp(-1, -2, 14));
        card.addView(mode, topLp(-1, -2, 10));
        miss.setOnClickListener(v -> { plannerAttend = false; plannerFeedback(v); });
        attend.setOnClickListener(v -> { plannerAttend = true; plannerFeedback(v); });

        LinearLayout stepper = row();
        stepper.setGravity(Gravity.CENTER_VERTICAL);
        Button minus = compactButton("−", soft, ink);
        Button plus = compactButton("+", hero, heroText);
        minus.setContentDescription("Decrease projected classes");
        plus.setContentDescription("Increase projected classes");
        plannerCountView = text("1 class", 20, ink, Typeface.NORMAL);
        plannerCountView.setGravity(Gravity.CENTER);
        stepper.addView(minus, lp(dp(48), dp(48)));
        stepper.addView(plannerCountView, new LinearLayout.LayoutParams(0, dp(48), 1));
        stepper.addView(plus, lp(dp(48), dp(48)));
        card.addView(stepper, topLp(-1, -2, 16));
        minus.setOnClickListener(v -> { plannerCount = Math.max(1, plannerCount - 1); plannerFeedback(v); });
        plus.setOnClickListener(v -> { plannerCount = Math.min(50, plannerCount + 1); plannerFeedback(v); });

        LinearLayout result = row();
        result.setGravity(Gravity.CENTER_VERTICAL);
        result.setPadding(dp(13), dp(12), dp(13), dp(12));
        result.setBackground(shape(soft, 12, 0));
        LinearLayout projection = column();
        projection.addView(text("Projected attendance", 10, muted, Typeface.NORMAL));
        projectionView = text("—", 36, ink, Typeface.BOLD);
        projection.addView(projectionView, topLp(-1, -2, 3));
        result.addView(projection, new LinearLayout.LayoutParams(0, -2, 1));
        projectionStatusView = text("Counts unavailable", 9, muted, Typeface.NORMAL);
        projectionStatusView.setPadding(dp(8), dp(7), dp(8), dp(7));
        projectionStatusView.setBackground(shape(surface, 100, 0));
        result.addView(projectionStatusView);
        card.addView(result, topLp(-1, -2, 14));
        plannerMeter = new PlannerMeter(getContext());
        card.addView(plannerMeter, topLp(-1, 28, 12));
        plannerFeedback = text("Choose a course to explore your options.", 12, muted, Typeface.NORMAL);
        card.addView(plannerFeedback, topLp(-1, -2, 6));
        
        return card;
    }

    private void buildNavigation(LinearLayout shell) {
        LinearLayout holder = new LinearLayout(getContext());
        holder.setGravity(Gravity.CENTER);
        holder.setPadding(dp(16), dp(8), dp(16), dp(8));
        shell.addView(holder, lp(-1, -2));
        nav.setPadding(dp(4), dp(3), dp(4), dp(3));
        nav.setGravity(Gravity.CENTER);
        navBackground = new NavBackgroundDrawable();
        nav.setBackground(navBackground);
        holder.addView(nav, lp(-1, -2));
        int[] icons = {R.drawable.ic_nav_home, R.drawable.ic_nav_courses, R.drawable.ic_nav_week,
                R.drawable.ic_nav_lab, R.drawable.ic_nav_academics};
        String[] labels = {"Home", "Courses", "Week", "What if?", "Marks"};
        for (int i = 0; i < labels.length; i++) {
            final int page = i;
            LinearLayout tab = new LinearLayout(getContext());
            tab.setOrientation(LinearLayout.VERTICAL);
            tab.setGravity(Gravity.CENTER);
            tab.setFocusable(true);
            tab.setMinimumHeight(dp(56));
            tab.setPadding(dp(4), dp(6), dp(4), dp(6));
            tab.setOnClickListener(v -> showPage(page));
            setTactileFeedback(tab);
            ImageView icon = new ImageView(getContext());
            icon.setImageResource(icons[i]);
            icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            TextView label = text(labels[i], 11, muted, Typeface.NORMAL);
            label.setSingleLine(true);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            label.setGravity(Gravity.CENTER);
            icon.setVisibility(VISIBLE);
            tab.addView(icon, lp(dp(24), dp(24)));
            tab.addView(label, topLp(-1, -2, 2));
            nav.addView(tab, new LinearLayout.LayoutParams(0, -2, 1));
            navTabs[i] = tab;
            navIcons[i] = icon;
            navItems[i] = label;
        }
        updateNavigation();
    }

    private void showPage(int page) {
        if (page < 0 || page >= pageScrolls.length || page == selectedPage) return;
        ScrollView incoming = pageScrolls[page];
        if (incoming == null) return;

        // Update visibility synchronously so rapid taps cannot hide the newly selected page.
        int direction = page > selectedPage ? 1 : -1;
        selectedPage = page;
        if (homeThemeButton != null) homeThemeButton.setVisibility(page == 0 ? VISIBLE : GONE);
        if (page == 2) updateTimetable();
        for (int i = 0; i < pageScrolls.length; i++) {
            ScrollView candidate = pageScrolls[i];
            if (candidate == null) continue;
            candidate.animate().cancel();
            candidate.setVisibility(i == page ? VISIBLE : GONE);
            candidate.setAlpha(1f);
            candidate.setTranslationX(0f);
            candidate.setTranslationY(0f);
            candidate.setScaleX(1f);
            candidate.setScaleY(1f);
        }
        incoming.bringToFront();
        animatePageArrival(incoming, direction);
        updateNavigation();
    }

    void openWidgetPage(int page) { showPage(page); }

    void openNotificationDestination(String destination) {
        if ("leave_od".equals(destination)) openOdDetails();
        else if ("week".equals(destination)) showPage(2);
        else if ("courses".equals(destination)) showPage(1);
        else showPage(0);
    }

    void animateCurrentPageEntry() {
        if (selectedPage >= 0 && selectedPage < pageScrolls.length && pageScrolls[selectedPage] != null)
            animatePageArrival(pageScrolls[selectedPage], 1);
    }

    private void animatePageArrival(View page, int direction) {
        page.animate().cancel();
        page.setTranslationX(0f);
        page.setTranslationY(0f);
        page.setScaleX(1f);
        page.setScaleY(1f);
        page.setAlpha(1f);
        if (android.os.Build.VERSION.SDK_INT >= 26 && !ValueAnimator.areAnimatorsEnabled()) return;
        page.setTranslationX(direction * dp(40));
        page.setAlpha(.72f);
        page.animate().translationX(0f).alpha(1f).setStartDelay(0).setDuration(280)
                .setInterpolator(new android.view.animation.PathInterpolator(.16f, 1f, .3f, 1f)).start();
    }

    /** Repairs page visibility after window reattachment or a background data refresh. */
    void restoreSelectedPage() {
        if (odDetailsOpen && odDetailsPage != null) {
            for (ScrollView candidate : pageScrolls) if (candidate != null) candidate.setVisibility(GONE);
            nav.setVisibility(GONE);
            odDetailsPage.setVisibility(VISIBLE);
            odDetailsPage.bringToFront();
            return;
        }
        ScrollView selected = pageScrolls[selectedPage];
        if (selected == null) return;
        for (int i = 0; i < pageScrolls.length; i++) {
            ScrollView candidate = pageScrolls[i];
            if (candidate == null) continue;
            candidate.animate().cancel();
            candidate.setVisibility(i == selectedPage ? VISIBLE : GONE);
            candidate.setAlpha(1f);
            candidate.setTranslationX(0f);
            candidate.setTranslationY(0f);
            candidate.setScaleX(1f);
            candidate.setScaleY(1f);
        }
        selected.bringToFront();
        updateNavigation();
    }

    private void openPlannerFor(String courseCode) {
        odMode = 0;
        if (courseCode != null) {
            for (int i = 0; i < state.rows.size(); i++) {
                if (courseCode.equalsIgnoreCase(state.rows.get(i).code)) {
                    plannerCourse = i;
                    break;
                }
            }
        }
        updatePlannerCourses();
        showPage(3);
    }

    private void updateNavigation() {
        nav.setVisibility(odDetailsOpen ? GONE : VISIBLE);
        boolean selectionChanged = false;
        for (int i = 0; i < navItems.length; i++) {
            TextView item = navItems[i];
            boolean selected = i == selectedPage;
            selectionChanged |= navTabs[i].isSelected() != selected;
            item.setTypeface(item.getTypeface(), selected ? Typeface.BOLD : Typeface.NORMAL);
            int selectedColor = heroText;
            item.setTextColor(selected ? selectedColor : muted);
            navIcons[i].setColorFilter(selected ? selectedColor : muted);
            navTabs[i].setBackground(cyberpunk && selected
                    ? new CutCornerDrawable(hero, hero, green, dp(9), dp(2))
                    : null);
            navTabs[i].setSelected(selected);
            if (selectionChanged) {
                navIcons[i].animate().cancel();
                navIcons[i].setTranslationY(0f);
                if (selected && navIcons[i].isShown()) settleFeedback(navIcons[i]);
            }
            navTabs[i].setContentDescription(new String[]{"Home", "Courses", "Week", "What if?", "Marks and academics"}[i]
                    + (selected ? ", selected" : ""));
            item.setContentDescription(new String[]{"Overview", "Courses", "Timetable", "Attendance lab", "KCT marks and academics"}[i]
                    + (selected ? ", selected" : ""));
        }
        if (navBackground != null) navBackground.moveTo(selectedPage, selectionChanged && nav.isShown());
    }

    private void setFilter(boolean focus) {
        belowOnly = focus;
        refreshVisibleRows();
        for (View chip : findTaggedViews("allFilter")) setChipSelected(chip, !focus);
        for (View chip : findTaggedViews("focusFilter")) setChipSelected(chip, focus);
        actions.onFilter(focus);
    }

    private void settleFeedback(View view) {
        view.animate().cancel();
        view.setTranslationY(0f);
        if (android.os.Build.VERSION.SDK_INT >= 26 && !ValueAnimator.areAnimatorsEnabled()) return;
        view.setTranslationY(dp(2));
        view.animate().translationY(0f).setStartDelay(0).setDuration(180)
                .setInterpolator(new DecelerateInterpolator(1.5f)).start();
    }

    private void setChipSelected(View chip, boolean selected) {
        if (chip instanceof TextView) {
            boolean changed = chip.isSelected() != selected;
            ((TextView) chip).setTextColor(selected && cyberpunk ? heroText : selected ? ink : muted);
            chip.setBackground(cyberpunk && selected ? new CutCornerDrawable(hero, hero, green, dp(7), dp(2))
                    : shape(selected ? surface : soft, 9, selected ? border : 0));
            chip.setSelected(selected);
            if (changed && selected && chip.isShown()) settleFeedback(chip);
        }
    }

    private List<View> findTaggedViews(String tag) {
        ArrayList<View> found = new ArrayList<>();
        findTagged(this, tag, found);
        return found;
    }

    private void findTagged(View view, String tag, List<View> found) {
        if (tag.equals(view.getTag())) found.add(view);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) findTagged(group.getChildAt(i), tag, found);
        }
    }

    private void updateSearchFields() {
        setTextWithoutCursorJump(coursesSearch, query);
        if (allCoursesFilter != null) {
            allCoursesFilter.setText("All courses · " + state.courses);
            allCoursesFilter.setContentDescription("Show all " + state.courses + " courses");
        }
        if (focusCoursesFilter != null) {
            focusCoursesFilter.setText("Below target · " + state.below);
            focusCoursesFilter.setContentDescription("Show courses below your " + state.target + "% target");
        }
        for (View chip : findTaggedViews("allFilter")) setChipSelected(chip, !belowOnly);
        for (View chip : findTaggedViews("focusFilter")) setChipSelected(chip, belowOnly);
    }

    private String courseHeading(Course course) {
        String title = course.name == null ? "" : course.name.trim();
        String code = course.code == null ? "" : course.code.trim();
        String heading = title.equalsIgnoreCase(code) || code.isEmpty() ? title : title + "  ·  " + code;
        if (isLabCourse(code)) heading += "  ·  LAB";
        return heading;
    }

    private String courseSubtitle(Course course) {
        String code = course.code == null ? "" : course.code.trim();
        boolean duplicateCode = course.name != null && course.name.trim().equalsIgnoreCase(code);
        String identifier = duplicateCode || code.isEmpty() ? "" : code + "  ·  ";
        String od = course.odCount > 0 ? "  ·  OD " + course.odCount : "";
        return course.countsKnown ? identifier + course.attended + " / " + course.total + " classes" + od
                : identifier + "Class counts unavailable";
    }

    private void setTextWithoutCursorJump(EditText view, String value) {
        if (view != null && !view.getText().toString().equals(value)) view.setText(value);
    }

    private void rebuildCourseRows() {
        int[] scrollY = {pageScrolls[0].getScrollY(), pageScrolls[1].getScrollY()};
        courseViews.clear();
        courseGroupViews.clear();
        for (int page = 0; page < courseLists.length; page++) {
            LinearLayout list = courseLists[page];
            list.removeAllViews();
            if (page == 0) rebuildOverviewRows(list);
            else rebuildGroupedCourseRows(list);
            final int restore = scrollY[page];
            final ScrollView scroll = pageScrolls[page];
            scroll.post(() -> scroll.scrollTo(0, restore));
        }
    }

    private void rebuildOverviewRows(LinearLayout list) {
        int riskCount = 0;
        for (CourseGroup group : groupedCourses()) {
            if (riskCount >= 2) break;
            boolean lectureAtRisk = group.lecture.below;
            boolean hasAtRiskLab = false;
            for (Course lab : group.labs) if (lab.below) { hasAtRiskLab = true; break; }
            if (!lectureAtRisk && !hasAtRiskLab) continue;
            if (lectureAtRisk) {
                addCourseRow(list, group.lecture, null, 6);
                riskCount++;
            }
            for (Course lab : group.labs) {
                if (lab.below && riskCount < 2) {
                    addCourseRow(list, lab, group.lecture, lectureAtRisk ? 4 : 6);
                    riskCount++;
                }
            }
        }
    }

    private void rebuildGroupedCourseRows(LinearLayout list) {
        for (CourseGroup group : groupedCourses()) {
            CourseGroupRow item = new CourseGroupRow(group);
            courseGroupViews.add(item);
            list.addView(item.root, topLp(-1, -2, 8));
        }
    }

    private void addCourseRow(LinearLayout list, Course course, Course pairedLecture, int topMargin) {
        CourseRow item = new CourseRow(course, pairedLecture, list == courseLists[0]);
        courseViews.add(item);
        LinearLayout.LayoutParams params = topLp(-1, -2, topMargin);
        list.addView(item.root, params);
    }

    private List<CourseGroup> groupedCourses() {
        Map<String, CourseGroup> groupsByLecture = new HashMap<>();
        for (Course course : state.rows) if (!isLabCourse(course.code)) {
            groupsByLecture.put(course.code.trim().toUpperCase(Locale.ROOT), new CourseGroup(course));
        }
        ArrayList<CourseGroup> groups = new ArrayList<>(groupsByLecture.values());
        for (Course course : state.rows) {
            if (!isLabCourse(course.code)) continue;
            String lectureCode = course.code.trim().substring(0, course.code.trim().length() - 1).toUpperCase(Locale.ROOT);
            CourseGroup group = groupsByLecture.get(lectureCode);
            if (group == null) groups.add(new CourseGroup(course));
            else group.labs.add(course);
        }
        for (CourseGroup group : groups) Collections.sort(group.labs, (a, b) -> Float.compare(a.percent, b.percent));
        Collections.sort(groups, (a, b) -> Float.compare(a.priority(), b.priority()));
        return groups;
    }

    private void refreshVisibleRows() {
        boolean anyVisible = false;
        for (CourseRow row : courseViews) {
            boolean visible = row.overviewPreview || ((!belowOnly || row.course.below) && matches(row.course, query));
            row.root.setVisibility(visible ? VISIBLE : GONE);
        }
        for (CourseGroupRow row : courseGroupViews) {
            boolean visible = row.matches();
            row.root.setVisibility(visible ? VISIBLE : GONE);
            anyVisible |= visible;
        }
        if (coursesEmptyView != null) {
            coursesEmptyView.setVisibility(anyVisible ? GONE : VISIBLE);
            if (state.rows.isEmpty()) {
                coursesEmptyView.setText(state.attendanceLoaded
                        ? "MyCamu returned no course attendance. Tap here to sync again."
                        : "Your courses appear here after your first MyCamu sync. Tap to connect.");
                coursesEmptyView.setContentDescription("Sync MyCamu to load course attendance");
                coursesEmptyView.setClickable(true);
                coursesEmptyView.setOnClickListener(v -> {
                    if (state.sessionExpired) actions.onPortal();
                    else actions.onSync();
                });
            } else {
                coursesEmptyView.setText(belowOnly && query.trim().isEmpty()
                        ? "All courses are on track with your attendance target."
                        : "No courses match this search and filter.");
                coursesEmptyView.setContentDescription(coursesEmptyView.getText());
                coursesEmptyView.setClickable(false);
                coursesEmptyView.setOnClickListener(null);
            }
        }
    }

    private boolean matches(Course course, String needle) {
        if (needle == null || needle.trim().isEmpty()) return true;
        String q = needle.trim().toLowerCase(Locale.ROOT);
        return course.name.toLowerCase(Locale.ROOT).contains(q) || course.code.toLowerCase(Locale.ROOT).contains(q);
    }

    private boolean isLabCourse(String code) {
        return code != null && code.trim().toUpperCase(Locale.ROOT).endsWith("L");
    }

    private final class LabBadgeSpan extends ReplacementSpan {
        private final Paint badgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float badgeTextSize;

        @Override public int getSize(Paint textPaint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
            badgeTextSize = textPaint.getTextSize() * .78f;
            badgePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            badgePaint.setTextSize(badgeTextSize);
            return (int) Math.ceil(badgePaint.measureText("LAB") + dp(12));
        }

        @Override public void draw(Canvas canvas, CharSequence text, int start, int end, float x,
                                   int top, int y, int bottom, Paint textPaint) {
            badgePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            badgePaint.setTextSize(badgeTextSize > 0 ? badgeTextSize : textPaint.getTextSize() * .78f);
            float width = badgePaint.measureText("LAB") + dp(12);
            Paint.FontMetrics metrics = badgePaint.getFontMetrics();
            float centerY = y + (textPaint.ascent() + textPaint.descent()) / 2f;
            float height = metrics.descent - metrics.ascent + dp(5);
            RectF pill = new RectF(x, centerY - height / 2f, x + width, centerY + height / 2f);
            badgePaint.setColor(labBadgeBackground);
            canvas.drawRoundRect(pill, dp(7), dp(7), badgePaint);
            badgePaint.setColor(labBadgeForeground);
            canvas.drawText("LAB", x + dp(6), centerY - (metrics.ascent + metrics.descent) / 2f, badgePaint);
        }
    }

    private void updatePlannerCourses() {
        if (coursePicker == null || coursePickerText == null) return;
        if (state.rows.isEmpty()) coursePickerText.setText("No courses available");
        else coursePickerText.setText(courseLabel(state.rows.get(Math.min(plannerCourse, state.rows.size() - 1))));
        coursePicker.setEnabled(!state.rows.isEmpty());
        coursePicker.setAlpha(state.rows.isEmpty() ? .6f : 1f);
        updatePlanner();
    }

    private String courseLabel(Course course) {
        return course.name + (isLabCourse(course.code) ? "  ·  LAB" : "")
                + (course.name.equalsIgnoreCase(course.code) ? "" : "  —  " + course.code);
    }

    private void showCoursePopup() {
        if (state.rows.isEmpty() || coursePicker == null) return;
        if (coursePopup != null && coursePopup.isShowing()) {
            coursePopup.dismiss();
            return;
        }
        LinearLayout entries = column();
        entries.setPadding(dp(8), dp(8), dp(8), dp(8));
        ScrollView list = new ScrollView(getContext());
        list.setFillViewport(false);
        list.setVerticalScrollBarEnabled(false);
        list.addView(entries, new ScrollView.LayoutParams(-1, -2));
        for (int i = 0; i < state.rows.size(); i++) {
            final int index = i;
            Course course = state.rows.get(i);
            LinearLayout item = column();
            item.setPadding(dp(14), dp(10), dp(14), dp(10));
            item.setBackground(shape(i == plannerCourse ? soft : surface, 10, 0));
            LinearLayout title = row();
            TextView name = text(course.name, 14, ink, Typeface.BOLD);
            name.setMaxLines(2);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            title.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
            if (isLabCourse(course.code)) {
                TextView badge = text("LAB", 10, green, Typeface.BOLD);
                badge.setPadding(dp(8), dp(4), dp(8), dp(4));
                badge.setBackground(shape(soft, 20, 0));
                title.addView(badge);
            }
            item.addView(title);
            TextView code = text(course.code, 10, muted, Typeface.NORMAL);
            item.addView(code, topLp(-1, -2, 3));
            item.setOnClickListener(v -> {
                plannerCourse = index;
                odMode = 0;
                updatePlannerCourses();
                if (coursePopup != null) coursePopup.dismiss();
            });
            entries.addView(item, topLp(-1, -2, 3));
        }
        Rect visible = new Rect();
        View root = getRootView();
        root.getWindowVisibleDisplayFrame(visible);
        int width = Math.min(getResources().getDisplayMetrics().widthPixels - dp(32), dp(440));
        int height = Math.min((int) (getResources().getDisplayMetrics().heightPixels * .62f),
                Math.max(dp(120), state.rows.size() * dp(72) + dp(16)));
        coursePopup = new PopupWindow(list, width, height, true);
        coursePopup.setBackgroundDrawable(shape(surface, 16, border));
        coursePopup.setElevation(dp(12));
        coursePopup.setOutsideTouchable(true);
        int[] anchor = new int[2];
        coursePicker.getLocationOnScreen(anchor);
        int below = visible.bottom - (anchor[1] + coursePicker.getHeight());
        int y = below >= height ? anchor[1] + coursePicker.getHeight() + dp(4) : Math.max(visible.top, anchor[1] - height - dp(4));
        coursePopup.showAtLocation(root, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, y);
    }

    private void plannerFeedback(View control) {
        updatePlanner();
        control.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
        projectionView.animate().cancel();
        projectionView.setTranslationY(0);
        if (android.os.Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled()) {
            projectionView.setTranslationY(dp(3));
            projectionView.animate().translationY(0).setDuration(160)
                    .setInterpolator(new DecelerateInterpolator()).start();
        }
        projectionView.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    }

    private void updateDecision() {
        if (decisionCourse == null) return;
        decisionAction.setOnClickListener(v -> {
            if (state.sessionExpired) actions.onPortal();
            else actions.onSync();
        });
        ScheduleItem next = nextScheduledClass(System.currentTimeMillis());
        long nextStartsAt = next == null ? -1 : next.startsAt;
        if (nextStartsAt != decisionStartsAt) attendForecastSelected = true;
        decisionStartsAt = nextStartsAt;
        Course course = null;
        if (next != null && !next.code.isEmpty()) {
            String code = normalizeCourseCode(next.code);
            for (Course row : state.rows) if (code.equals(normalizeCourseCode(row.code))) { course = row; break; }
        }
        decisionCode = course == null ? "" : course.code;
        nextClassHasApprovedOd = next != null && state.approvedOdClasses.contains(odClassKey(next.code, next.startsAt));
        hasForecast = course != null && course.countsKnown && !nextClassHasApprovedOd;
        targetHintView.setVisibility(next == null ? GONE : VISIBLE);
        boolean hasCurrentCounts = course != null && course.countsKnown;
        decisionFigures.setVisibility(hasCurrentCounts ? VISIBLE : GONE);
        decisionRulerLegend.setVisibility(hasCurrentCounts ? VISIBLE : GONE);
        attendForecastTile.setVisibility(hasForecast ? VISIBLE : GONE);
        skipForecastTile.setVisibility(hasForecast ? VISIBLE : GONE);
        forecastStatusView.setVisibility(nextClassHasApprovedOd || hasForecast ? VISIBLE : GONE);
        attendanceRail.setVisibility(hasCurrentCounts ? VISIBLE : GONE);
        if (next == null) {
            decisionCourse.setText(state.timetableLoaded && state.timetable.isEmpty()
                    ? "No classes scheduled!" : state.timetableLoaded ? "No upcoming class" : "Timetable not synced");
            decisionLabel.setText(state.timetableLoaded && state.timetable.isEmpty()
                    ? "MyCamu has no classes scheduled for this week."
                    : state.timetableLoaded ? "Your synced timetable has no future class yet."
                    : "Sync MyCamu to see your next class.");
            attendancePercentView.setText("—");
            heroMessageView.setText("— / — attended");
            captionView.setText("course attendance");
            targetHintView.setText("When a class is scheduled, compare attending with skipping before you decide.");
            forecastStatusView.setText("");
            decisionAction.setText(state.sessionExpired ? "Reconnect MyCamu  →"
                    : state.timetableLoaded ? "Refresh timetable  →" : "Sync timetable  →");
            decisionAction.setContentDescription(state.sessionExpired ? "Reconnect to MyCamu"
                    : "Refresh this week’s timetable and attendance from MyCamu");
            decisionAction.setVisibility(VISIBLE);
        } else if (course == null || (!course.countsKnown && !nextClassHasApprovedOd)) {
            decisionCourse.setText(next.name.isEmpty() ? next.code : next.name);
            decisionLabel.setText(next.date + " · " + next.time + " · attendance counts unavailable");
            attendancePercentView.setText("—");
            heroMessageView.setText("— / — attended");
            captionView.setText(next.code.isEmpty() ? "course not matched" : next.code);
            targetHintView.setText("Sync attendance counts to calculate both outcomes.");
            forecastStatusView.setText("");
            decisionAction.setText("Sync attendance  →");
            decisionAction.setContentDescription("Refresh attendance from MyCamu");
            decisionAction.setVisibility(VISIBLE);
        } else {
            currentCoursePercent = course.percent;
            attendancePercentView.setText(course.countsKnown ? Math.round(course.percent) + "%" : "—");
            heroMessageView.setText(course.countsKnown ? course.attended + " / " + course.total + " attended" : "counts unavailable");
            captionView.setText(course.code);
            if (nextClassHasApprovedOd) {
                decisionCourse.setText(course.name);
                decisionLabel.setText(next.date + " · " + next.time + " · approved OD");
                targetHintView.setText("This approved OD covers the scheduled class; it is not counted as a skip.");
                forecastStatusView.setText("✓  APPROVED OD · NO ABSENCE IMPACT");
                forecastStatusView.setTextColor(green);
                decisionAction.setText("View OD details  →");
                decisionAction.setOnClickListener(v -> openOdDetails());
            } else {
                attendForecast = 100f * (course.attended + 1) / (course.total + 1);
                skipForecast = 100f * course.attended / (course.total + 1);
                decisionCourse.setText(course.name);
                decisionLabel.setText(next.date + " · " + next.time + " · " + course.code);
                attendForecastView.setText(Math.round(attendForecast) + "%");
                skipForecastView.setText(Math.round(skipForecast) + "%");
                ((TextView) attendForecastTile.getChildAt(2)).setText(targetStatus(attendForecast));
                ((TextView) skipForecastTile.getChildAt(2)).setText(targetStatus(skipForecast));
                forecastStatusView.setText(attendForecastSelected
                        ? "Attend → " + targetStatus(attendForecast)
                        : "Skip → " + targetStatus(skipForecast));
                forecastStatusView.setTextColor(muted);
                updateForecastSelection(false);
                targetHintView.setText("Both estimates use recorded counts and your " + state.target + "% target.");
                decisionAction.setText("Plan this course  →");
                decisionAction.setOnClickListener(v -> openPlannerFor(decisionCode));
            }
            decisionAction.setVisibility(VISIBLE);
        }
        attendanceRail.invalidate();
        classStartHandler.removeCallbacks(classStartRefresh);
        if (next != null) classStartHandler.postDelayed(classStartRefresh,
                Math.max(250L, next.startsAt - System.currentTimeMillis() + 100L));
    }

    @Override protected void onDetachedFromWindow() {
        classStartHandler.removeCallbacks(classStartRefresh);
        super.onDetachedFromWindow();
    }

    private String targetStatus(float percent) {
        return (percent + 0.0001f >= state.target ? "Meets " : "Below ") + state.target + "% target";
    }

    private String normalizeCourseCode(String code) {
        return code == null ? "" : code.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private String odClassKey(String code, long startsAt) {
        return normalizeCourseCode(code) + ":" + (startsAt / 60_000L);
    }

    private void updatePlanner() {
        if (plannerCountView == null) return;
        plannerCountView.setText(plannerCount + (plannerCount == 1 ? " class" : " classes"));
        setChipSelected(plannerMissView, !plannerAttend);
        setChipSelected(plannerAttendView, plannerAttend);
        Course course = state.rows.isEmpty() ? null : state.rows.get(Math.min(plannerCourse, state.rows.size() - 1));
        if (plannerBaseline != null) plannerBaseline.setText(course == null ? "" : course.percentLabel
                + " now · " + course.counts + (course.targetCount.isEmpty() ? "" : "\n" + course.targetCount)
                + odCoverageSummary(course));
        if (course == null || !course.countsKnown || course.total + plannerCount <= 0) {
            odOptions.setVisibility(GONE);
            odNotice.setText(course != null && course.odCount > 0
                    ? course.odCount + (course.odCount == 1 ? " OD class is mapped" : " OD classes are mapped")
                        + ". Sync attendance counts before applying an OD calculation."
                    : "Approved OD appears after your class log syncs.");
            plannerMeter.setVisibility(GONE);
            plannerFeedback.setText("Sync your class counts to explore your options.");
            projectionView.setText("—");
            projectionView.setTextColor(ink);
            projectionStatusView.setText("Counts unavailable");
            projectionStatusView.setTextColor(muted);
            return;
        }
        boolean canPreviewOd = course.odReconciled && course.odCredit > 0 && course.odCredit <= course.total - course.attended;
        if (!canPreviewOd) odMode = 0;
        odOptions.setVisibility(canPreviewOd ? VISIBLE : GONE);
        for (int i = 0; i < odChips.length; i++) setChipSelected(odChips[i], i == odMode);
        odNotice.setText(course.odCredit == 0 && course.odCount > 0 && course.odReconciled
                ? course.odCount + (course.odCount == 1 ? " approved OD class is" : " approved OD classes are")
                    + " already marked present and included in the recorded counts."
                : course.odCredit == 0 && course.odCount > 0
                ? course.odCount + (course.odCount == 1 ? " approved OD class is" : " approved OD classes are")
                    + " mapped to this course. Sync the complete class log before changing its calculation."
                : course.odCredit == 0 ? "No approved OD awaiting credit in the loaded class log."
                : !canPreviewOd ? course.odCount + " OD classes mapped; " + course.odCredit
                    + " currently marked absent. Full counts must match before previewing credit."
                : odMode == 0 ? course.odCount + " approved OD classes mapped · " + course.odCredit
                    + (course.odCredit == 1 ? " marked absent. Choose your college’s rule to preview."
                    : " marked absent. Choose your college’s rule to preview.")
                : "OD preview · " + course.odCredit + (odMode == 1 ? " classes counted as present." : " classes excluded from total.")
                + " Recorded attendance is unchanged.");
        Attendance baseline = new Attendance(course.code, course.name, course.attended, course.total);
        if (odMode != 0) {
            baseline = baseline.withApprovedOd(course.odCredit, odMode == 2);
            plannerBaseline.setText("Recorded: " + course.attended + " / " + course.total
                    + "\nOD preview: " + baseline.attended + " / " + baseline.total
                    + " · " + (baseline.percentage() < state.target
                    ? (state.target >= 100 ? "100% cannot be recovered" : "Attend " + baseline.periodsToRecover(state.target))
                    : "Can miss " + baseline.safeAbsences(state.target)) + odCoverageSummary(course));
        }
        double projected = 100.0 * (baseline.attended + (plannerAttend ? plannerCount : 0)) / (baseline.total + plannerCount);
        plannerMeter.setVisibility(VISIBLE);
        plannerMeter.update((float) projected, course.code);
        Attendance forecast = new Attendance(course.code, course.name,
                baseline.attended + (plannerAttend ? plannerCount : 0), baseline.total + plannerCount);
        plannerFeedback.setText(projected >= state.target
                ? "This plan keeps you at your " + state.target + "% target or above."
                : state.target >= 100 ? "This plan stays below your 100% target."
                : "After this plan, attend " + forecast.periodsToRecover(state.target)
                    + " consecutive classes to reach " + state.target + "%.");
        projectionView.setText(String.format(Locale.getDefault(), "%.1f%%", projected));
        projectionView.setTextColor(projected < state.target ? red : ink);
        projectionStatusView.setText(String.format(Locale.getDefault(), "%+.1f pts\n%s",
                projected - course.percent, projected >= state.target ? "Above target" : "Below target"));
        projectionStatusView.setTextColor(projected < state.target ? red : muted);
    }

    private String odCoverageSummary(Course course) {
        return course.odCount == 0 ? "" : "\nOD coverage: " + course.odCount + " mapped · "
                + course.odCredit + " currently marked absent";
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private LinearLayout column() {
        LinearLayout box = new LinearLayout(getContext());
        box.setOrientation(LinearLayout.VERTICAL);
        return box;
    }

    private LinearLayout card() {
        LinearLayout card = column();
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(industrialSurface(surface, 20, border));
        return card;
    }

    private TextView text(String value, float size, int color, int style) {
        TextView view = new TextView(getContext());
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(uiTypeface, style);
        return view;
    }

    private TextView label(String value, float size, int color, int style) {
        TextView view = text(value, size, color, style);
        view.setTypeface(cyberpunk ? displayTypeface : uiTypeface, Typeface.BOLD);
        view.setLetterSpacing(.055f);
        return view;
    }

    private Button compactButton(String value, int background, int foreground) {
        Button button = new Button(getContext());
        button.setText(value);
        button.setTextSize(20);
        button.setTypeface(uiTypeface, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setIncludeFontPadding(false);
        button.setTextColor(foreground);
        button.setAllCaps(false);
        button.setPadding(0, 0, 0, 0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setBackground(shape(background, 14, 0));
        setTactileFeedback(button);
        return button;
    }

    private Button roundedButton(String value, int background, int foreground) {
        Button button = compactButton(value, background, foreground);
        button.setTextSize(13);
        button.setTypeface(uiTypeface, Typeface.BOLD);
        button.setMinHeight(dp(48));
        return button;
    }

    private void addTop(LinearLayout parent, View child, int marginDp) { parent.addView(child, topLp(-1, -2, marginDp)); }
    private void addBottom(LinearLayout parent, int paddingDp) { parent.setPadding(parent.getPaddingLeft(), parent.getPaddingTop(), parent.getPaddingRight(), dp(paddingDp)); }
    private LinearLayout.LayoutParams lp(int width, int height) { return new LinearLayout.LayoutParams(width, height); }
    private LinearLayout.LayoutParams topLp(int width, int height, int margin) {
        LinearLayout.LayoutParams p = lp(width, height); p.topMargin = dp(margin); return p;
    }
    private View space(int width, int height) { View view = new View(getContext()); view.setLayoutParams(lp(width, height)); return view; }
    private int dp(float value) { return (int) (value * getResources().getDisplayMetrics().density + .5f); }
    private GradientDrawable shape(int color, int radius, int stroke) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius));
        if (stroke != 0) d.setStroke(dp(1), stroke); return d;
    }

    private int statusTint(int color) {
        return Color.argb(0x24, Color.red(color), Color.green(color), Color.blue(color));
    }

    private Drawable industrialSurface(int background, int radius, int stroke) {
        if (!cyberpunk) return shape(background, radius, stroke);
        return new CutCornerDrawable(background, border, amber, dp(9), dp(2));
    }

    private void setTactileFeedback(View view) {
        view.setOnTouchListener((target, event) -> {
            if (!target.isEnabled() || (android.os.Build.VERSION.SDK_INT >= 26 && !ValueAnimator.areAnimatorsEnabled())) return false;
            int action = event.getActionMasked();
            if (action == android.view.MotionEvent.ACTION_DOWN) {
                target.animate().cancel();
                target.animate().scaleX(.98f).scaleY(.98f).setStartDelay(0).setDuration(70)
                        .setInterpolator(new DecelerateInterpolator()).start();
            } else if (action == android.view.MotionEvent.ACTION_UP
                    || action == android.view.MotionEvent.ACTION_CANCEL) {
                target.animate().scaleX(1f).scaleY(1f).setStartDelay(0).setDuration(140)
                        .setInterpolator(new DecelerateInterpolator(2f)).start();
            }
            return false;
        });
    }

    private final class CutCornerDrawable extends Drawable {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float cut, offset;

        CutCornerDrawable(int background, int borderColor, int shadowColor, float cut, float offset) {
            fill.setColor(background);
            edge.setColor(borderColor);
            edge.setStyle(Paint.Style.STROKE);
            edge.setStrokeWidth(dp(1));
            shadow.setColor(shadowColor);
            this.cut = cut;
            this.offset = offset;
        }

        private Path polygon(float left, float top, float right, float bottom) {
            Path path = new Path();
            path.moveTo(left, top);
            path.lineTo(right - cut, top);
            path.lineTo(right, top + cut);
            path.lineTo(right, bottom);
            path.lineTo(left + cut, bottom);
            path.lineTo(left, bottom - cut);
            path.close();
            return path;
        }

        @Override public void draw(Canvas canvas) {
            android.graphics.Rect bounds = getBounds();
            Path shape = polygon(bounds.left, bounds.top, bounds.right, bounds.bottom);
            canvas.drawPath(shape, shadow);
            Path face = polygon(bounds.left, bounds.top, Math.max(bounds.left, bounds.right - offset),
                    Math.max(bounds.top, bounds.bottom - offset));
            canvas.drawPath(face, fill);
            canvas.drawPath(face, edge);
            // Small registration marks sit in the border gutter, away from content.
            edge.setColor(green);
            canvas.drawLine(bounds.left + dp(1), bounds.top + dp(1),
                    bounds.left + dp(18), bounds.top + dp(1), edge);
            canvas.drawLine(bounds.left + dp(1), bounds.top + dp(1),
                    bounds.left + dp(1), bounds.top + dp(7), edge);
            edge.setColor(border);
        }

        @Override public void setAlpha(int alpha) { fill.setAlpha(alpha); edge.setAlpha(alpha); shadow.setAlpha(alpha); invalidateSelf(); }
        @Override public void setColorFilter(android.graphics.ColorFilter filter) { fill.setColorFilter(filter); edge.setColorFilter(filter); shadow.setColorFilter(filter); invalidateSelf(); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    private final class ScanlineDrawable extends Drawable {
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int spacing = dp(6);
        private final Paint city = new Paint(Paint.ANTI_ALIAS_FLAG);

        ScanlineDrawable() {
            Bitmap tile = Bitmap.createBitmap(1, Math.max(2, spacing), Bitmap.Config.ARGB_8888);
            tile.setPixel(0, 0, Color.argb(6, Color.red(ink), Color.green(ink), Color.blue(ink)));
            line.setShader(new BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
            line.setFilterBitmap(false);
        }

        @Override public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            canvas.drawColor(canvasColor());
            canvas.drawRect(bounds, line);
            // A quiet architectural silhouette, drawn once per frame without animators.
            city.setColor(Color.argb(15, Color.red(green), Color.green(green), Color.blue(green)));
            float unit = Math.max(dp(18), bounds.width() / 18f);
            for (int i = 0; i < 18; i++) {
                float left = bounds.left + i * unit;
                float height = dp(35 + ((i * 37 + 19) % 90));
                canvas.drawRect(left, bounds.bottom - height, left + unit * .72f, bounds.bottom, city);
            }
            city.setColor(Color.argb(28, Color.red(green), Color.green(green), Color.blue(green)));
            city.setStrokeWidth(dp(1));
            canvas.drawLine(bounds.left + dp(8), bounds.top + dp(80),
                    bounds.left + dp(8), bounds.bottom - dp(16), city);
            for (int y = bounds.top + dp(80); y < bounds.bottom; y += dp(64))
                canvas.drawLine(bounds.left + dp(8), y, bounds.left + dp(12), y, city);
        }

        private int canvasColor() { return DashboardView.this.canvas; }
        @Override public void setAlpha(int alpha) { line.setAlpha(alpha); invalidateSelf(); }
        @Override public void setColorFilter(android.graphics.ColorFilter filter) { line.setColorFilter(filter); invalidateSelf(); }
        @Override public int getOpacity() { return PixelFormat.OPAQUE; }
    }

    private final class NavBackgroundDrawable extends Drawable {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint active = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF outer = new RectF();
        private final RectF selected = new RectF();
        private float position;

        NavBackgroundDrawable() {
            fill.setColor(surface);
            outline.setColor(border);
            outline.setStyle(Paint.Style.STROKE);
            outline.setStrokeWidth(dp(1));
            active.setColor(hero);
        }

        void moveTo(int page, boolean animate) {
            if (navIndicatorAnimator != null) {
                navIndicatorAnimator.cancel();
                navIndicatorAnimator = null;
            }
            if (!animate || android.os.Build.VERSION.SDK_INT >= 26 && !ValueAnimator.areAnimatorsEnabled()) {
                position = page;
                invalidateSelf();
                return;
            }
            navIndicatorAnimator = ValueAnimator.ofFloat(position, page);
            navIndicatorAnimator.setDuration(300);
            navIndicatorAnimator.setInterpolator(new android.view.animation.PathInterpolator(.2f, 0f, .15f, 1f));
            navIndicatorAnimator.addUpdateListener(value -> {
                position = (float) value.getAnimatedValue();
                invalidateSelf();
            });
            navIndicatorAnimator.start();
        }

        @Override public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            outer.set(bounds.left + dp(.5f), bounds.top + dp(.5f), bounds.right - dp(.5f), bounds.bottom - dp(.5f));
            canvas.drawRoundRect(outer, dp(21), dp(21), fill);
            canvas.drawRoundRect(outer, dp(21), dp(21), outline);
            if (cyberpunk || bounds.width() == 0) return;
            float inset = dp(4);
            float cellWidth = (bounds.width() - 2f * inset) / navTabs.length;
            float left = bounds.left + inset + cellWidth * position;
            selected.set(left, bounds.top + dp(3), left + cellWidth, bounds.bottom - dp(3));
            canvas.drawRoundRect(selected, dp(16), dp(16), active);
        }

        @Override public void setAlpha(int alpha) { fill.setAlpha(alpha); outline.setAlpha(alpha); active.setAlpha(alpha); invalidateSelf(); }
        @Override public void setColorFilter(android.graphics.ColorFilter filter) {
            fill.setColorFilter(filter); outline.setColorFilter(filter); active.setColorFilter(filter); invalidateSelf();
        }
        @Override public int getOpacity() { return PixelFormat.OPAQUE; }
    }

    private final class SyncGlyphView extends View {
        private static final int MORPH_STEPS = 36;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF ring = new RectF();
        private final Path morphPath = new Path();
        private final android.animation.ArgbEvaluator colorEvaluator = new android.animation.ArgbEvaluator();
        private final float[] loadingPoints = new float[MORPH_STEPS * 2];
        private final float[] tickPoints = new float[MORPH_STEPS * 2];
        private ValueAnimator spin;
        private ValueAnimator markAnimator;
        private float rotation;
        private float markProgress;
        private float morphStartRotation, morphStartSweep;
        private boolean morphFromSpinner, morphPathsReady;
        private int state = 2; // idle, loading, success, failure

        SyncGlyphView(Context context) {
            super(context);
            setLayerType(View.LAYER_TYPE_HARDWARE, null);
        }

        void show(boolean loading, boolean failed, boolean succeeded) {
            int next = loading ? 0 : failed ? -1 : succeeded ? 1 : 2;
            if (state == next) return;
            boolean wasLoading = state == 0;
            float lastRotation = rotation;
            float lastSweep = 170f + 100f * (float) Math.sin(Math.toRadians(lastRotation));
            state = next;
            if (spin != null) {
                spin.cancel();
                spin = null;
            }
            if (markAnimator != null) {
                markAnimator.cancel();
                markAnimator = null;
            }
            animate().cancel();
            setScaleX(1f); setScaleY(1f); setAlpha(1f);
            boolean motionEnabled = android.os.Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled();
            if (loading && motionEnabled) {
                morphFromSpinner = false;
                spin = ValueAnimator.ofFloat(rotation, rotation + 360f);
                spin.setDuration(1200);
                spin.setRepeatCount(ValueAnimator.INFINITE);
                spin.setInterpolator(new LinearInterpolator());
                spin.addUpdateListener(value -> { rotation = (float) value.getAnimatedValue(); invalidate(); });
                spin.start();
            } else {
                if (next == 1 || next == -1) {
                    markProgress = motionEnabled ? 0f : 1f;
                    if (motionEnabled) {
                        morphFromSpinner = next == 1 && wasLoading;
                        morphPathsReady = false;
                        morphStartRotation = lastRotation;
                        morphStartSweep = lastSweep;
                        if (!morphFromSpinner) rotation = 0f;
                        markAnimator = ValueAnimator.ofFloat(0f, 1f);
                        markAnimator.setDuration(morphFromSpinner ? 200 : 220);
                        markAnimator.setInterpolator(new LinearInterpolator());
                        markAnimator.addUpdateListener(value -> {
                            markProgress = (float) value.getAnimatedValue();
                            if (markProgress >= 1f) morphFromSpinner = false;
                            invalidate();
                        });
                        markAnimator.start();
                    }
                } else {
                    rotation = 0f;
                    morphFromSpinner = false;
                    markProgress = 0f;
                }
            }
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f, cy = getHeight() / 2f, radius = dp(7.5f);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            if (state == 0) {
                ring.set(cx - radius, cy - radius, cx + radius, cy + radius);
                paint.setColor(heroMuted);
                paint.setAlpha(95);
                canvas.drawArc(ring, 0, 360, false, paint);
                paint.setColor(heroText);
                paint.setAlpha(255);
                float sweep = 170f + 100f * (float) Math.sin(Math.toRadians(rotation));
                canvas.drawArc(ring, rotation, sweep, false, paint);
            } else if (state == 1 || state == -1) {
                if (state == 1 && morphFromSpinner) {
                    drawSpinnerToTick(canvas, cx, cy);
                    return;
                }
                paint.setColor(state == 1 ? green : red);
                paint.setAlpha(Math.round(72 * (1f - markProgress)));
                float ringRadius = radius * (1.28f - .28f * markProgress);
                canvas.drawCircle(cx, cy, ringRadius, paint);
                paint.setAlpha(255);
                Path mark = new Path();
                if (state == 1) {
                    mark.moveTo(cx - dp(5), cy);
                    mark.lineTo(cx - dp(1.5f), cy + dp(4));
                    mark.lineTo(cx + dp(6), cy - dp(5));
                    PathMeasure measure = new PathMeasure(mark, false);
                    Path visible = new Path();
                    measure.getSegment(0, measure.getLength() * markProgress, visible, true);
                    canvas.drawPath(visible, paint);
                } else {
                    float first = Math.min(1f, markProgress * 2f);
                    float second = Math.max(0f, (markProgress - .5f) * 2f);
                    canvas.drawLine(cx - dp(4.5f), cy - dp(4.5f),
                            cx - dp(4.5f) + dp(9f) * first, cy - dp(4.5f) + dp(9f) * first, paint);
                    canvas.drawLine(cx + dp(4.5f), cy - dp(4.5f),
                            cx + dp(4.5f) - dp(9f) * second, cy - dp(4.5f) + dp(9f) * second, paint);
                }
            } else {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(heroText);
                paint.setTextSize(dp(25));
                paint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText("↻", cx, cy - (paint.ascent() + paint.descent()) / 2f, paint);
            }
        }

        private void drawSpinnerToTick(Canvas canvas, float cx, float cy) {
            if (!morphPathsReady) prepareSpinnerToTick(cx, cy);
            paint.setColor(heroMuted);
            paint.setAlpha(Math.round(95 * (1f - markProgress)));
            canvas.drawArc(ring, 0, 360, false, paint);
            paint.setColor((int) colorEvaluator.evaluate(markProgress, heroText, green));
            paint.setAlpha(255);
            morphPath.reset();
            for (int i = 0; i < MORPH_STEPS; i++) {
                int offset = i * 2;
                float x = loadingPoints[offset] + (tickPoints[offset] - loadingPoints[offset]) * markProgress;
                float y = loadingPoints[offset + 1] + (tickPoints[offset + 1] - loadingPoints[offset + 1]) * markProgress;
                if (i == 0) morphPath.moveTo(x, y);
                else morphPath.lineTo(x, y);
            }
            canvas.drawPath(morphPath, paint);
        }

        private void prepareSpinnerToTick(float cx, float cy) {
            Path loadingArc = new Path();
            loadingArc.arcTo(ring, morphStartRotation, morphStartSweep, false);
            Path tick = new Path();
            tick.moveTo(cx - dp(5), cy);
            tick.lineTo(cx - dp(1.5f), cy + dp(4));
            tick.lineTo(cx + dp(6), cy - dp(5));
            sample(loadingArc, loadingPoints);
            sample(tick, tickPoints);
            morphPathsReady = true;
        }

        private void sample(Path path, float[] points) {
            PathMeasure measure = new PathMeasure(path, false);
            float length = measure.getLength();
            float[] point = new float[2];
            for (int i = 0; i < MORPH_STEPS; i++) {
                measure.getPosTan(length * i / (MORPH_STEPS - 1f), point, null);
                points[i * 2] = point[0];
                points[i * 2 + 1] = point[1];
            }
        }

        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (state == 0 && spin == null) {
                state = 2;
                show(true, false, false);
            }
        }

        @Override protected void onDetachedFromWindow() {
            if (spin != null) { spin.cancel(); spin = null; }
            if (markAnimator != null) { markAnimator.cancel(); markAnimator = null; }
            if (morphFromSpinner) { morphFromSpinner = false; markProgress = 1f; }
            if (navIndicatorAnimator != null) { navIndicatorAnimator.cancel(); navIndicatorAnimator = null; }
            animate().cancel();
            setScaleX(1f); setScaleY(1f); setAlpha(1f);
            super.onDetachedFromWindow();
        }
    }

    private final class CourseGroupRow {
        final CourseGroup group;
        final CourseCardView root;
        private final ArrayList<Course> subjects = new ArrayList<>();

        CourseGroupRow(CourseGroup group) {
            this.group = group;
            subjects.add(group.lecture);
            subjects.addAll(group.labs);
            boolean atRisk = group.priority() < state.target;
            ArrayList<CourseMetricUiState> metrics = new ArrayList<>();
            StringBuilder description = new StringBuilder(courseHeading(group.lecture));
            for (int i = 0; i < subjects.size(); i++) {
                Course subject = subjects.get(i);
                String kind = isLabCourse(subject.code) ? "LAB" : "THEORY";
                if (isLabCourse(group.lecture.code) && subject == group.lecture) kind = "LAB";
                metrics.add(CourseCardStateMapper.map(subject, kind, courseHeading(subject)));
                description.append(". ").append(isLabCourse(subject.code) ? "Lab" : "Theory")
                        .append(" ").append(subject.percentLabel).append(", ").append(subject.counts);
            }
            CourseCardUiState cardState = new CourseCardUiState(
                    group.lecture.name, group.lecture.code,
                    courseHeading(group.lecture) + (atRisk ? ", needs focus" : ", on track")
                            + ". Tap to expand course guidance.",
                    description.toString(), metrics);
            root = new CourseCardView(getContext(), palette.toComposeColors(), cardState,
                    code -> { actions.onCourse(code); return kotlin.Unit.INSTANCE; },
                    code -> { openPlannerFor(code); return kotlin.Unit.INSTANCE; });
        }

        private void openPlannerFor(String code) { DashboardView.this.openPlannerFor(code); }

        boolean matches() {
            boolean hasMatch = false;
            boolean hasFocus = false;
            for (Course subject : subjects) {
                hasMatch |= DashboardView.this.matches(subject, query);
                hasFocus |= subject.below;
            }
            return hasMatch && (!belowOnly || hasFocus);
        }
    }

    private final class AttendanceRail extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private ValueAnimator selectionMotion;
        private float selectionBlend = 1f;

        void animateSelection(boolean attend) {
            if (selectionMotion != null) selectionMotion.cancel();
            float from = attendForecastSelected ? 1f : 0f;
            float to = attend ? 1f : 0f;
            selectionBlend = from;
            if (android.os.Build.VERSION.SDK_INT >= 26 && !ValueAnimator.areAnimatorsEnabled()) {
                selectionBlend = to; invalidate(); return;
            }
            selectionMotion = ValueAnimator.ofFloat(from, to);
            selectionMotion.setDuration(240);
            selectionMotion.setInterpolator(new DecelerateInterpolator(1.5f));
            selectionMotion.addUpdateListener(a -> { selectionBlend = (float) a.getAnimatedValue(); invalidate(); });
            selectionMotion.start();
        }

        @Override protected void onDetachedFromWindow() {
            if (selectionMotion != null) selectionMotion.cancel();
            super.onDetachedFromWindow();
        }

        AttendanceRail(Context context) {
            super(context);
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            float left = dp(2), right = getWidth() - dp(2), center = dp(11);
            float percent = currentCoursePercent;
            for (int i = 0; i <= 40; i++) {
                float x = left + (right - left) * i / 40f;
                boolean major = i % 10 == 0;
                paint.setColor(i / 40f <= percent / 100f ? lime : border);
                paint.setAlpha(i / 40f <= percent / 100f ? 255 : 150);
                paint.setStrokeWidth(dp(1.5f));
                c.drawLine(x, center - (major ? dp(8) : dp(4)), x,
                        center + (major ? dp(8) : dp(4)), paint);
            }
            paint.setAlpha(255);
            float targetX = left + (right - left) * Math.max(0, Math.min(100, state.target)) / 100f;
            paint.setColor(muted);
            paint.setStrokeWidth(dp(2));
            c.drawLine(targetX, center - dp(10), targetX, center + dp(10), paint);
            if (hasForecast) {
                float attendX = left + (right - left) * Math.max(0, Math.min(100, attendForecast)) / 100f;
                float skipX = left + (right - left) * Math.max(0, Math.min(100, skipForecast)) / 100f;
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(green);
                c.drawCircle(attendX, center - dp(3), dp(attendForecastSelected ? 4 : 2.5f), paint);
                paint.setColor(red);
                c.drawCircle(skipX, center + dp(3), dp(!attendForecastSelected ? 4 : 2.5f), paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(1.25f));
                paint.setColor(lime);
                float blend = selectionMotion != null && selectionMotion.isRunning() ? selectionBlend : attendForecastSelected ? 1f : 0f;
                float selectedX = skipX + (attendX - skipX) * blend;
                c.drawCircle(selectedX, center + dp(3) - dp(6) * blend, dp(5), paint);
                paint.setStyle(Paint.Style.STROKE);
            }
        }
    }

    private final class PlannerMeter extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float displayed, destination;
        private String course = "";
        private ValueAnimator motion;

        PlannerMeter(Context context) { super(context); }

        void update(float value, String code) {
            value = Math.max(0, Math.min(100, value));
            boolean changedCourse = !course.equals(code);
            course = code;
            setContentDescription("Projected attendance " + Math.round(value) + " percent; target " + state.target + " percent");
            if (!changedCourse && destination == value) { invalidate(); return; }
            destination = value;
            if (motion != null) motion.cancel();
            if (changedCourse || !isShown() || (android.os.Build.VERSION.SDK_INT >= 26 && !ValueAnimator.areAnimatorsEnabled())) {
                displayed = value;
                invalidate();
                return;
            }
            motion = ValueAnimator.ofFloat(displayed, value);
            motion.setDuration(260);
            motion.setInterpolator(new android.view.animation.PathInterpolator(.2f, 0f, 0f, 1f));
            motion.addUpdateListener(a -> { displayed = (float) a.getAnimatedValue(); invalidate(); });
            motion.start();
        }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            float left = dp(6), width = Math.max(0, getWidth() - dp(12)), y = getHeight() / 2f;
            paint.setColor(border);
            c.drawRoundRect(left, y-dp(3), left+width, y+dp(3), dp(3), dp(3), paint);
            float x = left + width * displayed / 100f;
            paint.setColor(displayed >= state.target ? green : amber);
            c.drawRoundRect(left, y-dp(3), x, y+dp(3), dp(3), dp(3), paint);
            c.drawCircle(x, y, dp(5), paint);
            paint.setColor(ink);
            paint.setStrokeWidth(dp(2));
            float target = left + width * state.target / 100f;
            c.drawLine(target, y-dp(9), target, y+dp(9), paint);
        }

        @Override protected void onDetachedFromWindow() {
            if (motion != null) motion.cancel();
            displayed = destination;
            super.onDetachedFromWindow();
        }
    }

    private final class AttendanceProgressBar extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float progress;
        private final float destination;
        private ValueAnimator progressMotion;
        private final int progressColor;

        AttendanceProgressBar(Context context, String code, float progress, int color) {
            super(context);
            destination = Math.max(0, Math.min(100, progress));
            Float previous = previousCourseProgress.put(code, destination);
            this.progress = previous == null ? destination : previous;
            progressColor = color;
            setLayerType(View.LAYER_TYPE_HARDWARE, null);
        }

        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (progress == destination) return;
            if (!isShown() || (android.os.Build.VERSION.SDK_INT >= 26 && !ValueAnimator.areAnimatorsEnabled())) {
                progress = destination; invalidate(); return;
            }
            progressMotion = ValueAnimator.ofFloat(progress, destination);
            progressMotion.setDuration(300);
            progressMotion.setInterpolator(new DecelerateInterpolator(1.5f));
            progressMotion.addUpdateListener(a -> { progress = (float) a.getAnimatedValue(); invalidate(); });
            progressMotion.start();
        }

        @Override protected void onDetachedFromWindow() {
            if (progressMotion != null) progressMotion.cancel();
            progress = destination;
            super.onDetachedFromWindow();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float height = getHeight(), width = getWidth(), radius = height / 2f;
            paint.setColor(border);
            canvas.drawRoundRect(0, 0, width, height, radius, radius, paint);
            float fill = width * progress / 100f;
            if (fill > 0) {
                paint.setColor(progressColor);
                canvas.drawRoundRect(0, 0, fill, height, radius, radius, paint);
            }
        }
    }

    private final class WeekPulseView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int[] counts = new int[7];
        private int today = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY;
        private int nextClassDay = -1;
        private final String[] labels = {"S", "M", "T", "W", "T", "F", "S"};

        WeekPulseView(Context context) {
            super(context);
            setContentDescription("Weekly timetable rhythm");
        }

        int setSchedule(List<ScheduleItem> schedule) {
            java.util.Arrays.fill(counts, 0);
            today = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY;
            nextClassDay = -1;
            int total = 0;
            long now = System.currentTimeMillis();
            long nextTime = Long.MAX_VALUE;
            if (schedule != null) {
                for (ScheduleItem item : schedule) {
                    if (item.allDay || item.cancelled || item.startsAt <= 0) continue;
                    Calendar day = Calendar.getInstance();
                    day.setTimeInMillis(item.startsAt);
                    int index = day.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY;
                    counts[index]++;
                    total++;
                    if (item.startsAt >= now && item.startsAt < nextTime) {
                        nextTime = item.startsAt;
                        nextClassDay = index;
                    }
                }
            }
            StringBuilder description = new StringBuilder("Weekly timetable: ");
            String[] dayNames = {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
            for (int i = 0; i < counts.length; i++) {
                if (i > 0) description.append("; ");
                description.append(dayNames[i]).append(" ").append(counts[i]).append(" classes");
            }
            setContentDescription(description.toString());
            invalidate();
            return total;
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float width = getWidth();
            float step = width / 7f;
            float baseY = getHeight() - dp(16);
            float graphTop = dp(20);
            int max = 1;
            for (int count : counts) max = Math.max(max, count);

            Path rhythm = new Path();
            for (int i = 0; i < counts.length; i++) {
                float height = counts[i] == 0 ? dp(4) : dp(8) + (baseY - graphTop - dp(8)) * counts[i] / max;
                float x = step * (i + .5f);
                float y = baseY - height;
                if (i == 0) rhythm.moveTo(x, y);
                else rhythm.lineTo(x, y);
            }
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1.5f));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(border);
            if (!cyberpunk) canvas.drawPath(rhythm, paint);

            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(Typeface.create("monospace", Typeface.BOLD));
            paint.setTextSize(sp(12));
            Calendar visibleWeek = Calendar.getInstance();
            visibleWeek.setFirstDayOfWeek(Calendar.SUNDAY);
            visibleWeek.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY);
            visibleWeek.set(Calendar.HOUR_OF_DAY, 0);
            visibleWeek.set(Calendar.MINUTE, 0);
            visibleWeek.set(Calendar.SECOND, 0);
            visibleWeek.set(Calendar.MILLISECOND, 0);
            for (int i = 0; i < counts.length; i++) {
                float x = step * (i + .5f);
                float height = counts[i] == 0 ? dp(4) : dp(8) + (baseY - graphTop - dp(8)) * counts[i] / max;
                float y = baseY - height;
                paint.setColor(i == nextClassDay ? amber : counts[i] > 0 ? lime : border);
                if (cyberpunk) {
                    float half = Math.min(dp(12), step * .27f);
                    paint.setColor(soft);
                    canvas.drawRect(x - half, y, x + half, baseY, paint);
                    paint.setColor(i == today ? amber : green);
                    canvas.drawRect(x - half, y, x + half, y + dp(2), paint);
                    // Each lit floor represents one scheduled class, not attendance.
                    float floor = (baseY - y) / Math.max(1, counts[i]);
                    for (int level = 0; level < counts[i]; level++) {
                        float fy = baseY - floor * (level + .5f);
                        canvas.drawRect(x - half + dp(3), fy - dp(1), x - dp(2), fy + dp(1), paint);
                        canvas.drawRect(x + dp(2), fy - dp(1), x + half - dp(3), fy + dp(1), paint);
                    }
                } else canvas.drawCircle(x, y, dp(i == nextClassDay ? 4 : 3), paint);
                paint.setColor(muted);
                canvas.drawText(counts[i] == 0 ? "·" : String.valueOf(counts[i]), x, y - dp(7), paint);
                Calendar date = (Calendar) visibleWeek.clone();
                date.add(Calendar.DAY_OF_YEAR, i);
                paint.setTextSize(sp(10));
                paint.setColor(i == today ? amber : muted);
                canvas.drawText(labels[i], x, getHeight() - dp(13), paint);
                paint.setTextSize(sp(11));
                paint.setColor(i == today ? ink : muted);
                canvas.drawText(String.valueOf(date.get(Calendar.DAY_OF_MONTH)), x, getHeight() - dp(1), paint);
            }
        }

        private float sp(float value) { return value * getResources().getDisplayMetrics().scaledDensity; }
    }

    private final class CourseRow {
        final Course course;
        final LinearLayout root;
        final LinearLayout detail;
        final boolean overviewPreview;
        CourseRow(Course course, Course pairedLecture, boolean overviewPreview) {
            this.course = course;
            this.overviewPreview = overviewPreview;
            root = card();
            root.setPadding(dp(10), dp(10), dp(10), dp(10));
            LinearLayout line = row();
            TextView icon = text(course.below ? "!" : "▤", 15, course.color, Typeface.BOLD);
            icon.setGravity(Gravity.CENTER);
            icon.setBackground(shape(course.below ? focusSurface : soft, 9, 0));
            line.addView(icon, lp(dp(34), dp(36)));
            LinearLayout names = column();
            names.setPadding(dp(8), 0, dp(3), 0);
            LinearLayout courseTitle = row();
            String suffix = isLabCourse(course.code) ? "   LAB" : "";
            SpannableString titleText = new SpannableString(course.name + suffix);
            if (!suffix.isEmpty()) {
                int labStart = titleText.length() - 3;
                titleText.setSpan(new LabBadgeSpan(), labStart, titleText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            TextView subjectName = text(titleText.toString(), 14, ink, Typeface.BOLD);
            subjectName.setText(titleText);
            subjectName.setMaxLines(2);
            subjectName.setEllipsize(android.text.TextUtils.TruncateAt.END);
            courseTitle.addView(subjectName, new LinearLayout.LayoutParams(-1, -2));
            names.addView(courseTitle);
            String subtitle = courseSubtitle(course);
            if (!subtitle.isEmpty()) names.addView(text(subtitle, 9, muted, Typeface.NORMAL), topLp(-1, -2, 3));
            line.addView(names, new LinearLayout.LayoutParams(0, -2, 1));
            LinearLayout percent = column();
            percent.setGravity(Gravity.RIGHT);
            percent.setMinimumWidth(dp(72));
            percent.setLayoutParams(new LinearLayout.LayoutParams(dp(72), -2));
            TextView pct = text(course.percentLabel, 15, course.color, Typeface.BOLD);
            pct.setGravity(Gravity.RIGHT);
            pct.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
            percent.addView(pct);
            AttendanceProgressBar bar = new AttendanceProgressBar(getContext(), course.code, course.percent, course.color);
            bar.setContentDescription("Attendance " + course.percentLabel);
            LinearLayout.LayoutParams barParams = topLp(dp(64), dp(7), 4);
            barParams.gravity = Gravity.RIGHT;
            percent.addView(bar, barParams);
            if (!course.targetCount.isEmpty()) {
                TextView targetCount = text(course.targetCount, 10, course.below ? course.color : green, Typeface.BOLD);
                targetCount.setGravity(Gravity.RIGHT);
                targetCount.setMaxLines(2);
                percent.addView(targetCount, topLp(-1, -2, 4));
            }
            line.addView(percent);
            TextView arrow = text("›", 20, muted, Typeface.NORMAL);
            arrow.setPadding(dp(8), 0, dp(3), 0);
            line.addView(arrow);
            root.addView(line);
            detail = column();
            detail.setPadding(dp(43), dp(10), dp(4), dp(2));
            if (pairedLecture != null) {
                detail.addView(text("Lab section · paired with " + pairedLecture.code, 11, green, Typeface.BOLD));
            }
            detail.addView(text(course.status, 10, course.color, Typeface.BOLD));
            detail.addView(text(course.counts + ". " + course.guidance, 10, muted, Typeface.NORMAL), topLp(-1, -2, 4));
            TextView lab = text("Explore in attendance lab  ↗", 9, green, Typeface.BOLD);
            lab.setPadding(0, dp(6), 0, 0);
            lab.setOnClickListener(v -> openPlannerFor(course.code));
            detail.addView(lab);
            detail.setVisibility(course.expanded ? VISIBLE : GONE);
            line.setFocusable(true);
            line.setContentDescription((pairedLecture == null ? "Course: " : "Lab course: ") + course.name
                    + (pairedLecture == null ? "" : ", paired with " + pairedLecture.name)
                    + ", attendance " + course.percentLabel + ". Double tap for details.");
            if (course.expanded && (android.os.Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled())) {
                detail.setAlpha(0f);
                detail.setTranslationY(dp(6));
                detail.animate().alpha(1f).translationY(0).setStartDelay(35).setDuration(180)
                        .setInterpolator(new DecelerateInterpolator(1.5f)).start();
            }
            root.addView(detail);
            line.setOnClickListener(v -> actions.onCourse(course.code));
            detail.setOnClickListener(v -> actions.onCourse(course.code));
        }
    }

    private final class SemesterGpaChart extends View {
        private final int seriesColor;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint labels = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final ArrayList<GpaPoint> points = new ArrayList<>();

        SemesterGpaChart(Context context, int seriesColor, int labelColor, Typeface typeface) {
            super(context);
            this.seriesColor = seriesColor;
            stroke.setColor(seriesColor);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(dp(1));
            stroke.setStrokeCap(Paint.Cap.ROUND);
            labels.setColor(labelColor);
            labels.setTextSize(11 * getResources().getDisplayMetrics().scaledDensity);
            labels.setTypeface(typeface);
        }

        void setPoints(List<GpaPoint> values) {
            points.clear();
            points.addAll(values);
            StringBuilder description = new StringBuilder("Semester GPA trend. ");
            for (GpaPoint point : points)
                description.append(semesterTitle(point.semester)).append(": ")
                        .append(String.format(Locale.ROOT, "%.2f", point.value)).append(". ");
            setContentDescription(description.toString());
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (points.isEmpty()) return;
            float left = dp(6), right = getWidth() - dp(6);
            float top = dp(25), baseline = getHeight() - dp(25);
            float slot = (right - left) / points.size();
            float barWidth = Math.min(dp(34), slot * .54f);
            float radius = Math.min(dp(6), barWidth / 2f);
            for (int i = 0; i < points.size(); i++) {
                GpaPoint point = points.get(i);
                float x = left + slot * (i + .5f);
                float barTop = baseline - (baseline - top) * Math.max(0f, Math.min(10f, point.value)) / 10f;
                RectF rect = new RectF(x - barWidth / 2f, barTop, x + barWidth / 2f, baseline);
                Path path = new Path();
                path.moveTo(rect.left, rect.bottom);
                path.lineTo(rect.left, rect.top + radius);
                path.quadTo(rect.left, rect.top, rect.left + radius, rect.top);
                path.lineTo(rect.right - radius, rect.top);
                path.quadTo(rect.right, rect.top, rect.right, rect.top + radius);
                path.lineTo(rect.right, rect.bottom);
                path.close();
                int endColor = Color.argb(Math.round(Color.alpha(seriesColor) * .12f),
                        Color.red(seriesColor), Color.green(seriesColor), Color.blue(seriesColor));
                fill.setShader(new android.graphics.LinearGradient(0, rect.top, 0, rect.bottom,
                        seriesColor, endColor, Shader.TileMode.CLAMP));
                canvas.drawPath(path, fill);
                fill.setShader(null);
                canvas.drawPath(path, stroke);

                String score = String.format(Locale.getDefault(), "%.2f", point.value);
                canvas.drawText(score, x - labels.measureText(score) / 2f, Math.max(dp(12), barTop - dp(6)), labels);
                String term = semesterShortLabel(point.semester);
                canvas.drawText(term, x - labels.measureText(term) / 2f, getHeight() - dp(5), labels);
            }
        }
    }

}
