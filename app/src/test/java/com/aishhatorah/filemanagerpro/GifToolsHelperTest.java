package com.aishhatorah.filemanagerpro;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.Assert.*;

public class GifToolsHelperTest {

    @Test
    public void parseGifFrameTimelineReadsPerFrameDelays() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // Minimal GIF89a header without global color table (packed = 0)
        out.write(new byte[]{'G', 'I', 'F', '8', '9', 'a', 1, 0, 1, 0, 0x00, 0, 0});

        // Frame 1 GCE: delay = 50 centis (500ms) = 0x32, 0x00
        out.write(new byte[]{0x21, (byte) 0xF9, 0x04, 0x04, 0x32, 0x00, 0x00, 0x00});
        // Frame 1 Image Descriptor + LZW min code size 2 + 1 data block + terminator
        out.write(new byte[]{0x2C, 0, 0, 0, 0, 1, 0, 1, 0, 0x00, 0x02, 0x01, 0x00, 0x00});

        // Frame 2 GCE: delay = 25 centis (250ms) = 0x19, 0x00
        out.write(new byte[]{0x21, (byte) 0xF9, 0x04, 0x04, 0x19, 0x00, 0x00, 0x00});
        // Frame 2 Image Descriptor
        out.write(new byte[]{0x2C, 0, 0, 0, 0, 1, 0, 1, 0, 0x00, 0x02, 0x01, 0x00, 0x00});

        // Trailer
        out.write(0x3B);

        List<GifToolsHelper.GifFrameTiming> timeline = GifToolsHelper.parseGifFrameTimeline(out.toByteArray());
        assertEquals(2, timeline.size());
        assertEquals(0L, timeline.get(0).startMs);
        assertEquals(500, timeline.get(0).delayMs);
        assertEquals(500L, timeline.get(1).startMs);
        assertEquals(250, timeline.get(1).delayMs);
    }
}
