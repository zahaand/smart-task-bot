package ru.zahaand.smarttaskbot.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.zahaand.smarttaskbot.dto.TaskDto;
import ru.zahaand.smarttaskbot.model.TaskStatus;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    AbsSender sender;
    @Mock
    TaskListKeyboardBuilder taskListKeyboardBuilder;
    @Mock
    CalendarKeyboardBuilder calendarKeyboardBuilder;

    NotificationService service;

    private static final Long CHAT_ID = 100L;
    private static final Integer MESSAGE_ID = 7;

    @BeforeEach
    void setUp() {
        service = new NotificationService(sender, taskListKeyboardBuilder, calendarKeyboardBuilder);
    }

    // ── sendTaskList ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("sendTaskList()")
    class SendTaskList {

        @Test
        @DisplayName("empty ACTIVE list sends empty-state message")
        void emptyActiveListSendsEmptyMessage() throws TelegramApiException {
            when(taskListKeyboardBuilder.buildKeyboard(any(), any())).thenReturn(new InlineKeyboardMarkup());

            service.sendTaskList(CHAT_ID, List.of(), TaskStatus.ACTIVE);

            ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
            verify(sender).execute(captor.capture());
            assertThat(captor.getValue().getText()).contains("No active tasks yet");
        }

        @Test
        @DisplayName("non-empty ACTIVE list sends header with count")
        void nonEmptyActiveListSendsHeader() throws TelegramApiException {
            List<TaskDto> tasks = List.of(new TaskDto(1L, "Buy milk", null));
            when(taskListKeyboardBuilder.buildKeyboard(any(), any())).thenReturn(new InlineKeyboardMarkup());

            service.sendTaskList(CHAT_ID, tasks, TaskStatus.ACTIVE);

            ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
            verify(sender).execute(captor.capture());
            assertThat(captor.getValue().getText()).contains("Active tasks").contains("1");
        }

        @Test
        @DisplayName("21-task list is truncated to 20 with overflow note")
        void twentyOneTasksTruncatedToTwenty() throws TelegramApiException {
            List<TaskDto> tasks = new ArrayList<>();
            for (int i = 1; i <= 21; i++) {
                tasks.add(new TaskDto((long) i, "Task " + i, null));
            }
            when(taskListKeyboardBuilder.buildKeyboard(any(), any())).thenReturn(new InlineKeyboardMarkup());

            service.sendTaskList(CHAT_ID, tasks, TaskStatus.ACTIVE);

            ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
            verify(sender).execute(captor.capture());
            String text = captor.getValue().getText();
            assertThat(text).contains("20").contains("…");
        }
    }

    // ── sendCalendar ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("sendCalendar()")
    class SendCalendar {

        @Test
        @DisplayName("sends message with text 'Select a date:'")
        void sendsSelectADateText() throws TelegramApiException {
            when(calendarKeyboardBuilder.buildCalendar(anyInt(), anyInt())).thenReturn(new InlineKeyboardMarkup());

            service.sendCalendar(CHAT_ID, 2026, 6);

            ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
            verify(sender).execute(captor.capture());
            assertThat(captor.getValue().getText()).isEqualTo("Select a date:");
        }
    }

    // ── editCalendar ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("editCalendar()")
    class EditCalendar {

        @Test
        @DisplayName("sends EditMessageText with correct chatId and messageId")
        void sendsEditWithCorrectIds() throws TelegramApiException {
            when(calendarKeyboardBuilder.buildCalendar(anyInt(), anyInt())).thenReturn(new InlineKeyboardMarkup());

            service.editCalendar(CHAT_ID, MESSAGE_ID, 2026, 6);

            ArgumentCaptor<EditMessageText> captor = ArgumentCaptor.forClass(EditMessageText.class);
            verify(sender).execute(captor.capture());
            assertThat(captor.getValue().getChatId()).isEqualTo(CHAT_ID.toString());
            assertThat(captor.getValue().getMessageId()).isEqualTo(MESSAGE_ID);
        }
    }

    // ── sendDeleteConfirmation ─────────────────────────────────────────────────

    @Nested
    @DisplayName("sendDeleteConfirmation()")
    class SendDeleteConfirmation {

        @Test
        @DisplayName("message text contains 'Delete task'")
        void messageTextContainsDeleteTask() throws TelegramApiException {
            service.sendDeleteConfirmation(CHAT_ID, 5L, "Buy groceries");

            ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
            verify(sender).execute(captor.capture());
            assertThat(captor.getValue().getText()).contains("Delete task");
        }

        @Test
        @DisplayName("long task text is truncated at 80 characters with ellipsis")
        void longTextIsTruncatedAt80Chars() throws TelegramApiException {
            String longText = "A".repeat(100);

            service.sendDeleteConfirmation(CHAT_ID, 5L, longText);

            ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
            verify(sender).execute(captor.capture());
            assertThat(captor.getValue().getText()).contains("A".repeat(80) + "…");
        }
    }
}
