package ru.zahaand.smarttaskbot.handler.callback;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.zahaand.smarttaskbot.config.BotConstants;
import ru.zahaand.smarttaskbot.model.ConversationState;
import ru.zahaand.smarttaskbot.model.Language;
import ru.zahaand.smarttaskbot.model.MessageKey;
import ru.zahaand.smarttaskbot.model.User;
import ru.zahaand.smarttaskbot.service.MessageService;
import ru.zahaand.smarttaskbot.service.NotificationService;
import ru.zahaand.smarttaskbot.service.UserService;
import ru.zahaand.smarttaskbot.service.UserStateService;

/**
 * Handles {@code tz:*} inline keyboard callbacks (registration step 2 — timezone selection).
 * User row already exists from /start; this handler only sets the timezone and transitions to IDLE.
 * <p>
 * Обрабатывает выбор часового пояса (шаг 2 регистрации).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TimezoneCallbackHandler {

    private final UserService userService;
    private final UserStateService userStateService;
    private final NotificationService notificationService;
    private final MessageService messageService;

    public void handle(Update update) {
        final CallbackQuery callbackQuery = update.getCallbackQuery();
        final Long chatId = callbackQuery.getMessage().getChatId();
        final Long telegramUserId = callbackQuery.getFrom().getId();
        final String data = callbackQuery.getData();

        if (data == null || !data.startsWith(BotConstants.TZ_CALLBACK_PREFIX)) {
            sendError(chatId, telegramUserId);
            return;
        }

        final String timezone = data.substring(BotConstants.TZ_CALLBACK_PREFIX.length());

        if (!BotConstants.VALID_TIMEZONES.contains(timezone)) {
            sendError(chatId, telegramUserId);
            return;
        }

        userService.updateTimezone(telegramUserId, timezone);
        userStateService.setState(telegramUserId, ConversationState.IDLE);

        final User user = userService.findById(telegramUserId);
        final String confirmText = messageService.get(MessageKey.TIMEZONE_CONFIRMED, user)
                .formatted(timezone);
        notificationService.sendPersistentMenu(chatId, confirmText, user.getLanguage());
        log.info("Timezone set: userId={}, timezone={}", telegramUserId, timezone);
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
