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
                "1. ניווט מהיר, כניסה מהירה ו-USB OTG",
                "• הקש על כל חלק בשורת הנתיב העליונה כדי לקפוץ ישר לתיקיית האב.\n"
                        + "• כניסה מהירה במסך הבית מחזירה אותך מיידית לתיקייה האחרונה.\n"
                        + "• בחיבור דיסק-און-קי (USB OTG) תופיע אוטומטית כרטיסייה לגישה ישירה.");

        addGuideCard(body, theme, textPrimary, accent,
                "2. תפריט המבורגר, בחירה מרובה, שינוי שם ואפליקציות",
                "• כל הכלים מרוכזים בכפתור ההמבורגר למעלה: חיפוש, מיון (שם/יצירה/שינוי/גודל/סוג), יצירה, גזירה, העתקה, דחיסה ומחיקה.\n"
                        + "• בחירה מרובה בלחיצה ארוכה מאפשרת גם שינוי שם אצוותי של קבצים ותיקיות.\n"
                        + "• קטגוריית אפליקציות מחולקת לקובצי APK מול אפליקציות מותקנות (משתמש/מערכת) עם אפשרות גיבוי וצפייה בקוד המקור.");

        addGuideCard(body, theme, textPrimary, accent,
                "3. עורך קוד מתקדם (20+ שפות) וארכיונים מוגני סיסמה",
                "• פתיחת קובצי קוד, קונפיגורציה ו-Markdown עם מספרי שורות מדויקים בגלישה, Undo/Redo, חיפוש והחלפה וזום באצבעות.\n"
                        + "• גיבוי אוטומטי (.bak) נשמר לפני שמירה לשחזור מהיר.\n"
                        + "• צפייה ועריכת קבצים ישירות בתוך ZIP/TAR/TAR.GZ בעץ תיקיות מלא, כולל דחיסה ופתיחה עם סיסמה.");

        addGuideCard(body, theme, textPrimary, accent,
                "4. נגנים, מצב מחוות, אקולייזר, חותכים, GIF וטפט",
                "• בווידאו (מצב מחוות): הקשה כפולה בימין/שמאל לקפיצה של 10 שנ', לחיצה ארוכה בצדדים להרצה ב-2x, והקשה במרכז להסתרה.\n"
                        + "• בשמע: תור ניגון עריכה, טיימר שינה, אקולייזר מובנה והתרעת מדיה נקייה.\n"
                        + "• תמיכה ביצירה ופירוק GIF, תצוגת SVG + קוד מקור, והגדרת טפט בחיתוך.\n"
                        + "• חותך אודיו ווידאו עם פס טווח אחיד ונוח (התחלה+סיום יחד).");

        addGuideCard(body, theme, textPrimary, accent,
                "5. התאמה אישית, תמונת רקע, נטפרי ודיווח תקלות",
                "• בהגדרות ניתן לבחור תמונת רקע מטושטשת לדף הבית ולתיקיות, להציג אייקוני קטגוריות בלבד, לסדר בגרירה את כפתורי הבית, ולבחור מ-12 ערכות נושא ו-16 צבעים.\n"
                        + "• עדכונים מ-GitHub תומכים אוטומטית בתעודת נטפרי (מוגבל מאובטחת לדומייני GitHub בלבד).\n"
                        + "• במסך 'יומן שגיאות' באודות ניתן לסנן לוגים ולשלוח דיווח ל-GitHub עם הסרה מלאה של פרטים אישיים.");

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
