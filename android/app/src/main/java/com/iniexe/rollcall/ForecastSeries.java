package com.iniexe.rollcall;

/** Presentation points only; Attendance remains the owner of recorded and OD counts. */
final class ForecastSeries {
    final double recorded;
    final double[] points;
    final int target, count;
    final boolean odPreview;

    ForecastSeries(Attendance recorded, Attendance baseline, boolean attend, int count, int target) {
        if (!recorded.countsKnown || !baseline.countsKnown || count < 0 || count > 20
                || target < 1 || target > 100) throw new IllegalArgumentException("Invalid forecast");
        this.recorded = recorded.total == 0 ? Double.NaN : recorded.percentage();
        this.target = target;
        this.count = count;
        odPreview = recorded.attended != baseline.attended || recorded.total != baseline.total;
        points = new double[count + 1];
        for (int n = 0; n <= count; n++) {
            int total = baseline.total + n;
            points[n] = total == 0 ? Double.NaN : 100.0 * (baseline.attended + (attend ? n : 0)) / total;
        }
    }

    int crossing() {
        for (int n = 1; n < points.length; n++) {
            if (!Double.isNaN(points[n - 1]) && (points[n - 1] >= target) != (points[n] >= target)) return n;
        }
        return -1;
    }
}
