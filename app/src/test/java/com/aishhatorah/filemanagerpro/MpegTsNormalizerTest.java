package com.aishhatorah.filemanagerpro;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MpegTsNormalizerTest {

    @Test
    public void detectsAndUnwraps192ByteBdavMtsPackets() throws Exception {
        int packetCount = 300;
        byte[] rawMts = new byte[packetCount * 192];
        byte[] expectedTs = new byte[packetCount * 188];

        for (int p = 0; p < packetCount; p++) {
            int mtsBase = p * 192;
            int tsBase = p * 188;
            // 4-byte BDAV TP_extra_header
            rawMts[mtsBase] = (byte) (p & 0xFF);
            rawMts[mtsBase + 1] = 0x11;
            rawMts[mtsBase + 2] = 0x22;
            rawMts[mtsBase + 3] = 0x33;
            // 188-byte MPEG-TS packet starting with 0x47
            rawMts[mtsBase + 4] = 0x47;
            expectedTs[tsBase] = 0x47;
            for (int i = 1; i < 188; i++) {
                byte val = (byte) ((p * 31 + i) & 0xFF);
                rawMts[mtsBase + 4 + i] = val;
                expectedTs[tsBase + i] = val;
            }
        }

        MpegTsNormalizer.TsFormatInfo probeInfo = MpegTsNormalizer.detectTsLayoutFromBytes(rawMts, rawMts.length);
        assertEquals(192, probeInfo.packetStride);
        assertEquals(4, probeInfo.syncOffset);
        assertEquals(packetCount, probeInfo.packetCount);
        assertTrue(probeInfo.needsNormalization());
        assertEquals(expectedTs.length, probeInfo.getVirtualLength());

        File tmp = File.createTempFile("test_camcorder_", ".mts");
        tmp.deleteOnExit();
        try (FileOutputStream fos = new FileOutputStream(tmp)) {
            fos.write(rawMts);
        }

        MpegTsNormalizer.TsFormatInfo fileInfo = MpegTsNormalizer.detectTsLayout(tmp);
        assertTrue(fileInfo.needsNormalization());
        assertEquals(expectedTs.length, fileInfo.getVirtualLength());

        byte[] readBack = new byte[expectedTs.length];
        try (MpegTsNormalizer.NormalizedTsStreamReader reader =
                     new MpegTsNormalizer.NormalizedTsStreamReader(tmp, fileInfo)) {
            // Read across packet boundaries with odd chunk sizes
            int pos = 0;
            while (pos < readBack.length) {
                int step = Math.min(333, readBack.length - pos);
                int r = reader.readAt(pos, readBack, pos, step);
                assertTrue(r > 0);
                pos += r;
            }
            assertArrayEquals(expectedTs, readBack);

            // Random-access seek read near the end of stream
            int tailOffset = expectedTs.length - 500;
            byte[] tailBuf = new byte[500];
            int tailRead = reader.readAt(tailOffset, tailBuf, 0, tailBuf.length);
            assertEquals(500, tailRead);
            assertArrayEquals(Arrays.copyOfRange(expectedTs, tailOffset, expectedTs.length), tailBuf);
        }
    }

    @Test
    public void detectsOffset188ByteTsAndRejectsStandardOrMp4() {
        // 188-byte TS with 13 leading garbage bytes
        byte[] offsetTs = new byte[13 + 188 * 10];
        for (int p = 0; p < 10; p++) {
            offsetTs[13 + p * 188] = 0x47;
        }
        MpegTsNormalizer.TsFormatInfo offsetInfo = MpegTsNormalizer.detectTsLayoutFromBytes(offsetTs, offsetTs.length);
        assertEquals(188, offsetInfo.packetStride);
        assertEquals(13, offsetInfo.syncOffset);
        assertEquals(10, offsetInfo.packetCount);
        assertTrue(offsetInfo.needsNormalization());

        // Standard aligned 188-byte TS needs no wrapper
        byte[] standardTs = new byte[188 * 10];
        for (int p = 0; p < 10; p++) {
            standardTs[p * 188] = 0x47;
        }
        MpegTsNormalizer.TsFormatInfo standardInfo = MpegTsNormalizer.detectTsLayoutFromBytes(standardTs, standardTs.length);
        assertEquals(188, standardInfo.packetStride);
        assertEquals(0, standardInfo.syncOffset);
        assertFalse(standardInfo.needsNormalization());

        // MP4 header is not TS
        byte[] mp4 = new byte[1024];
        mp4[4] = 'f';
        mp4[5] = 't';
        mp4[6] = 'y';
        mp4[7] = 'p';
        MpegTsNormalizer.TsFormatInfo mp4Info = MpegTsNormalizer.detectTsLayoutFromBytes(mp4, mp4.length);
        assertEquals(0, mp4Info.packetStride);
        assertFalse(mp4Info.needsNormalization());
    }

    @Test
    public void detectsPaddedBdavMtsBeyond2KbAndExtractsPcrDuration() throws Exception {
        int leadingGarbage = 4096 + 4;
        int packetCount = 40;
        byte[] raw = new byte[leadingGarbage + packetCount * 192];
        for (int p = 0; p < packetCount; p++) {
            int base = leadingGarbage + p * 192;
            raw[base] = 0x47;
            // PID = 0x0100
            raw[base + 1] = 0x01;
            raw[base + 2] = 0x00;
            // adaptation_field_control = 0x02 (adaptation field only), af_len = 7, PCR flag = 0x10
            raw[base + 3] = 0x20;
            raw[base + 4] = 0x07;
            raw[base + 5] = 0x10;
            // Encode PCR base = p * 90_000 (1 second per packet -> 39 seconds total)
            long pcr90k = p * 90_000L;
            raw[base + 6] = (byte) ((pcr90k >> 25) & 0xFF);
            raw[base + 7] = (byte) ((pcr90k >> 17) & 0xFF);
            raw[base + 8] = (byte) ((pcr90k >> 9) & 0xFF);
            raw[base + 9] = (byte) ((pcr90k >> 1) & 0xFF);
            raw[base + 10] = (byte) ((pcr90k & 0x01) << 7);
        }

        File tmp = File.createTempFile("test_padded_pcr_", ".mts");
        tmp.deleteOnExit();
        try (FileOutputStream fos = new FileOutputStream(tmp)) {
            fos.write(raw);
        }

        MpegTsNormalizer.TsFormatInfo info = MpegTsNormalizer.detectTsLayout(tmp);
        assertEquals(192, info.packetStride);
        assertEquals(leadingGarbage, info.syncOffset);
        assertTrue(info.needsNormalization());

        long durationMs = MpegTsNormalizer.extractTsDurationMs(tmp);
        assertEquals((packetCount - 1) * 1000L, durationMs);
    }

    @Test
    public void extractsBitrateDurationWhenPcrResetsMidStream() throws Exception {
        // מדמה קובץ MTS מאוחד שבו השעון מתאפס באמצע הקובץ (0..2s ואז שוב 0..2s)
        // כך ש-lastPcr - firstPcr == 0, והמערכת חייבת לעבור לאמידת קצב נתונים מונוטוני
        int packetCount = 400;
        byte[] raw = new byte[packetCount * 192];
        for (int p = 0; p < packetCount; p++) {
            int base = p * 192;
            raw[base + 4] = 0x47;
            raw[base + 5] = 0x01;
            raw[base + 6] = 0x00;
            raw[base + 7] = 0x20;
            raw[base + 8] = 0x07;
            raw[base + 9] = 0x10;
            int localPkt = (p < 200) ? (200 + p) : (p - 200);
            // 10ms (900 ticks) per packet -> 400 packets = 4000ms (~4 seconds), with tail PCR < head PCR due to reset
            long pcr90k = localPkt * 900L;
            raw[base + 10] = (byte) ((pcr90k >> 25) & 0xFF);
            raw[base + 11] = (byte) ((pcr90k >> 17) & 0xFF);
            raw[base + 12] = (byte) ((pcr90k >> 9) & 0xFF);
            raw[base + 13] = (byte) ((pcr90k >> 1) & 0xFF);
            raw[base + 14] = (byte) ((pcr90k & 0x01) << 7);
        }

        File tmp = File.createTempFile("test_pcr_reset_", ".mts");
        tmp.deleteOnExit();
        try (FileOutputStream fos = new FileOutputStream(tmp)) {
            fos.write(raw);
        }

        long durationMs = MpegTsNormalizer.extractTsDurationMs(tmp);
        assertTrue("Expected ~4000ms bitrate duration, got " + durationMs,
                durationMs >= 3500L && durationMs <= 4500L);
    }
}
