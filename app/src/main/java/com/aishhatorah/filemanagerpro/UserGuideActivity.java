package com.aishhatorah.filemanagerpro;

import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/**
 * מדריך למשתמש בעיצוב זכוכית אטומה ונוחה לקריאה מעל רקע מטושטש,
 * עם כותרת מעוגלת כלפי מעלה, מקש חזרה באייקון SVG בלבד ומספר גירסה אמיתי.
 */
public class UserGuideActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int theme = PrefsManager.getThemeMode(this);
        ThemeHelper.applyWindowStatusBar(this, theme);
        int textPrimary = ThemeHelper.getPrimaryTextColor(this, theme);
        int textSecondary = ThemeHelper.getSecondaryTextColor(this, theme);
        int accent = ThemeHelper.getAccentColor(this, theme);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        ThemeHelper.applyWallpaperBackdrop(this, root, theme);

        // כותרת מעוגלת כלפי מעלה ללא תמונת רקע בתוכה
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(10), dp(10), dp(14), dp(12));
        ThemeHelper.applyCurvedHeaderStyle(this, topBar, theme);

        ImageButton btnBack = ThemeHelper.createBorderlessSvgButton(
                this, R.drawable.ic_arrow_back, accent, "חזרה", 40, 8, v -> finish());
        topBar.addView(btnBack);

        ImageView heroIcon = new ImageView(this);
        heroIcon.setImageResource(R.drawable.ic_app_logo);
        int iconPx = dp(32);
        LinearLayout.LayoutParams hIcLp = new LinearLayout.LayoutParams(iconPx, iconPx);
        hIcLp.setMarginStart(dp(6));
        hIcLp.setMarginEnd(dp(10));
        topBar.addView(heroIcon, hIcLp);

        LinearLayout headerText = new LinearLayout(this);
        headerText.setOrientation(LinearLayout.VERTICAL);

        TextView tvHeader = new TextView(this);
        tvHeader.setText("מדריך למשתמש");
        tvHeader.setTextSize(ThemeHelper.spScaled(this, 17f));
        tvHeader.setTypeface(Typeface.DEFAULT_BOLD);
        tvHeader.setTextColor(textPrimary);
        headerText.addView(tvHeader);

        TextView tvSub = new TextView(this);
        tvSub.setText("File Manager Pro " + BuildConfig.VERSION_NAME);
        tvSub.setTextSize(ThemeHelper.spScaled(this, 12.5f));
        tvSub.setTextColor(textSecondary);
        headerText.addView(tvSub);

        topBar.addView(headerText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(topBar);

        ScrollView mainScroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(14), dp(12), dp(14), dp(24));

        addGuideCard(body, theme, textPrimary, accent,
                "1. ניווט מהיר, כניסה מהירה, USB OTG ונפחי תיקיות",
                "• הקש על כל חלק בשורת הנתיב העליונה כדי לקפוץ ישר לתיקיית האב.\n"
                        + "• כניסה מהירה במסך הבית מחזירה אותך מיידית לתיקייה האחרונה.\n"
                        + "• בחיבור כונן USB OTG או כרטיס SD תופיע כרטיסייה מתאימה; בלחיצה ארוכה ייפתח תפריט צף לפתיחה או להוצאה בטוחה מבלי להזיז את הסמלים.\n"
                        + "• צורת המיון שתבחר בתפריט נשמרת לכל תיקייה בנפרד; שינוי ברירת המחדל לכל המכשיר מתבצע במסך ההגדרות.");

        addGuideCard(body, theme, textPrimary, accent,
                "2. בחירה מרובה, וילון נגרר, ארכיונים ואפליקציות",
                "• לחיצה ארוכה על קובץ או החלקה לצד מכניסות למצב בחירה מרובה; את הווילון התחתון ניתן להרחיב בהחלקה למעלה ולצמצם בהחלקה למטה.\n"
                        + "• קטגוריית אפליקציות מחולקת לקובצי התקנה (APK/APKS/XAPK) ולאפליקציות מותקנות (משתמש/מערכת), עם גיבוי מלא גם של אפליקציות מפוצלות לחבילת .apks ושיתוף מהיר.\n"
                        + "• בלחיצה על קובץ APK תיפתח חלונית פרטים מסודרת עם כפתור 'הצג מקור' הפותח ישירות את עץ הקבצים (AndroidManifest.xml, XML ו-DEX) בלחיצה.\n"
                        + "• צפייה, עריכה חיה וחילוץ של ZIP/TAR/TAR.GZ/7Z/RAR, כולל דחיסה ופתיחה מוגנת סיסמה.");

        addGuideCard(body, theme, textPrimary, accent,
                "3. מציג PDF צף, מסמכים כבדים ועורך קוד (20+ שפות)",
                "• מציג ה-PDF נפתח בתצוגה מלאה עם סרגלים צפים שמוסתרים וחוזרים בלחיצה במרכז המסך; דפדפו אנכית או אופקית בהתאם להגדרות המחוות והאנימציה.\n"
                        + "• בפתיחת קובצי טקסט, קוד, PDF או Office גדולים מוצגת טעינה שקטה עם כפתור 'ביטול' צף ושקוף.\n"
                        + "• עורך הקוד כולל מספרי שורות, Undo/Redo, חיפוש והחלפה, זום באצבעות, תצוגת Markdown וגיבוי אוטומטי (.bak).");

        addGuideCard(body, theme, textPrimary, accent,
                "4. נגנים, בועת המוזיקה, מצב מחוות, GIF וחותכים",
                "• מציגי התמונות והווידאו פועלים בסגנון צף נקי על כל המסך; ניתן לשלוט בנפרד בכל מחווה בהגדרות (קפיצת 10 שנ', הרצה 2x בהחזקה, מעבר תמונות, זום ודפדוף PDF).\n"
                        + "• החליקו כלפי מעלה מבועת המוזיקה התחתונה לפתיחה רציפה של הנגן המלא, נהלו תור בגרירה, וקבעו בהגדרות אם כפתור החזרה ישוב למסך הקודם או לתיקיית השיר.\n"
                        + "• תמיכה בפירוק ויצירת GIF, תצוגת SVG + קוד מקור, הגדרת טפט בחיתוך, חותכי שמע/וידאו ודחיסת וידאו.");

        addGuideCard(body, theme, textPrimary, accent,
                "5. הגדרות באקורדיון, סידור בגרירה, נטפרי ודיווח תקלות",
                "• מסך ההגדרות מחולק לקטגוריות נפתחות ונוחות (מראה ורקע, מדיה ומחוות, סידור קטגוריות הבית, תצוגה ומיון, ברירות מחדל, אחסון וסל מחזור, עדכונים ואבחון).\n"
                        + "• בתוך 'סידור קטגוריות במסך הבית' ניתן לגרור ולהזיז כל אריח ואת קו ההפרדה האדום ביד חופשית, וכן להציג/להסתיר שמות קטגוריות.\n"
                        + "• ניתן להפעיל תמונות ממוזערות לתיקיות והצגת נפחי תיקיות בתת-תפריט 'תצוגה ומיון'.\n"
                        + "• עדכונים מ-GitHub תומכים בתעודת נטפרי (מוגבל מאובטחת לדומייני GitHub בלבד), וביומן השגיאות ניתן לסנן ולשלוח דיווח נקי מפרטים אישיים.");

        mainScroll.addView(body);
        root.addView(mainScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    private int dp(float value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                getResources().getDisplayMetrics()
        ));
    }

    private void addGuideCard(LinearLayout parent, int theme, int textPrimary, int accent,
                              String title, String content) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(ThemeHelper.createGlassCardBackground(this, theme, 16f, true, false));
        card.setElevation(dp(2));

        TextView tvHdr = new TextView(this);
        tvHdr.setText(title);
        tvHdr.setTextColor(accent);
        tvHdr.setTextSize(ThemeHelper.spScaled(this, 15f));
        tvHdr.setTypeface(Typeface.DEFAULT_BOLD);
        tvHdr.setPadding(0, 0, 0, dp(6));
        card.addView(tvHdr);

        TextView tvBody = new TextView(this);
        tvBody.setText(content);
        tvBody.setTextColor(textPrimary);
        tvBody.setTextSize(ThemeHelper.spScaled(this, 13.5f));
        tvBody.setLineSpacing(dp(2), 1.2f);
        card.addView(tvBody);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(10);
        parent.addView(card, lp);
    }
}
