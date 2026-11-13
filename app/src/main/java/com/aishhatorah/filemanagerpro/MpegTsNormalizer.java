package com.aishhatorah.filemanagerpro;

import android.media.MediaDataSource;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * מנוע נירמול זרם MPEG-TS לקבצי וידאו .mts / .m2ts (פורמט BDAV/AVCHD של 192 בתים לפאקט)
 * וכן לקבצי .ts עם היסט פתיחה לא מיושר: ממפה בזמן אמת (Zero-Copy, ללא קובץ זמני)
 * כל פאקט של 192 בתים (4 בתים TP_extra_header + 188 בתים MPEG-TS) לזרם תקני של 188 בתים.
 */
public final class MpegTsNormalizer {

    public static final int TS_PACKET_SIZE = 188;
    public static final int BDAV_PACKET_SIZE = 192;
    private static final byte TS_SYNC_BYTE = 0x47;
    private static final int PROBE_BUFFER_BYTES = 16384;
    private static final int REQUIRED_CONSECUTIVE_SYNC = 6;

    private MpegTsNormalizer() {
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
                    && (packetStride == BDAV_PACKET_SIZE || (packetStride == TS_PACKET_SIZE && syncOffset > 0));
        }

        public long getVirtualLength() {
            return packetCount * TS_PACKET_SIZE;
        }
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
        int tsOffset = findSyncOffset(probe, TS_PACKET_SIZE);
        if (tsOffset >= 0) {
            return new TsFormatInfo(TS_PACKET_SIZE, tsOffset, physicalLength);
        }
        return new TsFormatInfo(0, 0, physicalLength);
    }

    private static int findSyncOffset(byte[] buf, int stride) {
        int needed = Math.min(REQUIRED_CONSECUTIVE_SYNC, Math.max(3, (buf.length / stride)));
        int maxStart = Math.min(stride * 8, buf.length - (needed - 1) * stride - 1);
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
        return -1;
    }

    /**
     * קורא חוצץ מהיר הממפה היסטים וירטואליים של 188 בתים לקבצי .mts / .m2ts של 192 בתים.
     */
    public static final class NormalizedTsStreamReader implements Closeable {
        private static final int CACHE_PACKETS = 256;

        private final RandomAccessFile raf;
        private final TsFormatInfo info;
        private final byte[] cacheBuf;
        private long cachedStartPacket = -1L;
        private int cachedPacketCount = 0;
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
            int pktsToRead = (int) Math.min(CACHE_PACKETS, remainingPackets);
            int bytesToRead = (pktsToRead - 1) * info.packetStride + TS_PACKET_SIZE;
            long physOffset = info.syncOffset + startPkt * (long) info.packetStride;

            raf.seek(physOffset);
            int readSoFar = 0;
            while (readSoFar < bytesToRead) {
                int r = raf.read(cacheBuf, readSoFar, bytesToRead - readSoFar);
                if (r <= 0) break;
                readSoFar += r;
            }
            if (readSoFar < TS_PACKET_SIZE) {
                cachedStartPacket = -1L;
                cachedPacketCount = 0;
                return;
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
        TsFormatInfo info = detectTsLayout(file);
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
        TsFormatInfo info = detectTsLayout(file);
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
