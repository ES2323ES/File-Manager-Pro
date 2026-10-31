package com.aishhatorah.filemanagerpro;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
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
import android.widget.ScrollView;
import android.widget.TextView;

public class UserGuideDialogHelper {

    public static void show(Activity activity) {
        int theme = PrefsManager.getThemeMode(activity);
        int cardColor = ThemeHelper.getCardColor(theme);
        int textPrimary = ThemeHelper.getPrimaryTextColor(theme);
        int accent = ThemeHelper.getAccentColor(theme);

        ScrollView scroll = new ScrollView(activity);
        LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        container.setBackground(ThemedDialogHelper.createDialogBackground(theme));
        container.setPadding(42, 42, 42, 36);

        TextView header = new TextView(activity);
        header.setText("מדריך למשתמש — File Manager Pro");
        header.setTextSize(18.5f);
        header.setTypeface(Typeface.DEFAULT_BOLD);
        header.setTextColor(accent);
        header.setGravity(Gravity.CENTER);
        container.addView(header);

        TextView sub = new TextView(activity);
        sub.setText("המדריך הרשמי והמלא לניהול קבצים, צפייה פנימית, חילוץ ודחיסת ZIP, חיתוך מדיה ועדכונים אוטומטיים (גירסה " + BuildConfig.VERSION_NAME + ")");
        sub.setTextSize(12.5f);
        sub.setTextColor(ThemeHelper.getSecondaryTextColor(theme));
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, 8, 0, 26);
        container.addView(sub);

        addGuideCard(container, activity, R.drawable.ic_storage_internal,
                "1. ניווט במסך הבית ונפחים בזמן אמת",
                Color.parseColor("#2563EB"),
                cardColor, textPrimary,
                new String[]{
                        "שורה ראשונה: גישה אל ", "אחסון ראשי", ", ", "כרטיס SD", " ו-", "הורדות", ".",
                        "\nשורה שנייה: ", "תמונות", ", ", "שמע", " ו-", "סירטונים", " (כולל הצגת ", "הנפח המדויק", " של כל קטגוריה).",
                        "\nשורה שלישית: ", "מסמכים", ", ", "אפליקציות", " ו-", "קבצים חדשים", ".",
                        "\nשורה רביעית: ", "סל מיחזור", ", ", "חותך אודיו מקצועי", " ו-", "חותך וידאו מקצועי", "."
                });

        addGuideCard(container, activity, R.drawable.ic_documents,
                "2. פתיחה פנימית מלאה וחילוץ/דחיסת ZIP",
                Color.parseColor("#0D9488"),
                cardColor, textPrimary,
                new String[]{
                        "כלל הקבצים נפתחים ", "ישירות בתוך האפליקציה", " ללא צורך באפליקציות חיצוניות: ", "נגן אודיו ווידאו", ", ", "מציג תמונות", ", ", "קורא PDF ומסמכי Office", " ומחלץ ארכיוני ", "ZIP", ".",
                        "\nבלחיצה על ", "+", " למעלה משמאל בתיקיות ניתן ליצור ", "תיקייה חדשה", " או ", "קובץ חדש", ", ובתפריט הפעולות (שלוש נקודות מימין) ניתן לבצע ", "דחיסה לקובץ ZIP", "."
                });

        addGuideCard(container, activity, R.drawable.ic_recycle_bin,
                "3. מיון תיקיות בעברית וסל מיחזור מוגן",
                Color.parseColor("#DC2626"),
                cardColor, textPrimary,
                new String[]{
                        "האפליקציה מציגה אוטומטית ", "תיקיות בעברית לפני תיקיות באנגלית", " לנוחות מרבית.",
                        "\nבעת ריקון ", "סל המיחזור", " או ", "איפוס הגדרות", " מתבקש אישור כפול להגנה מלאה מפני מחיקה בשגגה."
                });

        addGuideCard(container, activity, R.drawable.ic_audio_cutter,
                "4. חותך אודיו וחותך וידאו מקצועי + שיתוף",
                Color.parseColor("#7C3AED"),
                cardColor, textPrimary,
                new String[]{
                        "בחרו קובץ שמע או סרטון, הגדירו את ", "נקודת ההתחלה והסיום", ", חתכו ושמרו ", "ללא פגיעה באיכות המקור", " — ושתפו מיד באמצעות לחצן ", "שיתוף הקובץ שנחתך", "."
                });

        addGuideCard(container, activity, R.drawable.ic_settings,
                "5. תצוגת רשת/רשימה, ניתוח נפחים והתרעת 96%",
                Color.parseColor("#D97706"),
                cardColor, textPrimary,
                new String[]{
                        "בהגדרות ניתן לבחור בין ", "תצוגת רשימה", " ל-", "תצוגת רשת", ", לעבור בין גווני ", "בהיר / כהה / ניאון", " ולהפעיל את כלי ה-", "ניתוח", " לאיתור הקבצים הגדולים ביותר והתרעת ", "96% תפוסה", "."
                });

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(scroll)
                .create();

        // סעיף יח: כפתור "הבנתי" בצבע ברור התואם לגוון האפליקציה
        Button btnUnderstood = ThemedDialogHelper.createThemedButton(activity, "הבנתי", theme, true, false);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btnLp.topMargin = 10;
        btnUnderstood.setLayoutParams(btnLp);
        btnUnderstood.setOnClickListener(v -> dialog.dismiss());
        container.addView(btnUnderstood);

        scroll.addView(container);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }

    private static void addGuideCard(
            LinearLayout parent,
            Activity activity,
            int iconRes,
            String title,
            int highlightColor,
            int cardBg,
            int bodyColor,
            String[] segments
    ) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(34, 30, 34, 30);

        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(30f);
        shape.setColor(cardBg);
        shape.setStroke(2, Color.argb(65, Color.red(highlightColor), Color.green(highlightColor), Color.blue(highlightColor)));
        card.setBackground(shape);

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardLp.bottomMargin = 22;
        card.setLayoutParams(cardLp);

        LinearLayout titleRow = new LinearLayout(activity);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        ImageView icon = new ImageView(activity);
        icon.setImageResource(iconRes);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(64, 64);
        iconLp.setMarginEnd(20);
        titleRow.addView(icon, iconLp);

        TextView tvTitle = new TextView(activity);
        tvTitle.setText(title);
        tvTitle.setTextSize(15f);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setTextColor(highlightColor);
        titleRow.addView(tvTitle);

        card.addView(titleRow);

        SpannableStringBuilder ssb = new SpannableStringBuilder();
        for (int i = 0; i < segments.length; i++) {
            int start = ssb.length();
            ssb.append(segments[i]);
            int end = ssb.length();
            if (i % 2 == 1) {
                ssb.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                ssb.setSpan(new ForegroundColorSpan(highlightColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }

        TextView tvBody = new TextView(activity);
        tvBody.setText(ssb);
        tvBody.setTextColor(bodyColor);
        tvBody.setTextSize(13.5f);
        tvBody.setLineSpacing(6f, 1.15f);
        tvBody.setPadding(0, 16, 0, 0);
        card.addView(tvBody);

        parent.addView(card);
    }
}
