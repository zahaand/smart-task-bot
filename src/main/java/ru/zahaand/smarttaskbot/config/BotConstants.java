package ru.zahaand.smarttaskbot.config;

import java.util.List;
import java.util.Set;

/**
 * Shared constants for bot interaction: timezone lists and callback prefixes.
 * Single source of truth — both keyboard construction and callback validation
 * reference this class to stay in sync.
 */
public final class BotConstants {

    // ── Callback prefixes ────────────────────────────────────────────────────

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

    // ── Persistent menu button labels ────────────────────────────────────────

    public static final String BTN_NEW_TASK = "📝 Новая задача";
    public static final String BTN_MY_TASKS = "📋 Мои задачи";
    public static final String BTN_REMINDER = "⏰ Напоминание";

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
