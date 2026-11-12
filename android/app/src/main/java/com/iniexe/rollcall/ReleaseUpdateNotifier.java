package com.iniexe.rollcall;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class ReleaseUpdateNotifier {
    private static final String API = "https://api.github.com/repos/inianexe/Rollcall/releases/tags/v1.0.0";
    private static final long CHECK_INTERVAL = 6L * 60 * 60 * 1000;
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    interface CheckCallback { void onResult(Release release, String error); }

    static final class Release {
        final String tag, title, notes, releaseUrl, apkName, downloadUrl, digest;
        final long versionCode;
        Release(String tag, String title, String notes, String releaseUrl,
                String apkName, String downloadUrl, String digest, long versionCode) {
            this.tag = tag; this.title = title; this.notes = notes; this.releaseUrl = releaseUrl;
            this.apkName = apkName; this.downloadUrl = downloadUrl; this.digest = digest;
            this.versionCode = versionCode;
        }
    }

    private ReleaseUpdateNotifier() { }

    static void check(Context context) {
        Context app = context.getApplicationContext();
        android.content.SharedPreferences prefs = app.getSharedPreferences("release_updates", Context.MODE_PRIVATE);
        boolean snoozed = prefs.getInt("snooze_count", 0) > 0;
        long now = System.currentTimeMillis();
        if (!snoozed && now - prefs.getLong("last_check", 0) < CHECK_INTERVAL) return;
        EXECUTOR.execute(() -> {
            try {
                Release release = fetchLatest(app);
                prefs.edit().putLong("last_check", now).apply();
                if (release != null && isNewer(release, app)) {
                    new Handler(Looper.getMainLooper()).post(() -> openUpdate(app, release));
                }
            } catch (Exception ignored) {
                // An optional update lookup must not affect app startup or MyCamu sync.
            }
        });
    }

    static void checkNow(Context context, CheckCallback callback) {
        Context app = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            Release release = null;
            String error = "";
            try {
                Release latest = fetchLatest(app);
                if (latest != null && isNewer(latest, app)) release = latest;
            } catch (Exception ignored) { error = "Couldn’t reach GitHub. Check your connection and try again."; }
            Release result = release;
            String resultError = error;
            new Handler(Looper.getMainLooper()).post(() -> callback.onResult(result, resultError));
        });
    }

    static void openUpdate(Context context, Release release) {
        Intent intent = updateIntent(context, release);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    static Intent updateIntent(Context context, Release release) {
        Intent intent = new Intent(context, UpdateActivity.class);
        intent.putExtra(UpdateActivity.EXTRA_TAG, release.tag);
        intent.putExtra(UpdateActivity.EXTRA_TITLE, release.title);
        intent.putExtra(UpdateActivity.EXTRA_NOTES, release.notes);
        intent.putExtra(UpdateActivity.EXTRA_RELEASE_URL, release.releaseUrl);
        intent.putExtra(UpdateActivity.EXTRA_APK_NAME, release.apkName);
        intent.putExtra(UpdateActivity.EXTRA_DOWNLOAD_URL, release.downloadUrl);
        intent.putExtra(UpdateActivity.EXTRA_DIGEST, release.digest);
        return intent;
    }

    private static Release fetchLatest(Context context) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(API).openConnection();
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("User-Agent", "Rollcall-Android");
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) throw new IllegalStateException("GitHub response");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = connection.getInputStream().read(buffer)) != -1) {
                if (output.size() + count > 256 * 1024) throw new IllegalStateException("Release response too large");
                output.write(buffer, 0, count);
            }
            JSONObject release = new JSONObject(new String(output.toByteArray(), StandardCharsets.UTF_8));
            String tag = release.optString("tag_name", ""), url = release.optString("html_url", "");
            if (tag.isEmpty() || release.optBoolean("draft") || release.optBoolean("prerelease") || !isGithubRelease(url)) return null;
            String body = release.optString("body", "");
            Matcher code = Pattern.compile("(?m)^<!-- rollcall-version-code: (\\d+) -->\\s*$").matcher(body);
            Matcher versionNameMatch = Pattern.compile("(?m)^<!-- rollcall-version-name: ([A-Za-z0-9._-]{1,80}) -->\\s*$").matcher(body);
            long versionCode = code.find() ? Long.parseLong(code.group(1)) : -1;
            String versionName = versionNameMatch.find() ? versionNameMatch.group(1) : tag;
            String notes = body.replaceAll("(?m)^<!-- rollcall-version-(?:code|name): .* -->\\s*$", "").trim();
            JSONArray assets = release.optJSONArray("assets");
            if (assets == null) return null;
            for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.optJSONObject(i);
                if (asset == null) continue;
                String name = asset.optString("name", ""), download = asset.optString("browser_download_url", "");
                if (!name.toLowerCase(java.util.Locale.ROOT).endsWith(".apk") || !isGithubDownload(download)) continue;
                return new Release(versionName, release.optString("name", "Rollcall update"),
                        notes, url, name, download, asset.optString("digest", ""), versionCode);
            }
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String versionName(Context context) throws PackageManager.NameNotFoundException {
        PackageInfo info = Build.VERSION.SDK_INT >= 33
                ? context.getPackageManager().getPackageInfo(context.getPackageName(), PackageManager.PackageInfoFlags.of(0))
                : context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
        return info.versionName == null ? "0" : info.versionName;
    }

    private static long versionCode(Context context) throws PackageManager.NameNotFoundException {
        PackageInfo info = Build.VERSION.SDK_INT >= 33
                ? context.getPackageManager().getPackageInfo(context.getPackageName(), PackageManager.PackageInfoFlags.of(0))
                : context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
        return Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
    }

    private static boolean isNewer(Release release, Context context) throws PackageManager.NameNotFoundException {
        return release.versionCode > 0 ? release.versionCode > versionCode(context)
                : isNewer(release.tag, versionName(context));
    }

    private static boolean isNewer(String latest, String current) {
        String[] a = latest.replaceFirst("^[vV]", "").split("[.+-]", 4);
        String[] b = current.replaceFirst("^[vV]", "").split("[.+-]", 4);
        try {
            for (int i = 0; i < Math.max(a.length, b.length); i++) {
                int x = i < a.length ? Integer.parseInt(a[i]) : 0;
                int y = i < b.length ? Integer.parseInt(b[i]) : 0;
                if (x != y) return x > y;
            }
        } catch (NumberFormatException ignored) { return false; }
        return false;
    }

    private static boolean isGithubRelease(String value) {
        Uri uri = Uri.parse(value);
        return "https".equals(uri.getScheme()) && "github.com".equals(uri.getHost())
                && uri.getPath() != null && uri.getPath().startsWith("/inianexe/Rollcall/releases/");
    }

    private static boolean isGithubDownload(String value) {
        Uri uri = Uri.parse(value);
        return "https".equals(uri.getScheme()) && "github.com".equals(uri.getHost())
                && uri.getPath() != null && uri.getPath().startsWith("/inianexe/Rollcall/releases/download/");
    }

}
