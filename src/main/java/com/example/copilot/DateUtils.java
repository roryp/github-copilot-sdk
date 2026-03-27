package com.example.copilot;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public final class DateUtils {

    private DateUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static long daysUntil(LocalDate target) {
        return ChronoUnit.DAYS.between(LocalDate.now(), target);
    }

    public static boolean isWeekend(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }

    public static String formatRelative(LocalDate date) {
        long daysDiff = ChronoUnit.DAYS.between(date, LocalDate.now());
        
        return switch ((int) daysDiff) {
            case 0 -> "today";
            case 1 -> "yesterday";
            case -1 -> "tomorrow";
            default -> {
                if (daysDiff > 0) {
                    yield daysDiff + " days ago";
                } else {
                    yield "in " + Math.abs(daysDiff) + " days";
                }
            }
        };
    }
}