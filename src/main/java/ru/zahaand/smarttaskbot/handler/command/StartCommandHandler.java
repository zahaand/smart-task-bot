package ru.zahaand.smarttaskbot.handler.command;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.zahaand.smarttaskbot.service.NotificationService;
import ru.zahaand.smarttaskbot.service.UserService;

@Component
@RequiredArgsConstructor
public class StartCommandHandler {

    private final UserService userService;
    private final NotificationService notificationService;

    public void handle(Update update) {
        Long telegramUserId = update.getMessage().getFrom().getId();
        Long chatId = update.getMessage().getChatId();

        if (userService.isRegistered(telegramUserId)) {
            notificationService.sendMessage(chatId,
                    "Welcome back! You are already registered.\nUse /help to see available commands.");
        } else {
            notificationService.sendTimezoneKeyboard(chatId,
                    "Welcome to Smart Task Bot!\nPlease select your timezone:");
        }
    }
}
