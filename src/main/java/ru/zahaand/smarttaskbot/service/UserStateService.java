package ru.zahaand.smarttaskbot.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import ru.zahaand.smarttaskbot.dto.ConversationContext;
import ru.zahaand.smarttaskbot.model.ConversationState;
import ru.zahaand.smarttaskbot.model.MessageKey;
import ru.zahaand.smarttaskbot.model.UserState;
import ru.zahaand.smarttaskbot.repository.UserStateRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Manages per-user conversational state for multi-step dialog flows.
 * All state transitions go through this service; handlers never write to
 * {@link UserStateRepository} directly.
 * <p>
 * Управляет состоянием диалога пользователя для многошаговых сценариев.
 * Все переходы состояний проходят через этот сервис — обработчики не пишут в репозиторий напрямую.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserStateService {

    private final UserStateRepository userStateRepository;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final MessageService messageService;

    private static final long STALE_HOURS = 24;

    // ── State reads ──────────────────────────────────────────────────────────

    /**
     * Returns the current conversation state for the user, defaulting to IDLE when no row exists.
     * <p>
     * Возвращает текущее состояние диалога пользователя; при отсутствии записи — IDLE.
     */
    public ConversationState getState(Long userId) {
        return userStateRepository.findById(userId)
                .map(UserState::getState)
                .orElse(ConversationState.IDLE);
    }

    /**
     * Deserializes and returns the conversation context stored for the user, or empty if absent/malformed.
     * Resets state to IDLE on malformed JSON to avoid stuck sessions.
     * <p>
     * Десериализует и возвращает контекст диалога; при отсутствии или повреждении — сбрасывает в IDLE.
     */
    public Optional<ConversationContext> getContext(Long userId) {
        return userStateRepository.findById(userId)
                .map(UserState::getContext)
                .filter(StringUtils::isNotBlank)
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

    /**
     * Transitions the user to {@code state} and clears any stored conversation context.
     * <p>
     * Переводит пользователя в {@code state} и очищает сохранённый контекст диалога.
     */
    public void setState(Long userId, ConversationState state) {
        UserState userState = findOrCreate(userId);
        userState.setState(state);
        userState.setContext(null);
        userState.setUpdatedAt(Instant.now());
        userStateRepository.save(userState);
        log.info("State transition userId={} → {}", userId, state);
    }

    /**
     * Transitions the user to {@code state} and stores a serialized {@link ConversationContext}.
     * <p>
     * Переводит в {@code state} и сохраняет сериализованный {@link ConversationContext}.
     */
    public void setStateWithContext(Long userId, ConversationState state, ConversationContext context) {
        UserState userState = findOrCreate(userId);
        userState.setState(state);
        userState.setContext(serializeContext(context));
        userState.setUpdatedAt(Instant.now());
        userStateRepository.save(userState);
        log.info("State transition userId={} → {} (with context)", userId, state);
    }

    /**
     * Replaces the stored context JSON without changing the current state.
     * <p>
     * Заменяет сохранённый JSON-контекст, не изменяя текущее состояние.
     */
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
     * Resets the user to IDLE and sends an OPERATION_CANCELLED message to the chat.
     * Language falls back to EN — avoids injecting {@link UserService} into this service.
     * <p>
     * Сбрасывает в IDLE и отправляет OPERATION_CANCELLED. Язык — EN (UserService не инжектируется).
     */
    public void cancelWithNotification(Long userId, Long chatId, ConversationState activeState) {
        setState(userId, ConversationState.IDLE);
        // Language resolved as null → EN fallback; avoids injecting UserService here
        notificationService.sendMessage(chatId,
                messageService.get(MessageKey.OPERATION_CANCELLED, (ru.zahaand.smarttaskbot.model.Language) null));
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


}
