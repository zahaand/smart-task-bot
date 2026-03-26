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
 * Handles the {@code /help} command.
 * Sends a localised help message listing all available bot commands.
 * <p>
 * Обрабатывает команду {@code /help}.
 * Отправляет локализованное сообщение со списком всех доступных команд бота.
 */
@Component
@RequiredArgsConstructor
public class HelpCommandHandler {

    private final NotificationService notificationService;
    private final UserService userService;
    private final MessageService messageService;

    public void handle(Update update) {
        final Long chatId = update.getMessage().getChatId();
        final Long userId = update.getMessage().getFrom().getId();
        final User user = userService.findById(userId);
        notificationService.sendMessage(chatId, messageService.get(MessageKey.HELP_TEXT, user));
    }
}
