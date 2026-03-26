package ru.zahaand.smarttaskbot.service;

import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalTime;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses free-text time input into {@link LocalTime}.
 * Supports 24-hour notation and Russian AM/PM suffixes ("утра" = AM, "вечера" = PM).
 * Never throws — all parse failures return {@link Optional#empty()}.
 */
@UtilityClass
public class TimeParserUtils {

    private static final Logger log = LoggerFactory.getLogger(TimeParserUtils.class);

    // HH:mm or H:mm  (24-hour, colon-separated)
    private static final Pattern P_HH_MM = Pattern.compile(
            "^(\\d{1,2}):(\\d{2})$");

    // HH mm or H mm  (24-hour, space-separated)
    private static final Pattern P_HH_SPACE_MM = Pattern.compile(
            "^(\\d{1,2}) (\\d{2})$");

    // HH-mm or H-mm  (24-hour, hyphen-separated)
    private static final Pattern P_HH_HYPHEN_MM = Pattern.compile(
            "^(\\d{1,2})-(\\d{2})$");

    // N утра  (N ∈ 1–11, whole-hour AM)
    private static final Pattern P_N_UTRA = Pattern.compile(
            "^(\\d{1,2})\\s+утра$", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    // N вечера  (N ∈ 1–11, whole-hour PM)
    private static final Pattern P_N_VECHERA = Pattern.compile(
            "^(\\d{1,2})\\s+вечера$", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    // N:mm утра
    private static final Pattern P_NMM_UTRA = Pattern.compile(
            "^(\\d{1,2}):(\\d{2})\\s+утра$", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    // N:mm вечера
    private static final Pattern P_NMM_VECHERA = Pattern.compile(
            "^(\\d{1,2}):(\\d{2})\\s+вечера$", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /**
     * Parses {@code input} into a {@link LocalTime}.
     *
     * @param input raw user text (may be null or blank)
     * @return parsed time, or empty if the input matches no supported format or is rejected
     */
    public static Optional<LocalTime> parse(String input) {
        if (StringUtils.isBlank(input)) {
            return Optional.empty();
        }

        final String trimmed = input.trim();

        try {
            return tryParse(trimmed);
        } catch (Exception e) {
            log.warn("Unexpected error parsing time input '{}': {}", trimmed, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Returns {@code true} when the input is exactly "12 утра" or "12 вечера"
     * (the ambiguous 12-o'clock case, requiring a specific format hint).
     */
    public static boolean isTwelveOClockAmbiguous(String input) {
        if (StringUtils.isBlank(input)) {
            return false;
        }
        final String normalized = input.trim().toLowerCase();
        return normalized.equals("12 утра") || normalized.equals("12 вечера");
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private static Optional<LocalTime> tryParse(String input) {
        Matcher m;

        m = P_HH_MM.matcher(input);
        if (m.matches()) {
            final int hour = Integer.parseInt(m.group(1));
            final int minute = Integer.parseInt(m.group(2));
            return validTime(hour, minute);
        }

        m = P_HH_SPACE_MM.matcher(input);
        if (m.matches()) {
            final int hour = Integer.parseInt(m.group(1));
            final int minute = Integer.parseInt(m.group(2));
            return validTime(hour, minute);
        }

        m = P_HH_HYPHEN_MM.matcher(input);
        if (m.matches()) {
            final int hour = Integer.parseInt(m.group(1));
            final int minute = Integer.parseInt(m.group(2));
            return validTime(hour, minute);
        }

        m = P_NMM_UTRA.matcher(input);
        if (m.matches()) {
            final int n = Integer.parseInt(m.group(1));
            final int minute = Integer.parseInt(m.group(2));
            if (n < 1 || n > 11) {
                return Optional.empty();
            }
            return validTime(n, minute);
        }

        m = P_NMM_VECHERA.matcher(input);
        if (m.matches()) {
            final int n = Integer.parseInt(m.group(1));
            final int minute = Integer.parseInt(m.group(2));
            if (n < 1 || n > 11) {
                return Optional.empty();
            }
            return validTime(n + 12, minute);
        }

        m = P_N_UTRA.matcher(input);
        if (m.matches()) {
            final int n = Integer.parseInt(m.group(1));
            if (n == 12 || n < 1 || n > 11) {
                return Optional.empty();
            }
            return validTime(n, 0);
        }

        m = P_N_VECHERA.matcher(input);
        if (m.matches()) {
            final int n = Integer.parseInt(m.group(1));
            if (n == 12 || n < 1 || n > 11) {
                return Optional.empty();
            }
            return validTime(n + 12, 0);
        }

        return Optional.empty();
    }

    private static Optional<LocalTime> validTime(int hour, int minute) {
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
            return Optional.empty();
        }
        return Optional.of(LocalTime.of(hour, minute));
    }
}
