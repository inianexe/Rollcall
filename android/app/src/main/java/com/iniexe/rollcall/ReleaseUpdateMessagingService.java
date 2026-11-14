package com.iniexe.rollcall;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.os.Build;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.google.firebase.auth.FirebaseAuth;

import java.util.Map;

public final class ReleaseUpdateMessagingService extends FirebaseMessagingService {
    private static final String CHANNEL = "app_updates";
    private static final String WEB_LOGIN_CHANNEL = "web_sign_in";
    private static final String DEVICE_LINK_CHANNEL = "device_link";
    private static final int NOTIFICATION_ID = 0x524f4c4c;

    @Override public void onNewToken(String token) {
        UpdatePushNotifications.subscribeIfAllowed(this);
        CloudAccountClient.registerCurrentApp(this, token);
        CloudAccountClient.registerCurrentDevice(this, token);
    }

    @Override public void onMessageReceived(RemoteMessage message) {
        Map<String, String> data = message.getData();
        if ("device_replacement".equals(data.get("type"))) {
            if (!belongsToSignedInUser(data)) return;
            String code = data.get("replacementCode");
            if (code != null && code.matches("[0-9]{6}")) showDeviceReplacementCode(code);
            return;
        }
        if ("web_login".equals(data.get("type"))) {
            if (!belongsToSignedInUser(data)) return;
            String challengeId = data.get("challengeId");
            if (challengeId != null && challengeId.length() <= 100) {
                getSharedPreferences("rollcall_cloud_account", MODE_PRIVATE).edit()
                        .putString("pending_web_challenge_id", challengeId).apply();
                showWebLoginRequest(challengeId);
            }
            return;
        }
        if (!"update".equals(data.get("type"))) return;
        try {
            long current = Build.VERSION.SDK_INT >= 28
                    ? getPackageManager().getPackageInfo(getPackageName(), 0).getLongVersionCode()
                    : getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
            if (Long.parseLong(data.get("versionCode")) <= current) return;
        } catch (Exception ignored) { return; }

        ReleaseUpdateNotifier.checkNow(this, (release, error) -> {
            if (release != null) showUpdateNotification(release);
        });
    }

    private boolean belongsToSignedInUser(Map<String, String> data) {
        com.google.firebase.auth.FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return user != null && user.isEmailVerified() && user.getUid().equals(data.get("uid"));
    }

    private void showWebLoginRequest(String challengeId) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null || (Build.VERSION.SDK_INT >= 24 && !manager.areNotificationsEnabled())) return;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(WEB_LOGIN_CHANNEL, "Web sign-in requests",
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Alerts you when a browser requests access to your Rollcall data");
            manager.createNotificationChannel(channel);
        }
        android.content.Intent intent = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (intent == null) return;
        intent.putExtra("rollcall_web_challenge_id", challengeId);
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent open = PendingIntent.getActivity(this, challengeId.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, WEB_LOGIN_CHANNEL) : new Notification.Builder(this);
        builder.setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
                .setContentTitle("Rollcall web sign-in")
                .setContentText("Open Rollcall to view your one-time code.")
                .setContentIntent(open).setAutoCancel(true)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setVisibility(Notification.VISIBILITY_PRIVATE);
        manager.notify(0x524f5700 | (challengeId.hashCode() & 0xff), builder.build());
    }

    private void showDeviceReplacementCode(String code) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null || (Build.VERSION.SDK_INT >= 24 && !manager.areNotificationsEnabled())) return;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(DEVICE_LINK_CHANNEL, "Phone link codes",
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("One-time codes requested when linking another Rollcall phone");
            manager.createNotificationChannel(channel);
        }
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, DEVICE_LINK_CHANNEL) : new Notification.Builder(this);
        Notification.Builder privateVersion = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, DEVICE_LINK_CHANNEL) : new Notification.Builder(this);
        builder.setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
                .setContentTitle("Rollcall phone-link code")
                .setContentText(code + " · expires in 10 min · enter on the new phone")
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setPublicVersion(privateVersion.setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
                        .setContentTitle("Rollcall phone-link code")
                        .setContentText("Unlock to view this one-time code.").build());
        manager.notify(0x524f4c50, builder.build());
    }

    private void showUpdateNotification(ReleaseUpdateNotifier.Release release) {
        if (!ClassReminderScheduler.canNotify(this)) return;
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null || (Build.VERSION.SDK_INT >= 24 && !manager.areNotificationsEnabled())) return;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL, "App updates",
                    NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Announcements when a new Rollcall update is available");
            manager.createNotificationChannel(channel);
        }
        int requestCode = release.tag.hashCode();
        PendingIntent open = PendingIntent.getActivity(this, requestCode,
                ReleaseUpdateNotifier.updateIntent(this, release),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        builder.setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
                .setContentTitle("Rollcall update · " + release.tag)
                .setContentText(release.title)
                .setStyle(new Notification.BigTextStyle().bigText("Version " + release.tag
                        + " is ready. Tap to review the notes and install it."))
                .setContentIntent(open).setAutoCancel(true)
                .setCategory(Notification.CATEGORY_RECOMMENDATION)
                .setVisibility(Notification.VISIBILITY_PRIVATE);
        manager.notify(NOTIFICATION_ID, builder.build());
    }
}
