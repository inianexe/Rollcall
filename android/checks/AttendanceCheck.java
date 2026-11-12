package com.iniexe.rollcall;

public final class AttendanceCheck {
    public static void main(String[] args) {
        for (int total = 1; total <= 80; total++) for (int attended = 0; attended <= total; attended++) {
            Attendance a = new Attendance("CS", "Course", attended, total);
            for (int target : new int[]{50, 65, 75, 80, 90, 95}) {
                if (a.percentage() >= target) {
                    int n = a.safeAbsences(target);
                    assert 100.0 * attended / (total + n) >= target;
                    assert 100.0 * attended / (total + n + 1) < target;
                } else {
                    int n = a.periodsToRecover(target);
                    assert 100.0 * (attended + n) / (total + n) >= target;
                    assert n == 0 || 100.0 * (attended + n - 1) / (total + n - 1) < target;
                }
            }
        }
        Attendance course = new Attendance("CSL", "Lab", 7, 10);
        Attendance present = course.withApprovedOd(2, false);
        assert present.attended == 9 && present.total == 10;
        Attendance excluded = course.withApprovedOd(2, true);
        assert excluded.attended == 7 && excluded.total == 8;
        assert course.attended == 7 && course.total == 10;
        assert Attendance.isApprovedOd("On Duty", "Approved");
        assert Attendance.isApprovedOd("O.D.", "approved");
        assert !Attendance.isApprovedOd("OD", "Not Approved");
        assert !Attendance.isApprovedOd("OD", "Submitted");
        assert !Attendance.isApprovedOd("Medical", "Approved");
        assert !Attendance.isApprovedOd(null, "Approved");
        boolean rejected = false;
        try { course.withApprovedOd(4, false); } catch (IllegalArgumentException expected) { rejected = true; }
        assert rejected;
        assert new Attendance("CS", "Course", 5, 10).periodsToRecover(100) == Integer.MAX_VALUE;
        System.out.println("Attendance boundaries and OD credit checks passed");
    }
}
