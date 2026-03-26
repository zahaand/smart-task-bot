package ru.zahaand.smarttaskbot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import ru.zahaand.smarttaskbot.config.BotConstantsUtils;
import ru.zahaand.smarttaskbot.dto.TaskDto;
import ru.zahaand.smarttaskbot.model.Language;
import ru.zahaand.smarttaskbot.model.MessageKey;
import ru.zahaand.smarttaskbot.model.TaskStatus;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the inline keyboard for the task list message.
 * Each task is a single clickable button leading to the task detail view.
 * On the Completed tab a "🗑 Delete All" row is appended when tasks are present;
 * a tab row is always appended last.
 * Accepts a list already truncated to at most 20 items by the caller.
 * All button labels are resolved via {@link MessageService} in the user's language.
 * <p>
 * Строит инлайн-клавиатуру для сообщения со списком задач.
 * Каждая задача — одна кнопка, ведущая к детальному просмотру задачи.
 * На вкладке «Выполненные» добавляет кнопку «🗑 Удалить все» при наличии задач;
 * последней строкой всегда идёт переключатель вкладок.
 * Все подписи кнопок разрешаются через {@link MessageService} на языке пользователя.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskListKeyboardBuilder {

    private final MessageService messageService;

    /**
     * Builds the complete inline keyboard for the given task list, active tab, and user language.
     *
     * @param tasks     task DTOs to render (caller must truncate to ≤20 items)
     * @param activeTab currently selected tab — determines Delete All visibility
     * @param language  user's language for button label localization (null falls back to EN)
     * @return fully assembled {@link InlineKeyboardMarkup}
     * <p>
     * Строит полную инлайн-клавиатуру для списка задач, активной вкладки и языка пользователя.
     */
    public InlineKeyboardMarkup buildKeyboard(List<TaskDto> tasks, TaskStatus activeTab, Language language) {
        final List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (TaskDto task : tasks) {
            rows.add(buildTaskButton(task));
        }

        if (activeTab == TaskStatus.COMPLETED && !tasks.isEmpty()) {
            rows.add(buildDeleteAllRow(language));
        }

        rows.add(buildTabRow(activeTab, language));

        final InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(rows);
        return markup;
    }

    private List<InlineKeyboardButton> buildTaskButton(TaskDto task) {
        String label = "#" + task.getId() + " " + task.getText();
        if (task.getReminderTime() != null) {
            label += " [⏰ " + task.getReminderTime() + "]";
        }
        return List.of(button(label, BotConstantsUtils.CB_TASK_DETAIL + task.getId()));
    }

    private List<InlineKeyboardButton> buildDeleteAllRow(Language language) {
        return List.of(button(messageService.get(MessageKey.BTN_DELETE_ALL, language), BotConstantsUtils.CB_DELETE_ALL_REQUEST));
    }

    private List<InlineKeyboardButton> buildTabRow(TaskStatus activeTab, Language language) {
        final String activeBase = messageService.get(MessageKey.TAB_ACTIVE, language);
        final String completedBase = messageService.get(MessageKey.TAB_COMPLETED, language);
        final String marker = messageService.get(MessageKey.TASK_COMPLETED_MARKER, language);
        final String activeLabel = activeBase + (activeTab == TaskStatus.ACTIVE ? marker : "");
        final String completedLabel = completedBase + (activeTab == TaskStatus.COMPLETED ? marker : "");

        return List.of(
                button(activeLabel, BotConstantsUtils.CB_TASKS_TAB + "ACTIVE"),
                button(completedLabel, BotConstantsUtils.CB_TASKS_TAB + "COMPLETED")
        );
    }

    private InlineKeyboardButton button(String text, String callbackData) {
        final InlineKeyboardButton btn = new InlineKeyboardButton(text);
        btn.setCallbackData(callbackData);
        return btn;
    }
}
