package ru.zahaand.smarttaskbot.handler.callback;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.zahaand.smarttaskbot.config.BotConstantsUtils;
import ru.zahaand.smarttaskbot.model.ConversationState;
import ru.zahaand.smarttaskbot.model.Language;
import ru.zahaand.smarttaskbot.model.MessageKey;
import ru.zahaand.smarttaskbot.service.MessageService;
import ru.zahaand.smarttaskbot.service.NotificationService;
import ru.zahaand.smarttaskbot.service.UserService;
import ru.zahaand.smarttaskbot.service.UserStateService;

/**
 * Handles all {@code SETTINGS_*} inline keyboard callbacks from the Settings menu.
 * <p>
 * Before any settings action, conversation state is reset to IDLE to abort any in-progress flow.
 * Account deletion must NOT call setState() after deleteUser() — CASCADE removes user_states row.
 * <p>
 * Обрабатывает все колбэки {@code SETTINGS_*} из меню настроек.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettingsCallbackHandler {

    private final UserService userService;
    private final UserStateService userStateService;
    private final NotificationService notificationService;
    private final MessageService messageService;

    /**
     * Dispatches a {@code SETTINGS_*} callback to the appropriate method.
     */
    public void handle(Update update) {
        final CallbackQuery cq = update.getCallbackQuery();
        final String data = cq.getData();
        final Long userId = cq.getFrom().getId();
        final Long chatId = cq.getMessage().getChatId();

        notificationService.answerCallbackQuery(cq.getId());

        if (StringUtils.isBlank(data)) {
            log.warn("SettingsCallbackHandler: null callback data for userId={}", userId);
            return;
        }

        if (data.equals(BotConstantsUtils.CB_SETTINGS_MENU)) {
            handleMenu(chatId, userId);
        } else if (data.equals(BotConstantsUtils.CB_SETTINGS_LANG_REQUEST)) {
            handleLanguageRequest(chatId, userId);
        } else if (data.startsWith(BotConstantsUtils.CB_SETTINGS_LANG)) {
            handleLanguageChange(chatId, userId, data);
        } else if (data.equals(BotConstantsUtils.CB_SETTINGS_TZ_REQUEST)) {
            handleTimezoneRequest(chatId, userId);
        } else if (data.equals(BotConstantsUtils.CB_SETTINGS_DEL_REQ)) {
            handleDeleteRequest(chatId, userId);
        } else if (data.equals(BotConstantsUtils.CB_SETTINGS_DEL_CFM)) {
            handleDeleteConfirm(chatId, userId);
        } else if (data.equals(BotConstantsUtils.CB_SETTINGS_DEL_CNC)) {
            handleDeleteCancel(chatId, userId);
        } else {
            log.warn("SettingsCallbackHandler: unrecognised callback '{}' for userId={}", data, userId);
        }
    }

    private void handleMenu(Long chatId, Long userId) {
        // Reset state before showing settings menu to abort any in-progress flow.
        userStateService.setState(userId, ConversationState.IDLE);
        final Language language = resolveLanguage(userId);
        notificationService.sendSettingsMenu(chatId, language);
        log.info("Settings menu opened: userId={}", userId);
    }

    private void handleLanguageRequest(Long chatId, Long userId) {
        // Reset state before settings action to abort any in-progress flow.
        userStateService.setState(userId, ConversationState.IDLE);
        final Language language = resolveLanguage(userId);
        notificationService.sendSettingsLanguageKeyboard(chatId, language);
        log.info("Language selection keyboard sent via Settings: userId={}", userId);
    }

    private void handleLanguageChange(Long chatId, Long userId, String data) {
        // Reset state before settings action to abort any in-progress flow.
        userStateService.setState(userId, ConversationState.IDLE);
        final String langCode = data.substring(BotConstantsUtils.CB_SETTINGS_LANG.length());
        final Language language;
        try {
            language = Language.valueOf(langCode);
        } catch (IllegalArgumentException e) {
            log.warn("SettingsCallbackHandler: unknown language code '{}' for userId={}", langCode, userId);
            notificationService.sendMessage(chatId,
                    messageService.get(MessageKey.SOMETHING_WENT_WRONG, resolveLanguage(userId)));
            return;
        }
        userService.updateLanguage(userId, language);
        final String confirmText = messageService.get(MessageKey.SETTINGS_LANGUAGE_CHANGED, language);
        notificationService.sendPersistentMenu(chatId, confirmText, language);
        log.info("Language changed via Settings: userId={}, language={}", userId, language);
    }

    private void handleTimezoneRequest(Long chatId, Long userId) {
        // Reset state before settings action to abort any in-progress flow.
        userStateService.setState(userId, ConversationState.IDLE);
        final Language language = resolveLanguage(userId);
        // Set AWAITING_TIMEZONE so TimezoneCallbackHandler can handle the CB_TZ:IANA selection.
        userStateService.setState(userId, ConversationState.AWAITING_TIMEZONE);
        notificationService.sendTimezoneKeyboard(chatId, language);
        log.info("Timezone change requested via Settings: userId={}", userId);
    }

    private void handleDeleteRequest(Long chatId, Long userId) {
        // Reset state before settings action to abort any in-progress flow.
        userStateService.setState(userId, ConversationState.IDLE);
        final Language language = resolveLanguage(userId);
        userStateService.setState(userId, ConversationState.CONFIRMING_DELETE_ACCOUNT);
        notificationService.sendAccountDeleteConfirmation(chatId, language);
        log.info("Account deletion requested: userId={}", userId);
    }

    private void handleDeleteConfirm(Long chatId, Long userId) {
        // Guard against double-tap: if user is already deleted, respond neutrally.
        if (!userService.userExists(userId)) {
            log.warn("SETTINGS_DELETE_CONFIRM: user already deleted userId={}", userId);
            return;
        }
        final Language language = resolveLanguage(userId);
        // deleteUser() triggers CASCADE deletion of user_states — do NOT call setState() after.
        userService.deleteUser(userId);
        notificationService.sendAccountDeleted(chatId, language);
        log.info("Account deleted via Settings: userId={}", userId);
    }

    private void handleDeleteCancel(Long chatId, Long userId) {
        userStateService.setState(userId, ConversationState.IDLE);
        final Language language = resolveLanguage(userId);
        final String confirmText = messageService.get(MessageKey.SETTINGS_DELETE_CANCELLED, language);
        notificationService.sendPersistentMenu(chatId, confirmText, language);
        log.info("Account deletion cancelled: userId={}", userId);
    }

    private Language resolveLanguage(Long userId) {
        try {
            return userService.findById(userId).getLanguage();
        } catch (Exception e) {
            return null;
        }
    }
}
