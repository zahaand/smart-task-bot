package ru.zahaand.smarttaskbot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.zahaand.smarttaskbot.dto.ConversationContext;
import ru.zahaand.smarttaskbot.model.ConversationState;
import ru.zahaand.smarttaskbot.model.UserState;
import ru.zahaand.smarttaskbot.repository.UserStateRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserStateServiceTest {

    @Mock
    private UserStateRepository userStateRepository;

    @Mock
    private NotificationService notificationService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    private UserStateService service;

    private static final Long USER_ID = 42L;
    private static final Long CHAT_ID = 100L;

    @BeforeEach
    void setUp() {
        service = new UserStateService(userStateRepository, notificationService, objectMapper);
    }

    // ── getState ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getState()")
    class GetState {

        @Test
        @DisplayName("returns IDLE when no row exists for user")
        void returnsIdleWhenNoRow() {
            when(userStateRepository.findById(USER_ID)).thenReturn(Optional.empty());

            assertThat(service.getState(USER_ID)).isEqualTo(ConversationState.IDLE);
        }

        @Test
        @DisplayName("returns stored state when row exists")
        void returnsStoredState() {
            UserState stored = new UserState(USER_ID);
            stored.setState(ConversationState.CREATING_TASK);
            when(userStateRepository.findById(USER_ID)).thenReturn(Optional.of(stored));

            assertThat(service.getState(USER_ID)).isEqualTo(ConversationState.CREATING_TASK);
        }
    }

    // ── setState ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("setState()")
    class SetState {

        @Test
        @DisplayName("upserts state and clears context")
        void upsertsStateAndClearsContext() {
            when(userStateRepository.findById(USER_ID)).thenReturn(Optional.empty());
            when(userStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.setState(USER_ID, ConversationState.CREATING_TASK);

            ArgumentCaptor<UserState> captor = ArgumentCaptor.forClass(UserState.class);
            verify(userStateRepository).save(captor.capture());
            assertThat(captor.getValue().getState()).isEqualTo(ConversationState.CREATING_TASK);
            assertThat(captor.getValue().getContext()).isNull();
        }

        @Test
        @DisplayName("overwrites existing row")
        void overwritesExistingRow() {
            UserState existing = new UserState(USER_ID);
            existing.setState(ConversationState.CREATING_TASK);
            existing.setContext("{\"taskId\":1}");
            when(userStateRepository.findById(USER_ID)).thenReturn(Optional.of(existing));
            when(userStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.setState(USER_ID, ConversationState.IDLE);

            ArgumentCaptor<UserState> captor = ArgumentCaptor.forClass(UserState.class);
            verify(userStateRepository).save(captor.capture());
            assertThat(captor.getValue().getState()).isEqualTo(ConversationState.IDLE);
            assertThat(captor.getValue().getContext()).isNull();
        }
    }

    // ── setStateWithContext ───────────────────────────────────────────────────

    @Nested
    @DisplayName("setStateWithContext()")
    class SetStateWithContext {

        @Test
        @DisplayName("serializes ConversationContext to JSON and saves")
        void serializesContext() {
            when(userStateRepository.findById(USER_ID)).thenReturn(Optional.empty());
            when(userStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ConversationContext ctx = ConversationContext.builder().taskId(7L).build();
            service.setStateWithContext(USER_ID, ConversationState.CONFIRMING_DELETE, ctx);

            ArgumentCaptor<UserState> captor = ArgumentCaptor.forClass(UserState.class);
            verify(userStateRepository).save(captor.capture());
            assertThat(captor.getValue().getState()).isEqualTo(ConversationState.CONFIRMING_DELETE);
            assertThat(captor.getValue().getContext()).contains("\"taskId\":7");
        }
    }

    // ── getContext ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getContext()")
    class GetContext {

        @Test
        @DisplayName("returns empty when no row exists")
        void returnsEmptyWhenNoRow() {
            when(userStateRepository.findById(USER_ID)).thenReturn(Optional.empty());

            assertThat(service.getContext(USER_ID)).isEmpty();
        }

        @Test
        @DisplayName("returns empty and resets state to IDLE when context JSON is malformed")
        void resetsToIdleOnMalformedJson() {
            UserState stored = new UserState(USER_ID);
            stored.setState(ConversationState.CONFIRMING_DELETE);
            stored.setContext("{not valid json}");
            when(userStateRepository.findById(USER_ID)).thenReturn(Optional.of(stored));
            when(userStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Optional<ConversationContext> result = service.getContext(USER_ID);

            assertThat(result).isEmpty();
            verify(userStateRepository, atLeastOnce()).save(any());
        }

        @Test
        @DisplayName("deserializes stored JSON into ConversationContext")
        void deserializesContext() {
            UserState stored = new UserState(USER_ID);
            stored.setContext("{\"taskId\":5,\"date\":\"2026-05-20\"}");
            when(userStateRepository.findById(USER_ID)).thenReturn(Optional.of(stored));

            Optional<ConversationContext> result = service.getContext(USER_ID);

            assertThat(result).isPresent();
            assertThat(result.get().getTaskId()).isEqualTo(5L);
            assertThat(result.get().getDate()).isEqualTo("2026-05-20");
        }
    }

    // ── resetIfStale ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("resetIfStale()")
    class ResetIfStale {

        @Test
        @DisplayName("resets to IDLE when updatedAt is more than 24h ago")
        void resetsStaleState() {
            UserState stored = new UserState(USER_ID);
            stored.setState(ConversationState.CREATING_TASK);
            stored.setUpdatedAt(Instant.now().minus(25, ChronoUnit.HOURS));
            when(userStateRepository.findById(USER_ID)).thenReturn(Optional.of(stored));
            when(userStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.resetIfStale(USER_ID);

            ArgumentCaptor<UserState> captor = ArgumentCaptor.forClass(UserState.class);
            verify(userStateRepository).save(captor.capture());
            assertThat(captor.getValue().getState()).isEqualTo(ConversationState.IDLE);
        }

        @Test
        @DisplayName("does NOT reset when updatedAt is less than 24h ago")
        void doesNotResetFreshState() {
            UserState stored = new UserState(USER_ID);
            stored.setState(ConversationState.CREATING_TASK);
            stored.setUpdatedAt(Instant.now().minus(1, ChronoUnit.HOURS));
            when(userStateRepository.findById(USER_ID)).thenReturn(Optional.of(stored));

            service.resetIfStale(USER_ID);

            verify(userStateRepository, never()).save(any());
        }

        @Test
        @DisplayName("does NOT reset when state is already IDLE")
        void doesNotResetIdleState() {
            UserState stored = new UserState(USER_ID);
            stored.setState(ConversationState.IDLE);
            stored.setUpdatedAt(Instant.now().minus(30, ChronoUnit.HOURS));
            when(userStateRepository.findById(USER_ID)).thenReturn(Optional.of(stored));

            service.resetIfStale(USER_ID);

            verify(userStateRepository, never()).save(any());
        }
    }

    // ── cancelWithNotification ────────────────────────────────────────────────

    @Nested
    @DisplayName("cancelWithNotification()")
    class CancelWithNotification {

        @Test
        @DisplayName("CREATING_TASK → sends \"Task creation cancelled.\"")
        void creatingTask() {
            when(userStateRepository.findById(USER_ID)).thenReturn(Optional.empty());
            when(userStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.cancelWithNotification(USER_ID, CHAT_ID, ConversationState.CREATING_TASK);

            verify(notificationService).sendMessage(eq(CHAT_ID), eq("Task creation cancelled."));
        }

        @Test
        @DisplayName("ENTERING_REMINDER_TIME → sends \"Reminder setup cancelled.\"")
        void enteringReminderTime() {
            when(userStateRepository.findById(USER_ID)).thenReturn(Optional.empty());
            when(userStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.cancelWithNotification(USER_ID, CHAT_ID, ConversationState.ENTERING_REMINDER_TIME);

            verify(notificationService).sendMessage(eq(CHAT_ID), eq("Reminder setup cancelled."));
        }

        @Test
        @DisplayName("CONFIRMING_DELETE → sends \"Deletion cancelled.\"")
        void confirmingDelete() {
            when(userStateRepository.findById(USER_ID)).thenReturn(Optional.empty());
            when(userStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.cancelWithNotification(USER_ID, CHAT_ID, ConversationState.CONFIRMING_DELETE);

            verify(notificationService).sendMessage(eq(CHAT_ID), eq("Deletion cancelled."));
        }

        @Test
        @DisplayName("SELECTING_REMINDER_DATE → sends \"Date selection cancelled.\"")
        void selectingReminderDate() {
            when(userStateRepository.findById(USER_ID)).thenReturn(Optional.empty());
            when(userStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.cancelWithNotification(USER_ID, CHAT_ID, ConversationState.SELECTING_REMINDER_DATE);

            verify(notificationService).sendMessage(eq(CHAT_ID), eq("Date selection cancelled."));
        }
    }
}
