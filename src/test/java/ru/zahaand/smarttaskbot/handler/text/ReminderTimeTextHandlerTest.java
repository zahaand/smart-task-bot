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
import ru.zahaand.smarttaskbot.dto.ConversationContext;
import ru.zahaand.smarttaskbot.dto.TaskDto;
import ru.zahaand.smarttaskbot.model.ConversationState;
import ru.zahaand.smarttaskbot.model.MessageKey;
import ru.zahaand.smarttaskbot.model.User;
import ru.zahaand.smarttaskbot.service.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReminderTimeTextHandlerTest {

    @Mock
    TimeParserService timeParserService;
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
    ReminderTimeTextHandler handler;

    private Update update;
    private Message message;
    private org.telegram.telegrambots.meta.api.objects.User from;

    private static final Long USER_ID = 42L;
    private static final Long CHAT_ID = 100L;
    private static final Long TASK_ID = 7L;
    private static final String DATE_STR = LocalDate.now().plusDays(1).toString(); // tomorrow

    @BeforeEach
    void setUp() {
        update = mock(Update.class);
        message = mock(Message.class);
        from = mock(org.telegram.telegrambots.meta.api.objects.User.class);

        when(update.getMessage()).thenReturn(message);
        when(message.getChatId()).thenReturn(CHAT_ID);
        when(message.getFrom()).thenReturn(from);
        when(from.getId()).thenReturn(USER_ID);

        when(userService.findById(USER_ID)).thenReturn(new User());
        when(messageService.get(any(MessageKey.class), any(User.class))).thenAnswer(inv -> {
            MessageKey key = inv.getArgument(0);
            return switch (key) {
                case TASK_REMINDER_SET -> "Reminder set for %s ✓";
                case SESSION_EXPIRED -> "Your session has expired. Please start over.";
                case TWELVE_OCLOCK_HINT -> "For 12 o'clock use the 24-hour format: 00:00 or 12:00.";
                case INVALID_TIME_FORMAT -> "Invalid time format. Use HH:MM, HH MM, or HH-MM.";
                default -> key.name();
            };
        });
    }

    @Nested
    @DisplayName("Valid time input")
    class ValidTime {

        @Test
        @DisplayName("creates reminder, sends confirmation, and transitions to IDLE")
        void createsReminderAndConfirms() {
            when(message.getText()).thenReturn("14:30");
            when(timeParserService.parse("14:30")).thenReturn(Optional.of(LocalTime.of(14, 30)));
            ConversationContext ctx = ConversationContext.builder()
                    .taskId(TASK_ID).date(DATE_STR).build();
            when(userStateService.getContext(USER_ID)).thenReturn(Optional.of(ctx));
            when(taskService.setReminderFromCalendar(eq(USER_ID), eq(TASK_ID), any(), eq(LocalTime.of(14, 30))))
                    .thenReturn(new TaskDto(TASK_ID, "Buy milk", DATE_STR + " 14:30"));

            handler.handle(update);

            verify(taskService).setReminderFromCalendar(eq(USER_ID), eq(TASK_ID), any(), eq(LocalTime.of(14, 30)));
            verify(notificationService).sendMessage(eq(CHAT_ID), contains("Reminder set"));
            verify(userStateService).setState(USER_ID, ConversationState.IDLE);
        }
    }

    @Nested
    @DisplayName("12-o'clock ambiguous input")
    class TwelveOClock {

        @Test
        @DisplayName("sends 24-hour format hint and stays in ENTERING_REMINDER_TIME")
        void sends12OClockHint() {
            when(message.getText()).thenReturn("12 вечера");
            when(timeParserService.parse("12 вечера")).thenReturn(Optional.empty());
            when(timeParserService.isTwelveOClockAmbiguous("12 вечера")).thenReturn(true);

            handler.handle(update);

            verify(notificationService).sendMessage(eq(CHAT_ID), contains("12"));
            verify(taskService, never()).setReminderFromCalendar(any(), any(), any(), any());
            verify(userStateService, never()).setState(any(), any());
        }
    }

    @Nested
    @DisplayName("Generic parse failure")
    class GenericFailure {

        @Test
        @DisplayName("sends generic format hint and stays in ENTERING_REMINDER_TIME")
        void sendsGenericHint() {
            when(message.getText()).thenReturn("abc");
            when(timeParserService.parse("abc")).thenReturn(Optional.empty());
            when(timeParserService.isTwelveOClockAmbiguous("abc")).thenReturn(false);

            handler.handle(update);

            verify(notificationService).sendMessage(eq(CHAT_ID), contains("Invalid time format"));
            verify(taskService, never()).setReminderFromCalendar(any(), any(), any(), any());
            verify(userStateService, never()).setState(any(), any());
        }
    }

    @Nested
    @DisplayName("Missing context")
    class MissingContext {

        @Test
        @DisplayName("sends expiry message and resets to IDLE when context is empty")
        void handlesExpiredSession() {
            when(message.getText()).thenReturn("14:30");
            when(timeParserService.parse("14:30")).thenReturn(Optional.of(LocalTime.of(14, 30)));
            when(userStateService.getContext(USER_ID)).thenReturn(Optional.empty());

            handler.handle(update);

            verify(notificationService).sendMessage(eq(CHAT_ID), contains("expired"));
            verify(userStateService).setState(USER_ID, ConversationState.IDLE);
            verify(taskService, never()).setReminderFromCalendar(any(), any(), any(), any());
        }
    }
}
