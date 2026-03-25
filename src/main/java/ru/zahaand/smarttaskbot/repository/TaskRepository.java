package ru.zahaand.smarttaskbot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.zahaand.smarttaskbot.model.Task;
import ru.zahaand.smarttaskbot.model.TaskStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link Task} entities.
 * Every query MUST include a {@code telegramUserId} predicate to enforce user-data isolation.
 * <p>
 * Репозиторий JPA для сущностей {@link Task}.
 * Каждый запрос ОБЯЗАН включать предикат {@code telegramUserId} для изоляции данных пользователей.
 */
public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findByUserTelegramUserIdAndStatus(Long telegramUserId, TaskStatus status);

    Optional<Task> findByIdAndUserTelegramUserId(Long id, Long telegramUserId);

    @Modifying
    @Query("DELETE FROM Task t WHERE t.id = :taskId AND t.user.telegramUserId = :telegramUserId")
    int deleteByIdAndUserTelegramUserId(@Param("taskId") Long taskId,
                                        @Param("telegramUserId") Long telegramUserId);

    /**
     * Returns the count of tasks with the given status that belong to the given user.
     * <p>
     * Возвращает количество задач с указанным статусом для данного пользователя.
     */
    long countByUserTelegramUserIdAndStatus(Long telegramUserId, TaskStatus status);

    /**
     * Bulk-deletes all tasks with the given status that belong to the given user.
     * Returns the number of rows deleted.
     * <p>
     * Массово удаляет все задачи с указанным статусом для данного пользователя. Возвращает число удалённых строк.
     */
    @Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("DELETE FROM Task t WHERE t.user.telegramUserId = :telegramUserId AND t.status = :status")
    int deleteAllByUserTelegramUserIdAndStatus(@Param("telegramUserId") Long telegramUserId,
                                               @Param("status") TaskStatus status);

    @Query("""
            SELECT t FROM Task t
            WHERE t.reminderProcessed = false
              AND t.status = 'ACTIVE'
              AND t.reminderTime IS NOT NULL
              AND t.reminderTime <= :now
              AND t.reminderRetryAt IS NULL
            """)
    List<Task> findDueReminders(@Param("now") Instant now);

    @Query("""
            SELECT t FROM Task t
            WHERE t.reminderProcessed = false
              AND t.status = 'ACTIVE'
              AND t.reminderRetryAt IS NOT NULL
              AND t.reminderRetryAt <= :now
            """)
    List<Task> findDueRetries(@Param("now") Instant now);
}
