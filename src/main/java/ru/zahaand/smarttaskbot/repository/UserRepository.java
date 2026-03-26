package ru.zahaand.smarttaskbot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.zahaand.smarttaskbot.model.User;

/**
 * Repository for {@link User} entities.
 * Primary key is {@code telegramUserId}; no custom queries needed —
 * standard {@code findById}, {@code save}, and {@code deleteById} suffice.
 * <p>
 * Репозиторий для сущностей {@link User}.
 * Первичный ключ — {@code telegramUserId}; пользовательские запросы не требуются —
 * достаточно стандартных {@code findById}, {@code save} и {@code deleteById}.
 */
public interface UserRepository extends JpaRepository<User, Long> {
}
