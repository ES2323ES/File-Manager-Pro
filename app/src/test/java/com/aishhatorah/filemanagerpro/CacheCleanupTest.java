package com.aishhatorah.filemanagerpro;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;

import static org.junit.Assert.*;

public class CacheCleanupTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File writeBytes(File target, int sizeBytes, long lastModifiedMs) throws Exception {
        File parent = target.getParentFile();
        if (parent != null && !parent.exists()) {
            assertTrue(parent.mkdirs());
        }
        byte[] buf = new byte[sizeBytes];
        Arrays.fill(buf, (byte) 0x5A);
        try (FileOutputStream fos = new FileOutputStream(target)) {
            fos.write(buf);
        }
        assertTrue(target.setLastModified(lastModifiedMs));
        return target;
    }

    @Test
    public void pruneCacheRootsRemovesExpiredManagedTempFilesAndKeepsFreshOnes() throws Exception {
        File internalCache = tmp.newFolder("internal_cache");
        File externalCache = tmp.newFolder("external_cache");

        long now = 1_800_000_000_000L;
        long staleMs = now - CacheCleanupManager.DEFAULT_TEMP_TTL_MS - 60_000L;
        long freshMs = now - 60_000L;

        File staleArch = writeBytes(new File(internalCache, "arch_preview/old.pdf"), 1024, staleMs);
        File freshArch = writeBytes(new File(internalCache, "arch_preview/new.pdf"), 1024, freshMs);
        File staleExt = writeBytes(new File(internalCache, "external_open/s1/doc.txt"), 512, staleMs);
        File freshOpenWith = writeBytes(new File(internalCache, "open_with/view.txt"), 512, freshMs);
        File staleTempPrefix = writeBytes(new File(internalCache, "mts_play_123.mp4"), 2048, staleMs);
        File freshTempPrefix = writeBytes(new File(internalCache, "ts_cut_src_999.ts"), 2048, freshMs);
        File staleEditTmp = writeBytes(new File(externalCache, ".tmp_edit_555.zip"), 512, staleMs);

        CacheCleanupManager.pruneCacheRoots(internalCache, externalCache, now, 1024L * 1024L);

        assertFalse(staleArch.exists());
        assertTrue(freshArch.exists());
        assertFalse(staleExt.exists());
        assertTrue(freshOpenWith.exists());
        assertFalse(staleTempPrefix.exists());
        assertTrue(freshTempPrefix.exists());
        assertFalse(staleEditTmp.exists());
    }

    @Test
    public void pruneCacheRootsEvictsOldestFilesToRespectHardSizeCap() throws Exception {
        File internalCache = tmp.newFolder("capped_cache");
        long now = 1_800_000_000_000L;

        File f1 = writeBytes(new File(internalCache, "arch_preview/a.bin"), 4000, now - 5_000L);
        File f2 = writeBytes(new File(internalCache, "open_with/b.bin"), 4000, now - 3_000L);
        File f3 = writeBytes(new File(internalCache, "other_c.bin"), 4000, now - 1_000L);

        CacheCleanupManager.pruneCacheRoots(internalCache, null, now, 8500L);

        assertFalse(f1.exists());
        assertTrue(f2.exists());
        assertTrue(f3.exists());
    }
}
