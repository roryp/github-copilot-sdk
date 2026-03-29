package com.example.copilot;

import java.time.LocalDate;
import java.time.DayOfWeek;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

public final class DateUtils {

    private DateUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static long daysUntil(LocalDate target) {
        Objects.requireNonNull(target, "target must not be null");
        return ChronoUnit.DAYS.between(LocalDate.now(), target);
    }

    public static boolean isWeekend(LocalDate date) {
        Objects.requireNonNull(date, "date must not be null");
        DayOfWeek day = date.getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }

    public static String formatRelative(LocalDate date) {
        Objects.requireNonNull(date, "date must not be null");
        long daysDiff = ChronoUnit.DAYS.between(date, LocalDate.now());
        
        if (daysDiff == 0L) {
            return "today";
        } else if (daysDiff == 1L) {
            return "yesterday";
        } else if (daysDiff == -1L) {
            return "tomorrow";
        } else if (daysDiff > 1L) {
            return daysDiff + " days ago";
        } else {
            return (-daysDiff) + " days from now";
        }
    }
}