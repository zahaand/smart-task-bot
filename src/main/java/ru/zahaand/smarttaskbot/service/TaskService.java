package ru.zahaand.smarttaskbot.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.zahaand.smarttaskbot.model.Task;
import ru.zahaand.smarttaskbot.model.TaskStatus;
import ru.zahaand.smarttaskbot.model.User;
import ru.zahaand.smarttaskbot.repository.TaskRepository;
import ru.zahaand.smarttaskbot.repository.UserRepository;

import java.time.LocalDateTime;

/**
 * Service for managing task lifecycle: creation, retrieval, reminders, and completion.
 * All business validation and domain logic is performed here.
 * Methods are added incrementally per user story.
 */
@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;

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

        Task task = new Task();
        task.setUser(user);
        task.setText(text);
        task.setStatus(TaskStatus.ACTIVE);
        task.setReminderProcessed(false);
        task.setCreatedAt(LocalDateTime.now());

        return taskRepository.save(task);
    }
}
