package ru.zahaand.smarttaskbot.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Table(name = "tasks")
@Getter
@Setter
@NoArgsConstructor
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
}
