package com.aishhatorah.filemanagerpro;

import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.RecyclerView;

import com.aishhatorah.filemanagerpro.settings.HomeOrderDragAdapter;

/**
 * סידור והסתרת קטגוריות מסך הבית באמצעות גרירה ושחרור (Drag & Drop) כולל קו חסימה:
 * כל קטגוריה שתמוקם מתחת לקו החסימה תוסתר מהמסך הראשי.
 */
public class HomeLayoutActivity extends AppCompatActivity {

    private HomeOrderDragAdapter adapter;

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int theme = PrefsManager.getThemeMode(this);
        ThemeHelper.applyWindowStatusBar(this, theme);
        int textPrimary = ThemeHelper.getPrimaryTextColor(this, theme);
        int accent = ThemeHelper.getAccentColor(this, theme);
        boolean dark = ThemeHelper.isDarkSurface(this, theme);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (adapter != null) adapter.persistOrder();
                finish();
            }
        });

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        ThemeHelper.applyWallpaperBackdrop(this, root, theme);

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(10), dp(8), dp(14), dp(10));
        ThemeHelper.applyCurvedHeaderStyle(this, topBar, theme);

        ImageButton btnBack = ThemeHelper.createBorderlessSvgButton(
                this, R.drawable.ic_arrow_back, accent, "שמור וחזור", 40, 8, v -> {
                    if (adapter != null) adapter.persistOrder();
                    finish();
                });
        topBar.addView(btnBack);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("סידור מסך הבית");
        tvTitle.setTextColor(textPrimary);
        tvTitle.setTextSize(ThemeHelper.spScaled(this, 17f));
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tLp.setMarginStart(dp(6));
        topBar.addView(tvTitle, tLp);

        Button btnReset = new Button(this);
        btnReset.setText("איפוס");
        btnReset.setAllCaps(false);
        btnReset.setTextSize(ThemeHelper.spScaled(this, 13f));
        btnReset.setTypeface(Typeface.DEFAULT_BOLD);
        btnReset.setTextColor(accent);
        GradientDrawable resetBg = new GradientDrawable();
        resetBg.setCornerRadius(dp(12));
        resetBg.setColor(ColorUtils.setAlphaComponent(accent, dark ? 28 : 16));
        btnReset.setBackground(resetBg);
        btnReset.setStateListAnimator(null);
        btnReset.setMinHeight(0);
        btnReset.setMinimumHeight(0);
        btnReset.setPadding(dp(14), dp(7), dp(14), dp(7));
        btnReset.setOnClickListener(v -> {
            if (adapter != null) adapter.resetToDefault();
            Toast.makeText(this, "סדר הבית אופס לברירת המחדל", Toast.LENGTH_SHORT).show();
        });
        topBar.addView(btnReset);
        root.addView(topBar);

        TextView tvHint = new TextView(this);
        tvHint.setText("גרור למעלה/למטה לשינוי סדר הקטגוריות. פריטים מתחת ל\"קו החסימה\" לא יופיעו במסך הבית.");
        tvHint.setTextColor(ThemeHelper.getSecondaryTextColor(this, theme));
        tvHint.setTextSize(ThemeHelper.spScaled(this, 12.5f));
        tvHint.setPadding(dp(16), dp(10), dp(16), dp(8));
        root.addView(tvHint);

        RecyclerView rv = new RecyclerView(this);
        adapter = HomeOrderDragAdapter.attachToRecyclerView(rv, this, theme, false, null);

        root.addView(rv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }
}
