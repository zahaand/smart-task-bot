package ru.zahaand.smarttaskbot.handler.text;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.zahaand.smarttaskbot.dto.TaskDto;
import ru.zahaand.smarttaskbot.model.Language;
import ru.zahaand.smarttaskbot.model.TaskStatus;
import ru.zahaand.smarttaskbot.service.NotificationService;
import ru.zahaand.smarttaskbot.service.TaskService;
import ru.zahaand.smarttaskbot.service.UserService;

import java.util.List;

/**
 * Handles "📋 My Tasks" persistent menu button tap.
 * Opens the active task list; the reminder flow starts from a task's inline button.
 */
@Component
@RequiredArgsConstructor
public class TaskListButtonHandler {

    private final TaskService taskService;
    private final NotificationService notificationService;
    private final UserService userService;

    public void handle(Update update) {
        final Long userId = update.getMessage().getFrom().getId();
        final Long chatId = update.getMessage().getChatId();

        final Language language = resolveLanguage(userId);
        final List<TaskDto> tasks = taskService.getActiveTasks(userId);
        notificationService.sendTaskList(chatId, tasks, TaskStatus.ACTIVE, language);
    }

    private Language resolveLanguage(Long userId) {
        try {
            return userService.findById(userId).getLanguage();
        } catch (Exception e) {
            return null;
        }
    }
}
