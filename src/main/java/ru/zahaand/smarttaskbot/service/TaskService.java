package ru.zahaand.smarttaskbot.service;

import lombok.RequiredArgsConstructor;
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

/**
 * Service for managing task lifecycle: creation, retrieval, reminders, and completion.
 * All business validation and domain logic is performed here.
 * Methods are added incrementally per user story.
 */
@Service
@RequiredArgsConstructor
public class TaskService {

    private static final DateTimeFormatter REMINDER_FORMATTER =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final UserService userService;

    /**
     * Creates a new ACTIVE task for the given user.
     *
     * @param telegramUserId owner of the task
     * @param text           task description (1–500 non-whitespace characters)
     * @return saved {@link Task} with generated id
     * @throws IllegalArgumentException if text is blank or exceeds 500 characters
     */
    public Task createTask(Long telegramUserId, String text) {

        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Please provide task text.\nUsage: /newtask <your task>");
        }

        if (text.length() > 500) {
            throw new IllegalArgumentException("Task text is too long (max 500 characters).");
        }

        User user = userRepository.findById(telegramUserId)
                .orElseThrow(() -> new IllegalStateException("User not found: " + telegramUserId));

        Task task = Task.builder()
                .user(user)
                .text(text)
                .status(TaskStatus.ACTIVE)
                .reminderProcessed(false)
                .createdAt(LocalDateTime.now())
                .build();

        return taskRepository.save(task);
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

    private TaskDto getTaskDto(Task task, ZoneId userZone) {
        String reminderTime = getReminderTime(task, userZone);
        return new TaskDto(task.getId(), task.getText(), reminderTime);
    }

    private String getReminderTime(Task task, ZoneId userZone) {
        return formatReminder(task.getReminderTime(), userZone);
    }

    private String formatReminder(Instant reminderTime, ZoneId userZone) {
        if (reminderTime == null) {
            return null;
        }

        ZonedDateTime userTime = reminderTime.atZone(userZone);

        return REMINDER_FORMATTER.format(userTime);
    }
}
