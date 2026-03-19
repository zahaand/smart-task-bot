package ru.zahaand.smarttaskbot.handler.command;

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
import ru.zahaand.smarttaskbot.dto.TaskDto;
import ru.zahaand.smarttaskbot.service.NotificationService;
import ru.zahaand.smarttaskbot.service.TaskService;

import java.util.NoSuchElementException;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DoneCommandHandlerTest {

    @Mock
    private TaskService taskService;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private DoneCommandHandler handler;

    private Update update;
    private Message message;
    private org.telegram.telegrambots.meta.api.objects.User from;

    private static final Long CHAT_ID = 100L;
    private static final Long USER_ID = 42L;

    @BeforeEach
    void setUp() {
        update = mock(Update.class);
        message = mock(Message.class);
        from = mock(org.telegram.telegrambots.meta.api.objects.User.class);
        when(update.getMessage()).thenReturn(message);
        when(message.getChatId()).thenReturn(CHAT_ID);
        when(message.getFrom()).thenReturn(from);
        when(from.getId()).thenReturn(USER_ID);
    }

    @Nested
    class Handle {

        @Test
        @DisplayName("sends usage hint when no task ID is provided (blank args)")
        void sendsUsageHintWhenNoArgs() {
            when(message.getText()).thenReturn("/done");

            handler.handle(update);

            verify(notificationService).sendMessage(eq(CHAT_ID), contains("Usage:"));
            verifyNoInteractions(taskService);
        }

        @Test
        @DisplayName("sends usage hint when task ID is non-numeric")
        void sendsUsageHintWhenTaskIdNonNumeric() {
            when(message.getText()).thenReturn("/done abc");

            handler.handle(update);

            verify(notificationService).sendMessage(eq(CHAT_ID), contains("Usage:"));
            verifyNoInteractions(taskService);
        }

        @Test
        @DisplayName("marks task complete and sends success message for valid task ID")
        void sendsSuccessMessageForValidTaskId() {
            when(message.getText()).thenReturn("/done 7");
            when(taskService.completeTask(USER_ID, 7L))
                    .thenReturn(new TaskDto(7L, "Buy milk", null));

            handler.handle(update);

            verify(notificationService).sendMessage(eq(CHAT_ID), contains("Task completed"));
        }

        @Test
        @DisplayName("sends error message when task not found (NoSuchElementException from taskService)")
        void sendsErrorWhenTaskNotFound() {
            when(message.getText()).thenReturn("/done 99");
            when(taskService.completeTask(USER_ID, 99L))
                    .thenThrow(new NoSuchElementException("Task #99 not found."));

            handler.handle(update);

            verify(notificationService).sendMessage(eq(CHAT_ID), contains("not found"));
        }
    }
}
