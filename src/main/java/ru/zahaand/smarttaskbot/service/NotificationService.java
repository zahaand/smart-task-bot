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
import ru.zahaand.smarttaskbot.model.Task;

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

    public NotificationService(@Lazy AbsSender sender) {
        this.sender = sender;
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
     * Attempts to edit an existing message in-place.
     * Falls back to sending a new message if the Telegram edit API rejects the request
     * (e.g. message is older than 48 hours).
     */
    public void safeEdit(EditMessageText editRequest, SendMessage fallback) {
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

    private ReplyKeyboardMarkup buildPersistentMenuKeyboard() {
        KeyboardRow row = new KeyboardRow();
        row.add(new KeyboardButton(BotConstants.BTN_NEW_TASK));
        row.add(new KeyboardButton(BotConstants.BTN_MY_TASKS));
        row.add(new KeyboardButton(BotConstants.BTN_REMINDER));

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
                InlineKeyboardButton keyboardButton = new InlineKeyboardButton(tz);
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
