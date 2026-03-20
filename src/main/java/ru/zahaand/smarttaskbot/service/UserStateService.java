package ru.zahaand.smarttaskbot.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.zahaand.smarttaskbot.dto.ConversationContext;
import ru.zahaand.smarttaskbot.model.ConversationState;
import ru.zahaand.smarttaskbot.model.UserState;
import ru.zahaand.smarttaskbot.repository.UserStateRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Manages per-user conversational state for multi-step dialog flows.
 * All state transitions go through this service; handlers never write to
 * {@link UserStateRepository} directly.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserStateService {

    private final UserStateRepository userStateRepository;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    private static final long STALE_HOURS = 24;

    // ── State reads ──────────────────────────────────────────────────────────

    public ConversationState getState(Long userId) {
        return userStateRepository.findById(userId)
                .map(UserState::getState)
                .orElse(ConversationState.IDLE);
    }

    public Optional<ConversationContext> getContext(Long userId) {
        return userStateRepository.findById(userId)
                .map(UserState::getContext)
                .filter(json -> json != null && !json.isBlank())
                .flatMap(json -> {
                    try {
                        return Optional.of(objectMapper.readValue(json, ConversationContext.class));
                    } catch (JsonProcessingException e) {
                        log.error("Malformed conversation context for userId={}, resetting to IDLE. json={}",
                                userId, json, e);
                        setState(userId, ConversationState.IDLE);
                        return Optional.empty();
                    }
                });
    }

    // ── State writes ─────────────────────────────────────────────────────────

    public void setState(Long userId, ConversationState state) {
        UserState userState = findOrCreate(userId);
        userState.setState(state);
        userState.setContext(null);
        userState.setUpdatedAt(Instant.now());
        userStateRepository.save(userState);
        log.info("State transition userId={} → {}", userId, state);
    }

    public void setStateWithContext(Long userId, ConversationState state, ConversationContext context) {
        UserState userState = findOrCreate(userId);
        userState.setState(state);
        userState.setContext(serializeContext(context));
        userState.setUpdatedAt(Instant.now());
        userStateRepository.save(userState);
        log.info("State transition userId={} → {} (with context)", userId, state);
    }

    public void updateContext(Long userId, ConversationContext context) {
        UserState userState = findOrCreate(userId);
        userState.setContext(serializeContext(context));
        userState.setUpdatedAt(Instant.now());
        userStateRepository.save(userState);
    }

    // ── Stale-state lazy reset ────────────────────────────────────────────────

    /**
     * Resets state to IDLE if the last transition was more than 24 hours ago.
     * Called at the top of every incoming event routing — no scheduled job needed.
     */
    public void resetIfStale(Long userId) {
        userStateRepository.findById(userId).ifPresent(userState -> {
            if (userState.getState() != ConversationState.IDLE
                    && userState.getUpdatedAt() != null
                    && userState.getUpdatedAt().isBefore(Instant.now().minus(STALE_HOURS, ChronoUnit.HOURS))) {
                log.info("Stale state reset userId={} (was {})", userId, userState.getState());
                userState.setState(ConversationState.IDLE);
                userState.setContext(null);
                userState.setUpdatedAt(Instant.now());
                userStateRepository.save(userState);
            }
        });
    }

    // ── Cancel with notification ──────────────────────────────────────────────

    /**
     * Resets the user to IDLE and sends a context-appropriate cancellation message.
     */
    public void cancelWithNotification(Long userId, Long chatId, ConversationState activeState) {
        setState(userId, ConversationState.IDLE);
        notificationService.sendMessage(chatId, cancelMessage(activeState));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private UserState findOrCreate(Long userId) {
        return userStateRepository.findById(userId)
                .orElse(new UserState(userId));
    }

    private String serializeContext(ConversationContext context) {
        try {
            return objectMapper.writeValueAsString(context);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize ConversationContext for userId — using null context", e);
            return null;
        }
    }

    private static String cancelMessage(ConversationState state) {
        return switch (state) {
            case CREATING_TASK -> "Task creation cancelled.";
            case ENTERING_REMINDER_TIME -> "Reminder setup cancelled.";
            case CONFIRMING_DELETE -> "Deletion cancelled.";
            case SELECTING_REMINDER_DATE -> "Date selection cancelled.";
            default -> "Action cancelled.";
        };
    }
}
