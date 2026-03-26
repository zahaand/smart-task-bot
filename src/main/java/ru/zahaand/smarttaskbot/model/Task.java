package ru.zahaand.smarttaskbot.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * JPA entity representing a user task.
 * Each task belongs to a {@link User}, has a status (ACTIVE/COMPLETED),
 * an optional reminder time stored as UTC {@link Instant}, and a creation timestamp.
 * <p>
 * JPA-сущность, представляющая задачу пользователя.
 * Каждая задача принадлежит {@link User}, имеет статус (ACTIVE/COMPLETED),
 * необязательное время напоминания в UTC {@link Instant} и временную метку создания.
 */
@Entity
@Table(name = "tasks")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "telegram_user_id", nullable = false)
    private User user;

    @Column(name = "text", nullable = false, length = 500)
    private String text;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TaskStatus status;

    @Column(name = "reminder_time", nullable = true)
    private Instant reminderTime;

    @Column(name = "reminder_processed", nullable = false)
    private boolean reminderProcessed;

    @Column(name = "reminder_retry_at", nullable = true)
    private Instant reminderRetryAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (status == null) {
            status = TaskStatus.ACTIVE;
        }
        reminderProcessed = false;
        createdAt = LocalDateTime.now();
    }
}
