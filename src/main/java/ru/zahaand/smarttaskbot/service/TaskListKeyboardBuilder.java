package ru.zahaand.smarttaskbot.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import ru.zahaand.smarttaskbot.config.BotConstants;
import ru.zahaand.smarttaskbot.dto.TaskDto;
import ru.zahaand.smarttaskbot.model.Language;
import ru.zahaand.smarttaskbot.model.MessageKey;
import ru.zahaand.smarttaskbot.model.TaskStatus;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the inline keyboard for the task list message.
 * Each task row gets action buttons; on the Completed tab a "🗑 Delete All" row is
 * appended when tasks are present; a tab row is always appended last.
 * Accepts a list already truncated to at most 20 items by the caller.
 * All button labels are resolved via {@link MessageService} in the user's language.
 * <p>
 * Строит инлайн-клавиатуру для сообщения со списком задач.
 * На вкладке «Выполненные» добавляет кнопку «🗑 Удалить все» при наличии задач;
 * последней строкой всегда идёт переключатель вкладок.
 * Все подписи кнопок разрешаются через {@link MessageService} на языке пользователя.
 */
@Component
@RequiredArgsConstructor
public class TaskListKeyboardBuilder {

    private final MessageService messageService;

    /**
     * Builds the complete inline keyboard for the given task list, active tab, and user language.
     *
     * @param tasks     task DTOs to render (caller must truncate to ≤20 items)
     * @param activeTab currently selected tab — determines action buttons and Delete All visibility
     * @param language  user's language for button label localization (null falls back to EN)
     * @return fully assembled {@link InlineKeyboardMarkup}
     * <p>
     * Строит полную инлайн-клавиатуру для списка задач, активной вкладки и языка пользователя.
     */
    public InlineKeyboardMarkup buildKeyboard(List<TaskDto> tasks, TaskStatus activeTab, Language language) {
        final List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (TaskDto task : tasks) {
            // Text row: task title as a non-interactive label
            rows.add(buildTaskTextRow(task));
            // Action row: operation buttons
            rows.add(buildTaskActionRow(task, activeTab, language));
        }

        if (activeTab == TaskStatus.COMPLETED && !tasks.isEmpty()) {
            rows.add(buildDeleteAllRow(language));
        }

        rows.add(buildTabRow(activeTab, language));

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

    private List<InlineKeyboardButton> buildTaskActionRow(TaskDto task, TaskStatus activeTab, Language language) {
        final List<InlineKeyboardButton> row = new ArrayList<>();
        final String id = task.getId().toString();

        if (activeTab == TaskStatus.ACTIVE) {
            row.add(button(messageService.get(MessageKey.BTN_REMIND, language), BotConstants.CB_TASK_REMIND + id));
            row.add(button(messageService.get(MessageKey.BTN_COMPLETE, language), BotConstants.CB_TASK_DONE + id));
        }
        row.add(button(messageService.get(MessageKey.BTN_DELETE, language), BotConstants.CB_TASK_DELETE + id));

        return row;
    }

    private List<InlineKeyboardButton> buildDeleteAllRow(Language language) {
        return List.of(button(messageService.get(MessageKey.BTN_DELETE_ALL, language), BotConstants.CB_DELETE_ALL_REQUEST));
    }

    private List<InlineKeyboardButton> buildTabRow(TaskStatus activeTab, Language language) {
        final String activeBase = messageService.get(MessageKey.TAB_ACTIVE, language);
        final String completedBase = messageService.get(MessageKey.TAB_COMPLETED, language);
        final String activeLabel = activeBase + (activeTab == TaskStatus.ACTIVE ? " ✓" : "");
        final String completedLabel = completedBase + (activeTab == TaskStatus.COMPLETED ? " ✓" : "");

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
