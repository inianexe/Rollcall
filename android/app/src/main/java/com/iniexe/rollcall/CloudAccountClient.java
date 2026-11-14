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

    static JSONObject registerDevice(String idToken, String deviceId, String fcmToken, String replacementCode, boolean enableSync) throws Exception {
        JSONObject body = new JSONObject().put("deviceId", deviceId).put("fcmToken", fcmToken).put("enableSync", enableSync);
        if (replacementCode != null && !replacementCode.trim().isEmpty()) body.put("replacementCode", replacementCode.trim());
        return request("/device/register", "POST", body, idToken);
    }

    static void sendDeviceReplacementCode(String idToken, String deviceId) throws Exception {
        request("/device/replacement-code", "POST", new JSONObject().put("deviceId", deviceId), idToken);
    }

    static JSONObject enrollApprovalDevice(Context context, String idToken, String replacementCode) throws Exception {
        com.google.firebase.auth.FirebaseUser user = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) throw new IllegalStateException("Sign in before setting up phone approvals.");
        String fcmToken = com.google.android.gms.tasks.Tasks.await(
                com.google.firebase.messaging.FirebaseMessaging.getInstance().getToken(), 20, TimeUnit.SECONDS);
        JSONObject body = new JSONObject().put("deviceId", deviceId(context)).put("fcmToken", fcmToken)
                .put("publicKey", ApprovalKeyStore.publicKey(user.getUid()));
        if (replacementCode != null && !replacementCode.trim().isEmpty()) body.put("replacementCode", replacementCode.trim());
        return request("/device/approval/enroll", "POST", body, idToken);
    }

    static JSONObject beginLegacyApprovalEnrollment(String idToken, String deviceId) throws Exception {
        return request("/device/approval/enroll-existing", "POST", new JSONObject().put("deviceId", deviceId), idToken);
    }

    static JSONObject completeLegacyApprovalEnrollment(String idToken, String deviceId, String requestId, String nonce) throws Exception {
        com.google.firebase.auth.FirebaseUser user = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) throw new IllegalStateException("Sign in before setting up phone approvals.");
        String publicKey = ApprovalKeyStore.publicKey(user.getUid());
        String message = "rollcall:enroll-approval:v1\n" + user.getUid() + "\n" + deviceId + "\n" + requestId + "\n" + nonce;
        return request("/device/approval/enroll-complete", "POST", new JSONObject().put("deviceId", deviceId)
                .put("requestId", requestId).put("nonce", nonce).put("publicKey", publicKey)
                .put("signature", ApprovalKeyStore.sign(user.getUid(), message)), idToken);
    }

    static void registerCurrentApp(Context context) {
        com.google.firebase.messaging.FirebaseMessaging.getInstance().getToken()
                .addOnSuccessListener(token -> registerCurrentApp(context, token))
                .addOnFailureListener(error -> recordAppRegistration(context, error));
    }

    static void registerCurrentApp(Context context, String fcmToken) {
        com.google.firebase.auth.FirebaseUser user = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || !user.isEmailVerified() || fcmToken == null || fcmToken.isEmpty()) return;
        Context app = context.getApplicationContext();
        String id = deviceId(app);
        user.getIdToken(false).addOnSuccessListener(token -> new Thread(() -> {
            try {
                request("/device/trust", "POST", new JSONObject().put("deviceId", id).put("fcmToken", fcmToken), token.getToken());
                app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                        .putString("trusted_app_device_id", id).putString("trusted_app_uid", user.getUid())
                        .putString("app_registration_uid", user.getUid()).remove("app_registration_error").apply();
            } catch (Exception error) { recordAppRegistration(app, error, user.getUid()); }
        }, "rollcall-app-trust").start()).addOnFailureListener(error -> recordAppRegistration(app, error, user.getUid()));
    }

    private static void recordAppRegistration(Context context, Exception error) {
        com.google.firebase.auth.FirebaseUser user = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        recordAppRegistration(context, error, user == null ? "" : user.getUid());
    }

    private static void recordAppRegistration(Context context, Exception error, String uid) {
        String message = error == null || error.getMessage() == null ? "Could not connect this app for account notifications." : error.getMessage();
        context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("app_registration_uid", uid).putString("app_registration_error", message).apply();
    }

    static void unregisterCurrentApp(Context context) {
        com.google.firebase.auth.FirebaseUser user = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        Context app = context.getApplicationContext();
        String id = deviceId(app);
        user.getIdToken(false).addOnSuccessListener(token -> new Thread(() -> {
            try { request("/device/untrust", "POST", new JSONObject().put("deviceId", id), token.getToken()); }
            catch (Exception ignored) { }
            app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .remove("trusted_app_device_id").remove("trusted_app_uid").apply();
        }, "rollcall-app-untrust").start());
    }

    static void registerCurrentDevice(Context context, String fcmToken) {
        com.google.firebase.auth.FirebaseUser user = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || !user.isEmailVerified() || !CloudSyncPolicy.allowsUpload(user.getUid(),
                context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("cloud_sync_uid", ""))) return;
        String deviceId = deviceId(context);
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!deviceId.equals(prefs.getString("registered_device_id", ""))
                || !user.getUid().equals(prefs.getString("registered_uid", user.getUid()))) return;
        user.getIdToken(false).addOnSuccessListener(token -> new Thread(() -> {
            try { registerDevice(token.getToken(), deviceId, fcmToken, "", false); }
            catch (Exception ignored) { }
        }, "rollcall-device-register").start());
    }

    static JSONObject respondToWebLogin(String idToken, String deviceId, String challengeId, String matchCode, boolean approve) throws Exception {
        com.google.firebase.auth.FirebaseUser user = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) throw new IllegalStateException("Sign in to your Rollcall account to approve this request.");
        String action = approve ? "approve" : "deny";
        String message = "rollcall:web-" + action + ":v1\n" + user.getUid() + "\n" + deviceId + "\n" + challengeId + "\n" + matchCode;
        return request("/auth/" + action, "POST", new JSONObject().put("deviceId", deviceId).put("challengeId", challengeId)
                .put("signature", ApprovalKeyStore.sign(user.getUid(), message)), idToken);
    }

    static void putSnapshot(String idToken, String deviceId, JSONObject snapshot) throws Exception {
        request("/snapshot", "PUT", new JSONObject().put("deviceId", deviceId).put("snapshot", snapshot), idToken);
    }

    static void deleteCloudAccount(String idToken) throws Exception {
        request("/account/delete", "POST", new JSONObject(), idToken);
    }

    static void deleteCloudSnapshot(String idToken) throws Exception {
        request("/snapshot", "DELETE", new JSONObject(), idToken);
    }

    static void pauseCloudSync(String idToken) throws Exception {
        request("/device/sync/pause", "POST", new JSONObject(), idToken);
    }

    static JSONObject webSessions(String idToken) throws Exception {
        return request("/auth/sessions", "GET", new JSONObject(), idToken);
    }

    static void revokeWebSession(String idToken, String sessionId) throws Exception {
        request("/auth/sessions/revoke", "POST", new JSONObject().put("sessionId", sessionId), idToken);
    }

    static void revokeAllWebSessions(String idToken) throws Exception {
        request("/auth/sessions/revoke", "POST", new JSONObject().put("all", true), idToken);
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
        connection.setDoOutput(!"GET".equals(method));
        try {
            if (connection.getDoOutput()) {
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
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
        com.google.firebase.auth.FirebaseUser user = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || !CloudSyncPolicy.allowsUpload(user.getUid(), context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("cloud_sync_uid", ""))) return;
        Constraints constraints = new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(CloudSnapshotSyncWorker.class)
                .setConstraints(constraints).setInitialDelay(2, TimeUnit.SECONDS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build();
        WorkManager.getInstance(context.getApplicationContext()).enqueueUniqueWork("rollcall-cloud-snapshot", ExistingWorkPolicy.REPLACE, request);
    }

}
