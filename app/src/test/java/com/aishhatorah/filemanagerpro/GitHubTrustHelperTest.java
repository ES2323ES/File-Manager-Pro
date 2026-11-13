package com.aishhatorah.filemanagerpro;

import org.junit.Test;

import static org.junit.Assert.*;

public class GitHubTrustHelperTest {

    @Test
    public void allowsOfficialGitHubHostsOnly() {
        assertTrue(GitHubTrustHelper.isAllowedGitHubHost("github.com"));
        assertTrue(GitHubTrustHelper.isAllowedGitHubHost("api.github.com"));
        assertTrue(GitHubTrustHelper.isAllowedGitHubHost("objects.githubusercontent.com"));
        assertTrue(GitHubTrustHelper.isAllowedGitHubHost("release-assets.githubusercontent.com"));

        assertFalse(GitHubTrustHelper.isAllowedGitHubHost("evilgithub.com"));
        assertFalse(GitHubTrustHelper.isAllowedGitHubHost("github.com.evil.org"));
        assertFalse(GitHubTrustHelper.isAllowedGitHubHost("google.com"));
        assertFalse(GitHubTrustHelper.isAllowedGitHubHost(""));
        assertFalse(GitHubTrustHelper.isAllowedGitHubHost(null));
    }

    @Test
    public void enforcesHttpsAndValidUrls() {
        assertTrue(GitHubTrustHelper.isAllowedGitHubHttpsUrl("https://api.github.com/repos/ES2323ES/File-Manager-Pro/releases"));
        assertTrue(GitHubTrustHelper.isAllowedGitHubHttpsUrl("https://github.com/ES2323ES/File-Manager-Pro"));

        assertFalse(GitHubTrustHelper.isAllowedGitHubHttpsUrl("http://github.com/ES2323ES/File-Manager-Pro"));
        assertFalse(GitHubTrustHelper.isAllowedGitHubHttpsUrl("https://notgithub.org/release.apk"));
        assertFalse(GitHubTrustHelper.isAllowedGitHubHttpsUrl("javascript:alert(1)"));
    }
}
