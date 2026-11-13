package com.aishhatorah.filemanagerpro;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * סעיף 1 + 35: בודק ומתקין עדכונים ישירות מ-GitHub תוך תמיכה מלאה בסינון אינטרנט (נטפרי)
 * באמצעות GitHubTrustHelper המוגבל אך ורק לדומיינים הרשמיים של GitHub, ועיצוב נקי ומתקפל.
 */
public class UpdateChecker {

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Pattern SEMVER_PATTERN = Pattern.compile("(\\d+\\.\\d+\\.\\d+)");

    public static void checkForUpdates(Activity activity, boolean manualTrigger) {
        final String repoSlug = PrefsManager.getGithubRepo(activity);
        if (manualTrigger) {
            Toast.makeText(activity, "בודק עדכונים זמינים...", Toast.LENGTH_SHORT).show();
        }

        EXECUTOR.execute(() -> {
            try {
                String apiUrl = "https://api.github.com/repos/" + repoSlug + "/releases/latest";
                HttpURLConnection conn = GitHubTrustHelper.openRedirectAwareConnection(apiUrl, "application/vnd.github+json");

                int status = conn.getResponseCode();
                if (status != 200) {
                    AppLogger.warn(activity, "Updater", "GitHub API returned status " + status);
                    if (manualTrigger) {
                        MAIN.post(() -> ThemedDialogHelper.showCustomDialog(
                                activity,
                                "האפליקציה מעודכנת",
                                "ברשותך הגירסה העדכנית ביותר (" + formatOfficialVer(BuildConfig.VERSION_NAME) + ").",
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
                        String assetName = asset.optString("name", "");
                        if ("File-Manager-Pro.apk".equalsIgnoreCase(assetName)) {
                            apkUrl = asset.optString("browser_download_url", null);
                            break;
                        }
                    }
                    if (apkUrl == null) {
                        for (int i = 0; i < assets.length(); i++) {
                            JSONObject asset = assets.getJSONObject(i);
                            String assetName = asset.optString("name", "").toLowerCase();
                            if (assetName.endsWith(".apk")) {
                                apkUrl = asset.optString("browser_download_url", null);
                                break;
                            }
                        }
                    }
                }

                String remoteVersion = extractRemoteSemver(tagName, releaseTitle, body);
                boolean isNewer = remoteVersion != null && compareSemver(remoteVersion, BuildConfig.VERSION_NAME) > 0;
                final String finalApkUrl = apkUrl;
                final String finalRemoteVer = remoteVersion != null ? remoteVersion : BuildConfig.VERSION_NAME;
                final List<String> noteLines = extractUpdateBulletLines(body);

                MAIN.post(() -> {
                    if (activity.isFinishing()) return;
                    if (isNewer) {
                        showUpdateAvailableDialog(activity, finalRemoteVer, noteLines, finalApkUrl, htmlUrl);
                    } else if (manualTrigger) {
                        ThemedDialogHelper.showCustomDialog(
                                activity,
                                "האפליקציה מעודכנת",
                                "ברשותך הגירסה החדשה ביותר (גירסה " + formatOfficialVer(BuildConfig.VERSION_NAME) + ").",
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
                AppLogger.warn(activity, "Updater", "Update check error: " + e.getMessage());
                if (manualTrigger) {
                    MAIN.post(() -> Toast.makeText(activity,
                            "לא ניתן להתחבר לשרת העדכונים כעת, בדוק חיבור אינטרנט",
                            Toast.LENGTH_LONG).show());
                }
            }
        });
    }

    public static String formatOfficialVer(String ver) {
        if (ver == null) return "";
        return compareSemver(ver, "0.2.5") >= 0 ? (ver + " רשמית") : ver;
    }

    private static String extractRemoteSemver(String tag, String title, String body) {
        String[] sources = {title, tag, body};
        for (String src : sources) {
            if (src == null) continue;
            Matcher m = SEMVER_PATTERN.matcher(src);
            if (m.find()) return m.group(1);
        }
        return null;
    }

    private static List<String> extractUpdateBulletLines(String rawMarkdown) {
        List<String> items = new ArrayList<>();
        if (rawMarkdown != null && !rawMarkdown.trim().isEmpty()) {
            String[] lines = rawMarkdown.split("\n");
            for (String raw : lines) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                if (line.contains("SHA-256") || line.contains("File-Manager-Pro") || line.contains("Source.zip")) continue;
                if (line.contains("הורדה ישירה") || line.contains("פרטים טכניים") || line.contains("פיתוח והנדסה") || line.contains("מפתח:")) continue;
                line = line.replace("**", "").replace("`", "").trim();
                if (line.startsWith("• ")) line = line.substring(2).trim();
                else if (line.startsWith("- ")) line = line.substring(2).trim();
                else if (line.startsWith("* ")) line = line.substring(2).trim();
                if (!line.isEmpty()) items.add(line);
            }
        }
        if (items.isEmpty()) {
            items.add("שדרוגים מקצועיים בביצועים, ממשק נקי, תמיכה בארכיונים ועריכת קוד, נגני מדיה מלאים ועוד.");
        }
        return items;
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

    private static void showUpdateAvailableDialog(Activity activity, String remoteVersion, List<String> bulletLines, String apkUrl, String htmlUrl) {
        int theme = PrefsManager.getThemeMode(activity);
        int textPrimary = ThemeHelper.getPrimaryTextColor(activity, theme);
        int accent = ThemeHelper.getAccentColor(activity, theme);

        ScrollView scroll = new ScrollView(activity);
        LinearLayout outer = new LinearLayout(activity);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        outer.setPadding(42, 40, 42, 34);
        outer.setBackground(ThemedDialogHelper.createDialogBackground(theme));

        LinearLayout heroBanner = new LinearLayout(activity);
        heroBanner.setOrientation(LinearLayout.VERTICAL);
        heroBanner.setGravity(Gravity.CENTER);
        heroBanner.setPadding(24, 24, 24, 24);
        GradientDrawable heroShape = new GradientDrawable();
        heroShape.setCornerRadius(24f);
        heroShape.setColor(accent);
        heroBanner.setBackground(heroShape);

        ImageView heroIcon = new ImageView(activity);
        heroIcon.setImageResource(R.drawable.ic_app_logo);
        LinearLayout.LayoutParams icLp = new LinearLayout.LayoutParams(88, 88);
        icLp.bottomMargin = 10;
        heroBanner.addView(heroIcon, icLp);

        TextView tvHeroTitle = new TextView(activity);
        tvHeroTitle.setText("גירסה " + formatOfficialVer(remoteVersion) + " זמינה");
        tvHeroTitle.setTextColor(Color.WHITE);
        tvHeroTitle.setTextSize(16.5f);
        tvHeroTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvHeroTitle.setGravity(Gravity.CENTER);
        heroBanner.addView(tvHeroTitle);

        LinearLayout.LayoutParams heroLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        heroLp.bottomMargin = 16;
        outer.addView(heroBanner, heroLp);

        // כרטיס מתקפל עם פירוט מה חדש
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(24, 20, 24, 20);
        GradientDrawable cardShape = new GradientDrawable();
        cardShape.setCornerRadius(20f);
        cardShape.setColor(ThemeHelper.getCardColor(activity, theme));
        cardShape.setStroke(2, ThemeHelper.getDividerColor(activity, theme));
        card.setBackground(cardShape);

        LinearLayout headerRow = new LinearLayout(activity);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView tvCardHeader = new TextView(activity);
        tvCardHeader.setText("מה חדש בגירסה " + formatOfficialVer(remoteVersion));
        tvCardHeader.setTextColor(accent);
        tvCardHeader.setTextSize(14f);
        tvCardHeader.setTypeface(Typeface.DEFAULT_BOLD);
        headerRow.addView(tvCardHeader, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvToggle = new TextView(activity);
        tvToggle.setText("הסתר ▾");
        tvToggle.setTextColor(ThemeHelper.getSecondaryTextColor(activity, theme));
        tvToggle.setTextSize(12.5f);
        headerRow.addView(tvToggle);
        card.addView(headerRow);

        SpannableStringBuilder richNotes = new SpannableStringBuilder();
        for (int i = 0; i < bulletLines.size(); i++) {
            if (i > 0) richNotes.append("\n\n");
            String line = bulletLines.get(i);
            int s = richNotes.length();
            richNotes.append("• ");
            richNotes.setSpan(new StyleSpan(Typeface.BOLD), s, richNotes.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            richNotes.setSpan(new ForegroundColorSpan(accent), s, richNotes.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            int b = richNotes.length();
            richNotes.append(line);
            richNotes.setSpan(new ForegroundColorSpan(textPrimary), b, richNotes.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        TextView tvBody = new TextView(activity);
        tvBody.setText(richNotes);
        tvBody.setTextSize(13f);
        tvBody.setPadding(0, 12, 0, 0);
        tvBody.setLineSpacing(4f, 1.15f);
        card.addView(tvBody);

        headerRow.setOnClickListener(v -> {
            boolean showing = tvBody.getVisibility() == View.VISIBLE;
            tvBody.setVisibility(showing ? View.GONE : View.VISIBLE);
            tvToggle.setText(showing ? "הרחב ◂" : "הסתר ▾");
        });

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.bottomMargin = 18;
        outer.addView(card, cardLp);

        AlertDialog dialog = new AlertDialog.Builder(activity).setView(scroll).create();

        Button btnDirectInstall = ThemedDialogHelper.createThemedButton(activity, "הורדה והתקנה ישירה", theme, true, false);
        LinearLayout.LayoutParams installLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        installLp.bottomMargin = 10;
        btnDirectInstall.setLayoutParams(installLp);
        btnDirectInstall.setOnClickListener(v -> {
            dialog.dismiss();
            if (apkUrl != null && !apkUrl.isEmpty()) {
                downloadAndAutoUpdateApk(activity, apkUrl, remoteVersion);
            } else {
                activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(htmlUrl)));
            }
        });
        outer.addView(btnDirectInstall);

        Button btnClose = ThemedDialogHelper.createThemedButton(activity, "סגור", theme, false, false);
        btnClose.setOnClickListener(v -> dialog.dismiss());
        outer.addView(btnClose);

        scroll.addView(outer);
        dialog.show();
        ThemedDialogHelper.applyResponsiveDialogWindow(activity, dialog);
    }

    private static void downloadAndAutoUpdateApk(Activity activity, String apkUrl, String remoteVersion) {
        int theme = PrefsManager.getThemeMode(activity);
        int textPrimary = ThemeHelper.getPrimaryTextColor(activity, theme);
        int textSecondary = ThemeHelper.getSecondaryTextColor(activity, theme);

        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        box.setPadding(48, 42, 48, 36);
        box.setBackground(ThemedDialogHelper.createDialogBackground(theme));

        TextView tvTitle = new TextView(activity);
        tvTitle.setText("מעדכן לגירסה " + formatOfficialVer(remoteVersion));
        tvTitle.setTextColor(textPrimary);
        tvTitle.setTextSize(16f);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setPadding(0, 0, 0, 12);
        box.addView(tvTitle);

        TextView tvStatus = new TextView(activity);
        tvStatus.setText("מוריד עדכון ישיר באופן מאובטח...");
        tvStatus.setTextColor(textSecondary);
        tvStatus.setTextSize(13.5f);
        tvStatus.setPadding(0, 0, 0, 16);
        box.addView(tvStatus);

        ProgressBar bar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        bar.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        bar.setMax(100);
        box.addView(bar);

        AlertDialog progressDialog = new AlertDialog.Builder(activity)
                .setView(box)
                .setCancelable(false)
                .create();
        progressDialog.show();
        Window window = progressDialog.getWindow();
        if (window != null) window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        EXECUTOR.execute(() -> {
            try {
                HttpURLConnection conn = GitHubTrustHelper.openRedirectAwareConnection(apkUrl, "application/octet-stream");
                int respCode = conn.getResponseCode();
                if (respCode != 200) throw new IllegalStateException("HTTP " + respCode);

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

                if (!apkFile.exists() || apkFile.length() < 100_000L) {
                    throw new IllegalStateException("קובץ העדכון שירד אינו שלם");
                }

                MAIN.post(() -> {
                    if (!activity.isFinishing()) {
                        progressDialog.dismiss();
                        installApkAutomatically(activity, apkFile);
                    }
                });
            } catch (Exception e) {
                AppLogger.error(activity, "Updater", "Download failed", e);
                MAIN.post(() -> {
                    if (!activity.isFinishing()) progressDialog.dismiss();
                    Toast.makeText(activity, "ההורדה נכשלה — בדוק חיבור אינטרנט ונסה שוב", Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private static void installApkAutomatically(Activity activity, File apkFile) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!activity.getPackageManager().canRequestPackageInstalls()) {
                Toast.makeText(activity, "נא לאשר התקנת עדכונים ישירה עבור File Manager Pro", Toast.LENGTH_LONG).show();
                Intent settingsIntent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + activity.getPackageName()));
                activity.startActivity(settingsIntent);
                return;
            }
        }

        PackageInstaller packageInstaller = activity.getPackageManager().getPackageInstaller();
        int sessionId = -1;
        try {
            PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                    PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            params.setAppPackageName(activity.getPackageName());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                try { params.setRequestUpdateOwnership(true); } catch (Throwable ignored) {}
            }

            sessionId = packageInstaller.createSession(params);
            try (PackageInstaller.Session session = packageInstaller.openSession(sessionId)) {
                try (OutputStream out = session.openWrite("base.apk", 0, apkFile.length());
                     InputStream in = new FileInputStream(apkFile)) {
                    byte[] buffer = new byte[16 * 1024];
                    int c;
                    while ((c = in.read(buffer)) != -1) out.write(buffer, 0, c);
                    session.fsync(out);
                }

                Intent callbackIntent = new Intent(activity, UpdateInstallReceiver.class);
                callbackIntent.setAction(UpdateInstallReceiver.ACTION_SELF_UPDATE_COMMIT);
                callbackIntent.setPackage(activity.getPackageName());
                callbackIntent.putExtra(UpdateInstallReceiver.EXTRA_APK_FILE_PATH, apkFile.getAbsolutePath());

                int flags = PendingIntent.FLAG_UPDATE_CURRENT;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) flags |= PendingIntent.FLAG_MUTABLE;

                PendingIntent pendingIntent = PendingIntent.getBroadcast(
                        activity, sessionId, callbackIntent, flags);
                session.commit(pendingIntent.getIntentSender());
                return;
            }
        } catch (Exception fallbackEx) {
            if (sessionId != -1) {
                try { packageInstaller.abandonSession(sessionId); } catch (Exception ignored) {}
            }
            Uri apkUri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".fileprovider", apkFile);
            Intent installIntent = new Intent(Intent.ACTION_VIEW);
            installIntent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(installIntent);
        }
    }
}
