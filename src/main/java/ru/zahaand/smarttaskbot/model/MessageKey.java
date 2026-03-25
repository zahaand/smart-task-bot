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

    // ── Task operations (extended) ────────────────────────────────────────────

    TASK_COMPLETED(
            "Task completed ✓",
            "Задача выполнена ✓"
    ),
    TASK_ALREADY_DELETED(
            "Task has already been deleted.",
            "Задача уже была удалена."
    ),
    NO_ACTIVE_TASKS(
            "You have no active tasks.\nUse /newtask to create one.",
            "У вас нет активных задач.\nИспользуйте /newtask для создания."
    ),
    TASKS_ACTIVE_HEADER(
            "Your active tasks",
            "Ваши активные задачи"
    ),

    // ── Errors & help ─────────────────────────────────────────────────────────

    SESSION_EXPIRED(
            "Your session has expired. Please start over.",
            "Сессия истекла. Пожалуйста, начните заново."
    ),
    HELP_TEXT(
            """
                    Smart Task Bot — available commands:
                    
                    /newtask <text>              — create a new task
                    /tasks                       — list your active tasks
                    /remind <id> DD.MM.YYYY HH:mm  — set a reminder
                    /done <id>                   — mark a task as completed
                    /help                        — show this message""",
            """
                    Smart Task Bot — доступные команды:
                    
                    /newtask <текст>                — создать новую задачу
                    /tasks                          — список активных задач
                    /remind <id> ДД.ММ.ГГГГ ЧЧ:мм  — установить напоминание
                    /done <id>                      — отметить задачу выполненной
                    /help                           — показать это сообщение"""
    ),
    DONE_USAGE_HINT(
            "Please provide a task ID.\nUsage: /done <task_id>",
            "Укажите ID задачи.\nИспользование: /done <task_id>"
    ),
    REMIND_USAGE_HINT(
            "Usage: /remind <id> DD.MM.YYYY HH:mm\nExample: /remind 42 25.03.2026 09:00",
            "Использование: /remind <id> ДД.ММ.ГГГГ ЧЧ:мм\nПример: /remind 42 25.03.2026 09:00"
    ),
    REMIND_FORMAT_ERROR(
            "Invalid date format.\nUsage: /remind <id> DD.MM.YYYY HH:mm\nExample: /remind 42 25.03.2026 09:00",
            "Неверный формат даты.\nИспользование: /remind <id> ДД.ММ.ГГГГ ЧЧ:мм\nПример: /remind 42 25.03.2026 09:00"
    ),
    TASK_TEXT_EMPTY(
            "Task text cannot be empty.",
            "Описание задачи не может быть пустым."
    ),
    TWELVE_OCLOCK_HINT(
            "For 12 o'clock use the 24-hour format: 00:00 (midnight) or 12:00 (noon).",
            "Для 12 часов используйте 24-часовой формат: 00:00 (полночь) или 12:00 (полдень)."
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
