package ru.zahaand.smarttaskbot.config;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shared constants for bot interaction: timezone lists and callback prefixes.
 * Single source of truth — both keyboard construction and callback validation
 * reference this class to stay in sync.
 */
public final class BotConstants {

    // ── Callback prefixes ────────────────────────────────────────────────────

    /** Language selection (new-user registration step 1). */
    public static final String CB_LANG    = "lang:";
    public static final String CB_LANG_EN = "lang:EN";
    public static final String CB_LANG_RU = "lang:RU";

    /** Delete All Completed tasks — three-step confirmation flow. */
    public static final String CB_DELETE_ALL_REQUEST = "DELETE_ALL:request";
    public static final String CB_DELETE_ALL_CONFIRM = "DELETE_ALL:confirm";
    public static final String CB_DELETE_ALL_CANCEL  = "DELETE_ALL:cancel";

    /**
     * Timezone selection (legacy — used by TimezoneCallbackHandler).
     */
    public static final String CB_TZ = "tz:";

    /**
     * @deprecated Use {@link #CB_TZ}. Kept for backward-compat with existing code.
     */
    public static final String TZ_CALLBACK_PREFIX = CB_TZ;

    public static final String CB_TASK_REMIND = "TASK_REMIND:";
    public static final String CB_TASK_DONE = "TASK_DONE:";
    public static final String CB_TASK_DELETE = "TASK_DELETE:";
    public static final String CB_CAL_DATE = "CAL_DATE:";
    public static final String CB_CAL_NAV = "CAL_NAV:";
    public static final String CB_CONFIRM_DELETE = "CONFIRM_DELETE:";
    public static final String CB_CONFIRM_CANCEL = "CONFIRM_CANCEL";
    public static final String CB_TASKS_TAB = "TASKS_TAB:";
    public static final String CB_NO_OP = "NO_OP";

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
     * Short English city codes for each IANA timezone in {@link #TIMEZONE_ROWS}.
     * Always displayed in English regardless of the user's language (FR-011).
     * Used inside timezone button labels as: "HH:mm CODES" (e.g. "10:00 MSK, SPB").
     */
    public static final Map<String, String> TIMEZONE_CITY_CODES = Map.of(
            "Europe/Kaliningrad", "KGD",
            "Europe/Moscow",      "MSK, SPB",
            "Asia/Yekaterinburg", "YEK",
            "Asia/Novosibirsk",   "NOV, OMS",
            "Asia/Vladivostok",   "VLA, KHA"
    );

    /**
     * Human-readable English display names for each IANA timezone in {@link #TIMEZONE_ROWS}.
     * Fallback label when live-time computation fails (ZoneRulesException).
     */
    public static final Map<String, String> TIMEZONE_DISPLAY_NAMES = Map.of(
            "Europe/Kaliningrad", "Kaliningrad",
            "Europe/Moscow",      "Moscow, SPb",
            "Asia/Yekaterinburg", "Yekaterinburg",
            "Asia/Novosibirsk",   "Novosibirsk",
            "Asia/Vladivostok",   "Vladivostok"
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
