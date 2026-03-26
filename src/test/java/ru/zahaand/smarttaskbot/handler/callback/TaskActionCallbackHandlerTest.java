package ru.zahaand.smarttaskbot.handler.callback;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.zahaand.smarttaskbot.config.BotConstantsUtils;
import ru.zahaand.smarttaskbot.dto.ConversationContextDto;
import ru.zahaand.smarttaskbot.dto.TaskDto;
import ru.zahaand.smarttaskbot.model.*;
import ru.zahaand.smarttaskbot.service.*;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskActionCallbackHandlerTest {

    @Mock
    TaskService taskService;
    @Mock
    UserStateService userStateService;
    @Mock
    NotificationService notificationService;
    @Mock
    UserService userService;
    @Mock
    MessageService messageService;
    @InjectMocks
    TaskActionCallbackHandler handler;

    private Update update;
    private CallbackQuery cq;
    private Message message;
    private org.telegram.telegrambots.meta.api.objects.User from;

    private static final Long USER_ID = 42L;
    private static final Long CHAT_ID = 100L;
    private static final Long TASK_ID = 7L;
    private static final Integer MSG_ID = 55;
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
        when(message.getMessageId()).thenReturn(MSG_ID);
        when(cq.getId()).thenReturn(CB_ID);

        lenient().when(messageService.get(any(MessageKey.class), nullable(Language.class))).thenReturn("Something went wrong. Please try again.");
    }

    @Nested
    class TaskDone {

        @DisplayName("marks task complete and refreshes active task list in place")
        @Test
        void completesTaskAndRefreshesList() {
            when(cq.getData()).thenReturn(BotConstantsUtils.CB_TASK_DONE + TASK_ID);
            when(taskService.completeTask(USER_ID, TASK_ID)).thenReturn(new TaskDto(TASK_ID, "Buy milk", null));
            when(taskService.getActiveTasks(USER_ID)).thenReturn(List.of());

            handler.handle(update);

            verify(taskService).completeTask(USER_ID, TASK_ID);
            verify(notificationService).answerCallbackQuery(CB_ID);
            verify(notificationService).editTaskList(eq(CHAT_ID), eq(MSG_ID), eq(List.of()), eq(TaskStatus.ACTIVE), isNull());
        }
    }

    @Nested
    class TaskRemind {

        @DisplayName("sets state to SELECTING_REMINDER_DATE with taskId in context and sends calendar")
        @Test
        void setsStateAndSendsCalendar() {
            when(cq.getData()).thenReturn(BotConstantsUtils.CB_TASK_REMIND + TASK_ID);

            handler.handle(update);

            ArgumentCaptor<ConversationContextDto> ctxCaptor = ArgumentCaptor.forClass(ConversationContextDto.class);
            verify(userStateService).setStateWithContext(eq(USER_ID), eq(ConversationState.SELECTING_REMINDER_DATE), ctxCaptor.capture());
            assertThat(ctxCaptor.getValue().getTaskId()).isEqualTo(TASK_ID);
            assertThat(ctxCaptor.getValue().getViewingYear()).isNotNull();
            assertThat(ctxCaptor.getValue().getViewingMonth()).isNotNull();

            verify(notificationService).answerCallbackQuery(CB_ID);
            verify(notificationService).sendCalendar(eq(CHAT_ID), anyInt(), anyInt(), isNull());
        }
    }

    @Nested
    class TaskDelete {

        @DisplayName("sets state to CONFIRMING_DELETE with taskId in context and sends confirmation")
        @Test
        void setsStateAndSendsConfirmation() {
            when(cq.getData()).thenReturn(BotConstantsUtils.CB_TASK_DELETE + TASK_ID);
            when(taskService.getTaskText(USER_ID, TASK_ID)).thenReturn("Buy milk");

            handler.handle(update);

            ArgumentCaptor<ConversationContextDto> ctxCaptor = ArgumentCaptor.forClass(ConversationContextDto.class);
            verify(userStateService).setStateWithContext(
                    eq(USER_ID), eq(ConversationState.CONFIRMING_DELETE), ctxCaptor.capture());
            assertThat(ctxCaptor.getValue().getTaskId()).isEqualTo(TASK_ID);

            verify(notificationService).answerCallbackQuery(CB_ID);
            verify(notificationService).sendDeleteConfirmation(eq(CHAT_ID), eq(TASK_ID), eq("Buy milk"), isNull());
        }

        @DisplayName("sends error message when task no longer exists")
        @Test
        void sendsErrorWhenTaskGone() {
            when(cq.getData()).thenReturn(BotConstantsUtils.CB_TASK_DELETE + TASK_ID);
            when(taskService.getTaskText(USER_ID, TASK_ID))
                    .thenThrow(new BotException(MessageKey.TASK_NOT_FOUND, TASK_ID));

            handler.handle(update);

            verify(notificationService).answerCallbackQuery(CB_ID);
            verify(notificationService).sendMessage(eq(CHAT_ID), contains("Something went wrong"));
            verify(notificationService, never()).sendDeleteConfirmation(any(), any(), any(), any());
        }
    }
}
