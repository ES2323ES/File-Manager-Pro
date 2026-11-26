package com.aishhatorah.filemanagerpro;

import org.junit.Test;

import static org.junit.Assert.*;

public class UpdateCheckerTest {

    @Test
    public void extractsAndComparesArbitraryDottedVersions() {
        assertEquals("0.3.7.1", UpdateChecker.extractRemoteSemver("V18", "V18 — File Manager Pro v0.3.7.1", ""));
        assertEquals("0.3.8", UpdateChecker.extractRemoteSemver("V19", "V19 — File Manager Pro v0.3.8", ""));
        assertEquals("1.0", UpdateChecker.extractRemoteSemver("V20", "Release v1.0", ""));
        assertEquals("2.1.4.9", UpdateChecker.extractRemoteSemver("v2.1.4.9", "Release", ""));

        assertTrue(UpdateChecker.compareSemver("0.3.7.1", "0.3.7") > 0);
        assertTrue(UpdateChecker.compareSemver("0.3.8", "0.3.7.1") > 0);
        assertTrue(UpdateChecker.compareSemver("1.0", "0.9.9.9") > 0);
        assertEquals(0, UpdateChecker.compareSemver("0.3.8.0", "0.3.8"));
    }

    @Test
    public void extractsRemoteVersionCodeFromReleaseBody() {
        String body = "### שיפורים\n• תיקון באגים\nVersionCode: 39\n";
        assertEquals(39, UpdateChecker.extractRemoteVersionCode(body));
        assertEquals(-1, UpdateChecker.extractRemoteVersionCode("No code here"));
    }
}
