package com.iniexe.rollcall;

import android.animation.ValueAnimator;
import android.app.AlertDialog;
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
import android.widget.SeekBar;
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
        void onAbout();
        void onProfile();
        void onRemindersToggle();
        void onFilter(boolean belowOnly);
        void onQuery(String value);
        void onCourse(String code);
        void onAnnouncementReceipt(String campaignId, String receiptState);
        void onHomePageShown();
    }

    static final class State {
        boolean dark;
        boolean remindersEnabled;
        String name = "MyCamu student", initials = "R";
        String status = "", heroMessage = "— / —";
        String overallCaption = "classes attended", targetHint = "";
        int target = 75, overallPercent = -1, courses, below, onTrack;
        boolean belowOnly, syncing, syncError, sessionExpired, syncSucceeded, attendanceLoaded, partialSync;
        String partialSyncStatus = "";
        boolean timetableLoaded, leavesLoaded;
        long attendanceUpdatedAt, timetableUpdatedAt, classMarksUpdatedAt, examScheduleCheckedAt;
        boolean cloudConfigured;
        boolean announcementsLoaded, announcementsLoading;
        String announcementsError = "";
        List<Announcement> announcements = new ArrayList<>();
        String query = "";
        String latestClassName = "", latestClassStatus = "";
        List<Course> rows = new ArrayList<>();
        List<ScheduleItem> timetable = new ArrayList<>();
        List<LeaveItem> leaves = new ArrayList<>();
        List<ClassMarkItem> classMarks = new ArrayList<>();
        List<AcademicCourse> academicCourses = new ArrayList<>();
        List<InternalMark> internalMarks = new ArrayList<>();
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

    static final class Announcement {
        String id = "", title = "", body = "", category = "announcement", priority = "normal", readState = "unread";
        String actionLabel = "", actionPath = "";
        long publishedAt, expiresAt;
        boolean showPopup;
    }

    static final class Course {
        String code = "", name = "", percentLabel = "", status = "", counts = "", guidance = "", targetCount = "";
        float percent;
        int attended, total, color, odCredit, odCount;
        boolean odReconciled;
        boolean countsKnown, below, expanded;
    }

    static final class ScheduleItem {
        String id = "", date = "", time = "", name = "", code = "", details = "";
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
        String id = "", day = "", time = "", code = "", name = "", status = "", leaveType = "", leaveStatus = "";
        long startsAt;
        int color;
    }

    static final class AcademicCourse {
        String semester = "", code = "", name = "", grade = "", result = "";
        double credits;
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
    private final int canvas, ink, muted, surface, raisedSurface, border, soft, green, red, amber, hero, heroText, heroMuted, lime;
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
    private final ArrayList<ImageView> avatarPhotos = new ArrayList<>();
    private final HashSet<String> expandedScheduleDays = new HashSet<>();
    private final Set<String> revealedContent = new HashSet<>();
    private boolean scheduleExpansionInitialized;
    private String pendingScheduleRevealDate = "";
    private StatusMarkView dashboardSyncGlyph;
    private final Typeface uiTypeface;
    private final Typeface uiBoldTypeface;
    private final Typeface displayTypeface;
    private final LinearLayout nav = new LinearLayout(getContext());
    private FrameLayout moreOverlay;
    private LinearLayout moreMenuPanel;
    private boolean moreMenuOpen;
    private LinearLayout homeHeader;
    private NavBackgroundDrawable navBackground;
    private ValueAnimator navIndicatorAnimator;
    private final LinearLayout[] navTabs = new LinearLayout[5];
    private final ImageView[] navIcons = new ImageView[5];
    private AttendanceRail attendanceRail;
    private TextView overviewGoal, courseSummary, decisionCourse, decisionLabel, decisionAction, plannerBaseline;
    private String decisionCode = "";
    private final TextView[] navItems = new TextView[5];

    private ShuffleTextView headlineView;
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
    private final Runnable classStartRefresh = () -> refreshOverviewAgenda();
    private View overviewCourseHeader;
    private LinearLayout overviewScheduleBlock, overviewWeekBlock, overviewAttendanceBlock, overviewExamSection;
    private LinearLayout overviewExamBlock;
    private TextView overviewExamTitle, overviewExamWhen, overviewExamDate, overviewExamDetails, overviewExamType,
            overviewExamSession, overviewExamFreshness, overviewScheduleFreshness;
    private TextView overviewAttendanceTitle, overviewAttendanceCourse, overviewAttendanceStatus;
    private LinearLayout overviewScheduleRows;
    private TextView overviewWeekMeta;
    private WeekPulseView overviewWeekPulse;
    private TextView projectionView, projectionStatusView, plannerCountView, plannerCurrentPercentView;
    private final java.util.Map<String, Float> previousCourseProgress = new java.util.HashMap<>();
    private TextView welcomeView;
    private TextView homeDateView, homeWeekNumberView, homeWeekMonthView, homeTicketLabelView, homeEncouragementView;
    private LinearLayout announcementList;
    private TextView announcementStatus;
    private TextView moreUpdatesLabel;
    private View moreUpdatesDot;
    private FrameLayout homeTicketView;
    private int homeTicketDestination = 2;
    private final int linkInk;
    private boolean wasSyncing;
    private TextView plannerFeedback, forecastLegend;
    private ForecastPlotView forecastPlot;
    private SeekBar forecastAmount;
    private TimetableTransitionView temporalFold;
    private TextView scheduleUpdatedView, scheduleEmptyView, historyEmptyView;
    private LinearLayout scheduleEmptyPanel;
    private TextView scheduleEmptyTitle, scheduleEmptyDetail;
    private LinearLayout scheduleRows, historyRows, scheduleContent, historyContent;
    private LinearLayout odOptions;
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
    private JellyRadioView coursesRadio, weekRadio, plannerRadio;
    private boolean showingHistory = true;
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
    private int plannerCount = 1, plannerCourse;
    private int selectedPage;
    private LinearLayout academicsContent;
    private String selectedSemester = "";
    private boolean initialized;
    private int swipeTouchSlop;
    private float swipeDownX, swipeDownY;
    private boolean swipeAllowed, swipingPages;

    DashboardView(Context context, Actions actions, AppPalette palette) {
        super(context);
        swipeTouchSlop = android.view.ViewConfiguration.get(context).getScaledTouchSlop();
        ThemeTypography typography = ThemeTypography.load(context, palette.themeIndex);
        uiTypeface = typography.body;
        uiBoldTypeface = typography.bodyBold;
        cyberpunk = "Night City 2077".equals(palette.name);
        displayTypeface = typography.display;
        cyberpunkTypeface = uiBoldTypeface;
        this.actions = actions;
        this.palette = palette;
        canvas = palette.canvas; ink = palette.ink; muted = palette.muted;
        surface = palette.surface; raisedSurface = palette.raisedSurface; border = palette.border; soft = palette.soft;
        green = palette.green; red = palette.red; amber = palette.amber;
        hero = palette.hero; heroText = palette.heroText; heroMuted = palette.heroMuted;
        lime = palette.dark ? palette.highlight : palette.green;
        linkInk = palette.dark ? palette.highlight : palette.green;
        track = palette.track;
        focusSurface = palette.focusSurface;
        labBadgeBackground = palette.button; labBadgeForeground = palette.buttonText;
        setBackgroundColor(canvas);
        build();
    }

    void setProfilePhoto(Bitmap bitmap) {
        for (ImageView photo : avatarPhotos) {
            photo.setImageBitmap(bitmap);
            photo.setVisibility(bitmap == null ? GONE : VISIBLE);
        }
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
        if (headlineView == null) return;
        updateAnnouncements(next);
        String displayName = next.name == null ? "MyCamu student"
                : next.name.trim().replaceAll("\\s+", " ").replaceAll("\\s+([,.;:!?])", "$1")
                        .replaceAll("\\.$", "");
        String name = displayName.toUpperCase(Locale.ROOT);
        String heroHeadline = name + ".\nYOU GOT THIS.";
        SpannableString heroSpans = new SpannableString(heroHeadline);
        heroSpans.setSpan(new android.text.style.ForegroundColorSpan(palette.highlight),
                name.length(), name.length() + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        heroSpans.setSpan(new android.text.style.ForegroundColorSpan(palette.highlight),
                heroHeadline.length() - 1, heroHeadline.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        headlineView.setShuffledText(heroSpans);
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String greeting = hour < 12 ? "Good morning" : hour < 17 ? "Good afternoon" : "Good evening";
        welcomeView.setText(greeting + ",");
        SimpleDateFormat broadcastDate = new SimpleDateFormat("EEE / dd MMM", Locale.ENGLISH);
        homeDateView.setText(broadcastDate.format(new Date()).toUpperCase(Locale.ROOT));
        // The physical calendar represents a real date, not a decorative week number.
        long now = System.currentTimeMillis();
        long eventAt = Long.MAX_VALUE;
        String eventLabel = "TODAY";
        homeTicketDestination = 2;
        for (ScheduleItem scheduled : next.timetable) {
            if (!scheduled.cancelled && !scheduled.allDay && scheduled.startsAt >= now
                    && scheduled.startsAt < eventAt) {
                eventAt = scheduled.startsAt;
                eventLabel = "NEXT CLASS";
            }
        }
        boolean futureExam = next.upcomingExamAt > 0 && !next.upcomingExamTitle.isEmpty()
                && (next.upcomingExamAt >= now || (!next.upcomingExamHasTime
                && new SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(new Date(next.upcomingExamAt))
                .equals(new SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(new Date(now)))));
        if (futureExam && (eventAt == Long.MAX_VALUE || next.upcomingExamAt < eventAt)) {
            eventAt = next.upcomingExamAt;
            eventLabel = "EXAM DATE";
            homeTicketDestination = 4;
        }
        Date displayedDate = new Date(eventAt == Long.MAX_VALUE ? now : eventAt);
        homeTicketLabelView.setText(eventLabel);
        homeWeekNumberView.setText(new SimpleDateFormat("dd", Locale.ENGLISH).format(displayedDate));
        homeWeekMonthView.setText(new SimpleDateFormat("EEE · MMM", Locale.ENGLISH)
                .format(displayedDate).toUpperCase(Locale.ROOT));
        if (homeTicketView != null) {
            homeTicketView.setContentDescription(eventLabel + ", "
                    + new SimpleDateFormat("EEEE, d MMMM", Locale.ENGLISH).format(displayedDate)
                    + ". Open " + (homeTicketDestination == 4 ? "marks and exams" : "your week"));
        }
        homeEncouragementView.setText("One class at a time. Your academic week, decoded.");
        String avatarLabel = next.initials == null || next.initials.isEmpty()
                ? "R." : next.initials.substring(0, 1) + ".";
        for (TextView avatar : avatars) avatar.setText(avatarLabel);
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
        statusView.setTextColor(next.syncError || next.sessionExpired ? red
                : next.syncSucceeded ? palette.syncSuccess : muted);
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
        boolean hasClassStatus = !next.latestClassName.isEmpty() && !next.latestClassStatus.isEmpty();
        overviewAttendanceBlock.setVisibility(hasClassStatus ? VISIBLE : GONE);
        if (hasClassStatus) {
            String statusLabel = "present".equals(next.latestClassStatus) ? "Present"
                    : "absent".equals(next.latestClassStatus) ? "Absent"
                    : "not_taken".equals(next.latestClassStatus) ? "Not marked yet" : "Unable to confirm";
            int statusColor = "present".equals(next.latestClassStatus) ? green
                    : "absent".equals(next.latestClassStatus) ? red
                    : "not_taken".equals(next.latestClassStatus) ? amber : muted;
            overviewAttendanceTitle.setText("LATEST CLASS");
            overviewAttendanceTitle.setTextColor(muted);
            overviewAttendanceCourse.setText(next.latestClassName);
            overviewAttendanceStatus.setText(statusLabel);
            overviewAttendanceStatus.setTextColor(statusColor);
            overviewAttendanceStatus.setBackground(shape(statusTint(statusColor), 9, 0));
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

    private void updateAnnouncements(State next) {
        int unread = 0;
        for (Announcement item : next.announcements) if (!"dismissed".equals(item.readState) && !"read".equals(item.readState)) unread++;
        if (moreUpdatesLabel != null) moreUpdatesLabel.setText(unread > 0 ? "Updates · " + unread + " new" : "Updates");
        if (moreUpdatesDot != null) moreUpdatesDot.setVisibility(unread > 0 ? VISIBLE : GONE);
        if (announcementList == null || announcementStatus == null) return;
        announcementList.removeAllViews();
        if (!next.cloudConfigured) {
            announcementStatus.setText("ACCOUNT NEEDED");
            addAnnouncementMessage("Sign in to your verified Rollcall account to receive developer updates.");
            return;
        }
        if (next.announcementsLoading) {
            announcementStatus.setText("SYNCING");
            addAnnouncementMessage("Checking the Rollcall inbox…");
            return;
        }
        if (!next.announcementsError.isEmpty()) {
            announcementStatus.setText("UNAVAILABLE");
            addAnnouncementMessage("Updates couldn’t be refreshed. Check your connection and try again.");
            return;
        }
        announcementStatus.setText(next.announcements.isEmpty() ? "UP TO DATE"
                : next.announcements.size() + (next.announcements.size() == 1 ? " UPDATE" : " UPDATES"));
        if (next.announcements.isEmpty()) {
            addAnnouncementMessage("No Rollcall updates right now.");
            return;
        }
        for (Announcement item : next.announcements) {
            if ("dismissed".equals(item.readState)) continue;
            LinearLayout card = column();
            card.setPadding(dp(12), dp(11), dp(12), dp(10));
            int accent = "critical".equals(item.priority) ? red
                    : "important".equals(item.priority) ? amber : lime;
            card.setBackground(industrialSurface(raisedSurface, 13, border));
            TextView type = label((item.priority + "  ·  " + item.category).toUpperCase(Locale.ROOT), 9, accent, Typeface.BOLD);
            card.addView(type);
            TextView title = text(item.title, 15, ink, Typeface.BOLD);
            title.setMaxLines(2);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            card.addView(title, topLp(-1, -2, 4));
            TextView message = text(item.body, 12, muted, Typeface.NORMAL);
            message.setMaxLines(5);
            message.setEllipsize(android.text.TextUtils.TruncateAt.END);
            card.addView(message, topLp(-1, -2, 4));
            LinearLayout receipts = row();
            receipts.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            if (!"read".equals(item.readState)) {
                TextView markRead = text("Mark read", 11, ink, Typeface.BOLD);
                markRead.setGravity(Gravity.CENTER);
                markRead.setMinHeight(dp(40));
                markRead.setPadding(dp(12), 0, dp(12), 0);
                markRead.setBackground(shape(soft, 10, border));
                markRead.setContentDescription("Mark announcement as read: " + item.title);
                markRead.setOnClickListener(v -> actions.onAnnouncementReceipt(item.id, "read"));
                receipts.addView(markRead, lp(-2, -2));
            } else {
                TextView read = label("READ", 9, green, Typeface.BOLD);
                read.setGravity(Gravity.CENTER);
                receipts.addView(read, lp(-2, dp(40)));
            }
            TextView dismiss = text("Dismiss", 11, muted, Typeface.BOLD);
            dismiss.setGravity(Gravity.CENTER);
            dismiss.setMinHeight(dp(40));
            dismiss.setPadding(dp(12), 0, dp(12), 0);
            dismiss.setBackground(shape(canvas, 10, border));
            dismiss.setContentDescription("Dismiss announcement: " + item.title);
            dismiss.setOnClickListener(v -> actions.onAnnouncementReceipt(item.id, "dismissed"));
            LinearLayout.LayoutParams dismissParams = lp(-2, -2);
            dismissParams.leftMargin = dp(8);
            receipts.addView(dismiss, dismissParams);
            card.addView(receipts, topLp(-1, -2, 4));
            announcementList.addView(card, topLp(-1, -2, 7));
        }
        if (announcementList.getChildCount() == 0) addAnnouncementMessage("You’ve dismissed all current updates.");
    }

    private void addAnnouncementMessage(String message) {
        TextView empty = text(message, 12, muted, Typeface.NORMAL);
        empty.setMaxLines(3);
        announcementList.addView(empty);
    }

    private String sourceTime(long time) {
        return time <= 0 ? "Not loaded" : new SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault()).format(new Date(time))
                + " (" + relativeAge(time) + ")";
    }

    private void openSignalPath() {
        String mycamu = state.sessionExpired ? "Sign-in expired · Saved data retained"
                : state.syncing ? "Checking MyCamu → On this device"
                : state.syncError ? "Refresh failed · Saved data retained"
                : state.partialSync ? "Partial update · Check category times"
                : state.attendanceUpdatedAt > 0 ? "Attendance saved on this device" : "Not connected yet";
        SyncPathView.Source[] sources = {
            new SyncPathView.Source("MyCamu → Device", mycamu,
                "Attendance: " + sourceTime(state.attendanceUpdatedAt)
                + "\nTimetable: " + sourceTime(state.timetableUpdatedAt)
                + "\nClass history: " + sourceTime(state.classMarksUpdatedAt)
                + "\n" + state.status + (state.partialSync ? "\n" + state.partialSyncStatus : ""),
                state.sessionExpired ? "Reconnect MyCamu" : "Refresh MyCamu", state.syncing),
            new SyncPathView.Source("KCT / SDMS → Device", state.academicConnected ? "Connected · See saved category times" : "Connection not confirmed",
                "Academic records: " + sourceTime(state.academicUpdatedAt)
                + "\nExam schedule: " + sourceTime(state.examScheduleCheckedAt)
                + "\n" + state.academicStatus + "\nMyCamu refresh does not confirm KCT freshness.", "Open KCT", false),
            new SyncPathView.Source("Device → Cloud / Web", state.cloudConfigured ? "Upload not confirmed" : "Cloud not configured",
                "Local saves do not confirm a cloud upload. This screen has no server acknowledgement timestamp. "
                + "The web displays a read-only snapshot after a successful upload.", "Account", false)
        };
        SyncPathView diagram = new SyncPathView(getContext(), palette, uiTypeface, sources,
                new Runnable[]{() -> { if (state.syncing) return; if (state.sessionExpired) actions.onPortal(); else actions.onSync(); },
                        () -> actions.onAcademicsPortal(), () -> actions.onProfile()});
        ScrollView scroll = new ScrollView(getContext()); scroll.addView(diagram);
        new android.app.AlertDialog.Builder(getContext()).setTitle("Signal path").setView(scroll)
                .setPositiveButton("Done", null).show();
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
        dashboardSyncGlyph.setState(syncing ? StatusMarkView.State.RUNNING
                : failed ? StatusMarkView.State.FAILED
                : succeeded ? StatusMarkView.State.DONE : StatusMarkView.State.PENDING);
    }

    private void build() {
        setClipChildren(false);
        setClipToPadding(false);

        LinearLayout shell = new LinearLayout(getContext());
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(canvas);
        addView(shell, new FrameLayout.LayoutParams(-1, -1));

        pages.setBackgroundColor(canvas);
        shell.addView(pages, new LinearLayout.LayoutParams(-1, 0, 1));
        buildOverview();
        buildCoursesPage();
        buildTimetablePage();
        buildLabPage();
        buildAcademicsPage();
        for (LinearLayout body : pageBodies) {
            View bottomSpace = new View(getContext());
            bottomSpace.setTag("rollcall-scroll-bottom-space");
            body.addView(bottomSpace, lp(1, dp(112)));
        }
        for (int i = 0; i < pageScrolls.length; i++) {
            pageScrolls[i].setVisibility(i == 0 ? VISIBLE : GONE);
            pages.addView(pageScrolls[i], new FrameLayout.LayoutParams(-1, -1));
        }
        buildOdDetailsPage();
        buildNavigation();
        buildMoreMenu();
    }

    private void buildOverview() {
        LinearLayout body = newPage(0);
        homeHeader = column();
        homeHeader.setTag("rollcall-home-header");
        homeHeader.setPadding(0, dp(12), 0, dp(10));
        homeHeader.setBackgroundColor(canvas);
        homeHeader.setClipChildren(false);
        homeHeader.setClipToPadding(false);
        homeHeader.addView(topBar(true), lp(-1, dp(48)));
        body.addView(homeHeader, topLp(-1, -2, 0));
        LinearLayout heroCard = column();
        heroCard.setPadding(dp(18), dp(18), dp(18), dp(16));
        heroCard.setBackground(shape(surface, palette.cardRadius, border));
        LinearLayout transmission = row();
        transmission.setGravity(Gravity.CENTER_VERTICAL);
        TextView transmissionLabel = label("YOUR ACADEMIC WEEK", 10, palette.highlight, Typeface.BOLD);
        transmission.addView(transmissionLabel, new LinearLayout.LayoutParams(0, -2, 1));
        homeDateView = text("THU / 12 NOV", 9, ink, Typeface.BOLD);
        homeDateView.setGravity(Gravity.CENTER);
        homeDateView.setPadding(dp(8), dp(6), dp(8), dp(6));
        homeDateView.setBackground(shape(raisedSurface, 100, border));
        transmission.addView(homeDateView, lp(-2, -2));
        heroCard.addView(transmission);

        LinearLayout identityRow = row();
        identityRow.setGravity(Gravity.CENTER_VERTICAL);
        identityRow.setClipChildren(false);
        LinearLayout identity = column();
        welcomeView = text("Good afternoon,", 13, muted, Typeface.NORMAL);
        identity.addView(welcomeView);
        headlineView = new ShuffleTextView(getContext());
        headlineView.setText("YOU GOT THIS.");
        headlineView.setTextSize(25);
        headlineView.setTextColor(ink);
        headlineView.setTypeface(displayTypeface);
        headlineView.setLetterSpacing(-.07f);
        headlineView.setMaxLines(4);
        headlineView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        identity.addView(headlineView, topLp(-1, -2, 4));
        homeEncouragementView = text("One class at a time. Your academic week, decoded.", 12, muted, Typeface.NORMAL);
        homeEncouragementView.setMaxLines(3);
        identity.addView(homeEncouragementView, topLp(-1, -2, 3));
        identityRow.addView(identity, new LinearLayout.LayoutParams(0, -2, 1));
        identityRow.addView(homeWeekTicket(), lp(dp(82), dp(94)));
        heroCard.addView(identityRow, topLp(-1, -2, 16));

        LinearLayout syncLine = row();
        syncLine.setGravity(Gravity.CENTER_VERTICAL);
        statusView = text("", 12, muted, Typeface.NORMAL);
        syncLine.addView(statusView, new LinearLayout.LayoutParams(0, -2, 1));
        dashboardSyncButton = new FrameLayout(getContext());
        dashboardSyncButton.setClickable(true);
        dashboardSyncButton.setFocusable(true);
        FrameLayout syncVisual = new FrameLayout(getContext());
        syncVisual.setBackground(shape(raisedSurface, 100, border));
        dashboardSyncGlyph = new StatusMarkView(getContext(), muted, palette.syncSuccess, red);
        syncVisual.addView(dashboardSyncGlyph,
                new FrameLayout.LayoutParams(dp(30), dp(30), Gravity.CENTER));
        dashboardSyncButton.addView(syncVisual,
                new FrameLayout.LayoutParams(dp(38), dp(38), Gravity.CENTER));
        dashboardSyncButton.setContentDescription("Refresh attendance from MyCamu");
        dashboardSyncButton.setOnClickListener(v -> actions.onSync());
        syncLine.addView(dashboardSyncButton, lp(dp(48), dp(48)));
        heroCard.addView(syncLine, topLp(-1, -2, 12));
        addTop(body, animated(heroCard, "home-hero"), 8);

        courseLists[0] = column();
        LinearLayout summary = column();
        summary.setPadding(dp(16), dp(14), dp(16), dp(14));
        summary.setBackground(industrialSurface(surface, 20, border));
        LinearLayout summaryHeader = row();
        summaryHeader.addView(label("NEXT CLASS", 11, lime, Typeface.BOLD),
                new LinearLayout.LayoutParams(0, -2, 1));
        overviewGoal = text("75% target  ↗", 11, lime, Typeface.BOLD);
        overviewGoal.setMinHeight(dp(44));
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
        decisionAction.setMinHeight(dp(48));
        decisionAction.setBackground(shape(labBadgeBackground, 12, 0));
        decisionAction.setOnClickListener(v -> {
            if (state.sessionExpired) actions.onPortal();
            else actions.onSync();
        });
        setTactileFeedback(decisionAction);
        summary.addView(decisionAction, topLp(-1, -2, 14));
        // Keep the interactive next-class forecast below the primary Home content.

        overviewExamSection = column();
        LinearLayout radarHeading = row();
        radarHeading.setGravity(Gravity.CENTER_VERTICAL);
        TextView radarTitle = text("On your radar", 21, ink, Typeface.BOLD);
        radarTitle.setTypeface(displayTypeface, Typeface.BOLD);
        radarHeading.addView(radarTitle, new LinearLayout.LayoutParams(0, -2, 1));
        radarHeading.addView(label("01 / EXAM", 10, amber, Typeface.BOLD));
        overviewExamSection.addView(radarHeading);
        overviewExamBlock = column();
        overviewExamBlock.setPadding(dp(16), dp(14), dp(16), dp(14));
        overviewExamBlock.setBackground(industrialSurface(surface, 16, border));
        overviewExamBlock.addView(label("● UPCOMING EXAM", 10, amber, Typeface.BOLD));
        overviewExamTitle = text("", 17, ink, Typeface.BOLD);
        overviewExamTitle.setMaxLines(4);
        overviewExamTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        overviewExamBlock.addView(overviewExamTitle, topLp(-1, -2, 5));
        LinearLayout examBadges = row();
        examBadges.setGravity(Gravity.CENTER_VERTICAL);
        overviewExamType = text("", 10, ink, Typeface.BOLD);
        overviewExamType.setPadding(dp(9), dp(5), dp(9), dp(5));
        overviewExamType.setBackground(shape(soft, 7, 0));
        examBadges.addView(overviewExamType, lp(-2, -2));
        overviewExamSession = text("", 10, ink, Typeface.BOLD);
        overviewExamSession.setPadding(dp(9), dp(5), dp(9), dp(5));
        LinearLayout.LayoutParams sessionParams = lp(-2, -2);
        sessionParams.leftMargin = dp(6);
        examBadges.addView(overviewExamSession, sessionParams);
        overviewExamBlock.addView(examBadges, topLp(-1, -2, 7));
        LinearLayout examTiming = row();
        examTiming.setGravity(Gravity.CENTER_VERTICAL);
        overviewExamWhen = text("", 13, linkInk, Typeface.BOLD);
        overviewExamWhen.setPadding(dp(11), dp(7), dp(11), dp(7));
        overviewExamWhen.setBackground(shape(soft, 8, 0));
        examTiming.addView(overviewExamWhen, lp(-2, -2));
        overviewExamDate = text("", 13, ink, Typeface.BOLD);
        overviewExamDate.setPadding(dp(10), dp(7), dp(10), dp(7));
        overviewExamDate.setSingleLine(true);
        overviewExamDate.setEllipsize(android.text.TextUtils.TruncateAt.END);
        overviewExamDate.setBackground(shape(soft, 8, 0));
        LinearLayout.LayoutParams examDateParams = new LinearLayout.LayoutParams(0, -2, 1f);
        examDateParams.leftMargin = dp(8);
        examTiming.addView(overviewExamDate, examDateParams);
        overviewExamBlock.addView(examTiming, topLp(-1, -2, 8));
        overviewExamDetails = text("", 11, muted, Typeface.NORMAL);
        overviewExamDetails.setMaxLines(2);
        overviewExamDetails.setEllipsize(android.text.TextUtils.TruncateAt.END);
        overviewExamBlock.addView(overviewExamDetails, topLp(-1, -2, 3));
        overviewExamFreshness = text("", 10, muted, Typeface.NORMAL);
        overviewExamBlock.addView(overviewExamFreshness, topLp(-1, -2, 7));
        overviewExamBlock.setVisibility(GONE);
        overviewExamSection.addView(overviewExamBlock, topLp(-1, -2, 8));
        addTop(body, overviewExamSection, 12);

        overviewScheduleBlock = column();
        overviewScheduleBlock.addView(overviewSectionHeader("Next up", "Week", () -> showPage(2)));
        overviewScheduleFreshness = text("", 10, muted, Typeface.NORMAL);
        overviewScheduleBlock.addView(overviewScheduleFreshness, topLp(-1, -2, 1));
        overviewScheduleRows = column();
        overviewScheduleBlock.addView(overviewScheduleRows, lp(-1, -2));
        overviewScheduleBlock.setVisibility(GONE);
        addTop(body, overviewScheduleBlock, 12);

        addTop(body, overviewSectionHeader("Next class", "What if?", () -> showPage(3)), 12);
        addTop(body, summary, 4);

        overviewWeekBlock = column();
        overviewWeekBlock.addView(overviewSectionHeader("This week", "Full week", () -> showPage(2)));
        overviewWeekMeta = text("Sync MyCamu to map your timetable.", 12, muted, Typeface.NORMAL);
        overviewWeekBlock.addView(overviewWeekMeta, topLp(-1, -2, 2));
        overviewWeekPulse = new WeekPulseView(getContext());
        overviewWeekPulse.setContentDescription("Weekly timetable preview. Open the full week schedule");
        overviewWeekPulse.setOnClickListener(v -> showPage(2));
        overviewWeekBlock.addView(overviewWeekPulse, topLp(-1, weekPulseHeight(), 2));
        overviewWeekBlock.setVisibility(GONE);
        addTop(body, overviewWeekBlock, 12);

        overviewAttendanceBlock = column();
        overviewAttendanceBlock.setPadding(dp(12), dp(9), dp(12), dp(9));
        overviewAttendanceBlock.setBackground(industrialSurface(surface, 12, border));
        overviewAttendanceBlock.setElevation(dp(1));
        overviewAttendanceTitle = label("LAST CLASS", 11, muted, Typeface.BOLD);
        overviewAttendanceBlock.addView(overviewAttendanceTitle);
        LinearLayout latestClassRow = row();
        latestClassRow.setGravity(Gravity.CENTER_VERTICAL);
        overviewAttendanceCourse = text("", 12, ink, Typeface.BOLD);
        overviewAttendanceCourse.setMaxLines(2);
        overviewAttendanceCourse.setEllipsize(android.text.TextUtils.TruncateAt.END);
        latestClassRow.addView(overviewAttendanceCourse, new LinearLayout.LayoutParams(0, -2, 1));
        overviewAttendanceStatus = text("", 10, ink, Typeface.BOLD);
        overviewAttendanceStatus.setGravity(Gravity.CENTER);
        overviewAttendanceStatus.setPadding(dp(9), dp(5), dp(9), dp(5));
        latestClassRow.addView(overviewAttendanceStatus, lp(-2, -2));
        overviewAttendanceBlock.addView(latestClassRow, topLp(-1, -2, 3));
        overviewAttendanceBlock.setVisibility(GONE);
        overviewAttendanceBlock.setOnClickListener(v -> { showingHistory = true; showPage(2); pageScrolls[2].scrollTo(0, 0); });
        overviewAttendanceBlock.setContentDescription("Latest class attendance. Open attendance history");
        addTop(body, overviewAttendanceBlock, 8);

        TextView dataSources = text("Data sources & sync  →", 13, linkInk, Typeface.BOLD);
        dataSources.setMinHeight(dp(48)); dataSources.setGravity(Gravity.CENTER_VERTICAL);
        dataSources.setOnClickListener(v -> openSignalPath());
        addTop(body, dataSources, 8);

        overviewCourseHeader = overviewSectionHeader("Needs attention", "All courses", () -> showPage(1));
        addTop(body, overviewCourseHeader, 12);
        body.addView(courseLists[0], lp(-1, -2));
        overviewEmptyView = text("", 12, green, Typeface.BOLD);
        addTop(body, overviewEmptyView, 6);

        addBottom(body, 24);
    }

    private FrameLayout homeWeekTicket() {
        FrameLayout ticket = new FrameLayout(getContext());
        LinearLayout face = column();
        face.setGravity(Gravity.CENTER_HORIZONTAL);
        face.setGravity(Gravity.CENTER);
        face.setPadding(dp(7), dp(7), dp(7), dp(7));
        face.setBackground(shape(raisedSurface, palette.controlRadius, border));
        homeTicketLabelView = label("TODAY", 9, muted, Typeface.BOLD);
        homeWeekNumberView = text("—", 29, ink, Typeface.BOLD);
        homeWeekMonthView = text("—", 9, muted, Typeface.BOLD);
        face.addView(homeTicketLabelView);
        face.addView(homeWeekNumberView, topLp(-1, -2, 2));
        face.addView(homeWeekMonthView, topLp(-1, -2, 2));
        ticket.addView(face, new FrameLayout.LayoutParams(-1, -1));
        homeTicketView = ticket;
        ticket.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        ticket.setFocusable(true);
        ticket.setClickable(true);
        ticket.setOnClickListener(v -> showPage(homeTicketDestination));
        setTactileFeedback(ticket);
        return ticket;
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
        addTop(body, heading("04 / SCENARIO LAB", "What if?", "Try the possibilities. Your attendance records never change."), 18);
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
        addInternalMarks(next);
        if (next.academicCourses == null || next.academicCourses.isEmpty()) {
            LinearLayout empty = column();
            empty.setPadding(dp(16), dp(16), dp(16), dp(16));
            empty.setBackground(industrialSurface(surface, 18, border));
            empty.addView(text("Semester results", 18, ink, Typeface.BOLD));
            String guidance = next.academicConnected
                    ? "KCT is connected. No completed results have been returned yet; Rollcall will keep the information on this device."
                    : "Sign in to KCT once. Rollcall will collect your semester results and next exam automatically on this device.";
            TextView detail = text(guidance,
                    13, muted, Typeface.NORMAL);
            detail.setLineSpacing(dp(2), 1f);
            empty.addView(detail, topLp(-1, -2, 8));
            empty.addView(text("Semester grades and GPA stay separate from internal assessment marks.", 12, muted, Typeface.NORMAL), topLp(-1, -2, 8));
            TextView open = text("Sign in to KCT  →", 14, palette.buttonText, Typeface.BOLD);
            open.setGravity(Gravity.CENTER);
            open.setMinHeight(dp(50));
            open.setBackground(shape(palette.button, 12, 0));
            open.setOnClickListener(v -> actions.onAcademicsPortal());
            setTactileFeedback(open);
            empty.addView(open, topLp(-1, -2, 14));
            academicsContent.addView(empty, topLp(-1, -2, 12));
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
            if (AcademicRecords.gradePoint(course.grade) >= 0 && course.credits > 0) eligible.add(course);
        }
        Collections.sort(semesters, (a, b) -> Integer.compare(AcademicRecords.semesterNumber(b), AcademicRecords.semesterNumber(a)));
        if (!selectedSemester.isEmpty() && !semesters.contains(selectedSemester)) selectedSemester = "";
        double weighted = 0, creditTotal = 0;
        for (AcademicCourse course : eligible) {
            weighted += course.credits * AcademicRecords.gradePoint(course.grade);
            creditTotal += course.credits;
        }
        double cgpa = creditTotal == 0 ? Double.NaN : weighted / creditTotal;

        LinearLayout summary = column();
        summary.setPadding(dp(18), dp(16), dp(18), dp(16));
        summary.setBackground(new EditorialCalloutDrawable(surface, border, "★", 26));
        summary.setElevation(dp(1));
        summary.addView(label("CGPA", 10, muted, Typeface.BOLD));
        TextView value = text(Double.isNaN(cgpa) ? "—" : String.format(Locale.getDefault(), "%.2f", cgpa) + " / 10",
                42, ink, Typeface.BOLD);
        value.setLetterSpacing(-.04f);
        summary.addView(value, topLp(-1, -2, 2));
        double earnedCredits = 0;
        for (AcademicCourse course : eligible)
            if (AcademicRecords.gradePoint(course.grade) >= 5) earnedCredits += course.credits;
        summary.addView(text(String.format(Locale.getDefault(), "%.1f credits counted · %.1f earned · %d semesters",
                creditTotal, earnedCredits, semesters.size()),
                13, muted, Typeface.NORMAL), topLp(-1, -2, 1));
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

        addTop(academicsContent, text("Semester results", 20, ink, Typeface.BOLD), 20);
        ArrayList<String> wheelLabels = new ArrayList<>();
        wheelLabels.add("All semesters");
        for (String semester : semesters) wheelLabels.add(AcademicRecords.semesterTitle(semester));
        LinearLayout wheelRow = row();
        wheelRow.setGravity(Gravity.CENTER_VERTICAL);
        OptionWheelView wheel = new OptionWheelView(getContext(), ink, muted, palette.highlight, uiBoldTypeface);
        wheel.setItems(wheelLabels, selectedSemester.isEmpty() ? 0 : semesters.indexOf(selectedSemester) + 1,
                index -> { selectedSemester = index == 0 ? "" : semesters.get(index - 1); updateAcademics(state); });
        TextView previous = text("PREV", 10, linkInk, Typeface.BOLD);
        previous.setGravity(Gravity.CENTER);
        previous.setMinHeight(dp(48));
        previous.setContentDescription("Previous semester");
        previous.setOnClickListener(v -> wheel.select(wheel.selection() - 1));
        wheelRow.addView(previous, lp(dp(56), dp(48)));
        wheelRow.addView(wheel, new LinearLayout.LayoutParams(0, dp(150), 1));
        TextView following = text("NEXT", 10, linkInk, Typeface.BOLD);
        following.setGravity(Gravity.CENTER);
        following.setMinHeight(dp(48));
        following.setContentDescription("Next semester");
        following.setOnClickListener(v -> wheel.select(wheel.selection() + 1));
        wheelRow.addView(following, lp(dp(56), dp(48)));
        academicsContent.addView(wheelRow);
        for (String semester : semesters) {
            if (!selectedSemester.isEmpty() && !selectedSemester.equals(semester)) continue;
            ArrayList<AcademicCourse> courses = grouped.get(semester);
            if (courses == null || courses.isEmpty()) continue;
            double termWeighted = 0, termCredits = 0;
            for (AcademicCourse course : courses) {
                int point = AcademicRecords.gradePoint(course.grade);
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
            titleText.addView(text(AcademicRecords.semesterTitle(semester) + " · SGPA", 16, ink, Typeface.BOLD));
            titleText.addView(text(String.format(Locale.getDefault(), "%d course records · %.1f GPA credits",
                    courses.size(), termCredits), 12, muted, Typeface.NORMAL), topLp(-1, -2, 2));
            title.addView(titleText, new LinearLayout.LayoutParams(0, -2, 1));
            TextView gpa = text(Double.isNaN(sgpa) ? "—" : String.format(Locale.getDefault(), "%.2f", sgpa),
                    22, palette.highlight, Typeface.BOLD);
            gpa.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            title.addView(gpa, lp(-2, -2));
            section.addView(title);
            LinearLayout courseRows = column();
            courseRows.setVisibility(GONE);
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
            section.setContentDescription(AcademicRecords.semesterTitle(semester) + ", SGPA "
                    + (Double.isNaN(sgpa) ? "not available" : String.format(Locale.getDefault(), "%.2f", sgpa))
                    + ". Double tap to show or hide course grades.");
            section.setOnClickListener(v -> courseRows.setVisibility(courseRows.getVisibility() == VISIBLE ? GONE : VISIBLE));
            View divider = new View(getContext());
            divider.setBackgroundColor(border);
            section.addView(divider, topLp(-1, dp(1), 3));
            academicsContent.addView(animated(section, "marks-" + semester), topLp(-1, -2, 4));
        }
        if (next.academicUpdatedAt > 0) {
            TextView updated = text("Last read from KCT " + relativeAge(next.academicUpdatedAt), 11, muted, Typeface.NORMAL);
            addTop(academicsContent, updated, 10);
        }
        if (!next.academicStatus.isEmpty()) addTop(academicsContent,
                text(next.academicStatus, 12, muted, Typeface.NORMAL), 4);
    }

    private void addInternalMarks(State next) {
        addTop(academicsContent, text("Internal assessments", 20, ink, Typeface.BOLD), 12);
        LinearLayout card = column();
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(industrialSurface(surface, 18, border));
        List<InternalMark> rows = next.internalMarks == null ? Collections.emptyList() : next.internalMarks;
        if (rows.isEmpty()) {
            card.addView(text("No internal marks synced", 15, ink, Typeface.BOLD));
            TextView detail = text("KCT lists internal marks in its student portal, but Rollcall has no verified student-facing data feed for them yet. Semester results remain available below.",
                    12, muted, Typeface.NORMAL);
            detail.setLineSpacing(dp(2), 1f);
            card.addView(detail, topLp(-1, -2, 6));
            TextView open = text("Open KCT portal  →", 13, palette.highlight, Typeface.BOLD);
            open.setGravity(Gravity.CENTER_VERTICAL);
            open.setMinHeight(dp(44));
            open.setOnClickListener(v -> actions.onAcademicsPortal());
            setTactileFeedback(open);
            card.addView(open, topLp(-1, -2, 4));
        } else {
            for (InternalMark mark : rows) {
                LinearLayout row = row();
                row.setGravity(Gravity.CENTER_VERTICAL);
                LinearLayout copy = column();
                copy.addView(text(mark.course, 13, ink, Typeface.BOLD));
                String subtitle = (mark.semester.isEmpty() ? "" : mark.semester + " · ") + mark.assessment
                        + (mark.date.isEmpty() ? "" : " · " + mark.date);
                copy.addView(text(subtitle, 11, muted, Typeface.NORMAL), topLp(-1, -2, 2));
                row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
                String value = mark.scoreAvailable
                        ? String.format(Locale.getDefault(), "%.1f / %.1f", mark.score, mark.maximum)
                        : mark.status.substring(0, 1).toUpperCase(Locale.ROOT) + mark.status.substring(1);
                row.addView(text(value, 13, mark.scoreAvailable ? palette.highlight : muted, Typeface.BOLD));
                card.addView(row, topLp(-1, -2, 6));
            }
        }
        academicsContent.addView(card, lp(-1, -2));
    }

    private void buildTimetablePage() {
        LinearLayout body = newPage(2);
        addTop(body, heading("03 / TIME & ATTENDANCE", "Your week", "Every class. Every record. In one place."), 18);
        scheduleUpdatedView = text("Waiting for MyCamu timetable", 11, muted, Typeface.NORMAL);

        weekRadio = new JellyRadioView(getContext(), new String[]{"SCHEDULE", "ATTENDANCE"},
                showingHistory ? 1 : 0, hero, soft, heroText, muted, uiBoldTypeface);
        weekRadio.onSelection(index -> { showingHistory = index == 1; updateTimetable(); });
        addTop(body, weekRadio, 10);
        addTop(body, scheduleUpdatedView, 8);

        scheduleContent = column();
        body.addView(scheduleContent, topLp(-1, -2, 10));
        LinearLayout weekHeading = row();
        weekHeading.setGravity(Gravity.CENTER_VERTICAL);
        weekHeading.addView(text("This week", 16, ink, Typeface.BOLD), new LinearLayout.LayoutParams(0, -2, 1));
        scheduleContent.addView(weekHeading);
        scheduleRows = column();
        temporalFold = new TimetableTransitionView(getContext(), palette, uiTypeface);
        scheduleContent.addView(scheduleRows, topLp(-1, -2, 24));
        scheduleEmptyPanel = column();
        scheduleEmptyPanel.setPadding(dp(18), dp(17), dp(18), dp(14));
        scheduleEmptyPanel.setMinimumHeight(dp(205));
        scheduleEmptyPanel.setBackground(shape(surface, 16, border));
        scheduleEmptyPanel.addView(label("THE WEEK / LIVE SCHEDULE", 9, palette.ink, Typeface.BOLD));
        scheduleEmptyTitle = text("NO CLASSES\nON YOUR\nSCHEDULE.", 24, ink, Typeface.BOLD);
        scheduleEmptyTitle.setLineSpacing(0f, .92f);
        scheduleEmptyPanel.addView(scheduleEmptyTitle, topLp(-1, -2, 12));
        scheduleEmptyDetail = text("Your connected timetable will appear here when classes are scheduled.", 13, muted, Typeface.NORMAL);
        scheduleEmptyDetail.setMaxLines(2);
        scheduleEmptyDetail.setLineSpacing(dp(2), 1f);
        scheduleEmptyPanel.addView(scheduleEmptyDetail, topLp(-1, -2, 8));
        scheduleEmptyView = text("Refresh timetable  →", 11, palette.buttonText, Typeface.BOLD);
        scheduleEmptyView.setGravity(Gravity.CENTER);
        scheduleEmptyView.setMinHeight(dp(44));
        scheduleEmptyView.setBackground(shape(palette.button, 9, 0));
        scheduleEmptyView.setTextColor(palette.buttonText);
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
        historyContent.addView(historyHeading);
        TextView leaveAndOd = text("Leave & OD records  →", 13, palette.highlight, Typeface.BOLD);
        leaveAndOd.setGravity(Gravity.CENTER_VERTICAL);
        leaveAndOd.setMinHeight(dp(48));
        leaveAndOd.setOnClickListener(v -> { showAllOd = false; openOdDetails(); });
        setTactileFeedback(leaveAndOd);
        historyContent.addView(leaveAndOd, topLp(-1, -2, 4));
        historyRows = column();
        historyContent.addView(historyRows, topLp(-1, -2, 6));
        historyEmptyView = text("Go to Home and tap refresh to load attendance history.", 12, muted, Typeface.NORMAL);
        historyEmptyView.setPadding(dp(12), dp(12), dp(12), dp(12));
        historyEmptyView.setMinHeight(dp(48));
        historyEmptyView.setTextColor(linkInk);
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
        TextView heading = text(cyberpunk ? title.toUpperCase(Locale.ROOT) : title, 21, ink, Typeface.BOLD);
        heading.setTypeface(displayTypeface);
        heading.setLetterSpacing(cyberpunk ? .04f : -.065f);
        header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        TextView button = text(action.toUpperCase(Locale.ROOT) + "  ↗", 10, linkInk, Typeface.BOLD);
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
        boolean upcomingExam = hasUpcomingExam(state);
        overviewScheduleBlock.setVisibility(nextItem != null || !upcomingExam ? VISIBLE : GONE);
        overviewWeekBlock.setVisibility(VISIBLE);
        overviewScheduleFreshness.setText(state.timetableLoaded
                ? (state.syncError ? "Saved timetable · updated " : "Timetable updated ")
                        + relativeAge(state.timetableUpdatedAt)
                : state.syncError ? "Timetable unavailable · refresh failed" : "Timetable not synced yet");
        overviewScheduleFreshness.setTextColor(state.syncError ? red : muted);
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
            if (!upcomingExam) addOverviewAgendaEmptyState();
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

    private void refreshOverviewAgenda() {
        updateDecision();
        updateOverviewSchedule();
        updateOverviewExam(state);
    }

    private void addOverviewAgendaEmptyState() {
        boolean timetableChecked = state.timetableLoaded && state.timetableUpdatedAt > 0;
        boolean examsChecked = state.examScheduleCheckedAt > 0;
        boolean allSourcesChecked = timetableChecked && examsChecked;
        LinearLayout empty = column();
        empty.setPadding(dp(16), dp(12), dp(16), dp(12));
        empty.setBackground(industrialSurface(surface, 16, border));
        String title;
        String detail;
        if (state.syncError) {
            title = allSourcesChecked ? "No upcoming items in saved schedule" : "Schedule refresh failed";
            detail = allSourcesChecked
                    ? "Showing the last checked schedule · timetable updated " + relativeAge(state.timetableUpdatedAt)
                    : "Rollcall couldn’t verify your latest class or exam schedule. Retry when your connection is ready.";
        } else if (allSourcesChecked) {
            title = "Nothing scheduled";
            detail = "No upcoming classes or exams. Timetable updated " + relativeAge(state.timetableUpdatedAt)
                    + " · exams checked " + relativeAge(state.examScheduleCheckedAt) + ".";
        } else {
            title = "Schedule isn’t available yet";
            detail = !timetableChecked && !examsChecked ? "Sync MyCamu and KCT to check classes and exams."
                    : !timetableChecked ? "Your class timetable hasn’t synced yet."
                    : "Your exam schedule hasn’t been checked yet.";
        }
        empty.addView(text(title, 15, ink, Typeface.BOLD));
        TextView explanation = text(detail, 12, muted, Typeface.NORMAL);
        explanation.setLineSpacing(dp(2), 1f);
        empty.addView(explanation, topLp(-1, -2, 4));
        if (!allSourcesChecked || state.syncError) {
            TextView retry = text("Refresh schedule  →", 12, palette.highlight, Typeface.BOLD);
            retry.setGravity(Gravity.CENTER_VERTICAL);
            retry.setMinHeight(dp(44));
            retry.setOnClickListener(v -> {
                if (state.sessionExpired) actions.onPortal(); else actions.onSync();
            });
            setTactileFeedback(retry);
            empty.addView(retry, topLp(-1, -2, 5));
        }
        overviewScheduleRows.addView(empty, lp(-1, -2));
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
        boolean upcoming = hasUpcomingExam(next);
        boolean hasNextClass = nextScheduledClass(System.currentTimeMillis()) != null;
        overviewExamSection.setVisibility(upcoming ? VISIBLE : GONE);
        overviewScheduleBlock.setVisibility(hasNextClass || !upcoming ? VISIBLE : GONE);
        if (!upcoming) return;
        overviewExamBlock.setVisibility(VISIBLE);
        overviewExamWhen.setVisibility(VISIBLE);
        overviewExamDate.setVisibility(VISIBLE);
        overviewExamTitle.setText(next.upcomingExamTitle);
        overviewExamType.setText(next.upcomingExamType);
        overviewExamType.setVisibility(next.upcomingExamType.isEmpty() ? GONE : VISIBLE);
        overviewExamSession.setText(next.upcomingExamSession);
        overviewExamSession.setVisibility(next.upcomingExamSession.isEmpty() ? GONE : VISIBLE);
        int sessionColor = amber;
        overviewExamSession.setTextColor(sessionColor);
        overviewExamSession.setBackground(shape(statusTint(sessionColor), 8, 0));
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
        overviewExamWhen.setText(countdown);
        overviewExamDate.setText(format.format(new Date(next.upcomingExamAt)));
        String details = next.upcomingExamCode;
        if (!next.upcomingExamDetails.isEmpty())
            details += (details.isEmpty() ? "" : " · ") + next.upcomingExamDetails;
        overviewExamDetails.setText(details);
        overviewExamDetails.setVisibility(details.isEmpty() ? GONE : VISIBLE);
        String examFreshness = next.examScheduleCheckedAt > 0
                ? "Exams checked " + relativeAge(next.examScheduleCheckedAt)
                : "Exam check time unavailable";
        String timetableFreshness = next.timetableLoaded
                ? (next.syncError ? "saved timetable updated " : "timetable updated ")
                        + relativeAge(next.timetableUpdatedAt)
                : "class timetable not synced";
        overviewExamFreshness.setText(examFreshness + " · " + timetableFreshness);
        overviewExamBlock.setContentDescription("Upcoming exam: " + next.upcomingExamTitle
                + (next.upcomingExamType.isEmpty() ? "" : ", " + next.upcomingExamType)
                + (next.upcomingExamSession.isEmpty() ? "" : ", session " + next.upcomingExamSession)
                + ", " + overviewExamWhen.getText() + ", " + overviewExamDate.getText()
                + (details.isEmpty() ? "" : ", " + details));
    }

    private boolean hasUpcomingExam(State next) {
        SimpleDateFormat dateKey = new SimpleDateFormat("yyyyMMdd", Locale.ROOT);
        return next.upcomingExamAt > 0
                && (next.upcomingExamHasTime ? next.upcomingExamAt > System.currentTimeMillis()
                : dateKey.format(new Date(next.upcomingExamAt)).compareTo(dateKey.format(new Date())) >= 0)
                && !next.upcomingExamTitle.isEmpty();
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
        selected.setBackground(shape(palette.button, 11, 0));
        other.setBackground(shape(soft, 11, border));
        ((TextView) selected.getChildAt(0)).setTextColor(palette.buttonText);
        ((TextView) selected.getChildAt(1)).setTextColor(palette.buttonText);
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
        weekRadio.select(showingHistory ? 1 : 0, false);
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
        ArrayList<TimetableTransitionView.Event> events = new ArrayList<>();
        for (ScheduleItem item : state.timetable) events.add(new TimetableTransitionView.Event(item.id,
                item.name, item.details, item.time, item.startsAt, item.endsAt, item.allDay, item.cancelled,
                state.approvedOdClasses.contains(odClassKey(item.code, item.startsAt))));
        temporalFold.update(events, state.timetableLoaded, state.syncError || state.sessionExpired
                || state.timetableUpdatedAt <= 0 || System.currentTimeMillis() - state.timetableUpdatedAt > 24 * 60 * 60_000L);
        scheduleRows.addView(temporalFold, lp(-1, -2));
        boolean hasSchedule = state.timetableLoaded && !state.timetable.isEmpty();
        boolean noClassesScheduled = !state.sessionExpired && !state.syncError
                && state.timetableLoaded && state.timetable.isEmpty();
        scheduleEmptyPanel.setVisibility(hasSchedule ? GONE : VISIBLE);
        scheduleEmptyTitle.setText(state.sessionExpired ? "RECONNECT TO\nSEE YOUR WEEK."
                : noClassesScheduled ? "NO CLASSES\nSCHEDULED."
                : state.syncError ? "YOUR WEEK\nNEEDS A\nREFRESH."
                : "YOUR WEEK\nIS WAITING.");
        scheduleEmptyTitle.setTextColor(ink);
        scheduleEmptyDetail.setText(state.sessionExpired
                ? "Sign in to MyCamu again to refresh your timetable."
                : noClassesScheduled
                    ? "There are no classes scheduled for this week."
                    : state.syncError ? "Rollcall could not refresh the timetable. Try again when the connection is ready."
                    : "Sync MyCamu to display your connected classes here.");
        scheduleEmptyDetail.setTextColor(muted);
        scheduleEmptyView.setText(state.sessionExpired ? "Reconnect MyCamu  →"
                : noClassesScheduled ? ""
                : state.syncError ? "Try again  →" : "Sync timetable  →");
        scheduleEmptyView.setContentDescription(state.sessionExpired ? "Reconnect MyCamu to load the timetable"
                : "Refresh timetable from MyCamu");
        scheduleEmptyView.setVisibility(noClassesScheduled ? GONE : VISIBLE);
        scheduleEmptyPanel.setClickable(!noClassesScheduled);
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
        View bottomSpace = new View(getContext());
        bottomSpace.setTag("rollcall-scroll-bottom-space");
        body.addView(bottomSpace, lp(1, dp(112)));
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
        nav.setVisibility(VISIBLE);
        odDetailsPage.setVisibility(VISIBLE);
        odDetailsPage.scrollTo(0, 0);
        odDetailsPage.bringToFront();
        animatePageArrival(odDetailsPage, 1);
        updateNavigation();
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

    private View courseTrace(Course course) {
        LinearLayout panel = column();
        ArrayList<HistorySeries.Event> events = new ArrayList<>();
        for (ClassMarkItem mark : state.classMarks) {
            if (course.code.isEmpty() || !normalizeCourseCode(course.code).equals(normalizeCourseCode(mark.code))) continue;
            String key = odClassKey(mark.code, mark.startsAt);
            if (!state.currentSemesterClassKeys.contains(key)) continue;
            String label = mark.name + " · " + (mark.startsAt > 0 ? mark.day + " · " + mark.time : "Time unavailable")
                    + " · " + mark.status.replace('_', ' ');
            boolean od = state.approvedOdClasses.contains(key);
            if (od) label += " · Approved OD (recorded status unchanged)";
            events.add(new HistorySeries.Event(mark.id, label, mark.status, mark.startsAt, od));
        }
        HistorySeries series = new HistorySeries(events, course.attended, course.total, course.odReconciled);
        panel.addView(text("Attendance trace", 14, ink, Typeface.BOLD));
        panel.addView(text(series.events.isEmpty() ? "Individual class marks have not been loaded for this course."
                : series.complete ? "Complete recorded history · Solid line: cumulative attendance"
                : "Partial history · " + series.events.size() + " available events. This timeline does not reconstruct a percentage.", 12, muted, Typeface.NORMAL));
        TextView detail = text("", 13, ink, Typeface.NORMAL);
        detail.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        ClassHistoryTraceView trace = new ClassHistoryTraceView(getContext(), series, ink, green, red, muted,
                i -> detail.setText((i + 1) + " / " + series.events.size() + " · " + series.events.get(i).label
                        + (series.complete ? String.format(Locale.getDefault(), " · %.1f%%", series.percentages[i]) : "")));
        panel.addView(trace, topLp(-1, dp(series.complete ? 140 : 64), 8));
        panel.addView(detail, topLp(-1, -2, 4));
        if (!series.events.isEmpty()) {
            trace.select(series.events.size() - 1, false);
            LinearLayout controls = row();
            for (int direction : new int[]{-1, 1}) {
                TextView control = text(direction < 0 ? "Previous record" : "Next record", 13, linkInk, Typeface.BOLD);
                control.setMinHeight(dp(48)); control.setGravity(Gravity.CENTER);
                control.setOnClickListener(v -> trace.select(trace.selected() + direction, true));
                controls.addView(control, new LinearLayout.LayoutParams(0, -2, 1));
            }
            panel.addView(controls);
            TextView legend = text("● Present · ■ Absent · ○ Unknown · Underline: approved OD", 12, muted, Typeface.NORMAL);
            panel.addView(legend);
        } else {
            TextView sync = text("Sync class history", 13, linkInk, Typeface.BOLD);
            sync.setMinHeight(dp(48)); sync.setGravity(Gravity.CENTER_VERTICAL);
            sync.setOnClickListener(v -> { if (state.sessionExpired) actions.onPortal(); else actions.onSync(); });
            panel.addView(sync);
        }
        return panel;
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
        scroll.setTag("rollcall-page-scroll-" + index);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        if (cyberpunk) scroll.setBackground(new ScanlineDrawable());
        else scroll.setBackgroundColor(canvas);
        LinearLayout body = column();
        body.setPadding(dp(18), 0, dp(18), dp(16));
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

    private View topBar(boolean showTheme) {
        LinearLayout bar = row();
        RollcallLogo logo = new RollcallLogo(getContext(), palette);
        logo.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        bar.addView(logo, lp(dp(34), dp(34)));
        TextView brand = text("rollcall.", 24, ink, Typeface.BOLD);
        SpannableString wordmark = new SpannableString("rollcall.");
        wordmark.setSpan(new android.text.style.ForegroundColorSpan(palette.highlight),
                8, 9, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        brand.setText(wordmark);
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
            ImageView settings = new ImageView(getContext());
            settings.setImageResource(R.drawable.ic_nav_settings);
            settings.setColorFilter(ink);
            settings.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            settings.setPadding(dp(12), dp(12), dp(12), dp(12));
            settings.setBackground(null);
            settings.setContentDescription("Settings");
            settings.setOnClickListener(v -> actions.onAbout());
            setTactileFeedback(settings);
            bar.addView(settings, lp(dp(48), dp(48)));
        }
        FrameLayout avatar = new FrameLayout(getContext());
        TextView initials = text("R", 12, ink, Typeface.BOLD);
        initials.setGravity(Gravity.CENTER);
        initials.setBackground(new android.graphics.drawable.InsetDrawable(shape(soft, 100, 0), dp(4)));
        avatar.addView(initials, new FrameLayout.LayoutParams(-1, -1));
        ImageView photo = new ImageView(getContext());
        photo.setTag("profile-photo-avatar");
        photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable photoShape = new GradientDrawable();
        photoShape.setShape(GradientDrawable.OVAL);
        photoShape.setColor(soft);
        photo.setBackground(photoShape);
        photo.setClipToOutline(true);
        photo.setVisibility(GONE);
        avatar.addView(photo, new FrameLayout.LayoutParams(-1, -1));
        avatar.setContentDescription("Profile and account");
        avatar.setFocusable(true);
        avatar.setClickable(true);
        avatar.setOnClickListener(v -> actions.onProfile());
        setTactileFeedback(avatar);
        avatars.add(initials);
        avatarPhotos.add(photo);
        bar.addView(avatar, lp(dp(48), dp(48)));
        return bar;
    }

    private View heading(String index, String title, String subtitle) {
        LinearLayout box = column();
        box.addView(label(index, 10, linkInk, Typeface.BOLD));
        SpannableString titleText = new SpannableString(title + ".");
        titleText.setSpan(new android.text.style.ForegroundColorSpan(palette.highlight), title.length(), titleText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        TextView heading = text(titleText.toString(), 35, ink, Typeface.BOLD);
        heading.setText(titleText);
        heading.setTypeface(displayTypeface, Typeface.BOLD);
        heading.setLetterSpacing(-.075f);
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
        TextView adjust = text("Adjust target  ↗", 10, linkInk, Typeface.BOLD);
        adjust.setOnClickListener(v -> actions.onTarget());
        card.addView(adjust);
        card.setOnClickListener(v -> actions.onTarget());
        return card;
    }


    private View filters() {
        coursesRadio = new JellyRadioView(getContext(),
                new String[]{"All courses · " + state.courses, "Below target · " + state.below},
                belowOnly ? 1 : 0, hero, soft, heroText, muted, uiBoldTypeface);
        allCoursesFilter = coursesRadio.chip(0);
        focusCoursesFilter = coursesRadio.chip(1);
        coursesRadio.onSelection(index -> setFilter(index == 1));
        return coursesRadio;
    }

    private TextView filterChip(String label) {
        TextView chip = text(label, 10, muted, Typeface.BOLD);
        chip.setPadding(dp(11), dp(8), dp(11), dp(8));
        chip.setGravity(Gravity.CENTER);
        chip.setMinHeight(dp(34));
        chip.setBackground(shape(soft, 10, border));
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
        field.setBackground(shape(soft, 9, border));
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
        LinearLayout card = column();
        card.setPadding(0, 0, 0, 0);
        card.addView(label("CHOOSE A COURSE", 10, muted, Typeface.BOLD));
        coursePicker = new FrameLayout(getContext());
        coursePicker.setBackground(shape(soft, 9, border));
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
        card.addView(coursePicker, topLp(-1, dp(56), 12));
        LinearLayout result = column();
        result.setPadding(dp(20), dp(20), dp(20), dp(20));
        result.setBackground(shape(surface, 16, border));
        result.addView(label("YOUR ATTENDANCE / PROJECTED", 12, palette.highlight, Typeface.BOLD));
        LinearLayout comparison = row();
        comparison.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout current = column();
        current.addView(text("CURRENT", 11, muted, Typeface.BOLD));
        plannerCurrentPercentView = text("—", 35, ink, Typeface.BOLD);
        plannerCurrentPercentView.setIncludeFontPadding(false);
        current.addView(plannerCurrentPercentView, topLp(-1, -2, 7));
        comparison.addView(current, new LinearLayout.LayoutParams(0, -2, 1));
        comparison.addView(text("→", 25, muted, Typeface.BOLD), lp(-2, -2));
        LinearLayout projection = column();
        projection.setGravity(Gravity.RIGHT);
        TextView after = text("AFTER PLAN", 11, muted, Typeface.BOLD);
        after.setGravity(Gravity.RIGHT);
        projection.addView(after);
        projectionView = text("—", 35, palette.button, Typeface.BOLD);
        projectionView.setGravity(Gravity.RIGHT);
        projectionView.setIncludeFontPadding(false);
        projection.addView(projectionView, topLp(-1, -2, 7));
        comparison.addView(projection, new LinearLayout.LayoutParams(0, -2, 1));
        result.addView(comparison, topLp(-1, -2, 21));
        projectionStatusView = text("Counts unavailable", 13, muted, Typeface.BOLD);
        result.addView(projectionStatusView, topLp(-1, -2, 17));
        card.addView(result, topLp(-1, -2, 19));

        forecastPlot = new ForecastPlotView(getContext(), ink, palette.highlight, muted);
        card.addView(forecastPlot, topLp(-1, dp(172), 12));
        forecastLegend = text("", 12, muted, Typeface.NORMAL);
        card.addView(forecastLegend, topLp(-1, -2, 4));
        plannerBaseline = text("", 13, ink, Typeface.NORMAL);
        card.addView(plannerBaseline, topLp(-1, -2, 10));
        odNotice = text("", 13, ink, Typeface.NORMAL);
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

        plannerRadio = new JellyRadioView(getContext(), new String[]{"MISS", "ATTEND"},
                plannerAttend ? 1 : 0, hero, soft, heroText, muted, uiBoldTypeface);
        plannerRadio.onSelection(index -> {
            plannerAttend = index == 1;
            plannerFeedback(plannerRadio.chip(index));
        });
        card.addView(label("SIMULATE / CLASSES", 12, ink, Typeface.BOLD), topLp(-1, -2, 18));
        card.addView(plannerRadio, topLp(-1, -2, 10));

        // Native implementation of the prototype's 1–20 class slider.
        LinearLayout countRow = row();
        countRow.addView(label("NUMBER OF CLASSES", 13, ink, Typeface.BOLD),
                new LinearLayout.LayoutParams(0, -2, 1));
        plannerCountView = text("01", 30, palette.button, Typeface.BOLD);
        plannerCountView.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        countRow.addView(plannerCountView);
        card.addView(countRow, topLp(-1, -2, 20));

        SeekBar amount = new SeekBar(getContext());
        forecastAmount = amount;
        amount.setMax(20);
        amount.setProgress(plannerCount);
        GradientDrawable sliderTrack = new GradientDrawable();
        sliderTrack.setShape(GradientDrawable.RECTANGLE);
        sliderTrack.setColor(track);
        sliderTrack.setCornerRadius(dp(6));
        GradientDrawable sliderFill = new GradientDrawable();
        sliderFill.setShape(GradientDrawable.RECTANGLE);
        sliderFill.setColor(palette.button);
        sliderFill.setCornerRadius(dp(6));
        android.graphics.drawable.LayerDrawable slider = new android.graphics.drawable.LayerDrawable(
                new android.graphics.drawable.Drawable[]{sliderTrack,
                        new android.graphics.drawable.ClipDrawable(sliderFill, Gravity.LEFT,
                                android.graphics.drawable.ClipDrawable.HORIZONTAL)});
        slider.setId(0, android.R.id.background);
        slider.setId(1, android.R.id.progress);
        slider.setLayerHeight(0, dp(10));
        slider.setLayerHeight(1, dp(10));
        slider.setLayerGravity(0, Gravity.CENTER_VERTICAL);
        slider.setLayerGravity(1, Gravity.CENTER_VERTICAL);
        amount.setProgressDrawable(slider);
        GradientDrawable sliderThumb = new GradientDrawable();
        sliderThumb.setShape(GradientDrawable.OVAL);
        sliderThumb.setColor(palette.button);
        sliderThumb.setStroke(dp(2), palette.buttonText);
        sliderThumb.setSize(dp(26), dp(26));
        amount.setThumb(sliderThumb);
        amount.setThumbOffset(dp(13));
        amount.setSplitTrack(false);
        amount.setMinimumHeight(dp(52));
        amount.setPadding(dp(13), 0, dp(13), 0);
        amount.setContentDescription("Number of hypothetical classes, zero to twenty");
        int[] lastHapticProgress = {amount.getProgress()};
        amount.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                if (!fromUser) return;
                plannerCount = value;
                updatePlanner();
                if (value != lastHapticProgress[0]) {
                    lastHapticProgress[0] = value;
                    SignalDrawing.tick(bar);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        card.addView(amount, topLp(-1, dp(56), 8));
        LinearLayout range = row();
        for (String mark : new String[]{"0", "5", "10", "15", "20"}) {
            TextView m = text(mark, 10, muted, Typeface.NORMAL);
            m.setGravity(Gravity.CENTER);
            range.addView(m, new LinearLayout.LayoutParams(0, -2, 1));
        }
        card.addView(range);
        LinearLayout steps = row();
        for (String action : new String[]{"− Class", "Reset", "+ Class"}) {
            TextView control = text(action, 14, ink, Typeface.BOLD);
            control.setGravity(Gravity.CENTER);
            control.setMinHeight(dp(48));
            control.setContentDescription(action.equals("Reset") ? "Reset hypothetical classes to zero" : action);
            control.setOnClickListener(v -> {
                int next = action.equals("Reset") ? 0 : Math.max(0, Math.min(20, plannerCount + (action.startsWith("+") ? 1 : -1)));
                if (next != plannerCount) { plannerCount = next; SignalDrawing.tick(v); }
                if (action.equals("Reset")) odMode = 0;
                amount.setProgress(plannerCount);
                updatePlanner();
            });
            steps.addView(control, new LinearLayout.LayoutParams(0, -2, 1));
        }
        card.addView(steps);

        plannerFeedback = text("Choose a course to explore your options.", 13, muted, Typeface.BOLD);
        card.addView(plannerFeedback, topLp(-1, -2, 6));
        
        return card;
    }

    private void buildNavigation() {
        FrameLayout holder = new FrameLayout(getContext());
        holder.setPadding(dp(16), dp(7), dp(16), dp(10));
        holder.setTag("rollcall-floating-navigation-holder");
        holder.setClipChildren(false);
        FrameLayout.LayoutParams holderParams = new FrameLayout.LayoutParams(-1, -2,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        addView(holder, holderParams);
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int navWidth = Math.min(dp(390), Math.max(dp(260), screenWidth - dp(32)));
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setTag("rollcall-floating-navigation");
        nav.setPadding(dp(6), dp(6), dp(6), dp(6));
        nav.setGravity(Gravity.CENTER);
        navBackground = new NavBackgroundDrawable();
        nav.setBackground(navBackground);
        nav.setElevation(dp(10));
        holder.addView(nav, new FrameLayout.LayoutParams(navWidth, dp(64), Gravity.CENTER));
        int[] icons = {R.drawable.ic_nav_home, R.drawable.ic_nav_courses, R.drawable.ic_nav_week,
                R.drawable.ic_nav_academics, R.drawable.ic_nav_more};
        String[] labels = {"Home", "Courses", "Week", "Marks", "More"};
        for (int i = 0; i < labels.length; i++) {
            final int page = i;
            LinearLayout tab = new LinearLayout(getContext());
            tab.setOrientation(LinearLayout.HORIZONTAL);
            tab.setGravity(Gravity.CENTER);
            tab.setFocusable(true);
            tab.setMinimumHeight(dp(52));
            tab.setPadding(dp(5), 0, dp(5), 0);
            tab.setOnClickListener(v -> {
                if (page == 4) toggleMoreMenu();
                else showPage(page == 3 ? 4 : page);
            });
            setTactileFeedback(tab);
            ImageView icon = new ImageView(getContext());
            icon.setImageResource(icons[i]);
            icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            TextView label = text(labels[i], 12, muted, Typeface.BOLD);
            label.setSingleLine(true);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            label.setGravity(Gravity.CENTER);
            icon.setVisibility(VISIBLE);
            if (i == 4) {
                FrameLayout iconFrame = new FrameLayout(getContext());
                iconFrame.addView(icon, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
                moreUpdatesDot = new View(getContext());
                moreUpdatesDot.setBackground(shape(palette.highlight, 100, palette.highlight));
                moreUpdatesDot.setVisibility(GONE);
                FrameLayout.LayoutParams dotParams = new FrameLayout.LayoutParams(dp(7), dp(7), Gravity.TOP | Gravity.RIGHT);
                iconFrame.addView(moreUpdatesDot, dotParams);
                tab.addView(iconFrame, lp(dp(25), dp(25)));
            } else tab.addView(icon, lp(dp(22), dp(22)));
            LinearLayout.LayoutParams labelParams = lp(-2, -2);
            labelParams.leftMargin = dp(5);
            tab.addView(label, labelParams);
            nav.addView(tab, new LinearLayout.LayoutParams(0, -1, .86f));
            navTabs[i] = tab;
            navIcons[i] = icon;
            navItems[i] = label;
        }
        updateNavigation();
    }

    private void buildMoreMenu() {
        moreOverlay = new FrameLayout(getContext());
        moreOverlay.setVisibility(GONE);
        moreOverlay.setFocusableInTouchMode(true);
        moreOverlay.setContentDescription("More destinations");
        moreOverlay.setOnClickListener(v -> closeMoreMenu(true));
        moreOverlay.setOnKeyListener((v, keyCode, event) -> {
            if ((keyCode == android.view.KeyEvent.KEYCODE_ESCAPE
                    || keyCode == android.view.KeyEvent.KEYCODE_BACK)
                    && event.getAction() == android.view.KeyEvent.ACTION_UP) {
                closeMoreMenu(true);
                return true;
            }
            return false;
        });

        moreMenuPanel = column();
        moreMenuPanel.setTag("rollcall-more-menu");
        moreMenuPanel.setPadding(dp(6), dp(6), dp(6), dp(6));
        moreMenuPanel.setBackground(shape(raisedSurface, 18, border));
        moreMenuPanel.setElevation(dp(16));
        moreMenuPanel.setOnClickListener(v -> { });
        addMoreMenuItem("Updates", android.R.drawable.ic_dialog_info, this::showAnnouncements);
        addMenuDivider();
        addMoreMenuItem("What If?", R.drawable.ic_nav_lab, () -> showPage(3));
        addMenuDivider();
        addMoreMenuItem("Leave & OD", R.drawable.ic_nav_week, this::openOdDetails);
        addMenuDivider();
        addMoreMenuItem("Settings", R.drawable.ic_nav_settings, () -> {
            if (odDetailsOpen) closeOdDetails();
            actions.onAbout();
        });

        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(dp(220), -2,
                Gravity.BOTTOM | Gravity.RIGHT);
        panelParams.rightMargin = dp(16);
        panelParams.bottomMargin = dp(82);
        moreOverlay.addView(moreMenuPanel, panelParams);
        addView(moreOverlay, new FrameLayout.LayoutParams(-1, -1));
    }

    private void addMoreMenuItem(String title, int iconResource, Runnable action) {
        LinearLayout item = row();
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(dp(10), 0, dp(10), 0);
        item.setMinimumHeight(dp(48));
        item.setFocusable(true);
        item.setClickable(true);
        item.setContentDescription("More: " + title);
        ImageView icon = new ImageView(getContext());
        icon.setImageResource(iconResource);
        icon.setColorFilter(ink);
        item.addView(icon, lp(dp(20), dp(20)));
        TextView label = text(title, 14, ink, Typeface.BOLD);
        if ("Updates".equals(title)) moreUpdatesLabel = label;
        LinearLayout.LayoutParams labelParams = lp(0, -2);
        labelParams.leftMargin = dp(11);
        labelParams.weight = 1;
        item.addView(label, labelParams);
        TextView arrow = text("›", 19, muted, Typeface.NORMAL);
        item.addView(arrow, lp(-2, -2));
        item.setOnClickListener(v -> {
            closeMoreMenu(false);
            action.run();
        });
        moreMenuPanel.addView(item, lp(-1, -2));
    }

    private void showAnnouncements() {
        ScrollView scroll = new ScrollView(getContext());
        LinearLayout content = column();
        content.setPadding(dp(16), dp(8), dp(16), dp(12));
        LinearLayout heading = row();
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.addView(label("ROLLCALL UPDATES", 10, lime, Typeface.BOLD),
                new LinearLayout.LayoutParams(0, -2, 1));
        announcementStatus = text("Checking for updates…", 10, muted, Typeface.NORMAL);
        announcementStatus.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        heading.addView(announcementStatus, lp(-2, -2));
        content.addView(heading);
        announcementList = column();
        content.addView(announcementList, topLp(-1, -2, 8));
        scroll.addView(content);
        AlertDialog dialog = new AlertDialog.Builder(getContext(), palette.dark
                ? R.style.RollcallDialogDark : R.style.RollcallDialogLight)
                .setTitle("Updates")
                .setView(scroll)
                .setPositiveButton("Done", null)
                .create();
        dialog.setOnDismissListener(ignored -> {
            announcementList = null;
            announcementStatus = null;
        });
        dialog.show();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(shape(raisedSurface, 18, border));
        if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(lime);
        updateAnnouncements(state);
    }

    private void addMenuDivider() {
        View divider = new View(getContext());
        divider.setBackgroundColor(border);
        LinearLayout.LayoutParams params = lp(-1, dp(1));
        params.leftMargin = dp(10);
        params.rightMargin = dp(10);
        moreMenuPanel.addView(divider, params);
    }

    private void toggleMoreMenu() {
        if (moreMenuOpen) closeMoreMenu(true);
        else {
            moreMenuOpen = true;
            moreOverlay.setAlpha(1f);
            moreMenuPanel.setAlpha(0f);
            moreMenuPanel.setTranslationY(dp(8));
            moreOverlay.setVisibility(VISIBLE);
            moreOverlay.requestFocus();
            moreMenuPanel.animate().alpha(1f).translationY(0f).setDuration(190)
                    .setInterpolator(new android.view.animation.PathInterpolator(.22f, 1f, .36f, 1f)).start();
            updateNavigation();
        }
    }

    private void closeMoreMenu(boolean animate) {
        if (!moreMenuOpen) return;
        moreMenuOpen = false;
        if (animate && (android.os.Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled())) {
            moreMenuPanel.animate().alpha(0f).translationY(dp(5)).setDuration(130)
                    .withEndAction(() -> moreOverlay.setVisibility(GONE)).start();
        } else {
            moreMenuPanel.animate().cancel();
            moreOverlay.setVisibility(GONE);
        }
        updateNavigation();
    }

    boolean closeMoreMenuForBack() {
        if (!moreMenuOpen) return false;
        closeMoreMenu(false);
        return true;
    }

    private void showPage(int page) {
        if (page < 0 || page >= pageScrolls.length || page == selectedPage && !odDetailsOpen) return;
        if (odDetailsOpen) {
            odDetailsOpen = false;
            odDetailsPage.setVisibility(GONE);
        }
        ScrollView incoming = pageScrolls[page];
        if (incoming == null) return;

        // Update visibility synchronously so rapid taps cannot hide the newly selected page.
        int direction = page > selectedPage ? 1 : -1;
        selectedPage = page;
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
        if (page == 0) actions.onHomePageShown();
    }

    boolean isHomePageSelected() { return selectedPage == 0 && !odDetailsOpen; }

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
        // The source prototype fades from 50% and rises 10px; do not side-swipe screens.
        page.setTranslationY(dp(10));
        page.setAlpha(.5f);
        page.animate().translationY(0f).alpha(1f).setStartDelay(0).setDuration(260)
                .setInterpolator(new android.view.animation.PathInterpolator(.22f, .75f, .3f, 1f)).start();
    }

    /** Repairs page visibility after window reattachment or a background data refresh. */
    void restoreSelectedPage() {
        if (odDetailsOpen && odDetailsPage != null) {
            for (ScrollView candidate : pageScrolls) if (candidate != null) candidate.setVisibility(GONE);
            nav.setVisibility(VISIBLE);
            odDetailsPage.setVisibility(VISIBLE);
            odDetailsPage.bringToFront();
            updateNavigation();
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
        nav.setVisibility(VISIBLE);
        int selectedTab = moreMenuOpen || odDetailsOpen || selectedPage == 3
                ? 4 : selectedPage == 4 ? 3 : selectedPage;
        boolean selectionChanged = false;
        for (int i = 0; i < navItems.length; i++) {
            TextView item = navItems[i];
            boolean selected = i == selectedTab;
            selectionChanged |= navTabs[i].isSelected() != selected;
            item.setVisibility(selected ? VISIBLE : GONE);
            item.setTypeface(item.getTypeface(), Typeface.BOLD);
            item.setTextColor(selected ? palette.buttonText : muted);
            navIcons[i].setColorFilter(selected ? palette.buttonText : muted);
            navTabs[i].setBackground(null);
            LinearLayout.LayoutParams tabParams = (LinearLayout.LayoutParams) navTabs[i].getLayoutParams();
            float weight = selected ? 1.6f : .85f;
            if (tabParams.weight != weight) {
                tabParams.weight = weight;
                navTabs[i].setLayoutParams(tabParams);
            }
            navTabs[i].setSelected(selected);
            if (selectionChanged) {
                navIcons[i].animate().cancel();
                navIcons[i].setTranslationY(0f);
                if (selected && navIcons[i].isShown()) settleFeedback(navIcons[i]);
            }
            navTabs[i].setContentDescription(new String[]{"Home", "Courses", "Week", "Marks and academics", "More"}[i]
                    + (i == 4 && moreUpdatesDot != null && moreUpdatesDot.getVisibility() == VISIBLE ? ", updates available" : "")
                    + (selected ? ", selected" : ""));
            item.setContentDescription(new String[]{"Overview", "Courses", "Timetable", "KCT marks and academics", "More"}[i]
                    + (selected ? ", selected" : ""));
        }
        if (navBackground != null) navBackground.moveTo(selectedTab, selectionChanged && nav.isShown());
    }

    private void setFilter(boolean focus) {
        belowOnly = focus;
        refreshVisibleRows();
        coursesRadio.select(focus ? 1 : 0, false);
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
            ((TextView) chip).setTextColor(selected ? heroText : muted);
            chip.setBackground(cyberpunk && selected ? new CutCornerDrawable(hero, hero, green, dp(7), dp(2))
                    : shape(selected ? hero : soft, 9, selected ? hero : 0));
            chip.setSelected(selected);
            if (changed && selected && chip.isShown()) settleFeedback(chip);
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
        coursesRadio.select(belowOnly ? 1 : 0, false);
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
            list.addView(animated(item.root, "course-group-" + group.lecture.code), topLp(-1, -2, 8));
        }
    }

    private void addCourseRow(LinearLayout list, Course course, Course pairedLecture, int topMargin) {
        CourseRow item = new CourseRow(course, pairedLecture, list == courseLists[0]);
        courseViews.add(item);
        LinearLayout.LayoutParams params = topLp(-1, -2, topMargin);
        list.addView(animated(item.root, (item.overviewPreview ? "home-course-" : "course-") + course.code), params);
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
            badgePaint.setTypeface(uiBoldTypeface);
            badgePaint.setTextSize(badgeTextSize);
            return (int) Math.ceil(badgePaint.measureText("LAB") + dp(12));
        }

        @Override public void draw(Canvas canvas, CharSequence text, int start, int end, float x,
                                   int top, int y, int bottom, Paint textPaint) {
            badgePaint.setTypeface(uiBoldTypeface);
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
        NativeMotion.enableScrollDissolve(list, dp(20));
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
        coursePopup.setBackgroundDrawable(shape(raisedSurface, 16, border));
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
                    ? "No classes this week" : state.timetableLoaded ? "No upcoming class" : "Timetable not synced");
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

    private int courseThemeColor(Course course) {
        return course.below ? red : green;
    }

    private String normalizeCourseCode(String code) {
        return code == null ? "" : code.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private String odClassKey(String code, long startsAt) {
        return normalizeCourseCode(code) + ":" + (startsAt / 60_000L);
    }

    private void updatePlanner() {
        if (plannerCountView == null) return;
        plannerCountView.setText(String.format(Locale.ROOT, "%02d", plannerCount));
        plannerRadio.select(plannerAttend ? 1 : 0, false);
        Course course = state.rows.isEmpty() ? null : state.rows.get(Math.min(plannerCourse, state.rows.size() - 1));
        if (plannerBaseline != null) plannerBaseline.setText(course == null ? "" : course.percentLabel
                + " now · " + course.counts + (course.targetCount.isEmpty() ? "" : "\n" + course.targetCount)
                + odCoverageSummary(course));
        if (course == null || !course.countsKnown || course.total < 0) {
            odOptions.setVisibility(GONE);
            odNotice.setText(course != null && course.odCount > 0
                    ? course.odCount + (course.odCount == 1 ? " OD class is mapped" : " OD classes are mapped")
                        + ". Sync attendance counts before applying an OD calculation."
                    : "Approved OD appears after your class log syncs.");
            plannerFeedback.setText("Sync your class counts to explore your options.");
            plannerFeedback.setTextColor(muted);
            projectionView.setText("—");
            plannerCurrentPercentView.setText("—");
            projectionView.setTextColor(muted);
            projectionStatusView.setText("Counts unavailable");
            projectionStatusView.setTextColor(muted);
            forecastPlot.setSeries(null);
            forecastLegend.setText("Attendance counts unavailable. Sync MyCamu to plot a forecast.");
            forecastLegend.setOnClickListener(v -> actions.onSync());
            forecastLegend.setMinHeight(dp(48));
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
        ForecastSeries series = new ForecastSeries(new Attendance(course.code, course.name, course.attended, course.total),
                baseline, plannerAttend, plannerCount, state.target);
        forecastPlot.setSeries(series);
        forecastLegend.setOnClickListener(null);
        forecastLegend.setText("Solid dot: recorded · Dashed: hypothetical" + (series.odPreview ? " · OD preview applied" : "")
                + "\n0–" + plannerCount + " added classes · 0–100% attendance · Target " + state.target + "%"
                + (series.crossing() < 0 ? "" : "\nTarget crossed at " + series.crossing() + " hypothetical classes."));
        if (baseline.total + plannerCount == 0) {
            plannerCurrentPercentView.setText("—"); projectionView.setText("—");
            projectionStatusView.setText("No classes recorded");
            plannerFeedback.setText("Add hypothetical classes to explore a plan. Recorded attendance is unchanged.");
            return;
        }
        double projected = series.points[plannerCount];
        plannerCurrentPercentView.setText(String.format(Locale.getDefault(), "%.1f%%", baseline.percentage()));
        Attendance forecast = new Attendance(course.code, course.name,
                baseline.attended + (plannerAttend ? plannerCount : 0), baseline.total + plannerCount);
        plannerFeedback.setText(projected >= state.target
                ? "This plan keeps you at your " + state.target + "% target or above."
                : state.target >= 100 ? "This plan stays below your 100% target."
                : "After this plan, attend " + forecast.periodsToRecover(state.target)
                    + " consecutive classes to reach " + state.target + "%.");
        plannerFeedback.setTextColor(projected >= state.target ? green : red);
        projectionView.setText(String.format(Locale.getDefault(), "%.1f%%", projected));
        projectionView.setTextColor(projected >= state.target ? green : red);
        projectionStatusView.setText(String.format(Locale.getDefault(), "%+.1f POINTS · %s",
                projected - baseline.percentage(), projected >= state.target ? "ON TRACK" : "BELOW TARGET"));
        projectionStatusView.setTextColor(projected >= state.target ? green : red);
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
        card.setPadding(dp(RollcallSpacing.SPACE_5), dp(RollcallSpacing.SPACE_5),
                dp(RollcallSpacing.SPACE_5), dp(RollcallSpacing.SPACE_5));
        card.setBackground(industrialSurface(surface, palette.cardRadius, border));
        return card;
    }

    private AnimatedContentView animated(View content, String key) {
        return new AnimatedContentView(getContext(), content, revealedContent.contains(key),
                () -> revealedContent.add(key));
    }

    private TextView text(String value, float size, int color, int style) {
        TextView view = new TextView(getContext());
        view.setText(value);
        view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, size);
        view.setTextColor(color);
        view.setTypeface(style == Typeface.BOLD ? uiBoldTypeface : uiTypeface);
        view.setFontFeatureSettings("tnum");
        view.setIncludeFontPadding(false);
        return view;
    }

    private TextView label(String value, float size, int color, int style) {
        TextView view = text(value, size, color, style);
        view.setTypeface(cyberpunk ? displayTypeface : uiBoldTypeface);
        view.setLetterSpacing(palette.themeIndex == AppPalette.SIGNAL ? .09f : .055f);
        return view;
    }

    private Button compactButton(String value, int background, int foreground) {
        Button button = new Button(getContext());
        button.setText(value);
        button.setTextSize(20);
        button.setTypeface(uiBoldTypeface);
        button.setGravity(Gravity.CENTER);
        button.setIncludeFontPadding(false);
        button.setTextColor(foreground);
        button.setAllCaps(false);
        button.setPadding(0, 0, 0, 0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setBackground(shape(background, palette.controlRadius, 0));
        setTactileFeedback(button);
        return button;
    }

    private Button roundedButton(String value, int background, int foreground) {
        Button button = compactButton(value, background, foreground);
        button.setTextSize(13);
        button.setTypeface(uiBoldTypeface);
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
    private float sp(float value) { return value * getResources().getDisplayMetrics().scaledDensity; }
    private GradientDrawable shape(int color, int radius, int stroke) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(themeRadius(radius)));
        if (stroke != 0) d.setStroke(dp(1), stroke); return d;
    }

    private int themeRadius(int radius) {
        return radius >= 24 ? palette.featureRadius
                : radius >= 16 && radius <= 22 ? palette.cardRadius
                : radius >= 10 && radius <= 14 ? palette.controlRadius : radius;
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

    /** Non-interactive printed marks from the source's GPA and projection cards. */
    private final class EditorialCalloutDrawable extends Drawable {
        private final int background, mark, markAlpha;
        private final String glyph;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF boundsRect = new RectF();

        EditorialCalloutDrawable(int background, int mark, String glyph, int alpha) {
            this.background = background;
            this.mark = mark;
            this.glyph = glyph;
            this.markAlpha = alpha;
        }

        @Override public void draw(Canvas canvas) {
            Rect area = getBounds();
            boundsRect.set(area);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(background);
            canvas.drawRoundRect(boundsRect, dp(17), dp(17), paint);
            canvas.save();
            Path clip = new Path();
            clip.addRoundRect(boundsRect, dp(17), dp(17), Path.Direction.CW);
            canvas.clipPath(clip);
            paint.setColor(mark);
            paint.setAlpha(markAlpha);
            paint.setTypeface(displayTypeface);
            paint.setTextSize(sp(125));
            paint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(glyph, area.right + dp(15), area.top + dp(97), paint);
            canvas.restore();
            paint.setAlpha(255);
        }
        @Override public void setAlpha(int alpha) { invalidateSelf(); }
        @Override public void setColorFilter(android.graphics.ColorFilter filter) { invalidateSelf(); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    private final class NavBackgroundDrawable extends Drawable {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint active = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF outer = new RectF();
        private final RectF selected = new RectF();
        private float position;

        NavBackgroundDrawable() {
            fill.setColor(raisedSurface);
            outline.setColor(Color.argb(170,
                    Color.red(border), Color.green(border), Color.blue(border)));
            outline.setStyle(Paint.Style.STROKE);
            outline.setStrokeWidth(dp(1));
            active.setColor(palette.button);
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
            float outerRadius = dp(Math.min(palette.navRadius,
                    RollcallShape.capsuleRadiusDp(bounds.height() / getResources().getDisplayMetrics().density)));
            canvas.drawRoundRect(outer, outerRadius, outerRadius, fill);
            canvas.drawRoundRect(outer, outerRadius, outerRadius, outline);
            if (bounds.width() == 0 || navTabs[0] == null) return;
            int first = Math.max(0, Math.min(navTabs.length - 1, (int) position));
            int second = Math.min(navTabs.length - 1, first + 1);
            float progress = position - first;
            View start = navTabs[first], end = navTabs[second];
            if (start.getWidth() == 0 || end.getWidth() == 0) return;
            float leftStart = start.getLeft() + dp(1);
            float rightStart = start.getRight() - dp(1);
            float leftEnd = end.getLeft() + dp(1);
            float rightEnd = end.getRight() - dp(1);
            float left = bounds.left + leftStart + (leftEnd - leftStart) * progress;
            float right = bounds.left + rightStart + (rightEnd - rightStart) * progress;
            selected.set(left, bounds.top + dp(6), right, bounds.bottom - dp(6));
            float selectionInset = Math.max(0f,
                    (bounds.height() / getResources().getDisplayMetrics().density
                            - selected.height() / getResources().getDisplayMetrics().density) / 2f);
            float selectedRadius = dp(Math.min(
                    RollcallShape.capsuleRadiusDp(selected.height() / getResources().getDisplayMetrics().density),
                    RollcallShape.innerRadiusDp(palette.navRadius, selectionInset)));
            canvas.drawRoundRect(selected, selectedRadius, selectedRadius, active);
        }

        @Override public void setAlpha(int alpha) { fill.setAlpha(alpha); outline.setAlpha(alpha); active.setAlpha(alpha); invalidateSelf(); }
        @Override public void setColorFilter(android.graphics.ColorFilter filter) {
            fill.setColorFilter(filter); outline.setColorFilter(filter); active.setColorFilter(filter); invalidateSelf();
        }
        @Override public int getOpacity() { return PixelFormat.OPAQUE; }
    }

    private final class CourseGroupRow {
        final CourseGroup group;
        final LinearLayout root;
        private final ArrayList<Course> subjects = new ArrayList<>();

        CourseGroupRow(CourseGroup group) {
            this.group = group;
            root = card();
            root.setPadding(dp(17), dp(15), dp(17), dp(15));
            subjects.add(group.lecture);
            subjects.addAll(group.labs);

            LinearLayout header = row();
            header.setGravity(Gravity.CENTER_VERTICAL);
            TextView courseName = text(group.lecture.name, 16, ink, Typeface.BOLD);
            courseName.setMaxLines(2);
            courseName.setEllipsize(android.text.TextUtils.TruncateAt.END);
            header.addView(courseName, new LinearLayout.LayoutParams(0, -2, 1));
            boolean atRisk = group.priority() < state.target;
            int statusColor = atRisk ? red : green;
            int badgeBackground = statusTint(statusColor);
            int badgeForeground = statusColor;
            TextView percentage = text(group.lecture.percentLabel, 13, badgeForeground, Typeface.BOLD);
            percentage.setGravity(Gravity.CENTER);
            percentage.setPadding(dp(10), dp(8), dp(10), dp(8));
            percentage.setBackground(shape(badgeBackground, 9, 0));
            LinearLayout.LayoutParams percentageParams = lp(-2, -2);
            percentageParams.leftMargin = dp(10);
            header.addView(percentage, percentageParams);
            header.setOnClickListener(v -> actions.onCourse(group.lecture.code));
            header.setContentDescription(courseHeading(group.lecture) + (atRisk ? ", needs focus" : ", on track")
                    + ". Tap to expand course guidance.");
            root.addView(header);
            View tape = new View(getContext());
            tape.setBackground(shape(statusColor, 0, 0));
            LinearLayout.LayoutParams tapeParams = topLp(dp(48), dp(5), 5);
            tapeParams.gravity = Gravity.RIGHT;
            root.addView(tape, tapeParams);

            // Full-width rows keep both attendance decisions readable, including lab/theory pairs.
            LinearLayout meters = column();
            for (int i = 0; i < subjects.size(); i++) {
                Course subject = subjects.get(i);
                String kind = isLabCourse(subject.code) ? "LAB" : "THEORY";
                if (isLabCourse(group.lecture.code) && subject == group.lecture) kind = "LAB";
                LinearLayout metric = metric(subject, kind);
                LinearLayout.LayoutParams metricParams = topLp(-1, -2, i == 0 ? 0 : 8);
                meters.addView(metric, metricParams);
            }
            root.addView(meters, topLp(-1, -2, 2));
            for (Course subject : subjects) {
                if (!subject.expanded) continue;
                TextView guidance = text(subject.guidance, 10, muted, Typeface.NORMAL);
                guidance.setPadding(dp(2), dp(8), dp(2), 0);
                guidance.setContentDescription(subject.status + ". " + subject.guidance);
                root.addView(guidance);
                root.addView(courseTrace(subject), topLp(-1, -2, 12));
                TextView planner = text("Try in What if?  →", 12, linkInk, Typeface.BOLD);
                planner.setPadding(dp(2), dp(8), 0, dp(8));
                planner.setMinHeight(dp(48));
                planner.setGravity(Gravity.CENTER_VERTICAL);
                planner.setOnClickListener(v -> openPlannerFor(subject.code));
                root.addView(planner);
            }
            StringBuilder description = new StringBuilder(courseHeading(group.lecture));
            for (Course subject : subjects) {
                description.append(". ").append(isLabCourse(subject.code) ? "Lab" : "Theory")
                        .append(" ").append(subject.percentLabel).append(", ").append(subject.counts);
            }
            root.setContentDescription(description.toString());
            root.setFocusable(true);
        }

        private LinearLayout metric(Course subject, String kind) {
            LinearLayout box = column();
            box.setPadding(dp(2), dp(6), dp(2), dp(6));
            box.setFocusable(true);
            String identity = kind + (subject.code.isEmpty() ? "" : " / " + subject.code);
            box.addView(label(identity, 10, muted, Typeface.BOLD));
            int statusColor = courseThemeColor(subject);
            TextView percent = text(subject.percentLabel, 15, statusColor, Typeface.BOLD);
            box.addView(percent, topLp(-1, -2, 2));

            final String canMiss;
            final String mustAttend;
            if (!subject.countsKnown || subject.total <= 0 || state.target <= 0) {
                canMiss = "—";
                mustAttend = "—";
            } else {
                double target = state.target;
                int available = Math.max(0, (int) Math.floor(
                        (100d * subject.attended - target * subject.total) / target + 1e-9d));
                canMiss = Integer.toString(available);
                if (100d * subject.attended >= target * subject.total - 1e-9d) {
                    mustAttend = "0";
                } else if (target >= 100d) {
                    mustAttend = "N/A";
                } else {
                    mustAttend = Integer.toString(Math.max(0, (int) Math.ceil(
                            (target * subject.total - 100d * subject.attended) / (100d - target) - 1e-9d)));
                }
            }
            LinearLayout stats = row();
            stats.addView(attendanceMetricTile("CAN MISS", canMiss, !subject.below && !"—".equals(canMiss),
                    false), new LinearLayout.LayoutParams(0, -2, 1f));
            LinearLayout.LayoutParams second = new LinearLayout.LayoutParams(0, -2, 1f);
            second.leftMargin = dp(8);
            stats.addView(attendanceMetricTile("MUST ATTEND", mustAttend, false, subject.below), second);
            box.addView(stats, topLp(-1, -2, 7));
            AttendanceProgressBar meter = new AttendanceProgressBar(getContext(), subject.code,
                    subject.percent, statusColor);
            meter.setContentDescription("Attendance " + subject.percentLabel);
            box.addView(meter, topLp(-1, dp(7), 9));
            String detail = subject.countsKnown ? subject.attended + " / " + subject.total + " classes"
                    : "Attendance counts unavailable";
            box.addView(text(detail, 11, muted, Typeface.NORMAL), topLp(-1, -2, 4));
            box.setContentDescription(kind + " " + courseHeading(subject) + ". "
                    + subject.percentLabel + ". Can miss " + canMiss
                    + ". Must attend " + ("N/A".equals(mustAttend) ? "not recoverable at 100% target" : mustAttend)
                    + ". " + detail);
            box.setOnClickListener(v -> actions.onCourse(subject.code));
            return box;
        }

        private LinearLayout attendanceMetricTile(String title, String value, boolean positive, boolean warning) {
            LinearLayout tile = column();
            tile.setGravity(Gravity.CENTER);
            tile.setPadding(dp(8), dp(10), dp(8), dp(10));
            boolean strongSignalFill = palette.themeIndex == AppPalette.SIGNAL && (positive || warning);
            int statusColor = positive ? green : warning ? amber : ink;
            int background = positive ? (strongSignalFill ? hero : statusTint(statusColor))
                    : warning ? (strongSignalFill ? AppPalette.SIGNAL_COURSE_ATTEND_FILL
                    : statusTint(statusColor)) : surface;
            int foreground = strongSignalFill ? heroText : statusColor;
            int stroke = strongSignalFill ? 0 : positive || warning ? statusColor : border;
            tile.setBackground(shape(background, 10, stroke));
            TextView labelText = text(title, 10, foreground, Typeface.BOLD);
            labelText.setGravity(Gravity.CENTER);
            tile.addView(labelText);
            TextView number = text(value, 31, foreground, Typeface.BOLD);
            number.setGravity(Gravity.CENTER);
            number.setIncludeFontPadding(false);
            tile.addView(number, topLp(-1, -2, 4));
            return tile;
        }

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

    private int weekPulseHeight() {
        float fontScale = getResources().getConfiguration().fontScale;
        return dp(76 + Math.max(0f, fontScale - 1f) * 40f);
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
            float baseY = getHeight() - sp(10) - sp(11) - dp(12);
            float graphTop = Math.max(dp(20), sp(12) + dp(8));
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
            paint.setTypeface(uiBoldTypeface);
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
                canvas.drawText(labels[i], x, getHeight() - sp(11) - dp(4), paint);
                paint.setTextSize(sp(11));
                paint.setColor(i == today ? ink : muted);
                canvas.drawText(String.valueOf(date.get(Calendar.DAY_OF_MONTH)), x, getHeight() - dp(2), paint);
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
            int statusColor = courseThemeColor(course);
            TextView icon = text(course.below ? "!" : "▤", 15, statusColor, Typeface.BOLD);
            icon.setGravity(Gravity.CENTER);
            icon.setBackground(shape(soft, 9, statusColor));
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
            TextView pct = text(course.percentLabel, 15, statusColor, Typeface.BOLD);
            pct.setGravity(Gravity.RIGHT);
            pct.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
            percent.addView(pct);
            AttendanceProgressBar bar = new AttendanceProgressBar(getContext(), course.code, course.percent, statusColor);
            bar.setContentDescription("Attendance " + course.percentLabel);
            LinearLayout.LayoutParams barParams = topLp(dp(64), dp(7), 4);
            barParams.gravity = Gravity.RIGHT;
            percent.addView(bar, barParams);
            if (!course.targetCount.isEmpty()) {
                TextView targetCount = text(course.targetCount, 10, statusColor, Typeface.BOLD);
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
            detail.addView(text(course.status, 11, statusColor, Typeface.BOLD));
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


}
