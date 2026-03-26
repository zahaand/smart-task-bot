package ru.zahaand.smarttaskbot.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.zahaand.smarttaskbot.config.BotConstantsUtils;
import ru.zahaand.smarttaskbot.handler.callback.*;
import ru.zahaand.smarttaskbot.handler.command.*;
import ru.zahaand.smarttaskbot.handler.text.NewTaskButtonHandler;
import ru.zahaand.smarttaskbot.handler.text.ReminderTimeTextHandler;
import ru.zahaand.smarttaskbot.handler.text.TaskCreationTextHandler;
import ru.zahaand.smarttaskbot.handler.text.TaskListButtonHandler;
import ru.zahaand.smarttaskbot.model.ConversationState;
import ru.zahaand.smarttaskbot.model.Language;
import ru.zahaand.smarttaskbot.model.MessageKey;
import ru.zahaand.smarttaskbot.model.User;
import ru.zahaand.smarttaskbot.service.MessageService;
import ru.zahaand.smarttaskbot.service.NotificationService;
import ru.zahaand.smarttaskbot.service.UserService;
import ru.zahaand.smarttaskbot.service.UserStateService;

/**
 * Central router for incoming Telegram updates.
 * Delegates immediately to the appropriate handler — no business logic here.
 * <p>
 * Центральный маршрутизатор входящих обновлений Telegram.
 * Немедленно делегирует нужному обработчику — никакой бизнес-логики здесь нет.
 * <p>
 * routeMessage() step order:
 * 1. resetIfStale
 * 2. read state
 * 3. isCommand? → /cancel interrupts non-IDLE; other commands bypass state handlers
 * 4. isPersistentMenuButton? → cancel active flow, then route to button handler
 * 5. CREATING_TASK → TaskCreationTextHandler
 * 6. ENTERING_REMINDER_TIME → ReminderTimeTextHandler
 * 7. CONFIRMING_DELETE / SELECTING_REMINDER_DATE → "Please use the buttons above."
 * 8. button handlers (NewTaskButtonHandler, TaskListButtonHandler)
 * 9. command switch (legacy commands, backward compat)
 * 10. default → UnknownInputHandler
 * <p>
 * Порядок шагов routeMessage():
 * 1. resetIfStale
 * 2. чтение состояния
 * 3. isCommand? → /cancel прерывает не-IDLE; остальные команды обходят обработчики состояний
 * 4. isPersistentMenuButton? → отменить активный поток, затем направить к обработчику кнопки
 * 5. CREATING_TASK → TaskCreationTextHandler
 * 6. ENTERING_REMINDER_TIME → ReminderTimeTextHandler
 * 7. CONFIRMING_DELETE / SELECTING_REMINDER_DATE → «Используйте кнопки выше.»
 * 8. обработчики кнопок (NewTaskButtonHandler, TaskListButtonHandler)
 * 9. переключатель команд (legacy-команды, обратная совместимость)
 * 10. по умолчанию → UnknownInputHandler
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UpdateDispatcher {

    private final RegistrationGuard registrationGuard;
    private final UserStateService userStateService;
    private final NotificationService notificationService;
    private final MessageService messageService;
    private final UserService userService;

    // Callback handlers
    private final LanguageCallbackHandler languageCallbackHandler;
    private final TimezoneCallbackHandler timezoneCallbackHandler;
    private final SettingsCallbackHandler settingsCallbackHandler;
    private final TaskActionCallbackHandler taskActionCallbackHandler;
    private final TaskListTabCallbackHandler taskListTabCallbackHandler;
    private final CalendarCallbackHandler calendarCallbackHandler;
    private final DeleteConfirmCallbackHandler deleteConfirmCallbackHandler;
    private final DeleteAllCompletedCallbackHandler deleteAllCompletedCallbackHandler;

    // Text/button handlers
    private final NewTaskButtonHandler newTaskButtonHandler;
    private final TaskCreationTextHandler taskCreationTextHandler;
    private final TaskListButtonHandler taskListButtonHandler;
    private final ReminderTimeTextHandler reminderTimeTextHandler;

    // Command handlers
    private final StartCommandHandler startCommandHandler;
    private final HelpCommandHandler helpCommandHandler;
    private final NewTaskCommandHandler newTaskCommandHandler;
    private final TaskListCommandHandler taskListCommandHandler;
    private final RemindCommandHandler remindCommandHandler;
    private final DoneCommandHandler doneCommandHandler;
    private final UnknownInputHandler unknownInputHandler;

    // ── Entry point ───────────────────────────────────────────────────────────

    /**
     * Entry point for all incoming Telegram updates. Routes to callback or message handling.
     * <p>
     * Точка входа для всех входящих обновлений Telegram. Маршрутизирует колбэки или сообщения.
     */
    public void dispatch(Update update) {
        if (update.hasCallbackQuery()) {
            routeCallback(update);
            return;
        }
        if (update.hasMessage() && update.getMessage().hasText()) {
            routeMessage(update);
        }
    }

    // ── Callback routing ──────────────────────────────────────────────────────

    private void routeCallback(Update update) {
        String data = update.getCallbackQuery().getData();
        String callbackQueryId = update.getCallbackQuery().getId();

        if (data == null) {
            notificationService.answerCallbackQuery(callbackQueryId);
            return;
        }

        // NO_OP: silent answer, no further action
        if (data.equals(BotConstantsUtils.CB_NO_OP)) {
            notificationService.answerCallbackQuery(callbackQueryId);
            return;
        }

        if (data.startsWith(BotConstantsUtils.CB_LANG)) {
            languageCallbackHandler.handle(update);
            return;
        }

        if (data.startsWith(BotConstantsUtils.CB_TZ)) {
            timezoneCallbackHandler.handle(update);
            return;
        }

        if (data.startsWith(BotConstantsUtils.CB_TASK_REMIND)
                || data.startsWith(BotConstantsUtils.CB_TASK_DONE)
                || data.startsWith(BotConstantsUtils.CB_TASK_DELETE)) {
            taskActionCallbackHandler.handle(update);
            return;
        }

        if (data.startsWith(BotConstantsUtils.CB_CAL_DATE)
                || data.startsWith(BotConstantsUtils.CB_CAL_NAV)) {
            calendarCallbackHandler.handle(update);
            return;
        }

        if (data.startsWith(BotConstantsUtils.CB_CONFIRM_DELETE)
                || data.equals(BotConstantsUtils.CB_CONFIRM_CANCEL)) {
            deleteConfirmCallbackHandler.handle(update);
            return;
        }

        if (data.equals(BotConstantsUtils.CB_DELETE_ALL_REQUEST)
                || data.equals(BotConstantsUtils.CB_DELETE_ALL_CONFIRM)
                || data.equals(BotConstantsUtils.CB_DELETE_ALL_CANCEL)) {
            deleteAllCompletedCallbackHandler.handle(update);
            return;
        }

        if (data.startsWith(BotConstantsUtils.CB_TASKS_TAB)) {
            taskListTabCallbackHandler.handle(update);
            return;
        }

        if (data.equals(BotConstantsUtils.CB_SETTINGS_MENU)
                || data.equals(BotConstantsUtils.CB_SETTINGS_LANG_REQUEST)
                || data.startsWith(BotConstantsUtils.CB_SETTINGS_LANG)
                || data.equals(BotConstantsUtils.CB_SETTINGS_TZ_REQUEST)
                || data.equals(BotConstantsUtils.CB_SETTINGS_DEL_REQ)
                || data.equals(BotConstantsUtils.CB_SETTINGS_DEL_CFM)
                || data.equals(BotConstantsUtils.CB_SETTINGS_DEL_CNC)) {
            settingsCallbackHandler.handle(update);
            return;
        }

        log.warn("Unrecognised callback data: {}", data);
        notificationService.answerCallbackQuery(callbackQueryId);
    }

    // ── Message routing ───────────────────────────────────────────────────────

    private void routeMessage(Update update) {
        Long userId = update.getMessage().getFrom().getId();
        Long chatId = update.getMessage().getChatId();
        String text = update.getMessage().getText();

        // Step 1: lazy stale-state reset
        userStateService.resetIfStale(userId);

        // Step 2: read current state
        ConversationState state = userStateService.getState(userId);

        // Step 3: commands always bypass state handlers
        if (isCommand(text)) {
            if ("/cancel".equals(extractCommand(text)) && state != ConversationState.IDLE) {
                userStateService.cancelWithNotification(userId, chatId, state);
                return;
            }

            // All other commands fall through to the command switch (step 9)
            routeCommand(update, extractCommand(text), state);
            return;
        }

        // Step 4: persistent menu buttons interrupt active flows
        if (isPersistentMenuButton(text)) {
            if (state != ConversationState.IDLE) {
                userStateService.cancelWithNotification(userId, chatId, state);
            }
            routeMenuButton(update, text);
            return;
        }

        // Step 5: CREATING_TASK — free text is task input
        if (state == ConversationState.CREATING_TASK) {
            taskCreationTextHandler.handle(update);
            return;
        }

        // Step 6: ENTERING_REMINDER_TIME — free text is time input
        if (state == ConversationState.ENTERING_REMINDER_TIME) {
            reminderTimeTextHandler.handle(update);
            return;
        }

        // Step 7: button-only states — reject free text
        if (state == ConversationState.CONFIRMING_DELETE
                || state == ConversationState.SELECTING_REMINDER_DATE
                || state == ConversationState.CONFIRMING_DELETE_ACCOUNT) {
            final User buttonUser = findUserSafely(userId);
            notificationService.sendMessage(chatId, messageService.get(MessageKey.USE_BUTTONS, buttonUser != null ? buttonUser.getLanguage() : null));
            return;
        }

        // Steps 5 + 6 fall through here until handlers are wired in later phases.
        // For now route remaining state-dependent text to the command switch / unknown handler.

        // Step 9: command switch (reached only for non-command text in non-button states)
        registrationGuard.checkAndRoute(update, () -> unknownInputHandler.handle(update));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void routeCommand(Update update, String command, ConversationState state) {
        // /cancel already handled above; remaining commands always reach here
        switch (command) {
            case "/start" -> startCommandHandler.handle(update);
            case "/help" -> helpCommandHandler.handle(update);
            case "/newtask" -> registrationGuard.checkAndRoute(update,
                    () -> newTaskCommandHandler.handle(update));
            case "/tasks" -> registrationGuard.checkAndRoute(update,
                    () -> taskListCommandHandler.handle(update));
            case "/remind" -> registrationGuard.checkAndRoute(update,
                    () -> remindCommandHandler.handle(update));
            case "/done" -> registrationGuard.checkAndRoute(update,
                    () -> doneCommandHandler.handle(update));
            default -> registrationGuard.checkAndRoute(update,
                    () -> unknownInputHandler.handle(update));
        }
    }

    private void routeMenuButton(Update update, String text) {
        if (MessageKey.BTN_NEW_TASK.get(Language.EN).equals(text)
                || MessageKey.BTN_NEW_TASK.get(Language.RU).equals(text)) {
            newTaskButtonHandler.handle(update);
            return;
        }

        if (MessageKey.BTN_MY_TASKS.get(Language.EN).equals(text)
                || MessageKey.BTN_MY_TASKS.get(Language.RU).equals(text)) {
            taskListButtonHandler.handle(update);
            return;
        }

        if (MessageKey.BTN_SETTINGS.get(Language.EN).equals(text)
                || MessageKey.BTN_SETTINGS.get(Language.RU).equals(text)) {
            // State already reset by the caller (step 4) before this method is reached.
            final Long userId = update.getMessage().getFrom().getId();
            final Long chatId = update.getMessage().getChatId();
            final Language language;
            try {
                language = userService.findById(userId).getLanguage();
            } catch (Exception e) {
                log.warn("Settings button: user not found userId={}", userId);
                return;
            }
            notificationService.sendSettingsMenu(chatId, language);
            return;
        }

        if (MessageKey.BTN_START.get(Language.EN).equals(text)) {
            // Start button shown after account deletion — re-run the /start onboarding flow.
            startCommandHandler.handle(update);
            return;
        }

        log.warn("Unhandled persistent menu button: '{}'", text);
    }

    boolean isPersistentMenuButton(String text) {
        return MessageKey.BTN_NEW_TASK.get(Language.EN).equals(text)
                || MessageKey.BTN_NEW_TASK.get(Language.RU).equals(text)
                || MessageKey.BTN_MY_TASKS.get(Language.EN).equals(text)
                || MessageKey.BTN_MY_TASKS.get(Language.RU).equals(text)
                || MessageKey.BTN_SETTINGS.get(Language.EN).equals(text)
                || MessageKey.BTN_SETTINGS.get(Language.RU).equals(text)
                || MessageKey.BTN_START.get(Language.EN).equals(text);
    }

    private User findUserSafely(Long userId) {
        try {
            return userService.findById(userId);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isCommand(String text) {
        return StringUtils.startsWith(text, "/");
    }

    private String extractCommand(String text) {
        if (text == null) return "";
        String[] parts = text.split("\\s+", 2);
        String command = parts[0].toLowerCase();
        int atIndex = command.indexOf('@');
        if (atIndex != -1) command = command.substring(0, atIndex);
        return command;
    }
}
