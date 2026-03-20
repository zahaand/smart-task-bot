package ru.zahaand.smarttaskbot.handler.command;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.zahaand.smarttaskbot.service.NotificationService;

@Component
@RequiredArgsConstructor
public class UnknownInputHandler {

    private final NotificationService notificationService;

    public void handle(Update update) {
        Long chatId = update.getMessage().getChatId();
        notificationService.sendMessage(chatId, "Use the menu buttons below or /help to see available commands.");
    }
}
