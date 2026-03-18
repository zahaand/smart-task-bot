package ru.zahaand.smarttaskbot.handler.command;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.zahaand.smarttaskbot.dto.TaskDto;
import ru.zahaand.smarttaskbot.service.NotificationService;
import ru.zahaand.smarttaskbot.service.TaskService;

import java.util.List;

/**
 * Handles the {@code /tasks} command.
 * Fetches all ACTIVE tasks for the user and formats them into a numbered list.
 * Reminder times are shown in the user's local timezone when set.
 */
@Component
@RequiredArgsConstructor
public class TaskListCommandHandler {

    private final TaskService taskService;
    private final NotificationService notificationService;

    public void handle(Update update) {
        Long chatId = update.getMessage().getChatId();
        Long telegramUserId = update.getMessage().getFrom().getId();

        List<TaskDto> tasks = taskService.getActiveTasks(telegramUserId);

        if (tasks.isEmpty()) {
            notificationService.sendMessage(chatId,
                    "You have no active tasks.\nUse /newtask to create one.");
            return;
        }

        StringBuilder builder = new StringBuilder("Your active tasks:\n");
        for (TaskDto task : tasks) {
            builder.append("\n#")
                    .append(task.getId())
                    .append(" ")
                    .append(task.getText());

            if (task.getReminderTime() != null) {
                builder.append("  ⏰ ").append(task.getReminderTime());
            }
        }

        notificationService.sendMessage(chatId, builder.toString());
    }
}
