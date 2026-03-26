package ru.zahaand.smarttaskbot.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Tracks the current conversational state for a registered user.
 * One row per user; upserted on every state transition.
 * <p>
 * JPA note: fields are NOT final — JPA requires a no-arg constructor
 * and mutable managed state (Constitution §VIII JPA entity exception).
 */
@Entity
@Table(name = "user_states")
@Getter
@Setter
@NoArgsConstructor
public class UserState {

    @Id
    @Column(name = "telegram_user_id", nullable = false)
    private Long telegramUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 50)
    private ConversationState state = ConversationState.IDLE;

    /**
     * JSON-serialized {@link ru.zahaand.smarttaskbot.dto.ConversationContextDto}.
     * NULL when state is IDLE.
     */
    @Column(name = "context", columnDefinition = "TEXT")
    private String context;

    /**
     * UTC timestamp of last state transition; used for stale-state detection (>24h → reset).
     */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UserState(Long telegramUserId) {
        this.telegramUserId = telegramUserId;
        this.updatedAt = Instant.now();
    }
}
