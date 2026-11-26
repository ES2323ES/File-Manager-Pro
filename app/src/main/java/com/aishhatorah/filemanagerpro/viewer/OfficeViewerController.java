package com.aishhatorah.filemanagerpro.viewer;

import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.aishhatorah.filemanagerpro.ArchiveUtils;
import com.aishhatorah.filemanagerpro.StorageUtils;
import com.aishhatorah.filemanagerpro.ThemeHelper;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * בקר צפייה במסמכי Office (DOCX, XLSX, PPTX, ODT, ODS, ODP) עם חלונית טעינה שקופה וכפתור ביטול.
 */
public class OfficeViewerController {

    private final AppCompatActivity activity;
    private final ViewerHost host;

    public OfficeViewerController(ViewerHost host) {
        this.host = host;
        this.activity = host.getActivity();
    }

    public void render(LinearLayout hostContainer, int cardColor, int textPrimary, int textSecondary, int theme) {
        final File targetFile = host.getTargetFile();
        if (targetFile == null) return;
        int accent = ThemeHelper.getAccentColor(activity, theme);
        AtomicBoolean cancelled = new AtomicBoolean(false);

        FrameLayout stage = new FrameLayout(activity);
        LinearLayout card = ViewerUiHelper.createSurfaceCard(activity, cardColor);

        TextView tvSub = new TextView(activity);
        tvSub.setText("קורא מסמכים מובנה • " + StorageUtils.formatSize(targetFile.length()));
        tvSub.setTextColor(textSecondary);
        tvSub.setTextSize(12f);
        tvSub.setPadding(0, 0, 0, ViewerUiHelper.dp(activity, 10));
        card.addView(tvSub);

        ScrollView scroll = new ScrollView(activity);
        TextView tvDoc = new TextView(activity);
        tvDoc.setText("");
        tvDoc.setTextColor(textPrimary);
        tvDoc.setTextSize(ThemeHelper.spScaled(activity, 14f));
        tvDoc.setTextIsSelectable(true);
        tvDoc.setLineSpacing(6f, 1.2f);
        scroll.addView(tvDoc);
        card.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        stage.addView(card, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        FrameLayout loadingOverlay = ViewerUiHelper.createDocumentLoadingOverlay(activity, theme, accent, textPrimary, () -> {
            cancelled.set(true);
            host.proceedCloseOrNavigateBack();
        });
        stage.addView(loadingOverlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        hostContainer.addView(stage, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        host.getExecutor().execute(() -> {
            String content = ArchiveUtils.extractOfficeDocumentText(targetFile);
            host.getUiHandler().post(() -> {
                if (cancelled.get() || activity.isFinishing() || activity.isDestroyed()) return;
                loadingOverlay.setVisibility(View.GONE);
                tvDoc.setText(content);
            });
        });
    }
}
