package com.aishhatorah.filemanagerpro;

import android.media.MediaDataSource;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * מנוע נירמול זרם MPEG-TS לקבצי וידאו .mts / .m2ts (פורמט BDAV/AVCHD של 192 בתים לפאקט),
 * זרמי DVB 204-byte, וכן לקבצי .ts עם היסט פתיחה לא מיושר:
 * ממפה בזמן אמת (Zero-Copy, ללא קובץ זמני) לזרם תקני של 188 בתים, כולל התאוששות מסנכרון
 * וחילוץ ישיר של משך הווידאו מתוך חותמות הזמן PCR / PTS.
 */
public final class MpegTsNormalizer {

    public static final int TS_PACKET_SIZE = 188;
    public static final int BDAV_PACKET_SIZE = 192;
    public static final int TS_FEC_PACKET_SIZE = 204;
    private static final byte TS_SYNC_BYTE = 0x47;
    private static final int PROBE_BUFFER_BYTES = 262144;
    private static final int REQUIRED_CONSECUTIVE_SYNC = 6;
    private static final long MAX_PTS_PCR_SCAN_BYTES = 1024L * 1024L;
    private static final long PTS_WRAP_MODULO = 1L << 33;

    private static final Map<String, TsFormatInfo> FORMAT_CACHE =
            new LinkedHashMap<String, TsFormatInfo>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, TsFormatInfo> eldest) {
                    return size() > 16;
                }
            };

    private MpegTsNormalizer() {
    }

    public static boolean isLikelyMpegTsName(String fileName) {
        if (fileName == null) return false;
        String lower = fileName.trim().toLowerCase(Locale.ROOT);
        return lower.endsWith(".mts")
                || lower.endsWith(".m2ts")
                || lower.endsWith(".ts")
                || lower.endsWith(".m2t")
                || lower.endsWith(".tsv")
                || lower.endsWith(".tsa");
    }

    public static final class TsFormatInfo {
        public final int packetStride;
        public final int syncOffset;
        public final long physicalLength;
        public final long packetCount;

        public TsFormatInfo(int packetStride, int syncOffset, long physicalLength) {
            this.packetStride = packetStride;
            this.syncOffset = syncOffset;
            this.physicalLength = Math.max(0L, physicalLength);
            if (packetStride > 0 && this.physicalLength >= syncOffset + TS_PACKET_SIZE) {
                long remainingAfterSync = this.physicalLength - syncOffset;
                this.packetCount = 1L + (remainingAfterSync - TS_PACKET_SIZE) / packetStride;
            } else {
                this.packetCount = 0L;
            }
        }

        public boolean needsNormalization() {
            return packetCount > 0L
                    && (packetStride == BDAV_PACKET_SIZE
                    || packetStride == TS_FEC_PACKET_SIZE
                    || (packetStride == TS_PACKET_SIZE && syncOffset > 0));
        }

        public long getVirtualLength() {
            return packetCount * TS_PACKET_SIZE;
        }
    }

    public static TsFormatInfo detectTsLayoutCached(File file) {
        if (file == null || !file.isFile()) {
            return new TsFormatInfo(0, 0, 0L);
        }
        String key = file.getAbsolutePath() + "|" + file.length() + "|" + file.lastModified();
        synchronized (FORMAT_CACHE) {
            TsFormatInfo cached = FORMAT_CACHE.get(key);
            if (cached != null) {
                return cached;
            }
        }
        TsFormatInfo detected = detectTsLayout(file);
        synchronized (FORMAT_CACHE) {
            FORMAT_CACHE.put(key, detected);
        }
        return detected;
    }

    public static TsFormatInfo detectTsLayout(File file) {
        if (file == null || !file.isFile()) {
            return new TsFormatInfo(0, 0, 0L);
        }
        long length = file.length();
        if (length < TS_PACKET_SIZE * 3L) {
            return new TsFormatInfo(0, 0, length);
        }
        byte[] probe = new byte[(int) Math.min(PROBE_BUFFER_BYTES, length)];
        try (FileInputStream fis = new FileInputStream(file)) {
            int totalRead = 0;
            while (totalRead < probe.length) {
                int r = fis.read(probe, totalRead, probe.length - totalRead);
                if (r <= 0) break;
                totalRead += r;
            }
            if (totalRead < probe.length) {
                byte[] trimmed = new byte[totalRead];
                System.arraycopy(probe, 0, trimmed, 0, totalRead);
                probe = trimmed;
            }
            return detectTsLayoutFromBytes(probe, length);
        } catch (IOException e) {
            return new TsFormatInfo(0, 0, length);
        }
    }

    public static TsFormatInfo detectTsLayoutFromBytes(byte[] probe, long physicalLength) {
        if (probe == null || probe.length < TS_PACKET_SIZE * 3) {
            return new TsFormatInfo(0, 0, physicalLength);
        }
        int bdavOffset = findSyncOffset(probe, BDAV_PACKET_SIZE);
        if (bdavOffset >= 0) {
            return new TsFormatInfo(BDAV_PACKET_SIZE, bdavOffset, physicalLength);
        }
        int fecOffset = findSyncOffset(probe, TS_FEC_PACKET_SIZE);
        if (fecOffset >= 0) {
            return new TsFormatInfo(TS_FEC_PACKET_SIZE, fecOffset, physicalLength);
        }
        int tsOffset = findSyncOffset(probe, TS_PACKET_SIZE);
        if (tsOffset >= 0) {
            return new TsFormatInfo(TS_PACKET_SIZE, tsOffset, physicalLength);
        }
        return new TsFormatInfo(0, 0, physicalLength);
    }

    private static int findSyncOffset(byte[] buf, int stride) {
        int needed = Math.min(REQUIRED_CONSECUTIVE_SYNC, Math.max(3, (buf.length / stride)));
        int maxStart = buf.length - (needed - 1) * stride - 1;
        if (maxStart < 0) return -1;

        for (int offset = 0; offset <= maxStart; offset++) {
            if (buf[offset] != TS_SYNC_BYTE) continue;
            boolean allMatch = true;
            for (int k = 1; k < needed; k++) {
                if (buf[offset + k * stride] != TS_SYNC_BYTE) {
                    allMatch = false;
                    break;
                }
            }
            if (allMatch) {
                return offset;
            }
        }

        // גיבוי אם פאקט אחד בפתיחה משובש: בדיקת רוב מוחלט של 0x47 לאורך החוצץ
        if (buf.length >= stride * 12) {
            int limit = Math.min(stride * 16, buf.length - stride * 8 - 1);
            for (int offset = 0; offset <= limit; offset++) {
                if (buf[offset] != TS_SYNC_BYTE) continue;
                int hits = 0;
                int checks = 0;
                for (int pos = offset; pos < buf.length && checks < 24; pos += stride) {
                    checks++;
                    if (buf[pos] == TS_SYNC_BYTE) hits++;
                }
                if (checks >= 8 && hits * 4 >= checks * 3 && buf[offset + stride] == TS_SYNC_BYTE) {
                    return offset;
                }
            }
        }
        return -1;
    }

    /**
     * חילוץ מהיר של משך הסרטון (במילישניות) ישירות מתוך PCR / PTS של זרם MPEG-TS.
     * משמש כגיבוי מיידי כשאורך הזרם אינו מדווח ע"י ExoPlayer או MediaMetadataRetriever.
     */
    public static long extractTsDurationMs(File file) {
        if (file == null || !file.isFile()) return 0L;
        TsFormatInfo info = detectTsLayoutCached(file);
        if (info.packetStride <= 0 || info.packetCount < 4L) {
            return 0L;
        }
        int stride = info.packetStride;
        long totalPackets = info.packetCount;
        long scanPackets = Math.min(totalPackets, MAX_PTS_PCR_SCAN_BYTES / stride);
        if (scanPackets < 2L) return 0L;

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            byte[] buf = new byte[(int) (scanPackets * stride)];
            raf.seek(info.syncOffset);
            int headRead = readFullyUpTo(raf, buf, buf.length);
            long[] firstResult = scanFirstTimestamp90k(buf, headRead, stride);
            if (firstResult == null) return 0L;

            long firstTs90k = firstResult[0];
            int targetPid = (int) firstResult[1];
            boolean isPcrMode = firstResult[2] == 1L;

            long tailStartPkt = Math.max(0L, totalPackets - scanPackets);
            long tailPhys = info.syncOffset + tailStartPkt * (long) stride;
            raf.seek(tailPhys);
            int tailRead = readFullyUpTo(raf, buf, buf.length);
            long lastTs90k = scanLastTimestamp90k(buf, tailRead, stride, targetPid, isPcrMode);
            if (lastTs90k < 0L) {
                // ניסיון שני על כל PID שהוא אם PID ספציפי לא הופיע בזנב
                lastTs90k = scanLastTimestamp90k(buf, tailRead, stride, -1, isPcrMode);
            }
            if (lastTs90k >= 0L) {
                long diff90k = lastTs90k - firstTs90k;
                if (diff90k < 0L && firstTs90k > (PTS_WRAP_MODULO * 3L) / 4L && lastTs90k < PTS_WRAP_MODULO / 4L) {
                    diff90k += PTS_WRAP_MODULO;
                }
                if (diff90k > 0L) {
                    long ms = diff90k / 90L;
                    if (ms > 0L && ms <= 48L * 3600L * 1000L) {
                        if (info.getVirtualLength() > 2L * 1024L * 1024L) {
                            double impliedBps = (info.getVirtualLength() * 1000.0) / (double) ms;
                            long sampledMs = estimateDurationByBitrateSamplingMs(raf, info);
                            if (sampledMs > 0L && (impliedBps > 12_000_000.0 || impliedBps < 8_000.0
                                    || Math.abs(sampledMs - ms) > Math.max(sampledMs, ms) * 0.65)) {
                                return sampledMs;
                            }
                        }
                        return ms;
                    }
                }
            }

            // גיבוי עבור קבצים מאוחדים או קבצי מצלמה עם איפוס PCR/PTS באמצע הקובץ:
            // מדידת קצב הנתונים הממוצע מחלונות רציפים וגזירת משך הזמן הכולל
            return estimateDurationByBitrateSamplingMs(raf, info);
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    private static long estimateDurationByBitrateSamplingMs(RandomAccessFile raf, TsFormatInfo info) throws IOException {
        int stride = info.packetStride;
        long totalPackets = info.packetCount;
        if (stride <= 0 || totalPackets < 16L) return 0L;

        int winPkts = (int) Math.min(totalPackets, (512L * 1024L) / stride);
        if (winPkts < 8) return 0L;
        byte[] winBuf = new byte[winPkts * stride];

        double[] samplesBps = new double[5];
        int sampleCount = 0;

        for (int w = 0; w < 5; w++) {
            long startPkt = (totalPackets > winPkts)
                    ? ((totalPackets - winPkts) * w) / 4L
                    : 0L;
            raf.seek(info.syncOffset + startPkt * (long) stride);
            int read = readFullyUpTo(raf, winBuf, winBuf.length);
            if (read < stride * 4) continue;

            double bps = measureMonotonicWindowBytesPerSec(winBuf, read, stride);
            if (bps > 10_000.0 && bps < 25_000_000.0) {
                samplesBps[sampleCount++] = bps;
            }
        }
        if (sampleCount == 0) return 0L;

        java.util.Arrays.sort(samplesBps, 0, sampleCount);
        double medianBps = samplesBps[sampleCount / 2];
        long ms = Math.round((info.getVirtualLength() * 1000.0) / medianBps);
        if (ms <= 0L || ms > 48L * 3600L * 1000L) return 0L;
        return ms;
    }

    private static double measureMonotonicWindowBytesPerSec(byte[] buf, int len, int stride) {
        int bestPid = -1;
        long firstTs = -1L;
        int firstPkt = -1;
        long lastTs = -1L;
        int lastPkt = -1;
        boolean usePcr = true;

        int pktIdx = 0;
        for (int pos = 0; pos + TS_PACKET_SIZE <= len; pos += stride, pktIdx++) {
            if (buf[pos] != TS_SYNC_BYTE) continue;
            int pid = ((buf[pos + 1] & 0x1F) << 8) | (buf[pos + 2] & 0xFF);
            long pcr = parsePcr90k(buf, pos);
            if (pcr >= 0L) {
                if (firstTs < 0L) {
                    firstTs = pcr;
                    firstPkt = pktIdx;
                    bestPid = pid;
                    usePcr = true;
                } else if (pid == bestPid && pcr > firstTs) {
                    long delta = pcr - firstTs;
                    if (delta < 90000L * 120L) {
                        lastTs = pcr;
                        lastPkt = pktIdx;
                    }
                }
            }
        }

        if (firstTs < 0L || lastTs <= firstTs || lastPkt <= firstPkt) {
            firstTs = -1L;
            lastTs = -1L;
            bestPid = -1;
            usePcr = false;
            pktIdx = 0;
            for (int pos = 0; pos + TS_PACKET_SIZE <= len; pos += stride, pktIdx++) {
                if (buf[pos] != TS_SYNC_BYTE) continue;
                int pid = ((buf[pos + 1] & 0x1F) << 8) | (buf[pos + 2] & 0xFF);
                long pts = parsePesPts90k(buf, pos);
                if (pts >= 0L) {
                    if (firstTs < 0L) {
                        firstTs = pts;
                        firstPkt = pktIdx;
                        bestPid = pid;
                    } else if (pid == bestPid && pts > firstTs) {
                        long delta = pts - firstTs;
                        if (delta < 90000L * 120L) {
                            lastTs = pts;
                            lastPkt = pktIdx;
                        }
                    }
                }
            }
        }

        if (firstTs < 0L || lastTs <= firstTs || lastPkt <= firstPkt) {
            return -1.0;
        }
        long deltaTicks = lastTs - firstTs;
        if (deltaTicks < 9000L) return -1.0; // דרוש לפחות 0.1 שניה ליציבות
        long deltaBytes = (long) (lastPkt - firstPkt) * TS_PACKET_SIZE;
        return (deltaBytes * 90000.0) / (double) deltaTicks;
    }

    private static int readFullyUpTo(RandomAccessFile raf, byte[] dst, int maxLen) throws IOException {
        int total = 0;
        while (total < maxLen) {
            int r = raf.read(dst, total, maxLen - total);
            if (r <= 0) break;
            total += r;
        }
        return total;
    }

    private static long[] scanFirstTimestamp90k(byte[] buf, int len, int stride) {
        long fallbackPts = -1L;
        int fallbackPid = -1;
        for (int pos = 0; pos + TS_PACKET_SIZE <= len; pos += stride) {
            if (buf[pos] != TS_SYNC_BYTE) continue;
            int pid = ((buf[pos + 1] & 0x1F) << 8) | (buf[pos + 2] & 0xFF);
            long pcr = parsePcr90k(buf, pos);
            if (pcr >= 0L) {
                return new long[]{pcr, pid, 1L};
            }
            if (fallbackPts < 0L) {
                long pts = parsePesPts90k(buf, pos);
                if (pts >= 0L) {
                    fallbackPts = pts;
                    fallbackPid = pid;
                }
            }
        }
        if (fallbackPts >= 0L) {
            return new long[]{fallbackPts, fallbackPid, 0L};
        }
        return null;
    }

    private static long scanLastTimestamp90k(byte[] buf, int len, int stride, int preferredPid, boolean preferPcr) {
        int alignedEnd = ((len - TS_PACKET_SIZE) / stride) * stride;
        long fallbackPts = -1L;
        for (int pos = alignedEnd; pos >= 0; pos -= stride) {
            if (buf[pos] != TS_SYNC_BYTE) continue;
            int pid = ((buf[pos + 1] & 0x1F) << 8) | (buf[pos + 2] & 0xFF);
            if (preferredPid >= 0 && pid != preferredPid) continue;
            if (preferPcr) {
                long pcr = parsePcr90k(buf, pos);
                if (pcr >= 0L) return pcr;
            }
            if (fallbackPts < 0L) {
                long pts = parsePesPts90k(buf, pos);
                if (pts >= 0L) {
                    if (!preferPcr) return pts;
                    fallbackPts = pts;
                }
            }
        }
        return fallbackPts;
    }

    private static long parsePcr90k(byte[] buf, int pktPos) {
        int afc = (buf[pktPos + 3] >> 4) & 0x03;
        if ((afc & 0x02) == 0) return -1L;
        int afLen = buf[pktPos + 4] & 0xFF;
        if (afLen < 7) return -1L;
        int flags = buf[pktPos + 5] & 0xFF;
        if ((flags & 0x10) == 0) return -1L;
        long b0 = buf[pktPos + 6] & 0xFFL;
        long b1 = buf[pktPos + 7] & 0xFFL;
        long b2 = buf[pktPos + 8] & 0xFFL;
        long b3 = buf[pktPos + 9] & 0xFFL;
        long b4 = buf[pktPos + 10] & 0xFFL;
        return (b0 << 25) | (b1 << 17) | (b2 << 9) | (b3 << 1) | (b4 >> 7);
    }

    private static long parsePesPts90k(byte[] buf, int pktPos) {
        boolean pusi = (buf[pktPos + 1] & 0x40) != 0;
        if (!pusi) return -1L;
        int afc = (buf[pktPos + 3] >> 4) & 0x03;
        if ((afc & 0x01) == 0) return -1L;
        int payloadOff = pktPos + 4;
        if ((afc & 0x02) != 0) {
            int afLen = buf[pktPos + 4] & 0xFF;
            payloadOff += 1 + afLen;
        }
        if (payloadOff + 14 > pktPos + TS_PACKET_SIZE) return -1L;
        if (buf[payloadOff] != 0 || buf[payloadOff + 1] != 0 || buf[payloadOff + 2] != 1) return -1L;
        int streamId = buf[payloadOff + 3] & 0xFF;
        if (streamId < 0xBC || streamId == 0xBE || streamId == 0xBF) return -1L;
        int ptsDtsFlags = (buf[payloadOff + 7] >> 6) & 0x03;
        if ((ptsDtsFlags & 0x02) == 0) return -1L;
        int p = payloadOff + 9;
        long p0 = buf[p] & 0xFFL;
        long p1 = buf[p + 1] & 0xFFL;
        long p2 = buf[p + 2] & 0xFFL;
        long p3 = buf[p + 3] & 0xFFL;
        long p4 = buf[p + 4] & 0xFFL;
        return ((p0 & 0x0EL) << 29)
                | ((p1 & 0xFFL) << 22)
                | ((p2 & 0xFEL) << 14)
                | ((p3 & 0xFFL) << 7)
                | ((p4 & 0xFEL) >> 1);
    }

    /**
     * קורא חוצץ מהיר הממפה היסטים וירטואליים של 188 בתים לקבצי .mts / .m2ts של 192 בתים.
     */
    public static final class NormalizedTsStreamReader implements Closeable {
        private static final int CACHE_PACKETS = 2048;

        private final RandomAccessFile raf;
        private final TsFormatInfo info;
        private final byte[] cacheBuf;
        private long cachedStartPacket = -1L;
        private int cachedPacketCount = 0;
        private long lastRafPhysPos = -1L;
        private long cumulativeResyncBytes = 0L;
        private boolean closed = false;

        public NormalizedTsStreamReader(File file, TsFormatInfo info) throws IOException {
            if (file == null || info == null || !info.needsNormalization()) {
                throw new IOException("Invalid TS normalization info");
            }
            this.raf = new RandomAccessFile(file, "r");
            this.info = info;
            this.cacheBuf = new byte[CACHE_PACKETS * info.packetStride];
        }

        public long getVirtualLength() {
            return info.getVirtualLength();
        }

        public synchronized int readAt(long position, byte[] buffer, int offset, int size) throws IOException {
            if (closed) throw new IOException("Stream reader already closed");
            if (size <= 0) return 0;
            long virtualLen = info.getVirtualLength();
            if (position < 0L || position >= virtualLen) {
                return -1;
            }
            int totalWanted = (int) Math.min(size, virtualLen - position);
            int totalCopied = 0;
            long curVirt = position;

            while (totalCopied < totalWanted) {
                long pktIdx = curVirt / TS_PACKET_SIZE;
                int inPkt = (int) (curVirt % TS_PACKET_SIZE);

                if (cachedStartPacket < 0L
                        || pktIdx < cachedStartPacket
                        || pktIdx >= cachedStartPacket + cachedPacketCount) {
                    loadCacheWindow(pktIdx);
                    if (cachedPacketCount <= 0) {
                        break;
                    }
                }

                int localPkt = (int) (pktIdx - cachedStartPacket);
                int cacheOffset = localPkt * info.packetStride + inPkt;
                int availInPkt = TS_PACKET_SIZE - inPkt;
                int copyLen = Math.min(availInPkt, totalWanted - totalCopied);
                if (cacheOffset + copyLen > cacheBuf.length) {
                    break;
                }
                System.arraycopy(cacheBuf, cacheOffset, buffer, offset + totalCopied, copyLen);
                totalCopied += copyLen;
                curVirt += copyLen;
            }
            return totalCopied > 0 ? totalCopied : -1;
        }

        private void loadCacheWindow(long startPkt) throws IOException {
            long remainingPackets = info.packetCount - startPkt;
            if (remainingPackets <= 0L) {
                cachedStartPacket = -1L;
                cachedPacketCount = 0;
                return;
            }
            // באיפוס לאחור לפני חלון מוסנכרן, מאפסים את היסט התיקון המצטבר כדי שנקרא מחדש בצורה נקייה
            if (cachedStartPacket >= 0L && startPkt < cachedStartPacket) {
                cumulativeResyncBytes = 0L;
            }
            int pktsToRead = (int) Math.min(CACHE_PACKETS, remainingPackets);
            int bytesToRead = (pktsToRead - 1) * info.packetStride + TS_PACKET_SIZE;
            long physOffset = info.syncOffset + cumulativeResyncBytes + startPkt * (long) info.packetStride;

            if (lastRafPhysPos != physOffset) {
                raf.seek(physOffset);
            }
            int readSoFar = 0;
            while (readSoFar < bytesToRead) {
                int r = raf.read(cacheBuf, readSoFar, bytesToRead - readSoFar);
                if (r <= 0) break;
                readSoFar += r;
            }
            lastRafPhysPos = physOffset + readSoFar;
            if (readSoFar < TS_PACKET_SIZE) {
                cachedStartPacket = -1L;
                cachedPacketCount = 0;
                return;
            }

            // התאוששות אוטומטית אם באמצע קובץ מאוחד הוזזו הפאקטים בכמה בתים ושמירת ההיסט להמשך הקריאה
            if (cacheBuf[0] != TS_SYNC_BYTE && readSoFar >= info.packetStride * 3 + TS_PACKET_SIZE) {
                int resyncShift = -1;
                int searchMax = Math.min(readSoFar - info.packetStride * 2 - TS_PACKET_SIZE, info.packetStride * 4);
                for (int s = 1; s <= searchMax; s++) {
                    if (cacheBuf[s] == TS_SYNC_BYTE
                            && cacheBuf[s + info.packetStride] == TS_SYNC_BYTE
                            && cacheBuf[s + info.packetStride * 2] == TS_SYNC_BYTE) {
                        resyncShift = s;
                        break;
                    }
                }
                if (resyncShift > 0) {
                    cumulativeResyncBytes += resyncShift;
                    int shiftedLen = readSoFar - resyncShift;
                    System.arraycopy(cacheBuf, resyncShift, cacheBuf, 0, shiftedLen);
                    readSoFar = shiftedLen;
                }
            }

            cachedStartPacket = startPkt;
            cachedPacketCount = 1 + (readSoFar - TS_PACKET_SIZE) / info.packetStride;
        }

        @Override
        public synchronized void close() throws IOException {
            if (!closed) {
                closed = true;
                raf.close();
            }
        }
    }

    public static final class NormalizedTsMediaDataSource extends MediaDataSource {
        private final NormalizedTsStreamReader reader;

        public NormalizedTsMediaDataSource(File file, TsFormatInfo info) throws IOException {
            this.reader = new NormalizedTsStreamReader(file, info);
        }

        @Override
        public int readAt(long position, byte[] buffer, int offset, int size) throws IOException {
            return reader.readAt(position, buffer, offset, size);
        }

        @Override
        public long getSize() {
            return reader.getVirtualLength();
        }

        @Override
        public void close() throws IOException {
            reader.close();
        }
    }

    /**
     * מגדיר מקור ל-MediaMetadataRetriever תוך טיפול שקוף בקבצי .mts / .m2ts של 192 בתים.
     */
    public static Closeable setMediaMetadataRetrieverSource(MediaMetadataRetriever retriever, File file) throws IOException {
        TsFormatInfo info = detectTsLayoutCached(file);
        if (info.needsNormalization()) {
            NormalizedTsMediaDataSource ds = new NormalizedTsMediaDataSource(file, info);
            retriever.setDataSource(ds);
            return ds;
        }
        retriever.setDataSource(file.getAbsolutePath());
        return null;
    }

    /**
     * מגדיר מקור ל-MediaPlayer תוך טיפול שקוף בקבצי .mts / .m2ts של 192 בתים (כגיבוי).
     */
    public static Closeable setMediaPlayerSource(MediaPlayer player, File file, boolean useFdFallback) throws IOException {
        TsFormatInfo info = detectTsLayoutCached(file);
        if (info.needsNormalization()) {
            NormalizedTsMediaDataSource ds = new NormalizedTsMediaDataSource(file, info);
            player.setDataSource(ds);
            return ds;
        }
        if (useFdFallback) {
            try (FileInputStream fis = new FileInputStream(file)) {
                player.setDataSource(fis.getFD());
            }
        } else {
            player.setDataSource(file.getAbsolutePath());
        }
        return null;
    }
}
