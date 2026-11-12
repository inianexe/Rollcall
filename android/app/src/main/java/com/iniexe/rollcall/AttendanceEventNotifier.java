package com.iniexe.rollcall;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Deduplicates change alerts across foreground and WorkManager syncs. */
final class AttendanceEventNotifier {
    private static final String PREFS = "attendance_event_notices";
    private AttendanceEventNotifier() { }

    static synchronized void attendance(Context context, List<Attendance> before, long beforeAt,
                                        List<Attendance> after, int target) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (beforeAt <= 0 && !prefs.getBoolean("attendance_baseline", false)) {
            saveAttendance(prefs, after, target);
            return;
        }
        Map<String, Attendance> oldRows = index(before), newRows = index(after);
        Map<String, Boolean> oldStates = new HashMap<>(), newStates = new HashMap<>();
        oldRows.forEach((key, row) -> oldStates.put(key, row.percentage() >= target));
        newRows.forEach((key, row) -> newStates.put(key, row.percentage() >= target));
        String overall = "overall_" + target;
        Boolean oldOverall = prefs.contains(overall) ? prefs.getBoolean(overall, false) : side(oldRows, target);
        Boolean newOverall = side(newRows, target);
        if (oldOverall != null && newOverall != null && !oldOverall.equals(newOverall))
            post(context, "overall_" + target, newOverall ? "Overall attendance back on target" : "Overall attendance below target",
                    newOverall ? "Your weighted attendance is now at or above " + target + "%" : "Your weighted attendance is now below " + target + "%",
                    "home", "target:overall:" + target);
        for (Map.Entry<String, Boolean> entry : newStates.entrySet()) {
            String key = entry.getKey();
            Boolean previous = oldStates.get(key);
            if (previous == null) previous = prefs.contains("course_" + target + "_" + key)
                    ? prefs.getBoolean("course_" + target + "_" + key, false) : null;
            if (previous != null && !previous.equals(entry.getValue())) {
                Attendance row = newRows.get(key);
                boolean reached = entry.getValue();
                post(context, row.name + (reached ? " reached your target" : " fell below your target"),
                        row.name + " is now " + Math.round(row.percentage()) + "% against your " + target + "% target.",
                        "courses", "target:" + target + ":" + key + ":" + reached);
            }
        }
        saveAttendance(prefs, after, target);
    }

    static synchronized void leaves(Context context, List<LiveSnapshotStore.LeaveRecord> before, long beforeAt,
                                    List<LiveSnapshotStore.LeaveRecord> after) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (beforeAt <= 0 && !prefs.getBoolean("leaves_baseline", false)) {
            saveLeaves(prefs, after);
            return;
        }
        Map<String, String> old = leaveStates(before), current = leaveStates(after);
        for (Map.Entry<String, String> entry : current.entrySet()) {
            String previous = old.containsKey(entry.getKey()) ? old.get(entry.getKey())
                    : prefs.getString("leave_" + entry.getKey(), "");
            String status = entry.getValue();
            if (!previous.equals(status) && (status.contains("approv") || status.contains("reject"))) {
                String title = status.contains("approv") ? "OD request approved" : "OD request declined";
                post(context, "od_updates", title, "Your " + status + " request has been updated.", "leave_od", "leave:" + entry.getKey() + ":" + status);
            }
        }
        saveLeaves(prefs, after);
    }

    static synchronized void timetable(Context context, List<LiveSnapshotStore.ClassSession> before, long beforeAt,
                                       List<LiveSnapshotStore.ClassSession> after) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (beforeAt <= 0 && !prefs.getBoolean("timetable_baseline", false)) {
            saveTimetable(prefs, after);
            return;
        }
        Map<String, LiveSnapshotStore.ClassSession> old = sessionStates(before), current = sessionStates(after);
        for (Map.Entry<String, LiveSnapshotStore.ClassSession> entry : current.entrySet()) {
            LiveSnapshotStore.ClassSession previous = old.get(entry.getKey());
            LiveSnapshotStore.ClassSession session = entry.getValue();
            if (previous == null) {
                String oldSignature = prefs.getString("session_" + entry.getKey(), "");
                if (oldSignature.isEmpty()) {
                    if (beforeAt > 0 && session.startsAt > System.currentTimeMillis())
                        post(context, "schedule_updates", "Class added to your schedule", session.name + " is on your timetable.",
                                "week", "schedule:new:" + entry.getKey());
                    continue;
                }
                previous = parseSession(entry.getKey(), oldSignature);
            }
            if (previous == null) continue;
            String signature = signature(session);
            if (!signature.equals(signature(previous))) {
                post(context, "schedule_updates", session.cancelled ? "Class cancelled" : "Class schedule updated",
                        session.name + (session.cancelled ? " has been cancelled." : " has a schedule or location change."),
                        "week", "schedule:" + entry.getKey() + ":" + signature.hashCode());
            }
        }
        saveTimetable(prefs, after);
    }

    static synchronized void syncError(Context context, String before, String after) {
        if ((before == null ? "" : before).isEmpty() && after != null && !after.isEmpty())
            post(context, "sync_status", "Rollcall couldn't sync", "Open the app and check your MyCamu connection.", "home", "sync:error");
        else if (before != null && !before.isEmpty() && (after == null || after.isEmpty()))
            post(context, "sync_status", "Rollcall is up to date", "Your latest MyCamu information has synced.", "home", "sync:recovered");
    }

    static boolean targetSide(double percentage, int target) { return percentage >= target; }
    static synchronized void setTargetBaseline(Context context, List<Attendance> rows, int target) {
        saveAttendance(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE), rows, target);
    }
    static boolean isDecision(String status) {
        String value = status == null ? "" : status.toLowerCase(Locale.ROOT);
        return value.contains("approv") || value.contains("reject");
    }

    private static Map<String, Attendance> index(List<Attendance> rows) {
        Map<String, Attendance> result = new HashMap<>();
        for (Attendance row : rows) result.put(normalize(row.code.isEmpty() ? row.name : row.code), row);
        return result;
    }
    private static Boolean side(Map<String, Attendance> rows, int target) {
        int attended = 0, total = 0;
        for (Attendance row : rows.values()) if (row.countsKnown) { attended += row.attended; total += row.total; }
        return total == 0 ? null : (100d * attended / total) >= target;
    }
    private static void saveAttendance(SharedPreferences prefs, List<Attendance> rows, int target) {
        SharedPreferences.Editor edit = prefs.edit().putBoolean("attendance_baseline", true);
        Map<String, Attendance> indexed = index(rows);
        for (Map.Entry<String, Attendance> entry : indexed.entrySet())
            edit.putBoolean("course_" + target + "_" + entry.getKey(), entry.getValue().percentage() >= target);
        Boolean overall = side(indexed, target);
        if (overall != null) edit.putBoolean("overall_" + target, overall);
        edit.apply();
    }
    private static Map<String, String> leaveStates(List<LiveSnapshotStore.LeaveRecord> rows) {
        Map<String, String> result = new HashMap<>();
        for (LiveSnapshotStore.LeaveRecord row : rows) if (Attendance.isOdType(row.type))
            result.put(leaveKey(row), normalize(row.status));
        return result;
    }
    private static void saveLeaves(SharedPreferences prefs, List<LiveSnapshotStore.LeaveRecord> rows) {
        SharedPreferences.Editor edit = prefs.edit().putBoolean("leaves_baseline", true);
        leaveStates(rows).forEach((key, value) -> edit.putString("leave_" + key, value)); edit.apply();
    }
    private static Map<String, LiveSnapshotStore.ClassSession> sessionStates(List<LiveSnapshotStore.ClassSession> rows) {
        Map<String, LiveSnapshotStore.ClassSession> result = new HashMap<>();
        for (LiveSnapshotStore.ClassSession row : rows) result.put(sessionKey(row), row);
        return result;
    }
    private static void saveTimetable(SharedPreferences prefs, List<LiveSnapshotStore.ClassSession> rows) {
        SharedPreferences.Editor edit = prefs.edit().putBoolean("timetable_baseline", true);
        for (LiveSnapshotStore.ClassSession row : rows) edit.putString("session_" + sessionKey(row), signature(row));
        edit.apply();
    }
    private static void post(Context context, String title, String message, String destination, String key) {
        post(context, "attendance_target", title, message, destination, key);
    }
    private static void post(Context context, String channel, String title, String message, String destination, String key) {
        int id = key.hashCode() & 0x7fffffff;
        ClassAttendanceNotifier.postEvent(context, channel, title, message, destination, id);
    }
    private static String leaveKey(LiveSnapshotStore.LeaveRecord row) {
        return normalize(row.id.isEmpty() ? row.type + row.startsAt + row.periodNumbers : row.id);
    }
    private static String sessionKey(LiveSnapshotStore.ClassSession row) {
        return normalize(row.id.isEmpty() ? row.code + row.startsAt : row.id);
    }
    private static String signature(LiveSnapshotStore.ClassSession row) {
        return row.startsAt + "|" + row.endsAt + "|" + row.location + "|" + row.cancelled
                + "|" + row.code + "|" + row.name + "|" + row.staff;
    }
    private static LiveSnapshotStore.ClassSession parseSession(String key, String signature) {
        try {
            String[] p = signature.split("\\|", -1);
            return new LiveSnapshotStore.ClassSession(key, p[4], p[5], p[6], p[2], "", Long.parseLong(p[0]),
                    Long.parseLong(p[1]), false, Boolean.parseBoolean(p[3]));
        } catch (RuntimeException e) { return null; }
    }
    private static String normalize(String value) { return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", ""); }
}
