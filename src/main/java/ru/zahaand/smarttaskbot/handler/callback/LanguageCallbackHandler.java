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
import ru.zahaand.smarttaskbot.service.MessageService;
import ru.zahaand.smarttaskbot.service.NotificationService;
import ru.zahaand.smarttaskbot.service.UserService;
import ru.zahaand.smarttaskbot.service.UserStateService;

/**
 * Handles {@code lang:EN} and {@code lang:RU} inline keyboard callbacks (registration step 1).
 * Persists the chosen language, transitions state to AWAITING_TIMEZONE, and sends the timezone keyboard.
 * <p>
 * Обрабатывает выбор языка на шаге 1 регистрации.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LanguageCallbackHandler {

    private final UserService userService;
    private final UserStateService userStateService;
    private final NotificationService notificationService;
    private final MessageService messageService;

    public void handle(Update update) {
        final CallbackQuery cq = update.getCallbackQuery();
        final Long userId = cq.getFrom().getId();
        final Long chatId = cq.getMessage().getChatId();
        final String data = cq.getData();

        notificationService.answerCallbackQuery(cq.getId());

        final String langCode = data.substring(BotConstants.CB_LANG.length());
        final Language language;

        try {
            language = Language.valueOf(langCode);
        } catch (IllegalArgumentException e) {
            log.warn("LanguageCallbackHandler: unknown language code '{}' for userId={}", langCode, userId);
            notificationService.sendMessage(chatId,
                    messageService.get(MessageKey.SOMETHING_WENT_WRONG, Language.EN));
            return;
        }

        try {
            userService.updateLanguage(userId, language);
        } catch (IllegalStateException e) {
            log.error("LanguageCallbackHandler: user not found for userId={}", userId, e);
            notificationService.sendMessage(chatId,
                    messageService.get(MessageKey.SOMETHING_WENT_WRONG, Language.EN));
            return;
        }

        userStateService.setState(userId, ConversationState.AWAITING_TIMEZONE);
        notificationService.sendTimezoneKeyboard(chatId, language);
        log.info("Language selected: userId={}, language={}", userId, language);
    }
}
