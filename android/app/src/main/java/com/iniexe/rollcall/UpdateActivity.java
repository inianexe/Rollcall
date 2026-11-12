package com.iniexe.rollcall;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.database.Cursor;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class UpdateActivity extends Activity {
    static final String EXTRA_TAG = "release_tag", EXTRA_TITLE = "release_title", EXTRA_NOTES = "release_notes";
    static final String EXTRA_RELEASE_URL = "release_url", EXTRA_APK_NAME = "apk_name";
    static final String EXTRA_DOWNLOAD_URL = "download_url", EXTRA_DIGEST = "apk_digest";
    private static final String PREFS = "rollcall_apk_update";
    private static final String CHANNEL_ID = "download_id", CHANNEL_TAG = "download_tag";
    private static final int REQUEST_INSTALL_SOURCE = 7401, REQUEST_INSTALLER = 7402;
    private static final ExecutorService VERIFY = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable pollDownload = this::readDownload;

    private String tag, title, notes, releaseUrl, apkName, downloadUrl, digest;
    private AppPalette palette;
    private Typeface brandTypeface;
    private LinearLayout root, content;
    private ProgressBar progressBar;
    private TextView progressLabel, statusLabel, stageLabel, backLabel;
    private DownloadManager downloads;
    private SharedPreferences prefs;
    private SharedPreferences releasePrefs;
    private int maybeLaterCount;
    private long downloadId = -1, totalBytes = -1, apkVersionCode = -1;
    private File apkFile;
    private int state;
    private static final int AVAILABLE = 0, DOWNLOADING = 1, VERIFYING = 2, READY = 3,
            INSTALLING = 4, FAILED = 5, INSTALLED = 6;

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        Intent intent = getIntent();
        tag = intent.getStringExtra(EXTRA_TAG);
        title = intent.getStringExtra(EXTRA_TITLE);
        notes = intent.getStringExtra(EXTRA_NOTES);
        releaseUrl = intent.getStringExtra(EXTRA_RELEASE_URL);
        apkName = intent.getStringExtra(EXTRA_APK_NAME);
        downloadUrl = intent.getStringExtra(EXTRA_DOWNLOAD_URL);
        digest = intent.getStringExtra(EXTRA_DIGEST);
        if (!validIntent()) { finish(); return; }

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        releasePrefs = getSharedPreferences("release_updates", MODE_PRIVATE);
        if (tag.equals(releasePrefs.getString("snoozed_tag", ""))) {
            maybeLaterCount = releasePrefs.getInt("snooze_count", 0);
        } else {
            releasePrefs.edit().putString("snoozed_tag", tag).putInt("snooze_count", 0).apply();
        }
        downloads = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        SharedPreferences theme = getSharedPreferences("MainActivity_preferences", MODE_PRIVATE);
        boolean dark = theme.getBoolean("dark_theme", false);
        int index = theme.getInt("color_theme", theme.getInt(dark ? "dark_palette" : "light_palette", 0));
        palette = AppPalette.get(dark, index);
        try { brandTypeface = Typeface.createFromAsset(getAssets(), "fonts/JetBrainsMono-Regular.ttf"); }
        catch (RuntimeException ignored) { brandTypeface = Typeface.MONOSPACE; }
        getWindow().setStatusBarColor(palette.canvas);
        getWindow().setNavigationBarColor(palette.canvas);
        getWindow().getDecorView().setSystemUiVisibility(dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(palette.canvas);
        int statusInset = getResources().getDimensionPixelSize(getResources().getIdentifier("status_bar_height", "dimen", "android"));
        int navigationInset = getResources().getDimensionPixelSize(getResources().getIdentifier("navigation_bar_height", "dimen", "android"));
        root.setPadding(0, statusInset, 0, navigationInset + dp(24));
        backLabel = text("←  ROLLCALL / UPDATES", 12, palette.canvas == 0xFF121212 ? palette.button : palette.highlight, Typeface.BOLD);
        backLabel.setGravity(Gravity.CENTER_VERTICAL);
        backLabel.setPadding(dp(22), 0, dp(22), 0);
        backLabel.setOnClickListener(v -> finish());
        if (maybeLaterCount >= 3) backLabel.setVisibility(View.INVISIBLE);
        root.addView(backLabel, new LinearLayout.LayoutParams(-1, dp(48)));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(22), dp(4), dp(22), dp(12));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);

        long savedId = prefs.getLong(CHANNEL_ID, -1);
        String savedTag = prefs.getString(CHANNEL_TAG, "");
        if (savedId > 0 && tag.equals(savedTag)) {
            downloadId = savedId;
            state = DOWNLOADING;
            render();
            readDownload();
        } else {
            state = AVAILABLE;
            render();
        }
    }

    private boolean validIntent() {
        return safeTag(tag) && isGithubRelease(releaseUrl) && apkName != null
                && apkName.toLowerCase(java.util.Locale.ROOT).endsWith(".apk") && isGithubDownload(downloadUrl);
    }

    private boolean safeTag(String value) {
        return value != null && value.matches("[A-Za-z0-9._-]{1,80}");
    }

    private boolean isGithubRelease(String value) {
        Uri uri = Uri.parse(value == null ? "" : value);
        return "https".equals(uri.getScheme()) && "github.com".equals(uri.getHost())
                && uri.getPath() != null && uri.getPath().startsWith("/inianexe/Rollcall/releases/");
    }

    private boolean isGithubDownload(String value) {
        Uri uri = Uri.parse(value == null ? "" : value);
        return "https".equals(uri.getScheme()) && "github.com".equals(uri.getHost())
                && uri.getPath() != null && uri.getPath().startsWith("/inianexe/Rollcall/releases/download/");
    }

    private void render() {
        content.removeAllViews();
        while (root.getChildCount() > 2) root.removeViewAt(2);

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setGravity(Gravity.CENTER);
        hero.setPadding(dp(20), dp(22), dp(20), dp(22));
        GradientDrawable broadcast = new GradientDrawable();
        broadcast.setColor(0xFFCCFF00);
        broadcast.setCornerRadius(dp(18));
        hero.setBackground(broadcast);
        TextView overline = text("TORCH GENESIS / RELEASE TRANSMISSION", 10,
                0xFF111111, Typeface.BOLD);
        overline.setLetterSpacing(.06f);
        hero.addView(overline);
        TextView mark = text(state == INSTALLED ? "✓" : state == FAILED ? "×" : "↗", 38,
                0xFF111111, Typeface.BOLD);
        mark.setGravity(Gravity.CENTER);
        GradientDrawable badge = new GradientDrawable();
        badge.setColor(0x44FFFFFF);
        badge.setCornerRadius(dp(100));
        mark.setBackground(badge);
        hero.addView(mark, centered(dp(54), dp(54)));
        TextView heading = text(heading().toUpperCase(java.util.Locale.ROOT), 27, 0xFF111111, Typeface.BOLD);
        heading.setLetterSpacing(-.065f);
        heading.setGravity(Gravity.CENTER);
        heading.setPadding(0, dp(10), 0, dp(3));
        hero.addView(heading);
        TextView version = text(state == INSTALLED ? "You’re on " + tag : "Version " + tag,
                12, 0xFF343A26, Typeface.BOLD);
        version.setGravity(Gravity.CENTER);
        hero.addView(version);
        content.addView(hero, topMargin(dp(8)));

        if (state == AVAILABLE || state == FAILED) {
            LinearLayout card = card();
            card.setBackground(makeCardBackground(0xFFC8C8FF, 0xFF111111));
            card.addView(text("WHAT'S NEW / " + tag, 10, 0xFF111111, Typeface.BOLD));
            card.addView(text(title == null || title.trim().isEmpty() ? "What's new" : title,
                    19, 0xFF111111, Typeface.BOLD), topMargin(dp(8)));
            TextView note = text(cleanNotes(notes), 13, 0xFF333344, Typeface.NORMAL);
            note.setLineSpacing(dp(3), 1f);
            note.setPadding(0, dp(10), 0, 0);
            card.addView(note);
            content.addView(card, topMargin(dp(14)));
        }

        if (state == DOWNLOADING || state == VERIFYING || state == READY || state == INSTALLING) {
            stageLabel = text(stageText(), 15, palette.ink, Typeface.BOLD);
            stageLabel.setGravity(Gravity.CENTER);
            stageLabel.setPadding(0, dp(8), 0, dp(4));
            content.addView(stageLabel);
            if (state == DOWNLOADING || state == VERIFYING) {
                progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
                progressBar.setMax(100);
                progressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(palette.button));
                progressBar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(palette.track));
                progressBar.setIndeterminate(state == VERIFYING || totalBytes <= 0);
                progressBar.setProgress(state == DOWNLOADING && totalBytes > 0 ? (int) (100 * downloadBytes() / totalBytes) : 0);
                content.addView(progressBar, topMargin(dp(14)));
                progressLabel = text(downloadSizeText(), 12, palette.muted, Typeface.NORMAL);
                progressLabel.setGravity(Gravity.CENTER);
                content.addView(progressLabel, topMargin(dp(8)));
            }
            statusLabel = text(state == READY ? "Your download is checked and ready." :
                    state == INSTALLING ? "Android’s installer is handling the approval." :
                            "The download continues if you leave this screen.", 13, palette.muted, Typeface.NORMAL);
            statusLabel.setGravity(Gravity.CENTER);
            statusLabel.setPadding(0, dp(12), 0, 0);
            content.addView(statusLabel);
        } else if (state == FAILED) {
            TextView error = text(failureMessage, 13, palette.red, Typeface.NORMAL);
            error.setPadding(dp(4), dp(14), dp(4), 0);
            content.addView(error);
        } else if (state == INSTALLED) {
            TextView done = text("The update is installed. You’re all set.", 14, palette.muted, Typeface.NORMAL);
            done.setGravity(Gravity.CENTER);
            content.addView(done);
        }

        Button primary = button(primaryLabel().toUpperCase(java.util.Locale.ROOT) + "  ↗");
        primary.setOnClickListener(v -> primaryAction());
        root.addView(primary, new LinearLayout.LayoutParams(-1, dp(50)) {{ leftMargin = dp(20); rightMargin = dp(20); topMargin = dp(8); bottomMargin = dp(2); }});
        if (state != INSTALLED) {
            boolean showSecondary = state == DOWNLOADING
                    || ((state == AVAILABLE || state == FAILED) && maybeLaterCount < 3);
            TextView later = text(state == DOWNLOADING ? "Keep downloading in background" : "Maybe later", 13, palette.muted, Typeface.BOLD);
            later.setGravity(Gravity.CENTER);
            later.setMinHeight(dp(42));
            later.setOnClickListener(v -> {
                if (state != DOWNLOADING) {
                    maybeLaterCount++;
                    releasePrefs.edit().putString("snoozed_tag", tag).putInt("snooze_count", maybeLaterCount).apply();
                }
                finish();
            });
            if (!showSecondary) {
                later.setVisibility(View.INVISIBLE);
                later.setClickable(false);
            }
            root.addView(later, new LinearLayout.LayoutParams(-1, dp(42)) {{ bottomMargin = dp(8); }});
        }
    }

    private String failureMessage = "";

    private String heading() {
        switch (state) {
            case DOWNLOADING: return "Getting things ready…";
            case VERIFYING: return "Checking the update…";
            case READY: return "Ready to upgrade!";
            case INSTALLING: return "Handing off to Android";
            case FAILED: return "Couldn’t finish the update";
            case INSTALLED: return "Nice, you’re updated";
            default: return "A little upgrade";
        }
    }

    private String stageText() {
        if (state == DOWNLOADING) return totalBytes > 0
                ? "Downloading " + Math.max(0, progressBar == null ? 0 : progressBar.getProgress()) + "%"
                : "Downloading update…";
        if (state == VERIFYING) return "Verifying the APK";
        if (state == READY) return "Your update is ready";
        return "Install confirmation is next";
    }

    private String primaryLabel() {
        switch (state) {
            case DOWNLOADING: return "Downloading…";
            case VERIFYING: return "Checking…";
            case READY: return "Install update";
            case INSTALLING: return "Waiting for Android";
            case FAILED: return "Try again";
            case INSTALLED: return "Done";
            default: return "Update now";
        }
    }

    private void primaryAction() {
        if (state == AVAILABLE || state == FAILED) startDownload();
        else if (state == READY) installUpdate();
        else if (state == INSTALLED) finish();
    }

    private void startDownload() {
        if (downloads == null) { fail("Android’s download service isn’t available."); return; }
        try {
            String fileName = "Rollcall-" + tag.replaceAll("[^A-Za-z0-9._-]", "_") + ".apk";
            File directory = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (directory == null) { fail("App storage isn’t available. Free up space and try again."); return; }
            File destination = new File(directory, fileName);
            if (destination.exists()) destination.delete();
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(downloadUrl));
            request.setTitle("Rollcall · " + tag);
            request.setDescription("Downloading the Rollcall update");
            request.setMimeType("application/vnd.android.package-archive");
            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(false);
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, fileName);
            downloadId = downloads.enqueue(request);
            prefs.edit().putLong(CHANNEL_ID, downloadId).putString(CHANNEL_TAG, tag).apply();
            totalBytes = -1;
            state = DOWNLOADING;
            render();
            readDownload();
        } catch (Exception e) { fail("Couldn’t start the download. Check storage and try again."); }
    }

    private long downloadBytes() {
        if (downloads == null || downloadId < 0) return 0;
        Cursor cursor = null;
        try {
            cursor = downloads.query(new DownloadManager.Query().setFilterById(downloadId));
            if (cursor != null && cursor.moveToFirst()) return cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
        } catch (Exception ignored) { }
        finally { if (cursor != null) cursor.close(); }
        return 0;
    }

    private void readDownload() {
        if (isFinishing() || downloadId < 0 || downloads == null) return;
        Cursor cursor = null;
        try {
            cursor = downloads.query(new DownloadManager.Query().setFilterById(downloadId));
            if (cursor == null || !cursor.moveToFirst()) { fail("The saved download is no longer available. Please try again."); return; }
            int status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            long bytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
            totalBytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
            if (status == DownloadManager.STATUS_PENDING || status == DownloadManager.STATUS_RUNNING
                    || status == DownloadManager.STATUS_PAUSED) {
                state = DOWNLOADING;
                if (totalBytes > 0) {
                    int percent = (int) Math.min(100, 100 * bytes / totalBytes);
                    if (progressBar != null) {
                        progressBar.setIndeterminate(false);
                        progressBar.setProgress(percent);
                    }
                    if (stageLabel != null) stageLabel.setText("Downloading " + percent + "%");
                }
                if (progressLabel != null) progressLabel.setText(sizeText(bytes) + " / " + sizeText(totalBytes));
                if (statusLabel != null && status == DownloadManager.STATUS_PAUSED)
                    statusLabel.setText("Download paused. Android will resume when the connection is available.");
                handler.removeCallbacks(pollDownload);
                handler.postDelayed(pollDownload, 450);
                return;
            }
            handler.removeCallbacks(pollDownload);
            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                String localUri = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI));
                apkFile = new File(Uri.parse(localUri).getPath());
                state = VERIFYING;
                render();
                verifyApk(apkFile);
            } else {
                int reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON));
                fail("Download failed (" + reason + "). Check your connection and try again.");
            }
        } catch (Exception e) { fail("Couldn’t read the download status. Please try again."); }
        finally { if (cursor != null) cursor.close(); }
    }

    private void verifyApk(File file) {
        VERIFY.execute(() -> {
            String error = "";
            long candidateVersion = -1;
            try {
                if (file == null || !file.isFile() || file.length() == 0) throw new Exception("The downloaded file is empty.");
                if (digest != null && !digest.isEmpty()) {
                    if (!digest.matches("(?i)^sha256:[a-f0-9]{64}$")) throw new Exception("GitHub returned an unsupported APK checksum.");
                    String actual = sha256(file);
                    if (!actual.equalsIgnoreCase(digest.substring("sha256:".length()))) throw new Exception("The APK checksum didn’t match GitHub’s release.");
                }
                int flags = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
                PackageInfo archive = getPackageManager().getPackageArchiveInfo(file.getAbsolutePath(), flags);
                if (archive == null || !getPackageName().equals(archive.packageName)) throw new Exception("This APK isn’t a Rollcall update.");
                candidateVersion = versionCode(archive);
                PackageInfo installed = packageInfo(flags);
                if (candidateVersion <= versionCode(installed)) throw new Exception("This release doesn’t contain a newer app version.");
                if (!sameSigner(installed, archive)) throw new Exception("This APK was signed with a different key. Android can’t safely install it over this copy of Rollcall.");
            } catch (Exception e) { error = e.getMessage() == null ? "Couldn’t verify the downloaded APK." : e.getMessage(); }
            String resultError = error;
            long verifiedVersion = candidateVersion;
            new Handler(Looper.getMainLooper()).post(() -> {
                if (!resultError.isEmpty()) { fail(resultError); return; }
                apkVersionCode = verifiedVersion;
                state = READY;
                render();
            });
        });
    }

    private PackageInfo packageInfo(int flags) throws PackageManager.NameNotFoundException {
        return Build.VERSION.SDK_INT >= 33
                ? getPackageManager().getPackageInfo(getPackageName(), PackageManager.PackageInfoFlags.of(flags))
                : getPackageManager().getPackageInfo(getPackageName(), flags);
    }

    private long versionCode(PackageInfo info) {
        return Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
    }

    private boolean sameSigner(PackageInfo installed, PackageInfo candidate) {
        if (Build.VERSION.SDK_INT >= 28 && installed.signingInfo != null && candidate.signingInfo != null) {
            Signature[] current = installed.signingInfo.getSigningCertificateHistory();
            Signature[] incoming = candidate.signingInfo.getSigningCertificateHistory();
            if (current == null || incoming == null) return false;
            for (Signature a : current) for (Signature b : incoming) if (Arrays.equals(a.toByteArray(), b.toByteArray())) return true;
            return false;
        }
        Signature[] current = installed.signatures, incoming = candidate.signatures;
        if (current == null || incoming == null || current.length != incoming.length) return false;
        for (Signature a : current) {
            boolean matched = false;
            for (Signature b : incoming) matched |= Arrays.equals(a.toByteArray(), b.toByteArray());
            if (!matched) return false;
        }
        return true;
    }

    private String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
        }
        StringBuilder value = new StringBuilder(64);
        for (byte b : digest.digest()) value.append(String.format(java.util.Locale.ROOT, "%02x", b & 0xff));
        return value.toString();
    }

    private void installUpdate() {
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            try {
                Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(settings, REQUEST_INSTALL_SOURCE);
            } catch (Exception e) { fail("Allow Rollcall to install updates in Android Settings, then return here."); }
            return;
        }
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".updates", apkFile);
            Intent install = new Intent(Intent.ACTION_INSTALL_PACKAGE);
            install.setData(uri);
            install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            install.putExtra(Intent.EXTRA_RETURN_RESULT, true);
            state = INSTALLING;
            render();
            startActivityForResult(install, REQUEST_INSTALLER);
        } catch (Exception e) { fail("Android couldn’t open the package installer. Try again from this screen."); }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_INSTALL_SOURCE) {
            state = READY;
            render();
        } else if (requestCode == REQUEST_INSTALLER) {
            checkInstalledVersion();
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (state == DOWNLOADING) readDownload();
        if (state == INSTALLING) checkInstalledVersion();
    }

    private void checkInstalledVersion() {
        try {
            PackageInfo installed = packageInfo(0);
            if (versionCode(installed) >= apkVersionCode && apkVersionCode > 0) {
                state = INSTALLED;
                prefs.edit().remove(CHANNEL_ID).remove(CHANNEL_TAG).apply();
                if (apkFile != null) apkFile.delete();
            } else state = READY;
        } catch (Exception ignored) { state = READY; }
        render();
    }

    private void fail(String message) {
        handler.removeCallbacks(pollDownload);
        state = FAILED;
        failureMessage = message;
        render();
    }

    private String downloadSizeText() {
        long bytes = downloadBytes();
        return totalBytes > 0 ? sizeText(bytes) + " / " + sizeText(totalBytes) : "Downloading update…";
    }

    private String sizeText(long bytes) {
        if (bytes < 0) return "—";
        if (bytes < 1024 * 1024) return String.format(java.util.Locale.ROOT, "%.0f KB", bytes / 1024f);
        return String.format(java.util.Locale.ROOT, "%.1f MB", bytes / (1024f * 1024f));
    }

    private String cleanNotes(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "Small fixes and improvements to make Rollcall feel better.";
        String clean = raw.replaceAll("(?m)^#{1,6}\\s*", "").replace("**", "").replace("`", "").trim();
        return clean.length() > 2400 ? clean.substring(0, 2400) + "…" : clean;
    }

    private LinearLayout card() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setPadding(dp(16), dp(16), dp(16), dp(16));
        GradientDrawable background = new GradientDrawable();
        background.setColor(palette.surface);
        background.setCornerRadius(dp(16));
        background.setStroke(dp(1), palette.border);
        view.setBackground(background);
        return view;
    }

    private TextView text(String value, int size, int color, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(brandTypeface, style);
        return view;
    }

    private GradientDrawable makeCardBackground(int color, int stroke) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(16));
        if (stroke != 0) shape.setStroke(dp(1), stroke);
        return shape;
    }

    private Button button(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextColor(palette.buttonText);
        button.setAllCaps(false);
        button.setTypeface(brandTypeface, Typeface.BOLD);
        GradientDrawable background = new GradientDrawable();
        background.setColor(palette.button);
        background.setCornerRadius(dp(10));
        button.setBackground(background);
        button.setEnabled(state != DOWNLOADING && state != VERIFYING && state != INSTALLING);
        return button;
    }

    private LinearLayout.LayoutParams centered(int width, int height) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        params.gravity = Gravity.CENTER_HORIZONTAL;
        return params;
    }

    private LinearLayout.LayoutParams topMargin(int margin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = margin;
        return params;
    }

    private int dp(float value) { return (int) (value * getResources().getDisplayMetrics().density + .5f); }

    @Override protected void onDestroy() {
        handler.removeCallbacks(pollDownload);
        super.onDestroy();
    }
}
