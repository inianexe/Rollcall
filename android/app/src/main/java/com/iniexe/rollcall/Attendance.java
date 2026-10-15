package com.iniexe.rollcall;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class Attendance {
    private static final Pattern ROW = Pattern.compile("^([\\w.-]+)\\s*[-–—]\\s*(.+?)\\s*:\\s*(\\d+(?:\\.\\d+)?)%\\s*[-–—:]?\\s*\\(\\s*(\\d+)\\s*/\\s*(\\d+)\\s*\\)$");
    final String code, name;
    final int attended, total;

    Attendance(String code, String name, int attended, int total) {
        if (code == null || code.trim().isEmpty() || name == null || name.trim().isEmpty() || attended < 0 || total < attended || total > 1_000_000)
            throw new IllegalArgumentException("Invalid course or attendance counts");
        this.code = code; this.name = name; this.attended = attended; this.total = total;
    }

    static Attendance parse(String text) {
        Matcher m = ROW.matcher(text.trim());
        if (!m.matches()) return null;
        try {
            int attended = Integer.parseInt(m.group(4)), total = Integer.parseInt(m.group(5));
            if (attended > total || total > 1_000_000) return null;
            double displayed = Double.parseDouble(m.group(3));
            if (total > 0 && Math.abs(100.0 * attended / total - displayed) > 1.01) return null;
            return new Attendance(m.group(1), m.group(2).trim(), attended, total);
        } catch (NumberFormatException e) { return null; }
    }

    double percentage() { return total == 0 ? 0 : 100.0 * attended / total; }
    int safeAbsences() { return Math.max(0, (4 * attended - 3 * total) / 3); }
    int periodsToRecover() { return Math.max(0, 3 * total - 4 * attended); }
    String guidance() {
        if (total == 0) return "No classes recorded yet";
        if (periodsToRecover() > 0) return "Attend " + periodsToRecover() + " periods to reach 75%";
        if (safeAbsences() == 0) return "Missing the next period drops below 75%";
        return "You can miss " + safeAbsences() + " more periods";
    }
}
