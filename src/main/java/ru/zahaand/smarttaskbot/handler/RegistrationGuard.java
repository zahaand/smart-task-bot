package ru.zahaand.smarttaskbot.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.zahaand.smarttaskbot.model.User;
import ru.zahaand.smarttaskbot.service.NotificationService;
import ru.zahaand.smarttaskbot.service.UserService;

/**
 * Guards all bot commands that require a fully registered user.
 * Three-state routing:
 * <ol>
 *   <li>No User row → sendLanguageKeyboard (restart onboarding)</li>
 *   <li>Language IS NULL → sendLanguageKeyboard (step 1 pending)</li>
 *   <li>Timezone IS NULL → sendTimezoneKeyboard in user's language (step 2 pending)</li>
 *   <li>Fully registered → run the command action</li>
 * </ol>
 *
 * Охраняет все команды, требующие полной регистрации пользователя.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RegistrationGuard {

    private final UserService userService;
    private final NotificationService notificationService;

    public void checkAndRoute(Update update, Runnable commandAction) {
        final Long telegramUserId = update.getMessage().getFrom().getId();
        final Long chatId = update.getMessage().getChatId();

        if (!userService.userExists(telegramUserId) || userService.isLanguagePending(telegramUserId)) {
            log.warn("Registration guard: language step pending for userId={}", telegramUserId);
            notificationService.sendLanguageKeyboard(chatId);
            return;
        }

        if (userService.isTimezonePending(telegramUserId)) {
            final User user = userService.findById(telegramUserId);
            log.warn("Registration guard: timezone step pending for userId={}", telegramUserId);
            notificationService.sendTimezoneKeyboard(chatId, user.getLanguage());
            return;
        }

        commandAction.run();
    }
}
