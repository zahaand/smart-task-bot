package ru.zahaand.smarttaskbot.service;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import ru.zahaand.smarttaskbot.config.BotConstants;
import ru.zahaand.smarttaskbot.dto.TaskDto;
import ru.zahaand.smarttaskbot.model.TaskStatus;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the inline keyboard for the task list message.
 * Each task row gets action buttons; on the Completed tab a "🗑 Delete All" row is
 * appended when tasks are present; a tab row is always appended last.
 * Accepts a list already truncated to at most 20 items by the caller.
 * <p>
 * Строит инлайн-клавиатуру для списка задач. На вкладке Completed добавляет кнопку
 * «🗑 Delete All» при наличии задач; последней строкой всегда идёт переключатель вкладок.
 */
@Component
public class TaskListKeyboardBuilder {

    /**
     * Builds the complete inline keyboard for the given task list and active tab.
     *
     * @param tasks     task DTOs to render (caller must truncate to ≤20 items)
     * @param activeTab currently selected tab — determines action buttons and Delete All visibility
     * @return fully assembled {@link InlineKeyboardMarkup}
     * <p>
     * Строит полную инлайн-клавиатуру для списка задач и активной вкладки.
     */
    public InlineKeyboardMarkup buildKeyboard(List<TaskDto> tasks, TaskStatus activeTab) {
        final List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (TaskDto task : tasks) {
            // Text row: task title as a non-interactive label
            rows.add(buildTaskTextRow(task));
            // Action row: operation buttons
            rows.add(buildTaskActionRow(task, activeTab));
        }

        if (activeTab == TaskStatus.COMPLETED && !tasks.isEmpty()) {
            rows.add(buildDeleteAllRow());
        }

        rows.add(buildTabRow(activeTab));

        final InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(rows);
        return markup;
    }

    private List<InlineKeyboardButton> buildTaskTextRow(TaskDto task) {
        String label = "#" + task.getId() + " " + task.getText();
        if (task.getReminderTime() != null) {
            label += " [⏰ " + task.getReminderTime() + "]";
        }
        return List.of(button(label, BotConstants.CB_NO_OP));
    }

    private List<InlineKeyboardButton> buildTaskActionRow(TaskDto task, TaskStatus activeTab) {
        final List<InlineKeyboardButton> row = new ArrayList<>();
        final String id = task.getId().toString();

        if (activeTab == TaskStatus.ACTIVE) {
            row.add(button("⏰ Remind", BotConstants.CB_TASK_REMIND + id));
            row.add(button("✅ Complete", BotConstants.CB_TASK_DONE + id));
        }
        row.add(button("🗑 Delete", BotConstants.CB_TASK_DELETE + id));

        return row;
    }

    private List<InlineKeyboardButton> buildDeleteAllRow() {
        return List.of(button("🗑 Delete All", BotConstants.CB_DELETE_ALL_REQUEST));
    }

    private List<InlineKeyboardButton> buildTabRow(TaskStatus activeTab) {
        final String activeLabel = activeTab == TaskStatus.ACTIVE ? "Active ✓" : "Active";
        final String completedLabel = activeTab == TaskStatus.COMPLETED ? "Completed ✓" : "Completed";

        return List.of(
                button(activeLabel, BotConstants.CB_TASKS_TAB + "ACTIVE"),
                button(completedLabel, BotConstants.CB_TASKS_TAB + "COMPLETED")
        );
    }

    private InlineKeyboardButton button(String text, String callbackData) {
        final InlineKeyboardButton btn = new InlineKeyboardButton(text);
        btn.setCallbackData(callbackData);
        return btn;
    }
}
