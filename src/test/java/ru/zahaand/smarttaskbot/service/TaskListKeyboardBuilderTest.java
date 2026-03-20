package ru.zahaand.smarttaskbot.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import ru.zahaand.smarttaskbot.config.BotConstants;
import ru.zahaand.smarttaskbot.dto.TaskDto;
import ru.zahaand.smarttaskbot.model.TaskStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TaskListKeyboardBuilderTest {

    private final TaskListKeyboardBuilder builder = new TaskListKeyboardBuilder();

    private static TaskDto activeTask(long id) {
        return new TaskDto(id, "Task " + id, null);
    }

    private static TaskDto completedTask(long id) {
        return new TaskDto(id, "Task " + id, null);
    }

    // For N tasks: 2 rows per task (text + action) + 1 tab row = 2N + 1
    private List<List<InlineKeyboardButton>> rows(List<TaskDto> tasks, TaskStatus tab) {
        return builder.buildKeyboard(tasks, tab).getKeyboard();
    }

    @Nested
    @DisplayName("Active task rows")
    class ActiveTaskRows {

        @Test
        @DisplayName("text row is a single NO_OP button containing the task id and text")
        void textRowIsNoop() {
            TaskDto task = activeTask(3L);
            List<List<InlineKeyboardButton>> rows = rows(List.of(task), TaskStatus.ACTIVE);
            // row 0 = text row
            List<InlineKeyboardButton> textRow = rows.get(0);
            assertThat(textRow).hasSize(1);
            assertThat(textRow.get(0).getCallbackData()).isEqualTo(BotConstants.CB_NO_OP);
            assertThat(textRow.get(0).getText()).contains("#3").contains("Task 3");
        }

        @Test
        @DisplayName("action row has exactly 3 buttons: Remind, Complete, Delete")
        void actionRowHasThreeButtons() {
            TaskDto task = activeTask(3L);
            List<List<InlineKeyboardButton>> rows = rows(List.of(task), TaskStatus.ACTIVE);
            // row 1 = action row
            List<InlineKeyboardButton> actionRow = rows.get(1);
            assertThat(actionRow).hasSize(3);
        }

        @Test
        @DisplayName("Remind button has TASK_REMIND:<id> callback")
        void remindButtonCallback() {
            TaskDto task = activeTask(5L);
            List<InlineKeyboardButton> actionRow = rows(List.of(task), TaskStatus.ACTIVE).get(1);
            assertThat(actionRow.get(0).getCallbackData())
                    .isEqualTo(BotConstants.CB_TASK_REMIND + "5");
        }

        @Test
        @DisplayName("Complete button has TASK_DONE:<id> callback")
        void completeButtonCallback() {
            TaskDto task = activeTask(5L);
            List<InlineKeyboardButton> actionRow = rows(List.of(task), TaskStatus.ACTIVE).get(1);
            assertThat(actionRow.get(1).getCallbackData())
                    .isEqualTo(BotConstants.CB_TASK_DONE + "5");
        }

        @Test
        @DisplayName("Delete button has TASK_DELETE:<id> callback")
        void deleteButtonCallback() {
            TaskDto task = activeTask(5L);
            List<InlineKeyboardButton> actionRow = rows(List.of(task), TaskStatus.ACTIVE).get(1);
            assertThat(actionRow.get(2).getCallbackData())
                    .isEqualTo(BotConstants.CB_TASK_DELETE + "5");
        }
    }

    @Nested
    @DisplayName("Completed task rows")
    class CompletedTaskRows {

        @Test
        @DisplayName("action row for completed task has exactly 1 button: Delete")
        void completedActionRowHasOneButton() {
            TaskDto task = completedTask(9L);
            List<List<InlineKeyboardButton>> rows = rows(List.of(task), TaskStatus.COMPLETED);
            // row 1 = action row
            List<InlineKeyboardButton> actionRow = rows.get(1);
            assertThat(actionRow).hasSize(1);
        }

        @Test
        @DisplayName("Delete button for completed task has TASK_DELETE:<id> callback")
        void completedDeleteButtonCallback() {
            TaskDto task = completedTask(9L);
            List<InlineKeyboardButton> actionRow = rows(List.of(task), TaskStatus.COMPLETED).get(1);
            assertThat(actionRow.get(0).getCallbackData())
                    .isEqualTo(BotConstants.CB_TASK_DELETE + "9");
        }
    }

    @Nested
    @DisplayName("Tab row")
    class TabRow {

        @Test
        @DisplayName("tab row is always present as the last row")
        void tabRowAlwaysPresent() {
            List<List<InlineKeyboardButton>> noTaskRows = rows(List.of(), TaskStatus.ACTIVE);
            List<InlineKeyboardButton> tabRow = noTaskRows.get(noTaskRows.size() - 1);
            assertThat(tabRow).hasSize(2);
        }

        @Test
        @DisplayName("ACTIVE tab shows \"Active ✓\" and \"Completed\"")
        void activeTabLabels() {
            List<List<InlineKeyboardButton>> r = rows(List.of(), TaskStatus.ACTIVE);
            List<InlineKeyboardButton> tabRow = r.get(r.size() - 1);
            assertThat(tabRow.get(0).getText()).isEqualTo("Active ✓");
            assertThat(tabRow.get(1).getText()).isEqualTo("Completed");
        }

        @Test
        @DisplayName("COMPLETED tab shows \"Active\" and \"Completed ✓\"")
        void completedTabLabels() {
            List<List<InlineKeyboardButton>> r = rows(List.of(), TaskStatus.COMPLETED);
            List<InlineKeyboardButton> tabRow = r.get(r.size() - 1);
            assertThat(tabRow.get(0).getText()).isEqualTo("Active");
            assertThat(tabRow.get(1).getText()).isEqualTo("Completed ✓");
        }

        @Test
        @DisplayName("tab callbacks are TASKS_TAB:ACTIVE and TASKS_TAB:COMPLETED")
        void tabCallbacks() {
            List<List<InlineKeyboardButton>> r = rows(List.of(), TaskStatus.ACTIVE);
            List<InlineKeyboardButton> tabRow = r.get(r.size() - 1);
            assertThat(tabRow.get(0).getCallbackData()).isEqualTo(BotConstants.CB_TASKS_TAB + "ACTIVE");
            assertThat(tabRow.get(1).getCallbackData()).isEqualTo(BotConstants.CB_TASKS_TAB + "COMPLETED");
        }
    }

    @Nested
    @DisplayName("Row count")
    class RowCount {

        @Test
        @DisplayName("0 tasks → 1 row (tab only)")
        void zeroTasks() {
            assertThat(rows(List.of(), TaskStatus.ACTIVE)).hasSize(1);
        }

        @Test
        @DisplayName("3 active tasks → 7 rows (2 per task + 1 tab)")
        void threeTasks() {
            List<TaskDto> tasks = List.of(activeTask(1), activeTask(2), activeTask(3));
            assertThat(rows(tasks, TaskStatus.ACTIVE)).hasSize(7);
        }

        @Test
        @DisplayName("task text row carries reminder time when set")
        void textRowIncludesReminderWhenSet() {
            TaskDto task = new TaskDto(1L, "Buy milk", "25.03.2026 09:00");
            List<InlineKeyboardButton> textRow = rows(List.of(task), TaskStatus.ACTIVE).get(0);
            assertThat(textRow.get(0).getText()).contains("⏰").contains("25.03.2026 09:00");
        }
    }
}
