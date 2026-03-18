package ru.zahaand.smarttaskbot.handler;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.zahaand.smarttaskbot.service.NotificationService;
import ru.zahaand.smarttaskbot.service.UserService;

/**
 * Security component that ensures the user is registered before accessing
 * protected bot commands. If the user is not found in the database,
 * redirects them to the timezone selection process.
 */
@Component
@RequiredArgsConstructor
public class RegistrationGuard {

    private final UserService userService;
    private final NotificationService notificationService;

    public void checkAndRoute(Update update, Runnable commandAction) {
        Long telegramUserId = update.getMessage().getFrom().getId();
        Long chatId = update.getMessage().getChatId();

        if (!userService.isRegistered(telegramUserId)) {
            notificationService.sendTimezoneKeyboard(chatId, "Please select your timezone first:");
            return;
        }

        commandAction.run();
    }
}
