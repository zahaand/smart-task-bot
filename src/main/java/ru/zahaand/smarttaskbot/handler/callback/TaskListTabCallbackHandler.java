package ru.zahaand.smarttaskbot.handler.callback;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import ru.zahaand.smarttaskbot.dto.TaskDto;
import ru.zahaand.smarttaskbot.model.TaskStatus;
import ru.zahaand.smarttaskbot.service.NotificationService;
import ru.zahaand.smarttaskbot.service.TaskService;

import java.util.List;

/**
 * Handles TASKS_TAB:ACTIVE and TASKS_TAB:COMPLETED callbacks.
 * Edits the existing task-list message in place to show the selected tab.
 */
@Component
@RequiredArgsConstructor
public class TaskListTabCallbackHandler {

    private final TaskService taskService;
    private final NotificationService notificationService;

    public void handle(Update update) {
        final CallbackQuery cq = update.getCallbackQuery();
        final Long userId = cq.getFrom().getId();
        final Long chatId = cq.getMessage().getChatId();
        final Integer messageId = cq.getMessage().getMessageId();
        final String data = cq.getData();

        // If the tapped tab button already shows ✓ it is already active — silent no-op.
        final InlineKeyboardMarkup markup = ((Message) cq.getMessage()).getReplyMarkup();
        if (isAlreadyActiveTab(markup, data)) {
            notificationService.answerCallbackQuery(cq.getId());
            return;
        }

        notificationService.answerCallbackQuery(cq.getId());

        final TaskStatus tab = data.endsWith("COMPLETED") ? TaskStatus.COMPLETED : TaskStatus.ACTIVE;
        final List<TaskDto> tasks = tab == TaskStatus.ACTIVE
                ? taskService.getActiveTasks(userId)
                : taskService.getCompletedTasks(userId);

        notificationService.editTaskList(chatId, messageId, tasks, tab);
    }

    /**
     * Returns true when the button that triggered this callback already carries ✓,
     * meaning the user tapped the tab they are currently on.
     */
    private boolean isAlreadyActiveTab(InlineKeyboardMarkup markup, String callbackData) {
        if (markup == null) {
            return false;
        }
        for (List<InlineKeyboardButton> row : markup.getKeyboard()) {
            for (InlineKeyboardButton btn : row) {
                if (callbackData.equals(btn.getCallbackData()) && btn.getText().contains("✓")) {
                    return true;
                }
            }
        }
        return false;
    }
}
