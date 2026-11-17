package com.aishhatorah.filemanagerpro;

import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Surface;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Locale;

/**
 * מנוע דחיסה והפחתת משקל לווידאו מבוסס MediaCodec + OpenGL Surface + MediaMuxer.
 */
public final class VideoCompressor {

    public enum QualityPreset {
        ORIGINAL(0, "מקורי (ללא דחיסה)", 0, 0),
        BALANCED_720P(1, "מאוזנת 720p (קל)", 720, 2_200_000),
        SHARE_480P(2, "שיתוף 480p (חיסכון גבוה)", 480, 1_000_000),
        COMPACT_360P(3, "מינימלית 360p (חיסכון שיא)", 360, 550_000);

        public final int id;
        public final String labelHe;
        public final int maxShortSide;
        public final int targetBitrateBps;

        QualityPreset(int id, String labelHe, int maxShortSide, int targetBitrateBps) {
            this.id = id;
            this.labelHe = labelHe;
            this.maxShortSide = maxShortSide;
            this.targetBitrateBps = targetBitrateBps;
        }
    }

    public interface ProgressCallback {
        void onProgress(int percent);
    }

    private VideoCompressor() {
    }

    public static int[] computeTargetDimensions(int srcWidth, int srcHeight, int maxShortSide) {
        int safeW = Math.max(16, srcWidth);
        int safeH = Math.max(16, srcHeight);
        if (maxShortSide <= 0) {
            return new int[]{align16(safeW), align16(safeH)};
        }
        int shortSide = Math.min(safeW, safeH);
        if (shortSide <= maxShortSide) {
            return new int[]{align16(safeW), align16(safeH)};
        }
        float ratio = (float) maxShortSide / (float) shortSide;
        int outW = align16(Math.round(safeW * ratio));
        int outH = align16(Math.round(safeH * ratio));
        return new int[]{outW, outH};
    }

    private static int align16(int v) {
        int aligned = ((v + 8) / 16) * 16;
        return Math.max(16, aligned);
    }

    public static int clampBitrateForResolution(int requestedBps, int srcBitrateBps) {
        if (requestedBps <= 0) return Math.max(250_000, srcBitrateBps);
        if (srcBitrateBps > 0) {
            int capped = Math.max(250_000, Math.round(srcBitrateBps * 0.85f));
            return Math.min(requestedBps, capped);
        }
        return requestedBps;
    }

    public static long estimateCompressedBytes(
            long clipDurationMs,
            QualityPreset preset,
            long sourceFileBytes,
            long totalSourceDurationMs,
            boolean muteAudio,
            float speed
    ) {
        long safeClipMs = Math.max(100L, clipDurationMs);
        float safeSpeed = Math.max(0.25f, speed);
        long outDurationMs = Math.max(100L, Math.round(safeClipMs / safeSpeed));
        if (preset == null || preset == QualityPreset.ORIGINAL) {
            long totalMs = Math.max(safeClipMs, totalSourceDurationMs);
            double frac = Math.min(1.0, (double) safeClipMs / (double) Math.max(1L, totalMs));
            long est = Math.round(sourceFileBytes * frac * (muteAudio ? 0.92 : 1.0));
            return Math.max(1024L, est);
        }
        int audioBps = muteAudio ? 0 : 128_000;
        long totalBps = (long) preset.targetBitrateBps + audioBps;
        long estBytes = Math.round((outDurationMs / 1000.0) * (totalBps / 8.0) * 1.03);
        long totalMs = Math.max(safeClipMs, totalSourceDurationMs);
        double clipFrac = Math.min(1.0, (double) safeClipMs / (double) Math.max(1L, totalMs));
        long origClipCap = Math.max(1024L, Math.round(sourceFileBytes * clipFrac));
        return Math.min(origClipCap, Math.max(4096L, estBytes));
    }

    public static void compressVideo(
            File sourceFile,
            File outFile,
            long startUs,
            long endUs,
            int extraRotationDeg,
            boolean muteAudio,
            float speed,
            QualityPreset preset,
            ProgressCallback progressCb
    ) throws IOException {
        if (sourceFile == null || !sourceFile.exists()) {
            throw new IOException("קובץ המקור אינו זמין");
        }
        if (preset == null || preset == QualityPreset.ORIGINAL) {
            throw new IllegalArgumentException("Preset must be a compressed preset");
        }

        float safeSpeed = Math.max(0.25f, Math.min(4.0f, speed));
        MediaExtractor videoExtractor = new MediaExtractor();
        MediaExtractor audioExtractor = null;
        MediaCodec decoder = null;
        MediaCodec encoder = null;
        MediaMuxer muxer = null;
        EglEncoderSurface eglSurface = null;
        DecoderOutputSurface outputSurface = null;

        boolean completedCleanly = false;
        try {
            videoExtractor.setDataSource(sourceFile.getAbsolutePath());
            int videoTrack = -1;
            int audioTrack = -1;
            MediaFormat srcVideoFmt = null;
            MediaFormat srcAudioFmt = null;

            for (int i = 0; i < videoExtractor.getTrackCount(); i++) {
                MediaFormat fmt = videoExtractor.getTrackFormat(i);
                String mime = fmt.getString(MediaFormat.KEY_MIME);
                if (mime == null) continue;
                if (videoTrack < 0 && mime.startsWith("video/")) {
                    videoTrack = i;
                    srcVideoFmt = fmt;
                } else if (!muteAudio && audioTrack < 0 && mime.startsWith("audio/")) {
                    audioTrack = i;
                    srcAudioFmt = fmt;
                }
            }

            if (videoTrack < 0 || srcVideoFmt == null) {
                throw new IOException("לא נמצא ערוץ וידאו נתמך בקובץ");
            }

            videoExtractor.selectTrack(videoTrack);
            int srcW = srcVideoFmt.containsKey(MediaFormat.KEY_WIDTH) ? srcVideoFmt.getInteger(MediaFormat.KEY_WIDTH) : 1280;
            int srcH = srcVideoFmt.containsKey(MediaFormat.KEY_HEIGHT) ? srcVideoFmt.getInteger(MediaFormat.KEY_HEIGHT) : 720;
            int srcRot = srcVideoFmt.containsKey(MediaFormat.KEY_ROTATION) ? srcVideoFmt.getInteger(MediaFormat.KEY_ROTATION) : 0;
            int fps = srcVideoFmt.containsKey(MediaFormat.KEY_FRAME_RATE) ? srcVideoFmt.getInteger(MediaFormat.KEY_FRAME_RATE) : 30;
            fps = Math.max(15, Math.min(60, fps));
            int srcBitrate = srcVideoFmt.containsKey(MediaFormat.KEY_BIT_RATE) ? srcVideoFmt.getInteger(MediaFormat.KEY_BIT_RATE) : 0;
            long totalUs = srcVideoFmt.containsKey(MediaFormat.KEY_DURATION) ? srcVideoFmt.getLong(MediaFormat.KEY_DURATION) : Math.max(1_000_000L, endUs);

            int[] dims = computeTargetDimensions(srcW, srcH, preset.maxShortSide);
            int outW = dims[0];
            int outH = dims[1];
            int targetBitrate = clampBitrateForResolution(preset.targetBitrateBps, srcBitrate);

            MediaFormat encFmt = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, outW, outH);
            encFmt.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            encFmt.setInteger(MediaFormat.KEY_BIT_RATE, targetBitrate);
            encFmt.setInteger(MediaFormat.KEY_FRAME_RATE, fps);
            encFmt.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2);

            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            encoder.configure(encFmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            eglSurface = new EglEncoderSurface(encoder.createInputSurface());
            encoder.start();
            eglSurface.makeCurrent();

            outputSurface = new DecoderOutputSurface();
            String srcMime = srcVideoFmt.getString(MediaFormat.KEY_MIME);
            decoder = MediaCodec.createDecoderByType(srcMime);
            decoder.configure(srcVideoFmt, outputSurface.getSurface(), null, 0);
            decoder.start();

            muxer = new MediaMuxer(outFile.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            muxer.setOrientationHint(((srcRot + extraRotationDeg) % 360 + 360) % 360);

            if (startUs > 0) {
                videoExtractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
            }

            MediaCodec.BufferInfo decInfo = new MediaCodec.BufferInfo();
            MediaCodec.BufferInfo encInfo = new MediaCodec.BufferInfo();
            boolean extractorDone = false;
            boolean decoderDone = false;
            boolean encoderDone = false;
            boolean muxerStarted = false;
            int muxerVideoTrack = -1;
            int muxerAudioTrack = -1;
            int lastReportedPct = -1;
            long clipSpanUs = Math.max(100_000L, Math.min(totalUs, endUs) - startUs);

            while (!encoderDone) {
                if (!extractorDone) {
                    int inIdx = decoder.dequeueInputBuffer(10_000);
                    if (inIdx >= 0) {
                        ByteBuffer inBuf = decoder.getInputBuffer(inIdx);
                        if (inBuf != null) {
                            int sampleSize = videoExtractor.readSampleData(inBuf, 0);
                            long sampleTime = videoExtractor.getSampleTime();
                            if (sampleSize < 0 || sampleTime > endUs) {
                                decoder.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                extractorDone = true;
                            } else {
                                decoder.queueInputBuffer(inIdx, 0, sampleSize, sampleTime, 0);
                                videoExtractor.advance();
                            }
                        }
                    }
                }

                boolean decoderProgress = true;
                while (!decoderDone && decoderProgress) {
                    int outIdx = decoder.dequeueOutputBuffer(decInfo, 10_000);
                    if (outIdx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        decoderProgress = false;
                    } else if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        // no-op
                    } else if (outIdx >= 0) {
                        boolean isEos = (decInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                        boolean inRange = decInfo.size > 0 && decInfo.presentationTimeUs >= startUs && decInfo.presentationTimeUs <= endUs;
                        decoder.releaseOutputBuffer(outIdx, inRange);
                        if (inRange) {
                            outputSurface.awaitNewImage();
                            outputSurface.drawImage();
                            long outPtsUs = Math.max(0L, (long) ((decInfo.presentationTimeUs - startUs) / safeSpeed));
                            eglSurface.setPresentationTime(outPtsUs * 1000L);
                            eglSurface.swapBuffers();

                            if (progressCb != null) {
                                int pct = (int) Math.min(92L, Math.max(0L, ((decInfo.presentationTimeUs - startUs) * 92L) / clipSpanUs));
                                if (pct != lastReportedPct) {
                                    lastReportedPct = pct;
                                    progressCb.onProgress(pct);
                                }
                            }
                        }
                        if (isEos) {
                            decoderDone = true;
                            encoder.signalEndOfInputStream();
                        }
                        break;
                    } else {
                        decoderProgress = false;
                    }
                }

                while (true) {
                    int encOutIdx = encoder.dequeueOutputBuffer(encInfo, 10_000);
                    if (encOutIdx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        break;
                    } else if (encOutIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        if (muxerStarted) {
                            throw new IOException("שינוי פורמט פלט לא צפוי במהלך הדחיסה");
                        }
                        MediaFormat encodedVideoFmt = encoder.getOutputFormat();
                        muxerVideoTrack = muxer.addTrack(encodedVideoFmt);
                        if (srcAudioFmt != null && audioTrack >= 0) {
                            try {
                                muxerAudioTrack = muxer.addTrack(srcAudioFmt);
                            } catch (Throwable ignored) {
                                muxerAudioTrack = -1;
                            }
                        }
                        muxer.start();
                        muxerStarted = true;
                    } else if (encOutIdx >= 0) {
                        ByteBuffer encBuf = encoder.getOutputBuffer(encOutIdx);
                        if (encBuf != null && (encInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && encInfo.size > 0 && muxerStarted) {
                            encBuf.position(encInfo.offset);
                            encBuf.limit(encInfo.offset + encInfo.size);
                            muxer.writeSampleData(muxerVideoTrack, encBuf, encInfo);
                        }
                        encoder.releaseOutputBuffer(encOutIdx, false);
                        if ((encInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            encoderDone = true;
                            break;
                        }
                    }
                }
            }

            if (muxerStarted && muxerAudioTrack >= 0 && srcAudioFmt != null) {
                audioExtractor = new MediaExtractor();
                audioExtractor.setDataSource(sourceFile.getAbsolutePath());
                audioExtractor.selectTrack(audioTrack);
                if (startUs > 0) {
                    audioExtractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                }
                int bufSize = srcAudioFmt.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)
                        ? Math.max(256 * 1024, srcAudioFmt.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE))
                        : 256 * 1024;
                ByteBuffer audioBuf = ByteBuffer.allocate(bufSize);
                MediaCodec.BufferInfo aInfo = new MediaCodec.BufferInfo();
                while (true) {
                    int sz = audioExtractor.readSampleData(audioBuf, 0);
                    if (sz < 0) break;
                    long pts = audioExtractor.getSampleTime();
                    if (pts > endUs) break;
                    if (pts >= startUs) {
                        aInfo.offset = 0;
                        aInfo.size = sz;
                        aInfo.presentationTimeUs = Math.max(0L, (long) ((pts - startUs) / safeSpeed));
                        int flags = audioExtractor.getSampleFlags();
                        aInfo.flags = (flags & MediaExtractor.SAMPLE_FLAG_SYNC) != 0
                                ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0;
                        muxer.writeSampleData(muxerAudioTrack, audioBuf, aInfo);
                    }
                    audioExtractor.advance();
                }
            }

            if (muxerStarted) {
                muxer.stop();
            }
            if (progressCb != null) {
                progressCb.onProgress(100);
            }
            completedCleanly = true;
        } catch (IOException ioe) {
            throw ioe;
        } catch (Throwable t) {
            throw new IOException(String.format(Locale.US, "שגיאה בדחיסת הווידאו: %s", t.getMessage()), t);
        } finally {
            if (decoder != null) {
                try { decoder.stop(); } catch (Throwable ignored) {}
                try { decoder.release(); } catch (Throwable ignored) {}
            }
            if (encoder != null) {
                try { encoder.stop(); } catch (Throwable ignored) {}
                try { encoder.release(); } catch (Throwable ignored) {}
            }
            try { videoExtractor.release(); } catch (Throwable ignored) {}
            if (audioExtractor != null) {
                try { audioExtractor.release(); } catch (Throwable ignored) {}
            }
            if (outputSurface != null) {
                outputSurface.release();
            }
            if (eglSurface != null) {
                eglSurface.release();
            }
            if (muxer != null) {
                try { muxer.release(); } catch (Throwable ignored) {}
            }
            if (!completedCleanly && outFile != null && outFile.exists()) {
                try { outFile.delete(); } catch (Throwable ignored) {}
            }
        }
    }

    private static final class EglEncoderSurface {
        private EGLDisplay eglDisplay = EGL14.EGL_NO_DISPLAY;
        private EGLContext eglContext = EGL14.EGL_NO_CONTEXT;
        private EGLSurface eglSurface = EGL14.EGL_NO_SURFACE;
        private Surface inputSurface;

        EglEncoderSurface(Surface surface) throws IOException {
            this.inputSurface = surface;
            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
            if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
                throw new IOException("EGL display unavailable");
            }
            int[] version = new int[2];
            if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
                throw new IOException("EGL init failed");
            }
            int[] attribList = {
                    EGL14.EGL_RED_SIZE, 8,
                    EGL14.EGL_GREEN_SIZE, 8,
                    EGL14.EGL_BLUE_SIZE, 8,
                    EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                    0x3142, 1,
                    EGL14.EGL_NONE
            };
            EGLConfig[] configs = new EGLConfig[1];
            int[] numConfigs = new int[1];
            if (!EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, configs.length, numConfigs, 0)
                    || numConfigs[0] <= 0) {
                throw new IOException("EGL config unavailable");
            }
            int[] ctxAttribs = {EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE};
            eglContext = EGL14.eglCreateContext(eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT, ctxAttribs, 0);
            if (eglContext == EGL14.EGL_NO_CONTEXT) {
                throw new IOException("EGL context failed");
            }
            int[] surfAttribs = {EGL14.EGL_NONE};
            eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, configs[0], inputSurface, surfAttribs, 0);
            if (eglSurface == EGL14.EGL_NO_SURFACE) {
                throw new IOException("EGL window surface failed");
            }
        }

        void makeCurrent() throws IOException {
            if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                throw new IOException("eglMakeCurrent failed");
            }
        }

        void swapBuffers() {
            EGL14.eglSwapBuffers(eglDisplay, eglSurface);
        }

        void setPresentationTime(long nsecs) {
            EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, nsecs);
        }

        void release() {
            if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
                if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, eglSurface);
                if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(eglDisplay, eglContext);
                EGL14.eglReleaseThread();
                EGL14.eglTerminate(eglDisplay);
            }
            if (inputSurface != null) {
                try { inputSurface.release(); } catch (Throwable ignored) {}
                inputSurface = null;
            }
            eglDisplay = EGL14.EGL_NO_DISPLAY;
            eglContext = EGL14.EGL_NO_CONTEXT;
            eglSurface = EGL14.EGL_NO_SURFACE;
        }
    }

    private static final class DecoderOutputSurface implements SurfaceTexture.OnFrameAvailableListener {
        private static final String VERTEX_SHADER =
                "uniform mat4 uMVPMatrix;\n" +
                "uniform mat4 uSTMatrix;\n" +
                "attribute vec4 aPosition;\n" +
                "attribute vec4 aTextureCoord;\n" +
                "varying vec2 vTextureCoord;\n" +
                "void main() {\n" +
                "  gl_Position = uMVPMatrix * aPosition;\n" +
                "  vTextureCoord = (uSTMatrix * aTextureCoord).xy;\n" +
                "}\n";

        private static final String FRAGMENT_SHADER =
                "#extension GL_OES_EGL_image_external : require\n" +
                "precision mediump float;\n" +
                "varying vec2 vTextureCoord;\n" +
                "uniform samplerExternalOES sTexture;\n" +
                "void main() {\n" +
                "  gl_FragColor = texture2D(sTexture, vTextureCoord);\n" +
                "}\n";

        private static final float[] TRIANGLE_VERTICES = {
                -1.0f, -1.0f, 0f, 0f, 0f,
                 1.0f, -1.0f, 0f, 1f, 0f,
                -1.0f,  1.0f, 0f, 0f, 1f,
                 1.0f,  1.0f, 0f, 1f, 1f
        };

        private final FloatBuffer vertices;
        private final float[] mvpMatrix = new float[16];
        private final float[] stMatrix = new float[16];
        private final Object frameSync = new Object();
        private boolean frameAvailable = false;

        private HandlerThread listenerThread;
        private SurfaceTexture surfaceTexture;
        private Surface surface;
        private int program;
        private int textureId;
        private int muMVPMatrixHandle;
        private int muSTMatrixHandle;
        private int maPositionHandle;
        private int maTextureHandle;

        DecoderOutputSurface() throws IOException {
            vertices = ByteBuffer.allocateDirect(TRIANGLE_VERTICES.length * 4)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer();
            vertices.put(TRIANGLE_VERTICES).position(0);
            Matrix.setIdentityM(mvpMatrix, 0);
            Matrix.setIdentityM(stMatrix, 0);

            initGl();

            listenerThread = new HandlerThread("VideoCompressGlSync");
            listenerThread.start();
            Handler handler = new Handler(listenerThread.getLooper());
            surfaceTexture = new SurfaceTexture(textureId);
            surfaceTexture.setOnFrameAvailableListener(this, handler);
            surface = new Surface(surfaceTexture);
        }

        private void initGl() throws IOException {
            int vs = compileShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER);
            int fs = compileShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
            program = GLES20.glCreateProgram();
            GLES20.glAttachShader(program, vs);
            GLES20.glAttachShader(program, fs);
            GLES20.glLinkProgram(program);
            int[] linkStatus = new int[1];
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
            if (linkStatus[0] != GLES20.GL_TRUE) {
                throw new IOException("GL program link failed");
            }
            maPositionHandle = GLES20.glGetAttribLocation(program, "aPosition");
            maTextureHandle = GLES20.glGetAttribLocation(program, "aTextureCoord");
            muMVPMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix");
            muSTMatrixHandle = GLES20.glGetUniformLocation(program, "uSTMatrix");

            int[] textures = new int[1];
            GLES20.glGenTextures(1, textures, 0);
            textureId = textures[0];
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
            GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        }

        private int compileShader(int type, String src) throws IOException {
            int shader = GLES20.glCreateShader(type);
            GLES20.glShaderSource(shader, src);
            GLES20.glCompileShader(shader);
            int[] compiled = new int[1];
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
            if (compiled[0] == 0) {
                GLES20.glDeleteShader(shader);
                throw new IOException("GL shader compile failed");
            }
            return shader;
        }

        Surface getSurface() {
            return surface;
        }

        @Override
        public void onFrameAvailable(SurfaceTexture st) {
            synchronized (frameSync) {
                frameAvailable = true;
                frameSync.notifyAll();
            }
        }

        void awaitNewImage() throws IOException {
            synchronized (frameSync) {
                long deadline = System.currentTimeMillis() + 2500L;
                while (!frameAvailable) {
                    long waitMs = deadline - System.currentTimeMillis();
                    if (waitMs <= 0) {
                        throw new IOException("חריגת זמן בהמתנה לפריים וידאו");
                    }
                    try {
                        frameSync.wait(waitMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("תהליך הדחיסה הופסק");
                    }
                }
                frameAvailable = false;
            }
            surfaceTexture.updateTexImage();
            surfaceTexture.getTransformMatrix(stMatrix);
        }

        void drawImage() {
            GLES20.glClearColor(0f, 0f, 0f, 1f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            GLES20.glUseProgram(program);

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);

            vertices.position(0);
            GLES20.glVertexAttribPointer(maPositionHandle, 3, GLES20.GL_FLOAT, false, 20, vertices);
            GLES20.glEnableVertexAttribArray(maPositionHandle);

            vertices.position(3);
            GLES20.glVertexAttribPointer(maTextureHandle, 2, GLES20.GL_FLOAT, false, 20, vertices);
            GLES20.glEnableVertexAttribArray(maTextureHandle);

            GLES20.glUniformMatrix4fv(muMVPMatrixHandle, 1, false, mvpMatrix, 0);
            GLES20.glUniformMatrix4fv(muSTMatrixHandle, 1, false, stMatrix, 0);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
            GLES20.glFinish();
        }

        void release() {
            if (surface != null) {
                try { surface.release(); } catch (Throwable ignored) {}
                surface = null;
            }
            if (surfaceTexture != null) {
                try { surfaceTexture.release(); } catch (Throwable ignored) {}
                surfaceTexture = null;
            }
            if (listenerThread != null) {
                listenerThread.quitSafely();
                listenerThread = null;
            }
        }
    }
}
