package com.iniexe.rollcall;

import java.util.List;
import java.util.Locale;

final class ProviderClassMarkMatcher {
    private ProviderClassMarkMatcher() { }

    static LiveSnapshotStore.ClassSession match(String code, long startsAt,
                                                List<LiveSnapshotStore.ClassSession> timetable) {
        String normalized = normalize(code);
        if (normalized.isEmpty()) return null;
        for (LiveSnapshotStore.ClassSession session : timetable) {
            String sessionCode = normalize(session.code);
            if (!sessionCode.isEmpty() && normalized.equals(sessionCode)
                    && Math.abs(session.startsAt - startsAt) < 60_000L) return session;
        }
        return null;
    }

    private static String normalize(String code) {
        return code.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
