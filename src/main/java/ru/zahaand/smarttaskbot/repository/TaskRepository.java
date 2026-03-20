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

public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findByUserTelegramUserIdAndStatus(Long telegramUserId, TaskStatus status);

    Optional<Task> findByIdAndUserTelegramUserId(Long id, Long telegramUserId);

    @Modifying
    @Query("DELETE FROM Task t WHERE t.id = :taskId AND t.user.telegramUserId = :telegramUserId")
    int deleteByIdAndUserTelegramUserId(@Param("taskId") Long taskId,
                                        @Param("telegramUserId") Long telegramUserId);

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
