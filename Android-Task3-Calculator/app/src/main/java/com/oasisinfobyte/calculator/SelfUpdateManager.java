package com.oasisinfobyte.calculator;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Checks the public GitHub Releases feed and hands signed APK updates to Android's installer. */
final class SelfUpdateManager {
    private static final String PREFS = "self_update";
    private static final String KEY_DOWNLOAD_ID = "download_id";
    private static final String KEY_PENDING_URL = "pending_url";
    private static final String KEY_PENDING_VERSION = "pending_version";
    private static final String KEY_LAST_CHECK = "last_check";
    private static final int CHECK_INTERVAL_HOURS = 6;
    private static final long POLL_INTERVAL_MS = 1200L;
    private static final Pattern VERSION_PATTERN = Pattern.compile("^v?(\\d{1,4})\\.(\\d{1,4})\\.(\\d{1,4})(?:[-+].*)?$");

    private final Activity activity;
    private final SharedPreferences preferences;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final DownloadManager downloadManager;
    private boolean updateDialogShown;
    private boolean installDialogShownThisSession;
    private boolean destroyed;

    private final Runnable pollDownload = new Runnable() {
        @Override
        public void run() {
            checkDownloadStatus();
        }
    };

    SelfUpdateManager(Activity activity) {
        this.activity = activity;
        preferences = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        downloadManager = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
    }

    void checkForUpdate() {
        if (destroyed || activity.isFinishing()) return;
        long lastCheck = preferences.getLong(KEY_LAST_CHECK, 0L);
        long interval = CHECK_INTERVAL_HOURS * 60L * 60L * 1000L;
        if (System.currentTimeMillis() - lastCheck < interval) return;

        preferences.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply();
        executor.execute(() -> {
            Release release = fetchLatestRelease();
            if (release == null || compareVersions(release.version, BuildConfig.VERSION_NAME) <= 0) return;
            mainHandler.post(() -> showUpdateAvailable(release));
        });
    }

    void onResume() {
        if (destroyed) return;
        String pendingUrl = preferences.getString(KEY_PENDING_URL, "");
        if (!pendingUrl.isEmpty()) {
            if (canInstallPackages()) {
                String pendingVersion = preferences.getString(KEY_PENDING_VERSION, "");
                enqueueDownload(pendingUrl, pendingVersion);
            }
            return;
        }
        if (preferences.getLong(KEY_DOWNLOAD_ID, -1L) > 0L) mainHandler.post(pollDownload);
    }

    void onPause() {
        mainHandler.removeCallbacks(pollDownload);
    }

    void onDestroy() {
        destroyed = true;
        mainHandler.removeCallbacksAndMessages(null);
        executor.shutdownNow();
    }

    private Release fetchLatestRelease() {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(ReleaseConfig.RELEASES_API).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
            connection.setRequestProperty("User-Agent", "Oasis-Calculator-Android-Updater");
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) return null;

            StringBuilder body = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) body.append(line);
            }

            JSONObject json = new JSONObject(body.toString());
            String tag = json.optString("tag_name", "");
            String version = tag.startsWith("v") ? tag.substring(1) : tag;
            if (!isValidVersion(version)) return null;

            JSONArray assets = json.optJSONArray("assets");
            if (assets == null) return null;
            for (int index = 0; index < assets.length(); index++) {
                JSONObject asset = assets.optJSONObject(index);
                if (asset == null || !ReleaseConfig.APK_ASSET_NAME.equals(asset.optString("name"))) continue;
                String downloadUrl = asset.optString("browser_download_url", "");
                if (!downloadUrl.startsWith(ReleaseConfig.RELEASE_DOWNLOAD_PREFIX)) return null;
                return new Release(version, downloadUrl);
            }
        } catch (Exception ignored) {
            // Update checks are best-effort and must never interrupt calculator use.
        } finally {
            if (connection != null) connection.disconnect();
        }
        return null;
    }

    private void showUpdateAvailable(Release release) {
        if (destroyed || activity.isFinishing() || updateDialogShown) return;
        updateDialogShown = true;
        new AlertDialog.Builder(activity)
                .setTitle("Update available")
                .setMessage("Oasis Calculator " + release.version + " is ready. Download the signed update now?")
                .setNegativeButton("Later", (dialog, which) -> dialog.dismiss())
                .setPositiveButton("Download", (dialog, which) -> requestUpdateDownload(release))
                .setOnCancelListener(dialog -> updateDialogShown = false)
                .show();
    }

    private void requestUpdateDownload(Release release) {
        preferences.edit()
                .putString(KEY_PENDING_URL, release.downloadUrl)
                .putString(KEY_PENDING_VERSION, release.version)
                .apply();
        if (canInstallPackages()) {
            enqueueDownload(release.downloadUrl, release.version);
            return;
        }
        new AlertDialog.Builder(activity)
                .setTitle("Allow app updates")
                .setMessage("Android requires your approval to install updates downloaded outside an app store. Allow Oasis Calculator to install this signed update in Settings.")
                .setNegativeButton("Not now", (dialog, which) -> clearPendingDownload())
                .setPositiveButton("Open Settings", (dialog, which) -> openInstallPermissionSettings())
                .show();
    }

    private boolean canInstallPackages() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                || activity.getPackageManager().canRequestPackageInstalls();
    }

    private void openInstallPermissionSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        try {
            Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(intent);
        } catch (Exception exception) {
            Toast.makeText(activity, "Open app settings and allow installing unknown apps.", Toast.LENGTH_LONG).show();
        }
    }

    private void enqueueDownload(String downloadUrl, String version) {
        if (downloadManager == null || destroyed) return;
        try {
            Uri uri = Uri.parse(downloadUrl);
            if (!"https".equals(uri.getScheme()) || !"github.com".equals(uri.getHost())
                    || !downloadUrl.startsWith(ReleaseConfig.RELEASE_DOWNLOAD_PREFIX)) {
                clearPendingDownload();
                return;
            }

            String safeVersion = version.replaceAll("[^A-Za-z0-9._-]", "_");
            String fileName = "oasis-calculator-" + safeVersion + ".apk";
            DownloadManager.Request request = new DownloadManager.Request(uri)
                    .setTitle("Oasis Calculator update")
                    .setDescription("Downloading version " + version)
                    .setMimeType("application/vnd.android.package-archive")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalFilesDir(activity, Environment.DIRECTORY_DOWNLOADS, fileName);
            long id = downloadManager.enqueue(request);
            preferences.edit()
                    .putLong(KEY_DOWNLOAD_ID, id)
                    .putString(KEY_PENDING_VERSION, version)
                    .remove(KEY_PENDING_URL)
                    .apply();
            Toast.makeText(activity, "Downloading Oasis Calculator " + version, Toast.LENGTH_LONG).show();
            mainHandler.removeCallbacks(pollDownload);
            mainHandler.post(pollDownload);
        } catch (Exception exception) {
            Toast.makeText(activity, "Could not start the update download.", Toast.LENGTH_LONG).show();
        }
    }

    private void checkDownloadStatus() {
        if (destroyed || activity.isFinishing() || downloadManager == null) return;
        long id = preferences.getLong(KEY_DOWNLOAD_ID, -1L);
        if (id <= 0L) return;

        DownloadManager.Query query = new DownloadManager.Query().setFilterById(id);
        try (Cursor cursor = downloadManager.query(query)) {
            if (cursor == null || !cursor.moveToFirst()) {
                clearDownloadId();
                return;
            }
            int statusColumn = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
            if (statusColumn < 0) return;
            int status = cursor.getInt(statusColumn);
            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                Uri apkUri = downloadManager.getUriForDownloadedFile(id);
                if (apkUri != null) showInstallUpdate(apkUri);
                return;
            }
            if (status == DownloadManager.STATUS_FAILED) {
                clearDownloadId();
                Toast.makeText(activity, "Update download failed. Try again later.", Toast.LENGTH_LONG).show();
                return;
            }
            mainHandler.removeCallbacks(pollDownload);
            mainHandler.postDelayed(pollDownload, POLL_INTERVAL_MS);
        } catch (Exception exception) {
            mainHandler.removeCallbacks(pollDownload);
        }
    }

    private void showInstallUpdate(Uri apkUri) {
        if (installDialogShownThisSession || destroyed || activity.isFinishing()) return;
        installDialogShownThisSession = true;
        String version = preferences.getString(KEY_PENDING_VERSION, "");
        new AlertDialog.Builder(activity)
                .setTitle("Update downloaded")
                .setMessage("Version " + version + " is ready. Android will ask you to confirm the installation.")
                .setNegativeButton("Later", (dialog, which) -> dialog.dismiss())
                .setPositiveButton("Install", (dialog, which) -> launchInstaller(apkUri))
                .show();
    }

    private void launchInstaller(Uri apkUri) {
        if (!canInstallPackages()) {
            installDialogShownThisSession = false;
            openInstallPermissionSettings();
            return;
        }
        try {
            Intent installIntent = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(apkUri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(installIntent);
        } catch (Exception exception) {
            Toast.makeText(activity, "Android could not open the package installer.", Toast.LENGTH_LONG).show();
            installDialogShownThisSession = false;
        }
    }

    private void clearPendingDownload() {
        preferences.edit().remove(KEY_PENDING_URL).remove(KEY_PENDING_VERSION).apply();
    }

    private void clearDownloadId() {
        preferences.edit().remove(KEY_DOWNLOAD_ID).remove(KEY_PENDING_VERSION).apply();
    }

    private static boolean isValidVersion(String value) {
        return value != null && VERSION_PATTERN.matcher(value).matches();
    }

    private static int compareVersions(String first, String second) {
        Matcher left = VERSION_PATTERN.matcher(first == null ? "" : first);
        Matcher right = VERSION_PATTERN.matcher(second == null ? "" : second);
        if (!left.matches() || !right.matches()) return 0;
        for (int part = 1; part <= 3; part++) {
            int comparison = Integer.compare(Integer.parseInt(left.group(part)), Integer.parseInt(right.group(part)));
            if (comparison != 0) return comparison;
        }
        return 0;
    }

    private static final class Release {
        final String version;
        final String downloadUrl;

        Release(String version, String downloadUrl) {
            this.version = version;
            this.downloadUrl = downloadUrl;
        }
    }
}
