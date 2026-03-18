package ru.zahaand.smarttaskbot.handler.command;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.zahaand.smarttaskbot.model.Task;
import ru.zahaand.smarttaskbot.service.NotificationService;
import ru.zahaand.smarttaskbot.service.TaskService;

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

    private static final String USAGE_HINT =
            "Please provide a task ID.\nUsage: /done <task_id>";

    public void handle(Update update) {
        Message message = update.getMessage();
        Long chatId = message.getChatId();
        Long telegramUserId = message.getFrom().getId();
        String messageText = message.getText();

        String argsText = extractArgs(messageText);

        if (argsText.isBlank()) {
            notificationService.sendMessage(chatId, USAGE_HINT);
            return;
        }

        Long taskId = getTaskId(argsText, chatId);
        if (taskId == null) {
            return;
        }

        try {
            Task task = taskService.completeTask(telegramUserId, taskId);

            notificationService.sendMessage(chatId,
                    "Task completed ✓\n#" + task.getId() + " " + task.getText());

        } catch (NoSuchElementException e) {
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

    private Long getTaskId(String argsText, Long chatId) {
        long taskId;

        try {
            taskId = Long.parseLong(argsText.split("\\s+")[0]);
        } catch (NumberFormatException e) {
            notificationService.sendMessage(chatId, USAGE_HINT);
            return null;
        }

        return taskId;
    }
}
