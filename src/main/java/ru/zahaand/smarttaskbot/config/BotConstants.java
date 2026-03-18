package ru.zahaand.smarttaskbot.config;

import java.util.List;
import java.util.Set;

/**
 * Shared constants for bot interaction: timezone lists and callback prefixes.
 * Single source of truth — both keyboard construction and callback validation
 * reference this class to stay in sync.
 */
public final class BotConstants {

    public static final String TZ_CALLBACK_PREFIX = "tz:";

    /**
     * Predefined timezone options displayed as inline keyboard rows (FR-018).
     * Order determines button layout: each inner list is one keyboard row.
     */
    public static final List<List<String>> TIMEZONE_ROWS = List.of(
            List.of("Europe/Kaliningrad", "Europe/Moscow"),
            List.of("Asia/Yekaterinburg", "Asia/Novosibirsk"),
            List.of("Asia/Vladivostok")
    );

    /**
     * Flat set of all valid timezone identifiers derived from {@link #TIMEZONE_ROWS}.
     * Used to validate incoming callback data before registration.
     */
    public static final Set<String> VALID_TIMEZONES = Set.of(
            "Europe/Kaliningrad",
            "Europe/Moscow",
            "Asia/Yekaterinburg",
            "Asia/Novosibirsk",
            "Asia/Vladivostok"
    );

    private BotConstants() {
        // utility class — no instances
    }
}
