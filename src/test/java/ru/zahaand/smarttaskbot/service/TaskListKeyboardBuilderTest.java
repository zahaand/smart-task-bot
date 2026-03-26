package ru.zahaand.smarttaskbot.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import ru.zahaand.smarttaskbot.config.BotConstantsUtils;
import ru.zahaand.smarttaskbot.dto.TaskDto;
import ru.zahaand.smarttaskbot.model.Language;
import ru.zahaand.smarttaskbot.model.MessageKey;
import ru.zahaand.smarttaskbot.model.TaskStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class TaskListKeyboardBuilderTest {

    @Mock
    MessageService messageService;

    TaskListKeyboardBuilder builder;

    @BeforeEach
    void setUp() {
        lenient().when(messageService.get(MessageKey.BTN_DELETE_ALL, Language.EN)).thenReturn("🗑 Delete All");
        lenient().when(messageService.get(MessageKey.TAB_ACTIVE, Language.EN)).thenReturn("Active");
        lenient().when(messageService.get(MessageKey.TAB_COMPLETED, Language.EN)).thenReturn("Completed");
        lenient().when(messageService.get(MessageKey.TASK_COMPLETED_MARKER, Language.EN)).thenReturn(" ✓");
        builder = new TaskListKeyboardBuilder(messageService);
    }

    private static TaskDto task(long id) {
        return new TaskDto(id, "Task " + id, null);
    }

    private List<List<InlineKeyboardButton>> rows(List<TaskDto> tasks, TaskStatus tab) {
        return builder.buildKeyboard(tasks, tab, Language.EN).getKeyboard();
    }

    @Nested
    class TaskButtons {

        @DisplayName("each task is a single button with TASK_DETAIL:<id> callback")
        @Test
        void taskButtonHasDetailCallback() {
            List<List<InlineKeyboardButton>> rows = rows(List.of(task(3L)), TaskStatus.ACTIVE);
            List<InlineKeyboardButton> taskRow = rows.get(0);
            assertThat(taskRow).hasSize(1);
            assertThat(taskRow.get(0).getCallbackData())
                    .isEqualTo(BotConstantsUtils.CB_TASK_DETAIL + "3");
        }

        @DisplayName("task button text contains task id and text")
        @Test
        void taskButtonTextContainsIdAndText() {
            List<List<InlineKeyboardButton>> rows = rows(List.of(task(5L)), TaskStatus.ACTIVE);
            assertThat(rows.get(0).get(0).getText()).contains("#5").contains("Task 5");
        }

        @DisplayName("task button includes reminder time when set")
        @Test
        void taskButtonIncludesReminderTime() {
            TaskDto taskWithReminder = new TaskDto(1L, "Buy milk", "25.03.2026 09:00");
            List<List<InlineKeyboardButton>> rows = rows(List.of(taskWithReminder), TaskStatus.ACTIVE);
            assertThat(rows.get(0).get(0).getText()).contains("⏰").contains("25.03.2026 09:00");
        }
    }

    @Nested
    class TabRow {

        @DisplayName("tab row is always present as the last row")
        @Test
        void tabRowAlwaysPresent() {
            List<List<InlineKeyboardButton>> noTaskRows = rows(List.of(), TaskStatus.ACTIVE);
            List<InlineKeyboardButton> tabRow = noTaskRows.get(noTaskRows.size() - 1);
            assertThat(tabRow).hasSize(2);
        }

        @DisplayName("ACTIVE tab shows \"Active ✓\" and \"Completed\"")
        @Test
        void activeTabLabels() {
            List<List<InlineKeyboardButton>> r = rows(List.of(), TaskStatus.ACTIVE);
            List<InlineKeyboardButton> tabRow = r.get(r.size() - 1);
            assertThat(tabRow.get(0).getText()).isEqualTo("Active ✓");
            assertThat(tabRow.get(1).getText()).isEqualTo("Completed");
        }

        @DisplayName("COMPLETED tab shows \"Active\" and \"Completed ✓\"")
        @Test
        void completedTabLabels() {
            List<List<InlineKeyboardButton>> r = rows(List.of(), TaskStatus.COMPLETED);
            List<InlineKeyboardButton> tabRow = r.get(r.size() - 1);
            assertThat(tabRow.get(0).getText()).isEqualTo("Active");
            assertThat(tabRow.get(1).getText()).isEqualTo("Completed ✓");
        }

        @DisplayName("tab callbacks are TASKS_TAB:ACTIVE and TASKS_TAB:COMPLETED")
        @Test
        void tabCallbacks() {
            List<List<InlineKeyboardButton>> r = rows(List.of(), TaskStatus.ACTIVE);
            List<InlineKeyboardButton> tabRow = r.get(r.size() - 1);
            assertThat(tabRow.get(0).getCallbackData()).isEqualTo(BotConstantsUtils.CB_TASKS_TAB + "ACTIVE");
            assertThat(tabRow.get(1).getCallbackData()).isEqualTo(BotConstantsUtils.CB_TASKS_TAB + "COMPLETED");
        }
    }

    @Nested
    class RowCount {

        @DisplayName("0 tasks → 1 row (tab only)")
        @Test
        void zeroTasks() {
            assertThat(rows(List.of(), TaskStatus.ACTIVE)).hasSize(1);
        }

        @DisplayName("3 active tasks → 4 rows (1 per task + 1 tab)")
        @Test
        void threeTasks() {
            List<TaskDto> tasks = List.of(task(1), task(2), task(3));
            assertThat(rows(tasks, TaskStatus.ACTIVE)).hasSize(4);
        }

        @DisplayName("2 completed tasks → 4 rows (1 per task + Delete All + tab)")
        @Test
        void completedTasksIncludeDeleteAll() {
            List<TaskDto> tasks = List.of(task(1), task(2));
            assertThat(rows(tasks, TaskStatus.COMPLETED)).hasSize(4);
        }
    }

    @Nested
    class DeleteAllRow {

        @DisplayName("Delete All row is present for non-empty COMPLETED tab")
        @Test
        void deleteAllPresent() {
            List<List<InlineKeyboardButton>> rows = rows(List.of(task(1)), TaskStatus.COMPLETED);
            // rows: task button, Delete All, tab
            List<InlineKeyboardButton> deleteAllRow = rows.get(1);
            assertThat(deleteAllRow.get(0).getCallbackData())
                    .isEqualTo(BotConstantsUtils.CB_DELETE_ALL_REQUEST);
        }

        @DisplayName("Delete All row is absent for ACTIVE tab")
        @Test
        void deleteAllAbsentForActive() {
            List<List<InlineKeyboardButton>> rows = rows(List.of(task(1)), TaskStatus.ACTIVE);
            // rows: task button, tab — no Delete All
            assertThat(rows).hasSize(2);
        }
    }
}
