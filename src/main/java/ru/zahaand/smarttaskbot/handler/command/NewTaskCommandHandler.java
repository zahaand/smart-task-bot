package ru.zahaand.smarttaskbot.handler.command;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.zahaand.smarttaskbot.dto.TaskDto;
import ru.zahaand.smarttaskbot.model.BotException;
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
            notificationService.sendTaskCreatedWithActions(chatId, task.getId(), task.getText(), user.getLanguage());
        } catch (BotException e) {
            final String msg = messageService.get(e.getMessageKey(), user);
            notificationService.sendMessage(chatId, e.getArgs().length > 0 ? msg.formatted(e.getArgs()) : msg);
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
