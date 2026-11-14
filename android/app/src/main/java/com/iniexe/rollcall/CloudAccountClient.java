package com.iniexe.rollcall;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

final class CloudAccountClient {
    private static final String API = BuildConfig.ROLLCALL_API_BASE_URL;
    private static final String PREFS = "rollcall_cloud_account";
    private CloudAccountClient() { }

    static String deviceId(Context context) {
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String id = prefs.getString("device_id", "");
        if (!id.isEmpty()) return id;
        id = UUID.randomUUID().toString();
        prefs.edit().putString("device_id", id).apply();
        return id;
    }

    static JSONObject registerDevice(String idToken, String deviceId, String fcmToken, String emailCode) throws Exception {
        JSONObject body = new JSONObject().put("deviceId", deviceId).put("fcmToken", fcmToken);
        if (emailCode != null && !emailCode.trim().isEmpty()) body.put("emailCode", emailCode.trim());
        return request("/device/register", "POST", body, idToken);
    }

    static void sendDeviceReplacementCode(String idToken) throws Exception {
        request("/device/replacement-code", "POST", new JSONObject(), idToken);
    }

    static void registerCurrentDevice(Context context, String fcmToken) {
        com.google.firebase.auth.FirebaseUser user = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || !user.isEmailVerified()) return;
        String deviceId = deviceId(context);
        if (!deviceId.equals(context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("registered_device_id", ""))) return;
        user.getIdToken(false).addOnSuccessListener(token -> new Thread(() -> {
            try { registerDevice(token.getToken(), deviceId, fcmToken, ""); }
            catch (Exception ignored) { }
        }, "rollcall-device-register").start());
    }

    static JSONObject fetchPendingCode(String idToken, String deviceId, String challengeId) throws Exception {
        return request("/auth/pending", "POST", new JSONObject().put("deviceId", deviceId).put("challengeId", challengeId), idToken);
    }

    static void putSnapshot(String idToken, String deviceId, JSONObject snapshot) throws Exception {
        request("/snapshot", "PUT", new JSONObject().put("deviceId", deviceId).put("snapshot", snapshot), idToken);
    }

    static void deleteCloudAccount(String idToken) throws Exception {
        request("/account/delete", "POST", new JSONObject(), idToken);
    }

    static JSONObject request(String path, String method, JSONObject body, String idToken) throws Exception {
        if (!API.startsWith("https://")) throw new IllegalStateException("Rollcall web sync is not configured yet.");
        HttpURLConnection connection = (HttpURLConnection) new URL(API + path).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(12_000);
        connection.setReadTimeout(15_000);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("Authorization", "Bearer " + idToken);
        connection.setDoOutput(true);
        try {
            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream out = connection.getOutputStream()) { out.write(bytes); }
            int status = connection.getResponseCode();
            InputStream input = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (input != null) try (InputStream in = input) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = in.read(buffer)) != -1) output.write(buffer, 0, count);
            }
            JSONObject response = output.size() == 0 ? new JSONObject() : new JSONObject(output.toString(StandardCharsets.UTF_8));
            if (status < 200 || status >= 300) throw new ApiException(status, response.optString("code"),
                    response.optString("error", "Cloud request failed (" + status + ")."));
            return response;
        } finally { connection.disconnect(); }
    }

    static final class ApiException extends IllegalStateException {
        final int status;
        final String code;
        ApiException(int status, String code, String message) { super(message); this.status = status; this.code = code; }
    }

    static void enqueueSnapshotSync(Context context) {
        if (com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() == null) return;
        Constraints constraints = new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(CloudSnapshotSyncWorker.class)
                .setConstraints(constraints).setInitialDelay(2, TimeUnit.SECONDS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build();
        WorkManager.getInstance(context.getApplicationContext()).enqueueUniqueWork("rollcall-cloud-snapshot", ExistingWorkPolicy.REPLACE, request);
    }

    static void setLastCloudSync(Context context, long time) {
        context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong("last_sync", time).apply();
    }

    static long lastCloudSync(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong("last_sync", 0);
    }
}
