package com.iniexe.rollcall;

import android.content.Context;
import android.app.NotificationManager;
import android.os.Build;

import com.google.firebase.messaging.FirebaseMessaging;

final class UpdatePushNotifications {
    private UpdatePushNotifications() { }

    static void subscribeIfAllowed(Context context) {
        if (!ClassReminderScheduler.canNotify(context)) return;
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 24 && (manager == null || !manager.areNotificationsEnabled())) return;
        FirebaseMessaging.getInstance().subscribeToTopic("app_updates");
    }
}
