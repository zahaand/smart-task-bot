package ru.zahaand.smarttaskbot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.zahaand.smarttaskbot.config.BotConstants;
import ru.zahaand.smarttaskbot.dto.TaskDto;
import ru.zahaand.smarttaskbot.model.Task;
import ru.zahaand.smarttaskbot.model.TaskStatus;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Service responsible for sending all outgoing communications to Telegram users.
 * Provides methods for sending plain text messages, interactive keyboards,
 * and scheduled task reminders.
 */
@Slf4j
@Service
public class NotificationService {

    private final AbsSender sender;
    private final TaskListKeyboardBuilder taskListKeyboardBuilder;
    private final CalendarKeyboardBuilder calendarKeyboardBuilder;

    private static final DateTimeFormatter TZ_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private static final int MAX_TASK_LIST_SIZE = 20;

    public NotificationService(@Lazy AbsSender sender,
                               TaskListKeyboardBuilder taskListKeyboardBuilder,
                               CalendarKeyboardBuilder calendarKeyboardBuilder) {
        this.sender = sender;
        this.taskListKeyboardBuilder = taskListKeyboardBuilder;
        this.calendarKeyboardBuilder = calendarKeyboardBuilder;
    }

    public void sendMessage(Long chatId, String text) {
        SendMessage message = new SendMessage(chatId.toString(), text);

        try {
            sender.execute(message);
        } catch (TelegramApiException e) {
            log.error("Failed to send message to chatId={}: {}", chatId, e.getMessage(), e);
        }
    }

    public void sendTimezoneKeyboard(Long chatId, String text) {
        SendMessage message = new SendMessage(chatId.toString(), text);

        message.setReplyMarkup(buildTimezoneKeyboard());

        try {
            sender.execute(message);
        } catch (TelegramApiException e) {
            log.error("Failed to send timezone keyboard to chatId={}: {}", chatId, e.getMessage(), e);
        }
    }

    public void sendReminder(Task task) {
        Long chatId = task.getUser().getTelegramUserId();
        sendMessage(chatId, "⏰ Reminder: " + task.getText());
    }

    /**
     * Answers a callback query to clear the Telegram loading spinner.
     * Every callback handler MUST call this per the callback-data-contract.
     */
    public void answerCallbackQuery(String callbackQueryId) {
        AnswerCallbackQuery answer = new AnswerCallbackQuery();
        answer.setCallbackQueryId(callbackQueryId);

        try {
            sender.execute(answer);
        } catch (TelegramApiException e) {
            log.warn("Failed to answer callbackQueryId={}: {}", callbackQueryId, e.getMessage());
        }
    }

    /**
     * Sends a message and attaches the persistent 3-button reply keyboard.
     * The keyboard stays visible across messages until explicitly removed.
     */
    public void sendPersistentMenu(Long chatId, String text) {
        SendMessage message = new SendMessage(chatId.toString(), text);
        message.setReplyMarkup(buildPersistentMenuKeyboard());

        try {
            sender.execute(message);
        } catch (TelegramApiException e) {
            log.error("Failed to send persistent menu to chatId={}: {}", chatId, e.getMessage(), e);
        }
    }

    /**
     * Sends a new task-list message with inline action buttons and a tab row.
     * Truncates to {@link #MAX_TASK_LIST_SIZE} tasks and appends a note when the list overflows.
     */
    public void sendTaskList(Long chatId, List<TaskDto> tasks, TaskStatus tab) {
        final boolean truncated = tasks.size() > MAX_TASK_LIST_SIZE;
        final List<TaskDto> visible = truncated ? tasks.subList(0, MAX_TASK_LIST_SIZE) : tasks;

        final String text = buildTaskListText(visible, tab, truncated);
        final InlineKeyboardMarkup keyboard = taskListKeyboardBuilder.buildKeyboard(visible, tab);

        final SendMessage message = new SendMessage(chatId.toString(), text);
        message.setReplyMarkup(keyboard);

        try {
            sender.execute(message);
        } catch (TelegramApiException e) {
            log.error("Failed to send task list to chatId={}: {}", chatId, e.getMessage(), e);
        }
    }

    /**
     * Edits an existing task-list message in-place.
     * Falls back to a new message via {@link #safeEdit} if the Telegram edit API rejects the request.
     */
    public void editTaskList(Long chatId, Integer messageId, List<TaskDto> tasks, TaskStatus tab) {
        final boolean truncated = tasks.size() > MAX_TASK_LIST_SIZE;
        final List<TaskDto> visible = truncated ? tasks.subList(0, MAX_TASK_LIST_SIZE) : tasks;

        final String text = buildTaskListText(visible, tab, truncated);
        final InlineKeyboardMarkup keyboard = taskListKeyboardBuilder.buildKeyboard(visible, tab);

        final EditMessageText edit = new EditMessageText();
        edit.setChatId(chatId.toString());
        edit.setMessageId(messageId);
        edit.setText(text);
        edit.setReplyMarkup(keyboard);

        final SendMessage fallback = new SendMessage(chatId.toString(), text);
        fallback.setReplyMarkup(keyboard);

        safeEdit(edit, fallback);
    }

    /**
     * Sends a new inline calendar message for the given month.
     */
    public void sendCalendar(Long chatId, int year, int month) {
        final InlineKeyboardMarkup keyboard = calendarKeyboardBuilder.buildCalendar(year, month);
        final SendMessage message = new SendMessage(chatId.toString(), "Select a date:");
        message.setReplyMarkup(keyboard);

        try {
            sender.execute(message);
        } catch (TelegramApiException e) {
            log.error("Failed to send calendar to chatId={}: {}", chatId, e.getMessage(), e);
        }
    }

    /**
     * Edits an existing calendar message to display a different month.
     * Falls back to sending a new message via {@link #safeEdit} if the edit fails.
     */
    public void editCalendar(Long chatId, Integer messageId, int year, int month) {
        final InlineKeyboardMarkup keyboard = calendarKeyboardBuilder.buildCalendar(year, month);

        final EditMessageText edit = new EditMessageText();
        edit.setChatId(chatId.toString());
        edit.setMessageId(messageId);
        edit.setText("Select a date:");
        edit.setReplyMarkup(keyboard);

        final SendMessage fallback = new SendMessage(chatId.toString(), "Select a date:");
        fallback.setReplyMarkup(keyboard);

        safeEdit(edit, fallback);
    }

    /**
     * Sends a delete-confirmation message with [✅ Yes, delete] and [❌ Cancel] inline buttons.
     */
    public void sendDeleteConfirmation(Long chatId, Long taskId, String taskText) {
        final String preview = taskText.length() > 80 ? taskText.substring(0, 80) + "…" : taskText;
        final String text = "Delete task?\n\n\"" + preview + "\"";

        final InlineKeyboardButton yesBtn = new InlineKeyboardButton("✅ Yes, delete");
        yesBtn.setCallbackData(BotConstants.CB_CONFIRM_DELETE + taskId);

        final InlineKeyboardButton cancelBtn = new InlineKeyboardButton("❌ Cancel");
        cancelBtn.setCallbackData(BotConstants.CB_CONFIRM_CANCEL);

        final InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(List.of(yesBtn, cancelBtn)));

        final SendMessage message = new SendMessage(chatId.toString(), text);
        message.setReplyMarkup(markup);

        try {
            sender.execute(message);
        } catch (TelegramApiException e) {
            log.error("Failed to send delete confirmation to chatId={}: {}", chatId, e.getMessage(), e);
        }
    }

    /**
     * Attempts to edit an existing message in-place.
     * Falls back to sending a new message if the Telegram edit API rejects the request
     * (e.g. message is older than 48 hours).
     */
    private void safeEdit(EditMessageText editRequest, SendMessage fallback) {
        try {
            sender.execute(editRequest);
        } catch (TelegramApiException e) {
            log.warn("EditMessageText failed (chatId={}, messageId={}), falling back to sendMessage: {}",
                    editRequest.getChatId(), editRequest.getMessageId(), e.getMessage());

            try {
                sender.execute(fallback);
            } catch (TelegramApiException fallbackEx) {
                log.error("Fallback sendMessage also failed for chatId={}: {}",
                        fallback.getChatId(), fallbackEx.getMessage(), fallbackEx);
            }
        }
    }

    private String buildTaskListText(List<TaskDto> tasks, TaskStatus tab, boolean truncated) {
        if (tasks.isEmpty()) {
            return tab == TaskStatus.ACTIVE
                    ? "📋 No active tasks yet. Tap \"📝 New Task\" to create one."
                    : "✅ No completed tasks yet.";
        }

        // Task titles and action buttons are rendered in the inline keyboard below this header.
        final String header = tab == TaskStatus.ACTIVE ? "📋 Active tasks" : "✅ Completed tasks";
        String text = header + " (" + tasks.size() + "):";

        if (truncated) {
            text += "\nShowing first " + MAX_TASK_LIST_SIZE + " tasks…";
        }

        return text;
    }

    private ReplyKeyboardMarkup buildPersistentMenuKeyboard() {
        KeyboardRow row = new KeyboardRow();
        row.add(new KeyboardButton(BotConstants.BTN_NEW_TASK));
        row.add(new KeyboardButton(BotConstants.BTN_MY_TASKS));

        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setKeyboard(List.of(row));
        markup.setResizeKeyboard(true);
        markup.setIsPersistent(true);

        return markup;
    }

    private InlineKeyboardMarkup buildTimezoneKeyboard() {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (List<String> rowTimezones : BotConstants.TIMEZONE_ROWS) {
            List<InlineKeyboardButton> row = new ArrayList<>();

            for (String tz : rowTimezones) {
                final String displayName = BotConstants.TIMEZONE_DISPLAY_NAMES.getOrDefault(tz, tz);
                final String currentTime = ZonedDateTime.now(ZoneId.of(tz)).format(TZ_TIME_FORMATTER);
                final String label = displayName + " (now " + currentTime + ")";
                final InlineKeyboardButton keyboardButton = new InlineKeyboardButton(label);
                keyboardButton.setCallbackData(BotConstants.TZ_CALLBACK_PREFIX + tz);
                row.add(keyboardButton);
            }

            rows.add(row);
        }

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(rows);

        return markup;
    }
}
