package com.example.copilot;

public final class StringUtils {
    
    private StringUtils() {
        throw new AssertionError("Utility class cannot be instantiated");
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
        var len = normalized.length();
        for (int i = 0; i < len / 2; i++) {
            if (normalized.charAt(i) != normalized.charAt(len - 1 - i)) {
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
        if (s.length() <= maxLen) {
            return s;
        }
        if (maxLen < 3) {
            return s.substring(0, maxLen);
        }
        return s.substring(0, maxLen - 3) + "...";
    }
}