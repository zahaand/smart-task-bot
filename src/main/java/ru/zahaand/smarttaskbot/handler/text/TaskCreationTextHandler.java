package ru.zahaand.smarttaskbot.handler.text;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.zahaand.smarttaskbot.dto.TaskDto;
import ru.zahaand.smarttaskbot.model.ConversationState;
import ru.zahaand.smarttaskbot.model.MessageKey;
import ru.zahaand.smarttaskbot.model.User;
import ru.zahaand.smarttaskbot.service.*;

/**
 * Handles free-text input while the user is in CREATING_TASK state.
 */
@Component
@RequiredArgsConstructor
public class TaskCreationTextHandler {

    private final TaskService taskService;
    private final UserStateService userStateService;
    private final NotificationService notificationService;
    private final UserService userService;
    private final MessageService messageService;

    public void handle(Update update) {
        final Long userId = update.getMessage().getFrom().getId();
        final Long chatId = update.getMessage().getChatId();
        final String text = update.getMessage().getText().trim();
        final User user = userService.findById(userId);

        if (StringUtils.isBlank(text)) {
            notificationService.sendMessage(chatId, messageService.get(MessageKey.TASK_TEXT_EMPTY, user));
            return;
        }

        final TaskDto created = taskService.createTask(userId, text);
        notificationService.sendTaskCreatedWithActions(chatId, created.getId(), created.getText(), user.getLanguage());
        userStateService.setState(userId, ConversationState.IDLE);
    }
}
