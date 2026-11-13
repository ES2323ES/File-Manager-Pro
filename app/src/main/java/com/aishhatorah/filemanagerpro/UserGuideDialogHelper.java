package com.aishhatorah.filemanagerpro;

import android.app.Activity;
import android.content.Intent;

/**
 * סעיף 20: שומר תאימות לקריאות קיימות ומפנה ישירות למסך המדריך המלא והחדש (UserGuideActivity).
 */
public class UserGuideDialogHelper {

    public static void show(Activity activity) {
        if (activity == null) return;
        activity.startActivity(new Intent(activity, UserGuideActivity.class));
    }
}
