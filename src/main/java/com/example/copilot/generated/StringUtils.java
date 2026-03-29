package com.example.copilot.generated;

import java.util.Locale;

public final class StringUtils {

    private StringUtils() {
        throw new AssertionError("Utility class cannot be instantiated");
    }

    public static String reverse(String s) {
        if (s == null) {
            return null;
        }
        if (s.isEmpty()) {
            return "";
        }
        int[] codePoints = s.codePoints().toArray();
        int[] reversed = new int[codePoints.length];
        for (int i = 0; i < codePoints.length; i++) {
            reversed[i] = codePoints[codePoints.length - 1 - i];
        }
        return new String(reversed, 0, reversed.length);
    }

    public static boolean isPalindrome(String s) {
        if (s == null) {
            return false;
        }
        if (s.isEmpty()) {
            return true;
        }
        String lower = s.toLowerCase(Locale.ROOT);
        int[] codePoints = lower.codePoints().toArray();
        int length = codePoints.length;
        for (int i = 0; i < length / 2; i++) {
            if (codePoints[i] != codePoints[length - 1 - i]) {
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
            throw new IllegalArgumentException("maxLen cannot be negative");
        }
        int count = s.codePointCount(0, s.length());
        if (count <= maxLen) {
            return s;
        }
        int[] codePoints = s.codePoints().toArray();
        if (maxLen < 3) {
            return new String(codePoints, 0, maxLen);
        }
        return new String(codePoints, 0, maxLen - 3) + "...";
    }
}