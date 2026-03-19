package ru.zahaand.smarttaskbot.handler.command;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.zahaand.smarttaskbot.dto.TaskDto;
import ru.zahaand.smarttaskbot.service.NotificationService;
import ru.zahaand.smarttaskbot.service.TaskService;

/**
 * Handles the {@code /newtask} command.
 * Extracts task text from the message, delegates creation to
 * {@link TaskService}, and replies with the assigned task ID.
 */
@Component
@RequiredArgsConstructor
public class NewTaskCommandHandler {

    private final TaskService taskService;
    private final NotificationService notificationService;

    public void handle(Update update) {
        Long chatId = update.getMessage().getChatId();
        Long telegramUserId = update.getMessage().getFrom().getId();
        String messageText = update.getMessage().getText();

        // Extract text after "/newtask" (with possible @botname suffix)
        String taskText = extractTaskText(messageText);

        try {
            TaskDto task = taskService.createTask(telegramUserId, taskText);
            notificationService.sendMessage(chatId,
                    "Task created ✓\n#" + task.getId() + ": " + task.getText());
        } catch (IllegalArgumentException e) {
            notificationService.sendMessage(chatId, e.getMessage());
        }
    }

    private String extractTaskText(String messageText) {
        // messageText starts with "/newtask" or "/newtask@botname"
        int spaceIndex = messageText.indexOf(' ');
        if (spaceIndex == -1) {
            return "";
        }
        return messageText.substring(spaceIndex + 1).trim();
    }
}
