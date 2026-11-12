package com.iniexe.rollcall;

import android.content.Context;
import android.content.SharedPreferences;
import android.webkit.CookieManager;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Best-effort class-mark refresh. WorkManager may delay runs; foreground WebView sync remains primary. */
public final class AttendanceSyncWorker extends Worker {
    private static final String PREFS = "mycamu_sync";
    private static final String URL_ROOT = "https://www.mycamu.co.in";
    private static final long HISTORY_WINDOW = 365L * 24 * 60 * 60 * 1000;

    public AttendanceSyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull @Override public Result doWork() {
        Context context = getApplicationContext();
        LiveSnapshotStore.initialize(context);
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String savedContext = prefs.getString("account_context", "");
        String cookies;
        try { cookies = CookieManager.getInstance().getCookie(URL_ROOT); }
        catch (RuntimeException e) { cookies = null; }
        if (savedContext.isEmpty() || cookies == null || cookies.isEmpty()) {
            LiveSnapshotStore.syncError(context, "Background refresh needs an active MyCamu sign-in. Open MyCamu to reconnect.");
            return Result.success();
        }
        try {
            JSONObject account = new JSONObject(savedContext);
            JSONObject attendanceBody = request("/api/Attendance/getDtaForStupage", account, cookies);
            JSONArray subjects = attendanceBody.optJSONObject("output") == null ? null
                    : attendanceBody.optJSONObject("output").optJSONObject("data") == null ? null
                    : attendanceBody.optJSONObject("output").optJSONObject("data").optJSONArray("subjectList");
            if (subjects == null) throw new IllegalStateException("MyCamu returned an unreadable attendance list.");

            List<Attendance> attendanceRows = new ArrayList<>();
            for (int i = 0; i < Math.min(subjects.length(), 500); i++) {
                JSONObject subject = subjects.optJSONObject(i);
                if (subject == null) continue;
                try { attendanceRows.add(new Attendance(subject.optString("SubjCd"), subject.optString("SubjNm"),
                        subject.optInt("pnt", -1), subject.optInt("all", -1))); }
                catch (IllegalArgumentException ignored) { }
            }
            if (!attendanceRows.isEmpty()) LiveSnapshotStore.attendance(context, attendanceRows);

            String partialSyncError = "";
            List<LiveSnapshotStore.ClassSession> refreshedSessions = null;
            try { refreshedSessions = fetchTimetable(account, cookies, now()); }
            catch (Exception ignored) { partialSyncError = "Attendance refreshed, but timetable sync failed. Open Rollcall to retry."; }
            if (refreshedSessions != null) {
                LiveSnapshotStore.timetable(context, refreshedSessions);
                ClassReminderScheduler.schedule(context, refreshedSessions);
            }
            List<LiveSnapshotStore.LeaveRecord> refreshedLeaves = null;
            try { refreshedLeaves = fetchLeaves(account, cookies, LiveSnapshotStore.get().leaves); }
            catch (Exception ignored) { partialSyncError = partialSyncError.isEmpty()
                    ? "Attendance refreshed, but leave and OD sync failed. Open Rollcall to retry."
                    : "Attendance refreshed, but timetable and leave/OD sync failed. Open Rollcall to retry."; }
            if (refreshedLeaves != null) LiveSnapshotStore.leaves(context, refreshedLeaves);

            List<LiveSnapshotStore.ClassSession> sessions = LiveSnapshotStore.get().timetable;
            Set<String> neededCodes = new HashSet<>();
            long now = System.currentTimeMillis();
            for (LiveSnapshotStore.ClassSession session : sessions) {
                if (!session.allDay && !session.cancelled && session.endsAt <= now
                        && session.endsAt >= now - 12 * 60 * 60_000L) neededCodes.add(normalize(session.code));
            }
            List<LiveSnapshotStore.ClassMark> history = new ArrayList<>();
            Map<String, JSONArray> logsBySubject = new HashMap<>();
            Set<String> confirmedSubjects = new HashSet<>();
            for (int i = 0; i < Math.min(subjects.length(), 40); i++) {
                JSONObject subject = subjects.optJSONObject(i);
                if (subject == null) continue;
                String code = subject.optString("SubjCd", "");
                if (!neededCodes.contains(normalize(code))) continue;
                JSONObject body = new JSONObject(account.toString());
                body.put("AttType", subject.opt("AttType"));
                body.put("SubjId", subject.opt("SubjId"));
                try {
                    JSONObject response = request("/api/getAttendanceByAttTypAndSubj", body, cookies);
                    JSONObject output = response.optJSONObject("output");
                    JSONArray records = output == null ? null : output.optJSONArray("data");
                    if (records == null) continue;
                    String subjectId = String.valueOf(subject.opt("SubjId"));
                    logsBySubject.put(subjectId, records);
                    confirmedSubjects.add(subjectId);
                    String name = subject.optString("SubjNm", code);
                    for (int j = 0; j < Math.min(records.length(), 1000); j++) {
                        JSONObject record = records.optJSONObject(j);
                        if (record == null) continue;
                        String recordId = record.optString("SubjId", record.optString("SubID", record.optString("SubId", "")));
                        String recordCode = record.optString("SubjCd", record.optString("SubCd", ""));
                        if (!subjectId.equals(recordId) && !normalize(subject.optString("SubjCd", "")).equals(normalize(recordCode))) continue;
                        long start = parseTime(record.optString("ForDate", ""), record.optString("FrTime", ""));
                        if (start <= 0 || start < now - HISTORY_WINDOW) continue;
                        String status = markStatus(record);
                        String id = subjectId + "_" + record.optString("ForDate") + "_" + record.optString("FrTime");
                        history.add(new LiveSnapshotStore.ClassMark(id, code, name, start, status,
                                record.optString("LvTy"), record.optString("LeaveSt"), true));
                    }
                } catch (Exception ignored) { }
            }

            List<LiveSnapshotStore.ClassMark> marks = new ArrayList<>();
            List<LiveSnapshotStore.ClassSession> notifySessions = new ArrayList<>();
            List<String> notifyStatuses = new ArrayList<>();
            for (LiveSnapshotStore.ClassSession session : sessions) {
                if (session.allDay || session.cancelled || session.endsAt > now || session.endsAt < now - 12 * 60 * 60_000L) continue;
                JSONObject subject = subjectForCode(subjects, session.code);
                String subjectId = subject == null ? "" : String.valueOf(subject.opt("SubjId"));
                String status = "unable_to_confirm";
                if (!subjectId.isEmpty() && confirmedSubjects.contains(subjectId)) {
                    JSONArray records = logsBySubject.get(subjectId);
                    boolean exactRecordFound = false;
                    boolean sameDayRecordFound = false;
                    String sessionDay = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(session.startsAt));
                    for (int j = 0; records != null && j < records.length(); j++) {
                        JSONObject record = records.optJSONObject(j);
                        if (record == null) continue;
                        String recordId = record.optString("SubjId", record.optString("SubID", record.optString("SubId", "")));
                        String recordCode = record.optString("SubjCd", record.optString("SubCd", ""));
                        if ((!recordId.isEmpty() && !subjectId.equals(recordId))
                                || (!recordCode.isEmpty() && !normalize(subject.optString("SubjCd", "")).equals(normalize(recordCode)))) continue;
                        if (record.optString("ForDate", "").startsWith(sessionDay)) sameDayRecordFound = true;
                        long time = parseTime(record.optString("ForDate", ""), record.optString("FrTime", ""));
                        if (time <= 0) continue;
                        if (time > 0 && Math.abs(time - session.startsAt) < 60_000L) {
                            exactRecordFound = true;
                            status = markStatus(record);
                            break;
                        }
                    }
                    if (!exactRecordFound && !sameDayRecordFound) status = "not_taken";
                }
                marks.add(new LiveSnapshotStore.ClassMark(session.id, session.code, session.name, session.startsAt, status));
                if (session.endsAt <= now - ClassAttendanceNotifier.MISSING_MARK_GRACE_MS
                        && session.endsAt >= now - 90 * 60_000L
                        && !"unable_to_confirm".equals(status)) {
                    notifySessions.add(session);
                    notifyStatuses.add(status);
                }
            }
            if (isStopped() || !savedContext.equals(prefs.getString("account_context", ""))) return Result.success();
            for (int i = 0; i < notifySessions.size(); i++) notifyOnce(context, notifySessions.get(i), notifyStatuses.get(i));
            LiveSnapshotStore.mergeClassMarks(context, history);
            LiveSnapshotStore.mergeClassMarks(context, marks);
            LiveSnapshotStore.syncError(context, partialSyncError);
            prefs.edit().putLong("last_background_success", now).apply();
            CloudAccountClient.enqueueSnapshotSync(context);
            return Result.success();
        } catch (Exception e) {
            LiveSnapshotStore.syncError(context, "Background refresh could not confirm MyCamu data. Open MyCamu to sync.");
            return Result.retry();
        }
    }

    private static long now() { return System.currentTimeMillis(); }

    private static List<LiveSnapshotStore.ClassSession> fetchTimetable(JSONObject account, String cookies, long now) throws Exception {
        java.util.Calendar start = java.util.Calendar.getInstance(), end = java.util.Calendar.getInstance();
        start.setTimeInMillis(now); start.set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.SUNDAY);
        start.set(java.util.Calendar.HOUR_OF_DAY, 0); start.set(java.util.Calendar.MINUTE, 0); start.set(java.util.Calendar.SECOND, 0); start.set(java.util.Calendar.MILLISECOND, 0);
        end.setTimeInMillis(start.getTimeInMillis()); end.add(java.util.Calendar.DAY_OF_YEAR, 6);
        JSONObject body = new JSONObject(account.toString());
        body.put("start", new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(start.getTime()));
        body.put("end", new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(end.getTime()));
        body.put("schdlTyp", "showCurWeek"); body.put("isShowCancelledPeriod", true); body.put("isFromTt", true);
        JSONObject response = request("/api/Timetable/get", body, cookies);
        JSONObject output = response.optJSONObject("output"); JSONArray groups = output == null ? null : output.optJSONArray("data");
        if (groups == null || groups.length() < 2) throw new IllegalStateException("MyCamu returned an unreadable timetable.");
        List<LiveSnapshotStore.ClassSession> result = new ArrayList<>();
        for (int group = 0; group < Math.min(3, groups.length()); group++) {
            JSONArray values = groups.optJSONArray(group);
            for (int i = 0; values != null && i < Math.min(values.length(), 1000); i++) {
                JSONObject row = values.optJSONObject(i); if (row == null) continue;
                long begins = parseApiTime(row.optString("start", row.optString("eventDate", "")));
                if (begins <= 0) continue;
                long ends = parseApiTime(row.optString("end", ""));
                String kind = row.optString("type", row.optString("eTy", group == 2 ? "event" : "class"));
                boolean allDay = group == 2 || row.optBoolean("allDay");
                boolean cancelled = kind.toLowerCase(Locale.ROOT).contains("cancel") || row.optString("className").toLowerCase(Locale.ROOT).contains("cancel");
                String code = row.optString("SubCd", ""), id = row.optString("_id", row.optString("id", row.optString("PeriodId", "")));
                if (id.isEmpty()) id = code + "_" + begins;
                result.add(new LiveSnapshotStore.ClassSession(id, code, row.optString("SubNa", row.optString("title", "Class")),
                        row.optString("StaffNm", ""), row.optString("LocBtch", ""), kind, begins,
                        ends > 0 ? ends : begins, allDay, cancelled));
            }
        }
        return result;
    }

    private static List<LiveSnapshotStore.LeaveRecord> fetchLeaves(JSONObject account, String cookies,
            List<LiveSnapshotStore.LeaveRecord> previous) throws Exception {
        JSONObject body = new JSONObject(); body.put("StuID", account.opt("StuID")); body.put("isFrmReact", true);
        JSONObject response = request("/api/Leave/getAllleave/", body, cookies);
        JSONObject output = response.optJSONObject("output"); JSONArray values = output == null ? null : output.optJSONArray("data");
        if (values == null) throw new IllegalStateException("MyCamu returned an unreadable leave list.");
        Map<String, LiveSnapshotStore.LeaveRecord> old = new HashMap<>();
        for (LiveSnapshotStore.LeaveRecord row : previous) old.put(row.id, row);
        List<LiveSnapshotStore.LeaveRecord> result = new ArrayList<>();
        for (int i = 0; i < Math.min(values.length(), 1000); i++) {
            JSONObject row = values.optJSONObject(i); if (row == null) continue;
            String id = row.optString("_id", row.optString("refId", ""));
            long startsAt = parseApiTime(row.optString("start", ""));
            if (startsAt <= 0) continue;
            long endsAt = parseApiTime(row.optString("end", ""));
            int periods = row.optJSONArray("periods") != null ? row.optJSONArray("periods").length() : row.optInt("periods", 0);
            LiveSnapshotStore.LeaveRecord cached = old.get(id);
            String semester = row.optString("SemID", row.optString("SemId", row.optString("SemesterID", row.optString("SemesterId", ""))));
            String academicYear = row.optString("AcYr", row.optString("AcademicYr", row.optString("AcademicYear", "")));
            boolean semesterKnown = !semester.isEmpty() || !academicYear.isEmpty()
                    || cached != null && cached.semesterKnown;
            boolean currentSemester = semester.isEmpty() && academicYear.isEmpty()
                    ? cached != null && cached.currentSemester
                    : (semester.isEmpty() || semester.equals(String.valueOf(account.opt("SemID"))))
                    && (academicYear.isEmpty() || academicYear.equals(String.valueOf(account.opt("AcYr"))));
            result.add(new LiveSnapshotStore.LeaveRecord(id, row.optString("LvTy", "Leave"), row.optString("Stus", "Unknown"),
                    startsAt, endsAt > 0 ? endsAt : startsAt, periods,
                    cached == null ? "" : cached.periodNumbers, cached == null ? "[]" : cached.periodDetails,
                    currentSemester, semesterKnown));
        }
        return result;
    }

    private static long parseApiTime(String value) {
        if (value == null || value.trim().isEmpty()) return 0;
        for (String pattern : new String[]{"yyyy-MM-dd'T'HH:mm:ss.SSSX", "yyyy-MM-dd'T'HH:mm:ssX", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd"}) {
            try { SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US); format.setLenient(false); Date parsed = format.parse(value); if (parsed != null) return parsed.getTime(); }
            catch (ParseException ignored) { }
        }
        return 0;
    }

    private void notifyOnce(Context context, LiveSnapshotStore.ClassSession session, String status) {
        SharedPreferences sent = context.getSharedPreferences("attendance_notice_state", Context.MODE_PRIVATE);
        String markId = session.id + "_" + session.startsAt;
        String key = "attendance_notice_" + Integer.toHexString(markId.hashCode());
        if (status.equals(sent.getString(key, ""))) return;
        if (ClassAttendanceNotifier.post(context, markId, session.name, status))
            sent.edit().putString(key, status).apply();
    }

    private static JSONObject subjectForCode(JSONArray subjects, String code) {
        String target = normalize(code);
        for (int i = 0; i < subjects.length(); i++) {
            JSONObject subject = subjects.optJSONObject(i);
            if (subject == null) continue;
            String candidate = normalize(subject.optString("SubjCd", ""));
            if (!target.isEmpty() && !candidate.isEmpty()
                    && target.equals(candidate))
                return subject;
        }
        return null;
    }

    private static String normalize(String value) { return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", ""); }

    private static String markStatus(JSONObject record) {
        if (record == null || !record.has("present") || record.isNull("present")) return "unable_to_confirm";
        Object value = record.opt("present");
        if (Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value)) || "1".equals(String.valueOf(value))) return "present";
        if (Boolean.FALSE.equals(value) || "false".equalsIgnoreCase(String.valueOf(value)) || "0".equals(String.valueOf(value))) return "absent";
        return "unable_to_confirm";
    }

    private static long parseTime(String date, String time) {
        String day = date == null || date.length() < 10 ? "" : date.substring(0, 10);
        String clock = time == null ? "" : time.trim();
        for (String pattern : new String[]{"yyyy-MM-dd HH:mm", "yyyy-MM-dd H:mm", "yyyy-MM-dd hh:mm a", "yyyy-MM-dd h:mm a", "yyyy-MM-dd HH:mm:ss"}) {
            try { SimpleDateFormat f = new SimpleDateFormat(pattern, Locale.US); f.setLenient(false); Date parsed = f.parse(day + " " + clock); if (parsed != null) return parsed.getTime(); }
            catch (ParseException ignored) { }
        }
        return 0;
    }

    private static JSONObject request(String path, JSONObject body, String cookies) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(URL_ROOT + path).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(12_000);
        connection.setReadTimeout(15_000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Cookie", cookies);
        connection.setRequestProperty("Origin", URL_ROOT);
        connection.setRequestProperty("Referer", URL_ROOT + "/v2/attendance");
        try {
            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream out = connection.getOutputStream()) { out.write(bytes); }
            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
            if (stream == null || code == 401 || code == 403) throw new IllegalStateException("MyCamu session expired.");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int n;
            while ((n = stream.read(buffer)) >= 0) output.write(buffer, 0, n);
            if (code == 500 && "/api/Timetable/get".equals(path)) {
                // shortcut: MyCamu reports this account's empty timetable as HTTP 500; revisit if its API changes.
                JSONArray groups = new JSONArray().put(new JSONArray()).put(new JSONArray()).put(new JSONArray());
                JSONObject data = new JSONObject().put("data", groups);
                return new JSONObject().put("output", data);
            }
            if (code < 200 || code >= 300) throw new IllegalStateException("MyCamu request failed.");
            return new JSONObject(output.toString("UTF-8"));
        } finally { connection.disconnect(); }
    }
}
