package com.aishhatorah.filemanagerpro;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class TsToMp4RemuxerTest {

    private static final byte[] SAMPLE_SPS_640X480 = new byte[]{
            0x67, 0x42, (byte) 0xC0, 0x1E, (byte) 0xF8, 0x14, 0x07, (byte) 0xB4
    };
    private static final byte[] SAMPLE_PPS = new byte[]{0x68, (byte) 0xCE, 0x38, (byte) 0x80};

    @Test
    public void parsesAvcSpsDimensionsAndNormalizesPtsResets() {
        int[] dims = TsToMp4Remuxer.parseH264SpsDimensions(SAMPLE_SPS_640X480);
        assertNotNull(dims);
        assertEquals(640, dims[0]);
        assertEquals(480, dims[1]);

        TsToMp4Remuxer.MonotonicPtsClock clock = new TsToMp4Remuxer.MonotonicPtsClock(33333L);
        long t0 = clock.normalize90k(90_000L);
        long t1 = clock.normalize90k(93_000L);
        long t2AfterReset = clock.normalize90k(0L);
        long t3 = clock.normalize90k(3_000L);

        assertEquals(0L, t0);
        assertTrue(t1 > t0);
        assertTrue("Expected monotonic time after PTS reset", t2AfterReset > t1);
        assertTrue("Expected monotonic advance after reset", t3 > t2AfterReset);
    }

    @Test
    public void demuxes192ByteBdavMtsWithPtsResetAndTrimsFromKeyframe() throws Exception {
        ByteArrayOutputStream bdavStream = new ByteArrayOutputStream();
        writeBdavPacket(bdavStream, buildPatPacket(0x0100));
        writeBdavPacket(bdavStream, buildPmtPacket(0x0100, 0x0101, 0x0102));

        // יוצר 12 פריימים (כל אחד ברווח של 100ms = 9000 ticks), כאשר באמצע יש איפוס PTS חזרה ל-0
        for (int f = 0; f < 12; f++) {
            long pts90k = (f < 6) ? (f * 9000L) : ((f - 6) * 9000L);
            boolean idr = (f % 4 == 0);
            byte[] vPayload = buildH264PesPayload(idr);
            writeBdavPacketsForPes(bdavStream, 0x0101, 0xE0, pts90k, vPayload);

            byte[] aPayload = buildAdtsAacFrame();
            writeBdavPacketsForPes(bdavStream, 0x0102, 0xC0, pts90k, aPayload);
        }

        File mtsFile = File.createTempFile("test_bdav_remux_", ".mts");
        mtsFile.deleteOnExit();
        try (FileOutputStream fos = new FileOutputStream(mtsFile)) {
            fos.write(bdavStream.toByteArray());
        }

        // 1) בדיקת המרה מלאה של כל הקובץ
        List<Long> fullVideoPts = new ArrayList<>();
        List<Long> fullAudioPts = new ArrayList<>();
        final TsToMp4Remuxer.StreamMetadata[] capturedMeta = new TsToMp4Remuxer.StreamMetadata[1];

        TsToMp4Remuxer.RemuxResult fullRes = TsToMp4Remuxer.demuxAndProcess(
                mtsFile, 0L, Long.MAX_VALUE,
                new TsToMp4Remuxer.SampleSink() {
                    @Override
                    public void onConfigure(TsToMp4Remuxer.StreamMetadata meta) {
                        capturedMeta[0] = meta;
                    }

                    @Override
                    public void onVideoSample(byte[] data, int len, long timeUs, boolean isKeyFrame) {
                        fullVideoPts.add(timeUs);
                    }

                    @Override
                    public void onAudioSample(byte[] data, int len, long timeUs) {
                        fullAudioPts.add(timeUs);
                    }

                    @Override
                    public void onFinish() {
                    }
                },
                null
        );

        assertTrue(fullRes.success);
        assertNotNull(capturedMeta[0]);
        assertEquals(640, capturedMeta[0].videoWidth);
        assertEquals(480, capturedMeta[0].videoHeight);
        assertNotNull(capturedMeta[0].h264Sps);
        assertNotNull(capturedMeta[0].h264Pps);
        assertNotNull(capturedMeta[0].aacCsd0);
        assertTrue(fullVideoPts.size() >= 10);
        assertTrue(fullAudioPts.size() >= 8);

        // וידוא שכל זמני הווידאו והשמע עולים מונוטונית גם אחרי איפוס PTS
        for (int i = 1; i < fullVideoPts.size(); i++) {
            assertTrue("Video PTS must remain monotonic at " + i, fullVideoPts.get(i) > fullVideoPts.get(i - 1));
        }
        for (int i = 1; i < fullAudioPts.size(); i++) {
            assertTrue("Audio PTS must remain monotonic at " + i, fullAudioPts.get(i) >= fullAudioPts.get(i - 1));
        }

        // 2) בדיקת חיתוך טווח [350ms .. 850ms] (מתחיל מה-Keyframe לפני נקודת ההתחלה ומאפס את הזמן ל-0)
        List<Long> cutVideoPts = new ArrayList<>();
        List<Boolean> cutKeyframes = new ArrayList<>();
        TsToMp4Remuxer.RemuxResult cutRes = TsToMp4Remuxer.demuxAndProcess(
                mtsFile, 350_000L, 850_000L,
                new TsToMp4Remuxer.SampleSink() {
                    @Override public void onConfigure(TsToMp4Remuxer.StreamMetadata meta) {}
                    @Override
                    public void onVideoSample(byte[] data, int len, long timeUs, boolean isKeyFrame) {
                        cutVideoPts.add(timeUs);
                        cutKeyframes.add(isKeyFrame);
                    }
                    @Override public void onAudioSample(byte[] data, int len, long timeUs) {}
                    @Override public void onFinish() {}
                },
                null
        );

        assertTrue(cutRes.success);
        assertFalse(cutVideoPts.isEmpty());
        assertTrue("Trimmed clip must start on keyframe", cutKeyframes.get(0));
        assertEquals(0L, (long) cutVideoPts.get(0));
        assertTrue(cutVideoPts.size() < fullVideoPts.size());
    }

    @Test
    public void bFramePtsPreservesDisplayOrderAndCttsBoxInjectedInMoov() throws Exception {
        // 1) בדיקת מצב B-frame ב-MonotonicPtsClock: מאפשר קפיצות שליליות של סדר תצוגה (33ms..100ms),
        //    תוך איפוס בקפיצת רצף גדולה (-45,000 ticks = -500ms)
        TsToMp4Remuxer.MonotonicPtsClock bClock = new TsToMp4Remuxer.MonotonicPtsClock(33_333L, true);
        long iFramePts = bClock.normalize90k(90_000L);   // 0 us (DTS 0)
        long pFramePts = bClock.normalize90k(99_000L);   // 100,000 us (DTS 33ms)
        long b1FramePts = bClock.normalize90k(93_000L);  // 33,333 us (DTS 67ms)
        long b2FramePts = bClock.normalize90k(96_000L);  // 66,666 us (DTS 100ms)
        assertEquals(0L, iFramePts);
        assertEquals(100_000L, pFramePts);
        assertTrue("B1 PTS must precede P-frame PTS", b1FramePts < pFramePts && b1FramePts > iFramePts);
        assertTrue("B2 PTS must sit between B1 and P-frame", b2FramePts > b1FramePts && b2FramePts < pFramePts);

        long afterDiscontinuity = bClock.normalize90k(0L); // איפוס גדול (>333ms אחורה)
        assertTrue("Large backward jump must still reset monotonically", afterDiscontinuity > pFramePts);

        // 2) בדיקת הזרקת תיבת ctts תקינה ל-moov בסוף קובץ MP4 (אחרי mdat) ועדכון כל גדלי התיבות האב
        byte[] ftyp = makeBox("ftyp", new byte[]{'i', 's', 'o', 'm', 0, 0, 2, 0});
        byte[] mdat = makeBox("mdat", new byte[]{1, 2, 3, 4, 5, 6, 7, 8});

        // mdhd v0: version/flags(4) + creation(4) + mod(4) + timescale=90000(4) + duration(4) + lang/predef(4)
        byte[] mdhdBody = new byte[24];
        writeBe32(mdhdBody, 12, 90_000);
        byte[] mdhd = makeBox("mdhd", mdhdBody);

        // hdlr: version/flags(4) + pre_defined(4) + handler_type="vide"(4) + reserved(12)
        byte[] hdlrBody = new byte[24];
        hdlrBody[8] = 'v';
        hdlrBody[9] = 'i';
        hdlrBody[10] = 'd';
        hdlrBody[11] = 'e';
        byte[] hdlr = makeBox("hdlr", hdlrBody);

        // stsz: version/flags(4) + sample_size(4) + sample_count=4(4) + 4 entries(16)
        byte[] stszBody = new byte[12 + 16];
        writeBe32(stszBody, 8, 4);
        byte[] stsz = makeBox("stsz", stszBody);

        byte[] stbl = makeBox("stbl", stsz);
        byte[] minf = makeBox("minf", stbl);
        byte[] mdia = makeBox("mdia", concat(mdhd, hdlr, minf));
        byte[] trak = makeBox("trak", mdia);
        byte[] moov = makeBox("moov", trak);

        File mp4 = File.createTempFile("test_ctts_", ".mp4");
        mp4.deleteOnExit();
        try (FileOutputStream fos = new FileOutputStream(mp4)) {
            fos.write(ftyp);
            fos.write(mdat);
            fos.write(moov);
        }

        long[] dtsUs = {0L, 33_333L, 66_667L, 100_000L};
        long[] ptsUs = {0L, 100_000L, 33_333L, 66_667L};
        assertTrue("Expected ctts box injection to succeed", TsToMp4Remuxer.injectCttsBoxIfNeeded(mp4, dtsUs, ptsUs));

        byte[] patched = java.nio.file.Files.readAllBytes(mp4.toPath());
        String ascii = new String(patched, java.nio.charset.StandardCharsets.US_ASCII);
        assertTrue("Patched MP4 must contain ctts atom", ascii.contains("ctts"));
        // 4 runs (0, +6000, -3000, -3000) -> 16 header + 4*8 = 48 bytes added to moov
        int moovOff = ftyp.length + mdat.length;
        int patchedMoovSize = readBe32(patched, moovOff);
        assertEquals(moov.length + 48, patchedMoovSize);
        assertEquals(patched.length, moovOff + patchedMoovSize);
    }

    private static byte[] makeBox(String type, byte[] payload) {
        byte[] out = new byte[8 + payload.length];
        writeBe32(out, 0, out.length);
        out[4] = (byte) type.charAt(0);
        out[5] = (byte) type.charAt(1);
        out[6] = (byte) type.charAt(2);
        out[7] = (byte) type.charAt(3);
        System.arraycopy(payload, 0, out, 8, payload.length);
        return out;
    }

    private static byte[] concat(byte[]... arrays) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        for (byte[] a : arrays) os.write(a);
        return os.toByteArray();
    }

    private static void writeBe32(byte[] buf, int off, int val) {
        buf[off] = (byte) ((val >> 24) & 0xFF);
        buf[off + 1] = (byte) ((val >> 16) & 0xFF);
        buf[off + 2] = (byte) ((val >> 8) & 0xFF);
        buf[off + 3] = (byte) (val & 0xFF);
    }

    private static int readBe32(byte[] buf, int off) {
        return ((buf[off] & 0xFF) << 24)
                | ((buf[off + 1] & 0xFF) << 16)
                | ((buf[off + 2] & 0xFF) << 8)
                | (buf[off + 3] & 0xFF);
    }

    private static void writeBdavPacket(ByteArrayOutputStream out, byte[] ts188) throws IOException {
        out.write(0x00);
        out.write(0x01);
        out.write(0x02);
        out.write(0x03);
        out.write(ts188);
    }

    private static byte[] buildPatPacket(int pmtPid) {
        byte[] p = emptyTsPacket(0x0000, true);
        int i = 4;
        p[i++] = 0x00; // pointer_field
        p[i++] = 0x00; // table_id PAT
        p[i++] = (byte) 0xB0;
        p[i++] = 13; // section_length
        p[i++] = 0x00;
        p[i++] = 0x01;
        p[i++] = (byte) 0xC1;
        p[i++] = 0x00;
        p[i++] = 0x00;
        p[i++] = 0x00;
        p[i++] = 0x01; // program_number 1
        p[i++] = (byte) (0xE0 | ((pmtPid >> 8) & 0x1F));
        p[i++] = (byte) (pmtPid & 0xFF);
        // 4 bytes CRC placeholder
        return p;
    }

    private static byte[] buildPmtPacket(int pmtPid, int videoPid, int audioPid) {
        byte[] p = emptyTsPacket(pmtPid, true);
        int i = 4;
        p[i++] = 0x00; // pointer_field
        p[i++] = 0x02; // table_id PMT
        p[i++] = (byte) 0xB0;
        p[i++] = 23; // section_length
        p[i++] = 0x00;
        p[i++] = 0x01;
        p[i++] = (byte) 0xC1;
        p[i++] = 0x00;
        p[i++] = 0x00;
        p[i++] = (byte) (0xE0 | ((videoPid >> 8) & 0x1F));
        p[i++] = (byte) (videoPid & 0xFF);
        p[i++] = (byte) 0xF0;
        p[i++] = 0x00; // program_info_length
        // H.264 stream
        p[i++] = TsToMp4Remuxer.STREAM_VIDEO_H264;
        p[i++] = (byte) (0xE0 | ((videoPid >> 8) & 0x1F));
        p[i++] = (byte) (videoPid & 0xFF);
        p[i++] = (byte) 0xF0;
        p[i++] = 0x00;
        // AAC ADTS stream
        p[i++] = TsToMp4Remuxer.STREAM_AUDIO_AAC_ADTS;
        p[i++] = (byte) (0xE0 | ((audioPid >> 8) & 0x1F));
        p[i++] = (byte) (audioPid & 0xFF);
        p[i++] = (byte) 0xF0;
        p[i++] = 0x00;
        return p;
    }

    private static byte[] buildH264PesPayload(boolean idr) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        if (idr) {
            os.write(new byte[]{0, 0, 0, 1});
            os.write(SAMPLE_SPS_640X480);
            os.write(new byte[]{0, 0, 0, 1});
            os.write(SAMPLE_PPS);
            os.write(new byte[]{0, 0, 0, 1, 0x65, (byte) 0x88, (byte) 0x84, 0x21});
        } else {
            os.write(new byte[]{0, 0, 0, 1, 0x41, (byte) 0x9A, 0x24, 0x6C});
        }
        return os.toByteArray();
    }

    private static byte[] buildAdtsAacFrame() {
        // 7-byte ADTS header (48kHz, 2ch, AAC-LC) + 5 bytes dummy AAC payload = 12 bytes
        int frameLen = 12;
        byte[] adts = new byte[frameLen];
        adts[0] = (byte) 0xFF;
        adts[1] = (byte) 0xF1; // no CRC
        // profile=1 (AAC-LC -> audioObj=2), freqIdx=3 (48kHz), ch=2
        adts[2] = (byte) ((1 << 6) | (3 << 2));
        adts[3] = (byte) ((2 << 6) | ((frameLen >> 11) & 0x03));
        adts[4] = (byte) ((frameLen >> 3) & 0xFF);
        adts[5] = (byte) (((frameLen & 0x07) << 5) | 0x1F);
        adts[6] = (byte) 0xFC;
        return adts;
    }

    private static void writeBdavPacketsForPes(ByteArrayOutputStream out, int pid, int streamId,
                                               long pts90k, byte[] payload) throws IOException {
        ByteArrayOutputStream pes = new ByteArrayOutputStream();
        pes.write(0x00);
        pes.write(0x00);
        pes.write(0x01);
        pes.write(streamId & 0xFF);
        pes.write(0x00);
        pes.write(0x00);
        pes.write(0x80);
        pes.write(0x80); // PTS flag
        pes.write(0x05); // PES header data len
        pes.write((byte) (0x21 | ((pts90k >> 29) & 0x0E)));
        pes.write((byte) ((pts90k >> 22) & 0xFF));
        pes.write((byte) (0x01 | ((pts90k >> 14) & 0xFE)));
        pes.write((byte) ((pts90k >> 7) & 0xFF));
        pes.write((byte) (0x01 | ((pts90k << 1) & 0xFE)));
        pes.write(payload);

        byte[] pesBytes = pes.toByteArray();
        int offset = 0;
        boolean first = true;
        while (offset < pesBytes.length) {
            byte[] pkt = emptyTsPacket(pid, first);
            int chunk = Math.min(184, pesBytes.length - offset);
            System.arraycopy(pesBytes, offset, pkt, 4, chunk);
            writeBdavPacket(out, pkt);
            offset += chunk;
            first = false;
        }
    }

    private static byte[] emptyTsPacket(int pid, boolean pusi) {
        byte[] pkt = new byte[188];
        for (int i = 4; i < 188; i++) pkt[i] = (byte) 0xFF;
        pkt[0] = 0x47;
        pkt[1] = (byte) ((pusi ? 0x40 : 0x00) | ((pid >> 8) & 0x1F));
        pkt[2] = (byte) (pid & 0xFF);
        pkt[3] = 0x10; // payload only
        return pkt;
    }
}
