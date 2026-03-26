package ru.zahaand.smarttaskbot.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import ru.zahaand.smarttaskbot.dto.TaskDto;
import ru.zahaand.smarttaskbot.model.*;
import ru.zahaand.smarttaskbot.repository.TaskRepository;
import ru.zahaand.smarttaskbot.repository.UserRepository;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Service for managing task lifecycle: creation, retrieval, reminders, completion, and bulk deletion.
 * All business validation and domain logic is performed here; repository calls are never made from handlers.
 * <p>
 * Сервис управления жизненным циклом задач: создание, получение, напоминания, завершение и массовое удаление.
 * Вся бизнес-логика находится здесь; обработчики не обращаются к репозиторию напрямую.
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

        if (StringUtils.isBlank(text)) {
            log.warn("Blank task text from userId={}", telegramUserId);
            throw new BotException(MessageKey.TASK_TEXT_EMPTY);
        }

        if (StringUtils.length(text) > 500) {
            log.warn("Task text too long ({} chars) from userId={}", text.length(), telegramUserId);
            throw new BotException(MessageKey.TASK_TEXT_TOO_LONG);
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
     * Returns all COMPLETED tasks for the given user as DTOs.
     *
     * @param telegramUserId owner of the tasks
     * @return list of {@link TaskDto}, empty if no completed tasks
     */
    public List<TaskDto> getCompletedTasks(Long telegramUserId) {
        ZoneId userZone = ZoneId.of(userService.getTimezone(telegramUserId));

        return taskRepository.findByUserTelegramUserIdAndStatus(telegramUserId, TaskStatus.COMPLETED)
                .stream()
                .map(task -> getTaskDto(task, userZone))
                .toList();
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
                    return new BotException(MessageKey.TASK_NOT_FOUND, taskId);
                });

        if (task.getStatus() == TaskStatus.COMPLETED) {
            log.warn("Reminder on completed task #{} by userId={}", taskId, telegramUserId);
            throw new BotException(MessageKey.CANNOT_REMIND_COMPLETED);
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
     * Sets a reminder on an existing ACTIVE task using a date and time from the inline calendar flow.
     * The date and time are in the user's local timezone and are converted to UTC before persistence.
     *
     * @param telegramUserId owner of the task
     * @param taskId         ID of the task to remind
     * @param date           date chosen from the inline calendar
     * @param time           time entered as free text and parsed by {@link TimeParserUtils}
     * @return {@link TaskDto} with the formatted reminder time for the confirmation reply
     * @throws NoSuchElementException   if the task does not exist or belongs to another user
     * @throws IllegalArgumentException if the task is already COMPLETED
     */
    public TaskDto setReminderFromCalendar(Long telegramUserId, Long taskId,
                                           LocalDate date, LocalTime time) {
        final Task task = taskRepository.findByIdAndUserTelegramUserId(taskId, telegramUserId)
                .orElseThrow(() -> {
                    log.error("Task #{} not found for userId={}", taskId, telegramUserId);
                    return new BotException(MessageKey.TASK_NOT_FOUND, taskId);
                });

        if (task.getStatus() == TaskStatus.COMPLETED) {
            log.warn("Reminder on completed task #{} by userId={}", taskId, telegramUserId);
            throw new BotException(MessageKey.CANNOT_REMIND_COMPLETED);
        }

        final ZoneId userZone = ZoneId.of(userService.getTimezone(telegramUserId));
        final Instant reminderInstant = LocalDateTime.of(date, time).atZone(userZone).toInstant();

        task.setReminderTime(reminderInstant);
        task.setReminderProcessed(false);
        task.setReminderRetryAt(null);

        taskRepository.save(task);
        log.info("Reminder set via calendar: taskId={}, userId={}", taskId, telegramUserId);

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
                    return new BotException(MessageKey.TASK_NOT_FOUND, taskId);
                });

        task.setStatus(TaskStatus.COMPLETED);
        Task saved = taskRepository.save(task);

        log.info("Task completed: id={}, userId={}", saved.getId(), telegramUserId);
        return new TaskDto(saved.getId(), saved.getText(), null);
    }

    /**
     * Returns a single task as a DTO for the given task ID and owner.
     * <p>
     * Возвращает задачу как DTO по ID задачи и владельцу.
     *
     * @param telegramUserId owner of the task
     * @param taskId         ID of the task
     * @return {@link TaskDto} with formatted reminder time in the user's timezone
     * @throws BotException if the task does not exist or belongs to another user
     */
    public TaskDto getTask(Long telegramUserId, Long taskId) {
        final Task task = taskRepository.findByIdAndUserTelegramUserId(taskId, telegramUserId)
                .orElseThrow(() -> new BotException(MessageKey.TASK_NOT_FOUND, taskId));
        final ZoneId userZone = ZoneId.of(userService.getTimezone(telegramUserId));
        return getTaskDto(task, userZone);
    }

    /**
     * Returns the task text for the given task ID and owner.
     *
     * @param telegramUserId owner of the task
     * @param taskId         ID of the task
     * @return task text
     * @throws BotException if the task does not exist or belongs to another user
     */
    public String getTaskText(Long telegramUserId, Long taskId) {
        return taskRepository.findByIdAndUserTelegramUserId(taskId, telegramUserId)
                .map(Task::getText)
                .orElseThrow(() -> new BotException(MessageKey.TASK_NOT_FOUND, taskId));
    }

    /**
     * Returns the count of COMPLETED tasks for the given user.
     *
     * @param telegramUserId owner of the tasks
     * @return number of completed tasks
     */
    public long countCompleted(Long telegramUserId) {
        return taskRepository.countByUserTelegramUserIdAndStatus(telegramUserId, TaskStatus.COMPLETED);
    }

    /**
     * Deletes all COMPLETED tasks for the given user in a single bulk operation.
     *
     * @param telegramUserId owner of the tasks
     * @return number of tasks deleted
     */
    @Transactional
    public int deleteAllCompleted(Long telegramUserId) {
        int deleted = taskRepository.deleteAllByUserTelegramUserIdAndStatus(telegramUserId, TaskStatus.COMPLETED);
        log.info("Deleted all completed tasks: count={}, userId={}", deleted, telegramUserId);
        return deleted;
    }

    /**
     * Deletes a task by ID and owner. Returns the number of rows deleted (0 or 1).
     *
     * @param telegramUserId owner of the task
     * @param taskId         ID of the task to delete
     * @return 1 if deleted, 0 if the task did not exist or belongs to another user
     */
    @Transactional
    public int deleteTask(Long telegramUserId, Long taskId) {
        int deleted = taskRepository.deleteByIdAndUserTelegramUserId(taskId, telegramUserId);
        log.info("Task delete: taskId={}, userId={}, rowsAffected={}", taskId, telegramUserId, deleted);
        return deleted;
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
