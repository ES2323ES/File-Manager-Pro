package com.aishhatorah.filemanagerpro;

import java.net.HttpURLConnection;
import java.net.URL;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Locale;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * סעיף 1: תמיכה מלאה ברשתות מסוננות (כגון נטפרי) לבדיקת והורדת עדכונים מ-GitHub,
 * ע"י שילוב מאובטח של תעודות אבטחה שהמשתמש התקין במכשיר (AndroidCAStore user certs)
 * תוך הגבלה מוחלטת וקשיחה לדומיינים הרשמיים של GitHub בלבד!
 */
public final class GitHubTrustHelper {

    private static volatile SSLSocketFactory cachedGitHubSocketFactory = null;

    private GitHubTrustHelper() {
    }

    public static boolean isAllowedGitHubHost(String host) {
        if (host == null) return false;
        String lower = host.trim().toLowerCase(Locale.ROOT);
        if (lower.isEmpty()) return false;
        return "github.com".equals(lower)
                || "api.github.com".equals(lower)
                || "objects.githubusercontent.com".equals(lower)
                || "raw.githubusercontent.com".equals(lower)
                || "release-assets.githubusercontent.com".equals(lower)
                || lower.endsWith(".github.com")
                || lower.endsWith(".githubusercontent.com");
    }

    public static boolean isAllowedGitHubHttpsUrl(String urlStr) {
        if (urlStr == null) return false;
        try {
            URL url = new URL(urlStr);
            if (!"https".equalsIgnoreCase(url.getProtocol())) return false;
            return isAllowedGitHubHost(url.getHost());
        } catch (Exception e) {
            return false;
        }
    }

    public static HttpURLConnection openRedirectAwareConnection(String urlStr, String acceptHeader) throws Exception {
        String currentUrl = urlStr;
        for (int hop = 0; hop < 8; hop++) {
            URL url = new URL(currentUrl);
            if (!"https".equalsIgnoreCase(url.getProtocol()) || !isAllowedGitHubHost(url.getHost())) {
                throw new SecurityException("Blocked non-GitHub update domain: " + url.getHost());
            }

            HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
            SSLSocketFactory sf = getOrBuildGitHubSocketFactory();
            if (sf != null) {
                conn.setSSLSocketFactory(sf);
            }
            conn.setInstanceFollowRedirects(false);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "FileManagerPro-Android/" + BuildConfig.VERSION_NAME);
            if (acceptHeader != null) {
                conn.setRequestProperty("Accept", acceptHeader);
            }
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(25000);

            int code = conn.getResponseCode();
            if (code == HttpURLConnection.HTTP_MOVED_PERM
                    || code == HttpURLConnection.HTTP_MOVED_TEMP
                    || code == HttpURLConnection.HTTP_SEE_OTHER
                    || code == 307
                    || code == 308) {
                String loc = conn.getHeaderField("Location");
                conn.disconnect();
                if (loc != null && !loc.isEmpty()) {
                    URL resolved = new URL(url, loc);
                    if (!"https".equalsIgnoreCase(resolved.getProtocol()) || !isAllowedGitHubHost(resolved.getHost())) {
                        throw new SecurityException("Blocked redirect outside GitHub: " + resolved.getHost());
                    }
                    currentUrl = resolved.toString();
                    continue;
                }
            }
            return conn;
        }
        throw new IllegalStateException("Too many redirects");
    }

    private static synchronized SSLSocketFactory getOrBuildGitHubSocketFactory() {
        if (cachedGitHubSocketFactory != null) {
            return cachedGitHubSocketFactory;
        }
        try {
            TrustManagerFactory sysTmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            sysTmf.init((KeyStore) null);
            final X509TrustManager sysTm = findX509TrustManager(sysTmf.getTrustManagers());

            KeyStore userCaStore = KeyStore.getInstance("AndroidCAStore");
            userCaStore.load(null, null);
            TrustManagerFactory userTmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            userTmf.init(userCaStore);
            final X509TrustManager androidCaTm = findX509TrustManager(userTmf.getTrustManagers());

            X509TrustManager composite = new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) throws java.security.cert.CertificateException {
                    if (sysTm != null) sysTm.checkClientTrusted(chain, authType);
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) throws java.security.cert.CertificateException {
                    try {
                        if (sysTm != null) {
                            sysTm.checkServerTrusted(chain, authType);
                            return;
                        }
                    } catch (java.security.cert.CertificateException sysEx) {
                        if (androidCaTm != null) {
                            androidCaTm.checkServerTrusted(chain, authType);
                            return;
                        }
                        throw sysEx;
                    }
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return androidCaTm != null ? androidCaTm.getAcceptedIssuers()
                            : (sysTm != null ? sysTm.getAcceptedIssuers() : new X509Certificate[0]);
                }
            };

            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, new TrustManager[]{composite}, null);
            cachedGitHubSocketFactory = sc.getSocketFactory();
        } catch (Throwable ignored) {
        }
        return cachedGitHubSocketFactory;
    }

    private static X509TrustManager findX509TrustManager(TrustManager[] list) {
        if (list == null) return null;
        for (TrustManager tm : list) {
            if (tm instanceof X509TrustManager) {
                return (X509TrustManager) tm;
            }
        }
        return null;
    }
}
