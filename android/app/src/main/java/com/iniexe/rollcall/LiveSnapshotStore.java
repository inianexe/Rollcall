package com.iniexe.rollcall;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class LiveSnapshotStore {
    interface Listener { void onSnapshot(Snapshot snapshot); }

    static final class ClassSession {
        final String id, code, name, staff, location, kind;
        final long startsAt, endsAt;
        final boolean allDay, cancelled;

        ClassSession(String id, String code, String name, String staff, String location, String kind,
                     long startsAt, long endsAt, boolean allDay, boolean cancelled) {
            this.id = id; this.code = code; this.name = name; this.staff = staff; this.location = location;
            this.kind = kind; this.startsAt = startsAt; this.endsAt = endsAt;
            this.allDay = allDay; this.cancelled = cancelled;
        }
    }

    static final class LeaveRecord {
        final String id, type, status, periodNumbers, periodDetails;
        final long startsAt, endsAt;
        final int periods;
        final boolean currentSemester, semesterKnown;

        LeaveRecord(String id, String type, String status, long startsAt, long endsAt, int periods,
                    String periodNumbers, String periodDetails) {
            this(id, type, status, startsAt, endsAt, periods, periodNumbers, periodDetails, false, false);
        }

        LeaveRecord(String id, String type, String status, long startsAt, long endsAt, int periods,
                    String periodNumbers, String periodDetails, boolean currentSemester) {
            this(id, type, status, startsAt, endsAt, periods, periodNumbers, periodDetails, currentSemester, false);
        }

        LeaveRecord(String id, String type, String status, long startsAt, long endsAt, int periods,
                    String periodNumbers, String periodDetails, boolean currentSemester, boolean semesterKnown) {
            this.id = id; this.type = type; this.status = status;
            this.startsAt = startsAt; this.endsAt = endsAt; this.periods = periods;
            this.periodNumbers = periodNumbers == null ? "" : periodNumbers;
            this.periodDetails = periodDetails == null ? "[]" : periodDetails;
            this.currentSemester = currentSemester;
            this.semesterKnown = semesterKnown;
        }
    }

    static final class ClassMark {
        final String id, code, name, status, leaveType, leaveStatus;
        final boolean leaveDetailsKnown;
        final long startsAt;
        ClassMark(String id, String code, String name, long startsAt, String status) {
            this(id, code, name, startsAt, status, "", "", false);
        }
        ClassMark(String id, String code, String name, long startsAt, String status,
                  String leaveType, String leaveStatus, boolean leaveDetailsKnown) {
            this.id = id; this.code = code; this.name = name; this.startsAt = startsAt; this.status = status;
            this.leaveType = leaveType; this.leaveStatus = leaveStatus; this.leaveDetailsKnown = leaveDetailsKnown;
        }
    }

    static final class Snapshot {
        final List<Attendance> attendance;
        final long attendanceAt;
        final List<ClassSession> timetable;
        final long timetableAt;
        final List<LeaveRecord> leaves;
        final long leavesAt;
        final List<ClassMark> classMarks;
        final long classMarksAt;
        final String syncError;
        final String status;

        Snapshot(List<Attendance> attendance, long attendanceAt, List<ClassSession> timetable, long timetableAt,
                 List<LeaveRecord> leaves, long leavesAt, List<ClassMark> classMarks, long classMarksAt,
                 String syncError, String status) {
            this.attendance = Collections.unmodifiableList(new ArrayList<>(attendance));
            this.attendanceAt = attendanceAt;
            this.timetable = Collections.unmodifiableList(new ArrayList<>(timetable));
            this.timetableAt = timetableAt;
            this.leaves = Collections.unmodifiableList(new ArrayList<>(leaves));
            this.leavesAt = leavesAt;
            this.classMarks = Collections.unmodifiableList(uniqueClassMarks(classMarks));
            this.classMarksAt = classMarksAt;
            this.syncError = syncError;
            this.status = status;
        }
    }

    private static final String PREFS = "attendance_snapshot";
    private static final String ROWS = "rows";
    private static final String UPDATED_AT = "updated_at";
    private static final String TIMETABLE = "timetable";
    private static final String TIMETABLE_AT = "timetable_updated_at";
    private static final String LEAVES = "leaves";
    private static final String LEAVES_AT = "leaves_updated_at";
    private static final String CLASS_MARKS = "class_marks";
    private static final String CLASS_MARKS_AT = "class_marks_updated_at";
    private static final String SYNC_ERROR = "sync_error";
    private static Snapshot current = new Snapshot(Collections.emptyList(), 0, Collections.emptyList(), 0,
            Collections.emptyList(), 0, Collections.emptyList(), 0, "", "Open MyCamu to load attendance.");
    private static Listener listener;
    private static boolean initialized;

    private LiveSnapshotStore() { }

    static synchronized void initialize(Context context) {
        if (initialized) return;
        initialized = true;
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long updatedAt = prefs.getLong(UPDATED_AT, 0);
        List<Attendance> rows = new ArrayList<>();
        List<ClassSession> sessions = new ArrayList<>();
        List<LeaveRecord> leaveRows = new ArrayList<>();
        List<ClassMark> marks = new ArrayList<>();
        try {
            JSONArray saved = new JSONArray(prefs.getString(ROWS, "[]"));
            for (int i = 0; i < saved.length(); i++) {
                JSONObject row = saved.optJSONObject(i);
                if (row == null) continue;
                try {
                    rows.add(new Attendance(row.optString("code"), row.optString("name"),
                            row.optInt("attended", -1), row.optInt("total", -1)));
                } catch (IllegalArgumentException ignored) { }
            }
        } catch (JSONException ignored) { }
        try {
            JSONArray saved = new JSONArray(prefs.getString(TIMETABLE, "[]"));
            for (int i = 0; i < saved.length(); i++) {
                JSONObject row = saved.optJSONObject(i);
                if (row == null) continue;
                sessions.add(new ClassSession(row.optString("id"), row.optString("code"), row.optString("name"),
                        row.optString("staff"), row.optString("location"), row.optString("kind"),
                        row.optLong("starts_at"), row.optLong("ends_at"), row.optBoolean("all_day"),
                        row.optBoolean("cancelled")));
            }
        } catch (JSONException ignored) { }
        try {
            JSONArray saved = new JSONArray(prefs.getString(LEAVES, "[]"));
            for (int i = 0; i < saved.length(); i++) {
                JSONObject row = saved.optJSONObject(i);
                if (row == null) continue;
                leaveRows.add(new LeaveRecord(row.optString("id"), row.optString("type"), row.optString("status"),
                        row.optLong("starts_at"), row.optLong("ends_at"), row.optInt("periods"),
                        row.optString("period_numbers"), row.optString("period_details", "[]"), row.optBoolean("current_semester"), row.optBoolean("semester_known")));
            }
        } catch (JSONException ignored) { }
        try {
            JSONArray saved = new JSONArray(prefs.getString(CLASS_MARKS, "[]"));
            for (int i = 0; i < saved.length(); i++) {
                JSONObject row = saved.optJSONObject(i);
                if (row != null) marks.add(new ClassMark(row.optString("id"), row.optString("code"),
                        row.optString("name"), row.optLong("starts_at"), row.optString("status"),
                        row.optString("leave_type"), row.optString("leave_status"), row.optBoolean("leave_details_known")));
            }
        } catch (JSONException ignored) { }
        current = new Snapshot(rows, updatedAt, sessions, prefs.getLong(TIMETABLE_AT, 0), leaveRows,
                prefs.getLong(LEAVES_AT, 0), marks, prefs.getLong(CLASS_MARKS_AT, 0), prefs.getString(SYNC_ERROR, ""),
                rows.isEmpty() ? "Open MyCamu to load attendance."
                : "Showing the last saved MyCamu update.");
    }

    static synchronized Snapshot get() { return current; }

    static synchronized void setListener(Listener value) {
        listener = value;
        if (listener != null) listener.onSnapshot(current);
    }

    static synchronized void attendance(Context context, List<Attendance> rows) {
        if (rows.isEmpty()) return;
        Snapshot previous = current;
        AttendanceEventNotifier.attendance(context, previous.attendance, previous.attendanceAt, rows,
                context.getSharedPreferences("MainActivity", Context.MODE_PRIVATE).getInt("attendance_target", 75));
        JSONArray saved = new JSONArray();
        for (Attendance row : rows) {
            JSONObject item = new JSONObject();
            try {
                item.put("code", row.code);
                item.put("name", row.name);
                item.put("attended", row.attended);
                item.put("total", row.total);
                saved.put(item);
            } catch (JSONException ignored) { }
        }
        long updatedAt = System.currentTimeMillis();
        context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(ROWS, saved.toString()).putLong(UPDATED_AT, updatedAt).apply();
        current = new Snapshot(rows, updatedAt, current.timetable, current.timetableAt,
                current.leaves, current.leavesAt, current.classMarks, current.classMarksAt, "",
                "Updated from MyCamu just now.");
        if (listener != null) listener.onSnapshot(current);
        RollcallTodayWidget.refresh(context);
    }

    static synchronized void timetable(Context context, List<ClassSession> rows) {
        Snapshot previous = current;
        AttendanceEventNotifier.timetable(context, previous.timetable, previous.timetableAt, rows);
        JSONArray saved = new JSONArray();
        for (ClassSession row : rows) {
            JSONObject item = new JSONObject();
            try {
                item.put("id", row.id); item.put("code", row.code); item.put("name", row.name);
                item.put("staff", row.staff); item.put("location", row.location); item.put("kind", row.kind);
                item.put("starts_at", row.startsAt); item.put("ends_at", row.endsAt);
                item.put("all_day", row.allDay); item.put("cancelled", row.cancelled); saved.put(item);
            } catch (JSONException ignored) { }
        }
        long updatedAt = System.currentTimeMillis();
        context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(TIMETABLE, saved.toString()).putLong(TIMETABLE_AT, updatedAt).apply();
        current = new Snapshot(current.attendance, current.attendanceAt, rows, updatedAt,
                current.leaves, current.leavesAt, current.classMarks, current.classMarksAt,
                current.syncError, current.status);
        if (listener != null) listener.onSnapshot(current);
        RollcallTodayWidget.refresh(context);
    }

    static synchronized void leaves(Context context, List<LeaveRecord> rows) {
        Snapshot previous = current;
        AttendanceEventNotifier.leaves(context, previous.leaves, previous.leavesAt, rows);
        JSONArray saved = new JSONArray();
        for (LeaveRecord row : rows) {
            JSONObject item = new JSONObject();
            try {
                item.put("id", row.id); item.put("type", row.type); item.put("status", row.status);
                item.put("starts_at", row.startsAt); item.put("ends_at", row.endsAt);
                item.put("periods", row.periods); item.put("period_numbers", row.periodNumbers);
                item.put("period_details", row.periodDetails); item.put("current_semester", row.currentSemester);
                item.put("semester_known", row.semesterKnown); saved.put(item);
            } catch (JSONException ignored) { }
        }
        long updatedAt = System.currentTimeMillis();
        context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(LEAVES, saved.toString()).putLong(LEAVES_AT, updatedAt).apply();
        current = new Snapshot(current.attendance, current.attendanceAt, current.timetable, current.timetableAt,
                rows, updatedAt, current.classMarks, current.classMarksAt, current.syncError, current.status);
        if (listener != null) listener.onSnapshot(current);
    }

    private static String classKey(ClassMark mark) {
        String code = mark.code == null ? "" : mark.code.trim().toUpperCase(java.util.Locale.ROOT);
        // Source IDs differ between timetable, foreground logs and background logs.
        // Keep theory/lab codes distinct and retain separate periods on the same day.
        return code.isEmpty() ? "id:" + mark.id : code + ":" + (mark.startsAt / 60_000L);
    }

    private static ClassMark retainLeaveDetails(ClassMark previous, ClassMark next) {
        if (previous != null && previous.leaveDetailsKnown && !next.leaveDetailsKnown
                && previous.status.equals(next.status)) {
            return new ClassMark(next.id, next.code, next.name, next.startsAt, next.status,
                    previous.leaveType, previous.leaveStatus, true);
        }
        return next;
    }

    private static boolean confirmed(ClassMark mark) {
        return "present".equals(mark.status) || "absent".equals(mark.status);
    }

    private static ArrayList<ClassMark> uniqueClassMarks(List<ClassMark> marks) {
        java.util.LinkedHashMap<String, ClassMark> rows = new java.util.LinkedHashMap<>();
        java.util.HashSet<String> conflicts = new java.util.HashSet<>();
        for (ClassMark mark : marks) {
            String key = classKey(mark);
            ClassMark previous = rows.get(key);
            if (previous != null && confirmed(previous) && confirmed(mark)
                    && !previous.status.equals(mark.status)) conflicts.add(key);
            if (conflicts.contains(key)) {
                rows.put(key, new ClassMark(mark.id, mark.code, mark.name,
                        mark.startsAt, "unable_to_confirm"));
            } else if (previous == null || confirmed(mark) || !confirmed(previous)) {
                rows.put(key, retainLeaveDetails(previous, mark));
            }
        }
        ArrayList<ClassMark> result = new ArrayList<>(rows.values());
        Collections.sort(result, (a, b) -> Long.compare(b.startsAt, a.startsAt));
        return result;
    }

    static synchronized void classMarks(Context context, List<ClassMark> marks) {
        marks = uniqueClassMarks(marks);
        JSONArray saved = new JSONArray();
        for (ClassMark mark : marks) {
            JSONObject item = new JSONObject();
            try {
                item.put("id", mark.id); item.put("code", mark.code); item.put("name", mark.name);
                item.put("starts_at", mark.startsAt); item.put("status", mark.status);
                item.put("leave_type", mark.leaveType); item.put("leave_status", mark.leaveStatus);
                item.put("leave_details_known", mark.leaveDetailsKnown); saved.put(item);
            } catch (JSONException ignored) { }
        }
        long updatedAt = System.currentTimeMillis();
        context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(CLASS_MARKS, saved.toString()).putLong(CLASS_MARKS_AT, updatedAt).apply();
        current = new Snapshot(current.attendance, current.attendanceAt, current.timetable, current.timetableAt,
                current.leaves, current.leavesAt, marks, updatedAt, current.syncError, current.status);
        if (listener != null) listener.onSnapshot(current);
        RollcallTodayWidget.refresh(context);
    }

    static synchronized void mergeClassMarks(Context context, List<ClassMark> marks) {
        java.util.LinkedHashMap<String, ClassMark> merged = new java.util.LinkedHashMap<>();
        for (ClassMark mark : current.classMarks) merged.put(classKey(mark), mark);
        for (ClassMark mark : uniqueClassMarks(marks)) {
            String key = classKey(mark);
            ClassMark previous = merged.get(key);
            if (previous == null || confirmed(mark) || !confirmed(previous)) merged.put(key, retainLeaveDetails(previous, mark));
        }
        ArrayList<ClassMark> rows = new ArrayList<>(merged.values());
        Collections.sort(rows, (a, b) -> Long.compare(b.startsAt, a.startsAt));
        if (rows.size() > 2000) rows = new ArrayList<>(rows.subList(0, 2000));
        classMarks(context, rows);
    }

    static synchronized void syncError(Context context, String error) {
        String value = error == null ? "" : error;
        AttendanceEventNotifier.syncError(context, current.syncError, value);
        context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(SYNC_ERROR, value).apply();
        current = new Snapshot(current.attendance, current.attendanceAt, current.timetable, current.timetableAt,
                current.leaves, current.leavesAt, current.classMarks, current.classMarksAt, value, current.status);
        if (listener != null) listener.onSnapshot(current);
    }

    static synchronized void clear(Context context) {
        context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
        context.getApplicationContext().getSharedPreferences("attendance_event_notices", Context.MODE_PRIVATE).edit().clear().apply();
        current = new Snapshot(Collections.emptyList(), 0, Collections.emptyList(), 0,
                Collections.emptyList(), 0, Collections.emptyList(), 0, "", "Open MyCamu to load attendance.");
        if (listener != null) listener.onSnapshot(current);
        RollcallTodayWidget.refresh(context);
    }

}
