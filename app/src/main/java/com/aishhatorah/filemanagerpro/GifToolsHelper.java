package com.aishhatorah.filemanagerpro;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Movie;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;
import android.view.Surface;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * סעיף 9: מנוע GIF מובנה ומהיר ב-Java טהורה (ללא ספריות NDK כבדות):
 * - פירוק GIF לתמונות PNG נפרדות
 * - יצירת GIF מתמונות מרובות או מתוך קטע וידאו
 * - המרת GIF לקובץ וידאו MP4 תקני
 */
public final class GifToolsHelper {

    private GifToolsHelper() {
    }

    public static List<Bitmap> decodeGifFrames(File gifFile, int maxFrames) throws IOException {
        List<Bitmap> frames = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(gifFile)) {
            Movie movie = Movie.decodeStream(fis);
            if (movie == null || movie.width() <= 0 || movie.height() <= 0) {
                throw new IOException("לא ניתן לפענח קובץ GIF");
            }
            int duration = Math.max(100, movie.duration());
            int stepMs = Math.max(40, duration / Math.min(maxFrames, Math.max(1, duration / 70)));
            int w = Math.min(movie.width(), 720);
            int h = Math.max(1, Math.round((float) movie.height() * w / movie.width()));
            float scale = (float) w / movie.width();

            for (int t = 0; t < duration && frames.size() < maxFrames; t += stepMs) {
                Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bmp);
                canvas.drawColor(Color.BLACK);
                canvas.scale(scale, scale);
                movie.setTime(t);
                movie.draw(canvas, 0, 0);
                frames.add(bmp);
            }
        }
        if (frames.isEmpty()) {
            throw new IOException("קובץ ה-GIF אינו מכיל פריימים");
        }
        return frames;
    }

    public static int splitGifToPngs(File gifFile, File outDir) throws IOException {
        if (!outDir.exists() && !outDir.mkdirs()) {
            throw new IOException("לא ניתן ליצור תיקיית פלט");
        }
        List<Bitmap> frames = decodeGifFrames(gifFile, 90);
        int count = 0;
        String base = gifFile.getName().replaceAll("(?i)\\.gif$", "");
        for (int i = 0; i < frames.size(); i++) {
            File out = new File(outDir, base + "_frame_" + (i + 1) + ".png");
            try (FileOutputStream fos = new FileOutputStream(out)) {
                frames.get(i).compress(Bitmap.CompressFormat.PNG, 95, fos);
                count++;
            }
            frames.get(i).recycle();
        }
        return count;
    }

    public static File videoToGif(Context context, File videoFile, File outGif, int maxWidth, int fps) throws IOException {
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        List<Bitmap> frames = new ArrayList<>();
        try {
            mmr.setDataSource(videoFile.getAbsolutePath());
            String durStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            long durMs = durStr != null ? Long.parseLong(durStr) : 4000L;
            long clipMs = Math.min(durMs, 10_000L);
            int totalFrames = Math.max(4, Math.min(60, (int) ((clipMs * fps) / 1000L)));
            long stepUs = (clipMs * 1000L) / totalFrames;
            for (int i = 0; i < totalFrames; i++) {
                Bitmap raw = mmr.getFrameAtTime(i * stepUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                if (raw != null) {
                    Bitmap scaled = scaleDown(raw, maxWidth);
                    if (scaled != raw) raw.recycle();
                    frames.add(scaled);
                }
            }
        } finally {
            try {
                mmr.release();
            } catch (Throwable ignored) {
            }
        }
        if (frames.isEmpty()) throw new IOException("לא ניתן לחלץ פריימים מהוידאו");
        try {
            encodeBitmapsToGif(frames, Math.max(40, 1000 / Math.max(1, fps)), outGif);
        } finally {
            for (Bitmap b : frames) b.recycle();
        }
        return outGif;
    }

    public static File imagesToGif(List<Bitmap> bitmaps, int delayMs, int maxWidth, File outGif) throws IOException {
        List<Bitmap> normalized = new ArrayList<>();
        for (Bitmap b : bitmaps) {
            normalized.add(scaleDown(b, maxWidth));
        }
        try {
            encodeBitmapsToGif(normalized, delayMs, outGif);
        } finally {
            for (int i = 0; i < normalized.size(); i++) {
                if (normalized.get(i) != bitmaps.get(i)) {
                    normalized.get(i).recycle();
                }
            }
        }
        return outGif;
    }

    public static File convertGifToMp4(File gifFile, File outMp4) throws IOException {
        List<Bitmap> frames = decodeGifFrames(gifFile, 90);
        Bitmap first = frames.get(0);
        int width = first.getWidth() & ~1;
        int height = first.getHeight() & ~1;
        if (width < 16) width = 16;
        if (height < 16) height = 16;

        MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, 1_500_000);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, 12);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);

        MediaCodec codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        Surface surface = codec.createInputSurface();
        codec.start();

        MediaMuxer muxer = new MediaMuxer(outMp4.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        int trackIndex = -1;
        boolean muxerStarted = false;

        try {
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            for (Bitmap frame : frames) {
                Canvas c = surface.lockCanvas(null);
                c.drawBitmap(frame, 0, 0, null);
                surface.unlockCanvasAndPost(c);
                drainEncoder(codec, muxer, info, false);
                int idx = drainStepTrack(codec, muxer, info, trackIndex, muxerStarted);
                if (idx >= 0) {
                    trackIndex = idx;
                    muxerStarted = true;
                }
            }
            codec.signalEndOfInputStream();
            while (true) {
                int outStatus = codec.dequeueOutputBuffer(info, 10_000);
                if (outStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    break;
                } else if (outStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (!muxerStarted) {
                        trackIndex = muxer.addTrack(codec.getOutputFormat());
                        muxer.start();
                        muxerStarted = true;
                    }
                } else if (outStatus >= 0) {
                    ByteBuffer buf = codec.getOutputBuffer(outStatus);
                    if (buf != null && info.size > 0 && muxerStarted && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                        muxer.writeSampleData(trackIndex, buf, info);
                    }
                    codec.releaseOutputBuffer(outStatus, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break;
                }
            }
        } finally {
            for (Bitmap b : frames) b.recycle();
            try { codec.stop(); } catch (Throwable ignored) {}
            try { codec.release(); } catch (Throwable ignored) {}
            try { surface.release(); } catch (Throwable ignored) {}
            try { if (muxerStarted) muxer.stop(); muxer.release(); } catch (Throwable ignored) {}
        }
        return outMp4;
    }

    private static void drainEncoder(MediaCodec codec, MediaMuxer muxer, MediaCodec.BufferInfo info, boolean eos) {
        // Handled cleanly in loop
    }

    private static int drainStepTrack(MediaCodec codec, MediaMuxer muxer, MediaCodec.BufferInfo info,
                                      int trackIndex, boolean muxerStarted) {
        while (true) {
            int status = codec.dequeueOutputBuffer(info, 2000);
            if (status == MediaCodec.INFO_TRY_AGAIN_LATER) break;
            if (status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED && !muxerStarted) {
                trackIndex = muxer.addTrack(codec.getOutputFormat());
                muxer.start();
                muxerStarted = true;
            } else if (status >= 0) {
                ByteBuffer buf = codec.getOutputBuffer(status);
                if (buf != null && info.size > 0 && muxerStarted && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                    muxer.writeSampleData(trackIndex, buf, info);
                }
                codec.releaseOutputBuffer(status, false);
            }
        }
        return muxerStarted ? trackIndex : -1;
    }

    private static Bitmap scaleDown(Bitmap src, int maxWidth) {
        if (src.getWidth() <= maxWidth) return src;
        int w = maxWidth;
        int h = Math.max(1, Math.round((float) src.getHeight() * w / src.getWidth()));
        return Bitmap.createScaledBitmap(src, w, h, true);
    }

    /**
     * מקודד GIF89a מהיר ונקי (6x6x6 Web-Safe + 40 Grayscale 256-color fixed LUT + LZW Encoder)
     */
    public static void encodeBitmapsToGif(List<Bitmap> frames, int delayMs, File outFile) throws IOException {
        if (frames == null || frames.isEmpty()) throw new IOException("אין פריימים לקידוד");
        int w = frames.get(0).getWidth();
        int h = frames.get(0).getHeight();
        byte[] gct = buildStandard256ColorTable();

        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(outFile))) {
            // GIF89a Header
            out.write("GIF89a".getBytes());
            writeShortLE(out, w);
            writeShortLE(out, h);
            out.write(0xF7); // Global color table flag + 8-bit color (256 colors)
            out.write(0);    // Background index
            out.write(0);    // Pixel aspect ratio
            out.write(gct);  // 256 * 3 = 768 bytes

            // Netscape 2.0 infinite loop extension
            out.write(new byte[]{0x21, (byte) 0xFF, 0x0B, 'N', 'E', 'T', 'S', 'C', 'A', 'P', 'E', '2', '.', '0', 0x03, 0x01, 0x00, 0x00, 0x00});

            int delayCentis = Math.max(2, delayMs / 10);
            int[] pixels = new int[w * h];
            byte[] indexed = new byte[w * h];

            for (Bitmap bmp : frames) {
                Bitmap target = (bmp.getWidth() == w && bmp.getHeight() == h)
                        ? bmp : Bitmap.createScaledBitmap(bmp, w, h, true);
                target.getPixels(pixels, 0, w, 0, 0, w, h);
                if (target != bmp) target.recycle();

                for (int i = 0; i < pixels.length; i++) {
                    indexed[i] = quantizeColorIndex(pixels[i]);
                }

                // Graphic Control Extension
                out.write(0x21);
                out.write(0xF9);
                out.write(0x04);
                out.write(0x04);
                writeShortLE(out, delayCentis);
                out.write(0x00);
                out.write(0x00);

                // Image Descriptor
                out.write(0x2C);
                writeShortLE(out, 0);
                writeShortLE(out, 0);
                writeShortLE(out, w);
                writeShortLE(out, h);
                out.write(0x00);

                // LZW minimum code size 8
                out.write(0x08);
                writeLzwData(out, indexed);
                out.write(0x00); // Block terminator
            }
            out.write(0x3B); // GIF Trailer
        }
    }

    private static byte[] buildStandard256ColorTable() {
        byte[] table = new byte[256 * 3];
        int idx = 0;
        // 6x6x6 RGB cube (216 colors)
        for (int r = 0; r < 6; r++) {
            for (int g = 0; g < 6; g++) {
                for (int b = 0; b < 6; b++) {
                    table[idx++] = (byte) (r * 51);
                    table[idx++] = (byte) (g * 51);
                    table[idx++] = (byte) (b * 51);
                }
            }
        }
        // 40 Grayscale levels
        for (int i = 0; i < 40; i++) {
            byte v = (byte) Math.min(255, (i * 255) / 39);
            table[idx++] = v;
            table[idx++] = v;
            table[idx++] = v;
        }
        return table;
    }

    private static byte quantizeColorIndex(int argb) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        if (Math.abs(r - g) < 10 && Math.abs(g - b) < 10) {
            int grayIdx = (r * 39 + 127) / 255;
            return (byte) (216 + grayIdx);
        }
        int ri = (r * 5 + 127) / 255;
        int gi = (g * 5 + 127) / 255;
        int bi = (b * 5 + 127) / 255;
        return (byte) (ri * 36 + gi * 6 + bi);
    }

    private static void writeLzwData(OutputStream out, byte[] indexedPixels) throws IOException {
        ByteArrayOutputStream blockBuf = new ByteArrayOutputStream();
        int bitAccum = 0;
        int bitCount = 0;
        int clearCode = 256;
        int eofCode = 257;
        int nextCode = 258;
        int codeBits = 9;

        bitCount = writeBits(blockBuf, bitAccum, bitCount, clearCode, codeBits);
        // Compact fast LZW stream with periodic table reset to guarantee zero dictionary bloat
        for (int i = 0; i < indexedPixels.length; i++) {
            int val = indexedPixels[i] & 0xFF;
            bitCount = writeBits(blockBuf, bitAccum, bitCount, val, codeBits);
            bitAccum = lastAccum;
            nextCode++;
            if (nextCode >= 510) {
                bitCount = writeBits(blockBuf, bitAccum, bitCount, clearCode, codeBits);
                bitAccum = lastAccum;
                nextCode = 258;
            }
        }
        bitCount = writeBits(blockBuf, bitAccum, bitCount, eofCode, codeBits);
        bitAccum = lastAccum;
        if (bitCount > 0) {
            blockBuf.write(bitAccum & 0xFF);
        }

        byte[] raw = blockBuf.toByteArray();
        int off = 0;
        while (off < raw.length) {
            int chunk = Math.min(254, raw.length - off);
            out.write(chunk);
            out.write(raw, off, chunk);
            off += chunk;
        }
    }

    private static int lastAccum = 0;

    private static int writeBits(ByteArrayOutputStream buf, int accum, int bitCount, int code, int bits) {
        int a = accum | (code << bitCount);
        int c = bitCount + bits;
        while (c >= 8) {
            buf.write(a & 0xFF);
            a >>>= 8;
            c -= 8;
        }
        lastAccum = a;
        return c;
    }

    private static void writeShortLE(OutputStream out, int val) throws IOException {
        out.write(val & 0xFF);
        out.write((val >> 8) & 0xFF);
    }
}
