package ru.zahaand.smarttaskbot.handler.text;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.zahaand.smarttaskbot.model.ConversationState;
import ru.zahaand.smarttaskbot.model.MessageKey;
import ru.zahaand.smarttaskbot.model.User;
import ru.zahaand.smarttaskbot.service.MessageService;
import ru.zahaand.smarttaskbot.service.NotificationService;
import ru.zahaand.smarttaskbot.service.UserService;
import ru.zahaand.smarttaskbot.service.UserStateService;

/**
 * Handles the "New Task" persistent menu button tap.
 * Transitions the user to CREATING_TASK state and prompts for task text.
 * <p>
 * Обрабатывает нажатие кнопки «Новая задача» в постоянном меню.
 * Переводит пользователя в состояние CREATING_TASK и запрашивает текст задачи.
 */
@Component
@RequiredArgsConstructor
public class NewTaskButtonHandler {

    private final UserStateService userStateService;
    private final NotificationService notificationService;
    private final UserService userService;
    private final MessageService messageService;

    public void handle(Update update) {
        final Long userId = update.getMessage().getFrom().getId();
        final Long chatId = update.getMessage().getChatId();
        final User user = userService.findById(userId);

        userStateService.setState(userId, ConversationState.CREATING_TASK);
        notificationService.sendMessage(chatId, messageService.get(MessageKey.ENTER_TASK_DESCRIPTION, user));
    }
}
