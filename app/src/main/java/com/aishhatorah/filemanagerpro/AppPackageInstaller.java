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
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * סעיף יב: מתקין חבילות אוניברסלי התומך בכל סוגי האפליקציות לאנדרואיד:
 * - קובצי APK רגילים (.apk)
 * - חבילות מפוצלות (.apks, .apkm, .xapk) כולל פריסת קובצי OBB במידה וקיימים בתוך XAPK!
 */
public final class AppPackageInstaller {

    private static final ExecutorService INSTALL_EXEC = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private AppPackageInstaller() {
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

        final String nameLower = packageFile.getName().toLowerCase(Locale.ROOT);
        final boolean isBundle = MediaFormatHelper.isSplitAppBundle(nameLower);

        int theme = PrefsManager.getThemeMode(activity);
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        box.setPadding(52, 46, 52, 40);
        box.setBackground(ThemedDialogHelper.createDialogBackground(theme));

        TextView tvTitle = new TextView(activity);
        tvTitle.setText("מתקין אפליקציה...");
        tvTitle.setTextColor(ThemeHelper.getPrimaryTextColor(theme));
        tvTitle.setTextSize(16f);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setPadding(0, 0, 0, 12);
        box.addView(tvTitle);

        TextView tvStatus = new TextView(activity);
        tvStatus.setText(isBundle ? "מכין חבילת התקנה מפוצלת (" + packageFile.getName() + ")..." : "מכין קובץ התקנה...");
        tvStatus.setTextColor(ThemeHelper.getSecondaryTextColor(theme));
        tvStatus.setTextSize(13f);
        tvStatus.setPadding(0, 0, 0, 18);
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

        INSTALL_EXEC.execute(() -> {
            PackageInstaller packageInstaller = activity.getPackageManager().getPackageInstaller();
            int sessionId = -1;
            try {
                PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                        PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
                }

                sessionId = packageInstaller.createSession(params);
                try (PackageInstaller.Session session = packageInstaller.openSession(sessionId)) {
                    int apkSplitsCount;

                    if (!isBundle) {
                        try (InputStream in = new BufferedInputStream(new FileInputStream(packageFile));
                             OutputStream out = session.openWrite("base.apk", 0, packageFile.length())) {
                            copyStream(in, out);
                            session.fsync(out);
                            apkSplitsCount = 1;
                        }
                    } else {
                        apkSplitsCount = streamSplitBundleIntoSession(packageFile, session, tvStatus);
                    }

                    if (apkSplitsCount <= 0) {
                        throw new IllegalStateException("לא נמצאו רכיבי APK תקינים בתוך החבילה");
                    }

                    Intent callbackIntent = new Intent(activity, UpdateInstallReceiver.class);
                    callbackIntent.setAction(UpdateInstallReceiver.ACTION_EXTERNAL_APP_INSTALL);
                    callbackIntent.setPackage(activity.getPackageName());
                    callbackIntent.putExtra(UpdateInstallReceiver.EXTRA_APP_LABEL, packageFile.getName());
                    if (!isBundle) {
                        callbackIntent.putExtra(UpdateInstallReceiver.EXTRA_APK_FILE_PATH, packageFile.getAbsolutePath());
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

                    MAIN.post(dialog::dismiss);
                    session.commit(pendingIntent.getIntentSender());
                }
            } catch (Exception ex) {
                if (sessionId != -1) {
                    try {
                        packageInstaller.abandonSession(sessionId);
                    } catch (Exception ignored) {
                    }
                }
                MAIN.post(() -> {
                    dialog.dismiss();
                    if (!isBundle) {
                        try {
                            Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".fileprovider", packageFile);
                            Intent install = new Intent(Intent.ACTION_VIEW);
                            install.setDataAndType(uri, "application/vnd.android.package-archive");
                            install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                            activity.startActivity(install);
                            return;
                        } catch (Exception ignored) {
                        }
                    }
                    Toast.makeText(activity, "לא ניתן היה להתקין את החבילה: " + packageFile.getName(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private static int streamSplitBundleIntoSession(File bundleFile, PackageInstaller.Session session, TextView tvStatus) throws Exception {
        int apkCount = 0;
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(bundleFile)))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    zis.closeEntry();
                    continue;
                }
                String entryName = entry.getName();
                String lower = entryName.toLowerCase(Locale.ROOT);

                if (lower.endsWith(".apk")) {
                    String safeSplitName = "split_" + apkCount + ".apk";
                    final int idxDisplay = apkCount + 1;
                    MAIN.post(() -> tvStatus.setText("טוען רכיב APK #" + idxDisplay + " מתוך החבילה..."));
                    try (OutputStream out = session.openWrite(safeSplitName, 0, -1)) {
                        copyStream(zis, out);
                        session.fsync(out);
                    }
                    apkCount++;
                } else if (lower.endsWith(".obb")) {
                    copyObbEntrySafely(entryName, zis);
                }
                zis.closeEntry();
            }
        }
        return apkCount;
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
