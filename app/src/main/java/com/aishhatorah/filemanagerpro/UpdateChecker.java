package com.aishhatorah.filemanagerpro;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UpdateChecker {

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Pattern SEMVER_PATTERN = Pattern.compile("(\\d+\\.\\d+\\.\\d+)");

    public static void checkForUpdates(Activity activity, boolean manualTrigger) {
        final String repoSlug = PrefsManager.getGithubRepo(activity);
        if (manualTrigger) {
            Toast.makeText(activity, "בודק עידכונים זמינים...", Toast.LENGTH_SHORT).show();
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
                        MAIN.post(() -> ThemedDialogHelper.showCustomDialog(
                                activity,
                                "האפליקציה מעודכנת",
                                "ברשותך הגירסה העדכנית ביותר (" + BuildConfig.VERSION_NAME + ").",
                                null,
                                "סגור",
                                null,
                                null,
                                null,
                                false
                        ));
                    }
                    return;
                }

                StringBuilder sb = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line).append("\n");
                }

                JSONObject release = new JSONObject(sb.toString());
                String tagName = release.optString("tag_name", "");
                String releaseTitle = release.optString("name", tagName);
                String body = release.optString("body", "");
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

                String remoteVersion = extractRemoteSemver(tagName, releaseTitle, body);
                boolean isNewer = remoteVersion != null && compareSemver(remoteVersion, BuildConfig.VERSION_NAME) > 0;
                final String finalApkUrl = apkUrl;
                final String finalRemoteVer = remoteVersion != null ? remoteVersion : BuildConfig.VERSION_NAME;
                final String cleanNotes = formatCleanUpdateNotes(body);

                MAIN.post(() -> {
                    if (activity.isFinishing()) return;
                    // סעיף ה: ההודעה תקפוץ בכניסה לאפליקציה אך ורק אם באמת קיים עדכון חדש יותר!
                    if (isNewer) {
                        showUpdateAvailableDialog(activity, finalRemoteVer, cleanNotes, finalApkUrl, htmlUrl);
                    } else if (manualTrigger) {
                        ThemedDialogHelper.showCustomDialog(
                                activity,
                                "האפליקציה מעודכנת",
                                "ברשותך הגירסה החדשה ביותר (גירסה " + BuildConfig.VERSION_NAME + ").",
                                null,
                                "סגור",
                                null,
                                null,
                                null,
                                false
                        );
                    }
                });
            } catch (Exception e) {
                if (manualTrigger) {
                    MAIN.post(() -> Toast.makeText(activity,
                            "שגיאה בבדיקת עדכונים: " + e.getMessage(),
                            Toast.LENGTH_LONG).show());
                }
            }
        });
    }

    private static String extractRemoteSemver(String tag, String title, String body) {
        String[] sources = {title, tag, body};
        for (String src : sources) {
            if (src == null) continue;
            Matcher m = SEMVER_PATTERN.matcher(src);
            if (m.find()) {
                return m.group(1);
            }
        }
        return null;
    }

    private static String formatCleanUpdateNotes(String rawMarkdown) {
        if (rawMarkdown == null || rawMarkdown.trim().isEmpty()) {
            return "• שיפורי ביצועים, יציבות ותמיכה מלאה בכל סוגי הקבצים.";
        }
        StringBuilder cleaned = new StringBuilder();
        String[] lines = rawMarkdown.split("\n");
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("## ") || line.startsWith("# ")) continue;
            if (line.contains("SHA-256") || line.contains("File-Manager-Pro.apk") || line.contains("Source.zip")) continue;
            if (line.contains("הורדה ישירה") || line.contains("פרטים טכניים") || line.contains("מפתח:")) continue;
            line = line.replace("**", "").replace("`", "");
            if (line.startsWith("- ")) {
                line = "• " + line.substring(2);
            }
            cleaned.append(line).append("\n");
        }
        String out = cleaned.toString().trim();
        return out.isEmpty()
                ? "• פתיחת מדיה, מסמכים וחילוץ ארכיונים בתוך האפליקציה\n• דחיסת ZIP, תעדוף תיקיות בעברית ושיתוף מחותך האודיו והוידאו"
                : out;
    }

    public static int compareSemver(String v1, String v2) {
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

    // סעיף ו: כותרת נקייה עם מספר הגירסה החדשה בלבד (כגון "גירסה 0.2.3 זמינה") + מה התחדש + הורדה והתקנה אוטומטית
    private static void showUpdateAvailableDialog(Activity activity, String remoteVersion, String cleanNotes, String apkUrl, String htmlUrl) {
        String title = "גירסה " + remoteVersion + " זמינה";
        String message = "מה התחדש בעדכון זה:\n\n" + cleanNotes;

        ThemedDialogHelper.showCustomDialog(
                activity,
                title,
                message,
                null,
                "הורדה והתקנה אוטומטית",
                () -> {
                    if (apkUrl != null && !apkUrl.isEmpty()) {
                        downloadAndAutoUpdateApk(activity, apkUrl, remoteVersion);
                    } else {
                        activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(htmlUrl)));
                    }
                },
                "סגור",
                null,
                false
        );
    }

    private static void downloadAndAutoUpdateApk(Activity activity, String apkUrl, String remoteVersion) {
        int theme = PrefsManager.getThemeMode(activity);
        int textPrimary = ThemeHelper.getPrimaryTextColor(theme);
        int textSecondary = ThemeHelper.getSecondaryTextColor(theme);

        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        box.setPadding(52, 46, 52, 40);
        box.setBackground(ThemedDialogHelper.createDialogBackground(theme));

        TextView tvTitle = new TextView(activity);
        tvTitle.setText("מעדכן לגירסה " + remoteVersion);
        tvTitle.setTextColor(textPrimary);
        tvTitle.setTextSize(16.5f);
        tvTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        tvTitle.setPadding(0, 0, 0, 14);
        box.addView(tvTitle);

        TextView tvStatus = new TextView(activity);
        tvStatus.setText("מוריד את העדכון הישיר...");
        tvStatus.setTextColor(textSecondary);
        tvStatus.setTextSize(13.5f);
        tvStatus.setPadding(0, 0, 0, 18);
        box.addView(tvStatus);

        ProgressBar bar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgress(0);
        box.addView(bar);

        AlertDialog progressDialog = new AlertDialog.Builder(activity)
                .setView(box)
                .setCancelable(false)
                .create();
        progressDialog.show();
        Window window = progressDialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        EXECUTOR.execute(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(apkUrl).openConnection();
                conn.setInstanceFollowRedirects(true);
                conn.connect();

                int fileLength = conn.getContentLength();
                File outDir = activity.getExternalCacheDir() != null ? activity.getExternalCacheDir() : activity.getCacheDir();
                File apkFile = new File(outDir, "FileManagerPro-" + remoteVersion + ".apk");

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
                                tvStatus.setText("הורדה: " + pct + "%  (" + StorageUtils.formatSize(finalTotal) + ")");
                            });
                        }
                    }
                }

                MAIN.post(() -> {
                    progressDialog.dismiss();
                    installApkAutomatically(activity, apkFile);
                });
            } catch (Exception e) {
                MAIN.post(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(activity, "ההורדה נכשלה: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private static void installApkAutomatically(Activity activity, File apkFile) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!activity.getPackageManager().canRequestPackageInstalls()) {
                Toast.makeText(activity, "נא לאשר התקנת עדכונים עבור File Manager Pro", Toast.LENGTH_LONG).show();
                Intent settingsIntent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + activity.getPackageName()));
                activity.startActivity(settingsIntent);
                return;
            }
        }

        PrefsManager.setPendingUpdateSuccessBanner(activity, true);

        // ניסיון התקנה אוטומטית באמצעות PackageInstaller Session API
        try {
            PackageInstaller packageInstaller = activity.getPackageManager().getPackageInstaller();
            PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                    PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            params.setAppPackageName(activity.getPackageName());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
            }

            int sessionId = packageInstaller.createSession(params);
            try (PackageInstaller.Session session = packageInstaller.openSession(sessionId)) {
                try (OutputStream out = session.openWrite("fmp_update_apk", 0, apkFile.length());
                     InputStream in = new FileInputStream(apkFile)) {
                    byte[] buffer = new byte[16 * 1024];
                    int c;
                    while ((c = in.read(buffer)) != -1) {
                        out.write(buffer, 0, c);
                    }
                    session.fsync(out);
                }

                Intent callbackIntent = new Intent(activity, UpdateInstallReceiver.class);
                int flags = PendingIntent.FLAG_UPDATE_CURRENT;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    flags |= PendingIntent.FLAG_MUTABLE;
                }
                PendingIntent pendingIntent = PendingIntent.getBroadcast(activity, sessionId, callbackIntent, flags);
                session.commit(pendingIntent.getIntentSender());
                return;
            }
        } catch (Exception fallbackEx) {
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
}
