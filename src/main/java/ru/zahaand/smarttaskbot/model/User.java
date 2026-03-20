package ru.zahaand.smarttaskbot.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @Column(name = "telegram_user_id", nullable = false)
    private Long telegramUserId;

    @Column(name = "username", nullable = true)
    private String username;

    // nullable during two-step registration (set on language callback, before timezone)
    @Column(name = "language", nullable = true, length = 2)
    @Enumerated(EnumType.STRING)
    private Language language;

    // nullable during two-step registration (set on timezone callback)
    @Column(name = "timezone", nullable = true, length = 50)
    private String timezone;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
