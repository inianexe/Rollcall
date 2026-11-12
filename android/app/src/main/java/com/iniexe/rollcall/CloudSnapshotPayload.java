package com.iniexe.rollcall;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class CloudSnapshotPayload {
    private CloudSnapshotPayload() { }

    static JSONObject build(Context context) throws JSONException {
        LiveSnapshotStore.initialize(context);
        LiveSnapshotStore.Snapshot snapshot = LiveSnapshotStore.get();
        SharedPreferences prefs = context.getSharedPreferences("MainActivity_preferences", Context.MODE_PRIVATE);
        JSONObject result = new JSONObject();
        result.put("schemaVersion", 1);
        result.put("name", prefs.getString("student_name", ""));
        result.put("institution", prefs.getString("institute_name", ""));
        result.put("rollNumber", prefs.getString("roll_number", ""));
        result.put("rollNumberLabel", prefs.getString("roll_number_label", "Roll No."));
        result.put("target", prefs.getInt("attendance_target", 75));
        result.put("updatedAt", Math.max(Math.max(snapshot.attendanceAt, snapshot.timetableAt),
                Math.max(Math.max(snapshot.leavesAt, snapshot.classMarksAt), prefs.getLong("kct_academic_updated_at", 0))));

        JSONArray courses = new JSONArray();
        for (Attendance row : snapshot.attendance) {
            JSONObject item = new JSONObject();
            item.put("code", row.code); item.put("name", row.name);
            item.put("attended", row.attended); item.put("total", row.total);
            item.put("percentage", row.countsKnown ? row.percentage() : JSONObject.NULL);
            item.put("countsKnown", row.countsKnown); courses.put(item);
        }
        result.put("courses", courses);

        JSONArray timetable = new JSONArray();
        for (LiveSnapshotStore.ClassSession row : snapshot.timetable) {
            JSONObject item = new JSONObject();
            item.put("id", row.id); item.put("code", row.code); item.put("name", row.name);
            item.put("staff", row.staff); item.put("location", row.location); item.put("kind", row.kind);
            item.put("startsAt", row.startsAt); item.put("endsAt", row.endsAt);
            item.put("allDay", row.allDay); item.put("cancelled", row.cancelled); timetable.put(item);
        }
        result.put("timetable", timetable);

        JSONArray classMarks = new JSONArray();
        for (LiveSnapshotStore.ClassMark row : snapshot.classMarks) {
            JSONObject item = new JSONObject();
            item.put("id", row.id); item.put("code", row.code); item.put("name", row.name);
            item.put("startsAt", row.startsAt); item.put("status", row.status);
            item.put("leaveType", row.leaveType); item.put("leaveStatus", row.leaveStatus);
            item.put("leaveDetailsKnown", row.leaveDetailsKnown); classMarks.put(item);
        }
        result.put("classMarks", classMarks);

        JSONArray leaves = new JSONArray();
        for (LiveSnapshotStore.LeaveRecord row : snapshot.leaves) {
            JSONObject item = new JSONObject();
            item.put("id", row.id); item.put("type", row.type); item.put("status", row.status);
            item.put("startsAt", row.startsAt); item.put("endsAt", row.endsAt); item.put("periods", row.periods);
            item.put("periodNumbers", row.periodNumbers); item.put("periodDetails", new JSONArray(row.periodDetails));
            item.put("currentSemester", row.currentSemester); item.put("semesterKnown", row.semesterKnown);
            leaves.put(item);
        }
        result.put("leaves", leaves);

        String rawRecords = prefs.getString("kct_academic_records", "[]");
        JSONArray academicRecords = new JSONArray(rawRecords);
        result.put("academicRecords", academicRecords);
        result.put("academicUpdatedAt", prefs.getLong("kct_academic_updated_at", 0));
        String rawExam = prefs.getString("kct_upcoming_exam", "{}");
        JSONObject exam = new JSONObject(rawExam);
        JSONArray exams = new JSONArray();
        if (exam.length() > 0) exams.put(exam);
        result.put("exams", exams);
        return result;
    }
}
