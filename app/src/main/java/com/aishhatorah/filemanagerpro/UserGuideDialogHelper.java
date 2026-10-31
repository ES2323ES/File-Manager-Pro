package com.aishhatorah.filemanagerpro;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class UserGuideDialogHelper {

    public static void show(Activity activity) {
        int theme = PrefsManager.getThemeMode(activity);
        int bgColor = ThemeHelper.getBgColor(theme);
        int cardColor = ThemeHelper.getCardColor(theme);
        int textPrimary = ThemeHelper.getPrimaryTextColor(theme);
        int accent = ThemeHelper.getAccentColor(theme);

        ScrollView scroll = new ScrollView(activity);
        LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        container.setBackgroundColor(bgColor);
        container.setPadding(40, 40, 40, 32);

        TextView header = new TextView(activity);
        header.setText("מדריך למשתמש — File Manager Pro");
        header.setTextSize(19f);
        header.setTypeface(Typeface.DEFAULT_BOLD);
        header.setTextColor(accent);
        header.setGravity(Gravity.CENTER);
        container.addView(header);

        TextView sub = new TextView(activity);
        sub.setText("המדריך הרשמי והמלא לניהול קבצים, חיתוך מדיה, ניתוח נפחים ועדכונים ישירים (גירסה 0.2.2)");
        sub.setTextSize(13f);
        sub.setTextColor(ThemeHelper.getSecondaryTextColor(theme));
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, 8, 0, 28);
        container.addView(sub);

        addGuideCard(container, activity, R.drawable.ic_storage_internal,
                "1. ניווט במסך הבית (4 שורות משמאל לימין)",
                Color.parseColor("#2563EB"),
                cardColor, textPrimary,
                new String[]{
                        "שורה ראשונה: גישה מהירה אל ", "אחסון ראשי", ", ", "כרטיס SD", " ותיקיית ", "הורדות", ".",
                        "\nשורה שנייה: סינון חכם של כלל קובצי המדיה — ", "תמונות", ", ", "שמע", " ו-", "סירטונים", ".",
                        "\nשורה שלישית: ריכוז ", "מסמכים", " (PDF/Office), קובצי התקנה ב-", "אפליקציות", " ו-", "קבצים חדשים", ".",
                        "\nשורה רביעית: כלי מערכת מתקדמים — ", "סל מיחזור", ", ", "חותך אודיו", " ו-", "חותך וידאו", "."
                });

        addGuideCard(container, activity, R.drawable.ic_documents,
                "2. עריכת קבצים מלאה ורענון בהחלקה",
                Color.parseColor("#0D9488"),
                cardColor, textPrimary,
                new String[]{
                        "לחיצה על תפריט הפעולות של כל קובץ או תיקייה מאפשרת ", "העתקה", ", ", "העברה", ", ", "שינוי שם", ", ", "שיתוף", " ו-", "מחיקה", ".",
                        "\nהחלקה מלמעלה למטה (", "Swipe to Refresh", ") מרעננת באופן מיידי את כלל הקבצים, הקטגוריות ומדידת הנפחים."
                });

        addGuideCard(container, activity, R.drawable.ic_recycle_bin,
                "3. סל מיחזור חכם והגנה מפני מחיקה",
                Color.parseColor("#DC2626"),
                cardColor, textPrimary,
                new String[]{
                        "כאשר ", "סל המיחזור", " מופעל בהגדרות, קבצים שנמחקים נשמרים בבטחה וניתנים ל-", "שחזור בלחיצה אחת", " למיקומם המקורי או לריקון סופי."
                });

        addGuideCard(container, activity, R.drawable.ic_audio_cutter,
                "4. חותך אודיו וחותך וידאו מובנים",
                Color.parseColor("#7C3AED"),
                cardColor, textPrimary,
                new String[]{
                        "בחרו קובץ שמע או סרטון, הגדירו את ", "נקודת ההתחלה והסיום", " בדיוק של שניות, והאפליקציה תחתוך ותשמור עותק נקי ומהיר ", "ללא פגיעה באיכות המקור", "."
                });

        addGuideCard(container, activity, R.drawable.ic_settings,
                "5. ניתוח הקבצים הגדולים ביותר והתרעת 96%",
                Color.parseColor("#D97706"),
                cardColor, textPrimary,
                new String[]{
                        "בתוך לחצן ", "ההגדרות (למעלה מימין)", " תמצאו את כלי ה-", "ניתוח", " הסורק ומדרג את הקבצים בעלי המשקל הגדול ביותר במכשיר.",
                        "\nבנוסף, האפליקציה מנטרת את הנפח ומתריעה אוטומטית ברגע שהזיכרון הפנימי או החיצוני מגיעים ל-", "96% תפוסה", "."
                });

        addGuideCard(container, activity, R.drawable.ic_info,
                "6. ערכות תאורה (בהיר, כהה, ניאון) ועדכוני GitHub",
                Color.parseColor("#0284C7"),
                cardColor, textPrimary,
                new String[]{
                        "בהגדרות ניתן לבחור בין 3 מצבי תאורה יפהפיים: ", "בהיר", ", ", "כהה", " או ", "ניאון", ".",
                        "\nבחלון ", "אודות (למעלה משמאל)", " ניתן לבצע ", "בדיקת עדכונים ישירה מול GitHub", ", להוריד את הגירסה החדשה ולהתקין אותה מיד בזכות ", "מפתח החתימה הקבוע", "."
                });

        scroll.addView(container);

        new AlertDialog.Builder(activity)
                .setView(scroll)
                .setPositiveButton("הבנתי, תודה!", null)
                .show();
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
        shape.setStroke(2, Color.argb(55, Color.red(highlightColor), Color.green(highlightColor), Color.blue(highlightColor)));
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
            // Odd indices are bold + colored highlights
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
