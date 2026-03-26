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
        // Lenient: not every test exercises every button variant
        lenient().when(messageService.get(MessageKey.BTN_REMIND, Language.EN)).thenReturn("⏰ Remind");
        lenient().when(messageService.get(MessageKey.BTN_COMPLETE, Language.EN)).thenReturn("✅ Complete");
        lenient().when(messageService.get(MessageKey.BTN_DELETE, Language.EN)).thenReturn("🗑 Delete");
        lenient().when(messageService.get(MessageKey.BTN_DELETE_ALL, Language.EN)).thenReturn("🗑 Delete All");
        lenient().when(messageService.get(MessageKey.TAB_ACTIVE, Language.EN)).thenReturn("Active");
        lenient().when(messageService.get(MessageKey.TAB_COMPLETED, Language.EN)).thenReturn("Completed");
        lenient().when(messageService.get(MessageKey.TASK_COMPLETED_MARKER, Language.EN)).thenReturn(" ✓");
        builder = new TaskListKeyboardBuilder(messageService);
    }

    private static TaskDto activeTask(long id) {
        return new TaskDto(id, "Task " + id, null);
    }

    private static TaskDto completedTask(long id) {
        return new TaskDto(id, "Task " + id, null);
    }

    // For N tasks: 2 rows per task (text + action) + 1 tab row = 2N + 1
    private List<List<InlineKeyboardButton>> rows(List<TaskDto> tasks, TaskStatus tab) {
        return builder.buildKeyboard(tasks, tab, Language.EN).getKeyboard();
    }

    @Nested
    class ActiveTaskRows {

        @DisplayName("text row is a single NO_OP button containing the task id and text")
        @Test
        void textRowIsNoop() {
            TaskDto task = activeTask(3L);
            List<List<InlineKeyboardButton>> rows = rows(List.of(task), TaskStatus.ACTIVE);
            // row 0 = text row
            List<InlineKeyboardButton> textRow = rows.get(0);
            assertThat(textRow).hasSize(1);
            assertThat(textRow.get(0).getCallbackData()).isEqualTo(BotConstantsUtils.CB_NO_OP);
            assertThat(textRow.get(0).getText()).contains("#3").contains("Task 3");
        }

        @DisplayName("action row has exactly 3 buttons: Remind, Complete, Delete")
        @Test
        void actionRowHasThreeButtons() {
            TaskDto task = activeTask(3L);
            List<List<InlineKeyboardButton>> rows = rows(List.of(task), TaskStatus.ACTIVE);
            // row 1 = action row
            List<InlineKeyboardButton> actionRow = rows.get(1);
            assertThat(actionRow).hasSize(3);
        }

        @DisplayName("Remind button has TASK_REMIND:<id> callback")
        @Test
        void remindButtonCallback() {
            TaskDto task = activeTask(5L);
            List<InlineKeyboardButton> actionRow = rows(List.of(task), TaskStatus.ACTIVE).get(1);
            assertThat(actionRow.get(0).getCallbackData())
                    .isEqualTo(BotConstantsUtils.CB_TASK_REMIND + "5");
        }

        @DisplayName("Complete button has TASK_DONE:<id> callback")
        @Test
        void completeButtonCallback() {
            TaskDto task = activeTask(5L);
            List<InlineKeyboardButton> actionRow = rows(List.of(task), TaskStatus.ACTIVE).get(1);
            assertThat(actionRow.get(1).getCallbackData())
                    .isEqualTo(BotConstantsUtils.CB_TASK_DONE + "5");
        }

        @DisplayName("Delete button has TASK_DELETE:<id> callback")
        @Test
        void deleteButtonCallback() {
            TaskDto task = activeTask(5L);
            List<InlineKeyboardButton> actionRow = rows(List.of(task), TaskStatus.ACTIVE).get(1);
            assertThat(actionRow.get(2).getCallbackData())
                    .isEqualTo(BotConstantsUtils.CB_TASK_DELETE + "5");
        }
    }

    @Nested
    class CompletedTaskRows {

        @DisplayName("action row for completed task has exactly 1 button: Delete")
        @Test
        void completedActionRowHasOneButton() {
            TaskDto task = completedTask(9L);
            List<List<InlineKeyboardButton>> rows = rows(List.of(task), TaskStatus.COMPLETED);
            // row 1 = action row
            List<InlineKeyboardButton> actionRow = rows.get(1);
            assertThat(actionRow).hasSize(1);
        }

        @DisplayName("Delete button for completed task has TASK_DELETE:<id> callback")
        @Test
        void completedDeleteButtonCallback() {
            TaskDto task = completedTask(9L);
            List<InlineKeyboardButton> actionRow = rows(List.of(task), TaskStatus.COMPLETED).get(1);
            assertThat(actionRow.get(0).getCallbackData())
                    .isEqualTo(BotConstantsUtils.CB_TASK_DELETE + "9");
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

        @DisplayName("3 active tasks → 7 rows (2 per task + 1 tab)")
        @Test
        void threeTasks() {
            List<TaskDto> tasks = List.of(activeTask(1), activeTask(2), activeTask(3));
            assertThat(rows(tasks, TaskStatus.ACTIVE)).hasSize(7);
        }

        @DisplayName("task text row carries reminder time when set")
        @Test
        void textRowIncludesReminderWhenSet() {
            TaskDto task = new TaskDto(1L, "Buy milk", "25.03.2026 09:00");
            List<InlineKeyboardButton> textRow = rows(List.of(task), TaskStatus.ACTIVE).get(0);
            assertThat(textRow.get(0).getText()).contains("⏰").contains("25.03.2026 09:00");
        }
    }
}
