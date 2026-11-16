package com.aishhatorah.filemanagerpro;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;

import androidx.core.graphics.ColorUtils;

/**
 * חילוץ והתאמת פלטת צבעים דינמית מעטיפת השמע עבור מסך האודיו והשורה החכמה.
 */
public class AudioPaletteHelper {

    public static class AudioAdaptivePalette {
        public final int bg;
        public final int textPrimary;
        public final int textSecondary;
        public final int accent;
        public final int playIconTint;
        public final boolean darkSurface;

        public AudioAdaptivePalette(
                int bg,
                int textPrimary,
                int textSecondary,
                int accent,
                int playIconTint,
                boolean darkSurface
        ) {
            this.bg = bg;
            this.textPrimary = textPrimary;
            this.textSecondary = textSecondary;
            this.accent = accent;
            this.playIconTint = playIconTint;
            this.darkSurface = darkSurface;
        }
    }

    public static AudioAdaptivePalette computePalette(
            Context context,
            Bitmap art,
            int theme,
            int defaultBg,
            int defaultTextPrimary,
            int defaultTextSecondary,
            int defaultAccent
    ) {
        boolean defaultDark = ThemeHelper.isDarkSurface(context, theme);
        if (art == null || art.isRecycled() || art.getWidth() <= 0 || art.getHeight() <= 0) {
            int playIcon = ColorUtils.calculateLuminance(defaultAccent) > 0.62
                    ? Color.parseColor("#0F172A")
                    : Color.WHITE;
            return new AudioAdaptivePalette(defaultBg, defaultTextPrimary, defaultTextSecondary, defaultAccent, playIcon, defaultDark);
        }

        Bitmap sample = Bitmap.createScaledBitmap(art, 24, 24, true);
        int count = sample.getWidth() * sample.getHeight();
        int[] pixels = new int[count];
        sample.getPixels(pixels, 0, sample.getWidth(), 0, 0, sample.getWidth(), sample.getHeight());
        if (sample != art) {
            sample.recycle();
        }

        final int bins = 15;
        float[] binScore = new float[bins];
        float[] binR = new float[bins];
        float[] binG = new float[bins];
        float[] binB = new float[bins];

        double sumR = 0, sumG = 0, sumB = 0;
        int visibleCount = 0;
        float[] hsv = new float[3];

        for (int p : pixels) {
            if (Color.alpha(p) < 128) continue;
            int r = Color.red(p);
            int g = Color.green(p);
            int b = Color.blue(p);
            sumR += r;
            sumG += g;
            sumB += b;
            visibleCount++;

            Color.colorToHSV(p, hsv);
            float sat = hsv[1];
            float val = hsv[2];
            if (val < 0.08f || (val > 0.96f && sat < 0.14f)) continue;
            float weight = sat * Math.max(0.15f, 1.0f - Math.abs(val - 0.56f) * 1.15f);
            int bin = Math.min(bins - 1, Math.max(0, (int) (hsv[0] / (360f / bins))));
            binScore[bin] += weight;
            binR[bin] += r * weight;
            binG[bin] += g * weight;
            binB[bin] += b * weight;
        }

        if (visibleCount == 0) {
            int playIcon = ColorUtils.calculateLuminance(defaultAccent) > 0.62
                    ? Color.parseColor("#0F172A")
                    : Color.WHITE;
            return new AudioAdaptivePalette(defaultBg, defaultTextPrimary, defaultTextSecondary, defaultAccent, playIcon, defaultDark);
        }

        int meanColor = Color.rgb(
                (int) Math.round(sumR / visibleCount),
                (int) Math.round(sumG / visibleCount),
                (int) Math.round(sumB / visibleCount)
        );

        int bestBin = -1;
        float bestWeight = 0.0f;
        for (int i = 0; i < bins; i++) {
            if (binScore[i] > bestWeight) {
                bestWeight = binScore[i];
                bestBin = i;
            }
        }

        boolean hasVibrant = bestBin >= 0 && bestWeight > 0.4f;
        int dominant = hasVibrant
                ? Color.rgb(
                Math.min(255, Math.round(binR[bestBin] / bestWeight)),
                Math.min(255, Math.round(binG[bestBin] / bestWeight)),
                Math.min(255, Math.round(binB[bestBin] / bestWeight)))
                : meanColor;

        double artLum = ColorUtils.calculateLuminance(meanColor);
        boolean darkSurface = defaultDark || artLum < 0.38;

        Color.colorToHSV(dominant, hsv);
        float bgSat = hasVibrant ? Math.min(0.48f, Math.max(0.22f, hsv[1] * 0.58f)) : 0.06f;
        int adaptedBg;
        int adaptedTextPrimary;
        int adaptedTextSecondary;
        int adaptedAccent;

        if (darkSurface) {
            float[] bgHsv = new float[]{hsv[0], bgSat, 0.14f};
            int deepTint = Color.HSVToColor(bgHsv);
            adaptedBg = ColorUtils.blendARGB(Color.parseColor("#0A0E17"), deepTint, 0.72f);
            adaptedTextPrimary = Color.parseColor("#F8FAFC");
            adaptedTextSecondary = ColorUtils.setAlphaComponent(adaptedTextPrimary, 178);

            if (hasVibrant) {
                float[] accHsv = new float[]{hsv[0], Math.min(0.86f, Math.max(0.50f, hsv[1])), 0.93f};
                int candidate = Color.HSVToColor(accHsv);
                if (ColorUtils.calculateContrast(candidate, adaptedBg) < 4.5) {
                    candidate = ColorUtils.blendARGB(candidate, Color.WHITE, 0.35f);
                }
                adaptedAccent = candidate;
            } else {
                adaptedAccent = Color.parseColor("#60A5FA");
            }
        } else {
            float[] bgHsv = new float[]{hsv[0], Math.min(0.24f, bgSat), 0.96f};
            int softTint = Color.HSVToColor(bgHsv);
            adaptedBg = ColorUtils.blendARGB(Color.parseColor("#F8FAFC"), softTint, 0.78f);
            adaptedTextPrimary = Color.parseColor("#0F172A");
            adaptedTextSecondary = ColorUtils.setAlphaComponent(adaptedTextPrimary, 175);

            if (hasVibrant) {
                float[] accHsv = new float[]{hsv[0], Math.min(0.95f, Math.max(0.58f, hsv[1])), 0.52f};
                int candidate = Color.HSVToColor(accHsv);
                if (ColorUtils.calculateContrast(candidate, adaptedBg) < 4.5) {
                    candidate = ColorUtils.blendARGB(candidate, Color.BLACK, 0.32f);
                }
                adaptedAccent = candidate;
            } else {
                adaptedAccent = defaultAccent;
            }
        }

        int playIconTint = ColorUtils.calculateLuminance(adaptedAccent) > 0.58
                ? Color.parseColor("#0F172A")
                : Color.WHITE;
        return new AudioAdaptivePalette(adaptedBg, adaptedTextPrimary, adaptedTextSecondary, adaptedAccent, playIconTint, darkSurface);
    }
}
