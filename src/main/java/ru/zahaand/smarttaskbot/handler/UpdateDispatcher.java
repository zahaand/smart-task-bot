package ru.zahaand.smarttaskbot.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.zahaand.smarttaskbot.config.BotConstants;
import ru.zahaand.smarttaskbot.handler.callback.TaskActionCallbackHandler;
import ru.zahaand.smarttaskbot.handler.callback.TaskListTabCallbackHandler;
import ru.zahaand.smarttaskbot.handler.callback.TimezoneCallbackHandler;
import ru.zahaand.smarttaskbot.handler.command.*;
import ru.zahaand.smarttaskbot.handler.text.NewTaskButtonHandler;
import ru.zahaand.smarttaskbot.handler.text.TaskCreationTextHandler;
import ru.zahaand.smarttaskbot.handler.text.TaskListButtonHandler;
import ru.zahaand.smarttaskbot.model.ConversationState;
import ru.zahaand.smarttaskbot.service.NotificationService;
import ru.zahaand.smarttaskbot.service.UserStateService;

/**
 * Central router for incoming Telegram updates.
 * Delegates immediately to the appropriate handler — no business logic here.
 * <p>
 * routeMessage() step order:
 * 1. resetIfStale
 * 2. read state
 * 3. isCommand? → /cancel interrupts non-IDLE; other commands bypass state handlers
 * 4. isPersistentMenuButton? → cancel active flow, then route to button handler
 * 5. CREATING_TASK → TaskCreationTextHandler (added in Phase 3)
 * 6. ENTERING_REMINDER_TIME → ReminderTimeTextHandler (added in Phase 5)
 * 7. CONFIRMING_DELETE / SELECTING_REMINDER_DATE → "Please use the buttons above."
 * 8. button handlers (NewTaskButtonHandler, TaskListButtonHandler — added in Phases 3–4)
 * 9. command switch (legacy commands, backward compat)
 * 10. default → UnknownInputHandler
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UpdateDispatcher {

    private final RegistrationGuard registrationGuard;
    private final UserStateService userStateService;
    private final NotificationService notificationService;

    // Callback handlers
    private final TimezoneCallbackHandler timezoneCallbackHandler;
    private final TaskActionCallbackHandler taskActionCallbackHandler;
    private final TaskListTabCallbackHandler taskListTabCallbackHandler;

    // Text/button handlers
    private final NewTaskButtonHandler newTaskButtonHandler;
    private final TaskCreationTextHandler taskCreationTextHandler;
    private final TaskListButtonHandler taskListButtonHandler;

    // Command handlers
    private final StartCommandHandler startCommandHandler;
    private final HelpCommandHandler helpCommandHandler;
    private final NewTaskCommandHandler newTaskCommandHandler;
    private final TaskListCommandHandler taskListCommandHandler;
    private final RemindCommandHandler remindCommandHandler;
    private final DoneCommandHandler doneCommandHandler;
    private final UnknownInputHandler unknownInputHandler;

    // ── Entry point ───────────────────────────────────────────────────────────

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
        if (data.equals(BotConstants.CB_NO_OP)) {
            notificationService.answerCallbackQuery(callbackQueryId);
            return;
        }

        if (data.startsWith(BotConstants.CB_TZ)) {
            timezoneCallbackHandler.handle(update);
            return;
        }

        if (data.startsWith(BotConstants.CB_TASK_REMIND)
                || data.startsWith(BotConstants.CB_TASK_DONE)
                || data.startsWith(BotConstants.CB_TASK_DELETE)) {
            taskActionCallbackHandler.handle(update);
            return;
        }

        if (data.startsWith(BotConstants.CB_CAL_DATE)
                || data.startsWith(BotConstants.CB_CAL_NAV)) {
            // CalendarCallbackHandler wired in Phase 5
            log.warn("CalendarCallbackHandler not yet wired — callback ignored: {}", data);
            notificationService.answerCallbackQuery(callbackQueryId);
            return;
        }

        if (data.startsWith(BotConstants.CB_CONFIRM_DELETE)
                || data.equals(BotConstants.CB_CONFIRM_CANCEL)) {
            // DeleteConfirmCallbackHandler wired in Phase 6
            log.warn("DeleteConfirmCallbackHandler not yet wired — callback ignored: {}", data);
            notificationService.answerCallbackQuery(callbackQueryId);
            return;
        }

        if (data.startsWith(BotConstants.CB_TASKS_TAB)) {
            taskListTabCallbackHandler.handle(update);
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
        // (ReminderTimeTextHandler wired in Phase 5)

        // Step 7: button-only states — reject free text
        if (state == ConversationState.CONFIRMING_DELETE
                || state == ConversationState.SELECTING_REMINDER_DATE) {
            notificationService.sendMessage(chatId, "Please use the buttons above.");
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
        if (BotConstants.BTN_NEW_TASK.equals(text)) {
            newTaskButtonHandler.handle(update);
            return;
        }

        if (BotConstants.BTN_MY_TASKS.equals(text) || BotConstants.BTN_REMINDER.equals(text)) {
            taskListButtonHandler.handle(update);
            return;
        }

        log.warn("Unhandled persistent menu button: '{}'", text);
    }

    boolean isPersistentMenuButton(String text) {
        return BotConstants.BTN_NEW_TASK.equals(text)
                || BotConstants.BTN_MY_TASKS.equals(text)
                || BotConstants.BTN_REMINDER.equals(text);
    }

    private boolean isCommand(String text) {
        return text != null && text.startsWith("/");
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
