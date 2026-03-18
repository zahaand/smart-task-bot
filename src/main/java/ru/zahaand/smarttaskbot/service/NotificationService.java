package ru.zahaand.smarttaskbot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
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
