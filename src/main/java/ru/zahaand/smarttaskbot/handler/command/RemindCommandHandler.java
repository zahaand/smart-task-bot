package ru.zahaand.smarttaskbot.handler.command;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.zahaand.smarttaskbot.dto.TaskDto;
import ru.zahaand.smarttaskbot.model.BotException;
import ru.zahaand.smarttaskbot.model.MessageKey;
import ru.zahaand.smarttaskbot.model.User;
import ru.zahaand.smarttaskbot.service.MessageService;
import ru.zahaand.smarttaskbot.service.NotificationService;
import ru.zahaand.smarttaskbot.service.TaskService;
import ru.zahaand.smarttaskbot.service.UserService;

import java.time.format.DateTimeParseException;

/**
 * Handles the {@code /remind} command.
 * Parses the task ID and datetime, delegates to {@link TaskService#setReminder},
 * and replies with a confirmation or error.
 * <p>
 * Обрабатывает команду {@code /remind}.
 * Парсит ID задачи и дату/время, делегирует в {@link TaskService#setReminder}
 * и отвечает подтверждением или ошибкой.
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

        if (StringUtils.isBlank(argsText) || parts.length < 3) {
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
            final String reminderDisplay = dto.getReminderTime() + " (" + timezone + ")";
            final String msg = messageService.get(MessageKey.REMINDER_SET, user)
                    .formatted(dto.getId(), dto.getText(), reminderDisplay);
            notificationService.sendMessage(chatId, msg);
        } catch (DateTimeParseException e) {
            notificationService.sendMessage(chatId, messageService.get(MessageKey.REMIND_FORMAT_ERROR, user));
        } catch (BotException e) {
            final String msg = messageService.get(e.getMessageKey(), user);
            notificationService.sendMessage(chatId, e.getArgs().length > 0 ? msg.formatted(e.getArgs()) : msg);
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
