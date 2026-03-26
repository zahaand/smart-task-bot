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
import ru.zahaand.smarttaskbot.model.*;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Service responsible for sending all outgoing communications to Telegram users.
 * Provides methods for sending plain text messages, interactive keyboards,
 * and scheduled task reminders.
 * <p>
 * Сервис отправки всех исходящих сообщений пользователям Telegram.
 * Предоставляет методы для текстовых сообщений, интерактивных клавиатур и напоминаний.
 */
@Slf4j
@Service
public class NotificationService {

    private final AbsSender sender;
    private final TaskListKeyboardBuilder taskListKeyboardBuilder;
    private final CalendarKeyboardBuilder calendarKeyboardBuilder;
    private final MessageService messageService;

    private static final DateTimeFormatter TZ_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private static final int MAX_TASK_LIST_SIZE = 20;

    public NotificationService(@Lazy AbsSender sender,
                               TaskListKeyboardBuilder taskListKeyboardBuilder,
                               CalendarKeyboardBuilder calendarKeyboardBuilder,
                               MessageService messageService) {
        this.sender = sender;
        this.taskListKeyboardBuilder = taskListKeyboardBuilder;
        this.calendarKeyboardBuilder = calendarKeyboardBuilder;
        this.messageService = messageService;
    }

    public void sendMessage(Long chatId, String text) {
        SendMessage message = new SendMessage(chatId.toString(), text);

        try {
            sender.execute(message);
        } catch (TelegramApiException e) {
            log.error("Failed to send message to chatId={}: {}", chatId, e.getMessage(), e);
        }
    }

    /**
     * Sends the bilingual welcome message with [🇬🇧 English] / [🇷🇺 Русский] inline buttons.
     * Always uses WELCOME_BILINGUAL (same in both languages by design).
     * <p>
     * Отправляет двуязычное приветствие с кнопками выбора языка.
     */
    public void sendLanguageKeyboard(Long chatId) {
        final String text = messageService.get(MessageKey.WELCOME_BILINGUAL, Language.EN);
        final SendMessage message = new SendMessage(chatId.toString(), text);

        final InlineKeyboardButton enBtn = new InlineKeyboardButton(
                messageService.get(MessageKey.BTN_LANG_EN, Language.EN));
        enBtn.setCallbackData(BotConstants.CB_LANG_EN);

        final InlineKeyboardButton ruBtn = new InlineKeyboardButton(
                messageService.get(MessageKey.BTN_LANG_RU, Language.EN));
        ruBtn.setCallbackData(BotConstants.CB_LANG_RU);

        final InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(List.of(enBtn, ruBtn)));
        message.setReplyMarkup(markup);

        try {
            sender.execute(message);
        } catch (TelegramApiException e) {
            log.error("Failed to send language keyboard to chatId={}: {}", chatId, e.getMessage(), e);
        }
    }

    /**
     * Sends the timezone selection keyboard with a language-aware prompt and localized city codes.
     * <p>
     * Отправляет клавиатуру выбора часового пояса с текстом и кодами городов на языке пользователя.
     */
    public void sendTimezoneKeyboard(Long chatId, Language language) {
        final String text = messageService.get(MessageKey.SELECT_TIMEZONE, language);
        final SendMessage message = new SendMessage(chatId.toString(), text);
        message.setReplyMarkup(buildTimezoneKeyboard(language));
        try {
            sender.execute(message);
        } catch (TelegramApiException e) {
            log.error("Failed to send timezone keyboard to chatId={}: {}", chatId, e.getMessage(), e);
        }
    }

    /**
     * Sends a message with the persistent reply keyboard, using language-aware button labels.
     * <p>
     * Отправляет сообщение с постоянной клавиатурой с кнопками на языке пользователя.
     */
    public void sendPersistentMenu(Long chatId, String text, Language language) {
        final SendMessage message = new SendMessage(chatId.toString(), text);
        message.setReplyMarkup(buildPersistentMenuKeyboard(language));

        try {
            sender.execute(message);
        } catch (TelegramApiException e) {
            log.error("Failed to send persistent menu to chatId={}: {}", chatId, e.getMessage(), e);
        }
    }

    public void sendReminder(Task task) {
        final User user = task.getUser();
        final String text = messageService.get(MessageKey.REMINDER_NOTIFICATION, user.getLanguage())
                .formatted(task.getText());
        sendMessage(user.getTelegramUserId(), text);
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
     * Sends a new task-list message with inline action buttons and a tab row.
     * Truncates to {@link #MAX_TASK_LIST_SIZE} tasks and appends a note when the list overflows.
     * Button labels are rendered in the given language (null falls back to EN).
     * <p>
     * Отправляет новое сообщение со списком задач и инлайн-кнопками.
     * Усекает до {@link #MAX_TASK_LIST_SIZE} задач; подписи кнопок на языке пользователя.
     */
    public void sendTaskList(Long chatId, List<TaskDto> tasks, TaskStatus tab, Language language) {
        final boolean truncated = tasks.size() > MAX_TASK_LIST_SIZE;
        final List<TaskDto> visible = truncated ? tasks.subList(0, MAX_TASK_LIST_SIZE) : tasks;

        final String text = buildTaskListText(visible, tab, truncated, language);
        final InlineKeyboardMarkup keyboard = taskListKeyboardBuilder.buildKeyboard(visible, tab, language);

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
     * Button labels are rendered in the given language (null falls back to EN).
     * <p>
     * Редактирует существующее сообщение со списком задач на месте; при неудаче отправляет новое.
     * Подписи кнопок на языке пользователя.
     */
    public void editTaskList(Long chatId, Integer messageId, List<TaskDto> tasks, TaskStatus tab, Language language) {
        final boolean truncated = tasks.size() > MAX_TASK_LIST_SIZE;
        final List<TaskDto> visible = truncated ? tasks.subList(0, MAX_TASK_LIST_SIZE) : tasks;

        final String text = buildTaskListText(visible, tab, truncated, language);
        final InlineKeyboardMarkup keyboard = taskListKeyboardBuilder.buildKeyboard(visible, tab, language);

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
    public void sendCalendar(Long chatId, int year, int month, Language language) {
        final InlineKeyboardMarkup keyboard = calendarKeyboardBuilder.buildCalendar(year, month);
        final String text = messageService.get(MessageKey.CHOOSE_REMINDER_DATE, language);
        final SendMessage message = new SendMessage(chatId.toString(), text);
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
    public void editCalendar(Long chatId, Integer messageId, int year, int month, Language language) {
        final InlineKeyboardMarkup keyboard = calendarKeyboardBuilder.buildCalendar(year, month);
        final String text = messageService.get(MessageKey.CHOOSE_REMINDER_DATE, language);

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
     * Sends a delete-confirmation message with [✅ Yes, delete] and [❌ Cancel] inline buttons.
     */
    public void sendDeleteConfirmation(Long chatId, Long taskId, String taskText, Language language) {
        final String preview = taskText.length() > 80 ? taskText.substring(0, 80) + "…" : taskText;
        final String text = messageService.get(MessageKey.TASK_DELETE_CONFIRM, language)
                .formatted(taskId, preview);

        final InlineKeyboardButton yesBtn = new InlineKeyboardButton(
                messageService.get(MessageKey.BTN_YES_DELETE, language));
        yesBtn.setCallbackData(BotConstants.CB_CONFIRM_DELETE + taskId);

        final InlineKeyboardButton cancelBtn = new InlineKeyboardButton(
                messageService.get(MessageKey.BTN_CANCEL, language));
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
     * Sends a delete-all-confirmation message with [✅ Yes, delete all] and [❌ Cancel] inline buttons.
     *
     * @param chatId    target chat
     * @param text      pre-formatted confirmation prompt (e.g. "Delete all 3 completed tasks?")
     * @param language  user's language for button labels
     */
    public void sendDeleteAllConfirmation(Long chatId, String text, Language language) {
        final InlineKeyboardButton yesBtn = new InlineKeyboardButton(
                messageService.get(MessageKey.BTN_YES_DELETE_ALL, language));
        yesBtn.setCallbackData(BotConstants.CB_DELETE_ALL_CONFIRM);

        final InlineKeyboardButton cancelBtn = new InlineKeyboardButton(
                messageService.get(MessageKey.BTN_CANCEL, language));
        cancelBtn.setCallbackData(BotConstants.CB_DELETE_ALL_CANCEL);

        final InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(List.of(yesBtn, cancelBtn)));

        final SendMessage message = new SendMessage(chatId.toString(), text);
        message.setReplyMarkup(markup);

        try {
            sender.execute(message);
        } catch (TelegramApiException e) {
            log.error("Failed to send delete-all confirmation to chatId={}: {}", chatId, e.getMessage(), e);
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

    private String buildTaskListText(List<TaskDto> tasks, TaskStatus tab, boolean truncated, Language language) {
        if (tasks.isEmpty()) {
            return tab == TaskStatus.ACTIVE
                    ? messageService.get(MessageKey.TASKS_EMPTY_ACTIVE, language)
                    : messageService.get(MessageKey.TASKS_EMPTY_COMPLETED, language);
        }

        // Task titles and action buttons are rendered in the inline keyboard below this header.
        final String header = tab == TaskStatus.ACTIVE
                ? messageService.get(MessageKey.TASKS_ACTIVE_LIST_HEADER, language)
                : messageService.get(MessageKey.TASKS_COMPLETED_HEADER, language);
        String text = header + " (" + tasks.size() + "):";

        if (truncated) {
            text += "\n" + messageService.get(MessageKey.TASKS_TRUNCATED, language)
                    .formatted(MAX_TASK_LIST_SIZE);
        }

        return text;
    }

    private ReplyKeyboardMarkup buildPersistentMenuKeyboard(Language language) {
        final KeyboardRow row = new KeyboardRow();
        row.add(new KeyboardButton(messageService.get(MessageKey.BTN_NEW_TASK, language)));
        row.add(new KeyboardButton(messageService.get(MessageKey.BTN_MY_TASKS, language)));

        final ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setKeyboard(List.of(row));
        markup.setResizeKeyboard(true);
        markup.setIsPersistent(true);

        return markup;
    }

    private InlineKeyboardMarkup buildTimezoneKeyboard(Language language) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (List<String> rowTimezones : BotConstants.TIMEZONE_ROWS) {
            List<InlineKeyboardButton> row = new ArrayList<>();

            for (String tz : rowTimezones) {
                final InlineKeyboardButton keyboardButton = new InlineKeyboardButton(buildTimezoneButtonLabel(tz, language));
                keyboardButton.setCallbackData(BotConstants.TZ_CALLBACK_PREFIX + tz);
                row.add(keyboardButton);
            }

            rows.add(row);
        }

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(rows);

        return markup;
    }

    /**
     * Computes a timezone button label as {@code "HH:mm CITY_CODES"} in the user's language
     * (e.g. {@code "10:00 MSK, SPB"} for EN or {@code "10:00 МСК, СПБ"} for RU).
     * Falls back to {@link BotConstants#TIMEZONE_DISPLAY_NAMES} when the zone rules cannot be loaded.
     * <p>
     * Вычисляет метку кнопки пояса «ЧЧ:мм КОД_ГОРОДА» на языке пользователя.
     * При ошибке загрузки правил зоны возвращает отображаемое имя из {@link BotConstants#TIMEZONE_DISPLAY_NAMES}.
     */
    private String buildTimezoneButtonLabel(String tz, Language language) {
        try {
            final String currentTime = ZonedDateTime.now(ZoneId.of(tz)).format(TZ_TIME_FORMATTER);
            final java.util.Map<String, String> codes = language == Language.RU
                    ? BotConstants.TIMEZONE_CITY_CODES_RU
                    : BotConstants.TIMEZONE_CITY_CODES;
            return currentTime + " " + codes.get(tz);
        } catch (java.time.zone.ZoneRulesException e) {
            log.warn("ZoneRulesException for tz='{}': {}", tz, e.getMessage());
            return BotConstants.TIMEZONE_DISPLAY_NAMES.getOrDefault(tz, tz);
        }
    }
}
