package ru.zahaand.smarttaskbot.model;

/**
 * All possible states in the user's conversation state machine.
 * AWAITING_LANGUAGE and AWAITING_TIMEZONE cover the two-step registration flow;
 * remaining states drive multi-step dialog flows (task creation, reminders, deletion).
 * <p>
 * Все возможные состояния машины состояний диалога пользователя.
 * AWAITING_LANGUAGE и AWAITING_TIMEZONE — двухшаговая регистрация;
 * остальные состояния управляют многошаговыми диалогами.
 */
public enum ConversationState {
    AWAITING_LANGUAGE,
    AWAITING_TIMEZONE,
    IDLE,
    CREATING_TASK,
    SELECTING_REMINDER_DATE,
    ENTERING_REMINDER_TIME,
    CONFIRMING_DELETE,
    CONFIRMING_DELETE_ACCOUNT
}
