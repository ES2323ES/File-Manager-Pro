package com.aishhatorah.filemanagerpro;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UpdateChecker {

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public static void checkForUpdates(Activity activity, boolean manualTrigger) {
        final String repoSlug = PrefsManager.getGithubRepo(activity);
        if (manualTrigger) {
            Toast.makeText(activity, "בודק עדכונים במאגר GitHub...", Toast.LENGTH_SHORT).show();
        }

        EXECUTOR.execute(() -> {
            try {
                String apiUrl = "https://api.github.com/repos/" + repoSlug + "/releases/latest";
                HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/vnd.github+json");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                int status = conn.getResponseCode();
                if (status != 200) {
                    if (manualTrigger) {
                        MAIN.post(() -> Toast.makeText(activity,
                                "לא נמצא עדכון חדש כרגע (גירסה נוכחית: " + BuildConfig.VERSION_NAME + ")",
                                Toast.LENGTH_LONG).show());
                    }
                    return;
                }

                StringBuilder sb = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                }

                JSONObject release = new JSONObject(sb.toString());
                String tagName = release.optString("tag_name", "");
                String releaseTitle = release.optString("name", tagName);
                String body = release.optString("body", "זמינה גירסה מעודכנת של File Manager Pro.");
                String htmlUrl = release.optString("html_url", "https://github.com/" + repoSlug + "/releases");

                String apkUrl = null;
                JSONArray assets = release.optJSONArray("assets");
                if (assets != null) {
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject asset = assets.getJSONObject(i);
                        String assetName = asset.optString("name", "").toLowerCase();
                        if (assetName.endsWith(".apk")) {
                            apkUrl = asset.optString("browser_download_url", null);
                            break;
                        }
                    }
                }

                boolean isNewer = isRemoteNewer(tagName, releaseTitle, BuildConfig.VERSION_NAME);
                final String finalApkUrl = apkUrl;

                MAIN.post(() -> {
                    if (activity.isFinishing()) return;
                    if (isNewer) {
                        showUpdateAvailableDialog(activity, releaseTitle, body, finalApkUrl, htmlUrl);
                    } else if (manualTrigger) {
                        new AlertDialog.Builder(activity)
                                .setTitle("האפליקציה מעודכנת")
                                .setMessage("ברשותך הגירסה החדשה ביותר (" + BuildConfig.VERSION_NAME + ").\nתגית אחרונה במאגר: " + tagName)
                                .setPositiveButton("סגור", null)
                                .setNeutralButton("פתח Releases ב-GitHub", (d, w) -> {
                                    activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(htmlUrl)));
                                })
                                .show();
                    }
                });
            } catch (Exception e) {
                if (manualTrigger) {
                    MAIN.post(() -> Toast.makeText(activity,
                            "שגיאה בבדיקת עדכון מול GitHub: " + e.getMessage(),
                            Toast.LENGTH_LONG).show());
                }
            }
        });
    }

    private static boolean isRemoteNewer(String tag, String title, String currentVersion) {
        if (tag == null) return false;
        String combined = (tag + " " + title).trim();
        // Check explicit V2, V3... releases (V1 corresponds to initial 0.2.2)
        if (tag.matches("^V([2-9]|[1-9][0-9]+)$")) {
            return true;
        }
        String numericRemote = combined.replaceAll("[^0-9.]", " ").trim().split("\\s+")[0];
        if (numericRemote.contains(".")) {
            return compareSemver(numericRemote, currentVersion) > 0;
        }
        return false;
    }

    private static int compareSemver(String v1, String v2) {
        String[] p1 = v1.split("\\.");
        String[] p2 = v2.split("\\.");
        int len = Math.max(p1.length, p2.length);
        for (int i = 0; i < len; i++) {
            int n1 = i < p1.length ? parseSafe(p1[i]) : 0;
            int n2 = i < p2.length ? parseSafe(p2[i]) : 0;
            if (n1 != n2) return Integer.compare(n1, n2);
        }
        return 0;
    }

    private static int parseSafe(String s) {
        try {
            return Integer.parseInt(s.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return 0;
        }
    }

    private static void showUpdateAvailableDialog(Activity activity, String releaseTitle, String notes, String apkUrl, String htmlUrl) {
        AlertDialog.Builder b = new AlertDialog.Builder(activity)
                .setTitle("עדכון חדש זמין: " + releaseTitle)
                .setMessage("גירסה מותקנת: " + BuildConfig.VERSION_NAME + "\n\nפרטי העדכון:\n" + notes)
                .setNegativeButton("מאוחר יותר", null);

        if (apkUrl != null && !apkUrl.isEmpty()) {
            b.setPositiveButton("הורד והתקן ישירות", (d, w) -> downloadAndInstallApk(activity, apkUrl));
        } else {
            b.setPositiveButton("להורדה מ-GitHub", (d, w) ->
                    activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(htmlUrl))));
        }
        b.show();
    }

    private static void downloadAndInstallApk(Activity activity, String apkUrl) {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(56, 42, 56, 32);

        TextView tvStatus = new TextView(activity);
        tvStatus.setText("מתחיל הורדה ישירה מ-GitHub...");
        ProgressBar bar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgress(0);

        box.addView(tvStatus);
        box.addView(bar);

        AlertDialog progressDialog = new AlertDialog.Builder(activity)
                .setTitle("מוריד עדכון File Manager Pro")
                .setView(box)
                .setCancelable(false)
                .create();
        progressDialog.show();

        EXECUTOR.execute(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(apkUrl).openConnection();
                conn.setInstanceFollowRedirects(true);
                conn.connect();

                int fileLength = conn.getContentLength();
                File outDir = activity.getExternalCacheDir() != null ? activity.getExternalCacheDir() : activity.getCacheDir();
                File apkFile = new File(outDir, "FileManagerPro-update.apk");

                try (InputStream input = conn.getInputStream();
                     FileOutputStream output = new FileOutputStream(apkFile)) {
                    byte[] data = new byte[8192];
                    long total = 0;
                    int count;
                    while ((count = input.read(data)) != -1) {
                        total += count;
                        output.write(data, 0, count);
                        if (fileLength > 0) {
                            int pct = (int) ((total * 100L) / fileLength);
                            long finalTotal = total;
                            MAIN.post(() -> {
                                bar.setProgress(pct);
                                tvStatus.setText("יורד כעת: " + pct + "% (" + StorageUtils.formatSize(finalTotal) + ")");
                            });
                        }
                    }
                }

                MAIN.post(() -> {
                    progressDialog.dismiss();
                    promptInstallApk(activity, apkFile);
                });
            } catch (Exception e) {
                MAIN.post(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(activity, "ההורדה נכשלה: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private static void promptInstallApk(Activity activity, File apkFile) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!activity.getPackageManager().canRequestPackageInstalls()) {
                Toast.makeText(activity, "נא לאשר התקנת עדכונים עבור File Manager Pro", Toast.LENGTH_LONG).show();
                Intent settingsIntent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + activity.getPackageName()));
                activity.startActivity(settingsIntent);
                return;
            }
        }

        Uri apkUri = FileProvider.getUriForFile(
                activity,
                activity.getPackageName() + ".fileprovider",
                apkFile
        );

        Intent installIntent = new Intent(Intent.ACTION_VIEW);
        installIntent.setDataAndType(apkUri, "application/vnd.android.package-archive");
        installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(installIntent);
    }
}
