package com.aishhatorah.filemanagerpro;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.webkit.MimeTypeMap;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.util.Locale;

/**
 * סעיפים 6, 14, 32: מערכת "פתיחה באמצעות" + זיהוי קבצים לא מוכרים + זכירת בחירת "תמיד" או "פעם אחת".
 */
public final class FileOpenWithHelper {

    public static final String ACTION_INTERNAL_CODE = "internal_code";
    public static final String ACTION_INTERNAL_IMAGE = "internal_image";
    public static final String ACTION_INTERNAL_AUDIO = "internal_audio";
    public static final String ACTION_INTERNAL_VIDEO = "internal_video";
    public static final String ACTION_INTERNAL_ARCHIVE = "internal_archive";
    public static final String ACTION_SYSTEM_APP = "system_app";

    private FileOpenWithHelper() {
    }

    public static boolean tryOpenByDefaultOrBuiltin(Activity activity, File file) {
        if (activity == null || file == null || !file.exists()) return false;
        String name = file.getName();
        String ext = MediaFormatHelper.getExtensionNormalized(name);

        // סעיף 32: בדוק אם המשתמש הגדיר "תמיד" לסיומת זו
        if (!ext.isEmpty()) {
            String savedDefault = PrefsManager.getDefaultOpenAction(activity, ext);
            if (!savedDefault.isEmpty()) {
                executeOpenAction(activity, file, savedDefault);
                return true;
            }
        }

        // חבילות התקנה לאנדרואיד
        if (MediaFormatHelper.isInstallableAppPackage(name)) {
            AppPackageInstaller.inspectAndInstall(activity, file);
            return true;
        }

        // סעיף 6: בחירת מנוע פנימי vs מערכת לוידאו / תמונות / אודיו
        if (MediaFormatHelper.isVideoFile(name)) {
            executeOpenAction(activity, file,
                    PrefsManager.isUseInternalVideoPlayer(activity) ? ACTION_INTERNAL_VIDEO : ACTION_SYSTEM_APP);
            return true;
        }
        if (MediaFormatHelper.isImageFile(name)) {
            executeOpenAction(activity, file,
                    PrefsManager.isUseInternalImageViewer(activity) ? ACTION_INTERNAL_IMAGE : ACTION_SYSTEM_APP);
            return true;
        }
        if (MediaFormatHelper.isAudioFile(name)) {
            executeOpenAction(activity, file,
                    PrefsManager.isUseInternalAudioPlayer(activity) ? ACTION_INTERNAL_AUDIO : ACTION_SYSTEM_APP);
            return true;
        }

        // ארכיונים
        if (MediaFormatHelper.isZipOrArchiveFile(name)) {
            executeOpenAction(activity, file, ACTION_INTERNAL_ARCHIVE);
            return true;
        }

        // קבצי קוד וטקסט מתקדמים
        if (MediaFormatHelper.isTextFile(name) || MediaFormatHelper.isCodeOrSyntaxFile(name)) {
            executeOpenAction(activity, file, ACTION_INTERNAL_CODE);
            return true;
        }

        // PDF / Office במסמך מובנה
        if (MediaFormatHelper.isDocumentFile(name)) {
            Intent i = new Intent(activity, BuiltInViewerActivity.class);
            i.putExtra(BuiltInViewerActivity.EXTRA_FILE_PATH, file.getAbsolutePath());
            activity.startActivity(i);
            return true;
        }

        // סעיף 14: קובץ לא מוכר -> הצג תיבת "פתח כ..."
        showOpenAsDialog(activity, file);
        return true;
    }

    public static void showOpenAsDialog(Activity activity, File file) {
        String ext = MediaFormatHelper.getExtensionNormalized(file.getName());
        int theme = PrefsManager.getThemeMode(activity);
        int textPrimary = ThemeHelper.getPrimaryTextColor(activity, theme);

        String[] labels = new String[]{
                "מסמך / טקסט / קוד",
                "תמונה",
                "שמע (אודיו)",
                "וידאו (סרטון)",
                "ארכיון דחוס",
                "אפליקציית מערכת חיצונית"
        };
        String[] actions = new String[]{
                ACTION_INTERNAL_CODE,
                ACTION_INTERNAL_IMAGE,
                ACTION_INTERNAL_AUDIO,
                ACTION_INTERNAL_VIDEO,
                ACTION_INTERNAL_ARCHIVE,
                ACTION_SYSTEM_APP
        };

        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);

        RadioGroup rg = new RadioGroup(activity);
        rg.setOrientation(RadioGroup.VERTICAL);
        for (int i = 0; i < labels.length; i++) {
            RadioButton rb = new RadioButton(activity);
            rb.setId(1000 + i);
            rb.setText(labels[i]);
            rb.setTextColor(textPrimary);
            rb.setPadding(8, 10, 8, 10);
            if (i == 0) rb.setChecked(true);
            rg.addView(rb);
        }
        panel.addView(rg);

        ThemedDialogHelper.showCustomDialog(
                activity,
                "פתיחה באמצעות — " + file.getName(),
                "בחר כיצד לפתוח את הקובץ. תוכל לבחור \"תמיד\" כדי לשמור לסיומת זו:",
                panel,
                "פעם אחת",
                () -> {
                    int sel = rg.getCheckedRadioButtonId() - 1000;
                    if (sel < 0 || sel >= actions.length) sel = 0;
                    executeOpenAction(activity, file, actions[sel]);
                },
                ext.isEmpty() ? null : "תמיד (" + ext + ")",
                () -> {
                    int sel = rg.getCheckedRadioButtonId() - 1000;
                    if (sel < 0 || sel >= actions.length) sel = 0;
                    PrefsManager.setDefaultOpenAction(activity, ext, actions[sel]);
                    executeOpenAction(activity, file, actions[sel]);
                },
                true
        );
    }

    public static void executeOpenAction(Activity activity, File file, String action) {
        try {
            if (ACTION_INTERNAL_CODE.equals(action)) {
                Intent i = new Intent(activity, CodeEditorActivity.class);
                i.putExtra(CodeEditorActivity.EXTRA_FILE_PATH, file.getAbsolutePath());
                activity.startActivity(i);
                return;
            }
            if (ACTION_SYSTEM_APP.equals(action)) {
                openWithSystemChooser(activity, file);
                return;
            }
            Intent i = new Intent(activity, BuiltInViewerActivity.class);
            i.putExtra(BuiltInViewerActivity.EXTRA_FILE_PATH, file.getAbsolutePath());
            i.putExtra(BuiltInViewerActivity.EXTRA_FORCE_TYPE, action);
            activity.startActivity(i);
        } catch (Throwable t) {
            AppLogger.error(activity, "OpenWith", "Failed opening file", t);
            Toast.makeText(activity, "לא ניתן לפתוח קובץ זה: " + t.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    public static void openWithSystemChooser(Activity activity, File file) {
        try {
            Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".fileprovider", file);
            String mime = guessMimeType(file.getName());
            Intent view = new Intent(Intent.ACTION_VIEW);
            view.setDataAndType(uri, mime);
            view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(Intent.createChooser(view, "פתיחה באמצעות"));
        } catch (Throwable t) {
            Toast.makeText(activity, "אין אפליקציית מערכת מתאימה לסוג קובץ זה", Toast.LENGTH_SHORT).show();
        }
    }

    public static String guessMimeType(String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (MediaFormatHelper.isVideoFile(lower)) return "video/*";
        if (MediaFormatHelper.isImageFile(lower)) return "image/*";
        if (MediaFormatHelper.isAudioFile(lower)) return "audio/*";
        if (MediaFormatHelper.isPdfFile(lower)) return "application/pdf";
        if (MediaFormatHelper.isTextFile(lower)) return "text/plain";
        int dot = lower.lastIndexOf('.');
        if (dot > 0 && dot < lower.length() - 1) {
            String ext = lower.substring(dot + 1);
            String m = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
            if (m != null) return m;
        }
        return "*/*";
    }
}
