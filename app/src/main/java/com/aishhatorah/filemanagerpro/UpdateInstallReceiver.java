package com.aishhatorah.filemanagerpro;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

public class UpdateInstallReceiver extends BroadcastReceiver {

    private static final String UPDATE_CHANNEL_ID = "fmp_update_completed_channel";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);

        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirmIntent = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirmIntent != null) {
                confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(confirmIntent);
            }
        } else if (status == PackageInstaller.STATUS_SUCCESS) {
            PrefsManager.setPendingUpdateSuccessBanner(context, true);
            postSuccessNotification(context);
            Toast.makeText(context, "File Manager Pro עודכן בהצלחה!", Toast.LENGTH_LONG).show();
        } else {
            String msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
            Toast.makeText(context, "התקנת העדכון נכשלה" + (msg != null ? ": " + msg : ""), Toast.LENGTH_LONG).show();
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

            String text = "העדכון לגירסה " + BuildConfig.VERSION_NAME + " הושלם בהצלחה. כל ההגדרות והקבצים נשמרו במלואם.";
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, UPDATE_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_info)
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
