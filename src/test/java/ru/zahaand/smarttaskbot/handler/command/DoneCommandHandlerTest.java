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
import ru.zahaand.smarttaskbot.model.BotException;
import ru.zahaand.smarttaskbot.model.MessageKey;
import ru.zahaand.smarttaskbot.model.User;
import ru.zahaand.smarttaskbot.service.MessageService;
import ru.zahaand.smarttaskbot.service.NotificationService;
import ru.zahaand.smarttaskbot.service.TaskService;
import ru.zahaand.smarttaskbot.service.UserService;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DoneCommandHandlerTest {

    @Mock
    private TaskService taskService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private UserService userService;

    @Mock
    private MessageService messageService;

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

        lenient().when(userService.findById(USER_ID)).thenReturn(new User());
        lenient().when(messageService.get(any(MessageKey.class), any(User.class))).thenAnswer(inv -> {
            MessageKey key = inv.getArgument(0);
            return switch (key) {
                case DONE_USAGE_HINT -> "Please provide a task ID.\nUsage: /done <task_id>";
                case TASK_COMPLETED -> "Task completed ✓";
                case TASK_NOT_FOUND -> "Task #%d not found.";
                default -> key.name();
            };
        });
    }

    @Nested
    class Handle {

        @DisplayName("sends usage hint when no task ID is provided (blank args)")
        @Test
        void sendsUsageHintWhenNoArgs() {
            when(message.getText()).thenReturn("/done");

            handler.handle(update);

            verify(notificationService).sendMessage(eq(CHAT_ID), contains("Usage:"));
            verifyNoInteractions(taskService);
        }

        @DisplayName("sends usage hint when task ID is non-numeric")
        @Test
        void sendsUsageHintWhenTaskIdNonNumeric() {
            when(message.getText()).thenReturn("/done abc");

            handler.handle(update);

            verify(notificationService).sendMessage(eq(CHAT_ID), contains("Usage:"));
            verifyNoInteractions(taskService);
        }

        @DisplayName("marks task complete and sends success message for valid task ID")
        @Test
        void sendsSuccessMessageForValidTaskId() {
            when(message.getText()).thenReturn("/done 7");
            when(taskService.completeTask(USER_ID, 7L))
                    .thenReturn(new TaskDto(7L, "Buy milk", null));

            handler.handle(update);

            verify(notificationService).sendMessage(eq(CHAT_ID), contains("Task completed"));
        }

        @DisplayName("sends error message when task not found (BotException from taskService)")
        @Test
        void sendsErrorWhenTaskNotFound() {
            when(message.getText()).thenReturn("/done 99");
            when(taskService.completeTask(USER_ID, 99L))
                    .thenThrow(new BotException(MessageKey.TASK_NOT_FOUND, 99L));

            handler.handle(update);

            verify(notificationService).sendMessage(eq(CHAT_ID), contains("not found"));
        }
    }
}
