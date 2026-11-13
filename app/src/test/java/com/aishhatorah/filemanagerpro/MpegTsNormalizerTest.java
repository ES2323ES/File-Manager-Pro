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
}
