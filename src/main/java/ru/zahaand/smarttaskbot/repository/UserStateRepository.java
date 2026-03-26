package ru.zahaand.smarttaskbot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.zahaand.smarttaskbot.model.UserState;

/**
 * Repository for per-user conversation state.
 * All access is by PK (telegramUserId); no custom queries needed.
 * {@code save()} acts as an upsert — JPA merges on existing PK.
 * <p>
 * Репозиторий для хранения состояния диалога пользователя.
 * Доступ по PK (telegramUserId); пользовательские запросы не требуются.
 * {@code save()} работает как upsert — JPA выполняет merge по существующему PK.
 */
public interface UserStateRepository extends JpaRepository<UserState, Long> {
}
