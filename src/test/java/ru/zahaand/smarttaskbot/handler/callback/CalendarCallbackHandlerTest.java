package ru.zahaand.smarttaskbot.handler.callback;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.zahaand.smarttaskbot.config.BotConstants;
import ru.zahaand.smarttaskbot.dto.ConversationContext;
import ru.zahaand.smarttaskbot.model.ConversationState;
import ru.zahaand.smarttaskbot.service.NotificationService;
import ru.zahaand.smarttaskbot.service.UserStateService;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CalendarCallbackHandlerTest {

    @Mock
    UserStateService userStateService;
    @Mock
    NotificationService notificationService;
    @InjectMocks
    CalendarCallbackHandler handler;

    private Update update;
    private CallbackQuery cq;
    private Message message;
    private org.telegram.telegrambots.meta.api.objects.User from;

    private static final Long USER_ID = 42L;
    private static final Long CHAT_ID = 100L;
    private static final Integer MSG_ID = 55;
    private static final String CB_ID = "cbQueryId";

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
    }

    // ── stale state guard ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Stale state guard")
    class StaleStateGuard {

        @Test
        @DisplayName("answers query and resets to IDLE when state ≠ SELECTING_REMINDER_DATE")
        void resetsWhenNotInSelectingState() {
            when(userStateService.getState(USER_ID)).thenReturn(ConversationState.IDLE);
            when(cq.getData()).thenReturn(BotConstants.CB_CAL_DATE + "2026-06-01");

            handler.handle(update);

            verify(notificationService).answerCallbackQuery(CB_ID);
            verify(userStateService).setState(USER_ID, ConversationState.IDLE);
            verify(notificationService).sendMessage(eq(CHAT_ID), contains("expired"));
            verifyNoMoreInteractions(notificationService);
        }
    }

    // ── CAL_NAV ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("CAL_NAV handling")
    class CalNav {

        private final LocalDate today = LocalDate.now();
        private final YearMonth current = YearMonth.from(today);
        private final YearMonth next = current.plusMonths(1);

        @BeforeEach
        void inSelectingState() {
            when(userStateService.getState(USER_ID)).thenReturn(ConversationState.SELECTING_REMINDER_DATE);
        }

        @Test
        @DisplayName("CAL_NAV:+1 from a future month navigates forward and calls editCalendar")
        void navigatesForward() {
            ConversationContext ctx = ConversationContext.builder()
                    .taskId(7L)
                    .viewingYear(next.getYear())
                    .viewingMonth(next.getMonthValue())
                    .build();
            when(userStateService.getContext(USER_ID)).thenReturn(Optional.of(ctx));
            when(cq.getData()).thenReturn(BotConstants.CB_CAL_NAV + "+1");

            handler.handle(update);

            YearMonth expected = next.plusMonths(1);
            verify(notificationService).editCalendar(CHAT_ID, MSG_ID, expected.getYear(), expected.getMonthValue());
            verify(notificationService).answerCallbackQuery(CB_ID);
        }

        @Test
        @DisplayName("CAL_NAV:-1 from a future month navigates back one month")
        void navigatesBack() {
            ConversationContext ctx = ConversationContext.builder()
                    .taskId(7L)
                    .viewingYear(next.getYear())
                    .viewingMonth(next.getMonthValue())
                    .build();
            when(userStateService.getContext(USER_ID)).thenReturn(Optional.of(ctx));
            when(cq.getData()).thenReturn(BotConstants.CB_CAL_NAV + "-1");

            handler.handle(update);

            verify(notificationService).editCalendar(CHAT_ID, MSG_ID, current.getYear(), current.getMonthValue());
            verify(notificationService).answerCallbackQuery(CB_ID);
        }

        @Test
        @DisplayName("CAL_NAV:-1 from the current month answers silently (server-side guard)")
        void doesNotNavigateBeforeCurrentMonth() {
            ConversationContext ctx = ConversationContext.builder()
                    .taskId(7L)
                    .viewingYear(current.getYear())
                    .viewingMonth(current.getMonthValue())
                    .build();
            when(userStateService.getContext(USER_ID)).thenReturn(Optional.of(ctx));
            when(cq.getData()).thenReturn(BotConstants.CB_CAL_NAV + "-1");

            handler.handle(update);

            verify(notificationService).answerCallbackQuery(CB_ID);
            verify(notificationService, never()).editCalendar(any(), any(), anyInt(), anyInt());
        }
    }

    // ── CAL_DATE ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("CAL_DATE handling")
    class CalDate {

        @BeforeEach
        void inSelectingState() {
            when(userStateService.getState(USER_ID)).thenReturn(ConversationState.SELECTING_REMINDER_DATE);
        }

        @Test
        @DisplayName("transitions to ENTERING_REMINDER_TIME with date stored in context")
        void transitionsState() {
            ConversationContext ctx = ConversationContext.builder().taskId(3L).build();
            when(userStateService.getContext(USER_ID)).thenReturn(Optional.of(ctx));
            when(cq.getData()).thenReturn(BotConstants.CB_CAL_DATE + "2026-06-15");

            handler.handle(update);

            ArgumentCaptor<ConversationContext> ctxCaptor = ArgumentCaptor.forClass(ConversationContext.class);
            verify(userStateService).setStateWithContext(
                    eq(USER_ID), eq(ConversationState.ENTERING_REMINDER_TIME), ctxCaptor.capture());
            assertThat(ctxCaptor.getValue().getDate()).isEqualTo("2026-06-15");
            assertThat(ctxCaptor.getValue().getTaskId()).isEqualTo(3L);
        }

        @Test
        @DisplayName("sends time-entry prompt message")
        void sendsTimePrompt() {
            ConversationContext ctx = ConversationContext.builder().taskId(3L).build();
            when(userStateService.getContext(USER_ID)).thenReturn(Optional.of(ctx));
            when(cq.getData()).thenReturn(BotConstants.CB_CAL_DATE + "2026-06-15");

            handler.handle(update);

            verify(notificationService).sendMessage(eq(CHAT_ID), anyString());
            verify(notificationService).answerCallbackQuery(CB_ID);
        }
    }
}
