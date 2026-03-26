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
import ru.zahaand.smarttaskbot.model.Language;
import ru.zahaand.smarttaskbot.model.MessageKey;
import ru.zahaand.smarttaskbot.model.TaskStatus;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    AbsSender sender;
    @Mock
    TaskListKeyboardBuilder taskListKeyboardBuilder;
    @Mock
    CalendarKeyboardBuilder calendarKeyboardBuilder;
    @Mock
    MessageService messageService;

    NotificationService service;

    private static final Long CHAT_ID = 100L;
    private static final Integer MESSAGE_ID = 7;

    @BeforeEach
    void setUp() {
        service = new NotificationService(sender, taskListKeyboardBuilder, calendarKeyboardBuilder, messageService);
        lenient().when(messageService.get(any(MessageKey.class), nullable(Language.class)))
                .thenAnswer(inv -> ((MessageKey) inv.getArgument(0)).get(inv.getArgument(1)));
    }

    // ── sendTaskList ───────────────────────────────────────────────────────────

    @Nested
    class SendTaskList {

        @DisplayName("empty ACTIVE list sends empty-state message")
        @Test
        void emptyActiveListSendsEmptyMessage() throws TelegramApiException {
            when(taskListKeyboardBuilder.buildKeyboard(any(), any(), any())).thenReturn(new InlineKeyboardMarkup());

            service.sendTaskList(CHAT_ID, List.of(), TaskStatus.ACTIVE, null);

            ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
            verify(sender).execute(captor.capture());
            assertThat(captor.getValue().getText()).contains("No active tasks yet");
        }

        @DisplayName("empty COMPLETED list sends empty-state message")
        @Test
        void emptyCompletedListSendsEmptyMessage() throws TelegramApiException {
            when(taskListKeyboardBuilder.buildKeyboard(any(), any(), any())).thenReturn(new InlineKeyboardMarkup());

            service.sendTaskList(CHAT_ID, List.of(), TaskStatus.COMPLETED, null);

            ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
            verify(sender).execute(captor.capture());
            assertThat(captor.getValue().getText()).contains("No completed tasks yet");
        }

        @DisplayName("non-empty ACTIVE list sends header with count")
        @Test
        void nonEmptyActiveListSendsHeader() throws TelegramApiException {
            List<TaskDto> tasks = List.of(new TaskDto(1L, "Buy milk", null));
            when(taskListKeyboardBuilder.buildKeyboard(any(), any(), any())).thenReturn(new InlineKeyboardMarkup());

            service.sendTaskList(CHAT_ID, tasks, TaskStatus.ACTIVE, null);

            ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
            verify(sender).execute(captor.capture());
            assertThat(captor.getValue().getText()).contains("Active tasks").contains("1");
        }

        @DisplayName("21-task list is truncated to 20 with overflow note")
        @Test
        void twentyOneTasksTruncatedToTwenty() throws TelegramApiException {
            List<TaskDto> tasks = new ArrayList<>();
            for (int i = 1; i <= 21; i++) {
                tasks.add(new TaskDto((long) i, "Task " + i, null));
            }
            when(taskListKeyboardBuilder.buildKeyboard(any(), any(), any())).thenReturn(new InlineKeyboardMarkup());

            service.sendTaskList(CHAT_ID, tasks, TaskStatus.ACTIVE, null);

            ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
            verify(sender).execute(captor.capture());
            String text = captor.getValue().getText();
            assertThat(text).contains("20").contains("…");
        }
    }

    // ── sendCalendar ───────────────────────────────────────────────────────────

    @Nested
    class SendCalendar {

        @DisplayName("sends message with localized 'Choose reminder date:' text")
        @Test
        void sendsChooseReminderDateText() throws TelegramApiException {
            when(calendarKeyboardBuilder.buildCalendar(anyInt(), anyInt())).thenReturn(new InlineKeyboardMarkup());

            service.sendCalendar(CHAT_ID, 2026, 6, null);

            ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
            verify(sender).execute(captor.capture());
            assertThat(captor.getValue().getText()).isEqualTo(MessageKey.CHOOSE_REMINDER_DATE.get(null));
        }
    }

    // ── editCalendar ───────────────────────────────────────────────────────────

    @Nested
    class EditCalendar {

        @DisplayName("sends EditMessageText with correct chatId and messageId")
        @Test
        void sendsEditWithCorrectIds() throws TelegramApiException {
            when(calendarKeyboardBuilder.buildCalendar(anyInt(), anyInt())).thenReturn(new InlineKeyboardMarkup());

            service.editCalendar(CHAT_ID, MESSAGE_ID, 2026, 6, null);

            ArgumentCaptor<EditMessageText> captor = ArgumentCaptor.forClass(EditMessageText.class);
            verify(sender).execute(captor.capture());
            assertThat(captor.getValue().getChatId()).isEqualTo(CHAT_ID.toString());
            assertThat(captor.getValue().getMessageId()).isEqualTo(MESSAGE_ID);
        }
    }

    // ── sendDeleteConfirmation ─────────────────────────────────────────────────

    @Nested
    class SendDeleteConfirmation {

        @DisplayName("message text contains 'Delete task'")
        @Test
        void messageTextContainsDeleteTask() throws TelegramApiException {
            service.sendDeleteConfirmation(CHAT_ID, 5L, "Buy groceries", null);

            ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
            verify(sender).execute(captor.capture());
            assertThat(captor.getValue().getText()).contains("Delete task");
        }

        @DisplayName("long task text is truncated at 80 characters with ellipsis")
        @Test
        void longTextIsTruncatedAt80Chars() throws TelegramApiException {
            String longText = "A".repeat(100);

            service.sendDeleteConfirmation(CHAT_ID, 5L, longText, null);

            ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
            verify(sender).execute(captor.capture());
            assertThat(captor.getValue().getText()).contains("A".repeat(80) + "…");
        }
    }

    // ── sendDeleteAllConfirmation ──────────────────────────────────────────────

    @Nested
    class SendDeleteAllConfirmation {

        @DisplayName("EN language: confirmation buttons contain 'Yes, delete all' and 'Cancel'")
        @Test
        void enLanguageButtonLabels() throws TelegramApiException {
            service.sendDeleteAllConfirmation(CHAT_ID, "Delete all 3 tasks?", Language.EN);

            ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
            verify(sender).execute(captor.capture());
            InlineKeyboardMarkup markup = (InlineKeyboardMarkup) captor.getValue().getReplyMarkup();
            String yesLabel = markup.getKeyboard().get(0).get(0).getText();
            String cancelLabel = markup.getKeyboard().get(0).get(1).getText();
            assertThat(yesLabel).contains("Yes, delete all");
            assertThat(cancelLabel).contains("Cancel");
        }

        @DisplayName("RU language: confirmation buttons are in Russian")
        @Test
        void ruLanguageButtonLabels() throws TelegramApiException {
            service.sendDeleteAllConfirmation(CHAT_ID, "Удалить все 3 задачи?", Language.RU);

            ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
            verify(sender).execute(captor.capture());
            InlineKeyboardMarkup markup = (InlineKeyboardMarkup) captor.getValue().getReplyMarkup();
            String yesLabel = markup.getKeyboard().get(0).get(0).getText();
            assertThat(yesLabel).contains("Да, удалить");
        }
    }
}
