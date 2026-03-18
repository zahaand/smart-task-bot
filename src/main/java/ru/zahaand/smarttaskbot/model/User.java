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

    @Column(name = "timezone", nullable = false, length = 50)
    private String timezone;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
