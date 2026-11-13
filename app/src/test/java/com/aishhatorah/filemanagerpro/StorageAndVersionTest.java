package com.aishhatorah.filemanagerpro;

import org.junit.Test;

import java.io.File;
import java.util.List;

import static org.junit.Assert.*;

public class StorageAndVersionTest {

    @Test
    public void hebrewFirstComparatorOrdersHebrewBeforeLatin() {
        assertTrue(StorageUtils.compareHebrewFirst("אבגד", "abcd") < 0);
        assertTrue(StorageUtils.compareHebrewFirst("abcd", "אבגד") > 0);
        assertTrue(StorageUtils.compareHebrewFirst("אבג", "תשר") < 0);
        assertEquals("1.5 KB", StorageUtils.formatSize(1536));
    }

    @Test
    public void videoFormatsRecognizedConsistently() {
        String[] videoExts = {"clip.mp4", "movie.mkv", "legacy.avi", "stream.ts", "flash.flv", "win.wmv", "apple.mov", "web.webm", "mobile.3gp"};
        for (String name : videoExts) {
            assertTrue("Expected video format: " + name, MediaFormatHelper.isVideoFile(name));
        }
    }

    @Test
    public void fileScanCacheSnapshotExtractionWorks() {
        StorageUtils.CategoryIndexSnapshot snap = new StorageUtils.CategoryIndexSnapshot();
        File img = new File("/tmp/pic.jpg");
        File vid = new File("/tmp/clip.mkv");
        snap.images.add(img);
        snap.videos.add(vid);

        List<File> imgs = FileScanCache.extractCategoryFiles(snap, FileBrowserActivity.MODE_IMAGES);
        List<File> vids = FileScanCache.extractCategoryFiles(snap, FileBrowserActivity.MODE_VIDEOS);
        assertEquals(1, imgs.size());
        assertEquals(img, imgs.get(0));
        assertEquals(1, vids.size());
        assertEquals(vid, vids.get(0));

        FileScanCache.invalidate();
        assertTrue(FileScanCache.isDirty());
    }

    @Test
    public void versionHelperFormatsOfficialAndComparesSemver() {
        assertEquals("0.2.9 רשמית", UpdateChecker.formatOfficialVer("0.2.9"));
        assertTrue(UpdateChecker.compareSemver("0.2.9", "0.2.8") > 0);
        assertEquals(0, UpdateChecker.compareSemver("0.2.9", "0.2.9"));
    }
}
