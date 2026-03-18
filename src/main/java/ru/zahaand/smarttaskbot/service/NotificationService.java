package ru.zahaand.smarttaskbot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.zahaand.smarttaskbot.model.Task;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class NotificationService {

    private static final List<List<String>> TIMEZONE_ROWS = List.of(
            List.of("Europe/Kaliningrad", "Europe/Moscow"),
            List.of("Asia/Yekaterinburg", "Asia/Novosibirsk"),
            List.of("Asia/Vladivostok")
    );

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

    public void sendTimezoneKeyboard(Long chatId) {
        SendMessage message = new SendMessage(chatId.toString(), "Please select your timezone:");

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

        for (List<String> rowTimezones : TIMEZONE_ROWS) {
            List<InlineKeyboardButton> row = new ArrayList<>();

            for (String tz : rowTimezones) {
                InlineKeyboardButton keyboardButton = new InlineKeyboardButton(tz);
                keyboardButton.setCallbackData("tz:" + tz);
                row.add(keyboardButton);
            }

            rows.add(row);
        }

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(rows);

        return markup;
    }
}
