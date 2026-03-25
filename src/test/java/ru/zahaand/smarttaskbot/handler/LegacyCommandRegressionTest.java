package ru.zahaand.smarttaskbot.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.zahaand.smarttaskbot.handler.callback.*;
import ru.zahaand.smarttaskbot.handler.command.*;
import ru.zahaand.smarttaskbot.handler.text.NewTaskButtonHandler;
import ru.zahaand.smarttaskbot.handler.text.ReminderTimeTextHandler;
import ru.zahaand.smarttaskbot.handler.text.TaskCreationTextHandler;
import ru.zahaand.smarttaskbot.handler.text.TaskListButtonHandler;
import ru.zahaand.smarttaskbot.model.ConversationState;
import ru.zahaand.smarttaskbot.service.MessageService;
import ru.zahaand.smarttaskbot.service.NotificationService;
import ru.zahaand.smarttaskbot.service.UserService;
import ru.zahaand.smarttaskbot.service.UserStateService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Regression tests: all legacy slash commands must still reach their handlers
 * after the state-machine refactoring. Tests run in both IDLE and non-IDLE states.
 */
@ExtendWith(MockitoExtension.class)
class LegacyCommandRegressionTest {

    @Mock
    RegistrationGuard registrationGuard;
    @Mock
    UserStateService userStateService;
    @Mock
    NotificationService notificationService;

    @Mock
    LanguageCallbackHandler languageCallbackHandler;
    @Mock
    MessageService messageService;
    @Mock
    UserService userService;

    @Mock
    TimezoneCallbackHandler timezoneCallbackHandler;
    @Mock
    TaskActionCallbackHandler taskActionCallbackHandler;
    @Mock
    TaskListTabCallbackHandler taskListTabCallbackHandler;
    @Mock
    CalendarCallbackHandler calendarCallbackHandler;
    @Mock
    DeleteConfirmCallbackHandler deleteConfirmCallbackHandler;

    @Mock
    NewTaskButtonHandler newTaskButtonHandler;
    @Mock
    TaskCreationTextHandler taskCreationTextHandler;
    @Mock
    TaskListButtonHandler taskListButtonHandler;
    @Mock
    ReminderTimeTextHandler reminderTimeTextHandler;

    @Mock
    StartCommandHandler startCommandHandler;
    @Mock
    HelpCommandHandler helpCommandHandler;
    @Mock
    NewTaskCommandHandler newTaskCommandHandler;
    @Mock
    TaskListCommandHandler taskListCommandHandler;
    @Mock
    RemindCommandHandler remindCommandHandler;
    @Mock
    DoneCommandHandler doneCommandHandler;
    @Mock
    UnknownInputHandler unknownInputHandler;

    @InjectMocks
    UpdateDispatcher dispatcher;

    private static final Long USER_ID = 42L;
    private static final Long CHAT_ID = 100L;

    private Update commandUpdate(String text) {
        Update update = mock(Update.class);
        Message msg = mock(Message.class);
        org.telegram.telegrambots.meta.api.objects.User from = mock(org.telegram.telegrambots.meta.api.objects.User.class);

        when(update.hasCallbackQuery()).thenReturn(false);
        when(update.hasMessage()).thenReturn(true);
        when(update.getMessage()).thenReturn(msg);
        when(msg.hasText()).thenReturn(true);
        when(msg.getText()).thenReturn(text);
        when(msg.getChatId()).thenReturn(CHAT_ID);
        when(msg.getFrom()).thenReturn(from);
        when(from.getId()).thenReturn(USER_ID);
        return update;
    }

    // Routes registrationGuard.checkAndRoute to immediately run the action
    private void guardAllowsAccess() {
        doAnswer(inv -> {
            ((Runnable) inv.getArgument(1)).run();
            return null;
        })
                .when(registrationGuard).checkAndRoute(any(), any());
    }

    @BeforeEach
    void stubResetIfStale() {
        doNothing().when(userStateService).resetIfStale(USER_ID);
    }

    // ── commands in IDLE state ─────────────────────────────────────────────────

    @Nested
    @DisplayName("Commands in IDLE state reach their handlers")
    class CommandsInIdle {

        @BeforeEach
        void idleState() {
            when(userStateService.getState(USER_ID))
                    .thenReturn(ConversationState.IDLE);
        }

        @Test
        @DisplayName("/help delegates to HelpCommandHandler")
        void helpCommand() {
            // /help routes directly — no guard involved
            Update update = commandUpdate("/help");
            dispatcher.dispatch(update);
            verify(helpCommandHandler).handle(update);
        }

        @Test
        @DisplayName("/newtask delegates to NewTaskCommandHandler")
        void newtaskCommand() {
            guardAllowsAccess();
            Update update = commandUpdate("/newtask Buy milk");
            dispatcher.dispatch(update);
            verify(newTaskCommandHandler).handle(update);
        }

        @Test
        @DisplayName("/tasks delegates to TaskListCommandHandler")
        void tasksCommand() {
            guardAllowsAccess();
            Update update = commandUpdate("/tasks");
            dispatcher.dispatch(update);
            verify(taskListCommandHandler).handle(update);
        }

        @Test
        @DisplayName("/remind delegates to RemindCommandHandler")
        void remindCommand() {
            guardAllowsAccess();
            Update update = commandUpdate("/remind 1 25.05.2026 14:00");
            dispatcher.dispatch(update);
            verify(remindCommandHandler).handle(update);
        }

        @Test
        @DisplayName("/done delegates to DoneCommandHandler")
        void doneCommand() {
            guardAllowsAccess();
            Update update = commandUpdate("/done 1");
            dispatcher.dispatch(update);
            verify(doneCommandHandler).handle(update);
        }
    }

    // ── commands in non-IDLE state bypass state routing ───────────────────────

    @Nested
    @DisplayName("Commands in CREATING_TASK still reach their handlers (bypass state routing)")
    class CommandsInCreatingTask {

        @BeforeEach
        void creatingTaskState() {
            when(userStateService.getState(USER_ID)).thenReturn(ConversationState.CREATING_TASK);
        }

        @Test
        @DisplayName("/newtask in CREATING_TASK bypasses state and reaches NewTaskCommandHandler")
        void newtaskBypassesState() {
            guardAllowsAccess();
            Update update = commandUpdate("/newtask Another task");
            dispatcher.dispatch(update);
            verify(newTaskCommandHandler).handle(update);
            verify(taskCreationTextHandler, never()).handle(any());
        }

        @Test
        @DisplayName("/tasks in CREATING_TASK bypasses state and reaches TaskListCommandHandler")
        void tasksCommandBypassesState() {
            guardAllowsAccess();
            Update update = commandUpdate("/tasks");
            dispatcher.dispatch(update);
            verify(taskListCommandHandler).handle(update);
            verify(taskCreationTextHandler, never()).handle(any());
        }

        @Test
        @DisplayName("/help in CREATING_TASK bypasses state and reaches HelpCommandHandler")
        void helpBypassesState() {
            // /help routes directly — no guard involved
            Update update = commandUpdate("/help");
            dispatcher.dispatch(update);
            verify(helpCommandHandler).handle(update);
        }
    }

    // ── /start is never guarded ───────────────────────────────────────────────

    @Nested
    @DisplayName("/start always delegates without registration check")
    class StartCommand {

        @Test
        @DisplayName("/start in IDLE reaches StartCommandHandler without going through RegistrationGuard")
        void startCommandInIdle() {
            when(userStateService.getState(USER_ID)).thenReturn(ConversationState.IDLE);
            Update update = commandUpdate("/start");
            dispatcher.dispatch(update);
            verify(startCommandHandler).handle(update);
            verify(registrationGuard, never()).checkAndRoute(any(), any());
        }
    }
}
