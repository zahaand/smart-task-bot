package ru.zahaand.smarttaskbot.handler.text;

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
import ru.zahaand.smarttaskbot.model.ConversationState;
import ru.zahaand.smarttaskbot.service.NotificationService;
import ru.zahaand.smarttaskbot.service.TaskService;
import ru.zahaand.smarttaskbot.service.UserStateService;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskCreationTextHandlerTest {

    @Mock
    TaskService taskService;
    @Mock
    UserStateService userStateService;
    @Mock
    NotificationService notificationService;
    @InjectMocks
    TaskCreationTextHandler handler;

    private Update update;
    private Message message;
    private org.telegram.telegrambots.meta.api.objects.User from;

    private static final Long USER_ID = 42L;
    private static final Long CHAT_ID = 100L;

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
    @DisplayName("Blank / whitespace input")
    class BlankInput {

        @Test
        @DisplayName("sends error and does NOT create task or change state")
        void blankTextSendsError() {
            when(message.getText()).thenReturn("   ");

            handler.handle(update);

            verify(notificationService).sendMessage(eq(CHAT_ID), contains("cannot be empty"));
            verifyNoInteractions(taskService);
            verifyNoInteractions(userStateService);
        }
    }

    @Nested
    @DisplayName("Valid input")
    class ValidInput {

        @Test
        @DisplayName("creates task, sends confirmation with id, and transitions to IDLE")
        void createsTaskAndConfirms() {
            when(message.getText()).thenReturn("Buy milk");
            when(taskService.createTask(USER_ID, "Buy milk"))
                    .thenReturn(new TaskDto(3L, "Buy milk", null));

            handler.handle(update);

            verify(taskService).createTask(USER_ID, "Buy milk");
            verify(notificationService).sendMessage(eq(CHAT_ID), contains("#3"));
            verify(userStateService).setState(USER_ID, ConversationState.IDLE);
        }

        @Test
        @DisplayName("trims leading/trailing whitespace from the message text")
        void trimsText() {
            when(message.getText()).thenReturn("  Buy milk  ");
            when(taskService.createTask(USER_ID, "Buy milk"))
                    .thenReturn(new TaskDto(1L, "Buy milk", null));

            handler.handle(update);

            verify(taskService).createTask(USER_ID, "Buy milk");
        }
    }
}
