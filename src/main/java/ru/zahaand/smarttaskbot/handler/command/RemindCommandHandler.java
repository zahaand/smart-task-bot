package ru.zahaand.smarttaskbot.handler.command;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.zahaand.smarttaskbot.dto.TaskDto;
import ru.zahaand.smarttaskbot.model.MessageKey;
import ru.zahaand.smarttaskbot.model.User;
import ru.zahaand.smarttaskbot.service.MessageService;
import ru.zahaand.smarttaskbot.service.NotificationService;
import ru.zahaand.smarttaskbot.service.TaskService;
import ru.zahaand.smarttaskbot.service.UserService;

import java.time.format.DateTimeParseException;
import java.util.NoSuchElementException;

/**
 * Handles the {@code /remind} command.
 */
@Component
@RequiredArgsConstructor
public class RemindCommandHandler {

    private final TaskService taskService;
    private final NotificationService notificationService;
    private final UserService userService;
    private final MessageService messageService;

    public void handle(Update update) {
        final Message message = update.getMessage();
        final Long chatId = message.getChatId();
        final Long telegramUserId = message.getFrom().getId();
        final String messageText = message.getText();
        final User user = userService.findById(telegramUserId);

        final String argsText = extractArgs(messageText);
        final String[] parts = argsText.split("\\s+");

        if (argsText.isBlank() || parts.length < 3) {
            notificationService.sendMessage(chatId, messageService.get(MessageKey.REMIND_USAGE_HINT, user));
            return;
        }

        final Long taskId = getTaskId(parts[0], chatId, user);
        if (taskId == null) {
            return;
        }

        final String dateTimeInput = "%s %s".formatted(parts[1], parts[2]);

        try {
            final TaskDto dto = taskService.setReminder(telegramUserId, taskId, dateTimeInput);
            final String timezone = userService.getTimezone(telegramUserId);
            notificationService.sendMessage(chatId,
                    "Reminder set ✓\n#" + dto.getId() + " " + dto.getText()
                            + " — " + dto.getReminderTime() + " (" + timezone + ")");
        } catch (DateTimeParseException e) {
            notificationService.sendMessage(chatId, messageService.get(MessageKey.REMIND_FORMAT_ERROR, user));
        } catch (NoSuchElementException | IllegalArgumentException e) {
            notificationService.sendMessage(chatId, e.getMessage());
        }
    }

    private String extractArgs(String messageText) {
        final int spaceIndex = messageText.indexOf(' ');
        if (spaceIndex == -1) {
            return "";
        }
        return messageText.substring(spaceIndex + 1).trim();
    }

    private Long getTaskId(String part, Long chatId, User user) {
        try {
            return Long.parseLong(part);
        } catch (NumberFormatException e) {
            notificationService.sendMessage(chatId, messageService.get(MessageKey.REMIND_USAGE_HINT, user));
            return null;
        }
    }
}
