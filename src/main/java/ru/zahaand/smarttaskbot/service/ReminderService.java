package ru.zahaand.smarttaskbot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.zahaand.smarttaskbot.model.MessageKey;
import ru.zahaand.smarttaskbot.model.Task;
import ru.zahaand.smarttaskbot.model.User;
import ru.zahaand.smarttaskbot.repository.TaskRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Scheduler that polls for due reminders and delivers them via {@link NotificationService}.
 * Runs every 60 seconds (fixedDelay — next poll starts after the previous one finishes).
 *
 * <p>Lifecycle per task:
 * <ol>
 *   <li>Initial delivery: {@code reminderTime ≤ now}, {@code reminderProcessed = false},
 *       {@code reminderRetryAt IS NULL}, {@code status = ACTIVE}</li>
 *   <li>On success → {@code reminderProcessed = true}</li>
 *   <li>On failure → {@code reminderRetryAt = now + 60s}</li>
 *   <li>Retry: {@code reminderRetryAt ≤ now}, {@code reminderProcessed = false},
 *       {@code status = ACTIVE}</li>
 *   <li>On retry success → {@code reminderProcessed = true}</li>
 *   <li>On retry failure → log WARN + {@code reminderProcessed = true} (discard, per FR-009)</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReminderService {

    private final TaskRepository taskRepository;
    private final NotificationService notificationService;
    private final UserService userService;
    private final MessageService messageService;

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void processDueReminders() {
        Instant now = Instant.now();

        processInitialReminders(now);
        processRetries(now);
    }

    private void processInitialReminders(Instant now) {
        List<Task> tasks = taskRepository.findDueReminders(now);

        for (Task task : tasks) {
            try {
                sendReminderMessage(task);
                task.setReminderProcessed(true);
            } catch (Exception e) {
                log.warn("Failed to send reminder for task #{}: {}", task.getId(), e.getMessage());
                task.setReminderRetryAt(now.plus(60, ChronoUnit.SECONDS));
            }
        }

        taskRepository.saveAll(tasks);
    }

    private void processRetries(Instant now) {
        List<Task> tasks = taskRepository.findDueRetries(now);

        for (Task task : tasks) {
            try {
                sendReminderMessage(task);
                task.setReminderProcessed(true);
            } catch (Exception e) {
                log.warn("Retry failed for task #{}, discarding reminder: {}", task.getId(), e.getMessage());
                task.setReminderProcessed(true);
            }
        }

        taskRepository.saveAll(tasks);
    }

    private void sendReminderMessage(Task task) {
        final User user = userService.findById(task.getUser().getTelegramUserId());
        final String text = messageService.get(MessageKey.REMINDER_NOTIFICATION, user)
                .formatted(task.getText());
        notificationService.sendMessage(user.getTelegramUserId(), text);
    }
}
