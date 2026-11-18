package com.aishhatorah.filemanagerpro;

import org.junit.Test;

import static org.junit.Assert.*;

public class FileIndexDatabaseTest {

    @Test
    public void normalizeStripsBidiMarksAndLowercases() {
        String raw = " \u200F\u202Aדוח_שנתי\u202C PDF ";
        assertEquals("דוח_שנתי pdf", FileIndexDatabase.normalize(raw));
        assertEquals("", FileIndexDatabase.normalize(null));
    }

    @Test
    public void tokenizeSplitsWhitespaceAndSupportsSingleChar() {
        String[] single = FileIndexDatabase.tokenize("א");
        assertArrayEquals(new String[]{"א"}, single);

        String[] multi = FileIndexDatabase.tokenize("  סרט   חתונה  2026 ");
        assertArrayEquals(new String[]{"סרט", "חתונה", "2026"}, multi);

        assertEquals(0, FileIndexDatabase.tokenize("   ").length);
    }

    @Test
    public void matchesAllTokensFindsBothFilesAndFolders() {
        String normFolder = FileIndexDatabase.normalize("תיקיית תמונות ירושלים 2026");
        String normFile = FileIndexDatabase.normalize("00014_AVCHD_ירושלים.mts");
        String[] tokens = FileIndexDatabase.tokenize("ירושלים 00014");

        assertFalse(FileIndexDatabase.matchesAllTokens(normFolder, tokens));
        assertTrue(FileIndexDatabase.matchesAllTokens(normFile, tokens));
        assertTrue(FileIndexDatabase.matchesAllTokens(normFolder, FileIndexDatabase.tokenize("תמונות ירושלים")));
    }
}
