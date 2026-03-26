package ru.zahaand.smarttaskbot.handler.command;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.zahaand.smarttaskbot.handler.RegistrationGuard;
import ru.zahaand.smarttaskbot.model.MessageKey;
import ru.zahaand.smarttaskbot.model.User;
import ru.zahaand.smarttaskbot.service.MessageService;
import ru.zahaand.smarttaskbot.service.NotificationService;
import ru.zahaand.smarttaskbot.service.UserService;

/**
 * Handles the {@code /start} command with three-branch routing:
 * <ol>
 *   <li>Fully registered user → ALREADY_REGISTERED + persistent menu in user's language</li>
 *   <li>Brand-new user (no DB row) → createPartialUser + language keyboard</li>
 *   <li>Mid-registration user (row exists, incomplete) → RegistrationGuard re-prompts</li>
 * </ol>
 * <p>
 * Обрабатывает команду {@code /start} с тремя ветками маршрутизации:
 * <ol>
 *   <li>Зарегистрированный пользователь → ALREADY_REGISTERED + постоянное меню на языке пользователя</li>
 *   <li>Новый пользователь (нет строки в БД) → createPartialUser + клавиатура выбора языка</li>
 *   <li>Пользователь в процессе регистрации (строка есть, незавершена) → RegistrationGuard повторно запрашивает шаг</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StartCommandHandler {

    private final UserService userService;
    private final NotificationService notificationService;
    private final RegistrationGuard registrationGuard;
    private final MessageService messageService;

    /**
     * Processes the {@code /start} command using three-branch routing per the class Javadoc.
     * <p>
     * Обрабатывает команду {@code /start} по трёхветочной схеме маршрутизации.
     */
    public void handle(Update update) {
        final Long telegramUserId = update.getMessage().getFrom().getId();
        final Long chatId = update.getMessage().getChatId();
        final String username = update.getMessage().getFrom().getUserName();

        if (userService.isRegistered(telegramUserId)) {
            final User user = userService.findById(telegramUserId);
            final String text = messageService.get(MessageKey.ALREADY_REGISTERED, user);
            notificationService.sendPersistentMenu(chatId, text, user.getLanguage());
            log.info("Returning registered user greeted: userId={}", telegramUserId);
        } else if (!userService.userExists(telegramUserId)) {
            userService.createPartialUser(telegramUserId, username);
            notificationService.sendLanguageKeyboard(chatId);
            log.info("New user onboarding started: userId={}", telegramUserId);
        } else {
            // Mid-registration: RegistrationGuard re-prompts the appropriate step
            log.info("Mid-registration user sent /start: userId={}", telegramUserId);
            registrationGuard.checkAndRoute(update, () -> { /* /start is always accessible */ });
        }
    }
}
