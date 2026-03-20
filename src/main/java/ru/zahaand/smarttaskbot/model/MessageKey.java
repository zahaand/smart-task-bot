package ru.zahaand.smarttaskbot.model;

/**
 * Typed keys for all user-facing strings in the bot.
 * Each constant carries both its English and Russian variants as constructor arguments.
 * Use {@link #get(Language)} to resolve the string at runtime.
 * Format strings follow {@link String#formatted} / {@code %s} / {@code %d} conventions.
 *
 * Типизированные ключи всех пользовательских сообщений бота.
 * Каждая константа хранит английский и русский варианты.
 * Используйте {@link #get(Language)} для получения строки во время выполнения.
 */
public enum MessageKey {

    // ── Registration ─────────────────────────────────────────────────────────

    WELCOME_BILINGUAL(
            "Welcome to Smart Task Bot! 🤖\n\nChoose your language:\n\nДобро пожаловать в Smart Task Bot! 🤖\n\nВыберите язык:",
            "Welcome to Smart Task Bot! 🤖\n\nChoose your language:\n\nДобро пожаловать в Smart Task Bot! 🤖\n\nВыберите язык:"
    ),
    SELECT_TIMEZONE(
            "Please select your timezone:",
            "Выберите часовой пояс:"
    ),
    ALREADY_REGISTERED(
            "You are already registered. Use the buttons below.",
            "Вы уже зарегистрированы. Используйте кнопки ниже."
    ),
    TIMEZONE_CONFIRMED(
            "Timezone set: %s ✓\nYou're all set! Use the buttons below.",
            "Часовой пояс: %s ✓\nВсё готово! Используйте кнопки ниже."
    ),

    // ── Task creation ─────────────────────────────────────────────────────────

    ENTER_TASK_DESCRIPTION(
            "Enter task description:",
            "Введите описание задачи:"
    ),
    CHOOSE_REMINDER_DATE(
            "Choose reminder date:",
            "Выберите дату напоминания:"
    ),
    ENTER_REMINDER_TIME(
            "Enter reminder time (HH:MM, HH MM, or HH-MM):",
            "Введите время напоминания (ЧЧ:ММ, ЧЧ ММ или ЧЧ-ММ):"
    ),
    TASK_CREATED(
            "Task created ✓",
            "Задача создана ✓"
    ),
    TASK_REMINDER_SET(
            "Reminder set for %s ✓",
            "Напоминание установлено на %s ✓"
    ),

    // ── Task operations ───────────────────────────────────────────────────────

    DELETE_CONFIRM_SINGLE(
            "Delete this task? This cannot be undone.",
            "Удалить задачу? Это нельзя отменить."
    ),
    DELETE_CONFIRM_ALL(
            "Delete all %d completed tasks? This cannot be undone.",
            "Удалить %d выполненных задач? Это нельзя отменить."
    ),
    TASK_DELETED(
            "Task deleted.",
            "Задача удалена."
    ),
    ALL_COMPLETED_DELETED(
            "All completed tasks deleted.",
            "Все выполненные задачи удалены."
    ),
    NO_COMPLETED_TASKS(
            "No completed tasks.",
            "Нет выполненных задач."
    ),

    // ── Errors & fallbacks ────────────────────────────────────────────────────

    SOMETHING_WENT_WRONG(
            "Something went wrong. Please try again.",
            "Что-то пошло не так. Попробуйте снова."
    ),
    INVALID_TIME_FORMAT(
            "Invalid time format. Use HH:MM, HH MM, or HH-MM.",
            "Неверный формат времени. Используйте ЧЧ:ММ, ЧЧ ММ или ЧЧ-ММ."
    ),
    OPERATION_CANCELLED(
            "Operation cancelled.",
            "Операция отменена."
    ),
    USE_BUTTONS(
            "Please use the buttons above.",
            "Используйте кнопки выше."
    ),
    COMING_SOON(
            "This feature is coming soon!",
            "Эта функция скоро появится!"
    ),

    // ── Button labels ─────────────────────────────────────────────────────────

    BTN_NEW_TASK(
            "📝 New Task",
            "📝 Новая задача"
    ),
    BTN_MY_TASKS(
            "📋 My Tasks",
            "📋 Мои задачи"
    ),
    BTN_YES_DELETE_ALL(
            "✅ Yes, delete all",
            "✅ Да, удалить всё"
    ),
    BTN_CANCEL(
            "❌ Cancel",
            "❌ Отмена"
    ),

    // ── Reminder notifications ────────────────────────────────────────────────

    REMINDER_NOTIFICATION(
            "⏰ Reminder: %s",
            "⏰ Напоминание: %s"
    );

    private final String en;
    private final String ru;

    MessageKey(String en, String ru) {
        this.en = en;
        this.ru = ru;
    }

    /**
     * Returns the string for the given language, falling back to EN when language is null.
     *
     * Возвращает строку для указанного языка, при null — возвращает английский вариант.
     */
    public String get(Language language) {
        return language == Language.RU ? ru : en;
    }
}
