package ru.zahaand.smarttaskbot.handler.callback;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.zahaand.smarttaskbot.config.BotConstantsUtils;
import ru.zahaand.smarttaskbot.model.ConversationState;
import ru.zahaand.smarttaskbot.model.Language;
import ru.zahaand.smarttaskbot.model.MessageKey;
import ru.zahaand.smarttaskbot.model.User;
import ru.zahaand.smarttaskbot.service.MessageService;
import ru.zahaand.smarttaskbot.service.NotificationService;
import ru.zahaand.smarttaskbot.service.UserService;
import ru.zahaand.smarttaskbot.service.UserStateService;

/**
 * Handles {@code tz:*} inline keyboard callbacks for timezone selection.
 * Used in two contexts: registration step 2 (new user) and Settings → Change Timezone (existing user).
 * Context is detected by checking whether the user is already registered before updating.
 * <p>
 * Обрабатывает колбэки {@code tz:*} для выбора часового пояса.
 * Используется в двух контекстах: шаг 2 регистрации (новый пользователь) и Настройки → Изменить
 * часовой пояс (зарегистрированный пользователь). Контекст определяется до обновления данных.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TimezoneCallbackHandler {

    private final UserService userService;
    private final UserStateService userStateService;
    private final NotificationService notificationService;
    private final MessageService messageService;

    /**
     * Processes a {@code tz:*} callback: sets the timezone, transitions to IDLE, and sends the persistent menu.
     * <p>
     * Обрабатывает колбэк {@code tz:*}: устанавливает часовой пояс, переходит в IDLE, отправляет меню.
     */
    public void handle(Update update) {
        final CallbackQuery callbackQuery = update.getCallbackQuery();
        final Long chatId = callbackQuery.getMessage().getChatId();
        final Long telegramUserId = callbackQuery.getFrom().getId();
        final String data = callbackQuery.getData();

        if (data == null || !data.startsWith(BotConstantsUtils.CB_TZ)) {
            sendError(chatId, telegramUserId);
            return;
        }

        final String timezone = data.substring(BotConstantsUtils.CB_TZ.length());

        if (!BotConstantsUtils.VALID_TIMEZONES.contains(timezone)) {
            sendError(chatId, telegramUserId);
            return;
        }

        // Detect context before updating: a registered user already has a timezone set.
        final boolean isSettingsContext = userService.isRegistered(telegramUserId);

        userService.updateTimezone(telegramUserId, timezone);
        userStateService.setState(telegramUserId, ConversationState.IDLE);

        final User user = userService.findById(telegramUserId);
        if (isSettingsContext) {
            final String confirmText = messageService.get(MessageKey.SETTINGS_TIMEZONE_CHANGED, user)
                    .formatted(timezone);
            notificationService.sendPersistentMenu(chatId, confirmText, user.getLanguage());
        } else {
            final String confirmText = messageService.get(MessageKey.TIMEZONE_CONFIRMED, user)
                    .formatted(timezone);
            notificationService.sendPersistentMenu(chatId, confirmText, user.getLanguage());
        }
        log.info("Timezone set: userId={}, timezone={}, settingsContext={}", telegramUserId, timezone, isSettingsContext);
    }

    private void sendError(Long chatId, Long userId) {
        Language lang = resolveLanguage(userId);
        notificationService.sendMessage(chatId,
                messageService.get(MessageKey.SOMETHING_WENT_WRONG, lang));
    }

    private Language resolveLanguage(Long userId) {
        try {
            return userService.findById(userId).getLanguage();
        } catch (Exception e) {
            return null;
        }
    }
}
