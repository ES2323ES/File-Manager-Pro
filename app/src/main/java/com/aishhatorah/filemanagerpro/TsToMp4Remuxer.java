package com.aishhatorah.filemanagerpro;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * מנוע המרה וחיתוך ישיר של קבצי MPEG-TS / BDAV (.mts / .m2ts / .ts) אל קובץ MP4 תקני (H.264/HEVC + AAC):
 * - מנרמל אוטומטית פאקטים של 192 בתים (BDAV), 204 בתים (FEC) או 188 בתים (TS).
 * - מפענח PAT/PMT, מאחד מנות PES, מתקן קפיצות/איפוסי PTS/PCR לזמן מונוטוני רציף.
 * - תומך בחיתוך טווח [startUs, endUs] מתחילת Keyframe נקי, ובהמרת שמע AC-3/MP2/LPCM ל-AAC
 *   (או דילוג שקט על ערוץ השמע אם חסר במכשיר מפענח AC-3, ללא דרישה למפענח חיצוני).
 */
public final class TsToMp4Remuxer {

    public static final int STREAM_VIDEO_MPEG1 = 0x01;
    public static final int STREAM_VIDEO_MPEG2 = 0x02;
    public static final int STREAM_AUDIO_MP2 = 0x03;
    public static final int STREAM_AUDIO_MP3 = 0x04;
    public static final int STREAM_PRIVATE_DATA = 0x06;
    public static final int STREAM_AUDIO_AAC_ADTS = 0x0F;
    public static final int STREAM_VIDEO_H264 = 0x1B;
    public static final int STREAM_VIDEO_HEVC = 0x24;
    public static final int STREAM_AUDIO_BDAV_LPCM = 0x80;
    public static final int STREAM_AUDIO_AC3 = 0x81;
    public static final int STREAM_AUDIO_EAC3 = 0x87;

    private static final int TS_PACKET_SIZE = MpegTsNormalizer.TS_PACKET_SIZE;
    private static final byte TS_SYNC_BYTE = 0x47;
    private static final int[] ADTS_SAMPLE_RATES = {
            96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050,
            16000, 12000, 11025, 8000, 7350, 0, 0, 0
    };

    private TsToMp4Remuxer() {
    }

    public static final class StreamMetadata {
        public int pmtPid = -1;
        public int videoPid = -1;
        public int videoStreamType = -1;
        public int audioPid = -1;
        public int audioStreamType = -1;
        public byte[] h264Sps;
        public byte[] h264Pps;
        public byte[] hevcVps;
        public byte[] hevcSps;
        public byte[] hevcPps;
        public int videoWidth = 1920;
        public int videoHeight = 1080;
        public byte[] aacCsd0;
        public int aacSampleRate = 48000;
        public int aacChannels = 2;
        public boolean audioTranscodeSupported = true;

        public boolean hasVideoFormat() {
            if (videoStreamType == STREAM_VIDEO_H264) {
                return h264Sps != null && h264Pps != null;
            }
            if (videoStreamType == STREAM_VIDEO_HEVC) {
                return hevcVps != null && hevcSps != null && hevcPps != null;
            }
            return false;
        }
    }

    public static final class RemuxResult {
        public final boolean success;
        public final int videoFramesWritten;
        public final int audioFramesWritten;
        public final boolean droppedUnsupportedAudio;
        public final String message;

        public RemuxResult(boolean success, int videoFramesWritten, int audioFramesWritten,
                           boolean droppedUnsupportedAudio, String message) {
            this.success = success;
            this.videoFramesWritten = videoFramesWritten;
            this.audioFramesWritten = audioFramesWritten;
            this.droppedUnsupportedAudio = droppedUnsupportedAudio;
            this.message = message;
        }
    }

    public interface SampleSink {
        void onConfigure(StreamMetadata meta) throws IOException;
        void onVideoSample(byte[] data, int len, long timeUs, boolean isKeyFrame) throws IOException;
        void onAudioSample(byte[] data, int len, long timeUs) throws IOException;
        void onFinish() throws IOException;
    }

    public interface ProgressCallback {
        void onProgress(int percent);
    }

    /**
     * מונוטוניזציה של חותמות PTS מ-90kHz למיקרו-שניות תוך התמודדות עם איפוסי PCR/PTS או גלישת 33-bit.
     */
    public static final class MonotonicPtsClock {
        private long originRaw90k = -1L;
        private long lastRaw90k = -1L;
        private long offsetUs = 0L;
        private long lastOutUs = -1L;
        private final long defaultStepUs;

        public MonotonicPtsClock(long defaultStepUs) {
            this.defaultStepUs = Math.max(1000L, defaultStepUs);
        }

        public void setOriginIfUnset(long raw90k) {
            if (originRaw90k < 0L && raw90k >= 0L) {
                originRaw90k = raw90k;
            }
        }

        public boolean hasOrigin() {
            return originRaw90k >= 0L;
        }

        public long getOriginRaw90k() {
            return originRaw90k;
        }

        public long normalize90k(long rawPts90k) {
            if (rawPts90k < 0L) {
                long out = lastOutUs < 0L ? 0L : (lastOutUs + defaultStepUs);
                lastOutUs = out;
                return out;
            }
            if (originRaw90k < 0L) {
                originRaw90k = rawPts90k;
            }
            if (lastRaw90k < 0L) {
                lastRaw90k = rawPts90k;
                long initialUs = ((rawPts90k - originRaw90k) * 100L) / 9L;
                if (initialUs < 0L || initialUs > 5_000_000L) {
                    offsetUs = -initialUs;
                    initialUs = 0L;
                }
                lastOutUs = initialUs;
                return initialUs;
            }

            long delta90k = rawPts90k - lastRaw90k;
            // טיפול בגלישת 33-bit
            if (delta90k < -(1L << 32)) {
                delta90k += (1L << 33);
            } else if (delta90k > (1L << 32)) {
                delta90k -= (1L << 33);
            }

            // אם יש קפיצה אחורה (> 0.25 שניה) או קפיצה קדימה מוגזמת (> 4 שניות) עקב איפוס/מיזוג
            if (delta90k < -22500L || delta90k > 360000L) {
                long expectedOutUs = lastOutUs + defaultStepUs;
                long rawUsFromOrigin = ((rawPts90k - originRaw90k) * 100L) / 9L;
                offsetUs = expectedOutUs - rawUsFromOrigin;
                lastRaw90k = rawPts90k;
                lastOutUs = expectedOutUs;
                return expectedOutUs;
            }

            lastRaw90k = rawPts90k;
            long rawUsFromOrigin = ((rawPts90k - originRaw90k) * 100L) / 9L;
            long candidate = rawUsFromOrigin + offsetUs;
            if (candidate <= lastOutUs) {
                candidate = lastOutUs + Math.max(1000L, defaultStepUs / 2L);
            }
            lastOutUs = candidate;
            return candidate;
        }
    }

    public static RemuxResult remuxToMp4(File inputFile, File outputMp4, long startUs, long endUs,
                                          ProgressCallback progressCb) throws IOException {
        AndroidMuxerSink sink = new AndroidMuxerSink(outputMp4);
        try {
            return demuxAndProcess(inputFile, startUs, endUs, sink, progressCb);
        } finally {
            sink.releaseQuietly();
        }
    }

    public static RemuxResult demuxAndProcess(File inputFile, long startUs, long endUs,
                                              SampleSink sink, ProgressCallback progressCb) throws IOException {
        if (inputFile == null || !inputFile.isFile()) {
            throw new IOException("קובץ הקלט אינו קיים");
        }
        MpegTsNormalizer.TsFormatInfo info = MpegTsNormalizer.detectTsLayoutCached(inputFile);
        if (info.packetStride <= 0 || info.packetCount <= 0L) {
            throw new IOException("מבנה קובץ ה-MTS/TS אינו תקין");
        }

        final boolean isTrimmed = startUs > 0L || (endUs > 0L && endUs < Long.MAX_VALUE / 2);
        final long effectiveStartUs = Math.max(0L, startUs);
        final long effectiveEndUs = endUs > effectiveStartUs ? endUs : Long.MAX_VALUE;

        StreamMetadata meta = new StreamMetadata();
        MonotonicPtsClock videoClock = new MonotonicPtsClock(33333L);
        MonotonicPtsClock audioClock = new MonotonicPtsClock(21333L);

        TsPacketSource pktSource = openPacketSource(inputFile, info);
        byte[] pkt = new byte[TS_PACKET_SIZE];

        ByteArrayOutputStream patAcc = new ByteArrayOutputStream(1024);
        ByteArrayOutputStream pmtAcc = new ByteArrayOutputStream(1024);
        ByteArrayOutputStream videoPes = new ByteArrayOutputStream(128 * 1024);
        ByteArrayOutputStream audioPes = new ByteArrayOutputStream(32 * 1024);

        boolean configured = false;
        boolean startedOutput = false;
        long baseTimeUs = -1L;
        int videoFramesWritten = 0;
        int audioFramesWritten = 0;
        long totalPackets = info.packetCount;
        long pktIndex = 0L;
        int lastPct = -1;

        List<BufferedFrame> preRollVideo = new ArrayList<>();
        List<BufferedFrame> preRollAudio = new ArrayList<>();

        try {
            while (pktSource.nextPacket(pkt)) {
                pktIndex++;
                if (progressCb != null && totalPackets > 0 && (pktIndex & 0x7FFL) == 0L) {
                    int pct = (int) Math.min(99L, (pktIndex * 100L) / totalPackets);
                    if (pct != lastPct) {
                        lastPct = pct;
                        progressCb.onProgress(pct);
                    }
                }

                if (pkt[0] != TS_SYNC_BYTE) {
                    continue;
                }
                boolean pusi = (pkt[1] & 0x40) != 0;
                int pid = ((pkt[1] & 0x1F) << 8) | (pkt[2] & 0xFF);
                int afc = (pkt[3] >> 4) & 0x03;
                if ((afc & 0x01) == 0) {
                    continue; // אין payload
                }
                int payloadOff = 4;
                if ((afc & 0x02) != 0) {
                    int afLen = pkt[4] & 0xFF;
                    payloadOff += 1 + afLen;
                }
                if (payloadOff >= TS_PACKET_SIZE) {
                    continue;
                }

                if (pid == 0x0000) {
                    if (pusi) patAcc.reset();
                    patAcc.write(pkt, payloadOff, TS_PACKET_SIZE - payloadOff);
                    if (meta.pmtPid < 0) {
                        int parsedPmt = parsePatForPmtPid(patAcc.toByteArray());
                        if (parsedPmt > 0) meta.pmtPid = parsedPmt;
                    }
                } else if (meta.pmtPid > 0 && pid == meta.pmtPid) {
                    if (pusi) pmtAcc.reset();
                    pmtAcc.write(pkt, payloadOff, TS_PACKET_SIZE - payloadOff);
                    if (meta.videoPid < 0) {
                        parsePmtStreams(pmtAcc.toByteArray(), meta);
                    }
                } else if (meta.videoPid > 0 && pid == meta.videoPid) {
                    if (pusi && videoPes.size() > 0) {
                        byte[] pesBytes = videoPes.toByteArray();
                        videoPes.reset();
                        ParsedVideoSample vs = processVideoPes(pesBytes, meta, videoClock);
                        if (vs != null && videoClock.hasOrigin() && !audioClock.hasOrigin()) {
                            audioClock.setOriginIfUnset(videoClock.getOriginRaw90k());
                        }
                        if (vs != null) {
                            if (!configured && meta.hasVideoFormat()) {
                                // אם יש ערוץ AAC, נחכה מעט עד שיזוהה CSD אם עדיין לא זוהה
                                if (meta.audioPid <= 0
                                        || meta.audioStreamType != STREAM_AUDIO_AAC_ADTS
                                        || meta.aacCsd0 != null
                                        || vs.timeUs > 600_000L
                                        || (isTrimmed && vs.timeUs >= effectiveStartUs)) {
                                    sink.onConfigure(meta);
                                    configured = true;
                                }
                            }
                            if (vs.timeUs > effectiveEndUs) {
                                break;
                            }
                            if (!configured) {
                                if (vs.isKeyFrame) {
                                    preRollVideo.clear();
                                }
                                if (preRollVideo.size() < 240) {
                                    preRollVideo.add(new BufferedFrame(vs.data, vs.timeUs, vs.isKeyFrame));
                                }
                            } else if (!isTrimmed || vs.timeUs >= effectiveStartUs) {
                                if (!startedOutput) {
                                    startedOutput = true;
                                    int kfIdx = findLastKeyframeIndex(preRollVideo);
                                    if (kfIdx >= 0) {
                                        baseTimeUs = preRollVideo.get(kfIdx).timeUs;
                                        for (int i = kfIdx; i < preRollVideo.size(); i++) {
                                            BufferedFrame bf = preRollVideo.get(i);
                                            sink.onVideoSample(bf.data, bf.data.length,
                                                    Math.max(0L, bf.timeUs - baseTimeUs), bf.isKeyFrame);
                                            videoFramesWritten++;
                                        }
                                        for (BufferedFrame af : preRollAudio) {
                                            if (af.timeUs >= baseTimeUs) {
                                                sink.onAudioSample(af.data, af.data.length, af.timeUs - baseTimeUs);
                                                audioFramesWritten++;
                                            }
                                        }
                                    } else {
                                        baseTimeUs = vs.timeUs;
                                    }
                                    preRollVideo.clear();
                                    preRollAudio.clear();
                                }
                                long outUs = Math.max(0L, vs.timeUs - baseTimeUs);
                                sink.onVideoSample(vs.data, vs.data.length, outUs, vs.isKeyFrame);
                                videoFramesWritten++;
                            } else {
                                if (vs.isKeyFrame) {
                                    preRollVideo.clear();
                                }
                                if (preRollVideo.size() < 240) {
                                    preRollVideo.add(new BufferedFrame(vs.data, vs.timeUs, vs.isKeyFrame));
                                }
                            }
                        }
                    }
                    videoPes.write(pkt, payloadOff, TS_PACKET_SIZE - payloadOff);
                } else if (meta.audioPid > 0 && pid == meta.audioPid) {
                    if (pusi && audioPes.size() > 0) {
                        byte[] pesBytes = audioPes.toByteArray();
                        audioPes.reset();
                        List<ParsedAudioFrame> aFrames = processAudioPes(pesBytes, meta, audioClock);
                        if (audioClock.hasOrigin() && !videoClock.hasOrigin()) {
                            videoClock.setOriginIfUnset(audioClock.getOriginRaw90k());
                        }
                        for (ParsedAudioFrame af : aFrames) {
                            if (af.timeUs > effectiveEndUs) continue;
                            if (startedOutput && configured) {
                                long outUs = Math.max(0L, af.timeUs - baseTimeUs);
                                sink.onAudioSample(af.data, af.data.length, outUs);
                                audioFramesWritten++;
                            } else if (!configured || (isTrimmed && af.timeUs >= Math.max(0L, effectiveStartUs - 2_000_000L))) {
                                if (preRollAudio.size() < 240) {
                                    preRollAudio.add(new BufferedFrame(af.data, af.timeUs, true));
                                }
                            }
                        }
                    }
                    audioPes.write(pkt, payloadOff, TS_PACKET_SIZE - payloadOff);
                }
            }

            // שטיפת חבילות סיום
            if (videoPes.size() > 0) {
                ParsedVideoSample vs = processVideoPes(videoPes.toByteArray(), meta, videoClock);
                if (vs != null && vs.timeUs <= effectiveEndUs) {
                    if (!configured && meta.hasVideoFormat()) {
                        sink.onConfigure(meta);
                        configured = true;
                    }
                    if (configured && !startedOutput && meta.hasVideoFormat()) {
                        startedOutput = true;
                        int kfIdx = findLastKeyframeIndex(preRollVideo);
                        if (kfIdx >= 0) {
                            baseTimeUs = preRollVideo.get(kfIdx).timeUs;
                            for (int i = kfIdx; i < preRollVideo.size(); i++) {
                                BufferedFrame bf = preRollVideo.get(i);
                                sink.onVideoSample(bf.data, bf.data.length,
                                        Math.max(0L, bf.timeUs - baseTimeUs), bf.isKeyFrame);
                                videoFramesWritten++;
                            }
                            for (BufferedFrame af : preRollAudio) {
                                if (af.timeUs >= baseTimeUs) {
                                    sink.onAudioSample(af.data, af.data.length, af.timeUs - baseTimeUs);
                                    audioFramesWritten++;
                                }
                            }
                            preRollVideo.clear();
                            preRollAudio.clear();
                        } else {
                            baseTimeUs = vs.timeUs;
                        }
                    }
                    if (startedOutput && configured) {
                        sink.onVideoSample(vs.data, vs.data.length, Math.max(0L, vs.timeUs - baseTimeUs), vs.isKeyFrame);
                        videoFramesWritten++;
                    }
                }
            }
            if (audioPes.size() > 0 && startedOutput && configured) {
                List<ParsedAudioFrame> aFrames = processAudioPes(audioPes.toByteArray(), meta, audioClock);
                for (ParsedAudioFrame af : aFrames) {
                    if (af.timeUs <= effectiveEndUs) {
                        sink.onAudioSample(af.data, af.data.length, Math.max(0L, af.timeUs - baseTimeUs));
                        audioFramesWritten++;
                    }
                }
            }

            if (!configured || videoFramesWritten == 0) {
                throw new IOException("לא נמצא זרם וידאו H.264/HEVC תקין בקובץ זה");
            }
            sink.onFinish();
            if (progressCb != null) progressCb.onProgress(100);
            return new RemuxResult(true, videoFramesWritten, audioFramesWritten,
                    !meta.audioTranscodeSupported, "OK");
        } finally {
            pktSource.close();
        }
    }

    private static int findLastKeyframeIndex(List<BufferedFrame> frames) {
        for (int i = frames.size() - 1; i >= 0; i--) {
            if (frames.get(i).isKeyFrame) return i;
        }
        return frames.isEmpty() ? -1 : 0;
    }

    // ==================== מפרק טבלאות PAT / PMT / PES ====================

    public static int parsePatForPmtPid(byte[] payload) {
        if (payload == null || payload.length < 13) return -1;
        int pointer = payload[0] & 0xFF;
        int pos = 1 + pointer;
        if (pos + 12 > payload.length) return -1;
        if ((payload[pos] & 0xFF) != 0x00) return -1; // table_id != PAT
        int sectionLen = ((payload[pos + 1] & 0x0F) << 8) | (payload[pos + 2] & 0xFF);
        int end = Math.min(payload.length, pos + 3 + sectionLen - 4);
        int entryPos = pos + 8;
        while (entryPos + 4 <= end) {
            int progNum = ((payload[entryPos] & 0xFF) << 8) | (payload[entryPos + 1] & 0xFF);
            int pid = ((payload[entryPos + 2] & 0x1F) << 8) | (payload[entryPos + 3] & 0xFF);
            if (progNum != 0 && pid > 0) {
                return pid;
            }
            entryPos += 4;
        }
        return -1;
    }

    public static void parsePmtStreams(byte[] payload, StreamMetadata meta) {
        if (payload == null || payload.length < 16) return;
        int pointer = payload[0] & 0xFF;
        int pos = 1 + pointer;
        if (pos + 12 > payload.length) return;
        if ((payload[pos] & 0xFF) != 0x02) return; // table_id != PMT
        int sectionLen = ((payload[pos + 1] & 0x0F) << 8) | (payload[pos + 2] & 0xFF);
        int end = Math.min(payload.length, pos + 3 + sectionLen - 4);
        int progInfoLen = ((payload[pos + 10] & 0x0F) << 8) | (payload[pos + 11] & 0xFF);
        int esPos = pos + 12 + progInfoLen;
        while (esPos + 5 <= end) {
            int streamType = payload[esPos] & 0xFF;
            int elemPid = ((payload[esPos + 1] & 0x1F) << 8) | (payload[esPos + 2] & 0xFF);
            int esInfoLen = ((payload[esPos + 3] & 0x0F) << 8) | (payload[esPos + 4] & 0xFF);

            if (streamType == STREAM_VIDEO_H264 || streamType == STREAM_VIDEO_HEVC
                    || streamType == STREAM_VIDEO_MPEG2 || streamType == STREAM_VIDEO_MPEG1) {
                if (meta.videoPid < 0) {
                    meta.videoPid = elemPid;
                    meta.videoStreamType = streamType;
                }
            } else if (streamType == STREAM_AUDIO_AAC_ADTS || streamType == STREAM_AUDIO_MP2
                    || streamType == STREAM_AUDIO_MP3 || streamType == STREAM_AUDIO_AC3
                    || streamType == STREAM_AUDIO_EAC3 || streamType == STREAM_AUDIO_BDAV_LPCM) {
                if (meta.audioPid < 0) {
                    meta.audioPid = elemPid;
                    meta.audioStreamType = streamType;
                }
            } else if (streamType == STREAM_PRIVATE_DATA && meta.audioPid < 0) {
                int descEnd = Math.min(end, esPos + 5 + esInfoLen);
                int resolvedType = detectPrivateAudioDescriptor(payload, esPos + 5, descEnd);
                if (resolvedType > 0) {
                    meta.audioPid = elemPid;
                    meta.audioStreamType = resolvedType;
                }
            }
            esPos += 5 + esInfoLen;
        }
    }

    private static int detectPrivateAudioDescriptor(byte[] buf, int start, int end) {
        int pos = start;
        while (pos + 2 <= end) {
            int tag = buf[pos] & 0xFF;
            int len = buf[pos + 1] & 0xFF;
            if (tag == 0x6A || tag == 0x81) return STREAM_AUDIO_AC3;
            if (tag == 0x7A) return STREAM_AUDIO_EAC3;
            if (tag == 0x05 && len >= 4 && pos + 6 <= end) {
                // Registration descriptor "AC-3"
                if (buf[pos + 2] == 'A' && buf[pos + 3] == 'C' && buf[pos + 4] == '-' && buf[pos + 5] == '3') {
                    return STREAM_AUDIO_AC3;
                }
            }
            pos += 2 + len;
        }
        return -1;
    }

    private static long parsePesPts(byte[] pes) {
        if (pes == null || pes.length < 14) return -1L;
        if (pes[0] != 0 || pes[1] != 0 || pes[2] != 1) return -1L;
        int ptsDtsFlags = (pes[7] >> 6) & 0x03;
        if ((ptsDtsFlags & 0x02) == 0) return -1L;
        int hdrLen = pes[8] & 0xFF;
        if (hdrLen < 5 || 9 + 5 > pes.length) return -1L;
        long p0 = pes[9] & 0xFFL;
        long p1 = pes[10] & 0xFFL;
        long p2 = pes[11] & 0xFFL;
        long p3 = pes[12] & 0xFFL;
        long p4 = pes[13] & 0xFFL;
        return ((p0 & 0x0EL) << 29)
                | ((p1 & 0xFFL) << 22)
                | ((p2 & 0xFEL) << 14)
                | ((p3 & 0xFFL) << 7)
                | ((p4 & 0xFEL) >> 1);
    }

    private static int getPesPayloadOffset(byte[] pes) {
        if (pes == null || pes.length < 9) return -1;
        if (pes[0] != 0 || pes[1] != 0 || pes[2] != 1) return -1;
        int hdrLen = pes[8] & 0xFF;
        int off = 9 + hdrLen;
        return off < pes.length ? off : -1;
    }

    // ==================== עיבוד וידאו (H.264 / HEVC Annex-B) ====================

    private static ParsedVideoSample processVideoPes(byte[] pes, StreamMetadata meta, MonotonicPtsClock clock) {
        int payloadOff = getPesPayloadOffset(pes);
        if (payloadOff < 0) return null;

        long rawPts = parsePesPts(pes);
        long timeUs = clock.normalize90k(rawPts);

        List<byte[]> nals = splitAnnexBNalUnits(pes, payloadOff, pes.length);
        if (nals.isEmpty()) return null;

        ByteArrayOutputStream normalizedSample = new ByteArrayOutputStream(pes.length - payloadOff + 32);
        boolean isKeyFrame = false;
        boolean hasSps = false;
        boolean hasVclSlice = false;

        for (byte[] nal : nals) {
            if (nal.length == 0) continue;
            if (meta.videoStreamType == STREAM_VIDEO_H264) {
                int nalType = nal[0] & 0x1F;
                if (nalType == 7) {
                    hasSps = true;
                    if (meta.h264Sps == null) {
                        meta.h264Sps = withStartCode(nal);
                        int[] dims = parseH264SpsDimensions(nal);
                        if (dims != null) {
                            meta.videoWidth = dims[0];
                            meta.videoHeight = dims[1];
                        }
                    }
                    writeStartCodeAndNal(normalizedSample, nal);
                    continue;
                } else if (nalType == 8) {
                    if (meta.h264Pps == null) {
                        meta.h264Pps = withStartCode(nal);
                    }
                    writeStartCodeAndNal(normalizedSample, nal);
                    continue;
                } else if (nalType == 5) {
                    isKeyFrame = true;
                    hasVclSlice = true;
                } else if (nalType >= 1 && nalType <= 4) {
                    hasVclSlice = true;
                } else if (nalType == 9 || nalType == 12) {
                    // AUD / Filler: לא חיוני בתוך sample של MP4
                    continue;
                }
                writeStartCodeAndNal(normalizedSample, nal);
            } else if (meta.videoStreamType == STREAM_VIDEO_HEVC) {
                int nalType = (nal[0] >> 1) & 0x3F;
                if (nalType == 32 && meta.hevcVps == null) {
                    meta.hevcVps = withStartCode(nal);
                } else if (nalType == 33) {
                    hasSps = true;
                    if (meta.hevcSps == null) {
                        meta.hevcSps = withStartCode(nal);
                    }
                } else if (nalType == 34 && meta.hevcPps == null) {
                    meta.hevcPps = withStartCode(nal);
                } else if (nalType >= 16 && nalType <= 21) {
                    isKeyFrame = true;
                    hasVclSlice = true;
                } else if (nalType <= 9) {
                    hasVclSlice = true;
                } else if (nalType == 35 || nalType == 38) {
                    continue;
                }
                writeStartCodeAndNal(normalizedSample, nal);
            }
        }

        if (!hasVclSlice || normalizedSample.size() == 0) {
            return null;
        }
        return new ParsedVideoSample(normalizedSample.toByteArray(), timeUs, isKeyFrame || hasSps);
    }

    public static List<byte[]> splitAnnexBNalUnits(byte[] buf, int start, int end) {
        List<byte[]> out = new ArrayList<>();
        int i = start;
        int nalStart = -1;
        while (i + 2 < end) {
            int scLen = 0;
            if (i + 3 < end && buf[i] == 0 && buf[i + 1] == 0 && buf[i + 2] == 0 && buf[i + 3] == 1) {
                scLen = 4;
            } else if (buf[i] == 0 && buf[i + 1] == 0 && buf[i + 2] == 1) {
                scLen = 3;
            }
            if (scLen > 0) {
                if (nalStart >= 0 && i > nalStart) {
                    out.add(Arrays.copyOfRange(buf, nalStart, i));
                }
                i += scLen;
                nalStart = i;
            } else {
                i++;
            }
        }
        if (nalStart >= 0 && end > nalStart) {
            out.add(Arrays.copyOfRange(buf, nalStart, end));
        }
        return out;
    }

    private static byte[] withStartCode(byte[] nal) {
        byte[] out = new byte[4 + nal.length];
        out[3] = 1;
        System.arraycopy(nal, 0, out, 4, nal.length);
        return out;
    }

    private static void writeStartCodeAndNal(ByteArrayOutputStream out, byte[] nal) {
        out.write(0);
        out.write(0);
        out.write(0);
        out.write(1);
        out.write(nal, 0, nal.length);
    }

    // ==================== עיבוד שמע (AAC ADTS + חילוץ CSD) ====================

    private static List<ParsedAudioFrame> processAudioPes(byte[] pes, StreamMetadata meta, MonotonicPtsClock clock) {
        List<ParsedAudioFrame> out = new ArrayList<>();
        int payloadOff = getPesPayloadOffset(pes);
        if (payloadOff < 0) return out;

        long rawPts = parsePesPts(pes);
        long baseUs = clock.normalize90k(rawPts);

        if (meta.audioStreamType == STREAM_AUDIO_AAC_ADTS) {
            int pos = payloadOff;
            int end = pes.length;
            int frameIdx = 0;
            while (pos + 7 <= end) {
                if ((pes[pos] & 0xFF) != 0xFF || (pes[pos + 1] & 0xF0) != 0xF0) {
                    pos++;
                    continue;
                }
                boolean noCrc = (pes[pos + 1] & 0x01) != 0;
                int hdrLen = noCrc ? 7 : 9;
                int frameLen = ((pes[pos + 3] & 0x03) << 11)
                        | ((pes[pos + 4] & 0xFF) << 3)
                        | ((pes[pos + 5] & 0xE0) >> 5);
                if (frameLen < hdrLen || pos + frameLen > end) {
                    break;
                }
                if (meta.aacCsd0 == null) {
                    int profile = (pes[pos + 2] >> 6) & 0x03;
                    int freqIdx = (pes[pos + 2] >> 2) & 0x0F;
                    int ch = ((pes[pos + 2] & 0x01) << 2) | ((pes[pos + 3] >> 6) & 0x03);
                    int audioObjType = profile + 1;
                    meta.aacSampleRate = (freqIdx < ADTS_SAMPLE_RATES.length && ADTS_SAMPLE_RATES[freqIdx] > 0)
                            ? ADTS_SAMPLE_RATES[freqIdx] : 48000;
                    meta.aacChannels = ch > 0 ? ch : 2;
                    meta.aacCsd0 = new byte[]{
                            (byte) ((audioObjType << 3) | (freqIdx >> 1)),
                            (byte) (((freqIdx & 1) << 7) | (meta.aacChannels << 3))
                    };
                }
                int payloadLen = frameLen - hdrLen;
                byte[] rawFrame = Arrays.copyOfRange(pes, pos + hdrLen, pos + frameLen);
                long frameDurUs = (1024L * 1_000_000L) / Math.max(8000, meta.aacSampleRate);
                out.add(new ParsedAudioFrame(rawFrame, baseUs + frameIdx * frameDurUs));
                frameIdx++;
                pos += frameLen;
            }
        } else if (meta.audioStreamType == STREAM_AUDIO_AC3 || meta.audioStreamType == STREAM_AUDIO_EAC3
                || meta.audioStreamType == STREAM_AUDIO_MP2 || meta.audioStreamType == STREAM_AUDIO_MP3
                || meta.audioStreamType == STREAM_AUDIO_BDAV_LPCM) {
            byte[] payload = Arrays.copyOfRange(pes, payloadOff, pes.length);
            out.add(new ParsedAudioFrame(payload, baseUs));
        }
        return out;
    }

    // ==================== מנתח מימדי H.264 SPS ====================

    public static int[] parseH264SpsDimensions(byte[] nal) {
        try {
            if (nal == null || nal.length < 5) return null;
            byte[] rbsp = removeEmulationPrevention(nal, 1, nal.length);
            BitReader br = new BitReader(rbsp);
            int profileIdc = br.readBits(8);
            br.readBits(8); // constraints + reserved
            br.readBits(8); // level_idc
            br.readUe(); // seq_parameter_set_id

            if (profileIdc == 100 || profileIdc == 110 || profileIdc == 122 || profileIdc == 244
                    || profileIdc == 44 || profileIdc == 83 || profileIdc == 86 || profileIdc == 118
                    || profileIdc == 128 || profileIdc == 138) {
                int chromaFormatIdc = br.readUe();
                if (chromaFormatIdc == 3) br.readBits(1);
                br.readUe(); // bit_depth_luma_minus8
                br.readUe(); // bit_depth_chroma_minus8
                br.readBits(1); // qpprime_y_zero_transform_bypass_flag
                if (br.readBits(1) == 1) {
                    int limit = chromaFormatIdc != 3 ? 8 : 12;
                    for (int i = 0; i < limit; i++) {
                        if (br.readBits(1) == 1) {
                            skipScalingList(br, i < 6 ? 16 : 64);
                        }
                    }
                }
            }

            br.readUe(); // log2_max_frame_num_minus4
            int picOrderCntType = br.readUe();
            if (picOrderCntType == 0) {
                br.readUe();
            } else if (picOrderCntType == 1) {
                br.readBits(1);
                br.readSe();
                br.readSe();
                int count = br.readUe();
                for (int i = 0; i < count; i++) br.readSe();
            }
            br.readUe(); // max_num_ref_frames
            br.readBits(1); // gaps_in_frame_num_value_allowed_flag
            int picWidthInMbsMinus1 = br.readUe();
            int picHeightInMapUnitsMinus1 = br.readUe();
            int frameMbsOnlyFlag = br.readBits(1);
            if (frameMbsOnlyFlag == 0) br.readBits(1);
            br.readBits(1); // direct_8x8_inference_flag

            int cropLeft = 0, cropRight = 0, cropTop = 0, cropBottom = 0;
            if (br.readBits(1) == 1) {
                cropLeft = br.readUe();
                cropRight = br.readUe();
                cropTop = br.readUe();
                cropBottom = br.readUe();
            }

            int width = (picWidthInMbsMinus1 + 1) * 16 - (cropLeft + cropRight) * 2;
            int height = (2 - frameMbsOnlyFlag) * (picHeightInMapUnitsMinus1 + 1) * 16 - (cropTop + cropBottom) * 2;
            if (width >= 64 && height >= 64 && width <= 7680 && height <= 4320) {
                return new int[]{width, height};
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static void skipScalingList(BitReader br, int size) {
        int lastScale = 8;
        int nextScale = 8;
        for (int j = 0; j < size; j++) {
            if (nextScale != 0) {
                int deltaScale = br.readSe();
                nextScale = (lastScale + deltaScale + 256) % 256;
            }
            lastScale = nextScale == 0 ? lastScale : nextScale;
        }
    }

    private static byte[] removeEmulationPrevention(byte[] src, int start, int end) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(end - start);
        int zeros = 0;
        for (int i = start; i < end; i++) {
            byte b = src[i];
            if (zeros == 2 && b == 0x03) {
                zeros = 0;
                continue;
            }
            out.write(b);
            zeros = (b == 0) ? (zeros + 1) : 0;
        }
        return out.toByteArray();
    }

    private static final class BitReader {
        private final byte[] data;
        private int bitPos = 0;

        BitReader(byte[] data) {
            this.data = data;
        }

        int readBits(int n) {
            int val = 0;
            for (int i = 0; i < n; i++) {
                int byteIdx = bitPos >> 3;
                if (byteIdx >= data.length) return val;
                int bitIdx = 7 - (bitPos & 7);
                val = (val << 1) | ((data[byteIdx] >> bitIdx) & 1);
                bitPos++;
            }
            return val;
        }

        int readUe() {
            int zeros = 0;
            while (readBits(1) == 0 && zeros < 31) {
                zeros++;
            }
            if (zeros == 0) return 0;
            return ((1 << zeros) - 1) + readBits(zeros);
        }

        int readSe() {
            int ue = readUe();
            int sign = ((ue & 1) == 0) ? -1 : 1;
            return sign * ((ue + 1) >> 1);
        }
    }

    // ==================== ספק פאקטים 188-byte מיושרים ====================

    private interface TsPacketSource extends Closeable {
        boolean nextPacket(byte[] dst188) throws IOException;
    }

    private static TsPacketSource openPacketSource(File file, MpegTsNormalizer.TsFormatInfo info) throws IOException {
        if (info.needsNormalization()) {
            final MpegTsNormalizer.NormalizedTsStreamReader reader =
                    new MpegTsNormalizer.NormalizedTsStreamReader(file, info);
            final long totalVirt = info.getVirtualLength();
            return new TsPacketSource() {
                long pos = 0L;

                @Override
                public boolean nextPacket(byte[] dst188) throws IOException {
                    if (pos + TS_PACKET_SIZE > totalVirt) return false;
                    int read = 0;
                    while (read < TS_PACKET_SIZE) {
                        int r = reader.readAt(pos + read, dst188, read, TS_PACKET_SIZE - read);
                        if (r <= 0) break;
                        read += r;
                    }
                    pos += TS_PACKET_SIZE;
                    return read == TS_PACKET_SIZE;
                }

                @Override
                public void close() throws IOException {
                    reader.close();
                }
            };
        } else {
            final FileInputStream fis = new FileInputStream(file);
            if (info.syncOffset > 0) {
                long toSkip = info.syncOffset;
                while (toSkip > 0L) {
                    long skipped = fis.skip(toSkip);
                    if (skipped <= 0L) break;
                    toSkip -= skipped;
                }
            }
            final byte[] chunk = new byte[TS_PACKET_SIZE * 256];
            return new TsPacketSource() {
                int chunkPos = 0;
                int chunkLen = 0;

                @Override
                public boolean nextPacket(byte[] dst188) throws IOException {
                    if (chunkPos + TS_PACKET_SIZE > chunkLen) {
                        int rem = Math.max(0, chunkLen - chunkPos);
                        if (rem > 0) {
                            System.arraycopy(chunk, chunkPos, chunk, 0, rem);
                        }
                        chunkPos = 0;
                        chunkLen = rem;
                        while (chunkLen < chunk.length) {
                            int r = fis.read(chunk, chunkLen, chunk.length - chunkLen);
                            if (r <= 0) break;
                            chunkLen += r;
                        }
                        if (chunkLen < TS_PACKET_SIZE) return false;
                    }
                    System.arraycopy(chunk, chunkPos, dst188, 0, TS_PACKET_SIZE);
                    chunkPos += TS_PACKET_SIZE;
                    return true;
                }

                @Override
                public void close() throws IOException {
                    fis.close();
                }
            };
        }
    }

    // ==================== Android MediaMuxer + המרת שמע ל-AAC ====================

    private static final class AndroidMuxerSink implements SampleSink {
        private final File outFile;
        private MediaMuxer muxer;
        private int videoTrackIdx = -1;
        private int audioTrackIdx = -1;
        private boolean muxerStarted = false;
        private long lastVideoPtsUs = -1L;
        private long lastAudioPtsUs = -1L;

        private StreamMetadata meta;
        private AudioTranscoder audioTranscoder;

        AndroidMuxerSink(File outFile) {
            this.outFile = outFile;
        }

        @Override
        public void onConfigure(StreamMetadata meta) throws IOException {
            this.meta = meta;
            muxer = new MediaMuxer(outFile.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

            if (meta.videoStreamType == STREAM_VIDEO_H264) {
                MediaFormat vf = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, meta.videoWidth, meta.videoHeight);
                if (meta.h264Sps != null) vf.setByteBuffer("csd-0", ByteBuffer.wrap(meta.h264Sps));
                if (meta.h264Pps != null) vf.setByteBuffer("csd-1", ByteBuffer.wrap(meta.h264Pps));
                videoTrackIdx = muxer.addTrack(vf);
            } else if (meta.videoStreamType == STREAM_VIDEO_HEVC) {
                MediaFormat vf = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_HEVC, meta.videoWidth, meta.videoHeight);
                ByteArrayOutputStream csd = new ByteArrayOutputStream();
                if (meta.hevcVps != null) csd.write(meta.hevcVps);
                if (meta.hevcSps != null) csd.write(meta.hevcSps);
                if (meta.hevcPps != null) csd.write(meta.hevcPps);
                if (csd.size() > 0) vf.setByteBuffer("csd-0", ByteBuffer.wrap(csd.toByteArray()));
                videoTrackIdx = muxer.addTrack(vf);
            } else {
                throw new IOException("קודק הווידאו בזרם אינו נתמך ביצוא MP4 ישיר");
            }

            if (meta.audioPid > 0) {
                if (meta.audioStreamType == STREAM_AUDIO_AAC_ADTS && meta.aacCsd0 != null) {
                    MediaFormat af = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, meta.aacSampleRate, meta.aacChannels);
                    af.setByteBuffer("csd-0", ByteBuffer.wrap(meta.aacCsd0));
                    try {
                        audioTrackIdx = muxer.addTrack(af);
                    } catch (Throwable t) {
                        audioTrackIdx = -1;
                        meta.audioTranscodeSupported = false;
                    }
                } else {
                    // ניסיון המרת MP2/MP3/AC-3/LPCM ל-AAC תקני של MP4 דרך MediaCodec
                    audioTranscoder = AudioTranscoder.tryCreate(meta.audioStreamType);
                    if (audioTranscoder != null) {
                        try {
                            MediaFormat aacFmt = audioTranscoder.getOutputAacFormat();
                            audioTrackIdx = muxer.addTrack(aacFmt);
                        } catch (Throwable t) {
                            audioTranscoder.release();
                            audioTranscoder = null;
                            audioTrackIdx = -1;
                            meta.audioTranscodeSupported = false;
                        }
                    } else {
                        meta.audioTranscodeSupported = false;
                    }
                }
            }

            muxer.start();
            muxerStarted = true;
        }

        @Override
        public void onVideoSample(byte[] data, int len, long timeUs, boolean isKeyFrame) {
            if (!muxerStarted || videoTrackIdx < 0 || data == null || len <= 0) return;
            int safeLen = Math.min(len, data.length);
            if (safeLen <= 0) return;
            long pts = timeUs <= lastVideoPtsUs ? (lastVideoPtsUs + 1000L) : timeUs;
            lastVideoPtsUs = pts;
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            info.offset = 0;
            info.size = safeLen;
            info.presentationTimeUs = pts;
            info.flags = isKeyFrame ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0;
            try {
                muxer.writeSampleData(videoTrackIdx, ByteBuffer.wrap(data, 0, safeLen), info);
            } catch (Throwable ignored) {
            }
        }

        @Override
        public void onAudioSample(byte[] data, int len, long timeUs) {
            if (!muxerStarted || audioTrackIdx < 0 || data == null || len <= 0) return;
            int safeLen = Math.min(len, data.length);
            if (safeLen <= 0) return;
            if (audioTranscoder != null) {
                try {
                    audioTranscoder.transcodeAndWrite(data, safeLen, timeUs, muxer, audioTrackIdx,
                            newPts -> {
                                long safePts = newPts <= lastAudioPtsUs ? (lastAudioPtsUs + 1000L) : newPts;
                                lastAudioPtsUs = safePts;
                                return safePts;
                            });
                } catch (Throwable ignored) {
                }
                return;
            }
            long pts = timeUs <= lastAudioPtsUs ? (lastAudioPtsUs + 1000L) : timeUs;
            lastAudioPtsUs = pts;
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            info.offset = 0;
            info.size = safeLen;
            info.presentationTimeUs = pts;
            info.flags = 0;
            try {
                muxer.writeSampleData(audioTrackIdx, ByteBuffer.wrap(data, 0, safeLen), info);
            } catch (Throwable ignored) {
            }
        }

        @Override
        public void onFinish() {
            if (audioTranscoder != null && muxerStarted && audioTrackIdx >= 0) {
                audioTranscoder.flush(muxer, audioTrackIdx,
                        newPts -> {
                            long safePts = newPts <= lastAudioPtsUs ? (lastAudioPtsUs + 1000L) : newPts;
                            lastAudioPtsUs = safePts;
                            return safePts;
                        });
            }
            releaseQuietly();
        }

        void releaseQuietly() {
            if (audioTranscoder != null) {
                audioTranscoder.release();
                audioTranscoder = null;
            }
            if (muxer != null) {
                try {
                    if (muxerStarted) muxer.stop();
                } catch (Throwable ignored) {
                }
                try {
                    muxer.release();
                } catch (Throwable ignored) {
                }
                muxer = null;
                muxerStarted = false;
            }
        }
    }

    private interface PtsAllocator {
        long allocatePts(long candidateUs);
    }

    private static final class AudioTranscoder {
        private final MediaCodec decoder;
        private final MediaCodec encoder;
        private final boolean isLpcm;
        private int decoderChannels = 2;
        private int decoderSampleRate = 48000;
        private MediaFormat outputAacFormat;

        private AudioTranscoder(MediaCodec decoder, MediaCodec encoder, boolean isLpcm, MediaFormat initialAacFormat) {
            this.decoder = decoder;
            this.encoder = encoder;
            this.isLpcm = isLpcm;
            this.outputAacFormat = initialAacFormat;
        }

        static AudioTranscoder tryCreate(int streamType) {
            boolean lpcm = (streamType == STREAM_AUDIO_BDAV_LPCM);
            String decMime = null;
            if (streamType == STREAM_AUDIO_MP2) decMime = "audio/mpeg-L2";
            else if (streamType == STREAM_AUDIO_MP3) decMime = MediaFormat.MIMETYPE_AUDIO_MPEG;
            else if (streamType == STREAM_AUDIO_AC3) decMime = "audio/ac3";
            else if (streamType == STREAM_AUDIO_EAC3) decMime = "audio/eac3";

            if (!lpcm && decMime == null) return null;

            MediaCodec dec = null;
            MediaCodec enc = null;
            try {
                if (!lpcm) {
                    dec = MediaCodec.createDecoderByType(decMime);
                    MediaFormat df = MediaFormat.createAudioFormat(decMime, 48000, 2);
                    dec.configure(df, null, null, 0);
                    dec.start();
                }
                enc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
                MediaFormat ef = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, 48000, 2);
                ef.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
                ef.setInteger(MediaFormat.KEY_BIT_RATE, 128000);
                ef.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384);
                enc.configure(ef, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                enc.start();

                // יצירת csd-0 ראשוני ל-48kHz stereo AAC-LC כדי שניתן יהיה לרשום את הערוץ ב-MediaMuxer
                byte[] csd0 = new byte[]{(byte) ((2 << 3) | (3 >> 1)), (byte) (((3 & 1) << 7) | (2 << 3))};
                ef.setByteBuffer("csd-0", ByteBuffer.wrap(csd0));
                return new AudioTranscoder(dec, enc, lpcm, ef);
            } catch (Throwable t) {
                if (dec != null) {
                    try { dec.stop(); dec.release(); } catch (Throwable ignored) {}
                }
                if (enc != null) {
                    try { enc.stop(); enc.release(); } catch (Throwable ignored) {}
                }
                return null;
            }
        }

        MediaFormat getOutputAacFormat() {
            return outputAacFormat;
        }

        void transcodeAndWrite(byte[] data, int len, long timeUs, MediaMuxer muxer, int trackIdx, PtsAllocator ptsAlloc) {
            try {
                if (isLpcm) {
                    if (len <= 4) return;
                    int pcmLen = len - 4;
                    byte[] pcm = new byte[pcmLen & ~1];
                    // המרת Big-Endian BDAV LPCM ל-Little-Endian PCM
                    for (int i = 0; i + 1 < pcm.length; i += 2) {
                        pcm[i] = data[4 + i + 1];
                        pcm[i + 1] = data[4 + i];
                    }
                    feedEncoder(pcm, pcm.length, timeUs, muxer, trackIdx, ptsAlloc);
                    return;
                }
                if (decoder == null) return;
                int inIdx = decoder.dequeueInputBuffer(5000);
                if (inIdx >= 0) {
                    ByteBuffer inBuf = decoder.getInputBuffer(inIdx);
                    if (inBuf != null) {
                        inBuf.clear();
                        int copy = Math.min(len, inBuf.remaining());
                        inBuf.put(data, 0, copy);
                        decoder.queueInputBuffer(inIdx, 0, copy, timeUs, 0);
                    }
                }
                MediaCodec.BufferInfo decInfo = new MediaCodec.BufferInfo();
                while (true) {
                    int outIdx = decoder.dequeueOutputBuffer(decInfo, 1000);
                    if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        MediaFormat fmt = decoder.getOutputFormat();
                        if (fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            decoderChannels = Math.max(1, fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT));
                        }
                        if (fmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            decoderSampleRate = Math.max(8000, fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE));
                        }
                        continue;
                    }
                    if (outIdx < 0) break;
                    ByteBuffer pcmBuf = decoder.getOutputBuffer(outIdx);
                    if (pcmBuf != null && decInfo.size > 0) {
                        pcmBuf.position(decInfo.offset);
                        byte[] pcm = new byte[decInfo.size];
                        pcmBuf.get(pcm);
                        byte[] stereoPcm = ensureStereoPcm16(pcm, decoderChannels);
                        feedEncoder(stereoPcm, stereoPcm.length, decInfo.presentationTimeUs, muxer, trackIdx, ptsAlloc);
                    }
                    decoder.releaseOutputBuffer(outIdx, false);
                }
            } catch (Throwable ignored) {
            }
        }

        private byte[] ensureStereoPcm16(byte[] pcm, int channels) {
            if (channels == 2 || pcm.length < 4) return pcm;
            if (channels == 1) {
                byte[] out = new byte[pcm.length * 2];
                for (int i = 0, o = 0; i + 1 < pcm.length; i += 2, o += 4) {
                    out[o] = pcm[i];
                    out[o + 1] = pcm[i + 1];
                    out[o + 2] = pcm[i];
                    out[o + 3] = pcm[i + 1];
                }
                return out;
            }
            int frameBytes = channels * 2;
            int frames = pcm.length / frameBytes;
            byte[] out = new byte[frames * 4];
            for (int f = 0; f < frames; f++) {
                int base = f * frameBytes;
                int outBase = f * 4;
                // ערוצים 0 ו-1 (קדמי שמאלי / ימני)
                out[outBase] = pcm[base];
                out[outBase + 1] = pcm[base + 1];
                out[outBase + 2] = pcm[base + 2];
                out[outBase + 3] = pcm[base + 3];
            }
            return out;
        }

        private void feedEncoder(byte[] pcm, int len, long timeUs, MediaMuxer muxer, int trackIdx, PtsAllocator ptsAlloc) {
            int pos = 0;
            int emptyTries = 0;
            while (pos < len) {
                int inIdx = encoder.dequeueInputBuffer(5000);
                if (inIdx < 0) {
                    drainEncoder(muxer, trackIdx, ptsAlloc);
                    if (++emptyTries >= 40) break;
                    continue;
                }
                emptyTries = 0;
                ByteBuffer inBuf = encoder.getInputBuffer(inIdx);
                if (inBuf == null) break;
                inBuf.clear();
                int copy = Math.min(len - pos, inBuf.remaining());
                if (copy <= 0) break;
                inBuf.put(pcm, pos, copy);
                long chunkPtsUs = timeUs + (pos * 1_000_000L) / Math.max(32000L, decoderSampleRate * 4L);
                encoder.queueInputBuffer(inIdx, 0, copy, chunkPtsUs, 0);
                pos += copy;
                drainEncoder(muxer, trackIdx, ptsAlloc);
            }
        }

        private void drainEncoder(MediaMuxer muxer, int trackIdx, PtsAllocator ptsAlloc) {
            MediaCodec.BufferInfo encInfo = new MediaCodec.BufferInfo();
            while (true) {
                int outIdx = encoder.dequeueOutputBuffer(encInfo, 1000);
                if (outIdx < 0) break;
                ByteBuffer outBuf = encoder.getOutputBuffer(outIdx);
                if (outBuf != null && encInfo.size > 0 && (encInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                    outBuf.position(encInfo.offset);
                    outBuf.limit(encInfo.offset + encInfo.size);
                    encInfo.presentationTimeUs = ptsAlloc.allocatePts(encInfo.presentationTimeUs);
                    muxer.writeSampleData(trackIdx, outBuf, encInfo);
                }
                encoder.releaseOutputBuffer(outIdx, false);
            }
        }

        void flush(MediaMuxer muxer, int trackIdx, PtsAllocator ptsAlloc) {
            try {
                drainEncoder(muxer, trackIdx, ptsAlloc);
            } catch (Throwable ignored) {
            }
        }

        void release() {
            if (decoder != null) {
                try { decoder.stop(); decoder.release(); } catch (Throwable ignored) {}
            }
            if (encoder != null) {
                try { encoder.stop(); encoder.release(); } catch (Throwable ignored) {}
            }
        }
    }

    private static final class ParsedVideoSample {
        final byte[] data;
        final long timeUs;
        final boolean isKeyFrame;

        ParsedVideoSample(byte[] data, long timeUs, boolean isKeyFrame) {
            this.data = data;
            this.timeUs = timeUs;
            this.isKeyFrame = isKeyFrame;
        }
    }

    private static final class ParsedAudioFrame {
        final byte[] data;
        final long timeUs;

        ParsedAudioFrame(byte[] data, long timeUs) {
            this.data = data;
            this.timeUs = timeUs;
        }
    }

    private static final class BufferedFrame {
        final byte[] data;
        final long timeUs;
        final boolean isKeyFrame;

        BufferedFrame(byte[] data, long timeUs, boolean isKeyFrame) {
            this.data = data;
            this.timeUs = timeUs;
            this.isKeyFrame = isKeyFrame;
        }
    }
}
