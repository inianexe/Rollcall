package com.iniexe.rollcall;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class Attendance {
    private static final Pattern ROW = Pattern.compile("^([\\w.-]+)\\s*[-–—]\\s*(.+?)\\s*:\\s*(\\d+(?:\\.\\d+)?)%\\s*[-–—:]?\\s*\\(\\s*(\\d+)\\s*/\\s*(\\d+)\\s*\\)$");
    private static final Pattern TOOLTIP = Pattern.compile("^[\\s■□▪•✓✔]*([\\w.-]+)\\s*[-–—]\\s*(.+?)\\s*:\\s*(\\d+(?:\\.\\d+)?)%\\s*$");
    final String code, name;
    final int attended, total;
    final boolean countsKnown;
    private final double displayedPercentage;

    Attendance(String code, String name, int attended, int total) {
        if (code == null || code.trim().isEmpty() || name == null || name.trim().isEmpty() || attended < 0 || total < attended || total > 1_000_000)
            throw new IllegalArgumentException("Invalid course or attendance counts");
        this.code = code; this.name = name; this.attended = attended; this.total = total;
        this.countsKnown = true; this.displayedPercentage = total == 0 ? 0 : 100.0 * attended / total;
    }

    private Attendance(String code, String name, double percentage) {
        if (code == null || code.trim().isEmpty() || name == null || name.trim().isEmpty() || percentage < 0 || percentage > 100)
            throw new IllegalArgumentException("Invalid course percentage");
        this.code = code; this.name = name; this.attended = 0; this.total = 0;
        this.countsKnown = false; this.displayedPercentage = percentage;
    }

    static Attendance parse(String text) {
        Matcher m = ROW.matcher(text.trim());
        if (!m.matches()) {
            Matcher tooltip = TOOLTIP.matcher(text.trim());
            if (!tooltip.matches()) return null;
            try {
                double percentage = Double.parseDouble(tooltip.group(3));
                if (percentage < 0 || percentage > 100) return null;
                return new Attendance(tooltip.group(1), tooltip.group(2).trim(), percentage);
            } catch (NumberFormatException e) { return null; }
        }
        try {
            int attended = Integer.parseInt(m.group(4)), total = Integer.parseInt(m.group(5));
            if (attended > total || total > 1_000_000) return null;
            double displayed = Double.parseDouble(m.group(3));
            if (total > 0 && Math.abs(100.0 * attended / total - displayed) > 1.01) return null;
            return new Attendance(m.group(1), m.group(2).trim(), attended, total);
        } catch (NumberFormatException e) { return null; }
    }

    double percentage() { return displayedPercentage; }
    int safeAbsences(double target) {
        return Math.max(0, (int) Math.floor((100.0 * attended - target * total) / target));
    }

    int periodsToRecover(double target) {
        if (target >= 100) return Integer.MAX_VALUE;
        return Math.max(0, (int) Math.ceil((target * total - 100.0 * attended) / (100.0 - target)));
    }

    Attendance withApprovedOd(int credit, boolean excludeFromTotal) {
        if (!countsKnown || credit < 0 || credit > total - attended)
            throw new IllegalArgumentException("OD credit must match recorded absent classes");
        return new Attendance(code, name, attended + (excludeFromTotal ? 0 : credit),
                total - (excludeFromTotal ? credit : 0));
    }

    static boolean isApprovedOd(String type, String status) {
        if (type == null || status == null || !"approved".equalsIgnoreCase(status.trim())) return false;
        String normalized = type.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z]", "");
        return "od".equals(normalized) || "onduty".equals(normalized);
    }

    static boolean isOdType(String type) {
        if (type == null) return false;
        String normalized = type.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z]", "");
        return "od".equals(normalized) || "onduty".equals(normalized);
    }

    String guidance(double target) {
        if (!countsKnown) return "MyCamu shows the percentage, but not attended / total counts. Safe absence guidance is unavailable.";
        if (total == 0) return "No classes recorded yet";
        if (percentage() < target) {
            int periods = periodsToRecover(target);
            return periods == Integer.MAX_VALUE ? "A 100% target cannot be recovered after missed periods"
                    : "Attend " + periods + " periods in a row to reach " + Math.round(target) + "%";
        }
        int absences = safeAbsences(target);
        if (absences == 0) return "One missed period would put you below " + Math.round(target) + "%";
        return "You can miss " + absences + " more periods and stay at " + Math.round(target) + "%";
    }
}
