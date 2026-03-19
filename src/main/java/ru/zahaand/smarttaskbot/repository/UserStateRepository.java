package ru.zahaand.smarttaskbot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.zahaand.smarttaskbot.model.UserState;

/**
 * Repository for per-user conversation state.
 * All access is by PK (telegramUserId); no custom queries needed.
 * {@code save()} acts as an upsert — JPA merges on existing PK.
 */
public interface UserStateRepository extends JpaRepository<UserState, Long> {
}
