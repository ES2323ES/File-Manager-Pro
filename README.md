<div dir="rtl" align="right">

# File Manager Pro — מנהל קבצים מתקדם לאנדרואיד (Java Native)

[![Build Signed APK & Publish Release](https://github.com/ES2323ES/File-Manager-Pro/actions/workflows/release.yml/badge.svg)](https://github.com/ES2323ES/File-Manager-Pro/actions/workflows/release.yml)

**גירסה נוכחית:** `0.2.2` | **שפת פיתוח:** `100% Native Java` | **מפתח:** [אש התורה — מתמחים טופ](https://mitmachim.top/user/%D7%90%D7%A9-%D7%94%D7%AA%D7%95%D7%A8%D7%94) · [פרופיל GitHub](https://github.com/ES2323ES)

---

## הורדה ישירה מהמאגר (APK + ZIP)

ניתן להוריד את הגירסה העדכנית ביותר ישירות מכאן או מלשונית **Releases** בצד הימני של המאגר (`V1`, `V2`, `V3`):

- **[הורדת קובץ התקנה ישיר לאנדרואיד (File-Manager-Pro.apk)](../../releases/latest/download/File-Manager-Pro.apk)**
- **[הורדת פרויקט המקור המלא בקובץ ארכיון (ZIP)](../../archive/refs/heads/main.zip)**
- **[מעבר לכל הגרסאות והעדכונים (Releases: V1 / V2 / V3)](../../releases)**

---

## תכונות מרכזיות בגירסה 0.2.2

1. **פיתוח נייטיב מלא ב-Java (ללא פשרות):**
   מבוסס על Android SDK 34 ו-Java 17 עם ביצועים מהירים, צריכת זיכרון מזערית וממשק עברי מוקפד.
2. **מסך בית מסודר ב-4 שורות × 3 עמודות (משמאל לימין):**
   - **שורה ראשונה:** אחסון ראשי · כרטיס SD · הורדות
   - **שורה שנייה:** תמונות · שמע · סירטונים
   - **שורה שלישית:** מסמכים · אפליקציות · קבצים חדשים
   - **שורה רביעית:** סל מיחזור · חותך אודיו · חותך וידאו
3. **3 ערכות תאורה לבחירה בהגדרות:**
   מעבר מיידי בין מצב **בהיר**, **כהה**, ו-**ניאון** (כחול-טורקיז חשמלי בניגודיות גבוהה).
4. **ניתוח משקל קבצים והתרעת 96% תפוסה:**
   כלי **ניתוח** מתקדם בהגדרות לאיתור הקבצים הכבדים ביותר במכשיר, כולל התרעה אוטומטית כאשר הזיכרון הפנימי או כרטיס ה-SD מגיעים ל-**96%** תפוסה.
5. **חותך אודיו וחותך וידאו מובנים (Lossless):**
   חיתוך מהיר ומדויק של קובצי שמע ווידאו באמצעות `MediaExtractor` ו-`MediaMuxer` ישירות מהמכשיר וללא איבוד איכות.
6. **סל מיחזור חכם ועריכת קבצים מלאה:**
   העתקה, העברה, שינוי שם, מחיקה לסל מיחזור ושחזור בלחיצה, רענון בהחלקה מלמעלה למטה (`SwipeRefreshLayout`) והרשאות מלאות לכל סוגי המדיה והקבצים.
7. **מנגנון עדכונים אוטומטי מ-GitHub ומפתח חתימה קבוע:**
   בדיקת עדכונים בלחיצה מתוך חלון **אודות**, הורדה ישירה והתקנה חלקה הודות לחתימה דיגיטלית קבועה (`PKCS#12 RSA-2048`) המאפשרת שדרוג רציף בין `V1`, `V2`, `V3` ללא מחיקת נתונים.

---

## מבנה הפרויקט

```text
File-Manager-Pro/
├── .github/workflows/release.yml       # בנייה אוטומטית של APK + ZIP ויצירת Releases (V1, V2, V3)
├── keystore/file-manager-pro-release.p12 # מפתח חתימה קבוע לעדכונים רציפים
├── app/
│   ├── build.gradle                    # הגדרות גירסה 0.2.2 וחתימה אוטומטית
│   └── src/main/
│       ├── AndroidManifest.xml         # הרשאות מדיה, אחסון מלא והתקנת עדכונים
│       ├── java/com/aishhatorah/filemanagerpro/
│       │   ├── MainActivity.java           # מסך הבית, החלקה לרענון ורשת 4x3
│       │   ├── FileBrowserActivity.java    # סייר קבצים, העתקה, העברה, מחיקה וחיפוש
│       │   ├── StorageAnalyzerActivity.java# ניתוח הקבצים הכבדים ביותר ותפוסת זיכרון
│       │   ├── SettingsActivity.java       # כל הגדרות האפליקציה וערכות בהיר/כהה/ניאון
│       │   ├── AudioCutterActivity.java    # חותך אודיו נייטיב
│       │   ├── VideoCutterActivity.java    # חותך וידאו נייטיב
│       │   ├── RecycleBinManager.java      # ניהול סל מיחזור ושחזור קבצים
│       │   ├── StorageUtils.java           # חישוב נפחים והתרעת 96% תפוסה
│       │   ├── UpdateChecker.java          # בדיקת עדכונים ב-GitHub, הורדה והתקנה
│       │   ├── AboutDialogHelper.java      # חלון אודות, גירסה וקרדיטים
│       │   ├── UserGuideDialogHelper.java  # מדריך למשתמש מפואר ומעוצב
│       │   ├── ThemeHelper.java            # מנוע ערכות התאורה (בהיר, כהה, ניאון)
│       │   └── PrefsManager.java           # שמירת כלל העדפות המשתמש
│       └── res/                        # פריסות XML, אייקון כחול מותאם וערכי עברית
└── settings.gradle
```

---

## קרדיט ופיתוח

- **מפתח ראשי:** אש התורה
- **פרופיל במתמחים טופ:** [מתמחים טופ — @אש התורה](https://mitmachim.top/user/%D7%90%D7%A9-%D7%94%D7%AA%D7%95%D7%A8%D7%94)
- **פרופיל GitHub:** [github.com/ES2323ES](https://github.com/ES2323ES)

</div>
