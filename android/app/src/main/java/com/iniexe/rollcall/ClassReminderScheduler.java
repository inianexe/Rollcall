package com.iniexe.rollcall;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class ClassReminderScheduler {
    static final String ACTION_REMINDER = "com.iniexe.rollcall.CLASS_REMINDER";
    static final String ACTION_RESCHEDULE = "com.iniexe.rollcall.RESCHEDULE_REMINDERS";
    private static final String PREFS = "class_reminders";
    private static final String ENABLED = "enabled";
    private static final String IDS = "scheduled_ids";
    private static final String CHANNEL = "class_reminders";

    private ClassReminderScheduler() { }

    static boolean enabled(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(ENABLED, true);
    }

    static boolean canNotify(Context context) {
        return Build.VERSION.SDK_INT < 33
                || context.checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    static void setEnabled(Context context, boolean enabled) {
        context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(ENABLED, enabled).apply();
        schedule(context, LiveSnapshotStore.get().timetable);
        RollcallTodayWidget.refresh(context);
    }

    static void scheduleSaved(Context context) {
        LiveSnapshotStore.initialize(context);
        schedule(context, LiveSnapshotStore.get().timetable);
    }

    static void schedule(Context context, List<LiveSnapshotStore.ClassSession> sessions) {
        Context app = context.getApplicationContext();
        android.content.SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Set<String> oldIds = new HashSet<>(prefs.getStringSet(IDS, new HashSet<>()));
        AlarmManager alarms = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
        NotificationManager notifications = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
        for (String value : oldIds) {
            try {
                int id = Integer.parseInt(value);
                PendingIntent pending = pendingIntent(app, id, PendingIntent.FLAG_NO_CREATE);
                if (pending != null && alarms != null) alarms.cancel(pending);
                if (pending != null) pending.cancel();
                if (notifications != null) notifications.cancel(id);
            } catch (NumberFormatException ignored) { }
        }

        Set<String> newIds = new HashSet<>();
        if (enabled(app) && canNotify(app) && alarms != null) {
            long now = System.currentTimeMillis();
            for (LiveSnapshotStore.ClassSession session : sessions) {
                if (session.allDay || session.cancelled || session.startsAt <= now) continue;
                long triggerAt = session.startsAt - 10 * 60_000L;
                if (triggerAt <= now) triggerAt = now + 2_000L;
                int id = (session.id + "_" + session.startsAt).hashCode() & 0x7fffffff;
                if (!newIds.add(String.valueOf(id))) continue;
                Intent intent = new Intent(app, ClassReminderReceiver.class).setAction(ACTION_REMINDER)
                        .setData(android.net.Uri.parse("rollcall://class-reminder/" + id))
                        .putExtra("id", id).putExtra("course", session.name).putExtra("code", session.code)
                        .putExtra("starts_at", session.startsAt);
                PendingIntent pending = PendingIntent.getBroadcast(app, id, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending);
            }
        }
        prefs.edit().putStringSet(IDS, new HashSet<>(newIds)).apply();
    }

    static void showReminder(Context context, Intent intent) {
        Context app = context.getApplicationContext();
        if (!enabled(app) || !canNotify(app)) return;
        NotificationManager manager = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            android.app.NotificationChannel channel = new android.app.NotificationChannel(CHANNEL,
                    "Class reminders", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Optional reminders before scheduled classes");
            manager.createNotificationChannel(channel);
        }
        int id = intent.getIntExtra("id", 0);
        String course = intent.getStringExtra("course");
        String code = intent.getStringExtra("code");
        String title = course == null || course.trim().isEmpty() ? "Your next class" : course;
        if (code != null && !code.trim().isEmpty()) title += " · " + code;
        String text = "Your scheduled class starts soon.";
        Intent open = new Intent(app, MainActivity.class);
        PendingIntent content = PendingIntent.getActivity(app, id, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        android.app.Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new android.app.Notification.Builder(app, CHANNEL)
                : new android.app.Notification.Builder(app);
        builder.setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(title).setContentText(text).setContentIntent(content)
                .setCategory(android.app.Notification.CATEGORY_REMINDER)
                .setVisibility(android.app.Notification.VISIBILITY_PRIVATE).setAutoCancel(true);
        if (Build.VERSION.SDK_INT >= 21) {
            android.app.Notification.Builder publicBuilder = new android.app.Notification.Builder(app)
                    .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                    .setContentTitle("Class reminder").setContentText("A scheduled class starts soon.");
            builder.setPublicVersion(publicBuilder.build());
        }
        manager.notify(id, builder.build());
    }

    private static PendingIntent pendingIntent(Context context, int id, int mode) {
        Intent intent = new Intent(context, ClassReminderReceiver.class).setAction(ACTION_REMINDER)
                .setData(android.net.Uri.parse("rollcall://class-reminder/" + id));
        int flags = mode | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        return PendingIntent.getBroadcast(context, id, intent, flags);
    }
}
