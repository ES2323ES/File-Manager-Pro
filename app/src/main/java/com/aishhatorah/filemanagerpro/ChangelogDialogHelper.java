package com.aishhatorah.filemanagerpro;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * סעיפים 7, 35: יומן שינויים נקי ומינימליסטי עם כרטיסיות מתרחבות (Accordion) לפי גירסה.
 * גירסה 0.2.8 פתוחה כברירת מחדל והגרסאות הקודמות נפתחות בהקשה.
 */
public class ChangelogDialogHelper {

    private static int dp(Activity activity, float value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                activity.getResources().getDisplayMetrics()
        ));
    }

    public static void show(Activity activity) {
        int theme = PrefsManager.getThemeMode(activity);
        int textPrimary = ThemeHelper.getPrimaryTextColor(activity, theme);
        int textSecondary = ThemeHelper.getSecondaryTextColor(activity, theme);
        int accent = ThemeHelper.getAccentColor(activity, theme);
        int divider = ThemeHelper.getDividerColor(activity, theme);
        int subtleBg = ThemeHelper.getBgColor(activity, theme);

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        LinearLayout outer = new LinearLayout(activity);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        outer.setPadding(dp(activity, 16), dp(activity, 16), dp(activity, 16), dp(activity, 14));
        outer.setBackground(ThemedDialogHelper.createDialogBackground(activity, theme));

        // כותרת נקייה
        LinearLayout headerRow = new LinearLayout(activity);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.setPadding(dp(activity, 4), 0, dp(activity, 4), dp(activity, 12));

        ImageView heroIcon = new ImageView(activity);
        heroIcon.setImageResource(R.drawable.ic_app_logo);
        int iconPx = dp(activity, 36);
        LinearLayout.LayoutParams hIcLp = new LinearLayout.LayoutParams(iconPx, iconPx);
        hIcLp.setMarginEnd(dp(activity, 10));
        headerRow.addView(heroIcon, hIcLp);

        LinearLayout headerText = new LinearLayout(activity);
        headerText.setOrientation(LinearLayout.VERTICAL);
        TextView tvHeader = new TextView(activity);
        tvHeader.setText("יומן שינויים");
        tvHeader.setTextSize(ThemeHelper.spScaled(activity, 17.5f));
        tvHeader.setTypeface(Typeface.DEFAULT_BOLD);
        tvHeader.setTextColor(textPrimary);
        headerText.addView(tvHeader);

        TextView tvSub = new TextView(activity);
        tvSub.setText("File Manager Pro");
        tvSub.setTextSize(ThemeHelper.spScaled(activity, 13f));
        tvSub.setTextColor(textSecondary);
        headerText.addView(tvSub);

        headerRow.addView(headerText);
        outer.addView(headerRow);

        // ===================== גירסה 0.2.9 רשמית =====================
        SpannableStringBuilder v29 = new SpannableStringBuilder();
        appendBullet(v29, "מנוע וידאו Media3 ExoPlayer ותמיכה מלאה בקבצי MTS / M2TS (AVCHD): ", accent,
                "שדרוג נגן הווידאו למנוע Media3 ExoPlayer עם מנרמל זרם 192-byte BDAV ללא קובץ זמני, סריקת PCR מורחבת לדילוג מדויק ומהיר, יחס תצוגה SAR נכון (16:9), תמונות ממוזערות גם ל-MTS, שליטה מלאה בכל הפורמטים (MTS/M2TS/TS/MKV/AVI/MOV/WebM/MP4) ומסך צפייה נקי ללא כפתור עורך מיותר.", textPrimary);
        appendBullet(v29, "\n\nטעינה מיידית בכל הקטגוריות (פי 50–100 מהר יותר): ", accent,
                "מנוע אינדוקס מהיר מבוסס MediaStore SQLite עם שמירת מטמון מלא של הקטגוריות, חישוב מטא-דאטה מקדים למיון מהיר וביטול סריקות כפולות בחזרה למסך.", textPrimary);
        appendBullet(v29, "\n\nתיקון ניסוח הודעת 'האפליקציה מעודכנת': ", accent,
                "טקסט ברור, מדויק ואחיד בהודעה שהאפליקציה מעודכנת לגרסה האחרונה וטיפול נקי בשגיאות תקשורת זמניות.", textPrimary);

        addCollapsibleVersionCard(outer, activity, true,
                "גירסה 0.2.9 רשמית", "הגירסה הנוכחית • לחץ לצמצום/הרחבה",
                accent, textPrimary, textSecondary, subtleBg, divider, v29);

        // ===================== גירסה 0.2.8 רשמית =====================
        SpannableStringBuilder v28 = new SpannableStringBuilder();
        appendBullet(v28, "שחזור עיצוב נקי, ערכות נושא מותאמות וגודל תצוגה: ", accent,
                "ממשק מינימליסטי ורספונסיבי לכל מסך, ערכות נושא (כולל שחור עמוק AMOLED, אוקיינוס, יער ומערכת דינאמית), בחירת צבע מרכזי וסקאלת גודל תצוגה.", textPrimary);
        appendBullet(v28, "\n\nתפריט המבורגר, בחירה מרובה, Breadcrumbs ושינוי שם אצווה: ", accent,
                "תפריט המבורגר מרוכז עם מיון (כולל תאריך יצירה), סייר נתיב לחיץ, בחירה מרובה עם פעולות שיתוף/העתקה/העברה/מחיקה/שינוי שם אוטומטי והתראות התקדמות.", textPrimary);
        appendBullet(v28, "\n\nעורך קוד מתקדם, Markdown, SVG וקבצי גיבוי .bak: ", accent,
                "מספור שורות, Undo/Redo, חיפוש והחלפה, צביעת תחביר (Java/Kotlin/Python/JSON/XML/JS/TS/SQL/HTML/CSS), עטיפת שורות, תצוגת SVG וקבצי גיבוי אוטומטיים.", textPrimary);
        appendBullet(v28, "\n\nמנהל ארכיונים מלא (ZIP+סיסמה / TAR / TAR.GZ / GZ): ", accent,
                "פתיחה ויצירת ארכיונים במספר פורמטים כולל הצפנה בסיסמה, צפייה פנימית בקבצים בתוך ארכיון, הוספה, עריכה ומחיקת פריטים מתוכו.", textPrimary);
        appendBullet(v28, "\n\nתמיכה מלאה ב-GIF, עורך וידאו Pro, מחוות נגן, אקולייזר, טפט ו-OTG: ", accent,
                "הפעלת GIF מונפש וכלי יצירת/פירוק GIF, טיימר שינה ואקולייזר בנגן, מצב מחוות אוטומטי (הרצה כפולה בלחיצה ארוכה), קביעת טפט בחיתוך, זיהוי USB OTG ומנתח קוד ה-APK.", textPrimary);
        appendBullet(v28, "\n\nתמיכה מאובטחת בנטפרי, יומן אבחון ומטמון מהיר: ", accent,
                "אימות תעודת משתמש מוגבל ל-GitHub בלבד, זיכרון מטמון מהיר לתיקיות, חיבור USB OTG, מדריך למשתמש ודיווח תקלות נקי מפרטים מזהים.", textPrimary);

        addCollapsibleVersionCard(outer, activity, false,
                "גירסה 0.2.8 רשמית", "עיצוב נקי, נטפרי, ארכיונים ועורך קוד מתקדם",
                accent, textPrimary, textSecondary, subtleBg, divider, v28);

        // ===================== גירסה 0.2.7 רשמית =====================
        SpannableStringBuilder v27 = new SpannableStringBuilder();
        appendBullet(v27, "תצוגת מדיה נקייה ומסך מלא בסיבוב: ", accent,
                "הצגה נקייה בהקשה, סיבוב מלא לוידאו, דפדוף בין תמונות, זום עמוק ופס הרצה מהיר בצד ימין.", textPrimary);
        appendBullet(v27, "\n\nהתקנת חבילות APKX / APK+ וזיהוי גירסה חדשה: ", accent,
                "תמיכה בכל חבילות ההתקנה והתרעה כשמותקנת גירסה חדשה יותר במכשיר.", textPrimary);
        addCollapsibleVersionCard(outer, activity, false,
                "גירסה 0.2.7 רשמית", "תצוגת מדיה, פס גלילה מהיר ומתקין אוניברסלי",
                accent, textPrimary, textSecondary, subtleBg, divider, v27);

        // ===================== גירסה 0.2.6 רשמית =====================
        SpannableStringBuilder v26 = new SpannableStringBuilder();
        appendBullet(v26, "תיקיות רשת למדיה ועריכת טקסט בנגיעה: ", accent,
                "תצוגת רשת לקטגוריות תמונות וסירטונים לפי תיקיות, בחירת רשת/רשימה לכל קטגוריה בהגדרות וניקוי כותרות.", textPrimary);
        addCollapsibleVersionCard(outer, activity, false,
                "גירסה 0.2.6 רשמית", "תיקיות רשת ועורך טקסט מהיר",
                accent, textPrimary, textSecondary, subtleBg, divider, v26);

        // ===================== גירסה 0.2.5 רשמית =====================
        SpannableStringBuilder v25 = new SpannableStringBuilder();
        appendBullet(v25, "נגן אודיו משודרג, התראות מדיה ומתקין אוניברסלי: ", accent,
                "פס התקדמות משמאל לימין, מונה שירים בתיקייה, עדכון עצמאי מאפליקציה ואייקוני ZIP/טקסט.", textPrimary);
        addCollapsibleVersionCard(outer, activity, false,
                "גירסה 0.2.5 רשמית", "נגן אודיו, אייקונים מותאמים ועדכונים",
                accent, textPrimary, textSecondary, subtleBg, divider, v25);

        // ===================== גירסה 0.2.4 =====================
        SpannableStringBuilder v24 = new SpannableStringBuilder();
        appendBullet(v24, "אייקונים אמיתיים ומיני-נגן ברקע: ", accent,
                "תמונות ממוזערות לקבצים, עורכי תמונות ווידאו, וניגון שמע ברקע.", textPrimary);
        addCollapsibleVersionCard(outer, activity, false,
                "גירסה 0.2.4", "עורכי מדיה וניגון ברקע",
                accent, textPrimary, textSecondary, subtleBg, divider, v24);

        // ===================== גירסה 0.2.3 & 0.2.2 =====================
        SpannableStringBuilder v22 = new SpannableStringBuilder();
        appendBullet(v22, "גירסה 0.2.3: ", accent,
                "מנוע מדיה וארכיוני ZIP מובנים, תיקיות בעברית תחילה וסל מיחזור מוגן.", textPrimary);
        appendBullet(v22, "\nגירסה 0.2.2: ", accent,
                "השקת האפליקציה — מסך בית בעברית מלאה, ערכות נושא ומנתח אחסון.", textPrimary);
        addCollapsibleVersionCard(outer, activity, false,
                "גירסאות 0.2.2 – 0.2.3", "השקת האפליקציה והתשתית הראשונית",
                accent, textPrimary, textSecondary, subtleBg, divider, v22);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(scroll)
                .create();

        Button btnClose = ThemedDialogHelper.createThemedButton(activity, "סגור", theme, true, false);
        btnClose.setGravity(Gravity.CENTER);
        btnClose.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        closeLp.topMargin = dp(activity, 6);
        btnClose.setLayoutParams(closeLp);
        btnClose.setOnClickListener(v -> dialog.dismiss());
        outer.addView(btnClose);

        scroll.addView(outer);
        dialog.show();
        ThemedDialogHelper.applyResponsiveDialogWindow(activity, dialog);
    }

    private static void appendBullet(
            SpannableStringBuilder sb,
            String coloredLead,
            int leadColor,
            String bodyText,
            int bodyColor
    ) {
        int startLead = sb.length();
        sb.append("• ").append(coloredLead.replaceFirst("^\\n+", ""));
        if (coloredLead.startsWith("\n\n")) {
            sb.replace(startLead, sb.length(), "\n\n• " + coloredLead.substring(2));
            startLead += 2;
        } else if (coloredLead.startsWith("\n")) {
            sb.replace(startLead, sb.length(), "\n• " + coloredLead.substring(1));
            startLead += 1;
        }
        int endLead = sb.length();
        sb.setSpan(new StyleSpan(Typeface.BOLD), startLead, endLead, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new ForegroundColorSpan(leadColor), startLead, endLead, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        int startBody = sb.length();
        sb.append(bodyText);
        sb.setSpan(new ForegroundColorSpan(bodyColor), startBody, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private static void addCollapsibleVersionCard(
            LinearLayout parent,
            Activity activity,
            boolean initiallyExpanded,
            String versionTag,
            String subtitleBadge,
            int accentColor,
            int textPrimary,
            int textSecondary,
            int cardBg,
            int dividerColor,
            CharSequence richContent
    ) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(activity, 13), dp(activity, 11), dp(activity, 13), dp(activity, 11));

        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(dp(activity, 12));
        shape.setColor(cardBg);
        shape.setStroke(2, initiallyExpanded ? accentColor : dividerColor);
        card.setBackground(shape);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(activity, 8);
        card.setLayoutParams(lp);

        LinearLayout headerRow = new LinearLayout(activity);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout titleCol = new LinearLayout(activity);
        titleCol.setOrientation(LinearLayout.VERTICAL);

        TextView tvVer = new TextView(activity);
        tvVer.setText(versionTag);
        tvVer.setTextSize(ThemeHelper.spScaled(activity, 14.5f));
        tvVer.setTypeface(Typeface.DEFAULT_BOLD);
        tvVer.setTextColor(initiallyExpanded ? accentColor : textPrimary);
        titleCol.addView(tvVer);

        TextView tvBadge = new TextView(activity);
        tvBadge.setText(subtitleBadge);
        tvBadge.setTextSize(ThemeHelper.spScaled(activity, 11.5f));
        tvBadge.setTextColor(textSecondary);
        titleCol.addView(tvBadge);

        headerRow.addView(titleCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvChevron = new TextView(activity);
        tvChevron.setText(initiallyExpanded ? "▲" : "▼");
        tvChevron.setTextColor(textSecondary);
        tvChevron.setTextSize(13f);
        tvChevron.setPadding(dp(activity, 6), 0, dp(activity, 2), 0);
        headerRow.addView(tvChevron);

        card.addView(headerRow);

        TextView tvBody = new TextView(activity);
        tvBody.setText(richContent);
        tvBody.setTextSize(ThemeHelper.spScaled(activity, 12.8f));
        tvBody.setLineSpacing(4f, 1.15f);
        tvBody.setPadding(0, dp(activity, 8), 0, 0);
        tvBody.setVisibility(initiallyExpanded ? View.VISIBLE : View.GONE);
        card.addView(tvBody);

        headerRow.setOnClickListener(v -> {
            boolean open = tvBody.getVisibility() != View.VISIBLE;
            tvBody.setVisibility(open ? View.VISIBLE : View.GONE);
            tvChevron.setText(open ? "▲" : "▼");
        });

        parent.addView(card);
    }
}
