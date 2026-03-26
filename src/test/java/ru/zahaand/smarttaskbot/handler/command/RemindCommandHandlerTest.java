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

import java.time.format.DateTimeParseException;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RemindCommandHandlerTest {

    @Mock
    private TaskService taskService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private UserService userService;

    @Mock
    private MessageService messageService;

    @InjectMocks
    private RemindCommandHandler handler;

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
                case REMIND_USAGE_HINT -> "Usage: /remind <id> DD.MM.YYYY HH:mm";
                case REMIND_FORMAT_ERROR -> "Invalid date format.\nUsage: /remind <id> DD.MM.YYYY HH:mm";
                case REMINDER_SET -> "Reminder set ✓\n#%d: %s — %s";
                case TASK_NOT_FOUND -> "Task #%d not found.";
                case CANNOT_REMIND_COMPLETED -> "Cannot set a reminder on a completed task.";
                default -> key.name();
            };
        });
    }

    @Nested
    class Handle {

        @DisplayName("sends usage hint when no arguments are provided")
        @Test
        void sendsUsageHintWhenNoArgs() {
            when(message.getText()).thenReturn("/remind");

            handler.handle(update);

            verify(notificationService).sendMessage(eq(CHAT_ID), contains("Usage:"));
            verifyNoInteractions(taskService);
        }

        @DisplayName("sends usage hint when fewer than 3 argument parts are given")
        @Test
        void sendsUsageHintWhenFewerThan3Parts() {
            when(message.getText()).thenReturn("/remind 7 25.03.2026");

            handler.handle(update);

            verify(notificationService).sendMessage(eq(CHAT_ID), contains("Usage:"));
            verifyNoInteractions(taskService);
        }

        @DisplayName("sends usage hint when task ID part is non-numeric")
        @Test
        void sendsUsageHintWhenTaskIdNonNumeric() {
            when(message.getText()).thenReturn("/remind abc 25.03.2026 09:00");

            handler.handle(update);

            verify(notificationService).sendMessage(eq(CHAT_ID), contains("Usage:"));
            verifyNoInteractions(taskService);
        }

        @DisplayName("sets reminder and sends success message for valid input")
        @Test
        void sendsSuccessMessageForValidInput() {
            when(message.getText()).thenReturn("/remind 7 25.03.2026 09:00");
            when(taskService.setReminder(USER_ID, 7L, "25.03.2026 09:00"))
                    .thenReturn(new TaskDto(7L, "Buy milk", "25.03.2026 09:00"));
            when(userService.getTimezone(USER_ID)).thenReturn("Europe/Moscow");

            handler.handle(update);

            verify(notificationService).sendMessage(eq(CHAT_ID), contains("Reminder set"));
        }

        @DisplayName("sends format error message when datetime cannot be parsed (DateTimeParseException)")
        @Test
        void sendsFormatErrorWhenDatetimeCannotBeParsed() {
            when(message.getText()).thenReturn("/remind 7 25-03-2026 09:00");
            when(taskService.setReminder(eq(USER_ID), eq(7L), anyString()))
                    .thenThrow(new DateTimeParseException("bad", "25-03-2026 09:00", 0));

            handler.handle(update);

            verify(notificationService).sendMessage(eq(CHAT_ID), contains("Invalid date format"));
        }

        @DisplayName("sends error message when task not found (BotException from taskService)")
        @Test
        void sendsErrorWhenTaskNotFound() {
            when(message.getText()).thenReturn("/remind 99 25.03.2026 09:00");
            when(taskService.setReminder(USER_ID, 99L, "25.03.2026 09:00"))
                    .thenThrow(new BotException(MessageKey.TASK_NOT_FOUND, 99L));

            handler.handle(update);

            verify(notificationService).sendMessage(eq(CHAT_ID), contains("not found"));
        }

        @DisplayName("sends error message when task is already completed (BotException)")
        @Test
        void sendsErrorWhenTaskAlreadyCompleted() {
            when(message.getText()).thenReturn("/remind 7 25.03.2026 09:00");
            when(taskService.setReminder(USER_ID, 7L, "25.03.2026 09:00"))
                    .thenThrow(new BotException(MessageKey.CANNOT_REMIND_COMPLETED));

            handler.handle(update);

            verify(notificationService).sendMessage(eq(CHAT_ID), contains("completed task"));
        }
    }
}
