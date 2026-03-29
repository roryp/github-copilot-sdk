package com.example.copilot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public final class FileUtils {

    private FileUtils() {
        throw new UnsupportedOperationException("Utility class");
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
        var units = new String[]{"KB", "MB", "GB", "TB", "PB", "EB"};
        var value = (double) bytes;
        int unitIndex = -1;
        while (value >= 1024.0 && unitIndex < units.length - 1) {
            value /= 1024.0;
            unitIndex++;
        }
        return String.format("%.1f %s", value, units[unitIndex]);
    }

    public static String extension(Path file) {
        Objects.requireNonNull(file, "file must not be null");
        var fileName = file.getFileName();
        if (fileName == null) {
            return "";
        }
        var name = fileName.toString();
        var lastDot = name.lastIndexOf('.');
        if (lastDot <= 0) {
            return "";
        }
        return name.substring(lastDot + 1);
    }
}