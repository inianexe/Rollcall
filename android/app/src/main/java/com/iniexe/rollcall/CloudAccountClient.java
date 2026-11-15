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
import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

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

    static JSONObject startEmailSignup(String email) throws Exception {
        return request("/auth/signup/start", "POST", new JSONObject().put("email", email), null);
    }

    static JSONObject completeEmailSignup(String email, String code, String password) throws Exception {
        return request("/auth/signup/complete", "POST",
                new JSONObject().put("email", email).put("code", code).put("password", password), null);
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

    static JSONObject previewWebSignIn(String idToken, String deviceId, WebLoginQr.Payload challenge) throws Exception {
        return request("/auth/qr/preview", "POST", webQrBody(deviceId, challenge), idToken);
    }

    static void decideWebSignIn(String idToken, String deviceId, WebLoginQr.Payload challenge, boolean approve) throws Exception {
        request(approve ? "/auth/qr/approve" : "/auth/qr/deny", "POST", webQrBody(deviceId, challenge), idToken);
    }

    private static JSONObject webQrBody(String deviceId, WebLoginQr.Payload challenge) throws Exception {
        return new JSONObject().put("deviceId", deviceId).put("challengeId", challenge.challengeId).put("secret", challenge.secret);
    }

    static void putSnapshot(String idToken, String deviceId, JSONObject snapshot) throws Exception {
        request("/snapshot", "PUT", new JSONObject().put("deviceId", deviceId).put("snapshot", snapshot), idToken);
    }

    static JSONObject fetchAnnouncements(String idToken) throws Exception {
        return request("/announcements", "GET", null, idToken);
    }

    static List<DashboardView.Announcement> parseAnnouncements(JSONObject response) {
        ArrayList<DashboardView.Announcement> result = new ArrayList<>();
        JSONArray rows = response == null ? null : response.optJSONArray("announcements");
        if (rows == null) return result;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) continue;
            String id = row.optString("id", "");
            String title = row.optString("title", "").trim();
            String message = row.optString("body", "").trim();
            if (!id.matches("[0-9a-fA-F-]{36}") || title.isEmpty() || message.isEmpty()) continue;
            DashboardView.Announcement item = new DashboardView.Announcement();
            item.id = id;
            item.title = title;
            item.body = message;
            item.category = row.optString("category", "announcement");
            item.priority = row.optString("priority", "normal");
            item.readState = row.optString("readState", "unread");
            JSONArray channels = row.optJSONArray("channels");
            if (channels != null) for (int channel = 0; channel < channels.length(); channel++)
                if ("popup".equals(channels.optString(channel))) item.showPopup = true;
            item.actionLabel = row.optString("actionLabel", "");
            item.actionPath = row.optString("actionPath", "");
            item.publishedAt = row.optLong("publishedAt", 0);
            item.expiresAt = row.optLong("expiresAt", 0);
            result.add(item);
        }
        return result;
    }

    static void setAnnouncementReceipt(String idToken, String campaignId, String state) throws Exception {
        if (campaignId == null || !campaignId.matches("[0-9a-fA-F-]{36}")) throw new IllegalArgumentException("Invalid announcement ID.");
        if (!"read".equals(state) && !"dismissed".equals(state)) throw new IllegalArgumentException("Invalid announcement receipt.");
        request("/announcements/" + campaignId + "/receipt", "POST", new JSONObject().put("state", state), idToken);
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
        if (idToken != null && !idToken.isEmpty()) connection.setRequestProperty("Authorization", "Bearer " + idToken);
        try {
            if (!"GET".equalsIgnoreCase(method)) {
                connection.setDoOutput(true);
                byte[] bytes = (body == null ? new JSONObject() : body).toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream out = connection.getOutputStream()) { out.write(bytes); }
            }
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

}
