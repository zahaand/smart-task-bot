package ru.zahaand.smarttaskbot.handler.callback;

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
import ru.zahaand.smarttaskbot.config.BotConstantsUtils;
import ru.zahaand.smarttaskbot.dto.ConversationContext;
import ru.zahaand.smarttaskbot.model.ConversationState;
import ru.zahaand.smarttaskbot.model.Language;
import ru.zahaand.smarttaskbot.model.MessageKey;
import ru.zahaand.smarttaskbot.service.*;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeleteConfirmCallbackHandlerTest {

    @Mock
    TaskService taskService;
    @Mock
    UserStateService userStateService;
    @Mock
    NotificationService notificationService;
    @Mock
    UserService userService;
    @Mock
    MessageService messageService;
    @InjectMocks
    DeleteConfirmCallbackHandler handler;

    private Update update;
    private CallbackQuery cq;
    private Message message;
    private org.telegram.telegrambots.meta.api.objects.User from;

    private static final Long USER_ID = 42L;
    private static final Long CHAT_ID = 100L;
    private static final Long TASK_ID = 7L;
    private static final String CB_ID = "cbId";

    @BeforeEach
    void setUp() {
        update = mock(Update.class);
        cq = mock(CallbackQuery.class);
        message = mock(Message.class);
        from = mock(org.telegram.telegrambots.meta.api.objects.User.class);

        when(update.getCallbackQuery()).thenReturn(cq);
        when(cq.getFrom()).thenReturn(from);
        when(from.getId()).thenReturn(USER_ID);
        when(cq.getMessage()).thenReturn(message);
        when(message.getChatId()).thenReturn(CHAT_ID);
        when(cq.getId()).thenReturn(CB_ID);

        when(messageService.get(any(MessageKey.class), nullable(Language.class))).thenAnswer(inv -> {
            MessageKey key = inv.getArgument(0);
            return switch (key) {
                case SESSION_EXPIRED -> "Your session has expired.";
                case TASK_DELETED -> "✅ Task deleted.";
                case TASK_ALREADY_DELETED -> "Task has already been deleted.";
                case OPERATION_CANCELLED -> "Operation cancelled.";
                default -> key.name();
            };
        });
    }

    // ── stale state guard ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Stale state guard")
    class StaleStateGuard {

        @Test
        @DisplayName("answers and resets to IDLE when state ≠ CONFIRMING_DELETE")
        void resetsWhenNotInConfirmingState() {
            when(userStateService.getState(USER_ID)).thenReturn(ConversationState.IDLE);
            when(cq.getData()).thenReturn(BotConstantsUtils.CB_CONFIRM_DELETE + TASK_ID);

            handler.handle(update);

            verify(notificationService).answerCallbackQuery(CB_ID);
            verify(userStateService).setState(USER_ID, ConversationState.IDLE);
            verify(notificationService).sendMessage(eq(CHAT_ID), contains("expired"));
            verify(taskService, never()).deleteTask(any(), any());
        }
    }

    // ── CONFIRM_DELETE ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("CONFIRM_DELETE")
    class ConfirmDelete {

        @BeforeEach
        void inConfirmingState() {
            when(userStateService.getState(USER_ID)).thenReturn(ConversationState.CONFIRMING_DELETE);
        }

        @Test
        @DisplayName("deletes task and sends \"✅ Task deleted.\" when row count is 1")
        void deletesTaskAndConfirms() {
            ConversationContext ctx = ConversationContext.builder().taskId(TASK_ID).build();
            when(userStateService.getContext(USER_ID)).thenReturn(Optional.of(ctx));
            when(taskService.deleteTask(USER_ID, TASK_ID)).thenReturn(1);
            when(cq.getData()).thenReturn(BotConstantsUtils.CB_CONFIRM_DELETE + TASK_ID);

            handler.handle(update);

            verify(taskService).deleteTask(USER_ID, TASK_ID);
            verify(notificationService).answerCallbackQuery(CB_ID);
            verify(userStateService).setState(USER_ID, ConversationState.IDLE);
            verify(notificationService).sendMessage(eq(CHAT_ID), contains("Task deleted"));
        }

        @Test
        @DisplayName("sends \"Task has already been deleted.\" when 0 rows affected")
        void sendsAlreadyDeletedWhenZeroRows() {
            ConversationContext ctx = ConversationContext.builder().taskId(TASK_ID).build();
            when(userStateService.getContext(USER_ID)).thenReturn(Optional.of(ctx));
            when(taskService.deleteTask(USER_ID, TASK_ID)).thenReturn(0);
            when(cq.getData()).thenReturn(BotConstantsUtils.CB_CONFIRM_DELETE + TASK_ID);

            handler.handle(update);

            verify(notificationService).sendMessage(eq(CHAT_ID), contains("already been deleted"));
            verify(userStateService).setState(USER_ID, ConversationState.IDLE);
        }

        @Test
        @DisplayName("resets to IDLE and sends expiry message when context is missing")
        void handlesNullContext() {
            when(userStateService.getContext(USER_ID)).thenReturn(Optional.empty());
            when(cq.getData()).thenReturn(BotConstantsUtils.CB_CONFIRM_DELETE + TASK_ID);

            handler.handle(update);

            verify(taskService, never()).deleteTask(any(), any());
            verify(notificationService).sendMessage(eq(CHAT_ID), contains("expired"));
        }
    }

    // ── CONFIRM_CANCEL ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("CONFIRM_CANCEL")
    class ConfirmCancel {

        @BeforeEach
        void inConfirmingState() {
            when(userStateService.getState(USER_ID)).thenReturn(ConversationState.CONFIRMING_DELETE);
        }

        @Test
        @DisplayName("sends \"Deletion cancelled.\", resets to IDLE, no deletion")
        void cancelsDeletionAndResets() {
            when(cq.getData()).thenReturn(BotConstantsUtils.CB_CONFIRM_CANCEL);

            handler.handle(update);

            verify(notificationService).answerCallbackQuery(CB_ID);
            verify(userStateService).setState(USER_ID, ConversationState.IDLE);
            verify(notificationService).sendMessage(eq(CHAT_ID), eq("Operation cancelled."));
            verify(taskService, never()).deleteTask(any(), any());
        }
    }
}
