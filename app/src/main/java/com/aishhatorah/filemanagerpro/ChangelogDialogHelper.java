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

/**
 * סעיפים טו, טז, כ: יומן שינויים מעוצב:
 * - כותרת נקייה: "יומן שינויים" ומתחתיה "File Manager Pro" (ללא "מפואר" וללא שורת המשנה הישנה)
 * - גירסה 0.2.5 מסומנת כ-"גירסה 0.2.5 רשמית" (ללא "רשמית" בגרסאות 0.2.2–0.2.4)
 * - בגירסה 0.2.2: "השקת האפליקציה" והסרת המילים "השקת ליבת Java נייטיב מלאה:"
 */
public class ChangelogDialogHelper {

    public static void show(Activity activity) {
        int theme = PrefsManager.getThemeMode(activity);
        int textPrimary = ThemeHelper.getPrimaryTextColor(theme);
        boolean isLight = theme == PrefsManager.THEME_LIGHT;

        int cBlue = isLight ? Color.parseColor("#1D4ED8") : Color.parseColor("#60A5FA");
        int cEmerald = isLight ? Color.parseColor("#047857") : Color.parseColor("#34D399");
        int cViolet = isLight ? Color.parseColor("#6D28D9") : Color.parseColor("#C084FC");
        int cAmber = isLight ? Color.parseColor("#B45309") : Color.parseColor("#FBBF24");
        int cRose = isLight ? Color.parseColor("#BE123C") : Color.parseColor("#FB7185");
        int cCyan = isLight ? Color.parseColor("#0E7490") : Color.parseColor("#22D3EE");

        ScrollView scroll = new ScrollView(activity);
        LinearLayout outer = new LinearLayout(activity);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        outer.setPadding(44, 44, 44, 36);
        outer.setBackground(ThemedDialogHelper.createDialogBackground(theme));

        // סעיף טו: כותרת יומן השינויים — "יומן שינויים" ומתחתיו "File Manager Pro"
        LinearLayout heroBanner = new LinearLayout(activity);
        heroBanner.setOrientation(LinearLayout.VERTICAL);
        heroBanner.setGravity(Gravity.CENTER);
        heroBanner.setPadding(32, 30, 32, 30);

        GradientDrawable heroShape = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.parseColor("#1E3A8A"), Color.parseColor("#4C1D95"), Color.parseColor("#0F766E")}
        );
        heroShape.setCornerRadius(30f);
        heroBanner.setBackground(heroShape);

        ImageView heroIcon = new ImageView(activity);
        heroIcon.setImageResource(R.drawable.ic_app_logo);
        LinearLayout.LayoutParams hIcLp = new LinearLayout.LayoutParams(108, 108);
        hIcLp.bottomMargin = 14;
        heroBanner.addView(heroIcon, hIcLp);

        TextView tvHeader = new TextView(activity);
        tvHeader.setText("יומן שינויים");
        tvHeader.setTextSize(18.5f);
        tvHeader.setTypeface(Typeface.DEFAULT_BOLD);
        tvHeader.setTextColor(Color.WHITE);
        tvHeader.setGravity(Gravity.CENTER);
        tvHeader.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        heroBanner.addView(tvHeader);

        TextView tvSub = new TextView(activity);
        tvSub.setText("File Manager Pro");
        tvSub.setTextSize(14.5f);
        tvSub.setTypeface(Typeface.DEFAULT_BOLD);
        tvSub.setTextColor(Color.parseColor("#E0E7FF"));
        tvSub.setGravity(Gravity.CENTER);
        tvSub.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        tvSub.setPadding(0, 6, 0, 0);
        heroBanner.addView(tvSub);

        LinearLayout.LayoutParams heroLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        heroLp.bottomMargin = 24;
        outer.addView(heroBanner, heroLp);

        // ===================== גירסה 0.2.5 רשמית =====================
        SpannableStringBuilder v25 = new SpannableStringBuilder();
        appendRichBullet(v25, "נגן אודיו משודרג ומותאם לסיבוב מסך: ", cBlue,
                "פס התקדמות משמאל לימין, החלפת מצב חזרה בלחיצה ישירה, אייקונים מותאמים לגודל וסיבוב המסך, עטיפת שיר על כל השטח ומונה קבצים בתיקייה (כגון 1/20).", textPrimary);
        appendRichBullet(v25, "\n\nהתראת מדיה חכמה ושורה תחתונה עם פס התקדמות: ", cViolet,
                "סמלי מדיה בבר ההתראות עם פס התקדמות משמאל לימין וחזרה חכמה לתיקיית השיר המתנגן, ופס התקדמות בשורה החכמה התחתונה ביציאה מהשיר.", textPrimary);
        appendRichBullet(v25, "\n\nתמיכה בכל סוגי המדיה והאפליקציות: ", cEmerald,
                "פתיחה מובנית של כלל פורמטי האודיו, הוידאו והתמונות, התקנת כל סוגי האפליקציות (APK / APKS / APKM / XAPK), ואייקונים ייחודיים לקובצי ZIP וטקסט.", textPrimary);
        appendRichBullet(v25, "\n\nתצוגת תיקיות, מסך ראשי ואייקון Pro חדש: ", cAmber,
                "הצגת שמות קבצים ותיקיות ארוכים במלואם, פתיחת אפשרויות עריכה בלחיצה על האייקון משמאל, שורת שם אפליקציה ומוני קבצים נעים במסך הראשי, וכיתוב Pro בתיקייה הלבנה של האייקון.", textPrimary);
        appendRichBullet(v25, "\n\nהתקנת עדכונים עצמאית ומסך עדכון מעוצב: ", cCyan,
                "חלון עדכון חדש המעוצב בסגנון יומן השינויים עם הורדה והתקנה ישירה ועצמאית בתוך האפליקציה, וקישורים למתמחים טופ ולעוגן פליי באודות.", textPrimary);

        addLuxuriousVersionCard(outer, activity, theme,
                "גירסה 0.2.5 רשמית", "הגירסה הנוכחית והחדשה ביותר",
                Color.parseColor("#1D4ED8"), R.drawable.ic_app_logo, v25);

        // ===================== גירסה 0.2.4 =====================
        SpannableStringBuilder v24 = new SpannableStringBuilder();
        appendRichBullet(v24, "אייקונים אמיתיים לכל קובץ: ", cBlue,
                "חילוץ והצגת עטיפת השיר המוטמעת בקובצי שמע, תצוגה מקדימה לתמונות ולסירטונים, והאייקון המקורי של כל קובץ APK.", textPrimary);
        appendRichBullet(v24, "\n\nמספר קבצים ונפח במסך הבית: ", cEmerald,
                "לצד משקל כל קטגוריה מוצג כעת גם מספר הקבצים המדויק בסוגריים, עם רענון מלא בהחלקה מלמעלה למטה עד לסיום הסריקה.", textPrimary);
        appendRichBullet(v24, "\n\nנגן אודיו מתקדם + מיני-נגן תחתון והתראה פעילה: ", cViolet,
                "ניגון ברקע גם ביציאה מהשיר עם שורה תחתונה חכמה (החלקה על השורה עוצרת את השיר), סמל האפליקציה בבר ההתראות, בחירת מהירות באמצע למטה, חזרה על השיר או התיקייה, העברה לשיר הבא/הקודם ותצוגת זמן מסודרת משמאל.", textPrimary);
        appendRichBullet(v24, "\n\nעורך תמונות מקצועי + הגדלה ביד: ", cAmber,
                "זום חופשי בשתי אצבעות בתמונות ולחצן עורך תמונות מקצועי (עיפרון) עם חיתוך, סיבוב, פילטרים, בהירות/ניגודיות ומכחול ציור.", textPrimary);
        appendRichBullet(v24, "\n\nנגן ועורך וידאו מקצועי: ", cRose,
                "הגדלת הסרטון ביד, סיבוב מסך, הגדרות וידאו מתחת לסגור, מהירות הפעלה, 10 שניות קדימה/אחורה, מעבר בין סרטוני התיקייה ועורך וידאו מקצועי מובנה.", textPrimary);
        appendRichBullet(v24, "\n\nמחוות לחיצה כפולה וכותרת נעה: ", cCyan,
                "לחיצה כפולה בצד ימין (+10) או בצד שמאל (-10) באודיו ובוידאו, ושורת שם קובץ הנעה תדיר למעלה באמצע.", textPrimary);

        addLuxuriousVersionCard(outer, activity, theme,
                "גירסה 0.2.4", "נגן שמע ברקע ועורכי מדיה מקצועיים",
                Color.parseColor("#2563EB"), R.drawable.ic_audio, v24);

        // ===================== גירסה 0.2.3 =====================
        SpannableStringBuilder v23 = new SpannableStringBuilder();
        appendRichBullet(v23, "פתיחה פנימית מלאה באפליקציה: ", cCyan,
                "פתיחת אודיו, תמונות, סרטונים, מסמכי PDF / Office וחילוץ ZIP ללא הזדקקות לאפליקציות חיצוניות.", textPrimary);
        appendRichBullet(v23, "\n\nארכיוני ZIP ויצירה מהירה (+): ", cEmerald,
                "דחיסת קבצים ותיקיות לקובץ ZIP בלחיצה ארוכה, ולחצן + ליצירת תיקייה חדשה או קובץ חדש.", textPrimary);
        appendRichBullet(v23, "\n\nסדר תיקיות בעברית ותצוגת רשת/רשימה: ", cViolet,
                "תיקיות בעברית מופיעות תמיד לפני אנגלית, שליטה בתצוגת רשת או רשימה, אימות כפול בריקון סל המיחזור ועדכון אוטומטי חכם.", textPrimary);

        addLuxuriousVersionCard(outer, activity, theme,
                "גירסה 0.2.3", "מנוע מדיה וארכיונים מובנה",
                Color.parseColor("#7C3AED"), R.drawable.ic_folder, v23);

        // ===================== גירסה 0.2.2 (סעיף טז) =====================
        SpannableStringBuilder v22 = new SpannableStringBuilder();
        appendRichBullet(v22, "", cAmber,
                "מסך בית 4×3 בעברית מלאה, ערכות נושא בהיר / כהה / ניאון, כלי ניתוח הקבצים הכבדים ביותר, התרעת 96% תפוסה וסל מיחזור חכם.", textPrimary);

        addLuxuriousVersionCard(outer, activity, theme,
                "גירסה 0.2.2", "השקת האפליקציה",
                Color.parseColor("#0D9488"), R.drawable.ic_storage_internal, v22);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(scroll)
                .create();

        Button btnClose = ThemedDialogHelper.createThemedButton(activity, "סגור", theme, true, false);
        btnClose.setGravity(Gravity.CENTER);
        btnClose.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        closeLp.topMargin = 8;
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

    private static void appendRichBullet(
            SpannableStringBuilder sb,
            String coloredLead,
            int leadColor,
            String bodyText,
            int bodyColor
    ) {
        int startLead = sb.length();
        sb.append("◆ ").append(coloredLead.replaceFirst("^\\n+", ""));
        if (coloredLead.startsWith("\n\n")) {
            sb.replace(startLead, sb.length(), "\n\n◆ " + coloredLead.substring(2));
            startLead += 2;
        }
        int endLead = sb.length();
        sb.setSpan(new StyleSpan(Typeface.BOLD), startLead, endLead, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new ForegroundColorSpan(leadColor), startLead, endLead, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        int startBody = sb.length();
        sb.append(bodyText);
        sb.setSpan(new ForegroundColorSpan(bodyColor), startBody, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private static void addLuxuriousVersionCard(
            LinearLayout parent,
            Activity activity,
            int theme,
            String versionTag,
            String subtitleBadge,
            int accentColor,
            int iconRes,
            CharSequence richContent
    ) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(32, 28, 32, 28);

        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(28f);
        int innerBg = theme == PrefsManager.THEME_LIGHT
                ? Color.parseColor("#F8FAFC")
                : (theme == PrefsManager.THEME_NEON ? Color.parseColor("#071029") : Color.parseColor("#0F172A"));
        shape.setColor(innerBg);
        shape.setStroke(3, Color.argb(115, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor)));
        card.setBackground(shape);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = 22;
        card.setLayoutParams(lp);

        LinearLayout headerRow = new LinearLayout(activity);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.setPadding(0, 0, 0, 16);

        ImageView iv = new ImageView(activity);
        iv.setImageResource(iconRes);
        LinearLayout.LayoutParams ivLp = new LinearLayout.LayoutParams(64, 64);
        ivLp.setMarginEnd(16);
        headerRow.addView(iv, ivLp);

        LinearLayout titleCol = new LinearLayout(activity);
        titleCol.setOrientation(LinearLayout.VERTICAL);

        TextView tvVer = new TextView(activity);
        tvVer.setText(versionTag);
        tvVer.setTextSize(16.5f);
        tvVer.setTypeface(Typeface.DEFAULT_BOLD);
        tvVer.setTextColor(accentColor);
        titleCol.addView(tvVer);

        TextView tvBadge = new TextView(activity);
        tvBadge.setText(subtitleBadge);
        tvBadge.setTextSize(11.5f);
        tvBadge.setTypeface(Typeface.DEFAULT_BOLD);
        tvBadge.setTextColor(ThemeHelper.getSecondaryTextColor(theme));
        titleCol.addView(tvBadge);

        headerRow.addView(titleCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(headerRow);

        TextView tvBody = new TextView(activity);
        tvBody.setText(richContent);
        tvBody.setTextSize(13.2f);
        tvBody.setLineSpacing(6f, 1.18f);
        card.addView(tvBody);

        parent.addView(card);
    }
}
