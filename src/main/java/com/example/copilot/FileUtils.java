package com.example.copilot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public final class FileUtils {

    private FileUtils() {
        throw new AssertionError("Utility class");
    }

    public static List<String> readLines(Path file) throws IOException {
        Objects.requireNonNull(file, "file must not be null");
        return Files.readAllLines(file);
    }

    public static String humanReadableSize(long bytes) {
        if (bytes < 0) {
            throw new IllegalArgumentException("bytes must not be negative");
        }
        if (bytes < 1024) {
            return bytes + " B";
        }
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String unit = "KMGTPE".charAt(exp - 1) + "B";
        double value = bytes / Math.pow(1024, exp);
        return String.format("%.1f %s", value, unit);
    }

    public static String extension(Path file) {
        Objects.requireNonNull(file, "file must not be null");
        var fileName = file.getFileName();
        if (fileName == null) {
            return "";
        }
        String name = fileName.toString();
        int lastDot = name.lastIndexOf('.');
        if (lastDot <= 0) {
            return "";
        }
        return name.substring(lastDot + 1);
    }
}