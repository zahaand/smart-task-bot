package ru.zahaand.smarttaskbot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.zahaand.smarttaskbot.dto.TaskDto;
import ru.zahaand.smarttaskbot.model.Task;
import ru.zahaand.smarttaskbot.model.TaskStatus;
import ru.zahaand.smarttaskbot.model.User;
import ru.zahaand.smarttaskbot.repository.TaskRepository;
import ru.zahaand.smarttaskbot.repository.UserRepository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Service for managing task lifecycle: creation, retrieval, reminders, and completion.
 * All business validation and domain logic is performed here.
 * Methods are added incrementally per user story.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final UserService userService;

    private static final DateTimeFormatter REMINDER_FORMATTER =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    /**
     * Creates a new ACTIVE task for the given user.
     *
     * @param telegramUserId owner of the task
     * @param text           task description (1–500 non-whitespace characters)
     * @return {@link TaskDto} with the assigned id and text (no reminder on creation)
     * @throws IllegalArgumentException if text is blank or exceeds 500 characters
     */
    public TaskDto createTask(Long telegramUserId, String text) {

        if (text == null || text.isBlank()) {
            log.warn("Blank task text from userId={}", telegramUserId);
            throw new IllegalArgumentException("Please provide task text.\nUsage: /newtask <your task>");
        }

        if (text.length() > 500) {
            log.warn("Task text too long ({} chars) from userId={}", text.length(), telegramUserId);
            throw new IllegalArgumentException("Task text is too long (max 500 characters).");
        }

        User user = userRepository.findById(telegramUserId)
                .orElseThrow(() -> {
                    log.error("User not found during task creation: userId={}", telegramUserId);
                    return new IllegalStateException("User not found: " + telegramUserId);
                });

        Task task = Task.builder()
                .user(user)
                .text(text)
                .status(TaskStatus.ACTIVE)
                .reminderProcessed(false)
                .createdAt(LocalDateTime.now())
                .build();

        Task saved = taskRepository.save(task);
        log.info("Task created: id={}, userId={}", saved.getId(), telegramUserId);
        return new TaskDto(saved.getId(), saved.getText(), null);
    }

    /**
     * Returns all ACTIVE tasks for the given user as DTOs with reminder times
     * pre-formatted in the user's local timezone.
     *
     * @param telegramUserId owner of the tasks
     * @return list of {@link TaskDto}, empty if no active tasks
     */
    public List<TaskDto> getActiveTasks(Long telegramUserId) {
        ZoneId userZone = ZoneId.of(userService.getTimezone(telegramUserId));

        return taskRepository.findByUserTelegramUserIdAndStatus(telegramUserId, TaskStatus.ACTIVE)
                .stream()
                .map(task -> getTaskDto(task, userZone))
                .toList();
    }

    /**
     * Sets a reminder on an existing ACTIVE task.
     * Parses {@code dateTimeInput} in the user's timezone and stores it as UTC.
     *
     * @param telegramUserId owner of the task
     * @param taskId         ID of the task to remind
     * @param dateTimeInput  datetime string in {@code dd.MM.yyyy HH:mm} format (user's local time)
     * @return {@link TaskDto} with the formatted reminder time for the confirmation reply
     * @throws NoSuchElementException   if the task does not exist or belongs to another user
     * @throws IllegalArgumentException if the task is already COMPLETED
     * @throws java.time.format.DateTimeParseException if {@code dateTimeInput} cannot be parsed
     */
    public TaskDto setReminder(Long telegramUserId, Long taskId, String dateTimeInput) {
        Task task = taskRepository.findByIdAndUserTelegramUserId(taskId, telegramUserId)
                .orElseThrow(() -> {
                    log.error("Task #{} not found for userId={}", taskId, telegramUserId);
                    return new NoSuchElementException("Task #%d not found.".formatted(taskId));
                });

        if (task.getStatus() == TaskStatus.COMPLETED) {
            log.warn("Reminder on completed task #{} by userId={}", taskId, telegramUserId);
            throw new IllegalArgumentException("Cannot set a reminder on a completed task.");
        }

        ZoneId userZone = ZoneId.of(userService.getTimezone(telegramUserId));
        LocalDateTime localDateTime = LocalDateTime.parse(dateTimeInput, REMINDER_FORMATTER);
        Instant reminderInstant = localDateTime.atZone(userZone).toInstant();

        task.setReminderTime(reminderInstant);
        task.setReminderProcessed(false);
        task.setReminderRetryAt(null);

        taskRepository.save(task);
        log.info("Reminder set: taskId={}, userId={}", taskId, telegramUserId);

        return getTaskDto(task, userZone);
    }

    /**
     * Marks a task as COMPLETED.
     *
     * @param telegramUserId owner of the task
     * @param taskId         ID of the task to complete
     * @return {@link TaskDto} with the task id and text for the confirmation reply
     * @throws NoSuchElementException if the task does not exist or belongs to another user
     */
    public TaskDto completeTask(Long telegramUserId, Long taskId) {
        Task task = taskRepository.findByIdAndUserTelegramUserId(taskId, telegramUserId)
                .orElseThrow(() -> {
                    log.error("Task #{} not found for userId={}", taskId, telegramUserId);
                    return new NoSuchElementException("Task #%d not found.".formatted(taskId));
                });

        task.setStatus(TaskStatus.COMPLETED);
        Task saved = taskRepository.save(task);

        log.info("Task completed: id={}, userId={}", saved.getId(), telegramUserId);
        return new TaskDto(saved.getId(), saved.getText(), null);
    }

    private TaskDto getTaskDto(Task task, ZoneId userZone) {
        String reminderTime = formatReminder(task.getReminderTime(), userZone);
        return new TaskDto(task.getId(), task.getText(), reminderTime);
    }

    private String formatReminder(Instant reminderTime, ZoneId userZone) {
        if (reminderTime == null) {
            return null;
        }

        ZonedDateTime userTime = reminderTime.atZone(userZone);

        return REMINDER_FORMATTER.format(userTime);
    }
}
