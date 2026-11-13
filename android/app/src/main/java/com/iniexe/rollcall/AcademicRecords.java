package com.iniexe.rollcall;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Grade and semester labels shared by the academic results presentation. */
final class AcademicRecords {
    private static final Pattern SEMESTER_NUMBER = Pattern.compile("(?i)semester\\s*[-:]?\\s*(\\d+)");
    private static final Pattern SEMESTER_TITLE = Pattern.compile("(?i)semester\\s*[-:]?\\s*\\d+");
    private static final Pattern YEAR = Pattern.compile("20\\d{2}");

    private AcademicRecords() { }

    static int gradePoint(String grade) {
        if (grade == null) return -1;
        switch (grade.trim().toUpperCase(Locale.ROOT)) {
            case "O": case "S": return 10;
            case "A+": return 9;
            case "A": return 8;
            case "B+": return 7;
            case "B": return 6;
            case "C": return 5;
            case "U": case "SA": case "WD": return 0;
            default: return -1;
        }
    }

    static int semesterNumber(String value) {
        Matcher match = SEMESTER_NUMBER.matcher(value);
        if (!match.find()) return 0;
        int term = Integer.parseInt(match.group(1));
        Matcher year = YEAR.matcher(value);
        return year.find() ? Integer.parseInt(year.group()) * 10 + term : term;
    }

    static String semesterTitle(String value) {
        Matcher match = SEMESTER_TITLE.matcher(value);
        return match.find() ? match.group().replaceAll("\\s+", " ") : value;
    }

    static String semesterShortLabel(String value) {
        Matcher match = SEMESTER_NUMBER.matcher(value);
        return match.find() ? "S" + match.group(1) : "Sem";
    }
}
