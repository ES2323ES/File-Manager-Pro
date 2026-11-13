package com.aishhatorah.filemanagerpro;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * סעיפים יב, יג:
 * - יב: מתקין אוניברסלי לכל סוגי האפליקציות ללא יוצא מן הכלל:
 *   .apk, .apkx, .apk+, .apks, .apkm, .xapk, .apkp, קובצי APK בודדים ששונו לסיומת apkx / apk+,
 *   חבילות מפוצלות מרובות רכיבים (כולל תיקיות פנימיות או ארכיונים מקוננים), סינון ABI תואם למכשיר והעתקת OBB!
 * - יג: בדיקה מוקדמת מול הגירסה המותקנת במכשיר — אם קיימת במכשיר גירסה חדשה יותר מהקובץ שבאים להתקין,
 *   מוצגת הודעה ברורה ומפורטת עם פרטי האפליקציה המותקנת ופרטי האפליקציה בקובץ ההתקנה!
 */
public final class AppPackageInstaller {

    private static final ExecutorService INSTALL_EXEC = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static final String[] KNOWN_CPU_ABIS = {
            "arm64_v8a", "arm64-v8a",
            "armeabi_v7a", "armeabi-v7a",
            "armeabi",
            "x86_64",
            "x86"
    };

    private static class StagedPackagePlan {
        File stagingDir;
        List<File> apkParts = new ArrayList<>();
        File baseApkFile = null;
        boolean singleDirectApk = false;
        String packageName = "";
        String candidateAppLabel = "";
        String candidateVersionName = "לא צוין";
        long candidateVersionCode = 0L;
    }

    private static class InstalledAppDetails {
        String appLabel;
        String versionName;
        long versionCode;
    }

    private AppPackageInstaller() {
    }

    /**
     * סעיפים 15, 16: הצגת פרטי חבילת ההתקנה לפני התקנה + אפשרויות "התקן", "הצג מקור ה-APK" (בסגנון MT Manager)
     * ו"צפה בתוכן הארכיון".
     */
    public static void inspectAndInstall(Activity activity, File packageFile) {
        if (activity == null || packageFile == null || !packageFile.exists()) return;
        INSTALL_EXEC.execute(() -> {
            PackageManager pm = activity.getPackageManager();
            String label = packageFile.getName();
            String pkgName = "לא מזוהה (חבילה מפוצלת / ארכיון)";
            String verName = "לא צוין";
            long verCode = 0L;
            String installedText = "לא מותקנת במכשיר";

            PackageInfo info = getArchivePackageInfoCompat(pm, packageFile.getAbsolutePath());
            if (info != null && info.packageName != null) {
                pkgName = info.packageName;
                verName = (info.versionName != null && !info.versionName.trim().isEmpty())
                        ? info.versionName.trim() : "לא צוין";
                verCode = getVersionCodeCompat(info);
                try {
                    if (info.applicationInfo != null) {
                        info.applicationInfo.sourceDir = packageFile.getAbsolutePath();
                        info.applicationInfo.publicSourceDir = packageFile.getAbsolutePath();
                        CharSequence cs = info.applicationInfo.loadLabel(pm);
                        if (cs != null && cs.length() > 0) label = cs.toString();
                    }
                } catch (Exception ignored) {
                }
                InstalledAppDetails installed = queryInstalledAppDetails(activity, pkgName);
                if (installed != null) {
                    installedText = installed.versionName + " (קוד " + installed.versionCode + ")";
                }
            }

            final String fLabel = label;
            final String fPkg = pkgName;
            final String fVer = verName + (verCode > 0 ? " (" + verCode + ")" : "");
            final String fInstalled = installedText;

            MAIN.post(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                String msg = "שם אפליקציה: " + fLabel
                        + "\nשם חבילה: " + fPkg
                        + "\nגירסה בקובץ: " + fVer
                        + "\nגירסה מותקנת כעת: " + fInstalled
                        + "\nנפח קובץ: " + StorageUtils.formatSize(packageFile.length());

                int theme = PrefsManager.getThemeMode(activity);
                Button btnViewSource = ThemedDialogHelper.createThemedButton(
                        activity, "הצג מקור ה-APK (Manifest / DEX)", theme, false, false);
                btnViewSource.setOnClickListener(v -> {
                    Intent i = new Intent(activity, ApkSourceInspectorActivity.class);
                    i.putExtra(ApkSourceInspectorActivity.EXTRA_APK_PATH, packageFile.getAbsolutePath());
                    activity.startActivity(i);
                });

                ThemedDialogHelper.showCustomDialog(
                        activity,
                        "מנהל התקנת אפליקציה",
                        msg,
                        btnViewSource,
                        "התקן",
                        () -> installAnyPackage(activity, packageFile),
                        "סגור",
                        null,
                        false
                );
            });
        });
    }

    public static void installAnyPackage(Activity activity, File packageFile) {
        if (activity == null || packageFile == null || !packageFile.exists()) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !activity.getPackageManager().canRequestPackageInstalls()) {
            Toast.makeText(activity, "נא לאשר התקנת אפליקציות עבור File Manager Pro", Toast.LENGTH_LONG).show();
            Intent settingsIntent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(settingsIntent);
            return;
        }

        AlertDialog progressDialog = showProgressDialog(activity, "בודק ומכין חבילת התקנה (" + packageFile.getName() + ")...");
        TextView tvStatus = progressDialog.findViewById(android.R.id.message);

        INSTALL_EXEC.execute(() -> {
            StagedPackagePlan plan = null;
            try {
                plan = prepareStagedPackagePlan(activity, packageFile, progressMsg -> {
                    if (tvStatus != null) {
                        MAIN.post(() -> tvStatus.setText(progressMsg));
                    }
                });

                if (plan.apkParts.isEmpty()) {
                    throw new IllegalStateException("לא נמצאו רכיבי APK תקינים בתוך הקובץ");
                }

                // סעיף יג: בדיקה האם כבר מותקנת במכשיר גירסה חדשה יותר של אותה אפליקציה
                InstalledAppDetails installed = queryInstalledAppDetails(activity, plan.packageName);
                if (installed != null && isInstalledNewerThanCandidate(installed, plan)) {
                    final StagedPackagePlan finalPlan = plan;
                    final InstalledAppDetails finalInstalled = installed;
                    MAIN.post(() -> {
                        dismissSafely(progressDialog);
                        showNewerVersionAlreadyInstalledDialog(activity, packageFile, finalPlan, finalInstalled);
                    });
                    return;
                }

                commitPackageSession(activity, packageFile, plan, progressDialog);
            } catch (Exception ex) {
                final StagedPackagePlan failedPlan = plan;
                MAIN.post(() -> {
                    dismissSafely(progressDialog);
                    if (trySystemFallbackForSingleApk(activity, packageFile, failedPlan)) {
                        return;
                    }
                    cleanupStagingDir(failedPlan);
                    AppLogger.error(activity, "Installer", "Install failed for " + packageFile.getName(), ex);
                    Toast.makeText(activity, "לא ניתן היה להתקין את החבילה: " + packageFile.getName() + " (" + ex.getMessage() + ")", Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private interface StatusUpdater {
        void onStatus(String text);
    }

    private static AlertDialog showProgressDialog(Activity activity, String initialStatus) {
        int theme = PrefsManager.getThemeMode(activity);
        float d = activity.getResources().getDisplayMetrics().density;

        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        int pad = Math.round(20 * d);
        box.setPadding(pad, pad, pad, Math.round(16 * d));
        box.setBackground(ThemedDialogHelper.createDialogBackground(theme));

        TextView tvTitle = new TextView(activity);
        tvTitle.setText("מתקין אפליקציה...");
        tvTitle.setTextColor(ThemeHelper.getPrimaryTextColor(theme));
        tvTitle.setTextSize(16f);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setPadding(0, 0, 0, Math.round(6 * d));
        box.addView(tvTitle);

        TextView tvStatus = new TextView(activity);
        tvStatus.setId(android.R.id.message);
        tvStatus.setText(initialStatus);
        tvStatus.setTextColor(ThemeHelper.getSecondaryTextColor(theme));
        tvStatus.setTextSize(13f);
        tvStatus.setPadding(0, 0, 0, Math.round(12 * d));
        box.addView(tvStatus);

        ProgressBar progress = new ProgressBar(activity);
        box.addView(progress);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(box)
                .setCancelable(false)
                .create();
        dialog.show();
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        return dialog;
    }

    private static void dismissSafely(AlertDialog dialog) {
        if (dialog != null) {
            try {
                if (dialog.isShowing()) dialog.dismiss();
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * מזהה באופן חכם את סוג החבילה לפי תוכן הקובץ עצמו (ולא רק לפי הסיומת!):
     * 1. אם הקובץ עצמו הוא APK חוקי (אפילו אם הסיומת שלו היא .apkx או .apk+) — מתקין אותו ישירות.
     * 2. אם הקובץ הוא ארכיון חבילה (.apkx, .apk+, .apks, .apkm, .xapk וכו') המכיל קובצי APK (גם בתתי-תיקיות או ארכיון פנימי) —
     *    מחלץ לתיקיית ביניים, מזהה את base.apk ואת פרטי הגירסה, מסנן פיצולי מעבד זרים ומתקין הכל בסשן אחד!
     */
    private static StagedPackagePlan prepareStagedPackagePlan(
            Activity activity,
            File packageFile,
            StatusUpdater updater
    ) throws Exception {
        PackageManager pm = activity.getPackageManager();
        StagedPackagePlan plan = new StagedPackagePlan();

        File stageRoot = new File(activity.getCacheDir(), "fmp_pkg_stage_" + System.currentTimeMillis());
        //noinspection ResultOfMethodCallIgnored
        stageRoot.mkdirs();
        plan.stagingDir = stageRoot;

        // בדיקה ראשונה: האם הקובץ עצמו הוא APK ישיר (למשל .apk או .apkx/.apk+ שהוא קובץ APK בודד)?
        PackageInfo directInfo = getArchivePackageInfoCompat(pm, packageFile.getAbsolutePath());
        if (directInfo != null && directInfo.packageName != null && !hasInnerApkEntries(packageFile)) {
            plan.singleDirectApk = true;
            if (packageFile.getName().toLowerCase(Locale.ROOT).endsWith(".apk")) {
                plan.baseApkFile = packageFile;
                plan.apkParts.add(packageFile);
            } else {
                // אם הסיומת היא .apkx או .apk+, ניצור עותק זמני עם סיומת .apk תקינה לתאימות 100% בכל המכשירים
                File normalizedApk = new File(stageRoot, "base_direct.apk");
                try (InputStream in = new BufferedInputStream(new FileInputStream(packageFile));
                     OutputStream out = new FileOutputStream(normalizedApk)) {
                    copyStream(in, out);
                }
                plan.baseApkFile = normalizedApk;
                plan.apkParts.add(normalizedApk);
            }
            populateCandidateMetadata(pm, directInfo, plan.baseApkFile.getAbsolutePath(), packageFile.getName(), plan);
            return plan;
        }

        // אחרת: מדובר בחבילת התקנה מפוצלת / מרובת רכיבים (.apkx, .apk+, .apks, .apkm, .xapk וכו')
        updater.onStatus("פורס רכיבי חבילה מתוך " + packageFile.getName() + "...");
        List<File> extractedApks = new ArrayList<>();
        extractApksAndObbsFromZipStream(
                new BufferedInputStream(new FileInputStream(packageFile)),
                stageRoot,
                extractedApks,
                0
        );

        // אם לא נמצאו קובצי .apk פנימיים אבל יש AndroidManifest.xml בשורש — הקובץ עצמו הוא APK ישיר!
        if (extractedApks.isEmpty() && hasRootAndroidManifest(packageFile)) {
            File normalizedApk = new File(stageRoot, "base_direct.apk");
            try (InputStream in = new BufferedInputStream(new FileInputStream(packageFile));
                 OutputStream out = new FileOutputStream(normalizedApk)) {
                copyStream(in, out);
            }
            plan.singleDirectApk = true;
            plan.baseApkFile = normalizedApk;
            plan.apkParts.add(normalizedApk);
            PackageInfo fallbackInfo = getArchivePackageInfoCompat(pm, normalizedApk.getAbsolutePath());
            if (fallbackInfo != null) {
                populateCandidateMetadata(pm, fallbackInfo, normalizedApk.getAbsolutePath(), packageFile.getName(), plan);
            }
            return plan;
        }

        if (extractedApks.isEmpty()) {
            throw new IllegalStateException("החבילה אינה מכילה קובצי התקנה נתמכים");
        }

        // זיהוי ה-base.apk והפרטים של האפליקציה + סינון פיצולי ABI שאינם מתאימים למעבד המכשיר
        Set<String> deviceAbis = getSupportedNormalizedAbis();
        boolean hasMatchingDeviceAbiSplit = false;
        for (File f : extractedApks) {
            String detectedAbi = detectExplicitCpuAbiInSplitName(f.getName());
            if (detectedAbi != null && deviceAbis.contains(detectedAbi)) {
                hasMatchingDeviceAbiSplit = true;
                break;
            }
        }

        File bestBaseCandidate = null;
        PackageInfo bestBasePkgInfo = null;

        for (File apkPart : extractedApks) {
            String detectedAbi = detectExplicitCpuAbiInSplitName(apkPart.getName());
            if (detectedAbi != null && hasMatchingDeviceAbiSplit && !deviceAbis.contains(detectedAbi)) {
                // דילוג על פיצול של ארכיטקטורת מעבד זרה (למשל x86_64 במכשיר arm64) כדי למנוע שגיאת התאמה
                continue;
            }
            plan.apkParts.add(apkPart);

            PackageInfo info = getArchivePackageInfoCompat(pm, apkPart.getAbsolutePath());
            if (info != null && info.packageName != null) {
                String lowerName = apkPart.getName().toLowerCase(Locale.ROOT);
                boolean looksLikeBase = lowerName.equals("base.apk")
                        || lowerName.endsWith("_base.apk")
                        || (!lowerName.contains("config.") && !lowerName.startsWith("split_config"));
                if (bestBaseCandidate == null || looksLikeBase) {
                    bestBaseCandidate = apkPart;
                    bestBasePkgInfo = info;
                }
            }
        }

        if (plan.apkParts.isEmpty()) {
            plan.apkParts.addAll(extractedApks);
        }

        if (bestBaseCandidate != null && bestBasePkgInfo != null) {
            plan.baseApkFile = bestBaseCandidate;
            populateCandidateMetadata(pm, bestBasePkgInfo, bestBaseCandidate.getAbsolutePath(), packageFile.getName(), plan);
        }

        return plan;
    }

    private static void extractApksAndObbsFromZipStream(
            InputStream rawIn,
            File stageRoot,
            List<File> outApks,
            int depth
    ) throws Exception {
        if (depth > 2) return;
        try (ZipInputStream zis = new ZipInputStream(rawIn)) {
            ZipEntry entry;
            int index = outApks.size();
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    zis.closeEntry();
                    continue;
                }
                String entryName = entry.getName();
                String cleanLeaf = new File(entryName.replace('\\', '/')).getName();
                String lower = cleanLeaf.toLowerCase(Locale.ROOT);

                if (lower.endsWith(".apk")) {
                    String safeFileName = (index++) + "_" + cleanLeaf.replaceAll("[^a-zA-Z0-9._-]", "_");
                    if (!safeFileName.toLowerCase(Locale.ROOT).endsWith(".apk")) {
                        safeFileName = safeFileName + ".apk";
                    }
                    File target = new File(stageRoot, safeFileName);
                    try (FileOutputStream fos = new FileOutputStream(target)) {
                        copyStream(zis, fos);
                    }
                    if (target.length() > 0) {
                        outApks.add(target);
                    }
                } else if (lower.endsWith(".obb")) {
                    copyObbEntrySafely(entryName, zis);
                } else if (depth < 2 && (lower.endsWith(".apks") || lower.endsWith(".xapk")
                        || lower.endsWith(".apkm") || lower.endsWith(".apkx")
                        || lower.endsWith(".apk+") || lower.endsWith(".zip"))) {
                    File nestedArchive = new File(stageRoot, "nested_" + depth + "_" + System.nanoTime() + ".zip");
                    try (FileOutputStream fos = new FileOutputStream(nestedArchive)) {
                        copyStream(zis, fos);
                    }
                    try (InputStream nestedIn = new BufferedInputStream(new FileInputStream(nestedArchive))) {
                        extractApksAndObbsFromZipStream(nestedIn, stageRoot, outApks, depth + 1);
                    } catch (Exception ignored) {
                    }
                    //noinspection ResultOfMethodCallIgnored
                    nestedArchive.delete();
                }
                zis.closeEntry();
            }
        }
    }

    private static boolean hasInnerApkEntries(File file) {
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            ZipEntry e;
            int checked = 0;
            while ((e = zis.getNextEntry()) != null && checked < 80) {
                checked++;
                String n = e.getName().toLowerCase(Locale.ROOT);
                if (!e.isDirectory() && (n.endsWith(".apk") || n.endsWith(".apks") || n.endsWith(".xapk"))) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private static boolean hasRootAndroidManifest(File file) {
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            ZipEntry e;
            int checked = 0;
            while ((e = zis.getNextEntry()) != null && checked < 60) {
                checked++;
                if ("AndroidManifest.xml".equals(e.getName())) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private static Set<String> getSupportedNormalizedAbis() {
        Set<String> set = new HashSet<>();
        if (Build.SUPPORTED_ABIS != null) {
            for (String abi : Build.SUPPORTED_ABIS) {
                if (abi != null) {
                    set.add(abi.toLowerCase(Locale.ROOT).replace('-', '_'));
                }
            }
        }
        return set;
    }

    private static String detectExplicitCpuAbiInSplitName(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        for (String abi : KNOWN_CPU_ABIS) {
            if (lower.contains(abi)) {
                return abi.replace('-', '_');
            }
        }
        return null;
    }

    private static PackageInfo getArchivePackageInfoCompat(PackageManager pm, String apkPath) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                return pm.getPackageArchiveInfo(apkPath, PackageManager.PackageInfoFlags.of(0));
            }
            return pm.getPackageArchiveInfo(apkPath, 0);
        } catch (Exception e) {
            return null;
        }
    }

    private static long getVersionCodeCompat(PackageInfo info) {
        if (info == null) return 0L;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return info.getLongVersionCode();
        }
        //noinspection deprecation
        return info.versionCode;
    }

    private static void populateCandidateMetadata(
            PackageManager pm,
            PackageInfo pkgInfo,
            String apkFilePath,
            String fallbackName,
            StagedPackagePlan plan
    ) {
        plan.packageName = pkgInfo.packageName != null ? pkgInfo.packageName : "";
        plan.candidateVersionName = (pkgInfo.versionName != null && !pkgInfo.versionName.trim().isEmpty())
                ? pkgInfo.versionName.trim()
                : "לא צוין";
        plan.candidateVersionCode = getVersionCodeCompat(pkgInfo);

        String resolvedLabel = fallbackName;
        try {
            ApplicationInfo appInfo = pkgInfo.applicationInfo;
            if (appInfo != null) {
                appInfo.sourceDir = apkFilePath;
                appInfo.publicSourceDir = apkFilePath;
                CharSequence cs = appInfo.loadLabel(pm);
                if (cs != null && cs.length() > 0) {
                    resolvedLabel = cs.toString();
                }
            }
        } catch (Exception ignored) {
        }
        plan.candidateAppLabel = resolvedLabel;
    }

    private static InstalledAppDetails queryInstalledAppDetails(Activity activity, String packageName) {
        if (packageName == null || packageName.isEmpty()) return null;
        try {
            PackageManager pm = activity.getPackageManager();
            PackageInfo installed;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                installed = pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0));
            } else {
                installed = pm.getPackageInfo(packageName, 0);
            }
            if (installed == null) return null;

            InstalledAppDetails details = new InstalledAppDetails();
            details.versionName = (installed.versionName != null && !installed.versionName.trim().isEmpty())
                    ? installed.versionName.trim()
                    : "לא צוין";
            details.versionCode = getVersionCodeCompat(installed);
            String label = packageName;
            if (installed.applicationInfo != null) {
                CharSequence cs = installed.applicationInfo.loadLabel(pm);
                if (cs != null && cs.length() > 0) {
                    label = cs.toString();
                }
            }
            details.appLabel = label;
            return details;
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isInstalledNewerThanCandidate(InstalledAppDetails installed, StagedPackagePlan candidate) {
        if (installed.versionCode > 0 && candidate.candidateVersionCode > 0) {
            if (installed.versionCode > candidate.candidateVersionCode) return true;
            if (installed.versionCode < candidate.candidateVersionCode) return false;
        }
        return UpdateChecker.compareSemver(installed.versionName, candidate.candidateVersionName) > 0;
    }

    // סעיף יג: התראה מסודרת ומפורטת כאשר קיימת במכשיר גירסה חדשה יותר של האפליקציה שבאים להתקין
    private static void showNewerVersionAlreadyInstalledDialog(
            Activity activity,
            File packageFile,
            StagedPackagePlan plan,
            InstalledAppDetails installed
    ) {
        String message = "במכשיר כבר קיימת גירסה יותר מעודכנת של אפליקציה זו!\n\n"
                + "פרטי האפליקציה המותקנת במכשיר:\n"
                + "• שם האפליקציה: " + installed.appLabel + "\n"
                + "• גירסה מותקנת: " + installed.versionName + " (קוד גירסה: " + installed.versionCode + ")\n"
                + "• שם חבילה: " + plan.packageName + "\n\n"
                + "פרטי האפליקציה בקובץ ההתקנה:\n"
                + "• שם האפליקציה: " + plan.candidateAppLabel + "\n"
                + "• שם הקובץ: " + packageFile.getName() + "\n"
                + "• גירסה בקובץ: " + plan.candidateVersionName + " (קוד גירסה: " + plan.candidateVersionCode + ")";

        ThemedDialogHelper.showCustomDialog(
                activity,
                "מותקנת גירסה יותר מעודכנת",
                message,
                null,
                "סגור",
                () -> cleanupStagingDir(plan),
                "נסה להתקין בכל זאת",
                () -> {
                    AlertDialog dlg = showProgressDialog(activity, "מבצע ניסיון התקנה...");
                    INSTALL_EXEC.execute(() -> {
                        try {
                            commitPackageSession(activity, packageFile, plan, dlg);
                        } catch (Exception e) {
                            MAIN.post(() -> {
                                dismissSafely(dlg);
                                cleanupStagingDir(plan);
                                Toast.makeText(activity, "ההתקנה נחסמה עקב גירסה חדשה יותר שמותקנת במכשיר", Toast.LENGTH_LONG).show();
                            });
                        }
                    });
                },
                false
        );
    }

    private static void commitPackageSession(
            Activity activity,
            File originalPackageFile,
            StagedPackagePlan plan,
            AlertDialog progressDialog
    ) throws Exception {
        PackageInstaller packageInstaller = activity.getPackageManager().getPackageInstaller();
        int sessionId = -1;
        try {
            PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                    PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            if (plan.packageName != null && !plan.packageName.isEmpty()) {
                params.setAppPackageName(plan.packageName);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
            }

            sessionId = packageInstaller.createSession(params);
            try (PackageInstaller.Session session = packageInstaller.openSession(sessionId)) {
                int partIdx = 0;
                for (File apkPart : plan.apkParts) {
                    String writeName = (apkPart == plan.baseApkFile)
                            ? "base.apk"
                            : ("split_" + partIdx + ".apk");
                    partIdx++;
                    long exactLen = apkPart.length() > 0 ? apkPart.length() : -1L;
                    try (InputStream in = new BufferedInputStream(new FileInputStream(apkPart));
                         OutputStream out = session.openWrite(writeName, 0, exactLen)) {
                        copyStream(in, out);
                        session.fsync(out);
                    }
                }

                Intent callbackIntent = new Intent(activity, UpdateInstallReceiver.class);
                callbackIntent.setAction(UpdateInstallReceiver.ACTION_EXTERNAL_APP_INSTALL);
                callbackIntent.setPackage(activity.getPackageName());
                String displayLabel = (plan.candidateAppLabel != null && !plan.candidateAppLabel.isEmpty())
                        ? plan.candidateAppLabel
                        : originalPackageFile.getName();
                callbackIntent.putExtra(UpdateInstallReceiver.EXTRA_APP_LABEL, displayLabel);
                if (plan.singleDirectApk && plan.baseApkFile != null) {
                    callbackIntent.putExtra(UpdateInstallReceiver.EXTRA_APK_FILE_PATH, plan.baseApkFile.getAbsolutePath());
                }

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

                MAIN.post(() -> dismissSafely(progressDialog));
                session.commit(pendingIntent.getIntentSender());

                // ניקוי קובצי הביניים לאחר שהסשן הועבר למערכת ההפעלה (השהיה קצרה למקרה של Fallback)
                MAIN.postDelayed(() -> cleanupStagingDir(plan), 45_000L);
            }
        } catch (Exception ex) {
            if (sessionId != -1) {
                try {
                    packageInstaller.abandonSession(sessionId);
                } catch (Exception ignored) {
                }
            }
            throw ex;
        }
    }

    private static boolean trySystemFallbackForSingleApk(
            Activity activity,
            File originalFile,
            StagedPackagePlan plan
    ) {
        try {
            File targetApk = null;
            if (plan != null && plan.singleDirectApk && plan.baseApkFile != null && plan.baseApkFile.exists()) {
                targetApk = plan.baseApkFile;
            } else if (originalFile.getName().toLowerCase(Locale.ROOT).endsWith(".apk")) {
                targetApk = originalFile;
            }
            if (targetApk == null) return false;

            Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".fileprovider", targetApk);
            Intent install = new Intent(Intent.ACTION_VIEW);
            install.setDataAndType(uri, "application/vnd.android.package-archive");
            install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(install);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void cleanupStagingDir(StagedPackagePlan plan) {
        if (plan == null || plan.stagingDir == null || !plan.stagingDir.exists()) return;
        INSTALL_EXEC.execute(() -> StorageUtils.deleteRecursively(plan.stagingDir));
    }

    private static void copyObbEntrySafely(String entryName, InputStream in) {
        try {
            String normalized = entryName.replace('\\', '/');
            int obbIdx = normalized.toLowerCase(Locale.ROOT).indexOf("android/obb/");
            File destFile;
            if (obbIdx >= 0) {
                String rel = normalized.substring(obbIdx);
                destFile = new File(Environment.getExternalStorageDirectory(), rel);
            } else {
                String cleanName = new File(normalized).getName();
                File obbRoot = new File(Environment.getExternalStorageDirectory(), "Android/obb");
                destFile = new File(obbRoot, cleanName);
            }
            File parent = destFile.getParentFile();
            if (parent != null && !parent.exists()) {
                //noinspection ResultOfMethodCallIgnored
                parent.mkdirs();
            }
            try (FileOutputStream fos = new FileOutputStream(destFile)) {
                copyStream(in, fos);
            }
        } catch (Exception ignored) {
        }
    }

    private static void copyStream(InputStream in, OutputStream out) throws Exception {
        byte[] buf = new byte[16 * 1024];
        int read;
        while ((read = in.read(buf)) != -1) {
            out.write(buf, 0, read);
        }
    }
}
