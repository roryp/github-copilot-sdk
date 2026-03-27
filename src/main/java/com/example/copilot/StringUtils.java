package com.example.copilot;

public final class StringUtils {

    private StringUtils() {
        throw new AssertionError("Utility class");
    }

    public static String reverse(String s) {
        if (s == null) {
            return null;
        }
        return new StringBuilder(s).reverse().toString();
    }

    public static boolean isPalindrome(String s) {
        if (s == null || s.isEmpty()) {
            return true;
        }
        var normalized = s.toLowerCase();
        var left = 0;
        var right = normalized.length() - 1;
        while (left < right) {
            if (normalized.charAt(left) != normalized.charAt(right)) {
                return false;
            }
            left++;
            right--;
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
        if (s.length() <= maxLen) {
            return s;
        }
        if (maxLen < 3) {
            return s.substring(0, maxLen);
        }
        return s.substring(0, maxLen - 3) + "...";
    }
}