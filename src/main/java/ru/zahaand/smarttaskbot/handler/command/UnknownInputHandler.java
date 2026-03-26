package ru.zahaand.smarttaskbot.handler.command;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.zahaand.smarttaskbot.model.MessageKey;
import ru.zahaand.smarttaskbot.model.User;
import ru.zahaand.smarttaskbot.service.MessageService;
import ru.zahaand.smarttaskbot.service.NotificationService;
import ru.zahaand.smarttaskbot.service.UserService;

/**
 * Fallback handler for unrecognised text input from registered users.
 * Sends a localised "please use buttons" message.
 * <p>
 * Обработчик нераспознанного текстового ввода от зарегистрированных пользователей.
 * Отправляет локализованное сообщение «используйте кнопки».
 */
@Component
@RequiredArgsConstructor
public class UnknownInputHandler {

    private final NotificationService notificationService;
    private final UserService userService;
    private final MessageService messageService;

    public void handle(Update update) {
        final Long chatId = update.getMessage().getChatId();
        final Long userId = update.getMessage().getFrom().getId();
        final User user = userService.findById(userId);
        notificationService.sendMessage(chatId, messageService.get(MessageKey.USE_BUTTONS, user));
    }
}
