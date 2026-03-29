package com.example.copilot.generated;

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
        long kib = bytes / 1024;
        if (kib < 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        }
        long mib = kib / 1024;
        if (mib < 1024) {
            return String.format("%.1f MB", bytes / 1048576.0);
        }
        long gib = mib / 1024;
        if (gib < 1024) {
            return String.format("%.1f GB", bytes / 1073741824.0);
        }
        return String.format("%.1f TB", bytes / 1099511627776.0);
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