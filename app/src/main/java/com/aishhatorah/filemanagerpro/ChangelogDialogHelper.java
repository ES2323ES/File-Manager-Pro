package com.aishhatorah.filemanagerpro;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * יומן שינויים בעיצוב זכוכית צפה מודרני עם חץ חזרה (SVG) בצד שמאל למעלה למסך האודות
 * וכרטיסיות מתרחבות עם אייקוני SVG נקיים.
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
        outer.setPadding(dp(activity, 16), dp(activity, 16), dp(activity, 16), dp(activity, 18));
        outer.setBackground(ThemedDialogHelper.createDialogBackground(activity, theme));

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(scroll)
                .create();

        // שורת כותרת: חץ חזרה (SVG, ללא מסגרת) בצד שמאל למעלה לחזרה למסך האודות
        LinearLayout headerRow = new LinearLayout(activity);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.setPadding(dp(activity, 2), 0, dp(activity, 4), dp(activity, 12));

        ImageButton btnBackTopLeft = ThemeHelper.createBorderlessSvgButton(
                activity, R.drawable.ic_arrow_back, accent, "חזרה לאודות", 38, 8, v -> dialog.dismiss());
        btnBackTopLeft.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        headerRow.addView(btnBackTopLeft);

        LinearLayout rightTitleGroup = new LinearLayout(activity);
        rightTitleGroup.setOrientation(LinearLayout.HORIZONTAL);
        rightTitleGroup.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        rightTitleGroup.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);

        ImageView heroIcon = new ImageView(activity);
        heroIcon.setImageResource(R.drawable.ic_app_logo);
        int iconPx = dp(activity, 34);
        LinearLayout.LayoutParams hIcLp = new LinearLayout.LayoutParams(iconPx, iconPx);
        hIcLp.setMarginEnd(dp(activity, 10));
        rightTitleGroup.addView(heroIcon, hIcLp);

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
        tvSub.setTextSize(ThemeHelper.spScaled(activity, 12.5f));
        tvSub.setTextColor(textSecondary);
        headerText.addView(tvSub);

        rightTitleGroup.addView(headerText);
        LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        rightLp.setMarginStart(dp(activity, 8));
        headerRow.addView(rightTitleGroup, rightLp);
        outer.addView(headerRow);

        // ===================== גירסה 0.3.7.1 =====================
        SpannableStringBuilder v371 = new SpannableStringBuilder();
        appendBullet(v371, "תיקון מלא של נגן האודיו והשמע: ", accent,
                "ביטול חסימת הניגון הכפויה במצבי \"נא לא להפריע\"/השתקה ב-AudioPlayerManager ובנגן הווידאו, ותיקון קריאת setPlaybackParams ב-MediaPlayer לפני תחילת הניגון.", textPrimary);
        appendBullet(v371, "\n\nתיקון תקיעות נגינת וידאו MTS/M2TS: ", accent,
                "החזרת הדגל FLAG_ALLOW_NON_IDR_KEYFRAMES ב-SeekableTsExtractor, תיקון לחיצת הפעלה בזמן הכנת הווידאו והפעלת מנגנון הגיבוי של MTS, ואיזון חוצץ הקריאה וההשמעה לפתיחה ודילוג חלקים.", textPrimary);

        addCollapsibleVersionCard(outer, activity, true,
                "גירסה 0.3.7.1", "הגירסה הנוכחית • תיקון אודיו ווידאו MTS",
                accent, textPrimary, textSecondary, subtleBg, divider, v371);

        // ===================== גירסה 0.3.7 =====================
        SpannableStringBuilder v37 = new SpannableStringBuilder();
        appendBullet(v37, "תיקון יסודי של נגינת וידאו MTS/M2TS (מהשורש): ", accent,
                "מניעת סימון פריימי P/B כ-Keyframe ב-SeekableTsExtractor, שמירת סדר תצוגה של B-frames עם הזרקת תיבת ctts ומיפוי שמע MP2/AC-3/LPCM בחותך ובמנרמל, הגדלת מאגר ה-TS ל-50 שניות וביטול פיענוח מקבילי שגרם לגמגומים.", textPrimary);
        appendBullet(v37, "\n\nמיון טבעי (מספרים ואז עברית), ראש תיקייה ופס גלילה 1.5 שניות: ", accent,
                "תיקיות וקבצים המתחילים במספר ממוינים לפני עברית ואנגלית בסדר מספרי טבעי (2 לפני 10), כניסה לתיקייה נפתחת תמיד מראש הרשימה, וקו ההרצה בצד ימין מופיע בגלילה ונעלם אוטומטית לאחר 1.5 שניות.", textPrimary);
        appendBullet(v37, "\n\nחזרה מהשורה החכמה לתיקיית האודיו, תמונות רשת וכרטיסיות אחסון: ", accent,
                "יציאה ממסך האודיו שנפתח דרך השורה החכמה מחזירה ישירות לתיקיית השמע שממנה נוגן הקובץ ומשם אחורה צעד-אחר-צעד, שוחזרו תצוגת הפריט הראשון ועיגול הניגון התכלת בתיקיות תמונות/סירטונים ולשונית ההוצאה הבטוחה בלחיצה על האחסון החיצוני.", textPrimary);
        appendBullet(v37, "\n\nוילונות צד מזכוכית, עורך קוד ותמונות מחודש ושיפורי ממשק (PR #3): ", accent,
                "וילונות צד מונפשים לתפריטי המבורגר, בחירה מרובה בהחלקה לצד, תצוגת Markdown כברירת מחדל וחיפוש/החלפה משולב בעורך הקוד, נקודות 5% בפסי התקדמות, סיבוב תור גרירה באודיו, וסידור פורמט הגירסה במסך האודות.", textPrimary);

        addCollapsibleVersionCard(outer, activity, false,
                "גירסה 0.3.7", "מיון טבעי, שדרוגי PR #3 ושיפורי ממשק",
                accent, textPrimary, textSecondary, subtleBg, divider, v37);

        // ===================== גירסה 0.3.6 =====================
        SpannableStringBuilder v36 = new SpannableStringBuilder();
        appendBullet(v36, "חיפוש מיידי מבוסס SQLite (WAL) בכל האחסון והכוננים: ", accent,
                "מנוע אינדוקס מקומי FileIndexDatabase לחיפוש מהיר בקובצי המכשיר, כרטיס SD ו-USB OTG.", textPrimary);
        appendBullet(v36, "\n\nשילוב USB OTG, מצב PiP וייצוב נגן TS/MTS: ", accent,
                "תמיכה ב-Picture-in-Picture לווידאו, תיקון זיהוי וייצוא MTS/M2TS, כפתור איפוס לטשטוש הרקע והצגת מונה זמן בשורה החכמה.", textPrimary);

        addCollapsibleVersionCard(outer, activity, false,
                "גירסה 0.3.6", "אינדקס חיפוש SQLite, PiP ושיפורי MTS/OTG",
                accent, textPrimary, textSecondary, subtleBg, divider, v36);

        // ===================== גירסה 0.3.5 =====================
        SpannableStringBuilder v35 = new SpannableStringBuilder();
        appendBullet(v35, "רקע מותאם אישית, ממשק זכוכית שקופה וערכות נושא מאוזנות: ", accent,
                "בחירת תמונת רקע עם שליטה מדויקת בעוצמת הטשטוש והבהירות, כותרות זכוכית שקופה מעוגלות בכל המסכים, טבלה מאוזנת של 12 ערכות נושא ב-3 שורות אחידות, וכפתור איפוס מהיר לגודל הטקסט תוך שמירה על מיקום הגלילה.", textPrimary);
        appendBullet(v35, "\n\nשדרוג פסי התקדמות, נגני מדיה ומסכי צפייה: ", accent,
                "פסי התקדמות עבים ומעוגלים עם סמן עומד מיושר, גלי תנועה חלקים הנעים לאחור בנגני הווידאו והשמע, מציג תמונות על כל המסך עם אייקונים נקיים מעל רקע שקוף למחצה, כותרת וידאו המותאמת לשורת הסטטוס, תצוגות הרצה 2x ו-10 שניות נקיות ללא אימוג'ים, ומציג PDF מוגדל עם חיצי דפדוף.", textPrimary);
        appendBullet(v35, "\n\nעורך ודחיסת וידאו, חותכי מדיה וניהול קבצים מתקדם: ", accent,
                "הוספת אפשרויות הפחתת משקל והמרת איכות וידאו בעורך ובחותך הווידאו, בחירת קובצי שמע ווידאו לחיתוך ישירות מתוך קטגוריות האפליקציה, מציג ארכיונים נקי עם בקשת סיסמה אוטומטית ואייקון שיתוף, מנתח מבנה APK, ואייקון דחיסה אחיד ומאוזן בסרגל הבחירה המרובה.", textPrimary);
        appendBullet(v35, "\n\nתיקוני יציבות, גודל אייקונים ודיוק סורק הקבצים: ", accent,
                "הגדלת אייקוני הבית והקטגוריות, שיפור ביצועי דחיסת קבצים ומניעת חריגות זיכרון בארכיונים גדולים, הוספת .nomedia לסל המיחזור, דיוק מונה הנפחים בסורק האחסון, ושדרוג הדיאלוגים ומדריך המשתמש.", textPrimary);

        addCollapsibleVersionCard(outer, activity, false,
                "גירסה 0.3.5", "עיצוב זכוכית, ערכות נושא, דחיסת וידאו ושיפורי ממשק",
                accent, textPrimary, textSecondary, subtleBg, divider, v35);

        // ===================== גירסה 0.3.4 =====================
        SpannableStringBuilder v34 = new SpannableStringBuilder();
        appendBullet(v34, "התאמת צבעים מלאה וצבעים וערכות נושא חדשות: ", accent,
                "12 ערכות נושא ו-16 צבעים לבחירה בהגדרות; התאמת צבע אייקוני התיקיות, המתגים, הכניסה המהירה ושאר האייקונים הרלוונטיים לצבע שנבחר (תוך שמירה על לוגו האפליקציה המקורי).", textPrimary);
        appendBullet(v34, "\n\nרשימות ותצוגת תיקיות נקייה וללא ריבועים: ", accent,
                "הסרת המלבנים סביב התיקיות והחלפתם בפסים דקים להפרדה בכל תצוגות הרשימה והרשת, תיקון מניעת חפיפה של שורות בגלילה מהירה, ואייקוני וידאו/תמונה לתיקיות מקובצות בקטגוריות סירטונים ותמונות.", textPrimary);
        appendBullet(v34, "\n\nנגני מדיה, מחוות החלקה והתקדמות העתקה: ", accent,
                "תפריט + ומהירות נקיים ללא ריבועים, עידון הגלים בפס ההתקדמות והפיכתו לפס רציף בעצירה, החלקה אופקית להרצת שניות עם מונה נקי במרכז, החלקה אנכית באודיו למעבר בין רצועות, השורה החכמה מותאמת לעטיפת האודיו, פתיחת וידאו במצב מוכן עם כפתור ההפעלה התכלת במרכז, וחלון התקדמות ברור להעתקה והעברה.", textPrimary);
        appendBullet(v34, "\n\nחיפוש משופר, התראות וליטושים: ", accent,
                "חיפוש לפי כל ספרה, אות או מחרוזת מכל מקום בשם הקובץ כולל תת-תיקיות, שמירת חיפושים אחרונים עם אייקון חיפוש חוזר, שימוש באייקון האפליקציה בהתראות, ותיקון נוסח הודעת העדכון.", textPrimary);

        addCollapsibleVersionCard(outer, activity, false,
                "גירסה 0.3.4", "התאמת צבעים מלאה, רשימות נקיות ונגנים משודרגים",
                accent, textPrimary, textSecondary, subtleBg, divider, v34);

        // ===================== גירסה 0.3.3 =====================
        SpannableStringBuilder v33 = new SpannableStringBuilder();
        appendBullet(v33, "מסך אודיו מחודש ואחיד המותאם לעטיפת השמע: ", accent,
                "רקע אחיד ונקי ללא מלבנים סביב הכותרת, העטיפה או הכלים; התאמת צבעי המסך והנגן אוטומטית לצבעי עטיפת האלבום (כאשר קיימת); שם קובץ יציב ללא ריצוד; כפתורי חזרה ושיתוף באייקון בלבד ללא מסגרות; זמן נוכחי, קו מפריד ואורך כולל בצבע אחיד.", textPrimary);
        appendBullet(v33, "\n\nפס התקדמות בצורת גלים (באודיו ובווידאו) וארגון מחדש של כפתורי השמע: ", accent,
                "סרגל גלי חלק ורספונסיבי בשני הנגנים, הסרת העיגולים סביב פקדי השליטה והחזרה, ושורה תחתונה נקייה עם אייקון + בצד ימין (תור, טיימר שינה, אקולייזר וחותך אודיו), אייקון מהירות בלבד במרכז ואייקון חזרה בצד שמאל.", textPrimary);
        appendBullet(v33, "\n\nליטוש מדריך למשתמש ומסך הבית: ", accent,
                "כותרת אחידה במדריך למשתמש בסגנון יומן השינויים, והסרת הריבועים סביב אייקוני ההגדרות והאודות במסך הראשי.", textPrimary);

        addCollapsibleVersionCard(outer, activity, false,
                "גירסה 0.3.3", "מסך אודיו מותאם לעטיפה, פס גלים ועדכוני ממשק",
                accent, textPrimary, textSecondary, subtleBg, divider, v33);

        // ===================== גירסה 0.3.2 =====================
        SpannableStringBuilder v32 = new SpannableStringBuilder();
        appendBullet(v32, "תמונה צלולה, חלקה וללא ריצודים בכל פורמט כולל MTS: ", accent,
                "שדרוג תצוגת החומרה (RGBA_8888 + מטריצת GPU אחידה), מאגרי טעינה יציבים ב-ExoPlayer, קריאה מהירה מוגדלת מזרם ה-MTS (384KB), ניטור אוטומטי של דילוג פריימים, וביטול ריענון מיותר של סרגלי הווידאו למניעת הבהובים וריצודים.", textPrimary);
        appendBullet(v32, "\n\nזום חד מכל נקודה במסך (עד פי 20): ", accent,
                "צביטה והגדלה חדה מכל פינה וצד בסרטון (ולא רק מהמרכז) תוך שמירה על חדות מקסימלית וללא פגיעה באיכות התמונה.", textPrimary);
        appendBullet(v32, "\n\nממשק מסך וידאו משודרג ונקי: ", accent,
                "הסתרת פקדים אוטומטית עם פתיחת הווידאו והחזרה בהקשה; כותרת יציבה ומלאה למעלה ללא ריצוד; עיגול הפעלה תכלת יחיד במרכז; כפתור נעילה מעל פס ההתקדמות ושחרור בתחתית המסך; אייקונים נקיים ללא טקסט ומסגרות (כולל חזרה, אפשרויות ומהירות), וסידור כפתורי ניווט, סיבוב מסך, השמעה אקראית, מתיחה והתאמה למסך, קפיצות 10 שניות וחזרה.", textPrimary);

        addCollapsibleVersionCard(outer, activity, false,
                "גירסה 0.3.2", "וידאו צלול ללא ריצוד, זום מכל צד וממשק וידאו משופר",
                accent, textPrimary, textSecondary, subtleBg, divider, v32);

        // ===================== גירסה 0.3.1 =====================
        SpannableStringBuilder v31 = new SpannableStringBuilder();
        appendBullet(v31, "חותך הווידאו תומך כעת במלואו בקבצי MTS / M2TS / TS: ", accent,
                "מנוע המרה וחיתוך מובנה (Pure Java ללא תלויות חיצוניות) המאפשר בחירה, תצוגה מקדימה וחיתוך חלק של קבצי MTS ל-MP4 תקני ישירות מכל טווח זמן, כולל שמירה על אודיו מסונכרן.", textPrimary);
        appendBullet(v31, "\n\nתיקון יסודי של פס ההתקדמות וקפיצות חוזרות להתחלה ב-MTS: ", accent,
                "שמירת משך הסרטון ואמידת אורך עמידה גם כשחותמות PCR מתאפסות באמצע הקובץ, הסרת פיצול שדות שפגע בפריימים שזורים, ומנגנון נרמול אוטומטי ל-MP4 זמני חלק בכל מקרה של זרם עם איפוסי PTS חוזרים.", textPrimary);

        addCollapsibleVersionCard(outer, activity, false,
                "גירסה 0.3.1", "חותך וידאו MTS ותיקון פס ההתקדמות",
                accent, textPrimary, textSecondary, subtleBg, divider, v31);

        // ===================== גירסה 0.3.0 =====================
        SpannableStringBuilder v30 = new SpannableStringBuilder();
        appendBullet(v30, "תיקון מלא לקבצי וידאו MTS / M2TS ולפס ההתקדמות: ", accent,
                "זיהוי מורחב של זרמי 192-byte BDAV ו-204-byte, תיעדוף ישיר של מחלץ MPEG-TS למניעת שגיאות קריאה או בקשות למפענח חיצוני, חישוב אורך ודילוג חלק מיושר לפאקטים בפס ההתקדמות והתאוששות אוטומטית מקודק שמע לא נתמך.", textPrimary);
        appendBullet(v30, "\n\nסרגל עליון נקי באייקונים בלבד: ", accent,
                "כפתורי ההגדרות והאודות בצדדים מוצגים עם אייקון בלבד וללא טקסט מיותר.", textPrimary);

        addCollapsibleVersionCard(outer, activity, false,
                "גירסה 0.3.0", "זיהוי MTS מתקדם ואייקונים נקיים",
                accent, textPrimary, textSecondary, subtleBg, divider, v30);

        // ===================== גירסה 0.2.9 =====================
        SpannableStringBuilder v29 = new SpannableStringBuilder();
        appendBullet(v29, "מנוע וידאו Media3 ExoPlayer ותמיכה מלאה בקבצי MTS / M2TS (AVCHD): ", accent,
                "שדרוג נגן הווידאו למנוע Media3 ExoPlayer עם מנרמל זרם 192-byte BDAV ללא קובץ זמני, סריקת PCR מורחבת לדילוג מדויק ומהיר, יחס תצוגה SAR נכון (16:9), תמונות ממוזערות גם ל-MTS, שליטה מלאה בכל הפורמטים (MTS/M2TS/TS/MKV/AVI/MOV/WebM/MP4) ומסך צפייה נקי ללא כפתור עורך מיותר.", textPrimary);
        appendBullet(v29, "\n\nטעינה מיידית בכל הקטגוריות (פי 50–100 מהר יותר): ", accent,
                "מנוע אינדוקס מהיר מבוסס MediaStore SQLite עם שמירת מטמון מלא של הקטגוריות, חישוב מטא-דאטה מקדים למיון מהיר וביטול סריקות כפולות בחזרה למסך.", textPrimary);
        appendBullet(v29, "\n\nתיקון ניסוח הודעת 'האפליקציה מעודכנת': ", accent,
                "טקסט ברור, מדויק ואחיד בהודעה שהאפליקציה מעודכנת לגרסה האחרונה וטיפול נקי בשגיאות תקשורת זמניות.", textPrimary);

        addCollapsibleVersionCard(outer, activity, false,
                "גירסה 0.2.9", "נגן Media3, טעינה מהירה ותיקון בדיקת עדכונים",
                accent, textPrimary, textSecondary, subtleBg, divider, v29);

        // ===================== גירסה 0.2.8 =====================
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
                "גירסה 0.2.8", "עיצוב נקי, נטפרי, ארכיונים ועורך קוד מתקדם",
                accent, textPrimary, textSecondary, subtleBg, divider, v28);

        // ===================== גירסה 0.2.7 =====================
        SpannableStringBuilder v27 = new SpannableStringBuilder();
        appendBullet(v27, "תצוגת מדיה נקייה ומסך מלא בסיבוב: ", accent,
                "הצגה נקייה בהקשה, סיבוב מלא לוידאו, דפדוף בין תמונות, זום עמוק ופס הרצה מהיר בצד ימין.", textPrimary);
        appendBullet(v27, "\n\nהתקנת חבילות APKX / APK+ וזיהוי גירסה חדשה: ", accent,
                "תמיכה בכל חבילות ההתקנה והתרעה כשמותקנת גירסה חדשה יותר במכשיר.", textPrimary);
        addCollapsibleVersionCard(outer, activity, false,
                "גירסה 0.2.7", "תצוגת מדיה, פס גלילה מהיר ומתקין אוניברסלי",
                accent, textPrimary, textSecondary, subtleBg, divider, v27);

        // ===================== גירסה 0.2.6 =====================
        SpannableStringBuilder v26 = new SpannableStringBuilder();
        appendBullet(v26, "תיקיות רשת למדיה ועריכת טקסט בנגיעה: ", accent,
                "תצוגת רשת לקטגוריות תמונות וסירטונים לפי תיקיות, בחירת רשת/רשימה לכל קטגוריה בהגדרות וניקוי כותרות.", textPrimary);
        addCollapsibleVersionCard(outer, activity, false,
                "גירסה 0.2.6", "תיקיות רשת ועורך טקסט מהיר",
                accent, textPrimary, textSecondary, subtleBg, divider, v26);

        // ===================== גירסה 0.2.5 =====================
        SpannableStringBuilder v25 = new SpannableStringBuilder();
        appendBullet(v25, "נגן אודיו משודרג, התראות מדיה ומתקין אוניברסלי: ", accent,
                "פס התקדמות משמאל לימין, מונה שירים בתיקייה, עדכון עצמאי מאפליקציה ואייקוני ZIP/טקסט.", textPrimary);
        addCollapsibleVersionCard(outer, activity, false,
                "גירסה 0.2.5", "נגן אודיו, אייקונים מותאמים ועדכונים",
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
        int theme = PrefsManager.getThemeMode(activity);
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(activity, 13), dp(activity, 11), dp(activity, 13), dp(activity, 11));
        card.setBackground(ThemeHelper.createGlassCardBackground(activity, theme, 14f, true, initiallyExpanded));

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

        ImageView ivChevron = new ImageView(activity);
        ivChevron.setImageResource(initiallyExpanded ? R.drawable.ic_chevron_up : R.drawable.ic_chevron_down);
        ivChevron.setColorFilter(initiallyExpanded ? accentColor : textSecondary);
        int chevPx = dp(activity, 22);
        LinearLayout.LayoutParams chevLp = new LinearLayout.LayoutParams(chevPx, chevPx);
        chevLp.setMarginStart(dp(activity, 6));
        headerRow.addView(ivChevron, chevLp);

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
            ivChevron.setImageResource(open ? R.drawable.ic_chevron_up : R.drawable.ic_chevron_down);
            ivChevron.setColorFilter(open ? accentColor : textSecondary);
        });

        parent.addView(card);
    }
}
