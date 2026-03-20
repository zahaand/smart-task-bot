package ru.zahaand.smarttaskbot.model;

public enum ConversationState {
    AWAITING_LANGUAGE,
    AWAITING_TIMEZONE,
    IDLE,
    CREATING_TASK,
    SELECTING_REMINDER_DATE,
    ENTERING_REMINDER_TIME,
    CONFIRMING_DELETE
}
