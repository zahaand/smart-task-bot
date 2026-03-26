package ru.zahaand.smarttaskbot.handler.callback;

import org.junit.jupiter.api.BeforeEach;
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
import ru.zahaand.smarttaskbot.config.BotConstantsUtils;
import ru.zahaand.smarttaskbot.dto.TaskDto;
import ru.zahaand.smarttaskbot.model.BotException;
import ru.zahaand.smarttaskbot.model.Language;
import ru.zahaand.smarttaskbot.model.MessageKey;
import ru.zahaand.smarttaskbot.model.User;
import ru.zahaand.smarttaskbot.service.MessageService;
import ru.zahaand.smarttaskbot.service.NotificationService;
import ru.zahaand.smarttaskbot.service.TaskService;
import ru.zahaand.smarttaskbot.service.UserService;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskDetailCallbackHandlerTest {

    @Mock
    TaskService taskService;
    @Mock
    NotificationService notificationService;
    @Mock
    UserService userService;
    @Mock
    MessageService messageService;
    @InjectMocks
    TaskDetailCallbackHandler handler;

    private Update update;
    private CallbackQuery cq;
    private Message message;
    private org.telegram.telegrambots.meta.api.objects.User from;

    private static final Long USER_ID = 42L;
    private static final Long CHAT_ID = 100L;
    private static final Long TASK_ID = 7L;
    private static final String CB_ID = "cbId";

    @BeforeEach
    void setUp() {
        update = mock(Update.class);
        cq = mock(CallbackQuery.class);
        message = mock(Message.class);
        from = mock(org.telegram.telegrambots.meta.api.objects.User.class);

        when(update.getCallbackQuery()).thenReturn(cq);
        when(cq.getFrom()).thenReturn(from);
        when(from.getId()).thenReturn(USER_ID);
        when(cq.getMessage()).thenReturn(message);
        when(message.getChatId()).thenReturn(CHAT_ID);
        when(cq.getId()).thenReturn(CB_ID);
        when(cq.getData()).thenReturn(BotConstantsUtils.CB_TASK_DETAIL + TASK_ID);

        final User user = mock(User.class);
        lenient().when(user.getLanguage()).thenReturn(Language.EN);
        lenient().when(userService.findById(USER_ID)).thenReturn(user);
        lenient().when(messageService.get(any(MessageKey.class), nullable(Language.class)))
                .thenReturn("Something went wrong. Please try again.");
    }

    @Nested
    class HappyPath {

        @DisplayName("loads task and sends detail view with action buttons")
        @Test
        void loadsTaskAndSendsDetail() {
            final TaskDto task = new TaskDto(TASK_ID, "Buy milk", null);
            when(taskService.getTask(USER_ID, TASK_ID)).thenReturn(task);

            handler.handle(update);

            verify(notificationService).answerCallbackQuery(CB_ID);
            verify(notificationService).sendTaskDetail(eq(CHAT_ID), eq(task), eq(Language.EN));
        }
    }

    @Nested
    class TaskNotFound {

        @DisplayName("sends error message when task does not exist")
        @Test
        void sendsErrorWhenTaskNotFound() {
            when(taskService.getTask(USER_ID, TASK_ID))
                    .thenThrow(new BotException(MessageKey.TASK_NOT_FOUND, TASK_ID));

            handler.handle(update);

            verify(notificationService).answerCallbackQuery(CB_ID);
            verify(notificationService).sendMessage(eq(CHAT_ID), contains("Something went wrong"));
            verify(notificationService, never()).sendTaskDetail(any(), any(), any());
        }
    }
}
