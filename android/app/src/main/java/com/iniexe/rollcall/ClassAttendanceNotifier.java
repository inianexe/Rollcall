package com.iniexe.rollcall;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

final class ClassAttendanceNotifier {
    private static final String CHANNEL = "attendance_updates";
    static final long MISSING_MARK_GRACE_MS = 5 * 60_000L;

    private ClassAttendanceNotifier() { }

    static void ensureChannels(Context context) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        manager.createNotificationChannel(new NotificationChannel(CHANNEL, "Attendance updates",
                NotificationManager.IMPORTANCE_DEFAULT));
        manager.createNotificationChannel(new NotificationChannel("attendance_target", "Attendance target",
                NotificationManager.IMPORTANCE_DEFAULT));
        manager.createNotificationChannel(new NotificationChannel("od_updates", "Leave and OD updates",
                NotificationManager.IMPORTANCE_DEFAULT));
        manager.createNotificationChannel(new NotificationChannel("schedule_updates", "Schedule updates",
                NotificationManager.IMPORTANCE_DEFAULT));
        manager.createNotificationChannel(new NotificationChannel("sync_status", "Sync status",
                NotificationManager.IMPORTANCE_LOW));
    }

    static boolean postEvent(Context context, String channel, String title, String message, String destination, int id) {
        if (!ClassReminderScheduler.canNotify(context)) return false;
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null || (Build.VERSION.SDK_INT >= 24 && !manager.areNotificationsEnabled())) return false;
        try {
            ensureChannels(context);
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel existing = manager.getNotificationChannel(channel);
                if (existing == null || existing.getImportance() == NotificationManager.IMPORTANCE_NONE) return false;
            }
            Intent open = new Intent(context, MainActivity.class).putExtra("notification_destination", destination)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent content = PendingIntent.getActivity(context, id,
                    open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                    ? new Notification.Builder(context, channel) : new Notification.Builder(context);
            builder.setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(message)
                    .setContentIntent(content).setAutoCancel(true).setCategory(Notification.CATEGORY_STATUS)
                    .setVisibility(Notification.VISIBILITY_PRIVATE);
            manager.notify(id, builder.build());
            return true;
        } catch (SecurityException | IllegalArgumentException ignored) { return false; }
    }

    static boolean post(Context context, String sessionId, String course, String status) {
        if (!ClassReminderScheduler.canNotify(context)) return false;
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null || (Build.VERSION.SDK_INT >= 24 && !manager.areNotificationsEnabled())) return false;
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel channel = new NotificationChannel(CHANNEL, "Attendance updates",
                        NotificationManager.IMPORTANCE_DEFAULT);
                channel.setDescription("Updates when class attendance is recorded or still missing");
                manager.createNotificationChannel(channel);
                NotificationChannel existing = manager.getNotificationChannel(CHANNEL);
                if (existing != null && existing.getImportance() == NotificationManager.IMPORTANCE_NONE) return false;
            }
            String fullCourse = course == null || course.trim().isEmpty() ? "Class" : course.trim();
            String statusLabel = "present".equals(status) ? "PRESENT"
                    : "absent".equals(status) ? "ABSENT" : "NOT MARKED";
            String title = DashboardView.shortCourseName(fullCourse) + " · " + statusLabel;
            String message = "present".equals(status) ? fullCourse + " · Attendance marked present"
                    : "absent".equals(status) ? fullCourse + " · Attendance marked absent"
                    : fullCourse + " · Attendance has not been marked yet";
            int id = ("attendance:" + sessionId).hashCode() & 0x7fffffff;
            Intent open = new Intent(context, MainActivity.class).putExtra("notification_destination", "week");
            PendingIntent content = PendingIntent.getActivity(context, id, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                    ? new Notification.Builder(context, CHANNEL) : new Notification.Builder(context);
            builder.setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(message)
                    .setContentIntent(content).setAutoCancel(true).setCategory(Notification.CATEGORY_STATUS)
                    .setVisibility(Notification.VISIBILITY_PRIVATE);
            manager.notify(id, builder.build());
            return true;
        } catch (SecurityException | IllegalArgumentException ignored) {
            return false;
        }
    }
}
