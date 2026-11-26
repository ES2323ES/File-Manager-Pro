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
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.view.Surface;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * סעיף 9: מנוע GIF מובנה ומהיר ב-Java טהורה (ללא ספריות NDK כבדות):
 * - פירוק GIF לתמונות PNG נפרדות
 * - יצירת GIF מתמונות מרובות או מתוך קטע וידאו
 * - המרת GIF לקובץ וידאו MP4 תקני במהירות זהה לחלוטין למקור
 */
public final class GifToolsHelper {

    public static final class GifFrameTiming {
        public final long startMs;
        public final int delayMs;

        public GifFrameTiming(long startMs, int delayMs) {
            this.startMs = startMs;
            this.delayMs = delayMs;
        }
    }

    private static final class DecodedGifFrame {
        final Bitmap bitmap;
        final long startMs;
        final int delayMs;

        DecodedGifFrame(Bitmap bitmap, long startMs, int delayMs) {
            this.bitmap = bitmap;
            this.startMs = startMs;
            this.delayMs = delayMs;
        }
    }

    private GifToolsHelper() {
    }

    /**
     * מפרסר תקני ל-GIF87a/GIF89a המחלץ את השהיית כל פריים מתוך ה-Graphic Control Extension (0x21 0xF9).
     */
    public static List<GifFrameTiming> parseGifFrameTimeline(byte[] data) {
        List<GifFrameTiming> timeline = new ArrayList<>();
        if (data == null || data.length < 14) return timeline;
        if (data[0] != 'G' || data[1] != 'I' || data[2] != 'F') return timeline;

        int pos = 10;
        int packed = data[pos++] & 0xFF;
        pos += 2; // bg color + aspect ratio
        if ((packed & 0x80) != 0) {
            int gctSize = 3 * (1 << ((packed & 0x07) + 1));
            pos += gctSize;
        }

        int pendingDelayMs = 100;
        long cursorMs = 0L;

        while (pos < data.length) {
            int blockId = data[pos++] & 0xFF;
            if (blockId == 0x3B) {
                break; // Trailer
            } else if (blockId == 0x21) {
                if (pos >= data.length) break;
                int label = data[pos++] & 0xFF;
                if (label == 0xF9 && pos + 5 < data.length) {
                    int blockSize = data[pos++] & 0xFF;
                    if (blockSize >= 4 && pos + blockSize <= data.length) {
                        int delayCentis = (data[pos + 1] & 0xFF) | ((data[pos + 2] & 0xFF) << 8);
                        pendingDelayMs = delayCentis <= 1 ? 100 : delayCentis * 10;
                    }
                    pos += blockSize;
                    if (pos < data.length && data[pos] == 0) pos++;
                } else {
                    pos = skipGifSubBlocks(data, pos);
                }
            } else if (blockId == 0x2C) {
                if (pos + 9 > data.length) break;
                int imgPacked = data[pos + 8] & 0xFF;
                pos += 9;
                if ((imgPacked & 0x80) != 0) {
                    int lctSize = 3 * (1 << ((imgPacked & 0x07) + 1));
                    pos += lctSize;
                }
                if (pos >= data.length) break;
                pos++; // LZW minimum code size
                pos = skipGifSubBlocks(data, pos);

                timeline.add(new GifFrameTiming(cursorMs, pendingDelayMs));
                cursorMs += pendingDelayMs;
                pendingDelayMs = 100;
            } else {
                break;
            }
        }
        return timeline;
    }

    private static int skipGifSubBlocks(byte[] data, int pos) {
        while (pos < data.length) {
            int subLen = data[pos++] & 0xFF;
            if (subLen == 0) break;
            pos += subLen;
        }
        return Math.min(pos, data.length);
    }

    private static byte[] readFileBytes(File file) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream((int) Math.min(file.length(), 32 * 1024 * 1024L));
        byte[] buf = new byte[16 * 1024];
        try (FileInputStream fis = new FileInputStream(file)) {
            int r;
            while ((r = fis.read(buf)) != -1) {
                baos.write(buf, 0, r);
            }
        }
        return baos.toByteArray();
    }

    private static List<DecodedGifFrame> decodeGifTimeline(File gifFile, int maxFrames) throws IOException {
        byte[] gifBytes = readFileBytes(gifFile);
        Movie movie = Movie.decodeByteArray(gifBytes, 0, gifBytes.length);
        if (movie == null || movie.width() <= 0 || movie.height() <= 0) {
            throw new IOException("לא ניתן לפענח קובץ GIF");
        }

        List<GifFrameTiming> rawTimeline = parseGifFrameTimeline(gifBytes);
        List<GifFrameTiming> chosenTimeline = new ArrayList<>();

        if (!rawTimeline.isEmpty()) {
            if (rawTimeline.size() <= maxFrames) {
                chosenTimeline.addAll(rawTimeline);
            } else {
                double stride = (double) rawTimeline.size() / (double) maxFrames;
                for (int i = 0; i < maxFrames; i++) {
                    int startIdx = (int) Math.floor(i * stride);
                    int endIdx = Math.min(rawTimeline.size(), (int) Math.floor((i + 1) * stride));
                    if (endIdx <= startIdx) endIdx = Math.min(rawTimeline.size(), startIdx + 1);
                    int mergedDelay = 0;
                    for (int j = startIdx; j < endIdx; j++) {
                        mergedDelay += rawTimeline.get(j).delayMs;
                    }
                    chosenTimeline.add(new GifFrameTiming(rawTimeline.get(startIdx).startMs, Math.max(20, mergedDelay)));
                }
            }
        } else {
            int duration = Math.max(100, movie.duration());
            int stepMs = Math.max(40, duration / Math.min(maxFrames, Math.max(1, duration / 70)));
            for (int t = 0; t < duration && chosenTimeline.size() < maxFrames; t += stepMs) {
                int delay = Math.min(stepMs, Math.max(20, duration - t));
                chosenTimeline.add(new GifFrameTiming(t, delay));
            }
        }

        int w = Math.min(movie.width(), 720);
        int h = Math.max(1, Math.round((float) movie.height() * w / movie.width()));
        float scale = (float) w / movie.width();

        List<DecodedGifFrame> frames = new ArrayList<>(chosenTimeline.size());
        long movieDuration = Math.max(1, movie.duration());
        for (GifFrameTiming timing : chosenTimeline) {
            Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bmp);
            canvas.drawColor(Color.BLACK);
            canvas.scale(scale, scale);
            int sampleTime = (int) Math.min(Math.max(0L, timing.startMs), movieDuration - 1L);
            movie.setTime(sampleTime);
            movie.draw(canvas, 0, 0);
            frames.add(new DecodedGifFrame(bmp, timing.startMs, timing.delayMs));
        }

        if (frames.isEmpty()) {
            throw new IOException("קובץ ה-GIF אינו מכיל פריימים");
        }
        return frames;
    }

    public static List<Bitmap> decodeGifFrames(File gifFile, int maxFrames) throws IOException {
        List<DecodedGifFrame> timeline = decodeGifTimeline(gifFile, maxFrames);
        List<Bitmap> frames = new ArrayList<>(timeline.size());
        for (DecodedGifFrame f : timeline) {
            frames.add(f.bitmap);
        }
        return frames;
    }

    public static int splitGifToPngs(File gifFile, File outDir) throws IOException {
        if (!outDir.exists() && !outDir.mkdirs()) {
            throw new IOException("לא ניתן ליצור תיקיית פלט");
        }
        List<Bitmap> frames = decodeGifFrames(gifFile, 120);
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
        List<Bitmap> frames = extractFramesForGif(videoFile, maxWidth, fps);
        boolean isTs = MpegTsNormalizer.isLikelyMpegTsName(videoFile.getName())
                || MpegTsNormalizer.detectTsLayoutCached(videoFile).packetStride > 0;
        if (frames.isEmpty() && isTs && context != null) {
            File tmpMp4 = new File(context.getCacheDir(), "gif_mts_tmp_" + System.currentTimeMillis() + ".mp4");
            try {
                TsToMp4Remuxer.remuxToMp4(videoFile, tmpMp4, 0L, 11_000_000L, null);
                frames = extractFramesForGif(tmpMp4, maxWidth, fps);
            } catch (Throwable ignored) {
            } finally {
                if (tmpMp4.exists()) {
                    try { tmpMp4.delete(); } catch (Throwable ignored) {}
                }
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

    private static List<Bitmap> extractFramesForGif(File videoFile, int maxWidth, int fps) {
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        java.io.Closeable tsDs = null;
        List<Bitmap> frames = new ArrayList<>();
        try {
            tsDs = MpegTsNormalizer.setMediaMetadataRetrieverSource(mmr, videoFile);
            String durStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            long durMs = (durStr != null && !durStr.trim().isEmpty()) ? Long.parseLong(durStr.trim()) : 0L;
            if (durMs <= 0L && MpegTsNormalizer.isLikelyMpegTsName(videoFile.getName())) {
                durMs = MpegTsNormalizer.extractTsDurationMs(videoFile);
            }
            if (durMs <= 0L) durMs = 4000L;
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
        } catch (Throwable ignored) {
        } finally {
            try {
                mmr.release();
            } catch (Throwable ignored) {
            }
            if (tsDs != null) {
                try {
                    tsDs.close();
                } catch (Throwable ignored) {
                }
            }
        }
        return frames;
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
        List<DecodedGifFrame> frames = decodeGifTimeline(gifFile, 240);
        Bitmap first = frames.get(0).bitmap;
        int width = first.getWidth() & ~1;
        int height = first.getHeight() & ~1;
        if (width < 16) width = 16;
        if (height < 16) height = 16;

        long totalDurationMs = 0L;
        for (DecodedGifFrame f : frames) {
            totalDurationMs += Math.max(20, f.delayMs);
        }
        int avgFps = Math.max(4, Math.min(60, Math.round((frames.size() * 1000f) / Math.max(100L, totalDurationMs))));

        MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, 2_000_000);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, avgFps);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);

        MediaCodec codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        Surface surface = codec.createInputSurface();
        codec.start();

        MediaMuxer muxer = new MediaMuxer(outMp4.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        int[] trackState = new int[]{-1, 0}; // [trackIndex, muxerStarted(0/1)]
        long[] lastPtsState = new long[]{-1L};
        Deque<Long> scheduledPtsUs = new ArrayDeque<>();

        EglVideoSurfaceRenderer eglRenderer = null;
        try {
            eglRenderer = new EglVideoSurfaceRenderer(surface, width, height);
        } catch (Throwable ignored) {
            eglRenderer = null;
        }

        try {
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            long cursorUs = 0L;

            for (int i = 0; i < frames.size(); i++) {
                DecodedGifFrame gf = frames.get(i);
                long ptsUs = cursorUs;
                long frameDurUs = Math.max(20_000L, gf.delayMs * 1000L);
                scheduledPtsUs.addLast(ptsUs);

                if (eglRenderer != null) {
                    eglRenderer.drawBitmap(gf.bitmap, ptsUs * 1000L);
                } else {
                    Canvas c = surface.lockCanvas(null);
                    c.drawBitmap(gf.bitmap, 0, 0, null);
                    surface.unlockCanvasAndPost(c);
                }
                drainEncoderOutput(codec, muxer, info, trackState, lastPtsState, eglRenderer == null ? scheduledPtsUs : null, false);
                cursorUs += frameDurUs;
            }

            // פריים סיום להחזקת משך ההשהיה של הפריים האחרון במלואו בכל נגני ה-MP4
            DecodedGifFrame lastFrame = frames.get(frames.size() - 1);
            long tailPtsUs = Math.max(cursorUs - 10_000L, lastPtsState[0] + 10_000L);
            scheduledPtsUs.addLast(tailPtsUs);
            if (eglRenderer != null) {
                eglRenderer.drawBitmap(lastFrame.bitmap, tailPtsUs * 1000L);
            } else {
                Canvas c = surface.lockCanvas(null);
                c.drawBitmap(lastFrame.bitmap, 0, 0, null);
                surface.unlockCanvasAndPost(c);
            }

            codec.signalEndOfInputStream();
            drainEncoderOutput(codec, muxer, info, trackState, lastPtsState, eglRenderer == null ? scheduledPtsUs : null, true);
        } finally {
            for (DecodedGifFrame f : frames) f.bitmap.recycle();
            if (eglRenderer != null) {
                try { eglRenderer.release(); } catch (Throwable ignored) {}
            }
            try { codec.stop(); } catch (Throwable ignored) {}
            try { codec.release(); } catch (Throwable ignored) {}
            try { surface.release(); } catch (Throwable ignored) {}
            try {
                if (trackState[1] == 1) muxer.stop();
                muxer.release();
            } catch (Throwable ignored) {}
        }
        return outMp4;
    }

    private static void drainEncoderOutput(MediaCodec codec, MediaMuxer muxer, MediaCodec.BufferInfo info,
                                           int[] trackState, long[] lastWrittenPtsUs,
                                           Deque<Long> fallbackPtsQueue, boolean waitForEos) {
        long timeoutUs = waitForEos ? 10_000L : 2_000L;
        int emptySpins = 0;
        while (true) {
            int status = codec.dequeueOutputBuffer(info, timeoutUs);
            if (status == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!waitForEos || ++emptySpins > 25) break;
                continue;
            }
            emptySpins = 0;
            if (status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (trackState[1] == 0) {
                    trackState[0] = muxer.addTrack(codec.getOutputFormat());
                    muxer.start();
                    trackState[1] = 1;
                }
            } else if (status >= 0) {
                ByteBuffer buf = codec.getOutputBuffer(status);
                if (buf != null && info.size > 0 && trackState[1] == 1
                        && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                    long ptsUs = info.presentationTimeUs;
                    if (fallbackPtsQueue != null && !fallbackPtsQueue.isEmpty()) {
                        ptsUs = fallbackPtsQueue.removeFirst();
                    }
                    if (ptsUs <= lastWrittenPtsUs[0]) {
                        ptsUs = lastWrittenPtsUs[0] + 10_000L;
                    }
                    lastWrittenPtsUs[0] = ptsUs;
                    info.presentationTimeUs = ptsUs;
                    muxer.writeSampleData(trackState[0], buf, info);
                }
                codec.releaseOutputBuffer(status, false);
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    break;
                }
            }
        }
    }

    private static final class EglVideoSurfaceRenderer {
        private final EGLDisplay eglDisplay;
        private final EGLContext eglContext;
        private final EGLSurface eglSurface;
        private final int program;
        private final int textureId;
        private final int aPos;
        private final int aTex;
        private final FloatBuffer vertexBuf;
        private final FloatBuffer texBuf;

        EglVideoSurfaceRenderer(Surface surface, int width, int height) {
            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
            if (eglDisplay == EGL14.EGL_NO_DISPLAY) throw new IllegalStateException("No EGL display");
            int[] ver = new int[2];
            if (!EGL14.eglInitialize(eglDisplay, ver, 0, ver, 1)) throw new IllegalStateException("EGL init failed");

            int[] attribList = {
                    EGL14.EGL_RED_SIZE, 8,
                    EGL14.EGL_GREEN_SIZE, 8,
                    EGL14.EGL_BLUE_SIZE, 8,
                    EGL14.EGL_ALPHA_SIZE, 8,
                    EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                    0x3142, 1, // EGL_RECORDABLE_ANDROID
                    EGL14.EGL_NONE
            };
            EGLConfig[] configs = new EGLConfig[1];
            int[] numConfigs = new int[1];
            if (!EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, 1, numConfigs, 0) || numConfigs[0] <= 0) {
                throw new IllegalStateException("EGL config failed");
            }
            eglContext = EGL14.eglCreateContext(eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT,
                    new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE}, 0);
            eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, configs[0], surface,
                    new int[]{EGL14.EGL_NONE}, 0);
            if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                throw new IllegalStateException("EGL makeCurrent failed");
            }

            GLES20.glViewport(0, 0, width, height);
            String vs = "attribute vec4 aPos;\nattribute vec2 aTex;\nvarying vec2 vTex;\nvoid main(){gl_Position=aPos;vTex=aTex;}";
            String fs = "precision mediump float;\nvarying vec2 vTex;\nuniform sampler2D uTex;\nvoid main(){gl_FragColor=texture2D(uTex,vTex);}";
            int vShader = compileShader(GLES20.GL_VERTEX_SHADER, vs);
            int fShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fs);
            program = GLES20.glCreateProgram();
            GLES20.glAttachShader(program, vShader);
            GLES20.glAttachShader(program, fShader);
            GLES20.glLinkProgram(program);
            GLES20.glDeleteShader(vShader);
            GLES20.glDeleteShader(fShader);

            aPos = GLES20.glGetAttribLocation(program, "aPos");
            aTex = GLES20.glGetAttribLocation(program, "aTex");

            int[] texIds = new int[1];
            GLES20.glGenTextures(1, texIds, 0);
            textureId = texIds[0];
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

            float[] verts = {-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f};
            float[] uvs = {0f, 1f, 1f, 1f, 0f, 0f, 1f, 0f};
            vertexBuf = ByteBuffer.allocateDirect(verts.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
            vertexBuf.put(verts).position(0);
            texBuf = ByteBuffer.allocateDirect(uvs.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
            texBuf.put(uvs).position(0);
        }

        private static int compileShader(int type, String src) {
            int s = GLES20.GL_NONE;
            s = GLES20.glCreateShader(type);
            GLES20.glShaderSource(s, src);
            GLES20.glCompileShader(s);
            return s;
        }

        void drawBitmap(Bitmap bmp, long ptsNs) {
            EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext);
            GLES20.glClearColor(0f, 0f, 0f, 1f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            GLES20.glUseProgram(program);

            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0);

            GLES20.glEnableVertexAttribArray(aPos);
            GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, vertexBuf);
            GLES20.glEnableVertexAttribArray(aTex);
            GLES20.glVertexAttribPointer(aTex, 2, GLES20.GL_FLOAT, false, 0, texBuf);

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
            EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, ptsNs);
            EGL14.eglSwapBuffers(eglDisplay, eglSurface);
        }

        void release() {
            GLES20.glDeleteTextures(1, new int[]{textureId}, 0);
            GLES20.glDeleteProgram(program);
            EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
            EGL14.eglDestroySurface(eglDisplay, eglSurface);
            EGL14.eglDestroyContext(eglDisplay, eglContext);
            EGL14.eglReleaseThread();
            EGL14.eglTerminate(eglDisplay);
        }
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
