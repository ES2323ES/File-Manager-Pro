package com.aishhatorah.filemanagerpro;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.content.FileProvider;

import java.io.File;

public class UpdateInstallReceiver extends BroadcastReceiver {

    public static final String ACTION_SELF_UPDATE_COMMIT = "com.aishhatorah.filemanagerpro.ACTION_SELF_UPDATE_COMMIT";
    public static final String ACTION_EXTERNAL_APP_INSTALL = "com.aishhatorah.filemanagerpro.ACTION_EXTERNAL_APP_INSTALL";
    public static final String EXTRA_APP_LABEL = "extra_app_label";
    public static final String EXTRA_APK_FILE_PATH = "extra_apk_file_path";

    private static final String UPDATE_CHANNEL_ID = "fmp_update_completed_channel";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        boolean isExternalApp = ACTION_EXTERNAL_APP_INSTALL.equals(action);
        String appLabel = intent.getStringExtra(EXTRA_APP_LABEL);
        String fallbackApkPath = intent.getStringExtra(EXTRA_APK_FILE_PATH);

        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);

        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirmIntent = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirmIntent != null) {
                try {
                    confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(confirmIntent);
                    return;
                } catch (Exception ignored) {
                }
            }
            if (launchFallbackApkView(context, fallbackApkPath)) {
                return;
            }
        } else if (status == PackageInstaller.STATUS_SUCCESS) {
            if (isExternalApp) {
                String labelMsg = (appLabel != null && !appLabel.isEmpty()) ? (" (" + appLabel + ")") : "";
                Toast.makeText(context, "האפליקציה הותקנה בהצלחה" + labelMsg, Toast.LENGTH_LONG).show();
            } else {
                PrefsManager.setPendingUpdateSuccessBanner(context, true);
                postSuccessNotification(context);
                Toast.makeText(context, "File Manager Pro עודכן בהצלחה!", Toast.LENGTH_LONG).show();
            }
        } else if (status == PackageInstaller.STATUS_FAILURE_ABORTED) {
            Toast.makeText(context, "ההתקנה בוטלה", Toast.LENGTH_SHORT).show();
        } else {
            // אם המערכת חסמה התקנת Session שקטה (למשל במכשירי Xiaomi/Samsung בפעם הראשונה), נעבור אוטומטית להתקנה ישירה מהקובץ ללא שגיאה!
            if (launchFallbackApkView(context, fallbackApkPath)) {
                return;
            }
            String prefix = isExternalApp ? "לא ניתן היה להשלים את התקנת האפליקציה" : "לא ניתן היה להשלים את התקנת העדכון";
            Toast.makeText(context, prefix, Toast.LENGTH_LONG).show();
        }
    }

    private boolean launchFallbackApkView(Context context, String apkPath) {
        if (apkPath == null || apkPath.isEmpty()) return false;
        try {
            File apkFile = new File(apkPath);
            if (!apkFile.exists() || !apkPath.toLowerCase().endsWith(".apk")) return false;
            Uri apkUri = FileProvider.getUriForFile(
                    context,
                    context.getPackageName() + ".fileprovider",
                    apkFile
            );
            Intent installIntent = new Intent(Intent.ACTION_VIEW);
            installIntent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(installIntent);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static void postSuccessNotification(Context context) {
        try {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;

            NotificationChannel channel = new NotificationChannel(
                    UPDATE_CHANNEL_ID,
                    "עדכוני File Manager Pro",
                    NotificationManager.IMPORTANCE_HIGH
            );
            nm.createNotificationChannel(channel);

            Intent launch = new Intent(context, MainActivity.class);
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pi = PendingIntent.getActivity(
                    context,
                    203,
                    launch,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            String verDisplay = BuildConfig.VERSION_NAME
                    + (UpdateChecker.compareSemver(BuildConfig.VERSION_NAME, "0.2.5") >= 0 ? " רשמית" : "");
            String text = "העדכון לגירסה " + verDisplay + " הושלם בהצלחה. כל ההגדרות והקבצים נשמרו במלואם.";
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, UPDATE_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_storage_internal)
                    .setContentTitle("File Manager Pro עודכן בהצלחה")
                    .setContentText(text)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setContentIntent(pi)
                    .setAutoCancel(true);

            nm.notify(2023, builder.build());
        } catch (Exception ignored) {
        }
    }
}
