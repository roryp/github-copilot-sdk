package com.example.copilot;

import java.util.Locale;

public final class StringUtils {

    private StringUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static String reverse(String s) {
        if (s == null) {
            return null;
        }
        int[] codePoints = s.codePoints().toArray();
        for (int i = 0, j = codePoints.length - 1; i < j; i++, j--) {
            int temp = codePoints[i];
            codePoints[i] = codePoints[j];
            codePoints[j] = temp;
        }
        return new String(codePoints, 0, codePoints.length);
    }

    public static boolean isPalindrome(String s) {
        if (s == null) {
            return false;
        }
        String normalized = s.toLowerCase(Locale.ROOT);
        int[] codePoints = normalized.codePoints().toArray();
        for (int i = 0, j = codePoints.length - 1; i < j; i++, j--) {
            if (codePoints[i] != codePoints[j]) {
                return false;
            }
        }
        return true;
    }

    public static String truncate(String s, int maxLen) {
        if (s == null) {
            return null;
        }
        if (maxLen < 0) {
            throw new IllegalArgumentException("maxLen must be non-negative");
        }
        int[] codePoints = s.codePoints().toArray();
        if (codePoints.length <= maxLen) {
            return s;
        }
        if (maxLen < 3) {
            return new String(codePoints, 0, maxLen);
        }
        return new String(codePoints, 0, maxLen - 3) + "...";
    }
}