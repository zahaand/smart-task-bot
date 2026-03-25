package ru.zahaand.smarttaskbot.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.zahaand.smarttaskbot.config.BotConstants;
import ru.zahaand.smarttaskbot.handler.callback.*;
import ru.zahaand.smarttaskbot.handler.command.*;
import ru.zahaand.smarttaskbot.handler.text.NewTaskButtonHandler;
import ru.zahaand.smarttaskbot.handler.text.ReminderTimeTextHandler;
import ru.zahaand.smarttaskbot.handler.text.TaskCreationTextHandler;
import ru.zahaand.smarttaskbot.handler.text.TaskListButtonHandler;
import ru.zahaand.smarttaskbot.model.ConversationState;
import ru.zahaand.smarttaskbot.model.MessageKey;
import ru.zahaand.smarttaskbot.service.MessageService;
import ru.zahaand.smarttaskbot.service.NotificationService;
import ru.zahaand.smarttaskbot.service.UserService;
import ru.zahaand.smarttaskbot.service.UserStateService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UpdateDispatcherTest {

    // ── all dispatcher dependencies ───────────────────────────────────────────
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

    // ── update builders ───────────────────────────────────────────────────────

    private Update messageUpdate(String text) {
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

    private Update callbackUpdate(String data) {
        Update update = mock(Update.class);
        CallbackQuery cq = mock(CallbackQuery.class);

        when(update.hasCallbackQuery()).thenReturn(true);
        when(update.getCallbackQuery()).thenReturn(cq);
        when(cq.getData()).thenReturn(data);
        // routeCallback() always reads cq.getId() (to build callbackQueryId) before branching
        when(cq.getId()).thenReturn("cbId");
        return update;
    }

    // ── callback routing ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("Callback routing")
    class CallbackRouting {
        // No resetIfStale stub needed — callback dispatch path doesn't call it

        @Test
        @DisplayName("CAL_DATE: callback routes to CalendarCallbackHandler")
        void calDateRoutesToCalendarHandler() {
            Update update = callbackUpdate(BotConstants.CB_CAL_DATE + "2026-06-01");
            dispatcher.dispatch(update);
            verify(calendarCallbackHandler).handle(update);
        }

        @Test
        @DisplayName("CAL_NAV: callback routes to CalendarCallbackHandler")
        void calNavRoutesToCalendarHandler() {
            Update update = callbackUpdate(BotConstants.CB_CAL_NAV + "+1");
            dispatcher.dispatch(update);
            verify(calendarCallbackHandler).handle(update);
        }

        @Test
        @DisplayName("TASKS_TAB: callback routes to TaskListTabCallbackHandler")
        void tasksTabRoutesToTabHandler() {
            Update update = callbackUpdate(BotConstants.CB_TASKS_TAB + "ACTIVE");
            dispatcher.dispatch(update);
            verify(taskListTabCallbackHandler).handle(update);
        }

        @Test
        @DisplayName("NO_OP callback is answered silently — no other handler called")
        void noOpAnsweredSilently() {
            Update update = callbackUpdate(BotConstants.CB_NO_OP);
            dispatcher.dispatch(update);
            verify(notificationService).answerCallbackQuery("cbId");
            verifyNoInteractions(calendarCallbackHandler, taskListTabCallbackHandler,
                    taskActionCallbackHandler, deleteConfirmCallbackHandler,
                    timezoneCallbackHandler);
        }

        @Test
        @DisplayName("CONFIRM_DELETE: callback routes to DeleteConfirmCallbackHandler")
        void confirmDeleteRoutesToDeleteConfirmHandler() {
            Update update = callbackUpdate(BotConstants.CB_CONFIRM_DELETE + "7");
            dispatcher.dispatch(update);
            verify(deleteConfirmCallbackHandler).handle(update);
        }

        @Test
        @DisplayName("CONFIRM_CANCEL callback routes to DeleteConfirmCallbackHandler")
        void confirmCancelRoutesToDeleteConfirmHandler() {
            Update update = callbackUpdate(BotConstants.CB_CONFIRM_CANCEL);
            dispatcher.dispatch(update);
            verify(deleteConfirmCallbackHandler).handle(update);
        }
    }

    // ── message routing ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("Message routing")
    class MessageRouting {

        @BeforeEach
        void stubResetIfStale() {
            // resetIfStale is always called first — stub to do nothing
            doNothing().when(userStateService).resetIfStale(USER_ID);
        }

        @Test
        @DisplayName("BTN_NEW_TASK in IDLE state routes to NewTaskButtonHandler")
        void newTaskButtonInIdleRoutesToHandler() {
            when(userStateService.getState(USER_ID)).thenReturn(ConversationState.IDLE);
            Update update = messageUpdate(BotConstants.BTN_NEW_TASK);

            dispatcher.dispatch(update);

            verify(newTaskButtonHandler).handle(update);
        }

        @Test
        @DisplayName("BTN_MY_TASKS in CREATING_TASK → cancelWithNotification then TaskListButtonHandler")
        void myTasksButtonInCreatingTaskCancelsThenRoutes() {
            when(userStateService.getState(USER_ID)).thenReturn(ConversationState.CREATING_TASK);
            Update update = messageUpdate(BotConstants.BTN_MY_TASKS);

            dispatcher.dispatch(update);

            verify(userStateService).cancelWithNotification(USER_ID, CHAT_ID, ConversationState.CREATING_TASK);
            verify(taskListButtonHandler).handle(update);
        }

        @Test
        @DisplayName("/cancel in CREATING_TASK calls cancelWithNotification and returns — no command handler")
        void cancelCommandInCreatingTaskCancelsAndReturns() {
            when(userStateService.getState(USER_ID)).thenReturn(ConversationState.CREATING_TASK);
            Update update = messageUpdate("/cancel");

            dispatcher.dispatch(update);

            verify(userStateService).cancelWithNotification(USER_ID, CHAT_ID, ConversationState.CREATING_TASK);
            verifyNoInteractions(newTaskCommandHandler, taskListCommandHandler,
                    remindCommandHandler, doneCommandHandler);
        }

        @Test
        @DisplayName("/newtask in CREATING_TASK bypasses state handler and routes to newTaskCommandHandler")
        void newtaskCommandInCreatingTaskBypassesStateAndRoutes() {
            when(userStateService.getState(USER_ID)).thenReturn(ConversationState.CREATING_TASK);
            doAnswer(inv -> {
                ((Runnable) inv.getArgument(1)).run();
                return null;
            })
                    .when(registrationGuard).checkAndRoute(any(), any());
            Update update = messageUpdate("/newtask some task");

            dispatcher.dispatch(update);

            verify(newTaskCommandHandler).handle(update);
            verify(taskCreationTextHandler, never()).handle(any());
        }

        @Test
        @DisplayName("free text in CONFIRMING_DELETE → sends \"Please use the buttons above.\"")
        void freeTextInConfirmingDeleteSendsButtonsMessage() {
            when(userStateService.getState(USER_ID)).thenReturn(ConversationState.CONFIRMING_DELETE);
            when(messageService.get(any(MessageKey.class), (ru.zahaand.smarttaskbot.model.Language) any()))
                    .thenReturn("Please use the buttons above.");
            Update update = messageUpdate("yes please delete");

            dispatcher.dispatch(update);

            verify(notificationService).sendMessage(CHAT_ID, "Please use the buttons above.");
            verify(taskCreationTextHandler, never()).handle(any());
            verify(reminderTimeTextHandler, never()).handle(any());
        }

        @Test
        @DisplayName("free text in CREATING_TASK routes to TaskCreationTextHandler")
        void freeTextInCreatingTaskRoutesToCreationHandler() {
            when(userStateService.getState(USER_ID)).thenReturn(ConversationState.CREATING_TASK);
            Update update = messageUpdate("Buy milk");

            dispatcher.dispatch(update);

            verify(taskCreationTextHandler).handle(update);
        }

        @Test
        @DisplayName("free text in ENTERING_REMINDER_TIME routes to ReminderTimeTextHandler")
        void freeTextInEnteringReminderTimeRoutesToReminderHandler() {
            when(userStateService.getState(USER_ID)).thenReturn(ConversationState.ENTERING_REMINDER_TIME);
            Update update = messageUpdate("14:30");

            dispatcher.dispatch(update);

            verify(reminderTimeTextHandler).handle(update);
        }
    }
}
