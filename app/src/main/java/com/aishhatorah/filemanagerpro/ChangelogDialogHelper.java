package com.aishhatorah.filemanagerpro;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class ChangelogDialogHelper {

    public static void show(Activity activity) {
        int theme = PrefsManager.getThemeMode(activity);
        int textPrimary = ThemeHelper.getPrimaryTextColor(theme);
        int textSecondary = ThemeHelper.getSecondaryTextColor(theme);
        int accent = ThemeHelper.getAccentColor(theme);

        ScrollView scroll = new ScrollView(activity);
        LinearLayout outer = new LinearLayout(activity);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        outer.setPadding(46, 44, 46, 36);
        outer.setBackground(ThemedDialogHelper.createDialogBackground(theme));

        TextView tvHeader = new TextView(activity);
        tvHeader.setText("יומן שינויים — File Manager Pro");
        tvHeader.setTextSize(18f);
        tvHeader.setTypeface(Typeface.DEFAULT_BOLD);
        tvHeader.setTextColor(accent);
        tvHeader.setGravity(Gravity.CENTER);
        outer.addView(tvHeader);

        TextView tvSub = new TextView(activity);
        tvSub.setText("פירוט מלא של כל החידושים והשיפורים בכל גירסה");
        tvSub.setTextSize(12.5f);
        tvSub.setTextColor(textSecondary);
        tvSub.setGravity(Gravity.CENTER);
        tvSub.setPadding(0, 8, 0, 24);
        outer.addView(tvSub);

        addVersionCard(outer, activity, theme, accent, textPrimary,
                "גירסה 0.2.3 (נוכחית)",
                new String[]{
                        "• נגן אודיו, נגן וידאו, מציג תמונות, קורא מסמכים (PDF / Office / TXT) ומחלץ ארכיונים מובנים בתוך האפליקציה ללא בקשת פתיחה מאפליקציות חיצוניות.",
                        "• הוספת דחיסה לקובץ ZIP וחילוץ ישיר של קובצי ZIP מתוך תפריט הקובץ.",
                        "• לחצן + חדש בתיקיות המאפשר יצירת תיקייה חדשה או קובץ חדש לבחירה.",
                        "• מיקום מדויק בשורות התיקיות: סמל התיקייה מצד שמאל ושלוש הנקודות מצד ימין, עם תפריט מותאם לגוון האפליקציה.",
                        "• תעדוף תיקיות בעברית לפני תיקיות באנגלית, ובחירת תצוגת רשת או רשימה בהגדרות.",
                        "• הצגת נפח אמיתי על גבי כל הקטגוריות במסך הבית (תמונות, שמע, סירטונים, מסמכים, אפליקציות, הורדות, קבצים חדשים וסל מיחזור).",
                        "• שיתוף ישיר של הקובץ החתוך מתוך חותך האודיו המקצועי וחותך הוידאו המקצועי.",
                        "• מנגנון בדיקת עדכונים חכם והתקנה אוטומטית עם הודעת אישור בסיום השדרוג.",
                        "• אימות כפול בריקון סל המיחזור ובאיפוס הגדרות, והגנת יציאה בלחיצה כפולה (לחץ שוב כדי לצאת)."
                });

        addVersionCard(outer, activity, theme, Color.parseColor("#0D9488"), textPrimary,
                "גירסה 0.2.2",
                new String[]{
                        "• השקה ראשונית של File Manager Pro בעברית מלאה.",
                        "• מסך בית ב-4 שורות × 3 עמודות (משמאל לימין) עם החלקה מלמעלה למטה לרענון.",
                        "• שליטה מלאה בגוון התאורה: בהיר, כהה וניאון.",
                        "• כלי ניתוח לאיתור הקבצים הכבדים ביותר והתרעה אוטומטית ב-96% תפוסה.",
                        "• סל מיחזור חכם עם שחזור למיקום המקורי, חותך אודיו וחותך וידאו."
                });

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(scroll)
                .create();

        Button btnClose = ThemedDialogHelper.createThemedButton(activity, "סגור", theme, true, false);
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        closeLp.topMargin = 10;
        btnClose.setLayoutParams(closeLp);
        btnClose.setOnClickListener(v -> dialog.dismiss());
        outer.addView(btnClose);

        scroll.addView(outer);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }

    private static void addVersionCard(
            LinearLayout parent,
            Activity activity,
            int theme,
            int badgeColor,
            int bodyColor,
            String versionTitle,
            String[] bullets
    ) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(32, 28, 32, 28);

        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(26f);
        int innerBg = theme == PrefsManager.THEME_LIGHT
                ? Color.parseColor("#F8FAFC")
                : (theme == PrefsManager.THEME_NEON ? Color.parseColor("#071029") : Color.parseColor("#0F172A"));
        shape.setColor(innerBg);
        shape.setStroke(2, Color.argb(80, Color.red(badgeColor), Color.green(badgeColor), Color.blue(badgeColor)));
        card.setBackground(shape);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = 20;
        card.setLayoutParams(lp);

        TextView tvVer = new TextView(activity);
        tvVer.setText(versionTitle);
        tvVer.setTextSize(15.5f);
        tvVer.setTypeface(Typeface.DEFAULT_BOLD);
        tvVer.setTextColor(badgeColor);
        tvVer.setPadding(0, 0, 0, 12);
        card.addView(tvVer);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bullets.length; i++) {
            if (i > 0) sb.append("\n\n");
            sb.append(bullets[i]);
        }

        TextView tvBody = new TextView(activity);
        tvBody.setText(sb.toString());
        tvBody.setTextSize(13f);
        tvBody.setTextColor(bodyColor);
        tvBody.setLineSpacing(5f, 1.15f);
        card.addView(tvBody);

        parent.addView(card);
    }
}
