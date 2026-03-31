package com.example.copilot.generated;

public final class StringUtils {
    
    private StringUtils() {
        throw new UnsupportedOperationException("Utility class");
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
        String normalized = s.toLowerCase();
        return normalized.contentEquals(new StringBuilder(normalized).reverse());
    }
    
    public static String truncate(String s, int maxLen) {
        if (s == null) {
            return null;
        }
        if (s.length() <= maxLen) {
            return s;
        }
        return s.substring(0, Math.max(0, maxLen - 3)) + "...";
    }
}