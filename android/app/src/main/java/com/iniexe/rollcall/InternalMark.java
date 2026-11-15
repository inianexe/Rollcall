package com.iniexe.rollcall;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class InternalMark {
    final String id, semester, courseCode, course, assessment, status, date, publishedAt, source;
    final long lastSyncedAt;
    final double score, maximum;
    final boolean scoreAvailable;

    private InternalMark(String id, String semester, String courseCode, String course, String assessment,
                         String status, String date, String publishedAt, String source, long lastSyncedAt,
                         double score, double maximum, boolean scoreAvailable) {
        this.id = id; this.semester = semester; this.courseCode = courseCode; this.course = course;
        this.assessment = assessment; this.status = status; this.date = date; this.publishedAt = publishedAt;
        this.source = source; this.lastSyncedAt = lastSyncedAt; this.score = score;
        this.maximum = maximum; this.scoreAvailable = scoreAvailable;
    }

    static InternalMark fromJson(JSONObject row) {
        if (row == null) return null;
        Map<String, Object> values = new LinkedHashMap<>();
        JSONArray keys = row.names();
        if (keys != null) for (int i = 0; i < keys.length(); i++) {
            String key = keys.optString(i);
            values.put(key, row.opt(key));
        }
        return fromValues(values);
    }

    static List<InternalMark> fromJson(JSONArray rows) {
        ArrayList<Map<String, Object>> values = new ArrayList<>();
        if (rows != null) for (int i = 0; i < Math.min(rows.length(), 2000); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) continue;
            Map<String, Object> value = new LinkedHashMap<>();
            JSONArray keys = row.names();
            if (keys != null) for (int k = 0; k < keys.length(); k++) {
                String key = keys.optString(k);
                value.put(key, row.opt(key));
            }
            values.add(value);
        }
        return fromValues(values);
    }

    static InternalMark fromValues(Map<String, ?> row) {
        if (row == null) return null;
        String course = bounded(text(row, "course", text(row, "name", "")), 100);
        String assessment = bounded(text(row, "assessment", text(row, "exam", "")), 100);
        if (course.isEmpty() || assessment.isEmpty()) return null;
        double score = number(row, "score", number(row, "marks", Double.NaN));
        double maximum = number(row, "maximum", number(row, "maxMarks", Double.NaN));
        boolean available = finite(score) && finite(maximum) && maximum > 0 && maximum <= 10000 && score >= 0 && score <= maximum;
        String status = text(row, "status", available ? "published" : "unpublished").trim().toLowerCase(Locale.ROOT);
        if (!status.equals("published") && !status.equals("unpublished") && !status.equals("withheld") && !status.equals("missing"))
            status = "unpublished";
        if (status.equals("published") && !available) return null;
        if (!status.equals("published")) available = false;
        String code = bounded(text(row, "courseCode", text(row, "code", "")), 40);
        String semester = bounded(text(row, "semester", ""), 80);
        String id = bounded(text(row, "assessmentId", text(row, "id", "")), 120);
        String date = bounded(text(row, "date", ""), 40);
        if (id.isEmpty()) id = semester + "|" + code + "|" + course + "|" + assessment + "|" + date;
        return new InternalMark(id, semester, code, course, assessment, status, date,
                bounded(text(row, "publishedAt", ""), 40), bounded(text(row, "source", "KCT"), 80),
                Math.max(0, (long) number(row, "lastSyncedAt", 0)), available ? score : Double.NaN,
                available ? maximum : Double.NaN, available);
    }

    static List<InternalMark> fromValues(List<? extends Map<String, ?>> rows) {
        LinkedHashMap<String, InternalMark> marks = new LinkedHashMap<>();
        if (rows != null) for (int i = 0; i < Math.min(rows.size(), 2000); i++) {
            InternalMark mark = fromValues(rows.get(i));
            if (mark != null) marks.put(mark.id, mark);
        }
        return new ArrayList<>(marks.values());
    }

    private static String text(Map<String, ?> row, String key, String fallback) {
        Object value = row.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private static double number(Map<String, ?> row, String key, double fallback) {
        Object value = row.get(key);
        if (value instanceof Number) return ((Number) value).doubleValue();
        if (value == null) return fallback;
        try { return Double.parseDouble(String.valueOf(value)); }
        catch (NumberFormatException ignored) { return Double.NaN; }
    }

    private static String bounded(String value, int max) {
        String normalized = value == null ? "" : value.trim();
        return normalized.length() <= max ? normalized : normalized.substring(0, max);
    }

    private static boolean finite(double value) { return !Double.isNaN(value) && !Double.isInfinite(value); }
}
