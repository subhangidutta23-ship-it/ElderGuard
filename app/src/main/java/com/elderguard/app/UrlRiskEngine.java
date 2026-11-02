package com.elderguard.app;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UrlRiskEngine {

    // Common URL shorteners used by scammers to mask real destinations
    private static final String[] SHORTENERS = {
            "bit.ly", "tinyurl.com", "t.co", "is.gd", "rb.gy", "cutt.ly", "shorturl.at"
    };

    // Free, cheap, or high-abuse top-level domains commonly used in phishing
    private static final String[] SUSPICIOUS_TLDS = {
            ".xyz", ".top", ".club", ".click", ".buzz", ".work", ".link", ".icu", ".cam"
    };

    // Legitimate, official Indian bank & utility domains for whitelist verification
    private static final String[] GENUINE_DOMAINS = {
            "onlinesbi.sbi", "sbi.co.in", "hdfcbank.com", "icicibank.com",
            "axisbank.com", "pnbindia.in", "paytm.com", "rbi.org.in", "npci.org.in"
    };

    public static class UrlCheckResult {
        public boolean hasUrl = false;
        public boolean isDangerous = false;
        public List<String> reasons = new ArrayList<>();
    }

    public static UrlCheckResult analyzeTextForUrls(String text) {
        UrlCheckResult result = new UrlCheckResult();
        if (text == null || text.trim().isEmpty()) return result;

        // Regex to extract full URLs or web addresses from message text
        Pattern pattern = Pattern.compile("(?i)\\b((?:https?://|www\\d{0,3}[.]|[a-z0-9.\\-]+[.][a-z]{2,4}/)(?:[^\\s()<>]+|\\(([^\\s()<>]+|(\\([^\\s()<>]+\\)))*\\))+(?:\\(([^\\s()<>]+|(\\([^\\s()<>]+\\)))*\\)|[^\\s`!()\\[\\]{};:'\".,<>?«»“”‘’]))");
        Matcher matcher = pattern.matcher(text);

        while (matcher.find()) {
            result.hasUrl = true;
            String url = matcher.group().toLowerCase();

            // 1. Check for URL shorteners
            for (String shortener : SHORTENERS) {
                if (url.contains(shortener)) {
                    result.isDangerous = true;
                    result.reasons.add("Masked Short URL (" + shortener + ")");
                    break;
                }
            }

            // 2. Check for suspicious/cheap domain extensions
            for (String tld : SUSPICIOUS_TLDS) {
                if (url.contains(tld)) {
                    result.isDangerous = true;
                    result.reasons.add("Suspicious Extension (" + tld + ")");
                    break;
                }
            }

            // 3. Check for direct APK / installation file downloads
            if (url.endsWith(".apk") || url.contains(".apk?")) {
                result.isDangerous = true;
                result.reasons.add("Direct APK App Download");
            }

            // 4. Check for bank name spoofing on non-official websites
            boolean claimsBank = url.contains("sbi") || url.contains("hdfc") ||
                    url.contains("icici") || url.contains("rbi") ||
                    url.contains("paytm") || url.contains("reward") ||
                    url.contains("cashback");
            if (claimsBank) {
                boolean isOfficial = false;
                for (String genuine : GENUINE_DOMAINS) {
                    if (url.contains(genuine)) {
                        isOfficial = true;
                        break;
                    }
                }
                if (!isOfficial) {
                    result.isDangerous = true;
                    result.reasons.add("Fake / Spoofed Financial Domain");
                }
            }
        }

        return result;
    }
}