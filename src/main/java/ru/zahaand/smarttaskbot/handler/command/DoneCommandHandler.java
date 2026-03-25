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

import java.util.NoSuchElementException;

/**
 * Handles the {@code /done} command.
 * Parses the task ID, delegates completion to {@link TaskService#completeTask},
 * and replies with a confirmation or error.
 */
@Component
@RequiredArgsConstructor
public class DoneCommandHandler {

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

        if (argsText.isBlank()) {
            notificationService.sendMessage(chatId, messageService.get(MessageKey.DONE_USAGE_HINT, user));
            return;
        }

        final Long taskId = getTaskId(argsText, chatId, user);
        if (taskId == null) {
            return;
        }

        try {
            final TaskDto task = taskService.completeTask(telegramUserId, taskId);
            notificationService.sendMessage(chatId,
                    messageService.get(MessageKey.TASK_COMPLETED, user)
                            + "\n#" + task.getId() + " " + task.getText());
        } catch (NoSuchElementException e) {
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

    private Long getTaskId(String argsText, Long chatId, User user) {
        try {
            return Long.parseLong(argsText.split("\\s+")[0]);
        } catch (NumberFormatException e) {
            notificationService.sendMessage(chatId, messageService.get(MessageKey.DONE_USAGE_HINT, user));
            return null;
        }
    }
}
