package com.iniexe.rollcall;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Build;
import android.widget.RemoteViews;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

final class RollcallWidgets {
    enum Type { NEXT, ATTENDANCE, WEEK, STREAK, REMINDER }
    private enum Size { COMPACT, MEDIUM, WIDE }

    private RollcallWidgets() { }

    static void update(Context context, AppWidgetManager manager, int[] widgetIds, Type type) {
        LiveSnapshotStore.initialize(context);
        for (int id : widgetIds) manager.updateAppWidget(id, build(context, manager, id, type));
    }

    static void refresh(Context context) {
        Context app = context.getApplicationContext();
        LiveSnapshotStore.initialize(app);
        AppWidgetManager manager = AppWidgetManager.getInstance(app);
        Class<?>[] providers = {RollcallTodayWidget.class, AttendanceHealthWidget.class,
                RollcallWeekWidget.class, RollcallStreakWidget.class, RollcallReminderWidget.class};
        Type[] types = Type.values();
        for (int i = 0; i < providers.length; i++) {
            int[] ids = manager.getAppWidgetIds(new ComponentName(app, providers[i].asSubclass(AppWidgetProvider.class)));
            update(app, manager, ids, types[i]);
        }
    }

    private static RemoteViews build(Context context, AppWidgetManager manager, int id, Type type) {
        LiveSnapshotStore.Snapshot snapshot = LiveSnapshotStore.get();
        SharedPreferences preferences = context.getSharedPreferences(MainActivity.class.getSimpleName(), Context.MODE_PRIVATE);
        boolean dark = preferences.getBoolean("dark_theme", false);
        int paletteIndex = preferences.contains("color_theme") ? preferences.getInt("color_theme", 0)
                : preferences.getInt(dark ? "dark_palette" : "light_palette", 0);
        if (paletteIndex != 5 && paletteIndex != 6) paletteIndex = 0;
        AppPalette palette = AppPalette.get(dark, paletteIndex);
        Size size = size(manager.getAppWidgetOptions(id));
        int layout = type == Type.NEXT ? R.layout.widget_today
                : type == Type.ATTENDANCE ? R.layout.widget_attendance
                : type == Type.WEEK ? R.layout.widget_week
                : type == Type.STREAK ? R.layout.widget_streak : R.layout.widget_reminder;
        RemoteViews views = new RemoteViews(context.getPackageName(), layout);
        views.setInt(R.id.widget_root, "setBackgroundColor", palette.surface);
        int padding = dp(context, size == Size.COMPACT ? 10 : size == Size.MEDIUM ? 12 : 14);
        views.setViewPadding(R.id.widget_root, padding, padding, padding, padding);
        views.setTextColor(R.id.widget_kicker, palette.muted);
        views.setTextColor(R.id.widget_updated, palette.muted);
        switch (type) {
            case NEXT: renderNext(context, views, snapshot, palette, preferences, size); break;
            case ATTENDANCE: renderAttendance(context, views, snapshot, palette, preferences, size); break;
            case WEEK: renderWeek(context, views, snapshot, palette, size); break;
            case STREAK: renderStreak(context, views, snapshot, palette, size); break;
            case REMINDER: renderReminder(context, views, snapshot, palette, size); break;
        }
        long updatedAt = type == Type.ATTENDANCE ? snapshot.attendanceAt
                : type == Type.STREAK ? snapshot.classMarksAt
                : type == Type.REMINDER || type == Type.WEEK ? snapshot.timetableAt
                : oldestAvailable(snapshot.attendanceAt, snapshot.timetableAt);
        views.setTextViewText(R.id.widget_updated, updatedAt == 0
                ? context.getString(R.string.widget_waiting_for_sync)
                : context.getString(R.string.widget_updated, relativeAge(updatedAt)));
        views.setViewVisibility(R.id.widget_updated,
                size == Size.WIDE ? android.view.View.VISIBLE : android.view.View.GONE);
        String destination = type == Type.REMINDER ? "settings"
                : type == Type.ATTENDANCE || type == Type.STREAK ? "overview" : "week";
        Intent launch = new Intent(context, MainActivity.class)
                .setAction("com.iniexe.rollcall.WIDGET." + type.name())
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.EXTRA_WIDGET_DESTINATION, destination);
        android.app.PendingIntent pending = android.app.PendingIntent.getActivity(context, id, launch,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_root, pending);
        return views;
    }

    private static void renderNext(Context context, RemoteViews views, LiveSnapshotStore.Snapshot snapshot,
                                   AppPalette palette, SharedPreferences preferences, Size size) {
        views.setTextViewText(R.id.widget_kicker, context.getString(R.string.widget_kicker));
        views.setViewVisibility(R.id.widget_kicker, size == Size.COMPACT
                ? android.view.View.GONE : android.view.View.VISIBLE);
        views.setTextViewTextSize(R.id.widget_time, android.util.TypedValue.COMPLEX_UNIT_SP, size == Size.COMPACT ? 20 : 22);
        views.setTextViewTextSize(R.id.widget_course, android.util.TypedValue.COMPLEX_UNIT_SP, size == Size.COMPACT ? 14 : 15);
        views.setTextColor(R.id.widget_course, palette.ink);
        views.setTextColor(R.id.widget_time, palette.highlight);
        views.setTextColor(R.id.widget_attendance, palette.green);
        views.setTextColor(R.id.widget_location, palette.muted);
        LiveSnapshotStore.ClassSession next = nextClass(snapshot.timetable, System.currentTimeMillis());
        if (next == null) {
            String course = context.getString(snapshot.timetableAt == 0
                    ? R.string.widget_sync_timetable : R.string.widget_no_upcoming);
            String time = context.getString(snapshot.timetableAt == 0
                    ? R.string.widget_open_to_import : R.string.widget_view_week);
            String attendance = overallAttendance(context, snapshot.attendance,
                    preferences.getInt("attendance_target", 75));
            views.setTextViewText(R.id.widget_course, course);
            views.setTextViewText(R.id.widget_time, time);
            views.setTextViewText(R.id.widget_attendance, attendance);
            views.setTextViewText(R.id.widget_location, "");
            views.setContentDescription(R.id.widget_root,
                    context.getString(R.string.widget_accessibility_next, course, time, attendance));
        } else {
            String name = next.name.isEmpty() ? next.code : next.name;
            Attendance row = matchingCourse(snapshot.attendance, next.code);
            String attendance = row == null ? context.getString(R.string.widget_course_attendance_unavailable)
                    : row.total == 0 && row.countsKnown ? context.getString(R.string.widget_no_classes_recorded)
                    : Math.round(row.percentage()) + "% · " + targetStatus(context, row.percentage(), preferences.getInt("attendance_target", 75));
            views.setTextViewText(R.id.widget_course, name);
            views.setTextViewText(R.id.widget_time, classTime(next.startsAt, System.currentTimeMillis()));
            views.setTextViewText(R.id.widget_attendance, attendance);
            String location = !next.location.isEmpty() ? next.location : next.code;
            views.setTextViewText(R.id.widget_location, location);
            views.setContentDescription(R.id.widget_root,
                    context.getString(R.string.widget_accessibility_next, name, classTime(next.startsAt, System.currentTimeMillis()), attendance));
        }
        boolean expanded = size != Size.COMPACT;
        views.setViewVisibility(R.id.widget_attendance, expanded ? android.view.View.VISIBLE : android.view.View.GONE);
        views.setViewVisibility(R.id.widget_location, expanded ? android.view.View.VISIBLE : android.view.View.GONE);
    }

    private static void renderAttendance(Context context, RemoteViews views, LiveSnapshotStore.Snapshot snapshot,
                                         AppPalette palette, SharedPreferences preferences, Size size) {
        int target = preferences.getInt("attendance_target", 75);
        RollcallWidgetMetrics.AttendanceSummary summary = RollcallWidgetMetrics.attendance(snapshot.attendance, target);
        views.setTextViewText(R.id.widget_kicker, context.getString(R.string.widget_attendance_title));
        views.setViewVisibility(R.id.widget_kicker, size == Size.COMPACT
                ? android.view.View.GONE : android.view.View.VISIBLE);
        views.setTextViewTextSize(R.id.widget_attendance_percent, android.util.TypedValue.COMPLEX_UNIT_SP,
                size == Size.COMPACT ? 30 : 38);
        views.setTextViewTextSize(R.id.widget_attendance_target, android.util.TypedValue.COMPLEX_UNIT_SP,
                size == Size.COMPACT ? 11 : 12);
        views.setTextColor(R.id.widget_attendance_percent, summary.percentage < 0 ? palette.muted
                : summary.percentage >= target ? palette.green : palette.red);
        views.setTextViewText(R.id.widget_attendance_percent, summary.percentage < 0
                ? "—" : summary.percentage + "%");
        views.setTextViewText(R.id.widget_attendance_target, summary.percentage < 0
                ? context.getString(R.string.widget_attendance_after_sync)
                : context.getString(summary.percentage >= target
                        ? R.string.widget_meets_target : R.string.widget_below_target, target));
        views.setTextColor(R.id.widget_attendance_target, palette.muted);
        views.setTextViewText(R.id.widget_attendance_track, summary.percentage < 0
                ? context.getString(R.string.widget_attendance_after_sync)
                : context.getString(R.string.widget_course_track, summary.onTrack, summary.onTrack + summary.belowTarget));
        views.setTextColor(R.id.widget_attendance_track, palette.muted);
        if (summary.percentage >= 0) {
            views.setProgressBar(R.id.widget_attendance_progress, 100, summary.percentage, false);
            if (Build.VERSION.SDK_INT >= 31) views.setColorStateList(R.id.widget_attendance_progress,
                    "setProgressTintList", ColorStateList.valueOf(summary.percentage >= target ? palette.green : palette.red));
        }
        views.setViewVisibility(R.id.widget_attendance_progress, size == Size.COMPACT || summary.percentage < 0
                ? android.view.View.GONE : android.view.View.VISIBLE);
        views.setViewVisibility(R.id.widget_attendance_track, size == Size.COMPACT
                ? android.view.View.GONE : android.view.View.VISIBLE);
        StringBuilder attention = new StringBuilder();
        for (int i = 0; i < Math.min(summary.attentionCourses.size(), size == Size.WIDE ? 3 : 2); i++) {
            Attendance row = summary.attentionCourses.get(i);
            if (attention.length() > 0) attention.append("\n");
            attention.append(row.name).append(" · ").append(Math.round(row.percentage())).append('%');
        }
        views.setTextViewText(R.id.widget_attention_courses, attention.length() == 0
                ? context.getString(summary.percentage < 0 ? R.string.widget_no_attendance_yet : R.string.widget_all_courses_on_track)
                : attention.toString());
        views.setTextColor(R.id.widget_attention_courses, attention.length() == 0 ? palette.muted : palette.red);
        views.setViewVisibility(R.id.widget_attention_courses, size == Size.WIDE
                ? android.view.View.VISIBLE : android.view.View.GONE);
        views.setContentDescription(R.id.widget_root, summary.percentage < 0
                ? context.getString(R.string.widget_attendance_after_sync)
                : context.getString(R.string.widget_accessibility_attendance, summary.percentage,
                        summary.onTrack, summary.onTrack + summary.belowTarget));
    }

    private static void renderWeek(Context context, RemoteViews views, LiveSnapshotStore.Snapshot snapshot,
                                   AppPalette palette, Size size) {
        views.setTextViewText(R.id.widget_kicker, context.getString(R.string.widget_week_title));
        views.setViewVisibility(R.id.widget_kicker, size == Size.COMPACT
                ? android.view.View.GONE : android.view.View.VISIBLE);
        views.setTextViewTextSize(R.id.widget_week_summary, android.util.TypedValue.COMPLEX_UNIT_SP,
                size == Size.COMPACT ? 17 : 20);
        views.setTextViewTextSize(R.id.widget_week_next, android.util.TypedValue.COMPLEX_UNIT_SP,
                size == Size.COMPACT ? 11 : 13);
        Calendar start = Calendar.getInstance();
        start.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        start.set(Calendar.HOUR_OF_DAY, 0); start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0); start.set(Calendar.MILLISECOND, 0);
        long weekStart = start.getTimeInMillis();
        Calendar end = (Calendar) start.clone(); end.add(Calendar.DAY_OF_YEAR, 7);
        int[] dayCounts = new int[7];
        int total = 0;
        for (LiveSnapshotStore.ClassSession session : snapshot.timetable) {
            if (session.allDay || session.cancelled || session.startsAt < weekStart || session.startsAt >= end.getTimeInMillis()) continue;
            Calendar day = Calendar.getInstance(); day.setTimeInMillis(session.startsAt);
            int index = (day.get(Calendar.DAY_OF_WEEK) + 5) % 7;
            dayCounts[index]++; total++;
        }
        int campusDays = 0;
        for (int count : dayCounts) if (count > 0) campusDays++;
        List<LiveSnapshotStore.ClassSession> upcoming = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (LiveSnapshotStore.ClassSession session : snapshot.timetable) {
            if (session.allDay || session.cancelled || session.startsAt < now || session.startsAt >= end.getTimeInMillis()) continue;
            upcoming.add(session);
            if (upcoming.size() == 3) break;
        }
        views.setTextViewText(R.id.widget_week_summary, total == 0
                ? context.getString(snapshot.timetableAt == 0 ? R.string.widget_sync_timetable : R.string.widget_week_empty)
                : context.getString(R.string.widget_week_count, total, campusDays));
        LiveSnapshotStore.ClassSession next = upcoming.isEmpty() ? null : upcoming.get(0);
        views.setTextViewText(R.id.widget_week_next, next == null ? context.getString(R.string.widget_no_upcoming)
                : next.name + " · " + classTime(next.startsAt, System.currentTimeMillis()));
        StringBuilder agenda = new StringBuilder();
        for (LiveSnapshotStore.ClassSession session : upcoming) {
            if (agenda.length() > 0) agenda.append('\n');
            agenda.append(new SimpleDateFormat("EEE", Locale.getDefault()).format(new Date(session.startsAt)))
                    .append(" · ").append(new SimpleDateFormat("h:mm a", Locale.getDefault()).format(new Date(session.startsAt)))
                    .append("  ").append(session.name);
        }
        views.setTextViewText(R.id.widget_week_agenda, agenda.length() == 0
                ? context.getString(R.string.widget_no_upcoming) : agenda.toString());
        views.setTextColor(R.id.widget_week_summary, palette.highlight);
        views.setTextColor(R.id.widget_week_next, palette.ink);
        views.setTextColor(R.id.widget_week_agenda, palette.muted);
        views.setViewVisibility(R.id.widget_week_agenda, size == Size.COMPACT
                ? android.view.View.GONE : android.view.View.VISIBLE);
        views.setViewVisibility(R.id.widget_week_days, size == Size.WIDE
                ? android.view.View.VISIBLE : android.view.View.GONE);
        SimpleDateFormat dayName = new SimpleDateFormat("EE", Locale.getDefault());
        Calendar today = Calendar.getInstance();
        for (int i = 0; i < 7; i++) {
            Calendar day = (Calendar) start.clone(); day.add(Calendar.DAY_OF_YEAR, i);
            int labelId = weekLabelId(i), countId = weekCountId(i);
            views.setTextViewText(labelId, dayName.format(day.getTime()).toUpperCase(Locale.ROOT));
            views.setTextViewText(countId, String.valueOf(dayCounts[i]));
            int color = sameDay(day, today) ? palette.highlight : palette.ink;
            views.setTextColor(labelId, color); views.setTextColor(countId, color);
        }
        views.setContentDescription(R.id.widget_root, context.getString(R.string.widget_accessibility_week, total, campusDays));
    }

    private static void renderStreak(Context context, RemoteViews views, LiveSnapshotStore.Snapshot snapshot,
                                     AppPalette palette, Size size) {
        int streak = RollcallWidgetMetrics.currentStreak(snapshot.classMarks);
        views.setTextViewText(R.id.widget_kicker, context.getString(R.string.widget_streak_title));
        views.setViewVisibility(R.id.widget_kicker, size == Size.COMPACT
                ? android.view.View.GONE : android.view.View.VISIBLE);
        views.setTextViewTextSize(R.id.widget_streak_count, android.util.TypedValue.COMPLEX_UNIT_SP,
                size == Size.COMPACT ? 32 : 42);
        views.setTextViewText(R.id.widget_streak_count, streak < 0 ? "—" : String.valueOf(streak));
        views.setTextColor(R.id.widget_streak_count, streak == 0 ? palette.red : palette.highlight);
        views.setTextViewText(R.id.widget_streak_label, streak < 0
                ? context.getString(R.string.widget_streak_empty)
                : context.getString(R.string.widget_streak_present, streak == 1 ? "class" : "classes"));
        List<String> recent = RollcallWidgetMetrics.recentConfirmedStatuses(snapshot.classMarks, 7);
        for (int i = 0; i < 7; i++) {
            int marker = i - (7 - recent.size());
            int color = marker < 0 ? palette.track : "present".equals(recent.get(marker)) ? palette.green : palette.red;
            views.setTextViewText(streakDotId(i), marker < 0 ? "·" : "●");
            views.setTextColor(streakDotId(i), color);
        }
        views.setViewVisibility(R.id.widget_streak_dots, size == Size.COMPACT
                ? android.view.View.GONE : android.view.View.VISIBLE);
        views.setContentDescription(R.id.widget_root, streak < 0 ? context.getString(R.string.widget_streak_empty)
                : context.getString(R.string.widget_accessibility_streak, streak));
    }

    private static void renderReminder(Context context, RemoteViews views, LiveSnapshotStore.Snapshot snapshot,
                                       AppPalette palette, Size size) {
        boolean enabled = ClassReminderScheduler.enabled(context);
        boolean allowed = ClassReminderScheduler.canNotify(context);
        views.setTextViewText(R.id.widget_kicker, context.getString(R.string.widget_reminder_title));
        views.setViewVisibility(R.id.widget_kicker, size == Size.COMPACT
                ? android.view.View.GONE : android.view.View.VISIBLE);
        views.setTextViewTextSize(R.id.widget_reminder_status, android.util.TypedValue.COMPLEX_UNIT_SP,
                size == Size.COMPACT ? 22 : 26);
        views.setTextViewText(R.id.widget_reminder_status, enabled && allowed
                ? context.getString(R.string.widget_reminders_on) : context.getString(R.string.widget_reminders_off));
        views.setTextColor(R.id.widget_reminder_status, enabled && allowed ? palette.green : palette.amber);
        views.setTextViewText(R.id.widget_reminder_permission, !allowed
                ? context.getString(R.string.widget_permission_blocked)
                : enabled ? context.getString(R.string.widget_reminders_explainer)
                : context.getString(R.string.widget_reminders_disabled));
        views.setTextColor(R.id.widget_reminder_permission, palette.muted);
        List<LiveSnapshotStore.ClassSession> future = futureClasses(snapshot.timetable, System.currentTimeMillis(), 2);
        views.setTextViewText(R.id.widget_reminder_next, future.isEmpty()
                ? context.getString(snapshot.timetableAt == 0 ? R.string.widget_sync_timetable : R.string.widget_no_upcoming)
                : reminderLine(future.get(0), System.currentTimeMillis()));
        StringBuilder later = new StringBuilder();
        for (int i = 1; i < future.size(); i++) {
            if (later.length() > 0) later.append('\n');
            later.append(reminderLine(future.get(i), System.currentTimeMillis()));
        }
        views.setTextViewText(R.id.widget_reminder_later, later.toString());
        views.setTextColor(R.id.widget_reminder_next, palette.ink);
        views.setTextColor(R.id.widget_reminder_later, palette.muted);
        views.setViewVisibility(R.id.widget_reminder_later, size == Size.WIDE && !later.toString().isEmpty()
                ? android.view.View.VISIBLE : android.view.View.GONE);
        views.setViewVisibility(R.id.widget_reminder_permission, size == Size.COMPACT
                ? (!allowed ? android.view.View.VISIBLE : android.view.View.GONE) : android.view.View.VISIBLE);
        views.setViewVisibility(R.id.widget_reminder_next, size == Size.COMPACT && !allowed
                ? android.view.View.GONE : android.view.View.VISIBLE);
        views.setContentDescription(R.id.widget_root,
                context.getString(R.string.widget_accessibility_reminder, enabled && allowed ? "on" : "off"));
    }

    private static String reminderLine(LiveSnapshotStore.ClassSession session, long now) {
        String time = new SimpleDateFormat("EEE · h:mm a", Locale.getDefault()).format(new Date(session.startsAt));
        String title = session.name.isEmpty() ? session.code : session.name;
        return session.startsAt - 10 * 60_000L <= now
                ? title + " · " + time + " · reminder due soon"
                : title + " · " + time + " · remind at "
                    + new SimpleDateFormat("h:mm a", Locale.getDefault()).format(new Date(session.startsAt - 10 * 60_000L));
    }

    private static Size size(android.os.Bundle options) {
        if (options == null) return Size.COMPACT;
        int width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0));
        int height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0));
        if (width >= 240 && height >= 120) return Size.WIDE;
        if (width >= 170 || height >= 110) return Size.MEDIUM;
        return Size.COMPACT;
    }

    private static List<LiveSnapshotStore.ClassSession> futureClasses(List<LiveSnapshotStore.ClassSession> sessions, long now, int limit) {
        List<LiveSnapshotStore.ClassSession> result = new ArrayList<>();
        for (LiveSnapshotStore.ClassSession session : sessions) {
            if (session.allDay || session.cancelled || session.startsAt <= now) continue;
            result.add(session);
            if (result.size() == limit) break;
        }
        return result;
    }

    private static LiveSnapshotStore.ClassSession nextClass(List<LiveSnapshotStore.ClassSession> sessions, long now) {
        List<LiveSnapshotStore.ClassSession> future = futureClasses(sessions, now, 1);
        return future.isEmpty() ? null : future.get(0);
    }

    private static Attendance matchingCourse(List<Attendance> rows, String code) {
        String target = normalize(code);
        for (Attendance row : rows) if (!target.isEmpty() && target.equals(normalize(row.code))) return row;
        return null;
    }

    private static String targetStatus(Context context, double percentage, int target) {
        return context.getString(percentage >= target ? R.string.widget_meets_target : R.string.widget_below_target, target);
    }

    private static String overallAttendance(Context context, List<Attendance> rows, int target) {
        RollcallWidgetMetrics.AttendanceSummary summary = RollcallWidgetMetrics.attendance(rows, target);
        return summary.percentage < 0 ? context.getString(R.string.widget_attendance_after_sync)
                : context.getString(R.string.widget_overall, summary.percentage,
                        context.getString(summary.percentage >= target ? R.string.widget_meets_target : R.string.widget_below_target, target));
    }

    private static String classTime(long startsAt, long now) {
        Calendar start = Calendar.getInstance(); start.setTimeInMillis(startsAt);
        Calendar today = Calendar.getInstance(); today.setTimeInMillis(now);
        Calendar tomorrow = (Calendar) today.clone(); tomorrow.add(Calendar.DAY_OF_YEAR, 1);
        String time = new SimpleDateFormat("h:mm a", Locale.getDefault()).format(new Date(startsAt));
        if (sameDay(start, today)) return "Today · " + time;
        if (sameDay(start, tomorrow)) return "Tomorrow · " + time;
        return new SimpleDateFormat("EEE, d MMM · h:mm a", Locale.getDefault()).format(new Date(startsAt));
    }

    private static boolean sameDay(Calendar first, Calendar second) {
        return first.get(Calendar.YEAR) == second.get(Calendar.YEAR)
                && first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR);
    }

    private static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + .5f);
    }

    private static int weekLabelId(int day) {
        int[] ids = {R.id.week_day_0_label, R.id.week_day_1_label, R.id.week_day_2_label,
                R.id.week_day_3_label, R.id.week_day_4_label, R.id.week_day_5_label, R.id.week_day_6_label};
        return ids[day];
    }

    private static int weekCountId(int day) {
        int[] ids = {R.id.week_day_0_count, R.id.week_day_1_count, R.id.week_day_2_count,
                R.id.week_day_3_count, R.id.week_day_4_count, R.id.week_day_5_count, R.id.week_day_6_count};
        return ids[day];
    }

    private static int streakDotId(int day) {
        int[] ids = {R.id.streak_dot_0, R.id.streak_dot_1, R.id.streak_dot_2,
                R.id.streak_dot_3, R.id.streak_dot_4, R.id.streak_dot_5, R.id.streak_dot_6};
        return ids[day];
    }

    private static long oldestAvailable(long first, long second) {
        if (first <= 0) return second;
        if (second <= 0) return first;
        return Math.min(first, second);
    }

    private static String relativeAge(long timestamp) {
        long minutes = Math.max(0, (System.currentTimeMillis() - timestamp) / 60_000L);
        if (minutes == 0) return "just now";
        if (minutes < 60) return minutes + "m ago";
        long hours = minutes / 60;
        if (hours < 24) return hours + "h ago";
        return (hours / 24) + "d ago";
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
