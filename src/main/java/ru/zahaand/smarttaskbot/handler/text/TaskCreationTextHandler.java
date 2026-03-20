package ru.zahaand.smarttaskbot.handler.text;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.zahaand.smarttaskbot.dto.TaskDto;
import ru.zahaand.smarttaskbot.model.ConversationState;
import ru.zahaand.smarttaskbot.service.NotificationService;
import ru.zahaand.smarttaskbot.service.TaskService;
import ru.zahaand.smarttaskbot.service.UserStateService;

/**
 * Handles free-text input while the user is in CREATING_TASK state.
 * Blank input stays in state with an error prompt; non-blank creates the task and returns to IDLE.
 */
@Component
@RequiredArgsConstructor
public class TaskCreationTextHandler {

    private final TaskService taskService;
    private final UserStateService userStateService;
    private final NotificationService notificationService;

    public void handle(Update update) {
        final Long userId = update.getMessage().getFrom().getId();
        final Long chatId = update.getMessage().getChatId();
        final String text = update.getMessage().getText().trim();

        if (text.isBlank()) {
            notificationService.sendMessage(chatId, "Task text cannot be empty.");
            return;
        }

        TaskDto created = taskService.createTask(userId, text);
        notificationService.sendMessage(chatId,
                "✅ Task #" + created.getId() + " created: " + created.getText());
        userStateService.setState(userId, ConversationState.IDLE);
    }
}
