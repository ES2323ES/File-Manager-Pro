package com.aishhatorah.filemanagerpro;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class VideoCompressorTest {

    @Test
    public void computeTargetDimensions_downscalesAndAlignsTo16() {
        assertArrayEquals(new int[]{1280, 720},
                VideoCompressor.computeTargetDimensions(1920, 1080, 720));
        assertArrayEquals(new int[]{480, 848},
                VideoCompressor.computeTargetDimensions(1080, 1920, 480));
        assertArrayEquals(new int[]{640, 368},
                VideoCompressor.computeTargetDimensions(1920, 1080, 360));
    }

    @Test
    public void computeTargetDimensions_neverUpscalesSmallerVideo() {
        assertArrayEquals(new int[]{640, 480},
                VideoCompressor.computeTargetDimensions(640, 480, 720));
    }

    @Test
    public void clampBitrateAndEstimate_shrinksBelowOriginalSize() {
        assertEquals(850_000, VideoCompressor.clampBitrateForResolution(2_200_000, 1_000_000));
        assertEquals(550_000, VideoCompressor.clampBitrateForResolution(550_000, 4_000_000));

        long origBytes = 40_000_000L;
        long estCompact = VideoCompressor.estimateCompressedBytes(
                60_000L, VideoCompressor.QualityPreset.COMPACT_360P, origBytes, 60_000L, false, 1.0f);
        long estShare = VideoCompressor.estimateCompressedBytes(
                60_000L, VideoCompressor.QualityPreset.SHARE_480P, origBytes, 60_000L, false, 1.0f);
        assertTrue(estCompact < estShare);
        assertTrue(estShare < origBytes);
    }
}
