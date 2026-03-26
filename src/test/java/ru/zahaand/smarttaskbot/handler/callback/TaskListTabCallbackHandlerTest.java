package ru.zahaand.smarttaskbot.handler.callback;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import ru.zahaand.smarttaskbot.config.BotConstants;
import ru.zahaand.smarttaskbot.dto.TaskDto;
import ru.zahaand.smarttaskbot.model.Language;
import ru.zahaand.smarttaskbot.model.TaskStatus;
import ru.zahaand.smarttaskbot.model.User;
import ru.zahaand.smarttaskbot.service.NotificationService;
import ru.zahaand.smarttaskbot.service.TaskService;
import ru.zahaand.smarttaskbot.service.UserService;

import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskListTabCallbackHandlerTest {

    @Mock
    TaskService taskService;
    @Mock
    NotificationService notificationService;
    @Mock
    UserService userService;
    @InjectMocks
    TaskListTabCallbackHandler handler;

    private static final Long USER_ID = 42L;
    private static final Long CHAT_ID = 100L;
    private static final Integer MESSAGE_ID = 7;

    private Update buildUpdate(String callbackData, InlineKeyboardMarkup markup) {
        Update update = mock(Update.class);
        CallbackQuery cq = mock(CallbackQuery.class);
        Message message = mock(Message.class);
        org.telegram.telegrambots.meta.api.objects.User from =
                mock(org.telegram.telegrambots.meta.api.objects.User.class);

        when(update.getCallbackQuery()).thenReturn(cq);
        when(cq.getFrom()).thenReturn(from);
        when(from.getId()).thenReturn(USER_ID);
        when(cq.getMessage()).thenReturn(message);
        when(message.getChatId()).thenReturn(CHAT_ID);
        when(message.getMessageId()).thenReturn(MESSAGE_ID);
        when(cq.getData()).thenReturn(callbackData);
        when(cq.getId()).thenReturn("cbId");
        when(message.getReplyMarkup()).thenReturn(markup);

        return update;
    }

    private InlineKeyboardMarkup markupWithButton(String callbackData, String label) {
        InlineKeyboardButton btn = new InlineKeyboardButton(label);
        btn.setCallbackData(callbackData);
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(List.of(btn)));
        return markup;
    }

    @Nested
    @DisplayName("Tab switching")
    class TabSwitching {

        @Test
        @DisplayName("ACTIVE tab: fetches active tasks and edits task list")
        void activeTabEditsWithActiveTasks() {
            String callbackData = BotConstants.CB_TASKS_TAB + "ACTIVE";
            List<TaskDto> tasks = List.of(new TaskDto(1L, "Buy milk", null));
            User user = mock(User.class);
            when(user.getLanguage()).thenReturn(Language.EN);
            when(userService.findById(USER_ID)).thenReturn(user);
            when(taskService.getActiveTasks(USER_ID)).thenReturn(tasks);
            // No ✓ in label → not already the active tab
            Update update = buildUpdate(callbackData, markupWithButton(callbackData, "📋 Active"));

            handler.handle(update);

            verify(taskService).getActiveTasks(USER_ID);
            verify(notificationService).editTaskList(CHAT_ID, MESSAGE_ID, tasks, TaskStatus.ACTIVE, Language.EN);
        }

        @Test
        @DisplayName("COMPLETED tab: fetches completed tasks and edits task list")
        void completedTabEditsWithCompletedTasks() {
            String callbackData = BotConstants.CB_TASKS_TAB + "COMPLETED";
            List<TaskDto> tasks = List.of();
            User user = mock(User.class);
            when(user.getLanguage()).thenReturn(Language.EN);
            when(userService.findById(USER_ID)).thenReturn(user);
            when(taskService.getCompletedTasks(USER_ID)).thenReturn(tasks);
            Update update = buildUpdate(callbackData, markupWithButton(callbackData, "✅ Completed"));

            handler.handle(update);

            verify(taskService).getCompletedTasks(USER_ID);
            verify(notificationService).editTaskList(CHAT_ID, MESSAGE_ID, tasks, TaskStatus.COMPLETED, Language.EN);
        }

        @Test
        @DisplayName("already-active tab: answers query silently without editing")
        void alreadyActiveTabIsNoOp() {
            String callbackData = BotConstants.CB_TASKS_TAB + "ACTIVE";
            // ✓ in label → handler treats this as the already-active tab
            Update update = buildUpdate(callbackData, markupWithButton(callbackData, "✓ Active"));

            handler.handle(update);

            verify(notificationService).answerCallbackQuery("cbId");
            verifyNoInteractions(taskService);
            verify(notificationService, never()).editTaskList(any(), any(), any(), any(), any());
        }
    }
}
