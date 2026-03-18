package ru.zahaand.smarttaskbot.handler;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.zahaand.smarttaskbot.service.NotificationService;
import ru.zahaand.smarttaskbot.service.UserService;

@Component
@RequiredArgsConstructor
public class RegistrationGuard {

    private final UserService userService;
    private final NotificationService notificationService;

    public void checkAndRoute(Update update, Runnable commandAction) {
        Long telegramUserId = update.getMessage().getFrom().getId();
        Long chatId = update.getMessage().getChatId();

        if (!userService.isRegistered(telegramUserId)) {
            notificationService.sendTimezoneKeyboard(chatId);
            return;
        }

        commandAction.run();
    }
}
