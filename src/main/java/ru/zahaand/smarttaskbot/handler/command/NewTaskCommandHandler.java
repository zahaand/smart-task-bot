package ru.zahaand.smarttaskbot.handler.command;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.zahaand.smarttaskbot.dto.TaskDto;
import ru.zahaand.smarttaskbot.model.MessageKey;
import ru.zahaand.smarttaskbot.model.User;
import ru.zahaand.smarttaskbot.service.MessageService;
import ru.zahaand.smarttaskbot.service.NotificationService;
import ru.zahaand.smarttaskbot.service.TaskService;
import ru.zahaand.smarttaskbot.service.UserService;

/**
 * Handles the {@code /newtask} command.
 */
@Component
@RequiredArgsConstructor
public class NewTaskCommandHandler {

    private final TaskService taskService;
    private final NotificationService notificationService;
    private final UserService userService;
    private final MessageService messageService;

    public void handle(Update update) {
        final Long chatId = update.getMessage().getChatId();
        final Long telegramUserId = update.getMessage().getFrom().getId();
        final String messageText = update.getMessage().getText();
        final User user = userService.findById(telegramUserId);

        final String taskText = extractTaskText(messageText);

        try {
            final TaskDto task = taskService.createTask(telegramUserId, taskText);
            notificationService.sendMessage(chatId,
                    messageService.get(MessageKey.TASK_CREATED, user)
                            + "\n#" + task.getId() + ": " + task.getText());
        } catch (IllegalArgumentException e) {
            notificationService.sendMessage(chatId, e.getMessage());
        }
    }

    private String extractTaskText(String messageText) {
        final int spaceIndex = messageText.indexOf(' ');
        if (spaceIndex == -1) {
            return "";
        }
        return messageText.substring(spaceIndex + 1).trim();
    }
}
