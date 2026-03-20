package ru.zahaand.smarttaskbot.handler.command;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.zahaand.smarttaskbot.service.NotificationService;
import ru.zahaand.smarttaskbot.service.UserService;

@Slf4j
@Component
@RequiredArgsConstructor
public class StartCommandHandler {

    private final UserService userService;
    private final NotificationService notificationService;

    public void handle(Update update) {
        Long telegramUserId = update.getMessage().getFrom().getId();
        Long chatId = update.getMessage().getChatId();

        if (userService.isRegistered(telegramUserId)) {
            log.info("Returning user greeted: userId={}", telegramUserId);
            notificationService.sendPersistentMenu(chatId, "Welcome back! Use the buttons below.");
        } else {
            notificationService.sendTimezoneKeyboard(chatId,
                    "Welcome to Smart Task Bot!\nPlease select your timezone:");
        }
    }
}
