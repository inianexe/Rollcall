package com.iniexe.rollcall;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

final class RollcallWidgetMetrics {
    static final class AttendanceSummary {
        final int percentage, onTrack, belowTarget;
        final List<Attendance> attentionCourses;

        AttendanceSummary(int percentage, int onTrack, List<Attendance> attentionCourses) {
            this.percentage = percentage;
            this.onTrack = onTrack;
            this.belowTarget = attentionCourses.size();
            this.attentionCourses = Collections.unmodifiableList(attentionCourses);
        }
    }

    private RollcallWidgetMetrics() { }

    static AttendanceSummary attendance(List<Attendance> rows, int target) {
        long attended = 0, total = 0;
        int onTrack = 0;
        List<Attendance> attention = new ArrayList<>();
        for (Attendance row : rows) {
            if (row.total > 0 && row.countsKnown) {
                attended += row.attended;
                total += row.total;
            }
            if (row.countsKnown && row.total == 0) continue;
            if (row.percentage() >= target) onTrack++;
            else attention.add(row);
        }
        Collections.sort(attention, Comparator.comparingDouble(Attendance::percentage));
        return new AttendanceSummary(total == 0 ? -1 : (int) Math.round(100d * attended / total), onTrack, attention);
    }

    static int currentStreak(List<LiveSnapshotStore.ClassMark> marks) {
        boolean foundConfirmed = false;
        int streak = 0;
        for (LiveSnapshotStore.ClassMark mark : newestFirst(marks)) {
            if ("present".equalsIgnoreCase(mark.status)) {
                foundConfirmed = true;
                streak++;
            } else if ("absent".equalsIgnoreCase(mark.status)) {
                foundConfirmed = true;
                break;
            }
        }
        return foundConfirmed ? streak : -1;
    }

    static List<String> recentConfirmedStatuses(List<LiveSnapshotStore.ClassMark> marks, int limit) {
        List<String> statuses = new ArrayList<>();
        if (limit <= 0) return statuses;
        for (LiveSnapshotStore.ClassMark mark : newestFirst(marks)) {
            if ("present".equalsIgnoreCase(mark.status) || "absent".equalsIgnoreCase(mark.status)) {
                statuses.add(mark.status);
                if (statuses.size() == limit) break;
            }
        }
        Collections.reverse(statuses);
        return statuses;
    }

    private static List<LiveSnapshotStore.ClassMark> newestFirst(List<LiveSnapshotStore.ClassMark> marks) {
        List<LiveSnapshotStore.ClassMark> sorted = new ArrayList<>(marks);
        Collections.sort(sorted, (a, b) -> Long.compare(b.startsAt, a.startsAt));
        return sorted;
    }
}
