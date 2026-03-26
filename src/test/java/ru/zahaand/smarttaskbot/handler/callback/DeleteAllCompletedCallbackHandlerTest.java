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
import ru.zahaand.smarttaskbot.config.BotConstants;
import ru.zahaand.smarttaskbot.model.Language;
import ru.zahaand.smarttaskbot.model.MessageKey;
import ru.zahaand.smarttaskbot.model.TaskStatus;
import ru.zahaand.smarttaskbot.service.MessageService;
import ru.zahaand.smarttaskbot.service.NotificationService;
import ru.zahaand.smarttaskbot.service.TaskService;
import ru.zahaand.smarttaskbot.service.UserService;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeleteAllCompletedCallbackHandlerTest {

    @Mock
    TaskService taskService;
    @Mock
    NotificationService notificationService;
    @Mock
    UserService userService;
    @Mock
    MessageService messageService;
    @InjectMocks
    DeleteAllCompletedCallbackHandler handler;

    private Update update;
    private CallbackQuery cq;
    private Message message;
    private org.telegram.telegrambots.meta.api.objects.User from;

    private static final Long USER_ID = 42L;
    private static final Long CHAT_ID = 100L;
    private static final Integer MSG_ID = 55;
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
        when(message.getMessageId()).thenReturn(MSG_ID);
        when(cq.getId()).thenReturn(CB_ID);

        lenient().when(messageService.get(any(MessageKey.class), nullable(Language.class))).thenAnswer(inv -> {
            MessageKey key = inv.getArgument(0);
            return switch (key) {
                case NO_COMPLETED_TASKS -> "No completed tasks.";
                case DELETE_CONFIRM_ALL -> "Delete all %d completed tasks? This cannot be undone.";
                case ALL_COMPLETED_DELETED -> "All completed tasks deleted.";
                case OPERATION_CANCELLED -> "Operation cancelled.";
                default -> key.name();
            };
        });
    }

    @Nested
    class RequestWithTasks {

        @DisplayName("answers callback and sends confirmation with formatted count")
        @Test
        void sendsConfirmationWithCount() {
            when(cq.getData()).thenReturn(BotConstants.CB_DELETE_ALL_REQUEST);
            when(taskService.countCompleted(USER_ID)).thenReturn(3L);

            handler.handle(update);

            verify(notificationService).answerCallbackQuery(CB_ID);
            verify(notificationService).sendDeleteAllConfirmation(eq(CHAT_ID), contains("3"), isNull());
            verify(notificationService, never()).sendMessage(any(), any());
        }
    }

    @Nested
    class RequestWithNoTasks {

        @DisplayName("answers callback and sends NO_COMPLETED_TASKS, no confirmation prompt")
        @Test
        void sendsNoCompletedTasksMessage() {
            when(cq.getData()).thenReturn(BotConstants.CB_DELETE_ALL_REQUEST);
            when(taskService.countCompleted(USER_ID)).thenReturn(0L);

            handler.handle(update);

            verify(notificationService).answerCallbackQuery(CB_ID);
            verify(notificationService).sendMessage(eq(CHAT_ID), contains("No completed"));
            verify(notificationService, never()).sendDeleteAllConfirmation(any(), any(), any());
        }
    }

    @Nested
    class Confirm {

        @DisplayName("deletes all, sends ALL_COMPLETED_DELETED, and re-renders completed tab")
        @Test
        void deletesAllAndReRenders() {
            when(cq.getData()).thenReturn(BotConstants.CB_DELETE_ALL_CONFIRM);
            when(taskService.deleteAllCompleted(USER_ID)).thenReturn(3);
            when(taskService.getCompletedTasks(USER_ID)).thenReturn(List.of());

            handler.handle(update);

            verify(notificationService).answerCallbackQuery(CB_ID);
            verify(taskService).deleteAllCompleted(USER_ID);
            verify(notificationService).sendMessage(eq(CHAT_ID), contains("deleted"));
            verify(notificationService).editTaskList(eq(CHAT_ID), eq(MSG_ID), eq(List.of()), eq(TaskStatus.COMPLETED), isNull());
        }
    }

    @Nested
    class Cancel {

        @DisplayName("sends OPERATION_CANCELLED, re-renders completed tab, no deletion")
        @Test
        void cancelsAndReRenders() {
            when(cq.getData()).thenReturn(BotConstants.CB_DELETE_ALL_CANCEL);
            when(taskService.getCompletedTasks(USER_ID)).thenReturn(List.of());

            handler.handle(update);

            verify(notificationService).answerCallbackQuery(CB_ID);
            verify(notificationService).sendMessage(eq(CHAT_ID), contains("cancelled"));
            verify(notificationService).editTaskList(eq(CHAT_ID), eq(MSG_ID), eq(List.of()), eq(TaskStatus.COMPLETED), isNull());
            verify(taskService, never()).deleteAllCompleted(any());
        }
    }
}
