package com.aishhatorah.filemanagerpro;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class ArchiveUtilsTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File writeSampleFile(String name, String text) throws Exception {
        File f = tmp.newFile(name);
        try (FileOutputStream fos = new FileOutputStream(f)) {
            fos.write(text.getBytes(StandardCharsets.UTF_8));
        }
        return f;
    }

    @Test
    public void zipSlipSafetyChecksWork() {
        assertFalse(ArchiveUtils.isSafeEntryName("../secret.txt"));
        assertFalse(ArchiveUtils.isSafeEntryName("a/../../etc/passwd"));
        assertFalse(ArchiveUtils.isSafeEntryName("/absolute/path.txt"));
        assertFalse(ArchiveUtils.isSafeEntryName("..\\windows\\win.ini"));
        assertTrue(ArchiveUtils.isSafeEntryName("folder/hello.txt"));
    }

    @Test
    public void standardZipCompressExtractAndEditWork() throws Exception {
        File src = writeSampleFile("hello.txt", "Shalom Olam!");
        File zip = new File(tmp.getRoot(), "test.zip");

        ArchiveUtils.compressFiles(Collections.singletonList(src), zip, "zip", null);
        assertTrue(zip.exists());

        List<ArchiveUtils.ArchiveEntryInfo> entries = ArchiveUtils.listArchiveEntries(zip);
        assertEquals(1, entries.size());
        assertEquals("hello.txt", entries.get(0).name);

        byte[] read = ArchiveUtils.readEntryBytes(zip, "hello.txt", null);
        assertEquals("Shalom Olam!", new String(read, StandardCharsets.UTF_8));

        ArchiveUtils.updateOrAddZipEntry(zip, "hello.txt", "Updated Content".getBytes(StandardCharsets.UTF_8));
        ArchiveUtils.updateOrAddZipEntry(zip, "extra.txt", "Added".getBytes(StandardCharsets.UTF_8));
        assertEquals("Updated Content", new String(ArchiveUtils.readEntryBytes(zip, "hello.txt", null), StandardCharsets.UTF_8));
        assertTrue(ArchiveUtils.deleteZipEntry(zip, "extra.txt"));
        assertEquals(1, ArchiveUtils.listArchiveEntries(zip).size());

        File outDir = tmp.newFolder("out_zip");
        int count = ArchiveUtils.extractArchive(zip, outDir, null);
        assertEquals(1, count);
        assertTrue(new File(outDir, "hello.txt").exists());
    }

    @Test
    public void passwordProtectedZipEnforcesPassword() throws Exception {
        File src = writeSampleFile("secret.txt", "TopSecretData123");
        File zip = new File(tmp.getRoot(), "locked.zip");

        ArchiveUtils.compressFiles(Collections.singletonList(src), zip, "zip", "MyPass99");
        assertTrue(ArchiveUtils.archiveRequiresPassword(zip));

        boolean failedWrong = false;
        try {
            ArchiveUtils.readEntryBytes(zip, "secret.txt", "WrongPass");
        } catch (Exception e) {
            failedWrong = true;
        }
        assertTrue(failedWrong);

        byte[] okBytes = ArchiveUtils.readEntryBytes(zip, "secret.txt", "MyPass99");
        assertEquals("TopSecretData123", new String(okBytes, StandardCharsets.UTF_8));
    }

    @Test
    public void tarAndTarGzRoundTripWork() throws Exception {
        File src = writeSampleFile("data.json", "{\"ok\":true}");
        File tgz = new File(tmp.getRoot(), "bundle.tar.gz");
        ArchiveUtils.compressFiles(Collections.singletonList(src), tgz, "tar.gz", null);

        List<ArchiveUtils.ArchiveEntryInfo> entries = ArchiveUtils.listArchiveEntries(tgz);
        assertEquals(1, entries.size());
        assertEquals("data.json", entries.get(0).name);

        byte[] content = ArchiveUtils.readEntryBytes(tgz, "data.json", null);
        assertEquals("{\"ok\":true}", new String(content, StandardCharsets.UTF_8));

        File outDir = tmp.newFolder("out_tgz");
        assertEquals(1, ArchiveUtils.extractArchive(tgz, outDir, null));
        assertTrue(new File(outDir, "data.json").exists());
    }
}
