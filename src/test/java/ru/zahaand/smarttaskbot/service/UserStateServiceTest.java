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
import ru.zahaand.smarttaskbot.dto.ConversationContextDto;
import ru.zahaand.smarttaskbot.model.ConversationState;
import ru.zahaand.smarttaskbot.model.Language;
import ru.zahaand.smarttaskbot.model.MessageKey;
import ru.zahaand.smarttaskbot.model.UserState;
import ru.zahaand.smarttaskbot.repository.UserStateRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserStateServiceTest {

    @Mock
    private UserStateRepository userStateRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private MessageService messageService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    private UserStateService service;

    private static final Long USER_ID = 42L;
    private static final Long CHAT_ID = 100L;

    @BeforeEach
    void setUp() {
        service = new UserStateService(userStateRepository, notificationService, objectMapper, messageService);
    }

    // ── getState ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getState()")
    class GetState {

        @DisplayName("returns IDLE when no row exists for user")
        @Test
        void returnsIdleWhenNoRow() {
            when(userStateRepository.findById(USER_ID)).thenReturn(Optional.empty());

            assertThat(service.getState(USER_ID)).isEqualTo(ConversationState.IDLE);
        }

        @DisplayName("returns stored state when row exists")
        @Test
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

        @DisplayName("upserts state and clears context")
        @Test
        void upsertsStateAndClearsContext() {
            when(userStateRepository.findById(USER_ID)).thenReturn(Optional.empty());
            when(userStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.setState(USER_ID, ConversationState.CREATING_TASK);

            ArgumentCaptor<UserState> captor = ArgumentCaptor.forClass(UserState.class);
            verify(userStateRepository).save(captor.capture());
            assertThat(captor.getValue().getState()).isEqualTo(ConversationState.CREATING_TASK);
            assertThat(captor.getValue().getContext()).isNull();
        }

        @DisplayName("overwrites existing row")
        @Test
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

        @DisplayName("serializes ConversationContextDto to JSON and saves")
        @Test
        void serializesContext() {
            when(userStateRepository.findById(USER_ID)).thenReturn(Optional.empty());
            when(userStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ConversationContextDto ctx = ConversationContextDto.builder().taskId(7L).build();
            service.setStateWithContext(USER_ID, ConversationState.CONFIRMING_DELETE, ctx);

            ArgumentCaptor<UserState> captor = ArgumentCaptor.forClass(UserState.class);
            verify(userStateRepository).save(captor.capture());
            assertThat(captor.getValue().getState()).isEqualTo(ConversationState.CONFIRMING_DELETE);
            assertThat(captor.getValue().getContext()).contains("\"taskId\":7");
        }
    }

    // ── updateContext ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateContext()")
    class UpdateContext {

        @DisplayName("serializes context JSON and saves")
        @Test
        void serializesContextAndSaves() {
            when(userStateRepository.findById(USER_ID)).thenReturn(Optional.empty());
            when(userStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ConversationContextDto ctx = ConversationContextDto.builder().taskId(3L).date("2026-06-01").build();
            service.updateContext(USER_ID, ctx);

            ArgumentCaptor<UserState> captor = ArgumentCaptor.forClass(UserState.class);
            verify(userStateRepository).save(captor.capture());
            assertThat(captor.getValue().getContext()).contains("\"taskId\":3");
            assertThat(captor.getValue().getContext()).contains("\"date\":\"2026-06-01\"");
        }

        @DisplayName("refreshes updatedAt timestamp")
        @Test
        void refreshesUpdatedAt() {
            Instant before = Instant.now().minusSeconds(1);
            when(userStateRepository.findById(USER_ID)).thenReturn(Optional.empty());
            when(userStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.updateContext(USER_ID, ConversationContextDto.builder().build());

            ArgumentCaptor<UserState> captor = ArgumentCaptor.forClass(UserState.class);
            verify(userStateRepository).save(captor.capture());
            assertThat(captor.getValue().getUpdatedAt()).isAfter(before);
        }
    }

    // ── getContext ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getContext()")
    class GetContext {

        @DisplayName("returns empty when no row exists")
        @Test
        void returnsEmptyWhenNoRow() {
            when(userStateRepository.findById(USER_ID)).thenReturn(Optional.empty());

            assertThat(service.getContext(USER_ID)).isEmpty();
        }

        @DisplayName("returns empty and resets state to IDLE when context JSON is malformed")
        @Test
        void resetsToIdleOnMalformedJson() {
            UserState stored = new UserState(USER_ID);
            stored.setState(ConversationState.CONFIRMING_DELETE);
            stored.setContext("{not valid json}");
            when(userStateRepository.findById(USER_ID)).thenReturn(Optional.of(stored));
            when(userStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Optional<ConversationContextDto> result = service.getContext(USER_ID);

            assertThat(result).isEmpty();
            verify(userStateRepository, atLeastOnce()).save(any());
        }

        @DisplayName("deserializes stored JSON into ConversationContextDto")
        @Test
        void deserializesContext() {
            UserState stored = new UserState(USER_ID);
            stored.setContext("{\"taskId\":5,\"date\":\"2026-05-20\"}");
            when(userStateRepository.findById(USER_ID)).thenReturn(Optional.of(stored));

            Optional<ConversationContextDto> result = service.getContext(USER_ID);

            assertThat(result).isPresent();
            assertThat(result.get().getTaskId()).isEqualTo(5L);
            assertThat(result.get().getDate()).isEqualTo("2026-05-20");
        }
    }

    // ── resetIfStale ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("resetIfStale()")
    class ResetIfStale {

        @DisplayName("resets to IDLE when updatedAt is more than 24h ago")
        @Test
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

        @DisplayName("does NOT reset when updatedAt is less than 24h ago")
        @Test
        void doesNotResetFreshState() {
            UserState stored = new UserState(USER_ID);
            stored.setState(ConversationState.CREATING_TASK);
            stored.setUpdatedAt(Instant.now().minus(1, ChronoUnit.HOURS));
            when(userStateRepository.findById(USER_ID)).thenReturn(Optional.of(stored));

            service.resetIfStale(USER_ID);

            verify(userStateRepository, never()).save(any());
        }

        @DisplayName("does NOT reset when state is already IDLE")
        @Test
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

        @DisplayName("CREATING_TASK → sends \"Operation cancelled.\"")
        @Test
        void creatingTask() {
            when(userStateRepository.findById(USER_ID)).thenReturn(Optional.empty());
            when(userStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(messageService.get(any(MessageKey.class), nullable(Language.class))).thenReturn("Operation cancelled.");

            service.cancelWithNotification(USER_ID, CHAT_ID, ConversationState.CREATING_TASK);

            verify(notificationService).sendMessage(eq(CHAT_ID), eq("Operation cancelled."));
        }

        @DisplayName("ENTERING_REMINDER_TIME → sends \"Operation cancelled.\"")
        @Test
        void enteringReminderTime() {
            when(userStateRepository.findById(USER_ID)).thenReturn(Optional.empty());
            when(userStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(messageService.get(any(MessageKey.class), nullable(Language.class))).thenReturn("Operation cancelled.");

            service.cancelWithNotification(USER_ID, CHAT_ID, ConversationState.ENTERING_REMINDER_TIME);

            verify(notificationService).sendMessage(eq(CHAT_ID), eq("Operation cancelled."));
        }

        @DisplayName("CONFIRMING_DELETE → sends \"Operation cancelled.\"")
        @Test
        void confirmingDelete() {
            when(userStateRepository.findById(USER_ID)).thenReturn(Optional.empty());
            when(userStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(messageService.get(any(MessageKey.class), nullable(Language.class))).thenReturn("Operation cancelled.");

            service.cancelWithNotification(USER_ID, CHAT_ID, ConversationState.CONFIRMING_DELETE);

            verify(notificationService).sendMessage(eq(CHAT_ID), eq("Operation cancelled."));
        }

        @DisplayName("SELECTING_REMINDER_DATE → sends \"Operation cancelled.\"")
        @Test
        void selectingReminderDate() {
            when(userStateRepository.findById(USER_ID)).thenReturn(Optional.empty());
            when(userStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(messageService.get(any(MessageKey.class), nullable(Language.class))).thenReturn("Operation cancelled.");

            service.cancelWithNotification(USER_ID, CHAT_ID, ConversationState.SELECTING_REMINDER_DATE);

            verify(notificationService).sendMessage(eq(CHAT_ID), eq("Operation cancelled."));
        }
    }
}
