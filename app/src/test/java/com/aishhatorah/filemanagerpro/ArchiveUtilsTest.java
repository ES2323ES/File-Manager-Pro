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

    @Test
    public void archiveTreeNavigationGroupsNestedFolders() {
        List<ArchiveUtils.ArchiveEntryInfo> entries = java.util.Arrays.asList(
                new ArchiveUtils.ArchiveEntryInfo("res/layout/main.xml", 120, 60, false, false),
                new ArchiveUtils.ArchiveEntryInfo("res/drawable/icon.png", 500, 300, false, false),
                new ArchiveUtils.ArchiveEntryInfo("AndroidManifest.xml", 800, 400, false, false),
                new ArchiveUtils.ArchiveEntryInfo("assets/sub/readme.txt", 40, 25, false, true)
        );

        List<ArchiveUtils.ArchiveTreeNode> rootNodes = ArchiveUtils.listTreeChildren(entries, "");
        assertEquals(3, rootNodes.size());
        assertTrue(rootNodes.get(0).directory);
        assertTrue(rootNodes.get(1).directory);
        assertFalse(rootNodes.get(2).directory);
        assertEquals("AndroidManifest.xml", rootNodes.get(2).displayName);

        List<ArchiveUtils.ArchiveTreeNode> resChildren = ArchiveUtils.listTreeChildren(entries, "res/");
        assertEquals(2, resChildren.size());
        assertTrue(resChildren.get(0).directory);
        assertTrue(resChildren.get(1).directory);

        List<ArchiveUtils.ArchiveTreeNode> layoutChildren = ArchiveUtils.listTreeChildren(entries, "res/layout/");
        assertEquals(1, layoutChildren.size());
        assertEquals("main.xml", layoutChildren.get(0).displayName);
        assertFalse(layoutChildren.get(0).directory);
    }

    @Test
    public void extractEntryToFileStreamsPlainAndEncryptedEntries() throws Exception {
        File folder = tmp.newFolder("src_pack");
        File sub = new File(folder, "sub");
        assertTrue(sub.mkdirs());
        File binFile = new File(sub, "payload.bin");
        byte[] binary = new byte[160_000];
        new java.util.Random(42L).nextBytes(binary);
        try (FileOutputStream fos = new FileOutputStream(binFile)) {
            fos.write(binary);
        }
        File txtFile = new File(folder, "notes.txt");
        try (FileOutputStream fos = new FileOutputStream(txtFile)) {
            fos.write("Fast streaming archive check".getBytes(StandardCharsets.UTF_8));
        }

        File plainZip = new File(tmp.getRoot(), "plain_stream.zip");
        ArchiveUtils.compressFiles(java.util.Arrays.asList(folder), plainZip, "zip", null);

        File extractedPlain = new File(tmp.getRoot(), "extracted_plain.bin");
        ArchiveUtils.extractEntryToFile(plainZip, "src_pack/sub/payload.bin", null, extractedPlain);
        assertArrayEquals(binary, java.nio.file.Files.readAllBytes(extractedPlain.toPath()));

        File encZip = new File(tmp.getRoot(), "enc_stream.zip");
        ArchiveUtils.compressFiles(java.util.Arrays.asList(binFile, txtFile), encZip, "zip", "StreamPass!7");
        assertTrue(ArchiveUtils.archiveRequiresPassword(encZip));

        File extractedEnc = new File(tmp.getRoot(), "extracted_enc.bin");
        ArchiveUtils.extractEntryToFile(encZip, "payload.bin", "StreamPass!7", extractedEnc);
        assertArrayEquals(binary, java.nio.file.Files.readAllBytes(extractedEnc.toPath()));

        File outAll = tmp.newFolder("out_enc_all");
        assertEquals(2, ArchiveUtils.extractArchive(encZip, outAll, "StreamPass!7"));
        assertArrayEquals(binary, java.nio.file.Files.readAllBytes(new File(outAll, "payload.bin").toPath()));
        assertEquals("Fast streaming archive check",
                new String(java.nio.file.Files.readAllBytes(new File(outAll, "notes.txt").toPath()), StandardCharsets.UTF_8));
    }

    @Test
    public void zip64CentralDirectoryAndExtraFieldResolveProperly() throws Exception {
        byte[] data = "Zip64PayloadVerify".getBytes(StandardCharsets.UTF_8);
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(data);
        byte[] nameBytes = "big.txt".getBytes(StandardCharsets.UTF_8);

        java.io.ByteArrayOutputStream raw = new java.io.ByteArrayOutputStream();
        java.nio.ByteBuffer lh = java.nio.ByteBuffer.allocate(30).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        lh.putInt(0x04034b50);
        lh.putShort((short) 45);
        lh.putShort((short) 0x0800);
        lh.putShort((short) 0); // STORE
        lh.putShort((short) 0);
        lh.putShort((short) 0);
        lh.putInt((int) crc.getValue());
        lh.putInt(data.length);
        lh.putInt(data.length);
        lh.putShort((short) nameBytes.length);
        lh.putShort((short) 0);
        raw.write(lh.array());
        raw.write(nameBytes);
        raw.write(data);

        long cdOffset = raw.size();
        // Zip64 Extra Field (headerId=0x0001, dataSize=24 -> uncomp, comp, offset)
        java.nio.ByteBuffer extra = java.nio.ByteBuffer.allocate(28).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        extra.putShort((short) 0x0001);
        extra.putShort((short) 24);
        extra.putLong(data.length);
        extra.putLong(data.length);
        extra.putLong(0L);

        java.nio.ByteBuffer cd = java.nio.ByteBuffer.allocate(46).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        cd.putInt(0x02014b50);
        cd.putShort((short) 45);
        cd.putShort((short) 45);
        cd.putShort((short) 0x0800);
        cd.putShort((short) 0);
        cd.putShort((short) 0);
        cd.putShort((short) 0);
        cd.putInt((int) crc.getValue());
        cd.putInt(0xFFFFFFFF);
        cd.putInt(0xFFFFFFFF);
        cd.putShort((short) nameBytes.length);
        cd.putShort((short) extra.capacity());
        cd.putShort((short) 0);
        cd.putShort((short) 0);
        cd.putShort((short) 0);
        cd.putInt(0);
        cd.putInt(0xFFFFFFFF);
        raw.write(cd.array());
        raw.write(nameBytes);
        raw.write(extra.array());

        long cdSize = raw.size() - cdOffset;
        long zip64EocdOffset = raw.size();

        java.nio.ByteBuffer z64Eocd = java.nio.ByteBuffer.allocate(56).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        z64Eocd.putInt(0x06064b50);
        z64Eocd.putLong(44L);
        z64Eocd.putShort((short) 45);
        z64Eocd.putShort((short) 45);
        z64Eocd.putInt(0);
        z64Eocd.putInt(0);
        z64Eocd.putLong(1L);
        z64Eocd.putLong(1L);
        z64Eocd.putLong(cdSize);
        z64Eocd.putLong(cdOffset);
        raw.write(z64Eocd.array());

        java.nio.ByteBuffer z64Loc = java.nio.ByteBuffer.allocate(20).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        z64Loc.putInt(0x07064b50);
        z64Loc.putInt(0);
        z64Loc.putLong(zip64EocdOffset);
        z64Loc.putInt(1);
        raw.write(z64Loc.array());

        java.nio.ByteBuffer eocd = java.nio.ByteBuffer.allocate(22).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        eocd.putInt(0x06054b50);
        eocd.putShort((short) 0);
        eocd.putShort((short) 0);
        eocd.putShort((short) 0xFFFF);
        eocd.putShort((short) 0xFFFF);
        eocd.putInt(0xFFFFFFFF);
        eocd.putInt(0xFFFFFFFF);
        eocd.putShort((short) 0);
        raw.write(eocd.array());

        File z64Zip = new File(tmp.getRoot(), "zip64_test.zip");
        try (FileOutputStream fos = new FileOutputStream(z64Zip)) {
            fos.write(raw.toByteArray());
        }

        List<ArchiveUtils.ArchiveEntryInfo> entries = ArchiveUtils.listArchiveEntries(z64Zip);
        assertEquals(1, entries.size());
        assertEquals("big.txt", entries.get(0).name);
        assertEquals(data.length, entries.get(0).size);
        assertEquals(data.length, entries.get(0).compressedSize);
    }

    @Test
    public void extractSingleOrFolderExtractsSelectedSubsetCleanly() throws Exception {
        File rootPack = tmp.newFolder("multi_pack");
        File dirA = new File(rootPack, "alpha");
        File dirB = new File(rootPack, "beta");
        assertTrue(dirA.mkdirs());
        assertTrue(dirB.mkdirs());
        try (FileOutputStream fos = new FileOutputStream(new File(dirA, "a1.txt"))) {
            fos.write("Alpha1".getBytes(StandardCharsets.UTF_8));
        }
        try (FileOutputStream fos = new FileOutputStream(new File(dirA, "a2.txt"))) {
            fos.write("Alpha2".getBytes(StandardCharsets.UTF_8));
        }
        try (FileOutputStream fos = new FileOutputStream(new File(dirB, "b1.txt"))) {
            fos.write("Beta1".getBytes(StandardCharsets.UTF_8));
        }

        File zipFile = new File(tmp.getRoot(), "subset.zip");
        ArchiveUtils.compressFiles(java.util.Arrays.asList(dirA, dirB), zipFile, "zip", null);

        File onlyFolderOut = tmp.newFolder("only_alpha");
        int dirCount = ArchiveUtils.extractSingleOrFolder(zipFile, "alpha/", true, onlyFolderOut, "", null);
        assertEquals(2, dirCount);
        assertTrue(new File(onlyFolderOut, "alpha/a1.txt").exists());
        assertTrue(new File(onlyFolderOut, "alpha/a2.txt").exists());
        assertFalse(new File(onlyFolderOut, "beta/b1.txt").exists());

        File onlyFileOut = tmp.newFolder("only_b1");
        int singleCount = ArchiveUtils.extractSingleOrFolder(zipFile, "beta/b1.txt", false, onlyFileOut, "", null);
        assertEquals(1, singleCount);
        assertTrue(new File(onlyFileOut, "b1.txt").exists());
        assertFalse(new File(onlyFileOut, "alpha").exists());

        File tgzFile = new File(tmp.getRoot(), "subset.tar.gz");
        ArchiveUtils.compressFiles(java.util.Arrays.asList(dirA, dirB), tgzFile, "tar.gz", null);
        File tgzAlphaOut = tmp.newFolder("tgz_alpha");
        int tgzDirCount = ArchiveUtils.extractSingleOrFolder(tgzFile, "alpha/", true, tgzAlphaOut, "", null);
        assertEquals(2, tgzDirCount);
        assertTrue(new File(tgzAlphaOut, "alpha/a1.txt").exists());
        assertFalse(new File(tgzAlphaOut, "beta/b1.txt").exists());
    }
}
