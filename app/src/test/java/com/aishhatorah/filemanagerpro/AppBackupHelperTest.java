package com.aishhatorah.filemanagerpro.browser;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.Assert.*;

public class AppBackupHelperTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File writeSampleFile(String name, String content) throws IOException {
        File f = tmp.newFile(name);
        try (FileOutputStream fos = new FileOutputStream(f)) {
            fos.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return f;
    }

    @Test
    public void sanitizeFileNameStripsIllegalCharsAndLeadingDots() {
        assertEquals("app", AppBackupHelper.sanitizeFileName(null));
        assertEquals("app", AppBackupHelper.sanitizeFileName("..."));
        assertEquals("My_App_1.0", AppBackupHelper.sanitizeFileName("..My/App:1.0"));
        assertEquals("אפליקציה_חדשה_", AppBackupHelper.sanitizeFileName("אפליקציה*חדשה?"));
    }

    @Test
    public void buildBackupFileNameUsesApksForSplitAndApkForSingle() throws IOException {
        AppBackupHelper.InstalledAppEntry single = new AppBackupHelper.InstalledAppEntry();
        single.appName = "Notes";
        single.versionName = "2.1";
        assertEquals("Notes_2.1.apk", AppBackupHelper.buildBackupFileName(single));

        AppBackupHelper.InstalledAppEntry split = new AppBackupHelper.InstalledAppEntry();
        split.appName = "Maps";
        split.versionName = "3.4";
        split.splitApks.add(writeSampleFile("split_arm64.apk", "split-bytes"));
        assertEquals("Maps_3.4.apks", AppBackupHelper.buildBackupFileName(split));
    }

    @Test
    public void createSplitApksArchivePackagesBaseAndDeduplicatedSplits() throws IOException {
        File base = writeSampleFile("base.apk", "BASE-APK-DATA");
        File dirA = tmp.newFolder("a");
        File dirB = tmp.newFolder("b");
        File split1 = new File(dirA, "split_config.arm64_v8a.apk");
        File split2 = new File(dirB, "split_config.arm64_v8a.apk");
        try (FileOutputStream fos = new FileOutputStream(split1)) {
            fos.write("SPLIT-ONE".getBytes(StandardCharsets.UTF_8));
        }
        try (FileOutputStream fos = new FileOutputStream(split2)) {
            fos.write("SPLIT-TWO".getBytes(StandardCharsets.UTF_8));
        }

        File outZip = new File(tmp.getRoot(), "bundle.apks");
        AppBackupHelper.createSplitApksArchive(base, Arrays.asList(split1, split2), outZip);

        assertTrue(outZip.exists());
        Map<String, String> entries = new HashMap<>();
        try (ZipFile zf = new ZipFile(outZip)) {
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry ze = en.nextElement();
                try (InputStream is = zf.getInputStream(ze)) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buf = new byte[1024];
                    int r;
                    while ((r = is.read(buf)) != -1) {
                        baos.write(buf, 0, r);
                    }
                    entries.put(ze.getName(), baos.toString(StandardCharsets.UTF_8));
                }
            }
        }

        assertEquals(3, entries.size());
        assertEquals("BASE-APK-DATA", entries.get("base.apk"));
        assertEquals("SPLIT-ONE", entries.get("split_config.arm64_v8a.apk"));
        assertEquals("SPLIT-TWO", entries.get("split_config.arm64_v8a_1.apk"));
    }

    @Test
    public void backupAppToDownloadsExportsSingleAndSplitWithoutOverwriting() throws IOException {
        File downloads = tmp.newFolder("Downloads");
        File baseSingle = writeSampleFile("single.apk", "SINGLE-CONTENT");
        AppBackupHelper.InstalledAppEntry single = new AppBackupHelper.InstalledAppEntry();
        single.appName = "Calculator";
        single.versionName = "1.0";
        single.sourceApk = baseSingle;

        File firstSingle = AppBackupHelper.backupAppToDownloads(single, downloads);
        File secondSingle = AppBackupHelper.backupAppToDownloads(single, downloads);
        assertEquals("Calculator_1.0.apk", firstSingle.getName());
        assertNotEquals(firstSingle.getName(), secondSingle.getName());
        assertTrue(firstSingle.exists());
        assertTrue(secondSingle.exists());

        File baseSplit = writeSampleFile("split_base.apk", "BASE-BYTES");
        File splitLang = writeSampleFile("split_config.he.apk", "HE-BYTES");
        AppBackupHelper.InstalledAppEntry split = new AppBackupHelper.InstalledAppEntry();
        split.appName = "Browser";
        split.versionName = "5.2";
        split.sourceApk = baseSplit;
        split.splitApks.add(splitLang);

        File backedUpSplit = AppBackupHelper.backupAppToDownloads(split, downloads);
        assertEquals("Browser_5.2.apks", backedUpSplit.getName());
        assertTrue(backedUpSplit.exists());
        try (ZipFile zf = new ZipFile(backedUpSplit)) {
            assertNotNull(zf.getEntry("base.apk"));
            assertNotNull(zf.getEntry("split_config.he.apk"));
        }
    }
}
