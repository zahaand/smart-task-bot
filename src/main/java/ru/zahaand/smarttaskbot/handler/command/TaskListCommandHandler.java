package ru.zahaand.smarttaskbot.handler.command;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.zahaand.smarttaskbot.dto.TaskDto;
import ru.zahaand.smarttaskbot.model.MessageKey;
import ru.zahaand.smarttaskbot.model.User;
import ru.zahaand.smarttaskbot.service.MessageService;
import ru.zahaand.smarttaskbot.service.NotificationService;
import ru.zahaand.smarttaskbot.service.TaskService;
import ru.zahaand.smarttaskbot.service.UserService;

import java.util.List;

/**
 * Handles the {@code /tasks} command.
 * Loads the user's active tasks and sends a task list with inline buttons.
 * <p>
 * Обрабатывает команду {@code /tasks}.
 * Загружает активные задачи пользователя и отправляет список с инлайн-кнопками.
 */
@Component
@RequiredArgsConstructor
public class TaskListCommandHandler {

    private final TaskService taskService;
    private final NotificationService notificationService;
    private final UserService userService;
    private final MessageService messageService;

    public void handle(Update update) {
        final Long chatId = update.getMessage().getChatId();
        final Long telegramUserId = update.getMessage().getFrom().getId();
        final User user = userService.findById(telegramUserId);

        final List<TaskDto> tasks = taskService.getActiveTasks(telegramUserId);

        if (tasks.isEmpty()) {
            notificationService.sendMessage(chatId, messageService.get(MessageKey.NO_ACTIVE_TASKS, user));
            return;
        }

        final StringBuilder builder = new StringBuilder(
                messageService.get(MessageKey.TASKS_ACTIVE_HEADER, user) + ":\n");
        for (TaskDto task : tasks) {
            builder.append("\n#")
                    .append(task.getId())
                    .append(" ")
                    .append(task.getText());
            if (task.getReminderTime() != null) {
                builder.append(messageService.get(MessageKey.REMINDER_ICON, user))
                        .append(task.getReminderTime());
            }
        }

        notificationService.sendMessage(chatId, builder.toString());
    }
}
