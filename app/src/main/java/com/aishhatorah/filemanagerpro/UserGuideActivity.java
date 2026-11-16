package com.aishhatorah.filemanagerpro;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * סעיף 35 + 45: מדריך משתמש ברור, ענייני ומסודר עם כותרות צבעוניות, כפתורי ניווט מהיר
 * והסבר מתומצת על כל הכלים המתקדמים של File Manager Pro v0.3.4.
 */
public class UserGuideActivity extends AppCompatActivity {

    private final List<View> sectionViews = new ArrayList<>();
    private ScrollView mainScroll;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int theme = PrefsManager.getThemeMode(this);
        ThemeHelper.applyWindowStatusBar(this, theme);
        int bg = ThemeHelper.getBgColor(this, theme);
        int cardBg = ThemeHelper.getCardColor(this, theme);
        int textPrimary = ThemeHelper.getPrimaryTextColor(this, theme);
        int textSecondary = ThemeHelper.getSecondaryTextColor(this, theme);
        int accent = ThemeHelper.getAccentColor(this, theme);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackgroundColor(bg);

        // כותרת זהה בעיצובה ליומן השינויים (אייקון אפליקציה + כותרת + תת-כותרת)
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.setPadding(dp(20), dp(16), dp(20), dp(10));

        android.widget.ImageView heroIcon = new android.widget.ImageView(this);
        heroIcon.setImageResource(R.drawable.ic_app_logo);
        int iconPx = dp(36);
        LinearLayout.LayoutParams hIcLp = new LinearLayout.LayoutParams(iconPx, iconPx);
        hIcLp.setMarginEnd(dp(10));
        headerRow.addView(heroIcon, hIcLp);

        LinearLayout headerText = new LinearLayout(this);
        headerText.setOrientation(LinearLayout.VERTICAL);

        TextView tvHeader = new TextView(this);
        tvHeader.setText("מדריך למשתמש");
        tvHeader.setTextSize(ThemeHelper.spScaled(this, 17.5f));
        tvHeader.setTypeface(Typeface.DEFAULT_BOLD);
        tvHeader.setTextColor(textPrimary);
        headerText.addView(tvHeader);

        TextView tvSub = new TextView(this);
        tvSub.setText("File Manager Pro");
        tvSub.setTextSize(ThemeHelper.spScaled(this, 13f));
        tvSub.setTextColor(textSecondary);
        headerText.addView(tvSub);

        headerRow.addView(headerText);
        root.addView(headerRow);

        // סרגל קיצורי קפיצה עליון
        HorizontalScrollView navScroll = new HorizontalScrollView(this);
        navScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout navRow = new LinearLayout(this);
        navRow.setOrientation(LinearLayout.HORIZONTAL);
        navRow.setPadding(18, 12, 18, 12);

        String[] topics = {
                "ניווט ושורת נתיב",
                "תפריט המבורגר ומיון",
                "קוד, Markdown וארכיונים",
                "וידאו, שמע, GIF וטפט",
                "מסך הבית, USB ונטפרי"
        };

        mainScroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(24, 16, 24, 36);

        sectionViews.clear();
        sectionViews.add(addGuideCard(body, cardBg, textPrimary, textSecondary, accent,
                "1. ניווט מהיר, כניסה מהירה ו-USB OTG",
                "• הקש על כל חלק בשורת הנתיב העליונה כדי לקפוץ ישר לתיקיית האב.\n"
                        + "• כניסה מהירה (בסרגל מסך הבית) מחזירה אותך מיידית לתיקייה האחרונה.\n"
                        + "• בחיבור דיסק-און-קי (USB OTG) תופיע אוטומטית כרטיסייה לגישה ישירה."));

        sectionViews.add(addGuideCard(body, cardBg, textPrimary, textSecondary, accent,
                "2. תפריט המבורגר, בחירה מרובה, שינוי שם ואפליקציות",
                "• כל הכלים מרוכזים בכפתור ההמבורגר למעלה: חיפוש, מיון (שם/יצירה/שינוי/גודל/סוג), יצירה, גזירה, העתקה, דחיסה ומחיקה.\n"
                        + "• בחירה מרובה בלחיצה ארוכה מאפשרת גם שינוי שם אצוותי של קבצים ותיקיות.\n"
                        + "• קטגוריית אפליקציות מחולקת לקובצי APK מול אפליקציות מותקנות (משתמש/מערכת) עם אפשרות גיבוי וצפייה בקוד המקור."));

        sectionViews.add(addGuideCard(body, cardBg, textPrimary, textSecondary, accent,
                "3. עורך קוד מתקדם (20+ שפות) וארכיונים מוגני סיסמה",
                "• פתיחת קובצי קוד, קונפיגורציה ו-Markdown עם מספרי שורות מדויקים בגלישה, Undo/Redo, חיפוש והחלפה וזום באצבעות.\n"
                        + "• גיבוי אוטומטי (.bak) נשמר לפני שמירה לשחזור מהיר.\n"
                        + "• צפייה ועריכת קבצים ישירות בתוך ZIP/TAR/TAR.GZ, כולל דחיסה ופתיחה עם סיסמה."));

        sectionViews.add(addGuideCard(body, cardBg, textPrimary, textSecondary, accent,
                "4. נגנים, מצב מחוות, אקולייזר, חותכים, GIF וטפט",
                "• בווידאו (מצב מחוות): הקשה כפולה בימין/שמאל לקפיצה של 10 שנ', לחיצה ארוכה בצדדים להרצה ב-2x, והקשה במרכז להסתרה.\n"
                        + "• בשמע: תור ניגון עריכה, טיימר שינה, אקולייזר מובנה והתרעת מדיה נקייה.\n"
                        + "• תמיכה ביצירה ופירוק GIF, תצוגת SVG + קוד מקור, והגדרת טפט עם מסגור וזום.\n"
                        + "• חותך אודיו ווידאו עם פס טווח אחיד ונוח (התחלה+סיום יחד)."));

        sectionViews.add(addGuideCard(body, cardBg, textPrimary, textSecondary, accent,
                "5. סידור מסך הבית, נטפרי ודיווח תקלות",
                "• בהגדרות ניתן לסדר בגרירה את כפתורי הבית, לשנות גודל תצוגה, לבחור בין נגן פנימי לחיצוני, ולבחור מ-12 ערכות נושא ו-16 צבעים.\n"
                        + "• עדכונים מ-GitHub תומכים אוטומטית בתעודת נטפרי (מוגבל מאובטחת לדומייני GitHub בלבד).\n"
                        + "• במסך 'יומן שגיאות ודיווח' באודות תוכלו לדווח ישירות ב-GitHub עם הסרה מלאה של פרטים אישיים."));

        for (int i = 0; i < topics.length; i++) {
            final int targetIdx = i;
            TextView pill = new TextView(this);
            pill.setText(topics[i]);
            pill.setTextColor(accent);
            pill.setTextSize(12.5f);
            pill.setTypeface(Typeface.DEFAULT_BOLD);
            pill.setPadding(20, 10, 20, 10);
            GradientDrawable shape = new GradientDrawable();
            shape.setCornerRadius(16f);
            shape.setColor(cardBg);
            shape.setStroke(2, accent);
            pill.setBackground(shape);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(10);
            pill.setLayoutParams(lp);
            pill.setOnClickListener(v -> {
                View target = sectionViews.get(targetIdx);
                mainScroll.smoothScrollTo(0, target.getTop());
            });
            navRow.addView(pill);
        }
        navScroll.addView(navRow);
        root.addView(navScroll);

        mainScroll.addView(body);
        root.addView(mainScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        Button btnClose = ThemedDialogHelper.createThemedButton(this, "סגור", theme, true, false);
        btnClose.setGravity(Gravity.CENTER);
        btnClose.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        closeLp.setMargins(dp(16), dp(6), dp(16), dp(12));
        btnClose.setLayoutParams(closeLp);
        btnClose.setOnClickListener(v -> finish());
        root.addView(btnClose);

        setContentView(root);
    }

    private int dp(float value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                getResources().getDisplayMetrics()
        ));
    }

    private View addGuideCard(LinearLayout parent, int cardBg, int textPrimary, int textSecondary, int accent,
                              String title, String content) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(24, 20, 24, 20);
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(18f);
        shape.setColor(cardBg);
        card.setBackground(shape);

        TextView tvHdr = new TextView(this);
        tvHdr.setText(title);
        tvHdr.setTextColor(accent);
        tvHdr.setTextSize(15f);
        tvHdr.setTypeface(Typeface.DEFAULT_BOLD);
        tvHdr.setPadding(0, 0, 0, 10);
        card.addView(tvHdr);

        TextView tvBody = new TextView(this);
        tvBody.setText(content);
        tvBody.setTextColor(textPrimary);
        tvBody.setTextSize(13.5f);
        tvBody.setLineSpacing(5f, 1.2f);
        card.addView(tvBody);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = 14;
        parent.addView(card, lp);
        return card;
    }
}
