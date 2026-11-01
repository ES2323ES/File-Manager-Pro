package com.aishhatorah.filemanagerpro;

import android.app.Activity;
import android.app.ActivityOptions;
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
import android.os.Bundle;
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
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
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
                HttpURLConnection conn = openRedirectAwareConnection(apiUrl, "application/vnd.github+json");

                int status = conn.getResponseCode();
                if (status != 200) {
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
                if (manualTrigger) {
                    MAIN.post(() -> Toast.makeText(activity,
                            "לא ניתן להתחבר לשרת העדכונים כעת, בדוק חיבור אינטרנט",
                            Toast.LENGTH_LONG).show());
                }
            }
        });
    }

    private static HttpURLConnection openRedirectAwareConnection(String urlStr, String acceptHeader) throws Exception {
        String currentUrl = urlStr;
        for (int hop = 0; hop < 6; hop++) {
            HttpURLConnection conn = (HttpURLConnection) new URL(currentUrl).openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "FileManagerPro-Android/" + BuildConfig.VERSION_NAME);
            if (acceptHeader != null) {
                conn.setRequestProperty("Accept", acceptHeader);
            }
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(20000);
            int code = conn.getResponseCode();
            if (code == HttpURLConnection.HTTP_MOVED_PERM
                    || code == HttpURLConnection.HTTP_MOVED_TEMP
                    || code == HttpURLConnection.HTTP_SEE_OTHER
                    || code == 307
                    || code == 308) {
                String loc = conn.getHeaderField("Location");
                conn.disconnect();
                if (loc != null && !loc.isEmpty()) {
                    currentUrl = new URL(new URL(currentUrl), loc).toString();
                    continue;
                }
            }
            return conn;
        }
        throw new IllegalStateException("Too many redirects");
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
            if (m.find()) {
                return m.group(1);
            }
        }
        return null;
    }

    private static List<String> extractUpdateBulletLines(String rawMarkdown) {
        List<String> items = new ArrayList<>();
        if (rawMarkdown != null && !rawMarkdown.trim().isEmpty()) {
            String[] lines = rawMarkdown.split("\n");
            for (String raw : lines) {
                String line = raw.trim();
                if (line.isEmpty()) continue;
                if (line.startsWith("#")) continue;
                if (line.contains("SHA-256") || line.contains("File-Manager-Pro") || line.contains("Source.zip")) continue;
                if (line.contains("הורדה ישירה") || line.contains("פרטים טכניים") || line.contains("פיתוח והנדסה") || line.contains("מפתח:")) continue;
                line = line.replace("**", "").replace("`", "").trim();
                if (line.startsWith("• ")) line = line.substring(2).trim();
                else if (line.startsWith("- ")) line = line.substring(2).trim();
                else if (line.startsWith("* ")) line = line.substring(2).trim();
                if (!line.isEmpty()) {
                    items.add(line);
                }
            }
        }
        if (items.isEmpty()) {
            items.add("תצוגת מדיה נקייה ומסך מלא: הקשה על וידאו או תמונה מציגה אותם בלבד ללא אופציות, ובסיבוב מסך הוידאו מוצג במסך מלא ללא מעיכה.");
            items.add("דפדוף בתמונות והגדלה עמוקה: מעבר קדימה ואחורה בין תמונות בתיקייה, הגדלה עד פי 25 ותצוגה צלולה וברורה מאוד.");
            items.add("תצוגת רשת מתוקנת, פס הרצה בימין והתקנת APKX / APK+: רשת בסגנון המסך הראשי, פס גלילה בימין והתראה על גירסה חדשה מותקנת.");
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

    /**
     * סעיף כג: הודעת עדכון מעוצבת בדיוק בסגנון יומן השינויים (כרטיס מהודר עם נקודות ◆ צבעוניות ומודגשות)
     * ולמטה לחצן "הורדה והתקנה ישירה" המבצע הורדה והתקנה עצמאית בתוך האפליקציה!
     */
    private static void showUpdateAvailableDialog(Activity activity, String remoteVersion, List<String> bulletLines, String apkUrl, String htmlUrl) {
        int theme = PrefsManager.getThemeMode(activity);
        int textPrimary = ThemeHelper.getPrimaryTextColor(theme);
        int accent = ThemeHelper.getAccentColor(theme);
        boolean isLight = theme == PrefsManager.THEME_LIGHT;

        int[] palette = new int[]{
                isLight ? Color.parseColor("#1D4ED8") : Color.parseColor("#60A5FA"),
                isLight ? Color.parseColor("#047857") : Color.parseColor("#34D399"),
                isLight ? Color.parseColor("#6D28D9") : Color.parseColor("#C084FC"),
                isLight ? Color.parseColor("#B45309") : Color.parseColor("#FBBF24"),
                isLight ? Color.parseColor("#BE123C") : Color.parseColor("#FB7185"),
                isLight ? Color.parseColor("#0E7490") : Color.parseColor("#22D3EE")
        };

        ScrollView scroll = new ScrollView(activity);
        LinearLayout outer = new LinearLayout(activity);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        outer.setPadding(44, 42, 44, 36);
        outer.setBackground(ThemedDialogHelper.createDialogBackground(theme));

        LinearLayout heroBanner = new LinearLayout(activity);
        heroBanner.setOrientation(LinearLayout.VERTICAL);
        heroBanner.setGravity(Gravity.CENTER);
        heroBanner.setPadding(28, 26, 28, 26);
        GradientDrawable heroShape = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.parseColor("#1E3A8A"), Color.parseColor("#4C1D95"), Color.parseColor("#0F766E")}
        );
        heroShape.setCornerRadius(28f);
        heroBanner.setBackground(heroShape);

        ImageView heroIcon = new ImageView(activity);
        heroIcon.setImageResource(R.drawable.ic_app_logo);
        LinearLayout.LayoutParams icLp = new LinearLayout.LayoutParams(96, 96);
        icLp.bottomMargin = 12;
        heroBanner.addView(heroIcon, icLp);

        TextView tvHeroTitle = new TextView(activity);
        tvHeroTitle.setText("גירסה " + formatOfficialVer(remoteVersion) + " זמינה");
        tvHeroTitle.setTextColor(Color.WHITE);
        tvHeroTitle.setTextSize(17f);
        tvHeroTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvHeroTitle.setGravity(Gravity.CENTER);
        tvHeroTitle.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        heroBanner.addView(tvHeroTitle);

        TextView tvHeroSub = new TextView(activity);
        tvHeroSub.setText("File Manager Pro");
        tvHeroSub.setTextColor(Color.parseColor("#E0E7FF"));
        tvHeroSub.setTextSize(13f);
        tvHeroSub.setTypeface(Typeface.DEFAULT_BOLD);
        tvHeroSub.setGravity(Gravity.CENTER);
        tvHeroSub.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        tvHeroSub.setPadding(0, 4, 0, 0);
        heroBanner.addView(tvHeroSub);

        LinearLayout.LayoutParams heroLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        heroLp.bottomMargin = 20;
        outer.addView(heroBanner, heroLp);

        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(30, 26, 30, 26);

        GradientDrawable cardShape = new GradientDrawable();
        cardShape.setCornerRadius(26f);
        int innerBg = isLight
                ? Color.parseColor("#F8FAFC")
                : (theme == PrefsManager.THEME_NEON ? Color.parseColor("#071029") : Color.parseColor("#0F172A"));
        cardShape.setColor(innerBg);
        cardShape.setStroke(3, Color.argb(115, Color.red(accent), Color.green(accent), Color.blue(accent)));
        card.setBackground(cardShape);

        TextView tvCardHeader = new TextView(activity);
        tvCardHeader.setText("מה התחדש בגירסה " + formatOfficialVer(remoteVersion) + ":");
        tvCardHeader.setTextColor(accent);
        tvCardHeader.setTextSize(14.5f);
        tvCardHeader.setTypeface(Typeface.DEFAULT_BOLD);
        tvCardHeader.setPadding(0, 0, 0, 14);
        card.addView(tvCardHeader);

        SpannableStringBuilder richNotes = new SpannableStringBuilder();
        for (int i = 0; i < bulletLines.size(); i++) {
            if (i > 0) richNotes.append("\n\n");
            String line = bulletLines.get(i);
            int color = palette[i % palette.length];
            int colonIdx = line.indexOf(':');
            if (colonIdx > 0 && colonIdx < 45) {
                String lead = "◆ " + line.substring(0, colonIdx + 1) + " ";
                String rest = line.substring(colonIdx + 1).trim();
                int s = richNotes.length();
                richNotes.append(lead);
                richNotes.setSpan(new StyleSpan(Typeface.BOLD), s, richNotes.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                richNotes.setSpan(new ForegroundColorSpan(color), s, richNotes.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                int b = richNotes.length();
                richNotes.append(rest);
                richNotes.setSpan(new ForegroundColorSpan(textPrimary), b, richNotes.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                int s = richNotes.length();
                richNotes.append("◆ ");
                richNotes.setSpan(new StyleSpan(Typeface.BOLD), s, richNotes.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                richNotes.setSpan(new ForegroundColorSpan(color), s, richNotes.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                int b = richNotes.length();
                richNotes.append(line);
                richNotes.setSpan(new ForegroundColorSpan(textPrimary), b, richNotes.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }

        TextView tvBody = new TextView(activity);
        tvBody.setText(richNotes);
        tvBody.setTextSize(13.2f);
        tvBody.setLineSpacing(6f, 1.18f);
        card.addView(tvBody);

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.bottomMargin = 20;
        outer.addView(card, cardLp);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(scroll)
                .create();

        Button btnDirectInstall = ThemedDialogHelper.createThemedButton(activity, "הורדה והתקנה ישירה", theme, true, false);
        btnDirectInstall.setGravity(Gravity.CENTER);
        btnDirectInstall.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        LinearLayout.LayoutParams installLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        installLp.bottomMargin = 12;
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
        btnClose.setGravity(Gravity.CENTER);
        btnClose.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        btnClose.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        btnClose.setOnClickListener(v -> dialog.dismiss());
        outer.addView(btnClose);

        scroll.addView(outer);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
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
        tvTitle.setText("מעדכן לגירסה " + formatOfficialVer(remoteVersion));
        tvTitle.setTextColor(textPrimary);
        tvTitle.setTextSize(16.5f);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setPadding(0, 0, 0, 14);
        box.addView(tvTitle);

        TextView tvStatus = new TextView(activity);
        tvStatus.setText("מוריד את העדכון הישיר לתוך האפליקציה...");
        tvStatus.setTextColor(textSecondary);
        tvStatus.setTextSize(13.5f);
        tvStatus.setPadding(0, 0, 0, 18);
        box.addView(tvStatus);

        ProgressBar bar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        bar.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
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
                HttpURLConnection conn = openRedirectAwareConnection(apkUrl, "application/octet-stream");
                int respCode = conn.getResponseCode();
                if (respCode != 200) {
                    throw new IllegalStateException("HTTP " + respCode);
                }

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
                                tvStatus.setText("הורדה ישירה: " + pct + "%  (" + StorageUtils.formatSize(finalTotal) + ")");
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
                MAIN.post(() -> {
                    if (!activity.isFinishing()) {
                        progressDialog.dismiss();
                    }
                    Toast.makeText(activity, "ההורדה נכשלה — בדוק חיבור אינטרנט ונסה שוב", Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    /**
     * סעיף כב: התקנה עצמאית וישירה של העדכון מתוך האפליקציה עצמה באמצעות PackageInstaller.Session,
     * עם תמיכה מלאה ב-Android 12..15 (כולל MODE_BACKGROUND_ACTIVITY_START_ALLOWED ב-Android 14+ וגיבוי אוטומטי).
     */
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
                try {
                    params.setRequestUpdateOwnership(true);
                } catch (Throwable ignored) {
                }
            }

            sessionId = packageInstaller.createSession(params);
            try (PackageInstaller.Session session = packageInstaller.openSession(sessionId)) {
                try (OutputStream out = session.openWrite("base.apk", 0, apkFile.length());
                     InputStream in = new FileInputStream(apkFile)) {
                    byte[] buffer = new byte[16 * 1024];
                    int c;
                    while ((c = in.read(buffer)) != -1) {
                        out.write(buffer, 0, c);
                    }
                    session.fsync(out);
                }

                Intent callbackIntent = new Intent(activity, UpdateInstallReceiver.class);
                callbackIntent.setAction(UpdateInstallReceiver.ACTION_SELF_UPDATE_COMMIT);
                callbackIntent.setPackage(activity.getPackageName());
                callbackIntent.putExtra(UpdateInstallReceiver.EXTRA_APK_FILE_PATH, apkFile.getAbsolutePath());

                int flags = PendingIntent.FLAG_UPDATE_CURRENT;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    flags |= PendingIntent.FLAG_MUTABLE;
                }

                PendingIntent pendingIntent = PendingIntent.getBroadcast(
                        activity,
                        sessionId,
                        callbackIntent,
                        flags
                );
                session.commit(pendingIntent.getIntentSender());
                return;
            }
        } catch (Exception fallbackEx) {
            if (sessionId != -1) {
                try {
                    packageInstaller.abandonSession(sessionId);
                } catch (Exception ignored) {
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
}
