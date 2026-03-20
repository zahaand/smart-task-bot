package ru.zahaand.smarttaskbot.handler.text;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.zahaand.smarttaskbot.model.ConversationState;
import ru.zahaand.smarttaskbot.service.NotificationService;
import ru.zahaand.smarttaskbot.service.UserStateService;

/**
 * Handles the "📝 New Task" persistent menu button tap.
 * Transitions the user to CREATING_TASK state and prompts for task text.
 */
@Component
@RequiredArgsConstructor
public class NewTaskButtonHandler {

    private final UserStateService userStateService;
    private final NotificationService notificationService;

    public void handle(Update update) {
        final Long userId = update.getMessage().getFrom().getId();
        final Long chatId = update.getMessage().getChatId();

        userStateService.setState(userId, ConversationState.CREATING_TASK);
        notificationService.sendMessage(chatId, "Enter task text:");
    }
}
