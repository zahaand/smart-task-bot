package ru.zahaand.smarttaskbot.handler.command;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.zahaand.smarttaskbot.model.MessageKey;
import ru.zahaand.smarttaskbot.model.User;
import ru.zahaand.smarttaskbot.service.MessageService;
import ru.zahaand.smarttaskbot.service.NotificationService;
import ru.zahaand.smarttaskbot.service.UserService;

/**
 * Handles the {@code /start} command and the "🚀 Start" button with three-branch routing:
 * <ol>
 *   <li>User does NOT exist in DB (new user or post-deletion) → createPartialUser + language keyboard</li>
 *   <li>Fully registered user → ALREADY_REGISTERED + persistent menu in user's language</li>
 *   <li>Mid-registration user (row exists, incomplete) → re-prompt the pending step explicitly</li>
 * </ol>
 * <p>
 * Обрабатывает команду {@code /start} и кнопку «🚀 Start» с тремя ветками маршрутизации:
 * <ol>
 *   <li>Пользователь НЕ существует в БД (новый или после удаления) → createPartialUser + клавиатура языка</li>
 *   <li>Зарегистрированный пользователь → ALREADY_REGISTERED + постоянное меню</li>
 *   <li>Пользователь в процессе регистрации → явный повтор незавершённого шага</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StartCommandHandler {

    private final UserService userService;
    private final NotificationService notificationService;
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

        if (!userService.userExists(telegramUserId)) {
            // Brand-new user or post-deletion: start onboarding from scratch.
            userService.createPartialUser(telegramUserId, username);
            notificationService.sendLanguageKeyboard(chatId);
            log.info("New user onboarding started: userId={}", telegramUserId);
        } else if (userService.isRegistered(telegramUserId)) {
            final User user = userService.findById(telegramUserId);
            final String text = messageService.get(MessageKey.ALREADY_REGISTERED, user);
            notificationService.sendPersistentMenu(chatId, text, user.getLanguage());
            log.info("Returning registered user greeted: userId={}", telegramUserId);
        } else {
            // Mid-registration: re-prompt the pending step without RegistrationGuard delegation.
            final User user = userService.findById(telegramUserId);
            if (user.getLanguage() == null) {
                notificationService.sendLanguageKeyboard(chatId);
                log.info("Mid-registration user re-prompted for language: userId={}", telegramUserId);
            } else {
                notificationService.sendTimezoneKeyboard(chatId, user.getLanguage());
                log.info("Mid-registration user re-prompted for timezone: userId={}", telegramUserId);
            }
        }
    }
}
