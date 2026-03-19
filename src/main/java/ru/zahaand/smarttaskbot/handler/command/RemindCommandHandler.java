package ru.zahaand.smarttaskbot.handler.command;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.zahaand.smarttaskbot.dto.TaskDto;
import ru.zahaand.smarttaskbot.service.NotificationService;
import ru.zahaand.smarttaskbot.service.TaskService;
import ru.zahaand.smarttaskbot.service.UserService;

import java.time.format.DateTimeParseException;
import java.util.NoSuchElementException;

/**
 * Handles the {@code /remind} command.
 * Parses task ID and datetime from the message, delegates to {@link TaskService#setReminder},
 * and replies with a confirmation or a specific error message.
 */
@Component
@RequiredArgsConstructor
public class RemindCommandHandler {

    private final TaskService taskService;
    private final NotificationService notificationService;
    private final UserService userService;

    private static final String USAGE_HINT =
            "Usage: /remind <id> DD.MM.YYYY HH:mm\nExample: /remind 42 25.03.2026 09:00";

    private static final String FORMAT_ERROR =
            "Invalid date format.\n" + USAGE_HINT;

    public void handle(Update update) {
        Message message = update.getMessage();
        Long chatId = message.getChatId();
        Long telegramUserId = message.getFrom().getId();
        String messageText = message.getText();

        String argsText = extractArgs(messageText);
        String[] parts = argsText.split("\\s+");

        // Expect exactly: <taskId> <date> <time>  →  3 parts
        if (argsText.isBlank() || parts.length < 3) {
            notificationService.sendMessage(chatId, USAGE_HINT);
            return;
        }

        Long taskId = getTaskId(parts[0], chatId);
        if (taskId == null) {
            return;
        }

        // Recombine date + time: "25.03.2026 09:00"
        String dateTimeInput = "%s %s".formatted(parts[1], parts[2]);

        try {
            TaskDto dto = taskService.setReminder(telegramUserId, taskId, dateTimeInput);

            String timezone = userService.getTimezone(telegramUserId);

            notificationService.sendMessage(chatId,
                    "Reminder set ✓\n#" + dto.getId() + " " + dto.getText()
                            + " — " + dto.getReminderTime() + " (" + timezone + ")");

        } catch (DateTimeParseException e) {
            notificationService.sendMessage(chatId, FORMAT_ERROR);
        } catch (NoSuchElementException | IllegalArgumentException e) {
            notificationService.sendMessage(chatId, e.getMessage());
        }
    }

    private String extractArgs(String messageText) {
        int spaceIndex = messageText.indexOf(' ');
        if (spaceIndex == -1) {
            return "";
        }

        return messageText.substring(spaceIndex + 1).trim();
    }

    private Long getTaskId(String part, Long chatId) {
        try {
            return Long.parseLong(part);
        } catch (NumberFormatException e) {
            notificationService.sendMessage(chatId, USAGE_HINT);
            return null;
        }
    }
}
