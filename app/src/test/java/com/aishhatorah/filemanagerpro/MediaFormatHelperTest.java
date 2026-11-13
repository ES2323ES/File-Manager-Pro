package com.aishhatorah.filemanagerpro;

import org.junit.Test;

import static org.junit.Assert.*;

public class MediaFormatHelperTest {

    @Test
    public void detectsAllMediaCodeArchiveAndPackageFormats() {
        assertTrue(MediaFormatHelper.isGifFile("anim.GIF"));
        assertTrue(MediaFormatHelper.isSvgFile("icon.svg"));
        assertTrue(MediaFormatHelper.isMarkdownFile("README.md"));
        assertTrue(MediaFormatHelper.isBackupFile("config.json.bak"));

        assertTrue(MediaFormatHelper.isZipOrArchiveFile("pack.tar.gz"));
        assertTrue(MediaFormatHelper.isTarGzArchive("pack.tgz"));
        assertTrue(MediaFormatHelper.isGzArchive("dump.sql.gz"));
        assertFalse(MediaFormatHelper.isGzArchive("archive.tar.gz"));

        assertTrue(MediaFormatHelper.isCodeOrSyntaxFile("Main.kt"));
        assertTrue(MediaFormatHelper.isCodeOrSyntaxFile("index.tsx"));
        assertTrue(MediaFormatHelper.isCodeOrSyntaxFile("Decompiled.smali"));

        assertTrue(MediaFormatHelper.isInstallableAppPackage("App.apkx"));
        assertTrue(MediaFormatHelper.isInstallableAppPackage("App.apk+"));
        assertTrue(MediaFormatHelper.isSplitAppBundle("Bundle.apkm"));
        assertEquals(".tar.gz", MediaFormatHelper.getExtensionNormalized("my.backup.TAR.GZ"));
    }
}
