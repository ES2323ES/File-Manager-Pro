package com.aishhatorah.filemanagerpro.viewer;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.ColorUtils;

import com.aishhatorah.filemanagerpro.ArchiveUtils;
import com.aishhatorah.filemanagerpro.CacheCleanupManager;
import com.aishhatorah.filemanagerpro.CodeEditorActivity;
import com.aishhatorah.filemanagerpro.FileOpenWithHelper;
import com.aishhatorah.filemanagerpro.FileThumbnailLoader;
import com.aishhatorah.filemanagerpro.GlassAnchoredPopoverHelper;
import com.aishhatorah.filemanagerpro.MediaFormatHelper;
import com.aishhatorah.filemanagerpro.OperationProgressHelper;
import com.aishhatorah.filemanagerpro.R;
import com.aishhatorah.filemanagerpro.StorageUtils;
import com.aishhatorah.filemanagerpro.ThemeHelper;
import com.aishhatorah.filemanagerpro.ThemedDialogHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * בקר סיור, חילוץ ועריכה אינטראקטיביים לארכיונים (ZIP/RAR/7Z/TAR/GZ כולל סיסמה).
 */
public class ArchiveViewerController {

    private final AppCompatActivity activity;
    private final ViewerHost host;
    private String archivePassword = "";

    public ArchiveViewerController(ViewerHost host) {
        this.host = host;
        this.activity = host.getActivity();
    }

    public void render(LinearLayout hostContainer, int cardColor, int textPrimary, int textSecondary, int accent, int theme) {
        final File targetFile = host.getTargetFile();
        if (targetFile == null) return;
        host.setArchiveMode(true);

        LinearLayout card = ViewerUiHelper.createSurfaceCard(activity, cardColor);
        GradientDrawable archCardBg = new GradientDrawable();
        archCardBg.setCornerRadius(dp(20));
        archCardBg.setColor(cardColor);
        archCardBg.setStroke(Math.max(1, dp(1)), ColorUtils.setAlphaComponent(textSecondary, 36));
        card.setBackground(archCardBg);

        LinearLayout headerRow = new LinearLayout(activity);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.setPadding(0, 0, 0, dp(6));

        ImageView ivArchBadge = new ImageView(activity);
        ivArchBadge.setImageResource(R.drawable.ic_zip_archive);
        ivArchBadge.setColorFilter(accent);
        headerRow.addView(ivArchBadge, new LinearLayout.LayoutParams(dp(24), dp(24)));

        LinearLayout titleCol = new LinearLayout(activity);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tcLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tcLp.setMarginStart(dp(10));
        titleCol.setLayoutParams(tcLp);

        TextView tvHeader = new TextView(activity);
        tvHeader.setText("סייר וחילוץ ארכיון");
        tvHeader.setTextColor(textPrimary);
        tvHeader.setTextSize(16.5f);
        tvHeader.setTypeface(Typeface.DEFAULT_BOLD);
        titleCol.addView(tvHeader);

        final TextView tvSub = new TextView(activity);
        tvSub.setText("טוען תוכן ארכיון...");
        tvSub.setTextColor(textSecondary);
        tvSub.setTextSize(12f);
        tvSub.setPadding(0, dp(2), 0, 0);
        titleCol.addView(tvSub);
        headerRow.addView(titleCol);
        card.addView(headerRow);

        final TextView tvBreadcrumb = new TextView(activity);
        tvBreadcrumb.setTextColor(accent);
        tvBreadcrumb.setTextSize(12f);
        tvBreadcrumb.setTypeface(Typeface.DEFAULT_BOLD);
        tvBreadcrumb.setPadding(dp(10), dp(6), dp(10), dp(6));
        tvBreadcrumb.setTextDirection(View.TEXT_DIRECTION_LTR);
        GradientDrawable crumbBg = new GradientDrawable();
        crumbBg.setCornerRadius(dp(10));
        crumbBg.setColor(ColorUtils.setAlphaComponent(accent, 22));
        crumbBg.setStroke(Math.max(1, dp(1)), ColorUtils.setAlphaComponent(accent, 55));
        tvBreadcrumb.setBackground(crumbBg);
        LinearLayout.LayoutParams crumbLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        crumbLp.bottomMargin = dp(8);
        card.addView(tvBreadcrumb, crumbLp);

        ScrollView scroll = new ScrollView(activity);
        final LinearLayout entriesList = new LinearLayout(activity);
        entriesList.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(entriesList);
        card.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout bottomActions = new LinearLayout(activity);
        bottomActions.setOrientation(LinearLayout.HORIZONTAL);
        bottomActions.setPadding(0, dp(10), 0, 0);

        Button btnExtract = ThemedDialogHelper.createThemedButton(activity, "חלץ הכל לכאן", theme, true, false);
        LinearLayout.LayoutParams exLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        btnExtract.setLayoutParams(exLp);
        bottomActions.addView(btnExtract);

        card.addView(bottomActions);
        hostContainer.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        final String[] currentArchiveFolder = new String[]{""};
        final List<ArchiveUtils.ArchiveEntryInfo> allEntries = new ArrayList<>();
        final boolean[] needsPwdState = new boolean[]{false};
        final boolean isZip = MediaFormatHelper.isZipContainer(targetFile.getName());
        final Runnable[] renderFolder = new Runnable[1];
        final Runnable[] reloadArchive = new Runnable[1];

        Runnable navigateUp = () -> {
            if (!currentArchiveFolder[0].isEmpty()) {
                String trimmed = currentArchiveFolder[0].endsWith("/")
                        ? currentArchiveFolder[0].substring(0, currentArchiveFolder[0].length() - 1)
                        : currentArchiveFolder[0];
                int lastSlash = trimmed.lastIndexOf('/');
                currentArchiveFolder[0] = lastSlash >= 0 ? trimmed.substring(0, lastSlash + 1) : "";
                renderFolder[0].run();
            } else {
                host.setArchiveNavigateUpHandler(null);
                host.proceedCloseOrNavigateBack();
            }
        };
        host.setArchiveNavigateUpHandler(navigateUp);

        renderFolder[0] = () -> {
            entriesList.removeAllViews();
            String folderPath = currentArchiveFolder[0];
            tvBreadcrumb.setText(folderPath.isEmpty() ? "/ (שורש הארכיון)" : ("/" + folderPath));
            List<ArchiveUtils.ArchiveTreeNode> nodes = ArchiveUtils.listTreeChildren(allEntries, folderPath);
            tvSub.setText("סה״כ " + allEntries.size() + " פריטים בארכיון • "
                    + nodes.size() + " בתיקייה זו • " + StorageUtils.formatSize(targetFile.length())
                    + (needsPwdState[0] ? " • מוגן בסיסמה" : ""));

            if (!folderPath.isEmpty()) {
                LinearLayout upRow = new LinearLayout(activity);
                upRow.setOrientation(LinearLayout.HORIZONTAL);
                upRow.setGravity(Gravity.CENTER_VERTICAL);
                upRow.setPadding(dp(10), dp(9), dp(10), dp(9));
                GradientDrawable upBg = new GradientDrawable();
                upBg.setCornerRadius(dp(12));
                upBg.setColor(ThemeHelper.getBgColor(activity, theme));
                upBg.setStroke(Math.max(1, dp(1)), ColorUtils.setAlphaComponent(accent, 50));
                upRow.setBackground(upBg);
                LinearLayout.LayoutParams upLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                upLp.bottomMargin = dp(6);
                upRow.setLayoutParams(upLp);

                ImageView ivUp = new ImageView(activity);
                ivUp.setImageResource(R.drawable.ic_arrow_back);
                ivUp.setColorFilter(accent);
                upRow.addView(ivUp, new LinearLayout.LayoutParams(dp(20), dp(20)));

                TextView tvUp = new TextView(activity);
                tvUp.setText(".. (חזרה לתיקייה הקודמת)");
                tvUp.setTextColor(accent);
                tvUp.setTextSize(13f);
                tvUp.setTypeface(Typeface.DEFAULT_BOLD);
                LinearLayout.LayoutParams tvUpLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                tvUpLp.setMarginStart(dp(8));
                upRow.addView(tvUp, tvUpLp);

                upRow.setOnClickListener(v -> navigateUp.run());
                entriesList.addView(upRow);
            }

            for (ArchiveUtils.ArchiveTreeNode node : nodes) {
                LinearLayout row = new LinearLayout(activity);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(10), dp(9), dp(10), dp(9));

                GradientDrawable bg = new GradientDrawable();
                bg.setCornerRadius(dp(12));
                bg.setColor(ThemeHelper.getBgColor(activity, theme));
                bg.setStroke(Math.max(1, dp(1)), ColorUtils.setAlphaComponent(textSecondary, 28));
                row.setBackground(bg);

                LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rLp.bottomMargin = dp(6);
                row.setLayoutParams(rLp);

                ImageView ivIcon = new ImageView(activity);
                if (node.directory) {
                    ivIcon.setImageResource(R.drawable.ic_folder);
                    ivIcon.setColorFilter(accent);
                } else {
                    ivIcon.setImageResource(FileThumbnailLoader.resolveFallbackDrawable(node.displayName.toLowerCase(Locale.ROOT)));
                    ivIcon.clearColorFilter();
                }
                row.addView(ivIcon, new LinearLayout.LayoutParams(dp(24), dp(24)));

                LinearLayout infoCol = new LinearLayout(activity);
                infoCol.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams nLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                nLp.setMarginStart(dp(10));
                nLp.setMarginEnd(dp(8));
                infoCol.setLayoutParams(nLp);

                TextView tvName = new TextView(activity);
                tvName.setText(node.displayName);
                tvName.setTextColor(textPrimary);
                tvName.setTextSize(13.5f);
                tvName.setTypeface(node.directory ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
                tvName.setMaxLines(1);
                tvName.setEllipsize(TextUtils.TruncateAt.MIDDLE);
                infoCol.addView(tvName);

                TextView tvMeta = new TextView(activity);
                String metaText = node.directory ? "תיקייה" : (node.size >= 0 ? StorageUtils.formatSize(node.size) : "קובץ");
                if (node.encrypted) metaText += " • מוגן";
                tvMeta.setText(metaText);
                tvMeta.setTextColor(textSecondary);
                tvMeta.setTextSize(11f);
                infoCol.addView(tvMeta);
                row.addView(infoCol);

                if (node.encrypted) {
                    ImageView ivLock = new ImageView(activity);
                    ivLock.setImageResource(R.drawable.ic_lock);
                    ivLock.setColorFilter(textSecondary);
                    LinearLayout.LayoutParams lkLp = new LinearLayout.LayoutParams(dp(15), dp(15));
                    lkLp.setMarginEnd(dp(6));
                    row.addView(ivLock, lkLp);
                }

                ImageButton btnExtractOne = ViewerUiHelper.createCleanBorderlessIconButton(activity, R.drawable.ic_downloads, dp(34), accent);
                btnExtractOne.setContentDescription(node.directory ? "חלץ תיקייה זו" : "חלץ קובץ זה");
                btnExtractOne.setOnClickListener(v -> runSingleNodeExtraction(targetFile, node));
                row.addView(btnExtractOne);

                if (node.directory) {
                    row.setOnClickListener(v -> {
                        currentArchiveFolder[0] = node.fullPath;
                        scroll.scrollTo(0, 0);
                        renderFolder[0].run();
                    });
                    row.setOnLongClickListener(v -> {
                        List<GlassAnchoredPopoverHelper.OptionItem> opts = new ArrayList<>();
                        opts.add(GlassAnchoredPopoverHelper.OptionItem.of("פתח תיקייה", R.drawable.ic_folder, () -> {
                            currentArchiveFolder[0] = node.fullPath;
                            scroll.scrollTo(0, 0);
                            renderFolder[0].run();
                        }));
                        opts.add(GlassAnchoredPopoverHelper.OptionItem.of("חלץ תיקייה זו בלבד", R.drawable.ic_downloads,
                                () -> runSingleNodeExtraction(targetFile, node)));
                        GlassAnchoredPopoverHelper.showMenu(activity, row, node.displayName, null, opts, false);
                        return true;
                    });
                } else {
                    final ArchiveUtils.ArchiveEntryInfo entryRef = node.entryInfo != null
                            ? node.entryInfo
                            : new ArchiveUtils.ArchiveEntryInfo(node.fullPath, node.size, node.compressedSize, false, node.encrypted);
                    row.setOnClickListener(v -> openArchiveEntry(targetFile, entryRef, () -> openArchiveEntry(targetFile, entryRef, null)));
                    row.setOnLongClickListener(v -> {
                        List<GlassAnchoredPopoverHelper.OptionItem> opts = new ArrayList<>();
                        opts.add(GlassAnchoredPopoverHelper.OptionItem.of("פתח / צפה", R.drawable.ic_visibility,
                                () -> openArchiveEntry(targetFile, entryRef, () -> openArchiveEntry(targetFile, entryRef, null))));
                        opts.add(GlassAnchoredPopoverHelper.OptionItem.of("חלץ קובץ זה בלבד", R.drawable.ic_downloads,
                                () -> runSingleNodeExtraction(targetFile, node)));
                        if (isZip && !node.encrypted) {
                            opts.add(GlassAnchoredPopoverHelper.OptionItem.destructive("מחק מהארכיון", R.drawable.ic_delete, true, () ->
                                    ThemedDialogHelper.showCustomDialog(activity, "מחיקה מהארכיון",
                                            "האם למחוק את \"" + node.displayName + "\" מתוך ה-ZIP?", null,
                                            "מחק", () -> host.getExecutor().execute(() -> {
                                                try {
                                                    ArchiveUtils.deleteZipEntry(targetFile, entryRef.name);
                                                    host.getUiHandler().post(reloadArchive[0]);
                                                } catch (Exception e) {
                                                    host.getUiHandler().post(() ->
                                                            Toast.makeText(activity, "שגיאה: " + e.getMessage(), Toast.LENGTH_LONG).show());
                                                }
                                            }), "ביטול", null, true)));
                        }
                        GlassAnchoredPopoverHelper.showMenu(activity, row, node.displayName, null, opts, false);
                        return true;
                    });
                }
                entriesList.addView(row);
            }
        };

        reloadArchive[0] = () -> host.getExecutor().execute(() -> {
            try {
                List<ArchiveUtils.ArchiveEntryInfo> fetched = ArchiveUtils.listArchiveEntries(targetFile, archivePassword);
                boolean needsPwd = ArchiveUtils.archiveRequiresPassword(targetFile);
                host.getUiHandler().post(() -> {
                    allEntries.clear();
                    allEntries.addAll(fetched);
                    needsPwdState[0] = needsPwd;
                    renderFolder[0].run();
                });
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : "";
                host.getUiHandler().post(() -> {
                    if (msg.contains("סיסמ")) {
                        promptArchivePassword(reloadArchive[0]);
                    } else {
                        tvSub.setText("שגיאה בקריאת הארכיון: " + msg);
                    }
                });
            }
        });
        reloadArchive[0].run();

        btnExtract.setOnClickListener(v -> {
            if (ArchiveUtils.archiveRequiresPassword(targetFile) && archivePassword.isEmpty()) {
                promptArchivePassword(() -> runFullExtraction(targetFile, btnExtract));
            } else {
                runFullExtraction(targetFile, btnExtract);
            }
        });
    }

    private void runSingleNodeExtraction(File targetFile, ArchiveUtils.ArchiveTreeNode node) {
        if ((node.encrypted || ArchiveUtils.archiveRequiresPassword(targetFile)) && archivePassword.isEmpty()) {
            promptArchivePassword(() -> runSingleNodeExtraction(targetFile, node));
            return;
        }
        final OperationProgressHelper.Handle progress = OperationProgressHelper.show(
                activity, node.directory ? "חילוץ תיקייה" : "חילוץ קובץ", node.displayName, true);
        host.getExecutor().execute(() -> {
            try {
                File destDir = targetFile.getParentFile() != null ? targetFile.getParentFile() : activity.getFilesDir();
                String cleanLeaf = new File(node.displayName).getName();
                if (cleanLeaf.isEmpty()) cleanLeaf = "extracted_item";
                File outDir = StorageUtils.uniqueDestinationFile(destDir, cleanLeaf + "_מחולץ");
                int count = ArchiveUtils.extractSingleOrFolder(
                        targetFile, node.fullPath, node.directory, outDir, archivePassword,
                        (itemName, done, total) -> progress.updateCounts(itemName, done, total, outDir.getName()));
                progress.finish();
                host.getUiHandler().post(() -> Toast.makeText(activity,
                        "חולצו " + count + " פריטים אל " + outDir.getName(), Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                progress.finish();
                String msg = e.getMessage() != null ? e.getMessage() : "";
                host.getUiHandler().post(() -> {
                    if (msg.contains("סיסמ")) {
                        archivePassword = "";
                        Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show();
                        promptArchivePassword(() -> runSingleNodeExtraction(targetFile, node));
                    } else {
                        Toast.makeText(activity, "שגיאה בחילוץ: " + msg, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private void promptArchivePassword(Runnable onEntered) {
        EditText et = new EditText(activity);
        et.setHint("הזן סיסמת ארכיון...");
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        et.setText(archivePassword);
        ThemedDialogHelper.showCustomDialog(activity, "ארכיון מוגן בסיסמה",
                "הזן את הסיסמה לפתיחה ועריכה:", et, "אישור", () -> {
                    archivePassword = et.getText() != null ? et.getText().toString() : "";
                    if (onEntered != null) onEntered.run();
                }, "ביטול", null, false);
    }

    private void runFullExtraction(File targetFile, Button btnExtract) {
        btnExtract.setEnabled(false);
        final OperationProgressHelper.Handle progress = OperationProgressHelper.show(
                activity, "חילוץ ארכיון", targetFile.getName(), true);
        host.getExecutor().execute(() -> {
            try {
                String baseName = targetFile.getName().replaceAll("(?i)\\.(tar\\.gz|tgz|zip|tar|gz|rar|7z|jar|cbz|apk|apkx|apk\\+|apks|apkm|xapk|apkp)$", "");
                File outDir = StorageUtils.uniqueDestinationFile(
                        targetFile.getParentFile() != null ? targetFile.getParentFile() : activity.getFilesDir(),
                        baseName + "_מחולץ");
                int count = ArchiveUtils.extractArchive(targetFile, outDir, archivePassword,
                        (itemName, done, total) -> progress.updateCounts(itemName, done, total, outDir.getName()));
                progress.finish();
                host.getUiHandler().post(() -> {
                    btnExtract.setEnabled(true);
                    ThemedDialogHelper.showCustomDialog(activity, "החילוץ הושלם בהצלחה",
                            "חולצו " + count + " קבצים אל:\n" + outDir.getAbsolutePath(),
                            null, "הבנתי", host::proceedCloseOrNavigateBack, null, null, false);
                });
            } catch (Exception e) {
                progress.finish();
                String msg = e.getMessage() != null ? e.getMessage() : "";
                host.getUiHandler().post(() -> {
                    btnExtract.setEnabled(true);
                    if (msg.contains("סיסמ")) {
                        archivePassword = "";
                        Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show();
                        promptArchivePassword(() -> runFullExtraction(targetFile, btnExtract));
                    } else {
                        Toast.makeText(activity, "שגיאה בחילוץ: " + msg, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private void openArchiveEntry(File targetFile, ArchiveUtils.ArchiveEntryInfo item, Runnable retryAfterPwd) {
        if (item.encrypted && archivePassword.isEmpty() && retryAfterPwd != null) {
            promptArchivePassword(retryAfterPwd);
            return;
        }
        String leaf = new File(item.name).getName().toLowerCase(Locale.ROOT);
        if (MediaFormatHelper.isZipContainer(targetFile.getName())
                && !item.encrypted
                && (MediaFormatHelper.isTextFile(leaf) || MediaFormatHelper.isCodeOrSyntaxFile(leaf))) {
            Intent ci = new Intent(activity, CodeEditorActivity.class);
            ci.putExtra(CodeEditorActivity.EXTRA_ARCHIVE_PATH, targetFile.getAbsolutePath());
            ci.putExtra(CodeEditorActivity.EXTRA_ARCHIVE_ENTRY, item.name);
            ci.putExtra(CodeEditorActivity.EXTRA_ARCHIVE_PASSWORD, archivePassword);
            activity.startActivity(ci);
            return;
        }

        host.getExecutor().execute(() -> {
            try {
                CacheCleanupManager.cleanupArchivePreviewDir(activity);
                File previewDir = new File(activity.getCacheDir(), "arch_preview");
                previewDir.mkdirs();
                String cleanLeaf = new File(item.name).getName();
                if (cleanLeaf.isEmpty()) cleanLeaf = "entry_" + System.currentTimeMillis();
                File tmp = new File(previewDir, cleanLeaf);
                ArchiveUtils.extractEntryToFile(targetFile, item.name, archivePassword, tmp);
                host.getUiHandler().post(() -> FileOpenWithHelper.tryOpenByDefaultOrBuiltin(activity, tmp));
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : "";
                host.getUiHandler().post(() -> {
                    if (msg.contains("סיסמ")) {
                        archivePassword = "";
                        Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show();
                        promptArchivePassword(() -> openArchiveEntry(targetFile, item, retryAfterPwd));
                    } else {
                        Toast.makeText(activity, "שגיאה בפתיחת פריט: " + msg, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private int dp(float v) {
        return ViewerUiHelper.dp(activity, v);
    }
}
